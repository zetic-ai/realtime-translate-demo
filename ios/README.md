# Relais for iOS

Native SwiftUI app for iPhone, iOS 16.6 or later. Use Xcode 26 and XcodeGen 2.38 or later. The current SDK pin in `project.yml` requires access to `zetic-ai/mlange_sdk`; retain that pin because the public 1.11.0 release omits local-loading constructors used by this app.

Generate the checked-in project after adding source files:

```sh
xcodegen generate --spec project.yml
xcodebuild -project RealtimeTranslate.xcodeproj -scheme RealtimeTranslate \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO test
```

If only command-line tools are selected globally, set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` for Xcode commands. Supply `MELANGE_PERSONAL_KEY` as a private build setting for real model loading; `Info.plist` resolves it into the runtime. Never commit the value. Builds without a key can run UI/unit tests but cannot initialize models.

`RealtimeTranslateViewModel` owns speech/translation and checkpoints finalized turns. `SessionHistory.swift` provides atomic protected files, recovery, structured summary validation and runtime handoff; `SessionHistoryView.swift` provides the history/detail screens. `MelangeTranslationRuntime` supports the existing translation model and the separate instruction model without sending transcripts to a remote generation API. `Localizable.xcstrings` supplies English, French and Spanish UI text.

The UI test launch states are fixtures, not microphone/model inference. See [Relais design and session contract](../docs/relais.md) for storage, summary behavior and physical-device validation requirements. The app's bundle ID and signing team remain unchanged.
