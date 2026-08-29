import Foundation

/// The pipeline boundary is intentionally blocked until the iOS artifact and tensor contracts are verified.
/// It must not fall back to Apple Speech or a cloud service.
actor TranslationPipeline {
  private let gate = ModelCompatibilityGate()

  func start(source: SpokenLanguage, target: TargetLanguage) throws {
    if let reason = gate.error(for: source, target: target) {
      throw PipelineError.compatibilityRequired(reason)
    }
  }

  func stop() {}
}

enum PipelineError: LocalizedError {
  case compatibilityRequired(String)

  var errorDescription: String? {
    switch self {
    case let .compatibilityRequired(reason): reason
    }
  }
}
