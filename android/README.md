# Android app

The Android implementation uses Kotlin and Jetpack Compose. Speech recognition uses the device's on-device recognizer with automatic language detection where the OS provides it; the app does not publish a fixed source-language list. Translation targets are the 38 languages listed by Hy-MT2. Screen state, design tokens, and accessibility rules follow the [shared UX and design specification](../docs/shared-ux-spec.md).

Before a device build, create a personal key directly from the authenticated [Melange Personal Access Token settings](https://melange.zetic.ai/settings?tab=pat) page and run `./setup.sh` from the repository root. The script creates the ignored, owner-read/write-only `android/.melange.local.properties` file. A `MELANGE_PERSONAL_KEY` environment variable takes precedence and is the CI setup. Run the script again to rotate the key. Never commit, share, or distribute a development APK containing the key.
