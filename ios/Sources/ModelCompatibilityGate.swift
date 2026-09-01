import Foundation

struct ModelCompatibilityGate {
  static let translationModel = "SJ_zetic/Hy-MT2-1.8B"

  func translationError(for source: SpokenLanguage, target: TargetLanguage) -> String? {
    _ = source
    _ = target
    return "Translation is unavailable because \(Self.translationModel) compatibility has not been verified."
  }
}
