# Realtime Translate Demo

Android와 iOS용 온디바이스 실시간 대화 번역 MVP입니다.

두 앱은 같은 대화 흐름과 디자인 토큰을 따르며, 각 플랫폼의 네이티브 UI로 구현합니다.

## 문서

- [MVP 기획](docs/mvp-plan.md)
- [공통 UX 및 디자인 명세](docs/shared-ux-spec.md)
- [모델 언어 호환성 게이트](docs/model-compatibility-gate.md)

## 구현 디렉터리

- `android/`: Kotlin 및 Jetpack Compose 앱
- `ios/`: Swift 및 SwiftUI 앱

실제 모델 지원 범위와 실시간 성능은 `docs/model-compatibility-gate.md`의 검증을 통과하기 전까지 확정된 기능으로 취급하지 않습니다.
