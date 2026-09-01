# MVP-REALTIMETRANSLATE 증거 로그

## Phase 2 — 2인 Push-to-Talk 재설계

### 결정

- 2026-09-01: 승인된 방향 전환에 따라 자동 화자 분리와 별도 음성 모델을 제거한다. A/B 버튼을 누른 사용자가 발화자의 유일한 귀속 근거다.
- 2026-09-01: A와 B의 발화는 한 번에 하나만 허용한다. 활성 사용자의 버튼 해제 또는 탭 종료 뒤에만 확정 STT와 번역을 진행한다.
- 2026-09-01: A 원문은 B의 읽을 언어로, B 원문은 A의 읽을 언어로 번역한다. partial은 활성 사용자 카드만 갱신하며, Hy-MT2는 확정 원문만 직렬 처리한다.
- 2026-09-01: Hy-MT2 모바일 runtime은 실기기 검증 전까지 미검증이다. 초기화·실행 실패 시 가짜 번역을 표시하지 않고 원문을 보존한 오류 상태를 표시한다.
- 2026-09-01: iOS가 user release 전에 final 결과를 보고해도 pending transcript로만 보관한다. 사용자 종료 후에만 final 처리와 번역을 시작해 Android와 동일한 PTT 타이밍을 유지한다.

### 증거와 잔여 검증

| 항목 | 상태 | 필요한 증거 |
| --- | --- | --- |
| A/B 수동 귀속과 단일 활성 PTT UI | Android/iOS 자동 UI 검증 통과 | 실제 두 사용자 발화의 실기기 확인 |
| A/B별 6개 언어 온디바이스 STT | 차단 | 실제 Android·iPhone capability·권한·인식 결과 |
| 네트워크 STT 자동 fallback 금지 | 구현 경로 및 문서 검증 통과 | 권한/미지원 실기기 오류 시나리오 |
| Hy-MT2 A→B/B→A 번역 | 차단 | 개인 키로 모델 초기화 후 실기기 runtime 결과 |
| 번역 직렬 처리와 오류 보존 | 자동 테스트 통과, runtime E2E 차단 | Hy-MT2 artifact load와 실기기 runtime 실패 시나리오 |

### Phase 2 플랫폼 검증

| 플랫폼 | 명령 또는 환경 | 결과 |
| --- | --- | --- |
| Android | external wrapper `-p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` | 통과 |
| Android | physical `2512BPNDAG`, Android 16 `connectedDebugAndroidTest` | UI 6개 중 6개 통과 |
| iOS | strict SwiftLint | 위반 0개 |
| iOS | unsigned generic iOS build | 통과 |
| iOS | unit tests | 8개 중 8개 통과 |
| iOS | iPhone 17 simulator UI tests | 4개 중 4개 통과 |

### Phase 2 문서 검증

| 확인 | 방법 | 결과 |
| --- | --- | --- |
| 제거 대상 문서 문자열 | README·docs·이 증거 로그에서 이전 음성 모델 식별자 검색 | 통과: 0건 |
| 단일 활성 PTT 계약 | README·docs에서 A/B, 단일 활성, 동시 발화 미지원 검색 | 통과: 계약 문구 확인 |
| 가짜 번역 금지 계약 | README·docs에서 runtime 미검증 및 오류 보존 검색 | 통과: 계약 문구 확인 |
| 이전 음성 분석·오디오 분기 참조 | 저장소 검색 | 통과: 0건 |
| 온라인 STT 자동 fallback | 구현 경로와 문서 검색 | 통과: 0건 |

## 결정

- 2026-08-29: Android(Kotlin/Jetpack Compose)와 iOS(Swift/SwiftUI)를 별도 네이티브 앱으로 구현한다.
- 2026-08-29: 실제 모델 언어 호환성은 모델 카드와 실기기 결과로 검증하기 전까지 미확정이다.
- 2026-08-29: 원격 저장소 `zetic-ai/realtime-translate-demo`를 private으로 생성했다.
- 2026-08-29: Hy-MT2 공식 Supported Languages 표의 38개 항목을 출력 언어 UI 후보로 기록했다. 모델 카드 본문의 33개, 메타데이터의 36개 표기와 불일치하므로 실제 릴리스 전 재확인이 필요하다.
- 2026-08-29: `SJ_zetic/Hy-MT2-1.8B` 변환본은 공식 원본과 별도로 Android/iOS 실기기에서 초기화와 번역을 검증해야 한다.
- 2026-08-29: 승인된 갱신 계약에 따라 기존 외부 STT 모델을 제외하고 Android/iOS의 온디바이스 STT를 사용한다. 온디바이스 capability 또는 권한이 없는 언어는 비활성화하며, 네트워크 STT 자동 fallback은 사용하지 않는다.
- 2026-08-29: 이전 단일 마이크 fan-out 설계는 문서화되었으나, 2026-09-01의 2인 PTT 설계로 대체되었다. 당시의 구현 및 실기기 E2E 증거는 기록되지 않았다.

## 초기 확인

| 확인 | 명령 또는 방법 | 결과 |
| --- | --- | --- |
| GitHub 인증 | `gh auth status` | `shinil-zetic` 계정, `repo` 권한 확인 |
| 원격 저장소 생성 | `gh repo create zetic-ai/realtime-translate-demo --private` | 성공 |
| 저장소 복제 | `git clone https://github.com/zetic-ai/realtime-translate-demo.git ...` | 성공; 빈 저장소 |

## 플랫폼 구현 검증

| 플랫폼 | 명령 | 결과 |
| --- | --- | --- |
| Android | `/Users/shinilheo/Melange/mlange_sdk/android/gradlew -p /Users/shinilheo/Melange/realtime-translate-demo/android test lintDebug assembleDebug assembleDebugAndroidTest` | 통과 |
| Android | `/Users/shinilheo/Melange/mlange_sdk/android/gradlew -p /Users/shinilheo/Melange/realtime-translate-demo/android connectedDebugAndroidTest` | 통과: API 34 AVD `lfm_api34`에서 UI 테스트 9개 중 9개 통과; 검증 후 emulator 종료 |
| iOS | strict SwiftLint | 통과: 위반 0개 |
| iOS | unsigned generic iOS build | 통과 |
| iOS | unit tests | 통과: 8개 중 8개 |
| iOS | iPhone 17 simulator UI tests | 통과: 4개 중 4개 |

## 구현 정책 보완 증거

- Android API 31~32는 on-device service가 있으면 여섯 입력 언어를 provisional로 시작하며, 언어별 온디바이스 실패를 `onDeviceUnsupported` 오류로 표면화한다. API 33 이상은 installed와 downloadable을 구분하고, downloadable 언어는 `언어 모델 다운로드` 버튼으로 실제 다운로드 요청에 도달한다. API 33 다운로드 완료 뒤에는 사용자 재시작, API 34 이상은 callback 뒤 재probe·시작 흐름을 적용한다.
- iOS 온디바이스 STT gate와 새 세션 시작 전 기존 세션 stop lifecycle 보완을 반영했다.

## 문서 계약 검증

| 확인 | 방법 | 결과 |
| --- | --- | --- |
| 제거된 STT 모델 문자열 | README·docs·evidence log 검사 | 0건 |
| 자동 네트워크 fallback 금지 | `rg -n -i '네트워크.*fallback|fallback.*네트워크' README.md docs work_logs/MVP-REALTIMETRANSLATE` | 금지 정책 명시 확인 |
| 플랫폼 실행 경로 | Android·iOS 구현 검증 | 제거된 STT 및 네트워크 STT 전환 실행 경로 0건 |

## 후속 검증

- **잔여 차단:** 실제 Android와 iPhone에서 한국어·중국어·일본어·영어·프랑스어·스페인어 각각의 온디바이스 STT capability·권한·인식 결과를 확인해야 한다. iPhone physical STT lifecycle도 미검증이다. `SJ_zetic/Hy-MT2-1.8B` artifact load, 모델 초기화, A→B/B→A 직렬 번역 E2E는 실제 기기와 runtime 증거가 필요하다. 현재 빌드·Android UI 실기기·iOS 시뮬레이터 테스트는 이 검증을 대체하지 않는다.

## 갱신 작업 실행 로그

| 시각 | 파일 또는 명령 | 결과 | 비고 |
| --- | --- | --- | --- |
| 2026-08-29 | README·docs·evidence log 문자열 검사 | 0건 | 제거된 STT 모델 문자열 검사 |
| 2026-08-29 | `rg -n -i '네트워크.*fallback|fallback.*네트워크' README.md docs work_logs/MVP-REALTIMETRANSLATE` | 금지 정책 명시 확인 | 자동 네트워크 fallback 금지 검사 |
| 2026-08-29 | Android·iOS 구현 검증 | 제거된 STT 및 네트워크 STT 전환 실행 경로 0건 | P1 보완 후 정책 검사 |
| 2026-08-29 | `git diff --check` | 통과 | 문서 갱신 diff 검사 |
