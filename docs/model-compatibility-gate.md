# 모델 언어 호환성 게이트

## 목적

요청된 6개 입력 언어와 번역 출력 언어를 모델 이름만으로 지원한다고 가정하지 않는다. 출력 언어 선택은 공식 표의 후보를 노출할 수 있지만, 실제 번역 실행은 아래의 문서 및 실기기 검증을 모두 통과해야 한다.

## 대상 모델

- `ajayshah/pyannote-segmentation-3.0`
- `realtonypark/Moonshine-Streaming-ASR-Encoder`
- `realtonypark/Moonshine-Streaming-ASR-Decoder`
- `SJ_zetic/Hy-MT2-1.8B`

## 번역 출력 언어 UI 목록

`Hy-MT2-1.8B` 공식 모델 카드의 **Supported Languages** 표를 출력 언어 선택 목록의 기준으로 사용한다. 아래 38개 항목은 UI에서 선택할 수 있다.

| 이름 | 코드 |
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

### 표기 불일치 및 변환본 검증

- 동일 공식 모델 카드에는 본문에 **33개 언어**라고 서술되고, 모델 메타데이터에는 **36 languages**라고 표시되지만, Supported Languages 표에는 위 **38개** 항목이 있다. 이 MVP의 UI 목록은 사용자가 확인할 수 있는 표의 38개 항목을 따르며, 불일치는 릴리스 전 공식 모델 버전과 함께 재확인한다.
- 앱에서 사용할 `SJ_zetic/Hy-MT2-1.8B`는 공식 `tencent/Hy-MT2-1.8B` 변환본이다. 위 목록은 UI 후보 목록일 뿐, 변환본의 모바일 초기화·추론 호환성을 보장하지 않는다. Android와 iOS 실기기에서 각 선택 언어의 초기화와 번역을 검증해야 한다.

## 출시 전 게이트

각 입력 언어(한국어, 중국어, 일본어, 영어, 프랑스어, 스페인어)와 출력 언어 조합마다 다음을 기록한다.

1. 네 모델의 모델 카드, 라이선스, 배포 형식, 모바일 런타임 호환성을 확인한다.
2. Encoder와 Decoder가 해당 입력 언어에서 함께 동작함을 확인한다.
3. `Hy-MT2-1.8B`의 공식 Supported Languages 표를 확인하고, 위 38개 항목만 출력 언어 후보로 사용한다.
4. 각 플랫폼의 실제 기기에서 마이크 입력으로 화자 구분, 부분 원문, 확정 원문, 번역을 차례로 검증한다.
5. 결과 언어, 실패 여부, 모델/아티팩트 버전, 기기와 OS 버전을 증거 로그에 남긴다.

## UI 노출 및 실행 규칙

- 위 38개 출력 언어는 검증 전에도 UI에서 선택할 수 있다.
- 선택한 언어 조합의 모델 초기화 또는 실기기 호환성 게이트가 실패하면 번역을 실행하지 않고 앱의 `error` 상태와 복구 방법을 표시한다.
- 모델이 일부 언어에서 화자 분리 또는 스트리밍 STT를 지원하지 않으면 해당 언어는 MVP 지원 목록에서 제외하고, 계약 변경 승인을 받은 뒤에만 대체 모델을 검토한다.
- 모델 라이선스, 모바일 배포 가능성, 메모리 또는 지연 시간이 기준을 충족하지 않으면 앱 통합을 시작하지 않는다.

## 증거 기록 양식

| 플랫폼 | 입력 | 출력 | STT | 화자 분리 | 번역 | 모델 버전 | 기기/OS | 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Android |  |  |  |  |  |  |  | 대기 |
| iOS |  |  |  |  |  |  |  | 대기 |
