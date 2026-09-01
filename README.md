# Realtime Translate Demo

Android와 iOS용 온디바이스 2인 push-to-talk 번역 MVP입니다.

두 사용자는 각자 A 또는 B 버튼을 눌러 자신의 발화를 지정합니다. 앱은 해당 사용자의 플랫폼 온디바이스 STT로 원문을 인식하고, 확정된 원문만 `SJ_zetic/Hy-MT2-1.8B`로 상대방의 읽기 언어에 번역합니다. 자동 화자 분리나 동시 발화 인식은 제공하지 않습니다.

두 앱은 같은 대화 흐름과 디자인 토큰을 따르며, 각 플랫폼의 네이티브 UI로 구현합니다.

## 문서

- [MVP 기획](docs/mvp-plan.md)
- [공통 UX 및 디자인 명세](docs/shared-ux-spec.md)
- [모델 언어 호환성 게이트](docs/model-compatibility-gate.md)

## 구현 디렉터리

- `android/`: Kotlin 및 Jetpack Compose 앱
- `ios/`: Swift 및 SwiftUI 앱

STT는 Android와 iOS의 온디바이스 음성 인식 기능을 사용합니다. 선택한 언어와 기기가 온디바이스 인식을 지원하고 필요한 권한을 허용한 경우에만 A 또는 B 발화를 시작하며, 네트워크 STT로 자동 전환하지 않습니다. 실제 모델·기기 지원 범위와 실시간 성능은 `docs/model-compatibility-gate.md`의 검증을 통과하기 전까지 확정된 기능으로 취급하지 않습니다.
