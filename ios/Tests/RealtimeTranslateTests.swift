import XCTest
import Speech
@testable import RealtimeTranslate

@MainActor
final class RealtimeTranslateTests: XCTestCase {
  func testHyMT2CandidateCatalogHas38Languages() {
    XCTAssertEqual(TargetLanguage.hyMT2Candidates.count, 38)
    XCTAssertEqual(TargetLanguage.hyMT2Candidates.first { $0.code == "tl" }?.name, "Filipino")
  }

  func testHyMT2RequestUsesOneUserPromptAndFullTargetLanguageName() {
    let request = HyMT2Request(sourceText: "Good morning", targetLanguage: .hyMT2Candidates[2])

    let expected = "Translate the following text into French. "
      + "Note that you should only output the translated result without any additional explanation:\nGood morning"
    XCTAssertEqual(request.userMessage, expected)
  }

  func testCompletedTurnBuildsTheOfficialHyMT2RequestBeforeReportingRuntimeUnavailable() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)
    viewModel.targetLanguageB = .hyMT2Candidates[2]

    viewModel.beginTurn(.a)
    recognizer.sendFinal("Good morning")
    viewModel.endTurn(.a)

    XCTAssertEqual(
      viewModel.mostRecentTranslationRequest,
      HyMT2Request(sourceText: "Good morning", targetLanguage: .hyMT2Candidates[2])
    )
    guard case .translationFailed = viewModel.items.last?.state else {
      return XCTFail("A missing runtime must not produce a translation.")
    }
  }

  func testSourceLanguagesStartWithAutomaticThenUsePlatformLocales() {
    let recognizer = FakeSpeechRecognizer()
    recognizer.sourceLanguages = [SpeechSourceLanguage(identifier: "fr-FR", name: "French (France)")]

    let viewModel = RealtimeTranslateViewModel(state: .ready, speechRecognizer: recognizer)

    XCTAssertEqual(viewModel.sourceLanguageA, .automatic)
    XCTAssertEqual(viewModel.sourceLanguageB, .automatic)
    XCTAssertEqual(viewModel.availableSourceLanguages, [.automatic, recognizer.sourceLanguages[0]])
  }

  func testOnlyOneSpeakerCanListenAtATime() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    viewModel.beginTurn(.b)

    XCTAssertEqual(viewModel.state, .listening(.a))
    XCTAssertEqual(recognizer.startedSources, [.automatic])
  }

  func testATurnRoutesTranslationToBLanguage() {
    let recognizer = FakeSpeechRecognizer()
    let viewModel = readyViewModel(recognizer)

    viewModel.beginTurn(.a)
    recognizer.sendPartial("Hello")
    XCTAssertEqual(viewModel.items.last?.speaker, .a)
    XCTAssertEqual(viewModel.items.last?.transcript, "Hello")
    recognizer.sendFinal("Hello there")
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
    recognizer.sendFinal("Hello there")
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
    RealtimeTranslateViewModel(state: .ready, speechRecognizer: recognizer)
  }
}

@MainActor
private final class FakeSpeechRecognizer: SpeechRecognizing {
  private var onPartial: ((String) -> Void)?
  private var onFinal: ((String) -> Void)?
  var sourceLanguages: [SpeechSourceLanguage] = []
  private(set) var startedSources: [SpeechSourceLanguage] = []
  private(set) var finishCount = 0
  private(set) var stopCount = 0

  func requestPermissions() async -> SpeechPermission { .granted }
  func availableSourceLanguages() -> [SpeechSourceLanguage] { sourceLanguages }
  func start(source: SpeechSourceLanguage, onPartial: @escaping (String) -> Void,
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
