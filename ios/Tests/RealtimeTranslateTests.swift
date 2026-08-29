import XCTest
@testable import RealtimeTranslate

final class RealtimeTranslateTests: XCTestCase {
  func testHyMT2CandidateCatalogHas38Languages() {
    XCTAssertEqual(TargetLanguage.hyMT2Candidates.count, 38)
  }

  func testCompatibilityGateBlocksUnverifiedPair() {
    let gate = ModelCompatibilityGate()
    XCTAssertNotNil(gate.error(for: .korean, target: TargetLanguage.hyMT2Candidates[0]))
  }
}
