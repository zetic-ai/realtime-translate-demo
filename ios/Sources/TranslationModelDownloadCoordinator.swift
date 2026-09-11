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

  nonisolated static let consentKey = "translationModel.backgroundDownloadConsent"
  private static let handleKey = "translationModel.backgroundDownloadHandle"

  @Published private(set) var phase: Phase = .idle
  @Published private(set) var isRemoving = false

  private let defaults: UserDefaults
  private let personalKey: String
  private var scheduleTask: Task<Void, Never>?
  private var pollTask: Task<Void, Never>?

  init(defaults: UserDefaults = .standard,
       personalKey: String = MelangeCredential.value(from: Bundle.main.infoDictionary ?? [:])) {
    self.defaults = defaults
    self.personalKey = personalKey
  }

  deinit {
    scheduleTask?.cancel()
    pollTask?.cancel()
  }

  var hasDownloadConsent: Bool { defaults.bool(forKey: Self.consentKey) }
  var isBackgroundDownloadInProgress: Bool { phase.isInProgress }
  var canUseDownloadedModel: Bool { phase == .installed }

  /// A returning user has already explicitly approved this transfer, so a launch can resume the
  /// persisted SDK job without showing the consent card again.
  func resumeIfConsented(hasLocalModel: Bool) {
    guard hasDownloadConsent else { return }
    guard !hasLocalModel else {
      phase = .installed
      return
    }
    scheduleIfNeeded()
  }

  func recordConsent() {
    defaults.set(true, forKey: Self.consentKey)
    scheduleIfNeeded()
  }

  /// Returns false while the SDK job owns model preparation. The caller must not instantiate the
  /// foreground model in that case: doing so would turn the background transfer into a duplicate
  /// foreground download.
  func requestModelUse(hasLocalModel: Bool) -> Bool {
    if hasLocalModel || canUseDownloadedModel { return true }
    guard hasDownloadConsent else { return false }
    scheduleIfNeeded()
    return false
  }

  func removeDownloadedModel() async -> RemovalOutcome {
    guard !isRemoving else { return .failed("The model removal is already in progress.") }
    isRemoving = true
    defer { isRemoving = false }

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
        _ = try await ZeticMLangeLLMModel.stopBackgroundDownload(handle)
      } catch {
        // Removal is still authoritative: a completed job can disappear between polling and the
        // stop request, and the SDK's removal result below tells us whether anything remains.
      }
    }

    do {
      let result = try await ZeticMLangeLLMModel.removeDownloadedModel(name: FirstRunModel.modelName)
      guard !result.isInUse else { return .inUse }
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
    guard !isRemoving, !phase.isInProgress, phase != .installed else { return }
    phase = .queued
    scheduleTask?.cancel()
    scheduleTask = Task { [weak self] in
      await self?.scheduleOrResume()
    }
  }

  private func scheduleOrResume() async {
    defer { scheduleTask = nil }
    guard hasDownloadConsent, !isRemoving else { return }

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
      let handle = try await ZeticMLangeLLMModel.downloadInBackground(
        personalKey: personalKey,
        name: FirstRunModel.modelName,
        cacheHandlingPolicy: .KEEP_EXISTING
      )
      guard !isRemoving, !Task.isCancelled else {
        _ = try? await ZeticMLangeLLMModel.stopBackgroundDownload(handle)
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
        guard let self else { return }
        guard !self.isRemoving else { return }
        await self.refresh(handle)
        guard !self.isRemoving, self.phase.isInProgress else { return }
        try? await Task.sleep(for: .seconds(1))
      }
    }
  }

  private func refresh(_ handle: BackgroundDownloadHandle) async {
    do {
      let status = try await ZeticMLangeLLMModel.getBackgroundDownloadStatus(handle)
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
    }
  }
}
