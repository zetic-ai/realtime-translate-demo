import XCTest

final class RealtimeTranslateUITests: XCTestCase {
  func testListeningAShowsPTTAndDisablesB() {
    let app = launch(state: "listeningA")
    XCTAssertTrue(app.staticTexts["A is speaking"].exists)
    XCTAssertTrue(app.buttons["End A Turn"].exists)
    XCTAssertFalse(app.buttons["Start B Turn"].isEnabled)
  }

  func testFinalizingShowsActiveSourceCard() {
    let app = launch(state: "finalizingA")
    XCTAssertTrue(app.staticTexts["Finalizing A's transcript"].exists)
    XCTAssertTrue(app.staticTexts["Hello."].exists)
  }

  func testTranslationErrorKeepsCardAndEnablesNextTurn() {
    let app = launch(state: "translationError")
    XCTAssertTrue(app.staticTexts["Hello."].exists)
    XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "Hy-MT2")).firstMatch.exists)
    XCTAssertTrue(app.buttons["Start A Turn"].isEnabled)
    XCTAssertTrue(app.buttons["Start B Turn"].isEnabled)
  }

  func testEndedStateOffersNewSession() {
    let app = launch(state: "ended")
    XCTAssertTrue(app.buttons["Start New Session"].exists)
  }

  func testReadyControlsRemainHittableAboveTheHomeIndicator() {
    let app = launch(state: "ready")
    let endSession = app.buttons["End Session"]

    XCTAssertTrue(endSession.isHittable)
    XCTAssertLessThan(endSession.frame.maxY, app.frame.maxY)
  }

  private func launch(state: String) -> XCUIApplication {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", state]
    app.launch()
    return app
  }
}
