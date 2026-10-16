# Relais design and session contract

## Mobile layout

The approved design keeps the conversation as the main surface. Header: menu, Relais, visible End action. Language choices remain compact and lock during an active turn. Bubbles use 8-point outer spacing, a 16-point opposite gutter, 12-point radius, 16-point bold translation above a divider and 13-point original text below. Neither text is collapsed. Touch targets remain native minimum sizes; typography follows system scaling. The A/B controls remain tap-to-start/tap-to-stop.

The palette derives from [Bouygues Telecom Trilogy](https://design.bouyguestelecom.fr/foundations/colors): main `#25465F`, main fade `#EFF2F8`, accent `#CC4E0A`, grey `#BBC6CD`, grey fade `#E9EFF4`, error `#D42D02`, and white. A/B placement and letters supplement color. No client-specific identity or palette appears in the interface. Relais uses native fonts, not proprietary brand fonts.

## History

End is disabled during a live turn so finalization is not discarded by a UI tap. Confirming End saves before resetting the live conversation and opens the saved session. Empty sessions are not added. The existing Clear conversation setting archives finalized turns before clearing them. History lists completed and recovered sessions; the active conversation stays on the live screen. History is accessible between turns; it does not reopen the microphone. Search matches titles. Rename is limited to 80 characters; Delete explicitly confirms removal of transcript and summary.

Data schema version 1 stores a stable session ID, title, start/end time, app language for summary generation, ordered turns, summary status and optional structured summary. Each turn retains its stable ID, speaker, original text, translation if available, and source/target language codes. JSON writes replace a single session atomically. Files are private to the app and excluded from cloud backup. Damaged history is retained and reported; the app does not overwrite it with an empty list.

At process restart, unfinished sessions remain readable in History. Interrupted generation becomes Failed with retry. A summary failure never deletes the underlying session. Rename while generating is preserved; deletion/cancellation invalidates the worker before it can publish a late result.

## Summary runtime

- Model: `zetic/LFM2.5-1.2B-Instruct`, version 1, model key `1401c9e207e142e082563c15d1301293` (Melange reported ready and download-ready on 2026-10-16).
- The [publisher's model card](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct) supplies the ChatML template. Output language follows the saved app language: English, French or Spanish.
- Separate consent for the summary-model download. Transcript text is passed only to native model generation, never to a remote summarization endpoint. SDK asset acquisition/authorization still uses networking.
- One model workload at a time: release translation before loading summary, and await summary shutdown before translation resumes. Token loops check cancellation and cap generated tokens. Models are cleaned up and closed after generation, including error/cancellation.
- Original text is split by UTF-8 size on Unicode scalar/code-point boundaries, preserving every character and the original turn ID. Batches stay bounded; summaries are accumulated by section without truncating later turns. There is no global cross-excerpt synthesis.
- Transcript delimiters are neutralized and the prompt treats transcript content as data. Strict output validation requires four arrays and nonempty valid source IDs for every point. The app does not execute generated text or use it as commands.
- Summary sections: key points, decisions, actions, questions. No invented assignees/deadlines requested. The UI requires human verification and links each point to the saved original/translation.

## Validation boundary

Unit tests cover persistence, interrupted-session recovery, write failures, UTF-8 chunk completeness, output schema and citation rejection, and the existing speech/translation lifecycle. Native builds validate SDK integration. Physical-device model quality, memory and airplane-mode acceptance require configured model credentials and supported test phones; a simulator/unit suite is not evidence for those properties.
