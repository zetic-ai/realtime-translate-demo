import XCTest
@testable import RealtimeTranslate

@MainActor final class SessionHistoryTests: XCTestCase {
  private func turn(_ text: String = "We agreed to meet Friday.") -> SavedTurn {
    SavedTurn(id: "turn-1", speaker: "A", original: text, translation: "Vendredi.", sourceLanguage: "en", targetLanguage: "fr")
  }
  func testHistoryRoundTripRenameAndDelete() throws {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: dir) }
    let store = SessionFileStore(directory: dir)
    let history = SessionHistory(store: store)
    history.checkpoint([turn()])
    XCTAssertTrue(history.savedSessions.isEmpty)
    XCTAssertEqual(try store.load().first?.turns, [turn()])
    let id = try XCTUnwrap(history.checkpoint([turn()], ending: true))
    XCTAssertEqual(history.savedSessions.count, 1)
    history.rename(id, title: "Meeting")
    let restored = try XCTUnwrap(SessionHistory(store: store).sessions.first)
    XCTAssertEqual(restored.title, "Meeting")
    XCTAssertEqual(restored.turns, [turn()])
    XCTAssertNotNil(restored.endedAt)
    XCTAssertEqual(try dir.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    history.delete(id)
    XCTAssertTrue(try store.load().isEmpty)
  }
  func testInterruptedSessionRecovery() throws {
    let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: dir) }
    let store = SessionFileStore(directory: dir)
    let history = SessionHistory(store: store)
    history.checkpoint([turn()])
    let restored = try XCTUnwrap(SessionHistory(store: store).sessions.first)
    XCTAssertNil(restored.endedAt)
    XCTAssertEqual(SessionHistory(store: store).savedSessions.count, 1)
    XCTAssertEqual(restored.turns, [turn()])
  }
  func testFailedSaveDoesNotReportSuccess() throws {
    let file = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: file) }
    try Data("blocked".utf8).write(to: file)
    let history = SessionHistory(store: SessionFileStore(directory: file.appendingPathComponent("child")))
    XCTAssertNil(history.checkpoint([turn()], ending: true))
    XCTAssertTrue(history.storageFailed)
  }
}
