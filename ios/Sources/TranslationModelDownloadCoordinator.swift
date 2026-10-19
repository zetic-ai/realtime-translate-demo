import Foundation
import ZeticMLange

/// Persists the SDK's background-download handle and translates its lifecycle into the small
/// amount of state the translation UI needs. A completed handle stays in storage so a relaunch
/// never starts a second transfer for the same model.
@MainActor
final class TranslationModelDownloadCoordinator: ObservableObject {
  enum Phase: Equatable {
    case idle
    case queued
    case resolving
    case downloading(Double?)
    case verifying
    case installed
    case failed(String)
    case stopped

    var isInProgress: Bool {
      switch self {
      case .queued, .resolving, .downloading, .verifying: true
      case .idle, .installed, .failed, .stopped: false
      }
    }

    var title: String {
      switch self {
      case .queued, .resolving, .downloading, .verifying:
        String(localized: "Download in progress",
               comment: "Status shown when a model is being downloaded in the background")
      case .installed:
        String(localized: "Translation model downloaded",
               comment: "Status shown after the background model download has completed")
      case .failed:
        String(localized: "Translation model download failed",
               comment: "Status shown when a background model download failed")
      case .stopped:
        String(localized: "Translation model download stopped",
               comment: "Status shown when a background model download was stopped")
      case .idle:
        String(localized: "Translation model not downloaded",
               comment: "Status shown before a model download has been approved")
      }
    }

    var detail: String? {
      switch self {
      case .queued:
        return String(localized: "Waiting to begin.",
                      comment: "Background download detail while the SDK has queued the request")
      case .resolving:
        return String(localized: "Preparing the download.",
                      comment: "Background download detail while the SDK resolves artifacts")
      case let .downloading(progress):
        guard let progress else {
          return String(localized: "Downloading the translation model.",
                        comment: "Background download detail when the SDK has no byte total")
        }
        let percent = Int((progress * 100).rounded())
        return String(localized: "Downloading translation model \(percent)%",
                             comment: "Background download detail. %lld is the percentage complete")
      case .verifying:
        return String(localized: "Verifying the downloaded model.",
                      comment: "Background download detail after bytes have arrived")
      case let .failed(message): return message
      case .idle, .installed, .stopped: return nil
      }
    }
  }

  enum RemovalOutcome: Equatable {
    case removed
    case inUse
    case failed(String)
  }

  /// The SDK owns cache discovery, transfers, and removal. These operations are injectable so
  /// lifecycle tests can exercise consent and relaunch without downloading a model.
  struct SDKClient {
    var download: () async throws -> BackgroundDownloadHandle
    var status: (BackgroundDownloadHandle) async throws -> BackgroundDownloadStatus
    var stop: (BackgroundDownloadHandle) async throws -> Void
    var remove: () async throws -> ModelRemovalResult

    static func live(personalKey: String) -> SDKClient {
      SDKClient(download: {
        try await ZeticMLangeLLMModel.downloadInBackground(
          personalKey: personalKey, name: FirstRunModel.modelName,
          cacheHandlingPolicy: .KEEP_EXISTING
        )
      }, status: { try await ZeticMLangeLLMModel.getBackgroundDownloadStatus($0) },
      stop: { _ = try await ZeticMLangeLLMModel.stopBackgroundDownload($0) },
      remove: { try await ZeticMLangeLLMModel.removeDownloadedModel(name: FirstRunModel.modelName) })
    }
  }

  nonisolated static let consentKey = "translationModel.backgroundDownloadConsent"
  static let handleKey = "translationModel.backgroundDownloadHandle"

  @Published private(set) var phase: Phase = .idle
  @Published private(set) var isRemoving = false

  private let defaults: UserDefaults
  private let personalKey: String
  private let sdk: SDKClient
  private var fixtureConsent: Bool?
  private var scheduleTask: Task<Void, Never>?
  private var pollTask: Task<Void, Never>?

  init(defaults: UserDefaults = .standard,
       personalKey: String = MelangeCredential.value(from: Bundle.main.infoDictionary ?? [:]),
       sdk: SDKClient? = nil, fixtureConsent: Bool? = nil) {
    self.defaults = defaults
    self.personalKey = personalKey
    self.sdk = sdk ?? .live(personalKey: personalKey)
    self.fixtureConsent = fixtureConsent
  }

  static func fromLaunchArguments(
    _ arguments: [String] = ProcessInfo.processInfo.arguments
  ) -> TranslationModelDownloadCoordinator {
    // Explicit UI scenarios stay in memory and never touch the real model or its saved consent.
    let scenario = FirstRunDefaults.value(named: "-firstRun", in: arguments)
    let state = FirstRunDefaults.value(named: "-uiState", in: arguments)
    let fixture = ["designConversation", "readyWithTranscript", "listeningA", "finalizingA",
                   "translationError", "setup", "loadingModel", "modelLoadFailed",
                   "longConversation", "ready", "permissionRequired"].contains(state ?? "")
    let fixtureConsent: Bool?
    if fixture {
      switch scenario {
      case "fresh", "consentNeeded": fixtureConsent = false
      case "returning": fixtureConsent = true
      default: fixtureConsent = UserDefaults.standard.bool(forKey: Self.consentKey)
      }
    } else {
      fixtureConsent = nil
    }
    return TranslationModelDownloadCoordinator(fixtureConsent: fixtureConsent)
  }

  deinit {
    scheduleTask?.cancel()
    pollTask?.cancel()
  }

  var hasDownloadConsent: Bool { fixtureConsent ?? defaults.bool(forKey: Self.consentKey) }
  var isBackgroundDownloadInProgress: Bool { phase.isInProgress }
  var canUseDownloadedModel: Bool { phase == .installed }

  /// A returning user has already explicitly approved this transfer, so a launch can resume the
  /// persisted SDK job without showing the consent card again.
  func resumeIfConsented() {
    guard hasDownloadConsent else { return }
    scheduleIfNeeded()
  }

  func recordConsent() {
    if fixtureConsent != nil {
      fixtureConsent = true
    } else {
      defaults.set(true, forKey: Self.consentKey)
    }
    scheduleIfNeeded()
  }

  /// Only an SDK-installed job can hand model preparation to the foreground runtime. Never race
  /// its in-progress background transfer with a second foreground download.
  func requestModelUse() -> Bool {
    guard !isRemoving, hasDownloadConsent else { return false }
    if canUseDownloadedModel { return true }
    scheduleIfNeeded()
    return false
  }

  func removeDownloadedModel() async -> RemovalOutcome {
    guard !isRemoving else { return .failed("The model removal is already in progress.") }
    if fixtureConsent != nil {
      fixtureConsent = false
      phase = .idle
      return .removed
    }
    isRemoving = true
    var removed = false
    defer {
      isRemoving = false
      // A refused/failed removal must not strand a cancelled poll in a progress phase.
      if !removed, storedHandle != nil {
        phase = .idle
        resumeIfConsented()
      }
    }

    let activePollTask = pollTask
    let activeScheduleTask = scheduleTask
    activePollTask?.cancel()
    activeScheduleTask?.cancel()
    pollTask = nil
    scheduleTask = nil
    await activeScheduleTask?.value
    await activePollTask?.value

    if let handle = storedHandle {
      do {
        try await sdk.stop(handle)
      } catch {
        // Removal is still authoritative: a completed job can disappear between polling and the
        // stop request, and the SDK's removal result below tells us whether anything remains.
      }
    }

    do {
      let result = try await sdk.remove()
      guard !result.isInUse else { return .inUse }
      removed = true
      clearConsentAndHandle()
      phase = .idle
      return .removed
    } catch {
      return .failed(error.localizedDescription)
    }
  }

  private var storedHandle: BackgroundDownloadHandle? {
    get {
      defaults.string(forKey: Self.handleKey).map(BackgroundDownloadHandle.init(id:))
    }
    set {
      if let newValue {
        defaults.set(newValue.id, forKey: Self.handleKey)
      } else {
        defaults.removeObject(forKey: Self.handleKey)
      }
    }
  }

  private func clearConsentAndHandle() {
    defaults.removeObject(forKey: Self.consentKey)
    storedHandle = nil
  }

  private func scheduleIfNeeded() {
    guard hasDownloadConsent, !isRemoving, !phase.isInProgress, phase != .installed else { return }
    if fixtureConsent != nil {
      phase = .installed
      return
    }
    let replacingFailedHandle: Bool
    if case .failed = phase { replacingFailedHandle = storedHandle != nil }
    else { replacingFailedHandle = false }
    phase = .queued
    scheduleTask?.cancel()
    scheduleTask = Task { [weak self] in
      await self?.scheduleOrResume(replacingFailedHandle: replacingFailedHandle)
    }
  }

  private func scheduleOrResume(replacingFailedHandle: Bool) async {
    defer { scheduleTask = nil }
    guard hasDownloadConsent, !isRemoving else { return }

    if replacingFailedHandle, let handle = storedHandle {
      do {
        // A lookup error does not prove the transfer stopped. An explicit retry first asks the
        // SDK to stop/reconcile the old job, so forgetting a stale handle cannot duplicate it.
        try await sdk.stop(handle)
        guard !isRemoving, !Task.isCancelled else { return }
        storedHandle = nil
      } catch {
        phase = .failed(error.localizedDescription)
        return
      }
    }

    if let handle = storedHandle {
      await refresh(handle)
      if phase.isInProgress { startPolling(handle) }
      return
    }

    guard !personalKey.isEmpty else {
      phase = .failed(TranslationRuntimeError.missingPersonalKey.localizedDescription)
      return
    }

    do {
      let handle = try await sdk.download()
      guard !isRemoving, !Task.isCancelled else {
        try? await sdk.stop(handle)
        return
      }
      storedHandle = handle
      await refresh(handle)
      if phase.isInProgress { startPolling(handle) }
    } catch {
      phase = .failed(error.localizedDescription)
    }
  }

  private func startPolling(_ handle: BackgroundDownloadHandle) {
    guard !isRemoving else { return }
    pollTask?.cancel()
    pollTask = Task { [weak self] in
      while !Task.isCancelled {
        guard await self?.poll(handle) == true else { return }
        try? await Task.sleep(for: .seconds(1))
      }
    }
  }

  private func poll(_ handle: BackgroundDownloadHandle) async -> Bool {
    guard !isRemoving else { return false }
    await refresh(handle)
    return !isRemoving && phase.isInProgress
  }

  private func refresh(_ handle: BackgroundDownloadHandle) async {
    do {
      let status = try await sdk.status(handle)
      guard !isRemoving, !Task.isCancelled else { return }
      switch status.state {
      case .queued:
        phase = .queued
      case .resolving:
        phase = .resolving
      case .downloading:
        let progress: Double? = status.totalBytes.flatMap { total in
          guard total > 0 else { return nil }
          return min(1, max(0, Double(status.bytesDownloaded) / Double(total)))
        }
        phase = .downloading(progress)
      case .verifying:
        phase = .verifying
      case .installed:
        phase = .installed
      case .failed:
        phase = .failed(status.errorMessage ?? String(
          localized: "The translation model could not be downloaded.",
          comment: "Fallback error after a background model download fails"
        ))
        storedHandle = nil
      case .stopped:
        phase = .stopped
        storedHandle = nil
      @unknown default:
        phase = .failed(String(
          localized: "The translation model returned an unsupported download status.",
          comment: "Fallback error when a newer SDK returns an unknown download state"
        ))
        storedHandle = nil
      }
    } catch {
      guard !isRemoving, !Task.isCancelled else { return }
      phase = .failed(error.localizedDescription)
      // Keep ownership until an explicit retry reconciles the job through the SDK stop API.
    }
  }
}
