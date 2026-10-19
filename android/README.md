# Relais for Android

Native Kotlin/Compose app. Requirements: JDK 17, Gradle 8.9 or later in the 8.x series, Android SDK 35. Set `ANDROID_HOME` or use an untracked `local.properties` with `sdk.dir`. Set `MELANGE_PERSONAL_KEY` in the build environment for real model loading; builds without it can run UI/unit tests but cannot initialize models.

From this directory:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
gradle :app:connectedDebugAndroidTest  # requires an emulator or phone
```

The SDK is pinned to `com.zeticai.mlange:mlange:1.11.0`, resolved from the public GitHub release repository in `settings.gradle.kts`. No GitHub Packages credentials are required. The old internal package is unavailable with the current build credentials. App ID and microphone permissions are unchanged.

`SessionViewModel` owns the speech/translation lifecycle and checkpoints finalized turns in `SessionHistory`. `SessionHistory.kt` holds atomic persistence and recovery. `SessionHistoryScreen.kt` renders history and session details. `RealtimeTranslateApp.kt` renders the conversation; `TurnTranslateRoot.kt` wraps menu, first-run flows and confirmations. `RealtimeTranslateTheme.kt` supplies the Bouygues palette.

See [Relais design and session contract](../docs/relais.md) for data behavior and model validation limits. Unit tests use injected runtime fakes; they do not measure actual model quality or device memory.
