import AVFAudio
import Foundation
import UIKit

@MainActor
final class RealtimeTranslateViewModel: ObservableObject {
  @Published private(set) var state: SessionState
  @Published var sourceLanguage: SpokenLanguage
  @Published var targetLanguage: TargetLanguage
  @Published private(set) var items: [ConversationItem]
  @Published private(set) var availableSourceLanguages: [SpokenLanguage]

  private let speechRecognizer: any SpeechRecognizing
  private let modelGate = ModelCompatibilityGate()

  init(
    state: SessionState = .permissionRequired,
    sourceLanguage: SpokenLanguage = .korean,
    targetLanguage: TargetLanguage = .hyMT2Candidates[0],
    items: [ConversationItem] = [],
    speechRecognizer: (any SpeechRecognizing)? = nil
  ) {
    let resolvedSpeechRecognizer = speechRecognizer ?? PlatformSpeechRecognizer()
    let supportedLanguages = SpokenLanguage.allCases.filter {
      if case .available = resolvedSpeechRecognizer.capability(for: $0) { return true }
      return false
    }
    self.state = state
    self.sourceLanguage = supportedLanguages.contains(sourceLanguage)
      ? sourceLanguage
      : supportedLanguages.first ?? sourceLanguage
    self.targetLanguage = targetLanguage
    self.items = items
    self.speechRecognizer = resolvedSpeechRecognizer
    self.availableSourceLanguages = supportedLanguages
  }

  static func fromLaunchArguments() -> RealtimeTranslateViewModel {
    let arguments = ProcessInfo.processInfo.arguments
    if arguments.contains("-uiState") {
      let value = arguments.drop { $0 != "-uiState" }.dropFirst().first
      if value == "recording" {
        return RealtimeTranslateViewModel(state: .recording, items: Self.previewItems)
      }
      if value == "finished" {
        return RealtimeTranslateViewModel(state: .finished, items: Self.previewItems)
      }
      if value == "processing" {
        return RealtimeTranslateViewModel(state: .processing, items: Self.previewItems)
      }
      if value == "error" {
        return RealtimeTranslateViewModel(state: .error("모델 호환성 검증이 완료되지 않았습니다."))
      }
      if value == "ready" {
        return RealtimeTranslateViewModel(state: .ready)
      }
      if value == "permissionRequired" {
        return RealtimeTranslateViewModel(state: .permissionRequired)
      }
    }
    return RealtimeTranslateViewModel()
  }

  func requestMicrophonePermission() {
    Task {
      let permission = await speechRecognizer.requestPermissions()
      refreshAvailableLanguages()
      state = permission == .granted ? .ready : .permissionRequired
    }
  }

  func openAppSettings() {
    guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else { return }
    UIApplication.shared.open(settingsURL)
  }

  func start() {
    guard availableSourceLanguages.contains(sourceLanguage) else {
      state = .error("\(sourceLanguage.rawValue)의 온디바이스 음성 인식 모델을 사용할 수 없습니다.")
      return
    }
    do {
      try speechRecognizer.start(
        source: sourceLanguage,
        onPartial: { [weak self] transcript in self?.receivePartial(transcript) },
        onFinal: { [weak self] transcript in self?.receiveFinal(transcript) }
      )
      state = .recording
    } catch {
      speechRecognizer.stop()
      state = .error(error.localizedDescription)
    }
  }

  func stop() {
    state = .processing
    speechRecognizer.stop()
    state = .finished
  }

  func beginNewSession() {
    speechRecognizer.stop()
    items = []
    state = .ready
  }

  private func refreshAvailableLanguages() {
    availableSourceLanguages = SpokenLanguage.allCases.filter {
      if case .available = speechRecognizer.capability(for: $0) { return true }
      return false
    }
    if !availableSourceLanguages.contains(sourceLanguage), let first = availableSourceLanguages.first {
      sourceLanguage = first
    }
  }

  private func receivePartial(_ transcript: String) {
    guard !transcript.isEmpty else { return }
    if let last = items.last, last.state == .processing {
      items[items.count - 1] = ConversationItem(
        id: last.id, speaker: nil, transcript: transcript, translation: nil, state: .processing
      )
    } else {
      items.append(
        ConversationItem(id: UUID(), speaker: nil, transcript: transcript, translation: nil, state: .processing)
      )
    }
  }

  private func receiveFinal(_ transcript: String) {
    guard !transcript.isEmpty else {
      speechRecognizer.stop()
      state = .error("온디바이스 음성 인식 결과를 받을 수 없습니다. 언어 모델을 확인한 뒤 다시 시도해 주세요.")
      return
    }
    if let last = items.last, last.state == .processing {
      items[items.count - 1] = ConversationItem(
        id: last.id, speaker: nil, transcript: transcript, translation: nil, state: .confirmed
      )
    } else {
      items.append(
        ConversationItem(id: UUID(), speaker: nil, transcript: transcript, translation: nil, state: .confirmed)
      )
    }
    if let reason = modelGate.translationError(for: sourceLanguage, target: targetLanguage) {
      speechRecognizer.stop()
      state = .error(reason)
    }
  }

  private static let previewItems = [
    ConversationItem(id: UUID(), speaker: "화자 1", transcript: "안녕하세요.", translation: "Hello.", state: .confirmed),
    ConversationItem(id: UUID(), speaker: nil, transcript: "반갑습니다", translation: nil, state: .processing)
  ]
}
