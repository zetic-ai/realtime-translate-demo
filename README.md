# Realtime Translate Demo

An on-device, two-speaker push-to-talk translation MVP for Android and iOS.

Each person selects their utterance with the A or B button. The app recognizes the selected person's source text with platform on-device STT, then translates only finalized text into the other person's reading language with `SJ_zetic/Hy-MT2-1.8B`. Automatic speaker separation and simultaneous speech recognition are not supported.

Both apps follow the same conversation flow and design tokens while using native platform UI.

## Documentation

- [MVP plan](docs/mvp-plan.md)
- [Shared UX and design specification](docs/shared-ux-spec.md)
- [Model language compatibility gate](docs/model-compatibility-gate.md)

## Implementation directories

- `android/`: Kotlin and Jetpack Compose app
- `ios/`: Swift and SwiftUI app

STT uses Android and iOS on-device speech recognition. An A or B utterance starts only when the selected language and device support on-device recognition and required permissions are granted; the app never automatically falls back to network STT. Model and device support, along with real-time performance, are not treated as confirmed features until the checks in `docs/model-compatibility-gate.md` pass.
