# 모델 언어 호환성 게이트

## 목적

각 사용자의 요청 입력 언어와 상대방의 번역 출력 언어를 지원한다고 가정하지 않는다. A/B의 입력 언어는 플랫폼별 온디바이스 STT capability와 권한 검사를 통과해야 한다. 출력 언어 선택은 공식 표의 후보를 노출할 수 있지만 실제 번역 실행은 아래의 문서 및 실기기 검증을 모두 통과해야 한다.

## 대상 구성 요소

- Android 온디바이스 STT: `SpeechRecognizer.createOnDeviceSpeechRecognizer()`
- iOS 온디바이스 STT: `SFSpeechRecognizer` + `requiresOnDeviceRecognition = true`
- 번역: `SJ_zetic/Hy-MT2-1.8B`

## STT 플랫폼 게이트

| 플랫폼 | 필수 구성 | 언어 활성화 조건 | 금지된 동작 |
| --- | --- | --- | --- |
| Android | API 31+ `SpeechRecognizer.createOnDeviceSpeechRecognizer()` | API 31~32는 on-device service가 있을 때 provisional 시작하며 언어별 실패는 `onDeviceUnsupported`으로 표면화. API 33 이상은 installed와 downloadable 상태를 구분 | 온디바이스 불가 시 네트워크 recognizer로 자동 fallback |
| iOS | `SFSpeechRecognizer`와 `requiresOnDeviceRecognition = true` | 선택 locale의 `supportsOnDeviceRecognition`, `isAvailable`, Speech 권한, 마이크 권한 통과 | 온디바이스 요구를 해제하거나 네트워크 인식으로 자동 fallback |

요청 입력 언어(한국어, 중국어, 일본어, 영어, 프랑스어, 스페인어)는 A와 B 각각에서 위 조건을 통과한 경우에만 활성화한다. Android API 31~32는 on-device service가 있으면 선택 언어를 provisional로 시작한다. API 33 이상에서는 installed-on-device와 downloadable 상태를 구분한다. iOS는 선택 locale의 `supportsOnDeviceRecognition`, `isAvailable`, Speech·마이크 권한을 확인한다.

## 실행 규칙

- A 또는 B 중 하나의 온디바이스 STT만 활성화한다. 동시 발화와 동시 STT는 지원하지 않는다.
- 사용자가 시작한 A/B 버튼이 발화자의 유일한 귀속 근거다. 자동 화자 귀속 또는 별도의 음성 분석은 수행하지 않는다.
- 부분 STT는 활성 사용자의 카드에만 표시하며 번역하지 않는다.
- 플랫폼 STT가 user release 전에 final 결과를 보고하면 이를 pending transcript로만 보관한다. release 또는 탭 종료 후에만 확정 원문을 Hy-MT2에 전달한다.
- Hy-MT2 요청은 직렬로 실행한다. 모델 초기화 또는 실행이 미검증이거나 실패한 경우 번역 결과를 만들지 않고 원문 카드의 번역 영역에 오류를 표시한다.

## 번역 출력 언어 UI 목록

`Hy-MT2-1.8B` 공식 모델 카드의 **Supported Languages** 표를 출력 언어 선택 목록의 기준으로 사용한다. 아래 38개 항목은 A와 B의 읽을 언어로 UI에서 선택할 수 있다.

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

- 동일 공식 모델 카드에는 본문에 **33개 언어**, 모델 메타데이터에는 **36 languages**, Supported Languages 표에는 위 **38개** 항목이 있다. MVP UI 후보는 표의 38개를 따르며, 릴리스 전 공식 모델 버전과 함께 재확인한다.
- 앱에서 사용할 `SJ_zetic/Hy-MT2-1.8B`는 공식 `tencent/Hy-MT2-1.8B` 변환본이다. 위 목록은 UI 후보일 뿐, 변환본의 모바일 초기화·추론 호환성을 보장하지 않는다. Android와 iOS 실기기에서 각 선택 언어의 초기화와 번역을 검증해야 한다.

## 출시 전 게이트

A와 B 각각의 입력 언어와 상대방 출력 언어 조합마다 다음을 기록한다.

1. Android와 iOS에서 A/B별 선택 입력 언어의 온디바이스 STT capability·권한을 확인한다.
2. A와 B 버튼이 한 번에 하나의 STT만 시작하고, 반대 버튼을 비활성화하는지 확인한다.
3. 부분 원문이 활성 사용자 카드만 갱신되고, user release 전 final 결과는 pending으로 보관하며, release 또는 탭 종료 후의 확정 원문만 번역되는지 확인한다.
4. 온디바이스 STT가 불가한 조건에서 네트워크 fallback이 발생하지 않고 복구 안내를 표시하는지 확인한다.
5. `Hy-MT2-1.8B`의 공식 Supported Languages 표를 확인하고 위 38개 항목만 읽을 언어 후보로 사용한다.
6. 각 플랫폼의 실제 기기에서 Hy-MT2 초기화, A→B 및 B→A 번역, 번역 직렬 처리, 런타임 실패 시 가짜 번역 없는 오류 표시를 확인한다.
7. 결과 언어, 실패 여부, 모델/아티팩트 버전, 기기와 OS 버전을 증거 로그에 남긴다.

## 현재 검증 상태

- Android의 단위 테스트·debug build·lint·Android test APK build와 physical Android 16 UI 테스트 6개는 통과했다.
- iOS strict SwiftLint(위반 0개), unsigned generic build, 단위 테스트 8개, iPhone 17 simulator UI 테스트 4개는 통과했다.
- 이 결과는 A/B UI와 플랫폼 통합 경로의 자동 검증이다. 실제 Android/iPhone에서 여섯 입력 언어의 온디바이스 capability·인식, iPhone physical lifecycle, Hy-MT2 artifact load·초기화·직렬 번역 E2E는 아직 통과 증거가 없다.

## 증거 기록 양식

| 플랫폼 | 발화자 | 입력 | 대상 출력 | 온디바이스 STT capability/권한 | 단일 활성 PTT | 확정 STT만 번역 | Hy-MT2 runtime | 기기/OS | 결과 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Android | A |  |  |  |  |  |  |  | 대기 |
| Android | B |  |  |  |  |  |  |  | 대기 |
| iOS | A |  |  |  |  |  |  |  | 대기 |
| iOS | B |  |  |  |  |  |  |  | 대기 |
