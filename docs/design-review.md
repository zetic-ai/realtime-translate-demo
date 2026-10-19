# Relais native design refinement

Stacked on PR #4 (`codex/relais-sessions`). This diff refines iOS and Android styling without changing speech, persistence or summary generation.

| Previous PR #4 preview | Updated iOS screen |
| --- | --- |
| ![Before](design/conversation-before.png) | ![After](design/conversation-after.png) |

Both captures use the same illustrative Hello / Bonjour fixture. The previous capture has English UI text; the new capture uses French. Neither exercises model inference.

![Saved session with navy selected segment and primary action](design/session-after.png)

## Native adaptation

- Bouygues Speak headings/wordmark, Read Regular body and Read Medium emphasis. Identical TTFs are registered on iOS and bundled as Android font resources. Native Dynamic Type / sp scaling and fallback glyphs remain enabled.
- Translation stays first and bold, original stays visible below. Live and saved bubbles share 16pt radius/padding; controls use 8pt corners and full-width functional actions have a 48pt minimum height.
- 8pt spacing with 4pt compact increments; navy functional primary, outlined complementary actions, and redundant A/B speaker labels alongside color.
- Saved-session segments use navy selection and explicit selected accessibility traits on iOS. Android retains native tabs with the Trilogy typography/palette. Native system dialogs, keyboard and platform icons remain native.

## Validation

- iOS simulator build and 174 unit tests passed; 6 existing UI scenarios passed (header, save/history/transcript, control reachability, welcome, consent and typed input). The save/history scenario passed again after the final session heading and padding refinements.
- Unsigned iPhone Release build passed. Three font files were verified present in the built app and byte-identical to the Android resources.
- Android: 116 unit tests, debug APK build, lint, and instrumentation-test source compilation passed. No connected Android device/emulator was available, so Android UI tests were not executed.
- Native French conversation and saved-session screens inspected; accessibility-extra-large text checked for the translated/original text, language controls and A/B talk controls. Simulator text size restored afterward.
- `git diff --check` passed.

Actual summary output quality, peak memory, offline acceptance and physical-device inference remain unverified. The summary preview is only an interface state, not model validation. The existing internal iOS SDK deployment-target linker warning remains.
