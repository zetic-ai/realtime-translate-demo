import XCTest
import Speech
@testable import RealtimeTranslate

@MainActor
final class RealtimeTranslateTests: XCTestCase {
  func testHyMT2CandidateCatalogHas38Languages() {
    XCTAssertEqual(TargetLanguage.hyMT2Candidates.count, 38)
  }

  func testOnlyOneSpeakerCanListenAtATime() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    viewModel.beginTurn(.b)

    XCTAssertEqual(viewModel.state, .listening(.a))
    XCTAssertEqual(recognizer.startedSources, [.korean])
  }

  func testATurnRoutesTranslationToBLanguage() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    recognizer.sendPartial("안녕")
    XCTAssertEqual(viewModel.items.last?.speaker, .a)
    XCTAssertEqual(viewModel.items.last?.transcript, "안녕")
    recognizer.sendFinal("안녕하세요")
    XCTAssertEqual(viewModel.state, .listening(.a))
    XCTAssertEqual(recognizer.stopCount, 0)
    viewModel.endTurn(.a)

    XCTAssertEqual(viewModel.items.last?.targetLanguage, viewModel.targetLanguageB)
    XCTAssertEqual(viewModel.state, .ready)
  }

  func testBTurnRoutesTranslationToALanguage() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.b)
    recognizer.sendFinal("hello")
    XCTAssertEqual(viewModel.state, .listening(.b))
    viewModel.endTurn(.b)

    XCTAssertEqual(viewModel.items.last?.speaker, .b)
    XCTAssertEqual(viewModel.items.last?.targetLanguage, viewModel.targetLanguageA)
  }

  func testFinalBeforeReleaseDoesNotTranslateUntilRelease() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    recognizer.sendFinal("안녕하세요")
    XCTAssertEqual(viewModel.state, .listening(.a))
    XCTAssertEqual(recognizer.stopCount, 0)

    viewModel.endTurn(.a)
    XCTAssertEqual(recognizer.finishCount, 1)

    guard case .translationFailed = viewModel.items.last?.state else {
      return XCTFail("The source card must show a translation error after release")
    }
    XCTAssertNil(viewModel.items.last?.translation)
    XCTAssertEqual(viewModel.state, .ready)
    viewModel.beginTurn(.b)
    XCTAssertEqual(viewModel.state, .listening(.b))
  }

  func testPartialAfterFinalKeepsPendingFinalForRelease() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    recognizer.sendFinal("final transcript")
    recognizer.sendPartial("newer preview")
    XCTAssertEqual(viewModel.items.last?.transcript, "newer preview")
    XCTAssertEqual(viewModel.state, .listening(.a))

    viewModel.endTurn(.a)
    XCTAssertEqual(viewModel.items.last?.transcript, "final transcript")
    XCTAssertEqual(viewModel.state, .ready)
  }

  func testTapToggleStartsThenEndsSameSpeaker() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    viewModel.endTurn(.a)

    XCTAssertEqual(viewModel.state, .finalizing(.a))
  }

  func testOnDeviceSpeechRequestNeverAllowsNetworkFallback() {
    let request = SFSpeechAudioBufferRecognitionRequest()
    PlatformSpeechRecognizer.configure(request)
    XCTAssertTrue(request.requiresOnDeviceRecognition)
    XCTAssertTrue(request.shouldReportPartialResults)
  }

  private func readyViewModel(_ recognizer: FakeSpeechRecognizer) -> RealtimeTranslateViewModel {
    RealtimeTranslateViewModel(state: .ready, sourceLanguageA: .korean, sourceLanguageB: .english,
                               speechRecognizer: recognizer)
  }
}

@MainActor
private final class FakeSpeechRecognizer: SpeechRecognizing {
  private var onPartial: ((String) -> Void)?
  private var onFinal: ((String) -> Void)?
  private(set) var startedSources: [SpokenLanguage] = []
  private(set) var finishCount = 0
  private(set) var stopCount = 0

  func requestPermissions() async -> SpeechPermission { .granted }
  func capability(for language: SpokenLanguage) -> SpeechLanguageCapability { .available }
  func start(source: SpokenLanguage, onPartial: @escaping (String) -> Void,
             onFinal: @escaping (String) -> Void) throws {
    startedSources.append(source)
    self.onPartial = onPartial
    self.onFinal = onFinal
  }
  func finish() { finishCount += 1 }
  func stop() { stopCount += 1 }
  func sendPartial(_ transcript: String) { onPartial?(transcript) }
  func sendFinal(_ transcript: String) { onFinal?(transcript) }
}
