import Foundation

struct ModelCompatibilityGate {
  static let segmentationModel = "ajayshah/pyannote-segmentation-3.0"
  static let translationModel = "SJ_zetic/Hy-MT2-1.8B"

  func translationError(for source: SpokenLanguage, target: TargetLanguage) -> String? {
    _ = source
    _ = target
    return "화자 분리와 번역 모델 호환성 검증이 완료되지 않았습니다. 실제 iOS 기기에서 "
      + "\(Self.segmentationModel), \(Self.translationModel)의 아티팩트와 언어 조합을 검증한 뒤 번역할 수 있습니다."
  }
}
