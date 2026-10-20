import XCTest
import ZeticMLange
@testable import RealtimeTranslate

@MainActor
final class DeviceRuntimeTests: XCTestCase {
  func testSDKBackgroundDownloadReportsModelInstalled() async throws {
    try XCTSkipUnless(ProcessInfo.processInfo.environment["RELAIS_SDK_CACHE_TESTS"] == "1",
                      "Set RELAIS_SDK_CACHE_TESTS=1 to check the SDK-managed model cache.")
    let personalKey = MelangeCredential.value(from: Bundle.main.infoDictionary ?? [:])
    try XCTSkipUnless(!personalKey.isEmpty, "The app build needs a Melange personal key for SDK downloads.")

    let handle = try await ZeticMLangeLLMModel.downloadInBackground(
      personalKey: personalKey,
      name: FirstRunModel.modelName,
      cacheHandlingPolicy: .KEEP_EXISTING
    )
    let handleAttachment = XCTAttachment(string: "Background download handle: \(handle.id)")
    handleAttachment.name = "sdk-background-download-handle"
    handleAttachment.lifetime = .keepAlways
    add(handleAttachment)

    func attach(_ status: BackgroundDownloadStatus) {
      let text = """
        handle: \(status.handle.id)
        state: \(status.state.rawValue)
        bytesDownloaded: \(status.bytesDownloaded)
        totalBytes: \(status.totalBytes.map { String($0) } ?? "unknown")
        errorMessagePresent: \(status.errorMessage != nil)
        """
      let attachment = XCTAttachment(string: text)
      attachment.name = "sdk-background-download-status"
      attachment.lifetime = .keepAlways
      add(attachment)
    }

    let deadline = Date().addingTimeInterval(120)
    var status = try await ZeticMLangeLLMModel.getBackgroundDownloadStatus(handle)
    while status.state != .installed {
      if status.state == .failed || status.state == .stopped {
        attach(status)
        XCTFail("SDK background model status ended at \(status.state.rawValue).")
        return
      }
      if Date() >= deadline {
        attach(status)
        XCTFail("SDK background model did not reach installed status within 120 seconds.")
        return
      }
      try await Task.sleep(for: .seconds(2))
      status = try await ZeticMLangeLLMModel.getBackgroundDownloadStatus(handle)
    }
    attach(status)
    XCTAssertEqual(status.state, .installed)
  }

  func testDeviceRealTranslationAndSummary() async throws {
    try XCTSkipUnless(ProcessInfo.processInfo.environment["RELAIS_REAL_MODEL_TESTS"] == "1",
                      "Set RELAIS_REAL_MODEL_TESTS=1 to download and run real models on device.")
    executionTimeAllowance = 1200
    let translation = MelangeTranslationRuntime()
    let summary = MelangeTranslationRuntime(modelName: SummaryEngine.modelName, version: 1,
                                           maxOutputTokens: 2048, contextSize: 4096)
    var stage = "translation model load"
    let started = Date()
    func record(_ name: String, _ text: String) {
      NSLog("Device validation [%@]: %@", name, text)
      let attachment = XCTAttachment(string: "Elapsed: \(Date().timeIntervalSince(started)) seconds\n\(text)")
      attachment.name = name
      attachment.lifetime = .keepAlways
      add(attachment)
    }
    do {
      try await translation.load { _ in }
      record("translation-model-ready", "Real model loaded on physical iPhone")
      var turns: [SavedTurn] = []
      for (index, text) in ["Please meet me at the station at ten tomorrow morning.",
                            "I will bring the project report to our meeting."].enumerated() {
        stage = "translation generation \(index + 1)"
        let output = try await translation.translate(prompt: HyMT2Request(sourceText: text, targetLanguage: .hyMT2Candidates[9]).flatPrompt)
        record("translation-\(index + 1)", "Original: \(text)\nKorean: \(output)")
        XCTAssertTrue(output.unicodeScalars.contains { (0xAC00...0xD7A3).contains($0.value) })
        turns.append(SavedTurn(id: index == 0 ? "6168076D-8742-476C-A52D-B328CA6A33AB" : "3371C11B-0480-47C7-9039-0E596D976CD6", speaker: "A", original: text,
                               translation: output, sourceLanguage: "en", targetLanguage: "ko"))
      }
      await translation.close()
      stage = "summary model load"
      try await summary.load { _ in }
      record("summary-model-ready", "Real summary model loaded after translation release")
      let language = Locale.current.language.languageCode?.identifier ?? "en"
      var parsed = SessionSummary.empty
      for (index, chunk) in SummaryEngine.chunks(turns).enumerated() {
        var attempt = 0
        parsed.append(try await SummaryEngine.generate(chunk, language: language) { prompt in
          attempt += 1
          stage = "summary generation \(index + 1), attempt \(attempt)"
          record("summary-prompt-\(index + 1)-attempt-\(attempt)", "Locale: \(language)\n\(prompt)")
          let output = try await summary.translate(prompt: prompt)
          record("summary-output-\(index + 1)-attempt-\(attempt)", output)
          stage = "summary JSON and citation validation \(index + 1), attempt \(attempt)"
          return output
        })
      }
      XCTAssertFalse(parsed.points.isEmpty)
      XCTAssertFalse(parsed.points.contains { $0.text == "Concise fact from the transcript" },
                     "The model must summarize transcript facts rather than copy the output example.")
      await summary.close()
      record("runtime-result", "Translation and summary runtime checks completed")
    } catch {
      record("runtime-failure", "Stage: \(stage)\nError: \(error)")
      await translation.close()
      await summary.close()
      throw error
    }
  }

}
