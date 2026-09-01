# iOS app

The iOS implementation uses Swift and SwiftUI. Screen state, design tokens, and accessibility rules follow the [shared UX and design specification](../docs/shared-ux-spec.md).

Before a device build, create a personal key directly from the authenticated [Melange Personal Access Token settings](https://melange.zetic.ai/settings?tab=pat) page and run `./setup.sh` from the repository root. The script creates the ignored, owner-read/write-only `ios/Config/Melange.local.xcconfig` file, which Xcode reads automatically. For iOS or CI, set `MELANGE_PERSONAL_KEY` and run `./setup.sh` before building. Run the script again to rotate the key. The key is expanded into the development app's `Info.plist`; never commit, share, or distribute that binary.
