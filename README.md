# Turn Translate

Turn Translate is an on-device, two-speaker push-to-talk translation MVP for Android and iOS.

Each person selects their utterance with the A or B button. The app recognizes the selected person's source text with platform on-device STT, then translates only finalized text into the other person's reading language with `SJ_zetic/Hy-MT2-1.8B`. Automatic speaker separation and simultaneous speech recognition are not supported.

Both apps follow the same conversation flow and design tokens while using native platform UI. A session follows `setup` -> `model loading` -> `ready` -> `end and unload` -> `setup`.

## Documentation

- [MVP plan](docs/mvp-plan.md)
- [Shared UX and design specification](docs/shared-ux-spec.md)
- [Hy-MT2 translation reference](docs/hy-mt2-integration-reference.md)

## Implementation directories

- `android/`: Kotlin and Jetpack Compose app
- `ios/`: Swift and SwiftUI app

STT uses Android and iOS on-device speech recognition. A speaker can choose `Automatic` or an OS-derived on-device recognition language; the app does not impose a source-language list, download speech models, or fall back to network STT. Android API 33 and later lists installed on-device locales, Android API 31-32 offers `Automatic`, and iOS lists only supported locales that are on-device capable. The reading-language selector uses the 38 official Hy-MT2 entries documented in `docs/hy-mt2-integration-reference.md`.

At session start, both apps load `SJ_zetic/Hy-MT2-1.8B` with Melange SDK `1.10.0`. Model loading exposes progress, failure, and retry states; the PTT controls become available only after the model is ready. Finalized source text is translated serially through the loaded model. Ending a session waits for model cleanup and close, then clears the prior conversation and returns to setup.

## Melange personal key setup

Create a personal key directly from the authenticated [Melange Personal Access Token settings](https://melange.zetic.ai/settings?tab=pat) page. Do not share or commit the key.

From the repository root, run:

```sh
./setup.sh
```

The script uses a pre-set `MELANGE_PERSONAL_KEY` when available; otherwise, it requests the key with a hidden terminal prompt. It writes these ignored, owner-read/write-only local files:

- `android/.melange.local.properties`
- `ios/Config/Melange.local.xcconfig`

Run `./setup.sh` again to rotate the key. Android reads `MELANGE_PERSONAL_KEY` from the build environment before its local file. For iOS or CI, set `MELANGE_PERSONAL_KEY`, run `./setup.sh` to materialize the ignored Xcode configuration, then build. The key is embedded in development app binaries for SDK initialization, so never distribute those binaries. Production distribution requires rotatable credential provisioning. The existing Android application ID, iOS bundle ID, and iOS signing configuration remain unchanged.
