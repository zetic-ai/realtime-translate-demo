# Relais for iOS

Native SwiftUI app for iPhone, iOS 16.6 or later. Use Xcode 26 and XcodeGen 2.38 or later.

`project.yml` pins public `zetic-ai/ZeticMLangeiOS` 1.11.0 to revision `c5c197ba01c5a05d877e2b0abaa95eb70848fa3d`. The immutable revision avoids a moved 1.11.0 tag previously recorded by SwiftPM at a commit referencing the 1.10.0 binary; security fingerprints remain unchanged.

The SDK owns model downloads, cache reuse and removal. After download consent, the app stores the SDK's background handle and waits for its `installed` status before loading the translation model. Both translation and summary use the SDK initializer with `KEEP_EXISTING`; the app does not inspect cache files, extract archives or load GGUF files directly. Offline cold-start behavior still requires physical-device validation.

Generate the checked-in project after adding source files:

```sh
xcodegen generate --spec project.yml
xcodebuild -project RealtimeTranslate.xcodeproj -scheme RealtimeTranslate \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO test
```

If only command-line tools are selected globally, set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` for Xcode commands. Supply `MELANGE_PERSONAL_KEY` as a private build setting for real model loading; `Info.plist` resolves it into the runtime. Never commit the value. Builds without a key can run UI/unit tests but cannot initialize models.

`DeviceRuntimeTests` runs real SDK checks only when explicitly enabled on a signed physical-device build: `RELAIS_SDK_CACHE_TESTS=1` checks background download readiness, and `RELAIS_REAL_MODEL_TESTS=1` runs translation and summary inference. The current summary model can return JSON that fails the app's required summary schema; that test intentionally preserves the validation failure.

`RealtimeTranslateViewModel` owns speech/translation and checkpoints finalized turns. `SessionHistory.swift` provides atomic protected files, recovery, structured summary validation and runtime handoff; `SessionHistoryView.swift` provides the history/detail screens. `MelangeTranslationRuntime` supports the existing translation model and the separate instruction model without sending transcripts to a remote generation API. `Localizable.xcstrings` supplies English, French and Spanish UI text.

The UI test launch states are fixtures, not microphone/model inference. See [Relais design and session contract](../docs/relais.md) for storage, summary behavior and physical-device validation requirements. The app's bundle ID and signing team remain unchanged.

For representative design captures, use a Debug build with `-uiState designConversation -firstRun returning -AppleLanguages '(fr)' -AppleLocale fr_FR`. It shows two illustrative English/French exchanges (four turns), without microphone or model inference. This preview state is excluded from Release. See [the design capture notes](../docs/design-review.md).
