import XCTest
import Speech
@testable import RealtimeTranslate

@MainActor
final class RealtimeTranslateTests: XCTestCase {
  func testHyMT2CandidateCatalogHas38Languages() {
    XCTAssertEqual(TargetLanguage.hyMT2Candidates.count, 38)
  }

  func testTranslationGateBlocksUnverifiedPairWithoutCreatingTranslation() {
    let gate = ModelCompatibilityGate()
    XCTAssertNotNil(gate.translationError(for: .korean, target: TargetLanguage.hyMT2Candidates[0]))
  }

  func testOnlyOnDeviceSupportedLocalesAreEnabled() {
    let recognizer = FakeSpeechRecognizer(availableLanguages: [.english])
    let viewModel = RealtimeTranslateViewModel(state: .ready, speechRecognizer: recognizer)

    XCTAssertEqual(viewModel.availableSourceLanguages, [.english])
  }

  func testPermissionRequestRequiresMicrophoneAndSpeechAuthorization() async {
    let recognizer = FakeSpeechRecognizer(permission: .required)
    let viewModel = RealtimeTranslateViewModel(speechRecognizer: recognizer)

    viewModel.requestMicrophonePermission()
    await Task.yield()

    XCTAssertEqual(viewModel.state, .permissionRequired)
    XCTAssertTrue(recognizer.didRequestPermissions)
  }

  func testOnDeviceSpeechRequestNeverAllowsNetworkFallback() {
    let request = SFSpeechAudioBufferRecognitionRequest()

    PlatformSpeechRecognizer.configure(request)

    XCTAssertTrue(request.requiresOnDeviceRecognition)
    XCTAssertTrue(request.shouldReportPartialResults)
  }

  func testPartialAndFinalResultsUpdateChatWithoutFakeTranslation() {
    let recognizer = FakeSpeechRecognizer(availableLanguages: [.english])
    let viewModel = RealtimeTranslateViewModel(
      state: .ready,
      sourceLanguage: .english,
      speechRecognizer: recognizer
    )

    viewModel.start()
    recognizer.sendPartial("hello")
    XCTAssertEqual(viewModel.items.last?.transcript, "hello")
    XCTAssertEqual(viewModel.items.last?.state, .processing)

    recognizer.sendFinal("hello world")
    XCTAssertEqual(viewModel.items.last?.transcript, "hello world")
    XCTAssertEqual(viewModel.items.last?.state, .confirmed)
    XCTAssertNil(viewModel.items.last?.translation)
    XCTAssertEqual(recognizer.stopCount, 1)
    guard case .error = viewModel.state else {
      return XCTFail("Unverified translation must remain in the error state")
    }
  }

  func testEmptyFinalAndNewSessionStopRecognizer() {
    let recognizer = FakeSpeechRecognizer(availableLanguages: [.english])
    let viewModel = RealtimeTranslateViewModel(
      state: .ready,
      sourceLanguage: .english,
      speechRecognizer: recognizer
    )

    viewModel.start()
    recognizer.sendFinal("")
    XCTAssertEqual(recognizer.stopCount, 1)

    viewModel.beginNewSession()
    XCTAssertEqual(recognizer.stopCount, 2)
  }
}

@MainActor
private final class FakeSpeechRecognizer: SpeechRecognizing {
  private let availableLanguages: Set<SpokenLanguage>
  private let permission: SpeechPermission
  private var onPartial: ((String) -> Void)?
  private var onFinal: ((String) -> Void)?
  private(set) var didRequestPermissions = false
  private(set) var stopCount = 0

  init(
    permission: SpeechPermission = .granted,
    availableLanguages: Set<SpokenLanguage> = Set(SpokenLanguage.allCases)
  ) {
    self.permission = permission
    self.availableLanguages = availableLanguages
  }

  func requestPermissions() async -> SpeechPermission {
    didRequestPermissions = true
    return permission
  }

  func capability(for language: SpokenLanguage) -> SpeechLanguageCapability {
    availableLanguages.contains(language) ? .available : .unavailable("온디바이스 모델 없음")
  }

  func start(
    source: SpokenLanguage,
    onPartial: @escaping (String) -> Void,
    onFinal: @escaping (String) -> Void
  ) throws {
    self.onPartial = onPartial
    self.onFinal = onFinal
  }

  func stop() { stopCount += 1 }

  func sendPartial(_ transcript: String) { onPartial?(transcript) }
  func sendFinal(_ transcript: String) { onFinal?(transcript) }
}
