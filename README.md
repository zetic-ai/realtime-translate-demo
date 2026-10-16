# Relais

Relais is a native iOS and Android app for two people to translate a conversation on one phone. Tap A or B to speak, then tap again to stop, or type a turn. Platform on-device speech recognition produces the original transcript and Melange runs `SJ_zetic/Hy-MT2-1.8B` for translation. There is no network STT fallback or automatic speaker separation.

The interface uses Bouygues Telecom Trilogy colors, compact language controls, and wide A/B chat bubbles. The **translation appears first in bold**, with the smaller original transcript always visible below. System fonts support platform text scaling. Replay remains an explicit action.

## Saved sessions and summaries

**End → End and save** writes the session before clearing the live conversation. **Menu → History** offers title search, date/duration/languages, reopening, rename and confirmed deletion. Session details have **AI summary** and **Transcript** tabs. A failed save retains the current conversation and offers retry.

Finalized turns are checkpointed locally, including originals whose translation failed. iOS uses atomic, file-protected JSON files in Application Support, excluded from backup. Android uses atomic JSON replacement in `noBackupFilesDir` and disables app backup. No audio is retained. An interrupted session is recoverable from History; interrupted summaries become retryable rather than staying in a loading state.

Summaries use the separate instruction model `zetic/LFM2.5-1.2B-Instruct`, version 1. The first use asks for permission to download model assets; this can use mobile data. Model acquisition/SDK authorization may need a network connection, but transcript inference runs locally with **no cloud fallback**. The translation model is released before summarizing, and starting a new conversation cancels and waits for summary cleanup before loading translation again. Keep the app in the foreground while summarizing; backgrounding interrupts generation safely.

Summaries contain key points, decisions, actions, and questions. Source buttons open the corresponding transcript turns. The model is instructed to use only explicit facts and avoid invented owners or deadlines. Output must match the structured schema and reference existing turns. These checks cannot establish factual correctness: the screen labels the result as an AI summary to verify against the transcript.

Long sessions are split into bounded Unicode-safe excerpts, with every original character retained. Excerpt summaries are shown in conversation order within each section. This avoids silent truncation, but may repeat points or miss a decision that depends on distant turns. Unsupported or malformed model output retains the transcript and offers retry.

## Build

- [iOS build instructions](ios/README.md): SwiftUI, Xcode/XcodeGen. Retains the existing pinned internal SDK because its local archive/GGUF loading APIs are required by the app.
- [Android build instructions](android/README.md): Kotlin, Compose, JDK 17, Android SDK 35. Uses Melange SDK 1.11.0 from its public GitHub release repository.
- [Relais design and session contract](docs/relais.md)
- [Translation prompt/model reference](docs/hy-mt2-integration-reference.md)

Supply `MELANGE_PERSONAL_KEY` through the build environment. Do not commit credentials or model artifacts. The existing Android application ID, iOS bundle ID and signing team are unchanged. The development key is embedded in the built app; production distribution needs suitable credential provisioning.
