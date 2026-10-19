import Foundation
import ZeticMLange

protocol TranslationRuntime: AnyObject {
  func load(onProgress: @escaping @Sendable (Double) -> Void) async throws
  func translate(prompt: String) async throws -> String
  /// Whether a model is in memory right now. Read synchronously from the main actor, so it must
  /// never touch the runtime's work queue: that queue can be busy for the whole of a 1.9 GB load.
  /// This is what tells the settings drawer that a delete would pull a mapped file out from under
  /// a model the app is still holding, which `isSessionLive` cannot: the model stays resident
  /// after `End Session` and is being mapped during `loadingModel`.
  var isModelResident: Bool { get }
  /// Abandons a load in flight and forgets it, leaving any already-resident model alone. Ending a
  /// session while the model is still downloading has to stop the transfer, not just stop watching
  /// it: a declined session that keeps burning cellular is worse than one that never started.
  func cancelLoad()
  func close() async
}

enum TranslationRuntimeError: LocalizedError, Equatable {
  case missingPersonalKey
  case modelNotLoaded
  case generationFailed(Int)
  case emptyOutput

  var errorDescription: String? {
    switch self {
    case .missingPersonalKey: "The Melange personal key is not configured in this app build."
    case .modelNotLoaded: "The translation model is not loaded."
    case let .generationFailed(code): "The translation model failed with code \(code)."
    case .emptyOutput: "The translation model returned an empty result."
    }
  }
}

enum MelangeCredential {
  static let infoDictionaryKey = "MelangePersonalKey"

  static func value(from infoDictionary: [String: Any]) -> String {
    guard let value = infoDictionary[infoDictionaryKey] as? String else { return "" }
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.hasPrefix("$(") ? "" : trimmed
  }
}

struct TranslationResponseAccumulator {
  private var output = ""

  mutating func append(token: String, generatedTokens: Int, code: Int) throws -> Bool {
    guard code == 0 else { throw TranslationRuntimeError.generationFailed(code) }
    guard generatedTokens > 0 else { return false }
    output.append(token)
    return true
  }

  func finalOutput() throws -> String {
    let trimmed = output.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { throw TranslationRuntimeError.emptyOutput }
    return trimmed
  }
}

/// The loaded-model surface used by the runtime and its lifecycle tests.
protocol LoadedLanguageModel: AnyObject {
  func run(_ text: String) throws
  func waitForNextToken() -> LLMNextTokenResult
  func cleanUp() throws
  func close()
}

extension ZeticMLangeLLMModel: LoadedLanguageModel {}

final class MelangeTranslationRuntime: TranslationRuntime, @unchecked Sendable {
  private let modelName: String
  private let version: Int?
  private let maxOutputTokens: Int?
  private let contextSize: Int

  /// Builds the loaded model. Injected only by tests, which cannot reach a 1.9 GB SDK download;
  /// the app always uses the SDK-managed initializer in `makeModel`.
  typealias ModelFactory = @Sendable (_ onProgress: @escaping @Sendable (Double) -> Void) async throws
    -> any LoadedLanguageModel

  private let personalKey: String
  private let modelFactory: ModelFactory?
  private let queue = DispatchQueue(label: "ai.zetic.turntranslate.melange", qos: .userInitiated)
  private var model: (any LoadedLanguageModel)?
  private var loadTask: Task<Void, Error>?
  /// Counts closes rather than latching a flag: a load that finishes after the close that
  /// invalidated it must release its model, and a runtime that is loaded again afterwards must
  /// still work. Read and written on `queue` alongside `model` and `loadTask`.
  private var closeGeneration = 0
  /// `model != nil`, readable without touching `queue`. The queue is held for the whole of a load,
  /// so a caller on the main actor must never wait on it just to ask a yes-or-no question.
  private let residency = NSLock()
  private var residentModel = false

  var isModelResident: Bool {
    residency.lock()
    defer { residency.unlock() }
    return residentModel
  }

  init(personalKey: String? = nil, infoDictionary: [String: Any] = Bundle.main.infoDictionary ?? [:],
       modelFactory: ModelFactory? = nil, modelName: String = "SJ_zetic/Hy-MT2-1.8B", version: Int? = nil, maxOutputTokens: Int? = nil,
       contextSize: Int = 2048) {
    self.modelName = modelName
    self.version = version
    self.maxOutputTokens = maxOutputTokens
    self.contextSize = contextSize
    self.personalKey = personalKey ?? MelangeCredential.value(from: infoDictionary)
    self.modelFactory = modelFactory
  }

  func load(onProgress: @escaping @Sendable (Double) -> Void) async throws {
    guard !personalKey.isEmpty else { throw TranslationRuntimeError.missingPersonalKey }
    // Memoized: `model != nil` is not held across the await, so overlapping calls would otherwise
    // each initialize their own copy of a 1.9 GB model.
    let task: Task<Void, Error>? = queue.sync {
      if model != nil { return nil }
      if let loadTask { return loadTask }
      let generation = closeGeneration
      let started = Task<Void, Error> { [weak self] in
        guard let self else { return }
        let loaded = try await makeModel(onProgress: onProgress)
        // Read before the queue hop: `Task.isCancelled` inside a `queue.sync` block is asking
        // about whichever task happens to own that thread, which is not this one.
        let cancelled = Task.isCancelled
        let adopted: Bool = queue.sync {
          guard !cancelled, generation == closeGeneration else { return false }
          self.model = loaded
          self.loadTask = nil
          // Written on `queue` alongside `model`, so residency can never end up reporting a model
          // that a close running at the same instant has already taken away.
          setResident(true)
          return true
        }
        guard adopted else {
          // The runtime this load belonged to was closed or cancelled while the model was being
          // built. Installing it now would hand 1.9 GB to nobody, so it is released instead.
          try? loaded.cleanUp()
          loaded.close()
          throw CancellationError()
        }
      }
      loadTask = started
      return started
    }
    guard let task else { return }
    do {
      try await task.value
    } catch {
      // Only this load's own memo is forgotten. A `cancelLoad` or a later `load` may already have
      // put a different task there, and clearing that one would strand it.
      queue.sync { if loadTask == task { loadTask = nil } }
      throw error
    }
  }

  func cancelLoad() {
    queue.sync {
      loadTask?.cancel()
      loadTask = nil
    }
  }

  private func makeModel(onProgress: @escaping @Sendable (Double) -> Void) async throws -> any LoadedLanguageModel {
    if let modelFactory { return try await modelFactory(onProgress) }
    // The last chance to stop before the SDK claims the network: everything past this line is a
    // transfer that runs to completion whatever the app does.
    try Task.checkCancellation()
    return try await ZeticMLangeLLMModel(
      personalKey: personalKey,
      name: modelName,
      version: version,
      modelMode: .RUN_AUTO,
      // A completed background job owns the SDK cache. Keep it intact while the foreground model
      // initializes, rather than letting the initializer replace or discard its artifacts.
      cacheHandlingPolicy: .KEEP_EXISTING,
      initOption: LLMInitOption(nCtx: contextSize),
      onDownload: { progress in onProgress(Double(progress)) }
    )
  }

  func translate(prompt: String) async throws -> String {
    let cancellation = GenerationCancellation()
    let tokenLimit = maxOutputTokens
    return try await withTaskCancellationHandler(operation: {
      try await withCheckedThrowingContinuation { continuation in
        // The model is read through `self` and then held on its own: a generation runs for seconds,
        // and a block that kept the runtime alive for all of it would be the block holding the last
        // strong reference when `deinit` runs.
        queue.async { [weak self] in
          guard let model = self?.model else {
            continuation.resume(throwing: TranslationRuntimeError.modelNotLoaded)
            return
          }
          defer { try? model.cleanUp() }
          do {
            try cancellation.check()
            try model.run(prompt)
            var tokenCount = 0
            var accumulator = TranslationResponseAccumulator()
            while true {
              try cancellation.check()
              let result = model.waitForNextToken()
              tokenCount += 1
              if let tokenLimit, tokenCount > tokenLimit { throw SummaryError.invalidOutput }
              let hasToken = try accumulator.append(
                token: result.token, generatedTokens: result.generatedTokens, code: result.code
              )
              if !hasToken || result.isFinished { break }
            }
            continuation.resume(returning: try accumulator.finalOutput())
          } catch {
            continuation.resume(throwing: error)
          }
        }
      }
    }, onCancel: { cancellation.cancel() })
  }

  /// Unloads the model and disowns any load still in flight. Both halves matter: without the
  /// second, a close during a load frees nothing and then installs a 1.9 GB model into a runtime
  /// nobody wants, and the finished memo makes the next `load` report success with no model.
  func close() async {
    await withCheckedContinuation { continuation in
      queue.async { [weak self] in
        guard let self else { return continuation.resume() }
        closeGeneration += 1
        loadTask?.cancel()
        loadTask = nil
        let model = self.model
        self.model = nil
        setResident(false)
        try? model?.cleanUp()
        model?.close()
        continuation.resume()
      }
    }
  }

  private func setResident(_ resident: Bool) {
    residency.lock()
    residentModel = resident
    residency.unlock()
  }

  /// Hands a model to `queue` to be released and returns immediately.
  ///
  /// The waiting version of this is a deadlock: `deinit` runs precisely when the last strong
  /// reference goes away, and if the block that dropped it is itself running on `queue`, a
  /// `queue.sync` from inside `deinit` waits for a block that is waiting for `deinit`.
  static func release(_ model: (any LoadedLanguageModel)?, on queue: DispatchQueue) {
    guard let model else { return }
    queue.async {
      try? model.cleanUp()
      model.close()
    }
  }

  deinit {
    // Reading the stored properties directly is safe here and only here: every queue block holds
    // `self` weakly, and a weak reference cannot be upgraded once deallocation has begun.
    loadTask?.cancel()
    let model = self.model
    self.model = nil
    Self.release(model, on: queue)
  }
}

private final class GenerationCancellation: @unchecked Sendable {
  private let lock = NSLock()
  private var cancelled = false
  func cancel() { lock.lock(); cancelled = true; lock.unlock() }
  func check() throws { lock.lock(); defer { lock.unlock() }; if cancelled { throw CancellationError() } }
}
