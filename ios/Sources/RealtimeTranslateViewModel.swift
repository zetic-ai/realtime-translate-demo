import AVFAudio
import Foundation
import UIKit

@MainActor
final class RealtimeTranslateViewModel: ObservableObject {
  @Published private(set) var state: SessionState
  @Published var sourceLanguage: SpokenLanguage
  @Published var targetLanguage: TargetLanguage
  @Published private(set) var items: [ConversationItem]

  private let pipeline = TranslationPipeline()

  init(
    state: SessionState = .permissionRequired,
    sourceLanguage: SpokenLanguage = .korean,
    targetLanguage: TargetLanguage = .hyMT2Candidates[0],
    items: [ConversationItem] = []
  ) {
    self.state = state
    self.sourceLanguage = sourceLanguage
    self.targetLanguage = targetLanguage
    self.items = items
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
    AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
      DispatchQueue.main.async {
        self?.state = granted ? .ready : .permissionRequired
      }
    }
  }

  func openAppSettings() {
    guard let settingsURL = URL(string: UIApplication.openSettingsURLString) else { return }
    UIApplication.shared.open(settingsURL)
  }

  func start() {
    Task {
      do {
        try await pipeline.start(source: sourceLanguage, target: targetLanguage)
        state = .recording
      } catch {
        state = .error(error.localizedDescription)
      }
    }
  }

  func stop() {
    state = .processing
    Task {
      await pipeline.stop()
      state = .finished
    }
  }

  func beginNewSession() {
    items = []
    state = .ready
  }

  private static let previewItems = [
    ConversationItem(id: UUID(), speaker: "화자 1", transcript: "안녕하세요.", translation: "Hello.", state: .confirmed),
    ConversationItem(id: UUID(), speaker: nil, transcript: "반갑습니다", translation: nil, state: .processing)
  ]
}
