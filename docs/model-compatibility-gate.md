# Model Language Compatibility Gate

## Purpose

Do not assume that each person's requested input language or the other person's translation output language is supported. A and B input languages must pass platform-specific on-device STT capability and permission checks. The UI may show output-language candidates from the official table, but a translation run is valid only after the documentation and physical-device checks below pass.

## Components in scope

- Android on-device STT: `SpeechRecognizer.createOnDeviceSpeechRecognizer()`
- iOS on-device STT: `SFSpeechRecognizer` with `requiresOnDeviceRecognition = true`
- Translation: `SJ_zetic/Hy-MT2-1.8B`

## Platform STT gate

| Platform | Required component | Language-enable condition | Prohibited behavior |
| --- | --- | --- | --- |
| Android | API 31+ `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | On API 31-32, start provisionally when an on-device service exists and expose language-specific failure as `onDeviceUnsupported`. On API 33 and later, distinguish installed and downloadable states. | Automatically fall back to a network recognizer when on-device recognition is unavailable. |
| iOS | `SFSpeechRecognizer` and `requiresOnDeviceRecognition = true` | Require `supportsOnDeviceRecognition`, `isAvailable`, Speech permission, and microphone permission for the selected locale. | Disable the on-device requirement or automatically fall back to network recognition. |

The requested input languages - Korean, Chinese, Japanese, English, French, and Spanish - are enabled for A and B only after these conditions pass.

## Runtime rules

- Only one of A or B on-device STT sessions is active. Simultaneous speech and simultaneous STT are not supported.
- The user-selected A/B button is the only speaker-assignment signal. The app performs neither automatic speaker attribution nor additional voice analysis.
- Partial STT appears only in the active speaker's card and is never translated.
- If platform STT reports a final result before user release, retain it only as a pending transcript. Send source text to Hy-MT2 only after release or tap stop.
- Hy-MT2 requests run serially. If model initialization or execution is unverified or fails, do not create a translation result; show an error in the source card's translation area.

## Translation output-language UI list

Use the official **Supported Languages** table for `Hy-MT2-1.8B` as the output-language selector source. The 38 entries below are available as reading-language options for A and B.

| Name | Code |
| --- | --- |
| Chinese | `zh` |
| English | `en` |
| French | `fr` |
| Portuguese | `pt` |
| Spanish | `es` |
| Japanese | `ja` |
| Turkish | `tr` |
| Russian | `ru` |
| Arabic | `ar` |
| Korean | `ko` |
| Thai | `th` |
| Italian | `it` |
| German | `de` |
| Vietnamese | `vi` |
| Malay | `ms` |
| Indonesian | `id` |
| Filipino | `tl` |
| Hindi | `hi` |
| Traditional Chinese | `zh-Hant` |
| Polish | `pl` |
| Czech | `cs` |
| Dutch | `nl` |
| Khmer | `km` |
| Burmese | `my` |
| Persian | `fa` |
| Gujarati | `gu` |
| Urdu | `ur` |
| Telugu | `te` |
| Marathi | `mr` |
| Hebrew | `he` |
| Bengali | `bn` |
| Tamil | `ta` |
| Ukrainian | `uk` |
| Tibetan | `bo` |
| Kazakh | `kk` |
| Mongolian | `mn` |
| Uyghur | `ug` |
| Cantonese | `yue` |

### Source discrepancies and converted-model verification

- The same official model card states **33 languages** in its body, **36 languages** in model metadata, and the 38 entries above in its Supported Languages table. The MVP uses the table's 38 entries as UI candidates and rechecks the official model version before release.
- `SJ_zetic/Hy-MT2-1.8B` used by the app is a conversion of the official `tencent/Hy-MT2-1.8B`. This list supplies UI candidates only; it does not guarantee the converted model's mobile initialization or inference compatibility. Verify initialization and translation for each selected language on physical Android and iOS devices.

## Pre-release gate

Record the following for each A/B input-language and other-person output-language combination.

1. Confirm on-device STT capability and permissions for each selected A/B input language on Android and iOS.
2. Confirm that A/B controls start only one STT session at a time and disable the opposite control.
3. Confirm that partial source text updates only the active card; a final result received before user release remains pending; and only source text finalized after release or tap stop is translated.
4. Confirm that unavailable on-device STT does not trigger network fallback and instead presents recovery guidance.
5. Confirm the official Hy-MT2 Supported Languages table and use only its 38 entries as reading-language candidates.
6. On physical devices for each platform, confirm Hy-MT2 initialization, A-to-B and B-to-A translation, serial translation handling, and explicit errors without fabricated translations when runtime execution fails.
7. Record output language, success or failure, model/artifact version, and device and OS version in an external audit log.

## Current verification status

- Android unit tests, debug build, lint, Android test APK build, and six physical Android 16 UI tests passed.
- iOS strict SwiftLint reported zero violations; the unsigned generic build, eight unit tests, and four iPhone 17 simulator UI tests passed.
- These checks cover the A/B UI and platform integration paths. Evidence is still required for on-device capability and recognition of the six input languages on physical Android and iPhone devices, the physical-iPhone lifecycle, and Hy-MT2 artifact loading, initialization, and serial translation end-to-end.

## External evidence template

Keep this record outside the product repository.

| Platform | Speaker | Input | Target output | On-device STT capability/permission | Single-active PTT | Translate finalized STT only | Hy-MT2 runtime | Device/OS | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Android | A |  |  |  |  |  |  |  | Pending |
| Android | B |  |  |  |  |  |  |  | Pending |
| iOS | A |  |  |  |  |  |  |  | Pending |
| iOS | B |  |  |  |  |  |  |  | Pending |
