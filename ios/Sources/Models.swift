import Foundation

// swiftlint:disable identifier_name
enum Speaker: String, CaseIterable, Identifiable {
  case a = "A"
  case b = "B"

  var id: String { rawValue }
  var counterpart: Speaker { self == .a ? .b : .a }
}
// swiftlint:enable identifier_name

enum SessionState: Equatable {
  case permissionRequired
  case ready
  case listening(Speaker)
  case finalizing(Speaker)
  case translating(Speaker)
  case ended
  case error(String)

  var title: String {
    switch self {
    case .permissionRequired: "마이크 권한 필요"
    case .ready: "대화 준비됨"
    case let .listening(speaker): "\(speaker.rawValue)가 말하는 중"
    case let .finalizing(speaker): "\(speaker.rawValue) 원문 확정 중"
    case let .translating(speaker): "\(speaker.counterpart.rawValue)에게 번역 중"
    case .ended: "세션이 종료되었습니다"
    case .error: "처리할 수 없습니다"
    }
  }
}

enum SpokenLanguage: String, CaseIterable, Identifiable {
  case korean = "한국어"
  case chinese = "중국어"
  case japanese = "일본어"
  case english = "영어"
  case french = "프랑스어"
  case spanish = "스페인어"

  var id: String { rawValue }
}

struct TargetLanguage: Identifiable, Hashable {
  let code: String
  let name: String
  var id: String { code }

  static let hyMT2Candidates = [
    ("zh", "중국어"), ("en", "영어"), ("fr", "프랑스어"), ("pt", "포르투갈어"),
    ("es", "스페인어"), ("ja", "일본어"), ("tr", "터키어"), ("ru", "러시아어"),
    ("ar", "아랍어"), ("ko", "한국어"), ("th", "태국어"), ("it", "이탈리아어"),
    ("de", "독일어"), ("vi", "베트남어"), ("ms", "말레이어"), ("id", "인도네시아어"),
    ("tl", "타갈로그어"), ("hi", "힌디어"), ("zh-Hant", "번체 중국어"), ("pl", "폴란드어"),
    ("cs", "체코어"), ("nl", "네덜란드어"), ("km", "크메르어"), ("my", "미얀마어"),
    ("fa", "페르시아어"), ("gu", "구자라트어"), ("ur", "우르두어"), ("te", "텔루구어"),
    ("mr", "마라티어"), ("he", "히브리어"), ("bn", "벵골어"), ("ta", "타밀어"),
    ("uk", "우크라이나어"), ("bo", "티베트어"), ("kk", "카자흐어"), ("mn", "몽골어"),
    ("ug", "위구르어"), ("yue", "광둥어")
  ].map(TargetLanguage.init)
}

extension TargetLanguage {
  init(_ value: (String, String)) {
    code = value.0
    name = value.1
  }
}

struct ConversationItem: Identifiable, Equatable {
  enum DeliveryState: Equatable { case partial, finalizing, translationFailed(String), translated }
  let id: UUID
  let speaker: Speaker
  let transcript: String
  let targetLanguage: TargetLanguage
  let translation: String?
  let state: DeliveryState
}
