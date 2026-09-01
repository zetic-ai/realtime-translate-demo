# Realtime Translate Demo

An on-device, two-speaker push-to-talk translation MVP for Android and iOS.

Each person selects their utterance with the A or B button. The app recognizes the selected person's source text with platform on-device STT, then translates only finalized text into the other person's reading language with `SJ_zetic/Hy-MT2-1.8B`. Automatic speaker separation and simultaneous speech recognition are not supported.

Both apps follow the same conversation flow and design tokens while using native platform UI.

## Documentation

- [MVP plan](docs/mvp-plan.md)
- [Shared UX and design specification](docs/shared-ux-spec.md)
- [Hy-MT2 translation reference](docs/hy-mt2-integration-reference.md)

## Implementation directories

- `android/`: Kotlin and Jetpack Compose app
- `ios/`: Swift and SwiftUI app

STT uses Android and iOS on-device speech recognition. A speaker can choose `Automatic` or an OS-provided recognition language; the app does not impose a source-language list and never automatically falls back to network STT. The reading-language selector uses the 38 official Hy-MT2 entries documented in `docs/hy-mt2-integration-reference.md`. GGUF runtime integration is not part of the current MVP.
