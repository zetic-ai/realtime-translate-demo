import XCTest

final class RealtimeTranslateUITests: XCTestCase {
  func testRecordingStateShowsConversationAndStopControl() {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", "recording"]
    app.launch()

    XCTAssertTrue(app.staticTexts["녹음 중"].exists)
    XCTAssertTrue(app.buttons["녹음 종료"].exists)
    XCTAssertTrue(app.staticTexts["화자 1"].exists)
    XCTAssertTrue(app.staticTexts["Hello."].exists)
  }

  func testErrorStateShowsRetry() {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", "error"]
    app.launch()

    XCTAssertTrue(app.buttons["다시 시도"].exists)
  }

  func testPermissionStateHidesStartAndShowsRecoveryControls() {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", "permissionRequired"]
    app.launch()

    XCTAssertFalse(app.buttons["번역 시작"].exists)
    XCTAssertTrue(app.buttons["마이크 권한 허용"].exists)
    XCTAssertTrue(app.buttons["앱 설정 열기"].exists)
  }

  func testFinishedStateOffersNewSession() {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", "finished"]
    app.launch()

    XCTAssertTrue(app.buttons["새 세션 시작"].exists)
  }

  func testProcessingStateShowsPartialTranscript() {
    let app = XCUIApplication()
    app.launchArguments = ["-uiState", "processing"]
    app.launch()

    XCTAssertTrue(app.staticTexts["남은 발화를 처리하는 중"].exists)
    XCTAssertTrue(app.staticTexts["반갑습니다"].exists)
    XCTAssertTrue(app.staticTexts["번역을 준비하는 중"].exists)
  }
}
