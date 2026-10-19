# Relais design and session contract

## Mobile layout

The approved design keeps the conversation as the main surface. Header: menu, Relais, visible End action. Language choices remain compact and lock during an active turn. Bubbles use 8-point outer spacing, a 16-point opposite gutter, 12-point radius, 16-point bold translation above a divider and 13-point original text below. Neither text is collapsed. Touch targets remain native minimum sizes; typography follows system scaling. The A/B controls remain tap-to-start/tap-to-stop.

The palette derives from [Bouygues Telecom Trilogy](https://design.bouyguestelecom.fr/foundations/colors): main `#25465F`, main fade `#EFF2F8`, accent `#CC4E0A`, grey `#BBC6CD`, grey fade `#E9EFF4`, error `#D42D02`, and white. A/B placement and letters supplement color. No client-specific identity or palette appears in the interface. Relais uses native fonts, not proprietary brand fonts.

## History

End is disabled during a live turn so finalization is not discarded by a UI tap. Confirming End saves before resetting the live conversation and opens the saved session. Empty sessions are not added. The existing Clear conversation setting archives finalized turns before clearing them. History lists completed and recovered sessions; the active conversation stays on the live screen. History is accessible between turns; it does not reopen the microphone. Search matches titles. Rename is limited to 80 characters; Delete explicitly confirms removal of the transcript.

Data schema version 1 stores a stable session ID, title, start/end time and ordered turns. Each turn retains its stable ID, speaker, original text, translation if available, and source/target language codes. JSON writes replace a single session atomically. Files are private to the app and excluded from cloud backup. Damaged history is retained and reported; the app does not overwrite it with an empty list.

At process restart, unfinished sessions remain readable in History. Opening any saved session shows the original and translated turns directly. There are no summary tabs, summary status badges, extra model consent or generation actions in this version.

## Deferred feature

AI summarization, its second model and runtime changes, consent flow, structured output/citation validation and summary-specific tests belong to a separate draft PR stacked after the design/history work. The existing translation runtime implementations and iOS SDK pin are retained here. Android retains the public 1.11.0 SDK repository migration already in this PR because the old internal package is unavailable with current build credentials; no summary-model options are enabled.

## Validation boundary

Unit tests cover persistence, interrupted-session recovery, write failures and the existing speech/translation lifecycle. Native builds validate integration. Physical-device translation acceptance and Android UI execution remain separate checks; a simulator/unit suite is not evidence for real model quality or device memory. Summary feature acceptance is deferred with its draft PR.
