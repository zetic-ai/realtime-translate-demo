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
    case .permissionRequired: "Microphone Permission Required"
    case .ready: "Ready to Talk"
    case let .listening(speaker): "\(speaker.rawValue) is speaking"
    case let .finalizing(speaker): "Finalizing \(speaker.rawValue)'s transcript"
    case let .translating(speaker): "Translating for \(speaker.counterpart.rawValue)"
    case .ended: "Session Ended"
    case .error: "Unable to Process"
    }
  }
}

struct SpeechSourceLanguage: Identifiable, Hashable {
  static let automatic = SpeechSourceLanguage(identifier: "automatic", name: "Automatic")

  let identifier: String
  let name: String

  var id: String { identifier }
}

struct TargetLanguage: Identifiable, Hashable {
  let code: String
  let name: String
  var id: String { code }

  static let hyMT2Candidates = [
    ("zh", "Chinese"), ("en", "English"), ("fr", "French"), ("pt", "Portuguese"),
    ("es", "Spanish"), ("ja", "Japanese"), ("tr", "Turkish"), ("ru", "Russian"),
    ("ar", "Arabic"), ("ko", "Korean"), ("th", "Thai"), ("it", "Italian"),
    ("de", "German"), ("vi", "Vietnamese"), ("ms", "Malay"), ("id", "Indonesian"),
    ("tl", "Filipino"), ("hi", "Hindi"), ("zh-Hant", "Traditional Chinese"), ("pl", "Polish"),
    ("cs", "Czech"), ("nl", "Dutch"), ("km", "Khmer"), ("my", "Burmese"),
    ("fa", "Persian"), ("gu", "Gujarati"), ("ur", "Urdu"), ("te", "Telugu"),
    ("mr", "Marathi"), ("he", "Hebrew"), ("bn", "Bengali"), ("ta", "Tamil"),
    ("uk", "Ukrainian"), ("bo", "Tibetan"), ("kk", "Kazakh"), ("mn", "Mongolian"),
    ("ug", "Uyghur"), ("yue", "Cantonese")
  ].map(TargetLanguage.init)
}

struct HyMT2Request: Equatable {
  let userMessage: String

  init(sourceText: String, targetLanguage: TargetLanguage) {
    let instruction = "Translate the following text into \(targetLanguage.name). "
      + "Note that you should only output the translated result without any additional explanation:"
    userMessage = "\(instruction)\n\(sourceText)"
  }
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
