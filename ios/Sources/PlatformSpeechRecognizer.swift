import AVFAudio
import Foundation
import Speech

enum SpeechPermission: Equatable {
  case granted
  case required
}

enum SpeechLanguageCapability: Equatable {
  case available
  case unavailable(String)
}

enum PlatformSpeechError: LocalizedError {
  case microphonePermissionRequired
  case speechPermissionRequired
  case unsupportedLanguage(String)
  case unavailable(String)

  var errorDescription: String? {
    switch self {
    case .microphonePermissionRequired: "마이크 권한이 필요합니다."
    case .speechPermissionRequired: "음성 인식 권한이 필요합니다."
    case let .unsupportedLanguage(reason), let .unavailable(reason): reason
    }
  }
}

@MainActor
protocol SpeechRecognizing: AnyObject {
  func requestPermissions() async -> SpeechPermission
  func capability(for language: SpokenLanguage) -> SpeechLanguageCapability
  func start(
    source: SpokenLanguage,
    onPartial: @escaping (String) -> Void,
    onFinal: @escaping (String) -> Void
  ) throws
  func stop()
}

@MainActor
final class PlatformSpeechRecognizer: NSObject, SpeechRecognizing {
  private var audioEngine: AVAudioEngine?
  private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
  private var recognitionTask: SFSpeechRecognitionTask?

  func requestPermissions() async -> SpeechPermission {
    let microphoneGranted = await requestMicrophonePermission()
    let speechGranted = await requestSpeechPermission()
    return microphoneGranted && speechGranted ? .granted : .required
  }

  func capability(for language: SpokenLanguage) -> SpeechLanguageCapability {
    guard let recognizer = SFSpeechRecognizer(locale: language.locale) else {
      return .unavailable("이 기기에서는 \(language.rawValue) 음성 인식을 사용할 수 없습니다.")
    }
    guard recognizer.isAvailable else {
      return .unavailable("\(language.rawValue) 음성 인식 서비스를 사용할 수 없습니다.")
    }
    guard recognizer.supportsOnDeviceRecognition else {
      return .unavailable("\(language.rawValue)의 온디바이스 음성 인식 모델이 설치되어 있지 않습니다.")
    }
    return .available
  }

  func start(
    source: SpokenLanguage,
    onPartial: @escaping (String) -> Void,
    onFinal: @escaping (String) -> Void
  ) throws {
    stop()
    switch capability(for: source) {
    case .available:
      break
    case let .unavailable(reason):
      throw PlatformSpeechError.unsupportedLanguage(reason)
    }

    let session = AVAudioSession.sharedInstance()
    do {
      try session.setCategory(.record, mode: .measurement, options: [])
      try session.setActive(true, options: .notifyOthersOnDeactivation)
    } catch {
      throw PlatformSpeechError.unavailable("마이크를 시작할 수 없습니다: \(error.localizedDescription)")
    }

    guard let recognizer = SFSpeechRecognizer(locale: source.locale) else {
      throw PlatformSpeechError.unsupportedLanguage("이 기기에서는 \(source.rawValue) 음성 인식을 사용할 수 없습니다.")
    }
    let request = SFSpeechAudioBufferRecognitionRequest()
    Self.configure(request)
    let engine = AVAudioEngine()
    let inputNode = engine.inputNode
    let inputFormat = inputNode.outputFormat(forBus: 0)
    inputNode.installTap(onBus: 0, bufferSize: 1_024, format: inputFormat) { buffer, _ in
      request.append(buffer)
    }

    recognitionTask = recognizer.recognitionTask(with: request) { result, error in
      if let result {
        let transcript = result.bestTranscription.formattedString
        if result.isFinal {
          onFinal(transcript)
        } else {
          onPartial(transcript)
        }
      }
      if let error, !Self.isCancellation(error) {
        onFinal("")
      }
    }
    audioEngine = engine
    recognitionRequest = request
    do {
      engine.prepare()
      try engine.start()
    } catch {
      stop()
      throw PlatformSpeechError.unavailable("마이크를 시작할 수 없습니다: \(error.localizedDescription)")
    }
  }

  func stop() {
    audioEngine?.inputNode.removeTap(onBus: 0)
    audioEngine?.stop()
    recognitionRequest?.endAudio()
    recognitionTask?.cancel()
    recognitionTask = nil
    recognitionRequest = nil
    audioEngine = nil
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
  }

  private func requestMicrophonePermission() async -> Bool {
    await withCheckedContinuation { continuation in
      AVAudioSession.sharedInstance().requestRecordPermission { granted in
        continuation.resume(returning: granted)
      }
    }
  }

  private func requestSpeechPermission() async -> Bool {
    await withCheckedContinuation { continuation in
      SFSpeechRecognizer.requestAuthorization { status in
        continuation.resume(returning: status == .authorized)
      }
    }
  }

  private static func isCancellation(_ error: Error) -> Bool {
    (error as NSError).code == 216
  }

  static func configure(_ request: SFSpeechAudioBufferRecognitionRequest) {
    request.requiresOnDeviceRecognition = true
    request.shouldReportPartialResults = true
  }
}

private extension SpokenLanguage {
  var locale: Locale {
    switch self {
    case .korean: Locale(identifier: "ko-KR")
    case .chinese: Locale(identifier: "zh-CN")
    case .japanese: Locale(identifier: "ja-JP")
    case .english: Locale(identifier: "en-US")
    case .french: Locale(identifier: "fr-FR")
    case .spanish: Locale(identifier: "es-ES")
    }
  }
}
