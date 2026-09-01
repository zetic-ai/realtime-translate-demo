import SwiftUI

enum DesignToken {
  static let primary = Color(red: 59 / 255, green: 91 / 255, blue: 219 / 255)
  static let secondary = Color(red: 11 / 255, green: 114 / 255, blue: 133 / 255)
  static let surfaceMuted = Color(red: 241 / 255, green: 243 / 255, blue: 245 / 255)
  static let textPrimary = Color(red: 31 / 255, green: 41 / 255, blue: 55 / 255)
  static let textSecondary = Color(red: 107 / 255, green: 114 / 255, blue: 128 / 255)
  static let error = Color(red: 201 / 255, green: 42 / 255, blue: 42 / 255)
}

struct RealtimeTranslateRootView: View {
  @StateObject var viewModel: RealtimeTranslateViewModel

  var body: some View {
    NavigationStack {
      Group {
        switch viewModel.state {
        case .permissionRequired:
          SetupView(viewModel: viewModel)
        case .error where viewModel.items.isEmpty:
          ErrorView(reason: errorReason, retry: viewModel.requestMicrophonePermission)
        default:
          ConversationView(viewModel: viewModel)
        }
      }
      .navigationTitle("실시간 번역")
    }
    .tint(DesignToken.primary)
  }

  private var errorReason: String {
    if case let .error(reason) = viewModel.state { return reason }
    return "처리할 수 없습니다."
  }
}

private struct SetupView: View {
  @ObservedObject var viewModel: RealtimeTranslateViewModel

  var body: some View {
    Form {
      speakerSection(.a, source: $viewModel.sourceLanguageA, target: $viewModel.targetLanguageA)
      speakerSection(.b, source: $viewModel.sourceLanguageB, target: $viewModel.targetLanguageB)
      Section("권한") {
        Text("마이크와 음성 인식 권한, 선택 언어의 온디바이스 모델이 필요합니다.")
          .foregroundStyle(DesignToken.textSecondary)
        Button("마이크 권한 허용", action: viewModel.requestMicrophonePermission)
        Button("앱 설정 열기", action: viewModel.openAppSettings)
      }
    }
  }

  @ViewBuilder private func speakerSection(
    _ speaker: Speaker, source: Binding<SpokenLanguage>, target: Binding<TargetLanguage>
  ) -> some View {
    Section("\(speaker.rawValue) 설정") {
      Picker("\(speaker.rawValue) 발화 언어", selection: source) {
        ForEach(viewModel.availableSourceLanguages) { Text($0.rawValue).tag($0) }
      }
      .accessibilityIdentifier("source-language-\(speaker.rawValue)")
      Picker("\(speaker.rawValue)가 읽을 번역 언어", selection: target) {
        ForEach(TargetLanguage.hyMT2Candidates) { Text($0.name).tag($0) }
      }
      .accessibilityIdentifier("target-language-\(speaker.rawValue)")
    }
  }
}

private struct ConversationView: View {
  @ObservedObject var viewModel: RealtimeTranslateViewModel

  var body: some View {
    VStack(spacing: 0) {
      StatusHeader(state: viewModel.state)
      List(viewModel.items) { ConversationBubble(item: $0) }
        .listStyle(.plain)
      controls
    }
  }

  private var controls: some View {
    VStack(spacing: 8) {
      if case .error = viewModel.state { ErrorBanner(reason: "음성 인식을 다시 시도하거나 설정을 확인해 주세요.") }
      if viewModel.state == .ended {
        Button("새 세션 시작", action: viewModel.beginNewSession).buttonStyle(.borderedProminent)
      } else {
        HStack(spacing: 12) {
          PTTButton(speaker: .a, state: viewModel.state, begin: viewModel.beginTurn, end: viewModel.endTurn)
          PTTButton(speaker: .b, state: viewModel.state, begin: viewModel.beginTurn, end: viewModel.endTurn)
        }
        Button("세션 종료", action: viewModel.endSession)
          .accessibilityLabel("세션 종료")
      }
    }
    .padding(16)
  }
}

private struct PTTButton: View {
  let speaker: Speaker
  let state: SessionState
  let begin: (Speaker) -> Void
  let end: (Speaker) -> Void

  private var isListening: Bool { state == .listening(speaker) }
  private var isBlocked: Bool {
    switch state {
    case .ready: return false
    case let .listening(active): return active != speaker
    default: return true
    }
  }
  private var label: String {
    isListening ? "\(speaker.rawValue) 발화 종료" : "\(speaker.rawValue) 길게 눌러 말하기"
  }

  var body: some View {
    Button(action: { isListening ? end(speaker) : begin(speaker) }, label: {
      Text(label).frame(maxWidth: .infinity)
    })
    .buttonStyle(.borderedProminent)
    .tint(speaker == .a ? DesignToken.primary : DesignToken.secondary)
    .disabled(isBlocked)
    .simultaneousGesture(LongPressGesture(minimumDuration: 0.15).onEnded { _ in
      if !isListening && !isBlocked { begin(speaker) }
    })
    .accessibilityLabel(isListening ? "\(speaker.rawValue) 발화 종료" : "\(speaker.rawValue) 발화 시작")
    .accessibilityHint(
      isBlocked ? "다른 발화 또는 번역 처리가 진행 중입니다." : "길게 눌러 말하거나 탭하여 시작하고 다시 탭하여 종료합니다."
    )
  }
}

private struct StatusHeader: View {
  let state: SessionState
  var body: some View {
    Label(state.title, systemImage: "mic.fill")
      .font(.subheadline).foregroundStyle(DesignToken.textPrimary)
      .frame(maxWidth: .infinity, alignment: .leading).padding(12).background(DesignToken.surfaceMuted)
      .accessibilityLabel(state.title)
  }
}

private struct ConversationBubble: View {
  let item: ConversationItem
  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text("\(item.speaker.rawValue) · \(item.targetLanguage.name)에게")
        .font(.caption).foregroundStyle(DesignToken.textSecondary)
      Text(item.transcript.isEmpty ? "듣는 중…" : item.transcript).font(.body).foregroundStyle(DesignToken.textPrimary)
      switch item.state {
      case .partial, .finalizing: Text("원문을 확정하는 중").font(.caption).foregroundStyle(DesignToken.textSecondary)
      case .translated: if let translation = item.translation { Text(translation).foregroundStyle(DesignToken.primary) }
      case let .translationFailed(reason): Text(reason).font(.caption).foregroundStyle(DesignToken.error)
      }
    }
    .padding(12).background(DesignToken.surfaceMuted).clipShape(RoundedRectangle(cornerRadius: 16))
    .accessibilityElement(children: .combine)
  }
}

private struct ErrorBanner: View {
  let reason: String
  var body: some View { Text(reason).font(.caption).foregroundStyle(DesignToken.error).padding(.horizontal, 16) }
}

private struct ErrorView: View {
  let reason: String
  let retry: () -> Void
  var body: some View {
    VStack(spacing: 12) {
      Label("처리할 수 없습니다", systemImage: "exclamationmark.triangle.fill")
      Text(reason).multilineTextAlignment(.center)
      Button("다시 시도", action: retry)
    }.foregroundStyle(DesignToken.error).padding(16)
  }
}
