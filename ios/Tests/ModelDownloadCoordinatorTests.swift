import XCTest
import ZeticMLange
@testable import RealtimeTranslate

@MainActor
final class ModelDownloadCoordinatorTests: XCTestCase {
  func testNoSDKWorkStartsWithoutConsentEvenWhenAnOldHandleExists() async throws {
    let defaults = try scratchDefaults()
    defaults.set("old-job", forKey: TranslationModelDownloadCoordinator.handleKey)
    let sdk = FakeDownloadSDK()
    let coordinator = makeCoordinator(defaults, sdk)

    coordinator.resumeIfConsented()
    XCTAssertFalse(coordinator.requestModelUse())
    await Task.yield()

    XCTAssertEqual(sdk.downloads, 0)
    XCTAssertEqual(sdk.queries, [])
    XCTAssertEqual(coordinator.phase, .idle)
  }

  func testConsentWithoutAHandleLetsSDKResolveLegacyCacheAndPersistsOnlyItsHandle() async throws {
    let defaults = try scratchDefaults()
    defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
    let sdk = FakeDownloadSDK()
    let coordinator = makeCoordinator(defaults, sdk)

    coordinator.resumeIfConsented()
    await expect(.installed, from: coordinator)

    XCTAssertEqual(sdk.downloads, 1)
    XCTAssertEqual(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey), "sdk-job")
    XCTAssertTrue(coordinator.requestModelUse())

    let relaunched = makeCoordinator(defaults, sdk)
    XCTAssertFalse(relaunched.requestModelUse(), "A persisted handle alone does not prove readiness")
    await expect(.installed, from: relaunched)
    XCTAssertTrue(relaunched.requestModelUse())
    XCTAssertEqual(sdk.downloads, 1, "A completed SDK job is resumed instead of downloaded again")
  }

  func testNonInstalledSDKStatesNeverAllowAForegroundLoad() async throws {
    for state: BackgroundDownloadState in [.queued, .resolving, .downloading, .verifying, .failed, .stopped] {
      let defaults = try scratchDefaults()
      defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
      defaults.set("sdk-job", forKey: TranslationModelDownloadCoordinator.handleKey)
      let sdk = FakeDownloadSDK()
      sdk.state = state
      let coordinator = makeCoordinator(defaults, sdk)
      coordinator.resumeIfConsented()
      await expect(sdk.phase, from: coordinator)

      XCTAssertFalse(coordinator.canUseDownloadedModel, "\(state) is not installed")
      XCTAssertFalse(coordinator.requestModelUse(), "\(state) must not race a foreground load")
    }
  }

  func testFailedAndStoppedJobsCanBeExplicitlyRetried() async throws {
    for state: BackgroundDownloadState in [.failed, .stopped] {
      let defaults = try scratchDefaults()
      defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
      defaults.set("old-job", forKey: TranslationModelDownloadCoordinator.handleKey)
      let sdk = FakeDownloadSDK()
      sdk.state = state
      let coordinator = makeCoordinator(defaults, sdk)
      coordinator.resumeIfConsented()
      await expect(sdk.phase, from: coordinator)
      XCTAssertNil(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey))
      XCTAssertEqual(sdk.downloads, 0, "Terminal failure must not create an automatic retry loop")

      sdk.state = .installed
      XCTAssertFalse(coordinator.requestModelUse())
      await expect(.installed, from: coordinator)
      XCTAssertEqual(sdk.downloads, 1)
      XCTAssertTrue(coordinator.hasDownloadConsent)
    }
  }

  func testLookupErrorKeepsOwnershipUntilExplicitRetryStopsTheOldSDKJob() async throws {
    let defaults = try scratchDefaults()
    defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
    defaults.set("stale-job", forKey: TranslationModelDownloadCoordinator.handleKey)
    let sdk = FakeDownloadSDK()
    sdk.lookupFails = true
    let coordinator = makeCoordinator(defaults, sdk)
    coordinator.resumeIfConsented()
    await expect(.failed("SDK lookup failed"), from: coordinator)
    XCTAssertEqual(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey), "stale-job")
    XCTAssertEqual(sdk.downloads, 0)

    sdk.lookupFails = false
    XCTAssertFalse(coordinator.requestModelUse())
    await expect(.installed, from: coordinator)
    XCTAssertEqual(sdk.stopped, ["stale-job"])
    XCTAssertEqual(sdk.downloads, 1)
    XCTAssertEqual(sdk.events, ["status:stale-job", "stop:stale-job", "download", "status:sdk-job"])
  }

  func testFailedStopDoesNotForgetOrDuplicateAJobOnRetry() async throws {
    let defaults = try scratchDefaults()
    defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
    defaults.set("stale-job", forKey: TranslationModelDownloadCoordinator.handleKey)
    let sdk = FakeDownloadSDK()
    sdk.lookupFails = true
    let coordinator = makeCoordinator(defaults, sdk)
    coordinator.resumeIfConsented()
    await expect(.failed("SDK lookup failed"), from: coordinator)

    sdk.stopFails = true
    XCTAssertFalse(coordinator.requestModelUse())
    await expect(.failed("SDK stop failed"), from: coordinator)
    XCTAssertEqual(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey), "stale-job")
    XCTAssertEqual(sdk.downloads, 0)
  }

  func testSDKRemovalClearsConsentAndHandleAndRequiresConsentAgain() async throws {
    let defaults = try scratchDefaults()
    defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
    defaults.set("sdk-job", forKey: TranslationModelDownloadCoordinator.handleKey)
    defaults.set("keep-conversation", forKey: "history.fixture")
    let sdk = FakeDownloadSDK()
    let coordinator = makeCoordinator(defaults, sdk)
    coordinator.resumeIfConsented()
    await expect(.installed, from: coordinator)

    let outcome = await coordinator.removeDownloadedModel()
    XCTAssertEqual(outcome, .removed)
    XCTAssertEqual(sdk.removals, 1)
    XCTAssertFalse(coordinator.hasDownloadConsent)
    XCTAssertNil(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey))
    XCTAssertEqual(defaults.string(forKey: "history.fixture"), "keep-conversation")
    XCTAssertFalse(coordinator.requestModelUse())
    XCTAssertEqual(sdk.downloads, 0)

    let firstRun = FirstRunModel(path: FixedNetworkPath(.unrestricted), hasPersonalKey: { true },
                                hasDownloadConsent: { coordinator.hasDownloadConsent })
    var starts = 0
    firstRun.requestSessionStart { starts += 1 }
    XCTAssertEqual(starts, 0)
    XCTAssertNotNil(firstRun.consent)
  }

  func testInstalledModelCannotStartWhileSDKRemovalIsSuspended() async throws {
    let defaults = try scratchDefaults()
    defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
    defaults.set("sdk-job", forKey: TranslationModelDownloadCoordinator.handleKey)
    let sdk = FakeDownloadSDK()
    let coordinator = makeCoordinator(defaults, sdk)
    coordinator.resumeIfConsented()
    await expect(.installed, from: coordinator)
    XCTAssertTrue(coordinator.requestModelUse())

    let removalStarted = expectation(description: "SDK removal is suspended")
    var finishRemoval: CheckedContinuation<Void, Never>?
    sdk.duringRemoval = {
      await withCheckedContinuation { continuation in
        finishRemoval = continuation
        removalStarted.fulfill()
      }
    }
    let removal = Task { await coordinator.removeDownloadedModel() }
    await fulfillment(of: [removalStarted], timeout: 2)

    XCTAssertTrue(coordinator.isRemoving)
    XCTAssertEqual(coordinator.phase, .installed, "The last SDK status can still be installed")
    XCTAssertFalse(coordinator.requestModelUse(), "Removal must block a new foreground load")
    coordinator.resumeIfConsented()
    await Task.yield()
    XCTAssertEqual(sdk.downloads, 0)
    XCTAssertEqual(sdk.removals, 1)

    finishRemoval?.resume()
    let outcome = await removal.value
    XCTAssertEqual(outcome, .removed)
    XCTAssertFalse(coordinator.isRemoving)
    XCTAssertFalse(coordinator.requestModelUse())
  }

  func testInUseAndFailedRemovalPreserveConsentAndResumeSDKStatusChecks() async throws {
    for fails in [false, true] {
      let defaults = try scratchDefaults()
      defaults.set(true, forKey: TranslationModelDownloadCoordinator.consentKey)
      defaults.set("sdk-job", forKey: TranslationModelDownloadCoordinator.handleKey)
      let sdk = FakeDownloadSDK()
      sdk.removalInUse = !fails
      sdk.removalFails = fails
      let coordinator = makeCoordinator(defaults, sdk)
      coordinator.resumeIfConsented()
      await expect(.installed, from: coordinator)

      let outcome = await coordinator.removeDownloadedModel()
      XCTAssertEqual(outcome, fails ? .failed("SDK removal failed") : .inUse)
      await expect(.installed, from: coordinator)
      XCTAssertTrue(coordinator.hasDownloadConsent)
      XCTAssertEqual(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey), "sdk-job")
      XCTAssertEqual(sdk.downloads, 0)
      XCTAssertGreaterThanOrEqual(sdk.queries.count, 2)
    }
  }

  func testUIFixturesNeverTouchRealSDKOrPersistedConsent() async throws {
    let defaults = try scratchDefaults()
    let sdk = FakeDownloadSDK()
    let coordinator = TranslationModelDownloadCoordinator(defaults: defaults, personalKey: "test",
                                                           sdk: sdk.client, fixtureConsent: false)
    coordinator.resumeIfConsented()
    XCTAssertEqual(coordinator.phase, .idle)
    coordinator.recordConsent()
    XCTAssertEqual(coordinator.phase, .installed)
    XCTAssertTrue(coordinator.requestModelUse())
    let outcome = await coordinator.removeDownloadedModel()
    XCTAssertEqual(outcome, .removed)
    XCTAssertFalse(coordinator.hasDownloadConsent)
    XCTAssertNil(defaults.object(forKey: TranslationModelDownloadCoordinator.consentKey))
    XCTAssertNil(defaults.object(forKey: TranslationModelDownloadCoordinator.handleKey))
    XCTAssertTrue(sdk.events.isEmpty)
  }

  func testRecognizedUIFixtureWithoutFirstRunArgumentUsesInMemoryConsent() async {
    let defaults = UserDefaults.standard
    let originalConsent = defaults.object(forKey: TranslationModelDownloadCoordinator.consentKey) as? Bool
    let originalHandle = defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey)
    let coordinator = TranslationModelDownloadCoordinator.fromLaunchArguments(["-uiState", "ready"])

    coordinator.recordConsent()

    XCTAssertEqual(coordinator.phase, .installed)
    XCTAssertTrue(coordinator.requestModelUse())
    XCTAssertEqual(defaults.object(forKey: TranslationModelDownloadCoordinator.consentKey) as? Bool,
                   originalConsent)
    XCTAssertEqual(defaults.string(forKey: TranslationModelDownloadCoordinator.handleKey), originalHandle)
    await Task.yield()
    XCTAssertEqual(coordinator.phase, .installed)
  }

  private func makeCoordinator(_ defaults: UserDefaults, _ sdk: FakeDownloadSDK)
    -> TranslationModelDownloadCoordinator {
    TranslationModelDownloadCoordinator(defaults: defaults, personalKey: "test", sdk: sdk.client)
  }

  private func scratchDefaults() throws -> UserDefaults {
    let name = "ModelDownloadCoordinatorTests.\(UUID().uuidString)"
    let defaults = try XCTUnwrap(UserDefaults(suiteName: name))
    addTeardownBlock { defaults.removePersistentDomain(forName: name) }
    return defaults
  }

  private func expect(_ phase: TranslationModelDownloadCoordinator.Phase,
                      from coordinator: TranslationModelDownloadCoordinator,
                      file: StaticString = #filePath, line: UInt = #line) async {
    let deadline = Date().addingTimeInterval(2)
    while coordinator.phase != phase, Date() < deadline {
      try? await Task.sleep(for: .milliseconds(10))
    }
    XCTAssertEqual(coordinator.phase, phase, file: file, line: line)
  }
}

@MainActor
private final class FakeDownloadSDK {
  var state: BackgroundDownloadState = .installed
  var lookupFails = false
  var stopFails = false
  var removalFails = false
  var removalInUse = false
  var duringRemoval: (() async -> Void)?
  var downloads = 0
  var queries: [String] = []
  var stopped: [String] = []
  var removals = 0
  var events: [String] = []

  var phase: TranslationModelDownloadCoordinator.Phase {
    switch state {
    case .queued: .queued
    case .resolving: .resolving
    case .downloading: .downloading(0.5)
    case .verifying: .verifying
    case .installed: .installed
    case .failed: .failed("SDK download failed")
    case .stopped: .stopped
    @unknown default: .failed("Unknown state")
    }
  }

  var client: TranslationModelDownloadCoordinator.SDKClient {
    .init(download: { [self] in
      downloads += 1
      events.append("download")
      return BackgroundDownloadHandle(id: "sdk-job")
    }, status: { [self] handle in
      queries.append(handle.id)
      events.append("status:\(handle.id)")
      if lookupFails { throw failure("SDK lookup failed") }
      return BackgroundDownloadStatus(handle: handle, state: state, bytesDownloaded: 50,
                                      totalBytes: 100, errorMessage: "SDK download failed")
    }, stop: { [self] handle in
      stopped.append(handle.id)
      events.append("stop:\(handle.id)")
      if stopFails { throw failure("SDK stop failed") }
    }, remove: { [self] in
      removals += 1
      events.append("remove")
      await duringRemoval?()
      if removalFails { throw failure("SDK removal failed") }
      return ModelRemovalResult(removed: !removalInUse, isInUse: removalInUse,
                                deletedFiles: 1, deletedBytes: 100)
    })
  }

  private func failure(_ message: String) -> NSError {
    NSError(domain: "FakeDownloadSDK", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
  }
}
