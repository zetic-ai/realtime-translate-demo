# MVP-REALTIMETRANSLATE 증거 로그

## 결정

- 2026-08-29: Android(Kotlin/Jetpack Compose)와 iOS(Swift/SwiftUI)를 별도 네이티브 앱으로 구현한다.
- 2026-08-29: 실제 모델 언어 호환성은 모델 카드와 실기기 결과로 검증하기 전까지 미확정이다.
- 2026-08-29: 원격 저장소 `zetic-ai/realtime-translate-demo`를 private으로 생성했다.
- 2026-08-29: Hy-MT2 공식 Supported Languages 표의 38개 항목을 출력 언어 UI 후보로 기록했다. 모델 카드 본문의 33개, 메타데이터의 36개 표기와 불일치하므로 실제 릴리스 전 재확인이 필요하다.
- 2026-08-29: `SJ_zetic/Hy-MT2-1.8B` 변환본은 공식 원본과 별도로 Android/iOS 실기기에서 초기화와 번역을 검증해야 한다.

## 초기 확인

| 확인 | 명령 또는 방법 | 결과 |
| --- | --- | --- |
| GitHub 인증 | `gh auth status` | `shinil-zetic` 계정, `repo` 권한 확인 |
| 원격 저장소 생성 | `gh repo create zetic-ai/realtime-translate-demo --private` | 성공 |
| 저장소 복제 | `git clone https://github.com/zetic-ai/realtime-translate-demo.git ...` | 성공; 빈 저장소 |

## 플랫폼 구현 검증

| 플랫폼 | 명령 | 결과 |
| --- | --- | --- |
| Android | `cd android && ./gradlew :app:connectedDebugAndroidTest` | 통과: API 34 AVD `lfm_api34` (`emulator-5554`)에서 Compose UI 테스트 3개 중 3개 통과; 검증 후 AVD 종료 |
| Android | `cd android && ./gradlew test lintDebug assembleDebug` | 통과: 단위 테스트 2개 통과, `lintDebug` 오류 0개·경고 9개, debug APK 조립 성공 |
| iOS | `swiftlint lint --strict --path ios/Sources` | 통과: strict SwiftLint 위반 없음 |
| iOS | `xcodebuild build -project ios/RealtimeTranslate.xcodeproj -scheme RealtimeTranslate -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO` | 통과: 서명 없이 iOS 빌드 성공 |
| iOS | `xcodebuild test -project ios/RealtimeTranslate.xcodeproj -scheme RealtimeTranslate -destination 'platform=iOS Simulator,name=iPhone 16'` | 통과: 단위 테스트 2개, UI 테스트 4개 통과 |

## 후속 검증

- 모델 카드·라이선스·모바일 배포 형식 확인
- **잔여 차단:** 실제 Android와 iOS 기기에서 승인된 4개 모델의 로드, 화자 분리, 스트리밍 STT, 번역을 언어 조합별로 E2E 검증해야 한다. 현재 빌드와 시뮬레이터 테스트는 이 실기기 검증을 대체하지 않는다.
