import Foundation

struct ModelCompatibilityGate {
  static let segmentationModel = "ajayshah/pyannote-segmentation-3.0"
  static let encoderModel = "realtonypark/Moonshine-Streaming-ASR-Encoder"
  static let decoderModel = "realtonypark/Moonshine-Streaming-ASR-Decoder"
  static let translationModel = "SJ_zetic/Hy-MT2-1.8B"

  func error(for source: SpokenLanguage, target: TargetLanguage) -> String? {
    _ = source
    _ = target
    return "모델 호환성 검증이 완료되지 않았습니다. 실제 iOS 기기에서 "
      + "\(Self.segmentationModel), \(Self.encoderModel), \(Self.decoderModel), "
      + "\(Self.translationModel)의 아티팩트와 언어 조합을 검증한 뒤 시작할 수 있습니다."
  }
}
