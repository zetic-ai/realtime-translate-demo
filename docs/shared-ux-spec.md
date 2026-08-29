# Android/iOS 공통 UX 및 디자인 명세

## 일관성 원칙

두 플랫폼은 각각 Jetpack Compose와 SwiftUI의 관용적 제어 요소를 사용하되, 정보 구조, 상태 전이, 용어, 메시지 의미, 토큰 값은 동일하게 유지한다. 플랫폼의 기본 내비게이션·권한 안내·햅틱은 각 OS 규칙을 따른다.

## 화면 구조

1. **세션 설정**: 입력 언어, 출력 언어, 마이크 권한 상태, 시작 버튼
2. **대화 세션**: 상태 헤더, 시간순 대화 목록, 녹음 시작/종료 제어
3. **오류 안내**: 복구 가능한 원인과 다시 시도 또는 설정 열기 동작

## 공통 상태

| 상태 | 표시 | 허용 동작 |
| --- | --- | --- |
| `permissionRequired` | 마이크 권한 필요 안내 | 권한 요청, 설정 열기 |
| `ready` | 언어 선택과 시작 가능 상태 | 언어 변경, 시작 |
| `recording` | 녹음 중 표시와 진행 중 발화 | 종료 |
| `processing` | 입력 종료 후 잔여 처리 중 표시 | 처리 완료 대기 |
| `finished` | 확정 대화 목록 | 새 세션 시작 |
| `error` | 실패 이유와 복구 동작 | 재시도 또는 설정 열기 |

부분 STT는 마지막 대화 항목 안에서 `processing` 상태로 표시하고, 확정 원문이 생길 때만 번역을 시작한다. 화자 정보는 확정된 발화에만 붙인다. 한 항목은 `화자 라벨`, `원문`, `번역문`, `확정/처리 상태`를 가진다.

## 온디바이스 STT 사전 조건

- Android API 31~32는 on-device service가 있으면 여섯 입력 언어를 provisional로 시작할 수 있다. 시작 시 언어별 온디바이스 인식이 실패하면 `onDeviceUnsupported` 오류와 복구 방법을 표시한다. API 33 이상에서는 installed-on-device와 downloadable 상태를 구분한다. installed-on-device probe를 통과한 언어만 선택·시작을 허용하며, downloadable 언어는 `언어 모델 다운로드` 버튼으로 실제 다운로드 요청을 시작한다. API 33에서는 다운로드 완료 뒤 사용자에게 세션 재시작을 안내하고, API 34 이상에서는 callback 뒤 probe를 다시 실행해 통과한 경우에만 시작한다.
- iOS는 선택 locale의 `supportsOnDeviceRecognition` 및 `isAvailable`가 참이고 Speech·마이크 권한이 허용되어야 한다. STT 요청에는 `requiresOnDeviceRecognition = true`를 설정한다.
- 위 조건 중 하나라도 충족하지 않으면 해당 언어를 시작 가능한 입력으로 표시하지 않거나 `error` 상태로 안내한다. 자동 네트워크 STT fallback은 사용하지 않는다.
- 두 플랫폼은 capability 확인 실패, 권한 거부, 언어 모델 미설치에 대해 같은 실패 원인과 복구 의미를 표시한다. Android는 API 31 미만, API 31~32의 `onDeviceUnsupported`, API 33 이상의 installed/downloadable 상태를, iOS는 온디바이스 인식을 지원하지 않는 locale을 명시적으로 구분한다.

## 출력 언어 선택과 실행 게이트

- 출력 언어 선택기는 [모델 언어 호환성 게이트](model-compatibility-gate.md)의 38개 항목을 모두 표시하고 선택을 허용한다.
- 입력 언어는 플랫폼 온디바이스 STT capability·권한을 통과해야 선택 및 세션 시작이 가능하다. 출력 언어 선택 자체는 지원 보증이 아니다. 세션 시작 또는 번역 직전에 선택 조합의 모델 초기화와 실기기 호환성 게이트를 확인한다.
- 초기화 또는 게이트가 실패하면 번역을 시도하거나 빈 번역 버블을 만들지 않는다. 공통 `error` 상태에서 선택 언어, 실패 원인, `다시 시도` 또는 `설정 열기`의 복구 동작을 표시한다.
- Android와 iOS는 같은 실패 조건에 같은 상태 키와 사용자 의미를 적용한다. 플랫폼별 오류 문구는 OS의 표현 방식에 맞출 수 있지만, 실패 원인과 복구 동작은 같아야 한다.

## 디자인 토큰

| 토큰 | 값 | 사용 |
| --- | --- | --- |
| `color.primary` | `#3B5BDB` | 주요 행동, 녹음 상태 |
| `color.surface` | `#FFFFFF` | 기본 배경 |
| `color.surfaceMuted` | `#F1F3F5` | 상대 화자 버블 |
| `color.textPrimary` | `#1F2937` | 본문 |
| `color.textSecondary` | `#6B7280` | 보조 정보 |
| `color.error` | `#C92A2A` | 오류 |
| `space.1/2/3/4` | `4/8/12/16 dp·pt` | 공통 간격 |
| `radius.message` | `16 dp·pt` | 채팅 버블 |
| `type.body` | `16 sp·pt` | 원문·번역문 |
| `type.meta` | `12 sp·pt` | 화자·상태 |

Android는 dp/sp, iOS는 pt와 Dynamic Type을 적용하되 표의 시각적 크기와 위계를 유지한다. 시스템 다크 모드 지원은 MVP 범위에 포함하지 않으며, 구현 시 강제 테마 전환을 추가하지 않는다.

## 접근성 및 검증

- 모든 제어 요소에 동일한 의미의 접근성 라벨을 제공한다.
- 본문과 상태는 글자 크기 확대 시 잘리거나 겹치지 않아야 한다.
- 색상만으로 녹음·처리·오류를 구별하지 않고, 텍스트와 아이콘을 함께 표시한다.
- Android와 iOS는 위 세 화면 및 여섯 공통 상태의 기준 스크린샷을 동일한 시나리오로 비교한다.
