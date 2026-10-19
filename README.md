# Relais

Relais is a native iOS and Android app for two people to translate a conversation on one phone. Tap A or B to speak, then tap again to stop, or type a turn. Platform on-device speech recognition produces the original transcript and Melange runs `SJ_zetic/Hy-MT2-1.8B` for translation. There is no network STT fallback or automatic speaker separation.

The interface uses Bouygues Telecom Trilogy colors, compact language controls, and wide A/B chat bubbles. The **translation appears first in bold**, with the smaller original transcript always visible below. Bouygues Speak headings and Bouygues Read body text retain native text scaling. Replay remains an explicit action.

## Saved sessions

**End → End and save** writes the session before clearing the live conversation. **Menu → History** offers title search, date/duration/languages, reopening, rename and confirmed deletion. Opening a saved session shows its full transcript directly. A failed save retains the current conversation and offers retry.

Finalized turns are checkpointed locally, including originals whose translation failed. iOS uses atomic, file-protected JSON files in Application Support, excluded from backup. Android uses atomic JSON replacement in `noBackupFilesDir` and disables app backup. No audio is retained. An interrupted session is recoverable from History.

AI summarization is excluded from this change and is tracked in a separate follow-up draft PR. This version has no summary UI, model download, runtime or generation entry point.

## Build

- [iOS build instructions](ios/README.md): SwiftUI, Xcode/XcodeGen. Retains the existing pinned internal SDK because its local archive/GGUF loading APIs are required by the app.
- [Android build instructions](android/README.md): Kotlin, Compose, JDK 17, Android SDK 35. Uses the public Melange SDK 1.11.0 repository; the summary-specific runtime changes are deferred.
- [Brand asset notices](THIRD_PARTY_NOTICES.md)
- [Relais design and session contract](docs/relais.md)
- [Translation prompt/model reference](docs/hy-mt2-integration-reference.md)

Supply `MELANGE_PERSONAL_KEY` through the build environment. Do not commit credentials or model artifacts. The existing Android application ID, iOS bundle ID and signing team are unchanged. The development key is embedded in the built app; production distribution needs suitable credential provisioning.
