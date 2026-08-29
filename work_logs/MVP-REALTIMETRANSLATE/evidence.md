# MVP-REALTIMETRANSLATE 증거 로그

## 결정

- 2026-08-29: Android(Kotlin/Jetpack Compose)와 iOS(Swift/SwiftUI)를 별도 네이티브 앱으로 구현한다.
- 2026-08-29: 실제 모델 언어 호환성은 모델 카드와 실기기 결과로 검증하기 전까지 미확정이다.
- 2026-08-29: 원격 저장소 `zetic-ai/realtime-translate-demo`를 private으로 생성했다.
- 2026-08-29: Hy-MT2 공식 Supported Languages 표의 38개 항목을 출력 언어 UI 후보로 기록했다. 모델 카드 본문의 33개, 메타데이터의 36개 표기와 불일치하므로 실제 릴리스 전 재확인이 필요하다.
- 2026-08-29: `SJ_zetic/Hy-MT2-1.8B` 변환본은 공식 원본과 별도로 Android/iOS 실기기에서 초기화와 번역을 검증해야 한다.
- 2026-08-29: 승인된 갱신 계약에 따라 기존 외부 STT 모델을 제외하고 Android/iOS의 온디바이스 STT를 사용한다. 온디바이스 capability 또는 권한이 없는 언어는 비활성화하며, 네트워크 STT 자동 fallback은 사용하지 않는다.
- 2026-08-29: 마이크 PCM은 pyannote와 플랫폼 STT에 fan-out한다. Hy-MT2는 확정 STT만 번역하고, pyannote와 Hy-MT2는 직렬 실행한다. 구현 및 실기기 E2E 증거는 아직 기록되지 않았다.

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
| iOS | `swiftlint lint --strict --path ios/Sources` | 통과: strict SwiftLint 위반 0개 |
| iOS | `xcodebuild build -project ios/RealtimeTranslate.xcodeproj -scheme RealtimeTranslate -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO` | 통과: 서명 없이 iOS 빌드 성공 |
| iOS | `xcodebuild test -project ios/RealtimeTranslate.xcodeproj -scheme RealtimeTranslate -destination 'platform=iOS Simulator,name=iPhone 16'` | 통과: 단위 테스트 7개 중 7개, 전체 UI 테스트 5개 중 5개 통과 |

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

- 모델 카드·라이선스·모바일 배포 형식 확인
- **잔여 차단:** 실제 Android API 33 이상과 iPhone에서 한국어·중국어·일본어·영어·프랑스어·스페인어 각각의 온디바이스 STT capability와 권한을 확인해야 한다. 실제 `SpeechRecognizer`/`SFSpeechRecognizer` 인식, pyannote PCM fan-out 및 화자 분리, 확정 STT만의 Hy-MT2 번역, pyannote·Hy-MT2 직렬 실행을 언어 조합별로 E2E 검증해야 한다. 현재 빌드와 시뮬레이터 테스트는 이 실기기 검증을 대체하지 않는다.

## 갱신 작업 실행 로그

| 시각 | 파일 또는 명령 | 결과 | 비고 |
| --- | --- | --- | --- |
| 2026-08-29 | README·docs·evidence log 문자열 검사 | 0건 | 제거된 STT 모델 문자열 검사 |
| 2026-08-29 | `rg -n -i '네트워크.*fallback|fallback.*네트워크' README.md docs work_logs/MVP-REALTIMETRANSLATE` | 금지 정책 명시 확인 | 자동 네트워크 fallback 금지 검사 |
| 2026-08-29 | Android·iOS 구현 검증 | 제거된 STT 및 네트워크 STT 전환 실행 경로 0건 | P1 보완 후 정책 검사 |
| 2026-08-29 | `git diff --check` | 통과 | 문서 갱신 diff 검사 |
