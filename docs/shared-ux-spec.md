# Android/iOS 공통 UX 및 디자인 명세

## 일관성 원칙

두 플랫폼은 Jetpack Compose와 SwiftUI의 관용적 제어 요소를 사용하되, 정보 구조, A/B의 의미, 상태 전이, 용어, 메시지 의미, 토큰 값은 동일하게 유지한다. 플랫폼의 기본 내비게이션·권한 안내·햅틱은 각 OS 규칙을 따른다.

## 화면 구조

1. **대화자 설정**: A와 B 각각의 말하기 언어·읽을 번역 언어, 마이크·음성 인식 권한과 온디바이스 capability 상태, `대화 시작` 버튼.
2. **대화**: 상태 헤더, 시간순 발화 카드, 하단의 A/B push-to-talk 버튼.
3. **오류 안내**: 실패한 사용자·언어와 원인, `다시 시도` 또는 `설정 열기` 동작.

### 대화 화면 레이아웃

```text
상태 헤더: "대화 준비됨" | "A가 말하는 중" | "번역 중"

[ A · 한국어 ]                 [ B · English ]
  안녕하세요                    Hello
  B에게: Hello                  A에게: 안녕하세요

────────────────────────────────────
[ A 길게 눌러 말하기 ] [ B 길게 눌러 말하기 ]
```

- A 카드와 버튼은 A의 발화만, B 카드와 버튼은 B의 발화만 만든다.
- A 원문은 `B에게: <B 읽기 언어>`로, B 원문은 `A에게: <A 읽기 언어>`로 대상 언어를 명시한다.
- 활성 발화의 부분 원문은 기존의 활성 카드 한 장만 갱신한다. 아직 확정되지 않은 부분 원문은 번역하지 않는다.
- A 또는 B가 활성일 때 반대쪽 버튼은 비활성화하고 이유를 텍스트로 알린다. 두 버튼의 동시 녹음은 지원하지 않는다.

## 공통 상태 전이

| 상태 | 표시 | 허용 동작 | 다음 상태 |
| --- | --- | --- | --- |
| `permissionRequired` | 권한 필요 | 권한 요청, 설정 열기 | `ready`, `error` |
| `ready` | A/B 버튼 사용 가능 | A 또는 B 시작, 언어 변경 | `listeningA`, `listeningB` |
| `listeningA` | "A가 말하는 중"과 A partial 카드 | A 종료 | `finalizingA`, `error` |
| `listeningB` | "B가 말하는 중"과 B partial 카드 | B 종료 | `finalizingB`, `error` |
| `finalizingA` / `finalizingB` | "A/B 원문 확정 중" | 완료 대기 | `translatingA`, `translatingB`, `error` |
| `translatingA` / `translatingB` | "B/A에게 번역 중" | 완료 대기 | `ready`, `error` |
| `error` | 실패 이유와 복구 동작, 기존 카드 유지 | 재시도, 설정 열기 | `ready`, `permissionRequired` |

플랫폼 STT가 사용자 종료 전에 final 결과를 알리더라도, 앱은 이를 활성 카드의 pending transcript로만 보관한다. 버튼 해제 또는 탭 토글 종료가 일어난 뒤에만 `listening*`에서 `finalizing*`로 이동하고 final·번역을 시작한다. 확정 원문이 없으면 카드를 완료하지 않고 `ready`로 돌아간다. 번역 오류가 나도 확정 원문 카드는 남기며, 번역 영역에 오류 상태를 표시한다.

## A/B 입력 방식과 접근성

- 기본 동작은 길게 누르는 동안 녹음하고 손을 떼면 종료하는 push-to-talk이다.
- 같은 버튼은 접근성 대체 동작으로 탭하여 시작하고, 같은 버튼을 다시 탭하여 종료할 수 있다. 화면에 현재 동작을 텍스트로 표시한다.
- 접근성 라벨은 `A 발화 시작`, `A 발화 종료`, `B 발화 시작`, `B 발화 종료`처럼 현재 동작과 사용자를 포함한다.
- 반대 버튼이 비활성화되면 `A 발화가 진행 중이므로 B 발화를 시작할 수 없음`과 동등한 설명을 제공한다.

## 온디바이스 STT 사전 조건

- Android API 31~32는 on-device service가 있으면 선택 언어를 provisional로 시작할 수 있다. 시작 시 언어별 온디바이스 인식이 실패하면 `onDeviceUnsupported` 오류와 복구 방법을 표시한다. API 33 이상은 installed-on-device와 downloadable 상태를 구분한다.
- iOS는 선택 locale의 `supportsOnDeviceRecognition` 및 `isAvailable`가 참이고 Speech·마이크 권한이 허용되어야 한다. STT 요청에는 `requiresOnDeviceRecognition = true`를 설정한다.
- 위 조건 중 하나라도 충족하지 않으면 해당 사용자 버튼을 시작 가능으로 표시하지 않거나 `error` 상태로 안내한다. 자동 네트워크 STT fallback은 사용하지 않는다.

## 번역 실행 게이트

- A와 B의 읽을 언어 선택기는 [모델 언어 호환성 게이트](model-compatibility-gate.md)의 38개 항목을 모두 표시한다.
- 번역은 확정 원문에만 실행하고, A는 B의 읽을 언어로, B는 A의 읽을 언어로 번역한다.
- 선택 언어 조합의 Hy-MT2 초기화, 모델 실행 또는 실기기 호환성 검증이 실패하면 번역을 가장하거나 빈 번역 버블을 만들지 않는다. 원문 카드를 보존하고 번역 영역에 오류와 복구 동작을 표시한다.
- Hy-MT2 요청은 직렬로 처리한다. 대기 중인 카드에는 대상 사용자와 `번역 대기 중`을 표시한다.

## 디자인 토큰

| 토큰 | 값 | 사용 |
| --- | --- | --- |
| `color.primary` | `#3B5BDB` | 주요 행동, 활성 A 버튼 |
| `color.secondary` | `#0B7285` | 활성 B 버튼 |
| `color.surface` | `#FFFFFF` | 기본 배경 |
| `color.surfaceMuted` | `#F1F3F5` | 비활성 제어·대기 카드 |
| `color.textPrimary` | `#1F2937` | 본문 |
| `color.textSecondary` | `#6B7280` | 보조 정보 |
| `color.error` | `#C92A2A` | 오류 |
| `space.1/2/3/4` | `4/8/12/16 dp·pt` | 공통 간격 |
| `radius.message` | `16 dp·pt` | 채팅 카드 |
| `radius.control` | `20 dp·pt` | A/B PTT 버튼 |
| `type.body` | `16 sp·pt` | 원문·번역문 |
| `type.meta` | `12 sp·pt` | 사용자·상태·대상 언어 |

Android는 dp/sp, iOS는 pt와 Dynamic Type을 적용하되 표의 시각적 크기와 위계를 유지한다. 시스템 다크 모드 지원은 MVP 범위에 포함하지 않으며, 구현 시 강제 테마 전환을 추가하지 않는다.

## Android/iOS parity 기준

| 시나리오 | 두 플랫폼에서 같은 결과 |
| --- | --- |
| A 시작 | A partial 카드와 A 활성 표시, B 버튼 비활성화 |
| B 시작 | B partial 카드와 B 활성 표시, A 버튼 비활성화 |
| release 또는 탭 종료 | 종료 전 final 결과는 pending으로 보관하고, 종료 후에만 해당 원문을 확정하여 상대 사용자 언어로 번역 대기 |
| 번역 성공 | 원문·대상 언어·번역문이 같은 카드에 표시 |
| STT 미지원/권한 거부 | 시작하지 않고 원인과 복구 동작 표시, 네트워크 전환 없음 |
| 번역 runtime 미검증/실패 | 원문 보존, 가짜 번역 없음, 번역 오류와 재시도 표시 |

## 검증

- 모든 제어 요소에 동일한 의미의 접근성 라벨을 제공한다.
- 본문과 상태는 글자 크기 확대 시 잘리거나 겹치지 않아야 한다.
- 색상만으로 A/B 활성, 처리, 오류를 구별하지 않고, 텍스트와 아이콘을 함께 표시한다.
- Android와 iOS는 위 parity 표의 시나리오, 설정 화면, 대화 화면, 오류 화면을 동일한 입력으로 캡처해 비교한다.
