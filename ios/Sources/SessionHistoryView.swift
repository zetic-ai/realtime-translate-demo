import SwiftUI

struct SessionHistoryView: View {
  @ObservedObject var history: SessionHistory
  @ObservedObject var viewModel: RealtimeTranslateViewModel
  @Environment(\.dismiss) private var dismiss
  var initialID: String? = nil
  @State private var path: [String] = []
  @State private var query = ""
  @State private var openedInitial = false
  var body: some View {
    NavigationStack(path: $path) {
      List {
        if history.storageFailed {
          Text("history.storageError").foregroundStyle(DesignToken.error)
          Button("history.retry", action: history.retryStorage)
        }
        if history.savedSessions.isEmpty { Text("history.empty").foregroundStyle(DesignToken.textSecondary) }
        ForEach(history.savedSessions.filter { query.isEmpty || $0.title.localizedCaseInsensitiveContains(query) }) { session in
          NavigationLink(value: session.id) {
            VStack(alignment: .leading, spacing: 8) {
              Text(verbatim: session.title).font(TrilogyFont.text(.headline)).lineLimit(2)
              HStack { Text(session.startedAt, format: .dateTime.day().month().hour().minute()); Text(verbatim: session.durationText) }.font(TrilogyFont.text(.caption))
              HStack {
                Text(verbatim: session.turns.map(\.targetLanguage).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }.joined(separator: " · ").uppercased())
              }.font(TrilogyFont.text(.caption)).foregroundStyle(DesignToken.textSecondary)
            }.padding(.vertical, 4)
          }
        }
      }
      .navigationDestination(for: String.self) { id in SavedSessionView(history: history, viewModel: viewModel, id: id) }
      .onAppear { if let initialID, !openedInitial { openedInitial = true; path = [initialID] } }
      .scrollContentBackground(.hidden)
      .searchable(text: $query, prompt: Text("history.search"))
      .navigationTitle(Text("history.title"))
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          Text("history.title").font(TrilogyFont.text(.title3)).foregroundStyle(DesignToken.textPrimary)
        }
        ToolbarItem(placement: .navigationBarTrailing) {
          Button(LocalizedStringKey(viewModel.isSessionLive ? "history.return" : "history.new")) { dismiss() }
        }
      }
    }.tint(DesignToken.accent).foregroundStyle(DesignToken.textPrimary).font(TrilogyFont.text(.body))
  }
}

struct SavedSessionView: View {
  @ObservedObject var history: SessionHistory
  @ObservedObject var viewModel: RealtimeTranslateViewModel
  let id: String
  @Environment(\.dismiss) private var dismiss
  @State private var renaming = false
  @State private var deleting = false
  @State private var title = ""
  private var session: SavedSession? { history.sessions.first { $0.id == id } }

  var body: some View {
    Group {
      if let session {
        VStack(alignment: .leading, spacing: 12) {
          VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: session.title).font(TrilogyFont.text(.title3, emphasized: true))
            HStack { Text(session.startedAt, format: .dateTime.day().month().hour().minute()); Text(verbatim: session.durationText) }.font(TrilogyFont.text(.caption))
            Text("history.turnCount \(session.turns.count)").font(TrilogyFont.text(.caption)).foregroundStyle(DesignToken.textSecondary)
          }.padding(.horizontal, 16)
          Text("history.transcript").font(TrilogyFont.text(.headline)).padding(.horizontal, 16)
          transcript(session)
          HistoryStorageNotice(history: history)
        }
      } else { Text("history.empty") }
    }
    .navigationTitle(Text("history.session"))
    .navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .principal) {
        Text("history.session").font(TrilogyFont.text(.title3)).foregroundStyle(DesignToken.textPrimary)
      }
      ToolbarItem(placement: .navigationBarTrailing) {
      Menu {
        Button("history.rename") { title = session?.title ?? ""; renaming = true }
        Button("history.delete", role: .destructive) { deleting = true }
      } label: { Image(systemName: "ellipsis").frame(width: 44, height: 44) }
      .accessibilityLabel(Text("history.options"))
    } }
    .alert("history.rename", isPresented: $renaming) {
      TextField("history.session", text: $title)
      Button("history.save") { history.rename(id, title: title) }
      Button("history.cancel", role: .cancel) {}
    }
    .alert("history.deleteQuestion", isPresented: $deleting) {
      Button("history.delete", role: .destructive) { history.delete(id); if !history.storageFailed { dismiss() } }
      Button("history.cancel", role: .cancel) {}
    } message: { Text("history.deleteDetail") }
  }

  private func transcript(_ session: SavedSession) -> some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(spacing: 8) {
          ForEach(Array(session.turns.enumerated()), id: \.element.id) { index, turn in
            ChatBubbleRow(isTrailing: turn.speaker == "B") {
              VStack(alignment: .leading, spacing: 8) {
                Text(verbatim: "\(turn.speaker) · \(turn.targetLanguage.uppercased()) · \(index + 1)")
                  .font(TrilogyFont.text(.caption)).foregroundStyle(DesignToken.textSecondary)
                if let translation = turn.translation { Text(verbatim: translation).font(TrilogyFont.translation) }
                else { Text("history.translationUnavailable").font(TrilogyFont.text(.caption)) }
                Divider()
                Text(verbatim: turn.original).font(TrilogyFont.original).textSelection(.enabled)
              }.fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 16).padding(.vertical, 12)
                .background(turn.speaker == "A" ? DesignToken.tintA : DesignToken.tintB)
                .clipShape(RoundedRectangle(cornerRadius: Layout.message))
              }.id(turn.id)
          }
        }.padding(.horizontal, 16).padding(.vertical, 8)
      }
    }
  }
}

struct HistoryStorageNotice: View {
  @ObservedObject var history: SessionHistory
  var body: some View {
    if history.storageFailed {
      VStack {
        Text("history.storageError").font(TrilogyFont.text(.footnote))
        Button("history.retry", action: history.retryStorage)
      }.padding().background(DesignToken.surface).foregroundStyle(DesignToken.error)
    }
  }
}
