import XCTest

final class RealtimeTranslateUITests: XCTestCase {
  func testListeningAShowsPTTAndDisablesB() {
    let app = launch(state: "listeningA")
    XCTAssertTrue(app.staticTexts["A가 말하는 중"].exists)
    XCTAssertTrue(app.buttons["A 발화 종료"].exists)
    XCTAssertFalse(app.buttons["B 발화 시작"].isEnabled)
  }

  func testFinalizingShowsActiveSourceCard() {
    let app = launch(state: "finalizingA")
    XCTAssertTrue(app.staticTexts["A 원문 확정 중"].exists)
    XCTAssertTrue(app.staticTexts["안녕하세요."].exists)
  }

  func testTranslationErrorKeepsCardAndEnablesNextTurn() {
    let app = launch(state: "translationError")
    XCTAssertTrue(app.staticTexts["Hello."].exists)
    XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Hy-MT2")).firstMatch.exists)
    XCTAssertTrue(app.buttons["A 발화 시작"].isEnabled)
    XCTAssertTrue(app.buttons["B 발화 시작"].isEnabled)
  }

  func testEndedStateOffersNewSession() {
    let app = launch(state: "ended")
    XCTAssertTrue(app.buttons["새 세션 시작"].exists)
  }

  private func launch(state: String) -> XCUIApplication {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", state]
    app.launch()
    return app
  }
}
