# Relais · Bouygues Trilogy refinement

Stacked on PR #4 (`codex/relais-sessions`). This diff refines iOS and Android styling while retaining saved-session history. AI summarization is excluded from both earlier PRs and tracked in a separate draft after this one.

| Previous full-width bubbles | Updated compact bubbles |
| --- | --- |
| ![Before](design/conversation-before.png) | ![After](design/conversation-after.png) |

Both captures show the same two complete English/French exchanges (four alternating A/B turns) in the French UI. Dialogue is illustrative fixture text; no model inference is exercised.

Saved sessions open directly to their transcript, without summary controls:

![Saved transcript](design/transcript-after.png)

## Native adaptation

- Bouygues Speak headings/wordmark, Read Regular body and Read Medium emphasis, bundled identically on iOS and Android with native scaling and fallback glyphs.
- Translation first and bold; original visible below. Live and saved bubbles use content-driven width capped at 84% of the viewport, 16pt outer margins, 16pt horizontal / 12pt vertical padding and a 16pt radius. A aligns left and B right.
- Navy functional primary actions, outlined complementary actions, redundant A/B speaker labels, 8pt control corners, 48pt minimum full-width actions and 8pt/4pt spacing increments.
- Native dialogs, keyboard and platform icons remain native adaptations. There is no summary tab, status badge, model consent or generation action in this version.

## Reproducible captures

In a Debug simulator build, launch with `-uiState designConversation -firstRun returning -AppleLanguages '(fr)' -AppleLocale fr_FR`. Four fixture turns use ephemeral preferences; the preview state is excluded from Release. End → Save opens the matching saved transcript directly. Existing single-turn UI fixtures remain available.

## Validation

The summary extraction passed 168 iOS unit tests, the save/history UI scenario, and 111 Android unit tests plus debug build, lint and instrumentation-source compilation. The save/history check verifies that summary actions are absent. After restacking, the design PR passed three targeted iOS UI checks (save/history, typed input, replay) an unsigned iPhone Release build, and the Android debug build, lint and instrumentation-source compilation. The updated native French saved-session screenshot was inspected; all four turns open directly without a summary tab. Android device UI execution and physical-device translation acceptance remain outstanding. The separated summary PR has its own real-device acceptance gate; fixture screenshots do not establish AI output quality.
