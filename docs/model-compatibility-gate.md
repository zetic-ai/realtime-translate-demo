# 모델 언어 호환성 게이트

## 목적

요청된 6개 입력 언어와 번역 출력 언어를 지원한다고 가정하지 않는다. 입력 언어는 플랫폼별 온디바이스 STT capability와 권한 검사를 통과해야 하며, 출력 언어 선택은 공식 표의 후보를 노출할 수 있지만 실제 번역 실행은 아래의 문서 및 실기기 검증을 모두 통과해야 한다.

## 대상 모델

- `ajayshah/pyannote-segmentation-3.0`
- `SJ_zetic/Hy-MT2-1.8B`

## STT 플랫폼 게이트

| 플랫폼 | 필수 구성 | 언어 활성화 조건 | 금지된 동작 |
| --- | --- | --- | --- |
| Android | API 31+ `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | API 31~32는 on-device service가 있을 때 provisional 시작하며 언어별 실패는 `onDeviceUnsupported`으로 표면화. API 33 이상은 installed와 downloadable 상태를 구분 | 온디바이스 불가 시 네트워크 recognizer로 자동 fallback |
| iOS | `SFSpeechRecognizer`와 `requiresOnDeviceRecognition = true` | 선택 locale의 `supportsOnDeviceRecognition`, `isAvailable`, Speech 권한, 마이크 권한 통과 | 온디바이스 요구를 해제하거나 네트워크 인식으로 자동 fallback |

요청 입력 언어(한국어, 중국어, 일본어, 영어, 프랑스어, 스페인어)는 각 기기·OS에서 위 조건을 통과한 경우에만 활성화한다. Android API 31~32는 on-device service가 있을 때 여섯 언어를 provisional로 시작하고, 시작 시 언어별 온디바이스 인식이 실패하면 `onDeviceUnsupported` 오류를 표시한다. API 33 이상에서는 installed-on-device와 downloadable 상태를 구분한다. installed-on-device probe가 통과한 언어만 선택·시작하며, downloadable 언어는 `언어 모델 다운로드` 버튼으로 실제 다운로드 요청을 시작한다. API 33에서는 다운로드 완료 후 사용자에게 세션 재시작을 안내한다. API 34 이상에서는 다운로드 callback 뒤 probe를 다시 실행하고 통과한 경우에만 시작한다.

## 파이프라인 실행 규칙

- 마이크 PCM을 pyannote와 플랫폼 STT에 fan-out한다. 두 소비자가 처리를 마칠 때까지 오디오 버퍼를 유지하고, 세션 종료 시 관련 리소스를 해제한다.
- pyannote 결과로 발화 구간과 화자 라벨을 확정 STT 발화에 귀속한다. 부분 STT는 UI에만 표시할 수 있으며 번역하지 않는다.
- Hy-MT2에는 확정 STT만 전달한다.
- pyannote와 Hy-MT2 추론은 메모리 압박을 제한하기 위해 직렬 실행한다. 이 규칙은 플랫폼 STT의 내부 실행을 제어한다는 뜻은 아니다.

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

1. 두 ML 모델의 모델 카드, 라이선스, 배포 형식, 모바일 런타임 호환성을 확인한다.
2. Android API 31~32에서 on-device service가 있을 때 provisional 시작하고 언어별 실패를 `onDeviceUnsupported`으로 표면화하는지 확인한다. API 33 이상에서는 선택 언어의 installed-on-device/downloadable 구분, `언어 모델 다운로드` 버튼의 실제 다운로드 요청, API 33 다운로드 완료 뒤 사용자 재시작, API 34 이상 callback 뒤 재probe·시작 흐름을 확인한다. iOS는 선택 locale의 `supportsOnDeviceRecognition`, `isAvailable`, Speech·마이크 권한을 확인한다.
3. 온디바이스 STT가 불가한 조건에서 네트워크 fallback이 발생하지 않고 복구 안내를 표시하는지 확인한다.
4. `Hy-MT2-1.8B`의 공식 Supported Languages 표를 확인하고, 위 38개 항목만 출력 언어 후보로 사용한다.
5. 각 플랫폼의 실제 기기에서 마이크 입력으로 PCM fan-out, 화자 구분, 부분 원문, 확정 원문, 확정 원문만의 번역을 차례로 검증한다. pyannote와 Hy-MT2가 직렬 실행되는지도 확인한다.
6. 결과 언어, 실패 여부, 모델/아티팩트 버전, 기기와 OS 버전을 증거 로그에 남긴다.

## UI 노출 및 실행 규칙

- 위 38개 출력 언어는 검증 전에도 UI에서 선택할 수 있다.
- 선택한 언어 조합의 플랫폼 온디바이스 STT, 모델 초기화 또는 실기기 호환성 게이트가 실패하면 번역을 실행하지 않고 앱의 `error` 상태와 복구 방법을 표시한다.
- Android API 31~32에서는 on-device service가 있으면 여섯 입력 언어를 provisional로 시작하고, 언어별 온디바이스 실패를 `onDeviceUnsupported` 오류로 표시한다. API 33 이상에서는 installed-on-device probe를 통과하지 못한 언어 중 downloadable 상태만 `언어 모델 다운로드`로 안내하며, 그 밖의 언어는 비활성화한다. 네트워크 STT로 자동 전환하지 않으며, 대체 STT 도입은 계약 변경 승인을 받은 뒤에만 검토한다.
- 모델 라이선스, 모바일 배포 가능성, 메모리 또는 지연 시간이 기준을 충족하지 않으면 앱 통합을 시작하지 않는다.

## 증거 기록 양식

| 플랫폼 | 입력 | 출력 | 온디바이스 STT capability/권한 | PCM fan-out/화자 분리 | 확정 STT만 번역 | 모델 버전 | 기기/OS | 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Android |  |  |  |  |  |  |  | 대기 |
| iOS |  |  |  |  |  |  |  | 대기 |
