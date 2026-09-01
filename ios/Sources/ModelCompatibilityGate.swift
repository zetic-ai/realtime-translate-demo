import Foundation

struct ModelCompatibilityGate {
  static let translationModel = "SJ_zetic/Hy-MT2-1.8B"

  func translationError(for source: SpokenLanguage, target: TargetLanguage) -> String? {
    _ = source
    _ = target
    return "\(Self.translationModel) 런타임과 아티팩트 호환성 검증이 완료되지 않아 번역할 수 없습니다."
  }
}
