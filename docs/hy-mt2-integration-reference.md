# Hy-MT2 Translation Reference

## Source of truth

This reference follows Tencent's official [Hy-MT2-1.8B-GGUF model card](https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF) and the official [Hy-MT2 chat template](https://huggingface.co/tencent/Hy-MT2-1.8B/blob/main/chat_template.jinja).

## Target languages

The A and B reading-language selectors use these 38 entries from the official Supported Languages table. The complete English language name is the value used in the translation prompt.

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

## Request contract

Create one plain-text prompt and send it as the only `user` message. Do not add a system message. Replace `{target_lang}` with the target language's complete English name and `{source_text}` with the finalized STT transcript.

```text
Translate the following text into {target_lang}. Note that you should only output the translated result without any additional explanation:
{source_text}
```

The runtime, not the application prompt builder, applies the model's Jinja chat template with `add_generation_prompt = true`. For this one-user-message request, the template emits the begin-of-sentence token, a user token, the prompt text, and then the assistant generation token. The template can process a system message, but the model card states that the 1.8B model has no default system prompt; this app does not send one.

## Current scope

The app exposes the official target-language list and constructs the documented request. Downloading a GGUF artifact, selecting a GGUF quantization, applying the chat template in a mobile runtime, and executing Hy-MT2 inference are intentionally outside the current app scope. Until that runtime integration exists, an unavailable translation runtime must show an error and must not fabricate a translation.
