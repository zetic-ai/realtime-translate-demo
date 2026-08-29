import SwiftUI

enum DesignToken {
  static let primary = Color(red: 59 / 255, green: 91 / 255, blue: 219 / 255)
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
        case .permissionRequired, .ready:
          SetupView(viewModel: viewModel)
        case .recording, .processing, .finished:
          ConversationView(viewModel: viewModel)
        case let .error(reason):
          if viewModel.items.isEmpty {
            ErrorView(reason: reason, retry: viewModel.start)
          } else {
            ConversationView(viewModel: viewModel, errorReason: reason)
          }
        }
      }
      .navigationTitle("실시간 번역")
    }
    .tint(DesignToken.primary)
  }
}

private struct SetupView: View {
  @ObservedObject var viewModel: RealtimeTranslateViewModel

  var body: some View {
    Form {
      Section("언어") {
        if viewModel.availableSourceLanguages.isEmpty {
          Text("사용 가능한 온디바이스 음성 인식 언어가 없습니다.")
            .foregroundStyle(DesignToken.textSecondary)
        } else {
          Picker("발화 언어", selection: $viewModel.sourceLanguage) {
            ForEach(viewModel.availableSourceLanguages) { Text($0.rawValue).tag($0) }
          }
          .accessibilityLabel("발화 언어")
        }
        Picker("번역 언어", selection: $viewModel.targetLanguage) {
          ForEach(TargetLanguage.hyMT2Candidates) { Text($0.name).tag($0) }
        }
        .accessibilityLabel("번역 언어")
      }
      Section("마이크") {
        Text(permissionText)
          .foregroundStyle(DesignToken.textSecondary)
        Button("마이크 권한 허용", action: viewModel.requestMicrophonePermission)
          .accessibilityLabel("마이크 권한 허용")
        Button("앱 설정 열기", action: viewModel.openAppSettings)
          .accessibilityLabel("앱 설정 열기")
      }
      if case .ready = viewModel.state {
        Section {
          Button("번역 시작", action: viewModel.start)
            .frame(maxWidth: .infinity)
            .accessibilityLabel("번역 시작")
            .disabled(viewModel.availableSourceLanguages.isEmpty)
        }
      }
    }
  }

  private var permissionText: String {
    if case .permissionRequired = viewModel.state {
      return "마이크 권한이 필요합니다."
    }
    return "마이크 권한이 허용되었습니다."
  }
}

private struct ConversationView: View {
  @ObservedObject var viewModel: RealtimeTranslateViewModel
  var errorReason: String?

  var body: some View {
    VStack(spacing: 0) {
      StatusHeader(state: viewModel.state)
      if let errorReason {
        ErrorBanner(reason: errorReason, retry: viewModel.beginNewSession)
      }
      List(viewModel.items) { item in
        ConversationBubble(item: item)
          .listRowSeparator(.hidden)
          .listRowBackground(Color.clear)
      }
      .listStyle(.plain)
      controls
    }
  }

  @ViewBuilder private var controls: some View {
    switch viewModel.state {
    case .recording:
      Button("녹음 종료", action: viewModel.stop)
        .buttonStyle(.borderedProminent)
        .accessibilityLabel("녹음 종료")
        .padding(16)
    case .finished, .error:
      Button("새 세션 시작", action: viewModel.beginNewSession)
        .buttonStyle(.borderedProminent)
        .accessibilityLabel("새 세션 시작")
        .padding(16)
    default:
      ProgressView("처리 중")
        .accessibilityLabel("처리 중")
        .padding(16)
    }
  }
}

private struct ErrorBanner: View {
  let reason: String
  let retry: () -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(reason)
      Button("새 세션 시작", action: retry)
        .accessibilityLabel("새 세션 시작")
    }
    .foregroundStyle(DesignToken.error)
    .padding(12)
    .accessibilityElement(children: .contain)
  }
}

private struct StatusHeader: View {
  let state: SessionState

  var body: some View {
    HStack(spacing: 8) {
      Image(systemName: icon)
      Text(state.title)
        .font(.subheadline)
    }
    .foregroundStyle(DesignToken.textPrimary)
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(12)
    .background(DesignToken.surfaceMuted)
    .accessibilityElement(children: .combine)
    .accessibilityLabel(state.title)
  }

  private var icon: String {
    switch state {
    case .recording: "mic.fill"
    case .processing: "hourglass"
    case .finished: "checkmark.circle.fill"
    default: "info.circle"
    }
  }
}

private struct ConversationBubble: View {
  let item: ConversationItem

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text(item.speaker ?? "발화 분석 중")
        .font(.caption)
        .foregroundStyle(DesignToken.textSecondary)
      Text(item.transcript)
        .font(.body)
        .foregroundStyle(DesignToken.textPrimary)
      if let translation = item.translation {
        Text(translation)
          .font(.body)
          .foregroundStyle(DesignToken.primary)
      } else {
        Text("번역을 준비하는 중")
          .font(.caption)
          .foregroundStyle(DesignToken.textSecondary)
      }
    }
    .padding(12)
    .background(DesignToken.surfaceMuted)
    .clipShape(RoundedRectangle(cornerRadius: 16))
    .accessibilityElement(children: .combine)
  }
}

private struct ErrorView: View {
  let reason: String
  let retry: () -> Void

  var body: some View {
    VStack(spacing: 12) {
      Label("처리할 수 없습니다", systemImage: "exclamationmark.triangle.fill")
        .font(.title3)
      Text(reason)
        .multilineTextAlignment(.center)
      Button("다시 시도", action: retry)
        .accessibilityLabel("다시 시도")
    }
    .foregroundStyle(DesignToken.error)
    .padding(16)
    .accessibilityElement(children: .contain)
  }
}
