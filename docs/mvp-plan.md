# Two-Speaker Push-to-Talk Translation MVP Plan

## Goal

Build native Android and iOS apps for one device, where two people explicitly assign their utterances with A or B controls. Each platform uses on-device STT, and `SJ_zetic/Hy-MT2-1.8B` translates finalized source text into the other person's reading language in a chat-style card.

## User flow

1. On the setup screen, users choose `Automatic` or an OS-provided recognition language and a reading language for both A and B, then grant microphone and speech-recognition permissions.
2. On the conversation screen, A or B holds their own button. A tap starts recording and a second tap stops it as an accessibility alternative.
3. Only the active person's partial source text updates. The other button is disabled so exactly one on-device STT session is active.
4. Releasing the button or tapping to stop finalizes the STT source text.
5. A source text is translated serially into B's reading language; B source text is translated serially into A's reading language with Hy-MT2. The translation appears in the same card.
6. When translation fails, the source text remains visible and the translation area shows an error and retry action instead of a fabricated result.

## Pipeline

`User-selected A or B button` -> `Platform on-device STT using Automatic or an OS-provided recognition language` -> `Finalized source text` -> `Serial Hy-MT2 translation` -> `Chat card in the other person's reading language`

| Stage | Component | Responsibility |
| --- | --- | --- |
| Speaker assignment | A/B push-to-talk controls | The user explicitly identifies the speaker. |
| Android STT | `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | Produces partial and final on-device source text for the selected speaker. |
| iOS STT | `SFSpeechRecognizer` with `requiresOnDeviceRecognition = true` | Produces partial and final on-device source text for the selected speaker. |
| Translation | `SJ_zetic/Hy-MT2-1.8B` | Translates finalized source text into the other person's reading language. |

STT and Hy-MT2 inference run off the UI thread. Hy-MT2 uses a serial queue that processes one finalized utterance at a time. If model initialization or runtime execution has not been verified or fails, the app shows an error rather than producing a translation.

## Language scope

- Speaking languages for A and B: `Automatic` or an OS-provided recognition language. The app does not restrict the source-language list; on-device STT availability remains determined by the device, OS, permissions, and downloaded speech assets.
- Reading languages for A and B: the 38 official Hy-MT2 options in the [Hy-MT2 translation reference](hy-mt2-integration-reference.md).
- A source text translates only into B's reading language, and B source text translates only into A's reading language. Automatic language detection and special handling for same-language pairs are out of scope.

The app does not preflight or gate a source language. If the platform cannot start the requested on-device recognition session, the app reports the platform error and does not fall back to network STT.

## Scope and constraints

- One device does not support simultaneous A/B speech or concurrent STT sessions. The opposite button is disabled while an utterance is active.
- Automatic speaker separation, separate voice models, additional audio fan-out, and persistent storage of audio or conversations are out of scope.
- Accounts, sign-in, cloud sync, export or sharing, and telephone-call recording are out of scope.

## Completion criteria

- Android and iOS implement the same A/B setup, single-active PTT behavior, translation after source finalization, and error-recovery meaning.
- Each A/B utterance card shows the speaker label, source text, target language for the other person, translated text, and processing or error state in chronological order.
- Hold-to-talk and tap-toggle provide the same start/stop outcome and have accessibility labels.
- The reading-language selector matches the 38 official Hy-MT2 entries, and the request builder uses the documented one-user-message prompt.
