# Shared Android/iOS UX and Design Specification

## Consistency principles

Both platforms use idiomatic Jetpack Compose and SwiftUI controls while preserving the same information architecture, A/B meaning, state transitions, terminology, message semantics, and token values. Platform-native navigation, permission guidance, haptics, and safe-area behavior follow OS conventions.

## Screen structure

1. **Speaker setup**: Speaking language and reading language for A and B, microphone and speech-recognition permission and on-device capability state, and a `Start conversation` button.
2. **Conversation**: A state header, chronologically ordered utterance cards, and A/B push-to-talk buttons at the bottom.
3. **Error guidance**: The affected speaker, language, and cause, with `Try again` or `Open settings` actions.

### Conversation layout

```text
Status header: "Conversation ready" | "A is speaking" | "Translating"

[ A - Korean ]                 [ B - English ]
  Hello                         Hello
  To B: Hello                   To A: Hello

------------------------------------
[ Hold A to talk ] [ Hold B to talk ]
```

- A cards and controls create only A utterances; B cards and controls create only B utterances.
- An A source text identifies `To B: <B reading language>`; a B source text identifies `To A: <A reading language>`.
- The active utterance's partial source text updates only its existing active card. Partial text is never translated.
- While A or B is active, the opposite button is disabled with a textual explanation. Simultaneous recording is not supported.
- Android applies safe-content insets so the header, cards, and PTT controls avoid system bars and gesture areas.

## Shared state transitions

| State | Display | Allowed actions | Next state |
| --- | --- | --- | --- |
| `permissionRequired` | Permission needed | Request permission, open settings | `ready`, `error` |
| `ready` | A/B controls available | Start A or B, change language | `listeningA`, `listeningB` |
| `listeningA` | `A is speaking` and A partial card | Stop A | `finalizingA`, `error` |
| `listeningB` | `B is speaking` and B partial card | Stop B | `finalizingB`, `error` |
| `finalizingA` / `finalizingB` | `Finalizing A/B source text` | Wait for completion | `translatingA`, `translatingB`, `error` |
| `translatingA` / `translatingB` | `Translating for B/A` | Wait for completion | `ready`, `error` |
| `error` | Failure cause and recovery action; existing cards remain | Retry, open settings | `ready`, `permissionRequired` |

If platform STT reports a final result before the user stops an utterance, the app stores it only as the active card's pending transcript. The app leaves `listening*` for `finalizing*` and starts finalization and translation only after a button release or tap-toggle stop. If there is no finalized source text, no card completes and the app returns to `ready`. A translation error leaves the finalized source card visible and shows an error state in the translation area.

## A/B input and accessibility

- The primary action is push-to-talk: recording lasts while the user holds a button and stops when it is released.
- The same control supports tap-to-start and tap-to-stop as an accessibility alternative. The current interaction is shown as text.
- Accessibility labels include the current action and speaker, such as `Start A utterance`, `Stop A utterance`, `Start B utterance`, and `Stop B utterance`.
- A disabled opposite control exposes equivalent explanatory text, such as `Cannot start B utterance while A utterance is active`.

## On-device STT prerequisites

- Android API 31-32 can start a selected language provisionally when an on-device service exists. If on-device recognition for that language fails at start, the app shows an `onDeviceUnsupported` error and recovery guidance. Android API 33 and later distinguish installed-on-device and downloadable states.
- iOS requires `supportsOnDeviceRecognition` and `isAvailable` for the selected locale, granted Speech and microphone permissions, and `requiresOnDeviceRecognition = true` on the STT request.
- If any condition is not met, the relevant speaker control is unavailable or the app enters `error` with guidance. Network STT fallback is never used.

## Translation execution gate

- A and B reading-language selectors show all 38 options from the [model language compatibility gate](model-compatibility-gate.md).
- Translation runs only for finalized source text: A translates to B's reading language and B translates to A's reading language.
- If Hy-MT2 initialization, model execution, or physical-device compatibility verification fails, the app preserves the source card and shows an error and recovery action instead of an invented translation or an empty translation bubble.
- Hy-MT2 requests are serial. A queued card displays the recipient and `Translation pending`.

## Design tokens

| Token | Value | Usage |
| --- | --- | --- |
| `color.primary` | `#3B5BDB` | Primary action and active A button |
| `color.secondary` | `#0B7285` | Active B button |
| `color.surface` | `#FFFFFF` | Default background |
| `color.surfaceMuted` | `#F1F3F5` | Disabled controls and queued cards |
| `color.textPrimary` | `#1F2937` | Body text |
| `color.textSecondary` | `#6B7280` | Supporting text |
| `color.error` | `#C92A2A` | Errors |
| `space.1/2/3/4` | `4/8/12/16 dp/pt` | Shared spacing |
| `radius.message` | `16 dp/pt` | Chat cards |
| `radius.control` | `20 dp/pt` | A/B PTT controls |
| `type.body` | `16 sp/pt` | Source and translated text |
| `type.meta` | `12 sp/pt` | Speaker, status, and target-language text |

Android uses dp/sp and iOS uses pt with Dynamic Type while maintaining the visual size and hierarchy in the table. System dark-mode support is outside MVP scope; do not add forced theme switching.

## Android/iOS parity criteria

| Scenario | Same result on both platforms |
| --- | --- |
| Start A | A partial card and active-A state; B control disabled |
| Start B | B partial card and active-B state; A control disabled |
| Release or tap stop | Final result received before stopping stays pending; after stopping, source text finalizes and translation queues for the other speaker's language |
| Translation succeeds | Source text, target language, and translated text appear in one card |
| STT unsupported or permission denied | Do not start; show cause and recovery action; do not switch to network recognition |
| Translation runtime unverified or fails | Preserve source text; do not invent a translation; show translation error and retry |

## Verification

- Every control has an equivalent accessibility label.
- Body text and state text do not clip or overlap at larger text sizes.
- A/B active state, processing, and errors are distinguished with text and icons in addition to color.
- Android and iOS capture and compare the parity-table scenarios plus setup, conversation, and error screens using the same inputs.
