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
            VStack(alignment: .leading, spacing: 6) {
              Text(verbatim: session.title).font(.headline).lineLimit(2)
              HStack { Text(session.startedAt, format: .dateTime.day().month().hour().minute()); Text(verbatim: session.durationText) }.font(.caption)
              HStack {
                Text(verbatim: session.turns.map(\.targetLanguage).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }.joined(separator: " · ").uppercased())
                Spacer()
                Text(summaryStatusKey(session.summaryStatus))
              }.font(.caption).foregroundStyle(DesignToken.textSecondary)
            }.padding(.vertical, 4)
          }
        }
      }
      .navigationDestination(for: String.self) { id in SavedSessionView(history: history, viewModel: viewModel, id: id) }
      .onAppear { if let initialID, !openedInitial { openedInitial = true; path = [initialID] } }
      .scrollContentBackground(.hidden)
      .searchable(text: $query, prompt: Text("history.search"))
      .navigationTitle(Text("history.title"))
      .toolbar { ToolbarItem(placement: .navigationBarTrailing) {
        Button(LocalizedStringKey(viewModel.isSessionLive ? "history.return" : "history.new")) { dismiss() }
      } }
    }.tint(DesignToken.accent).foregroundStyle(DesignToken.textPrimary)
  }
}

private func summaryStatusKey(_ status: SummaryStatus) -> LocalizedStringKey {
  switch status {
  case .pending: "history.summaryPending"
  case .generating: "history.generating"
  case .ready: "history.summaryReady"
  case .failed: "history.summaryFailed"
  }
}

struct SavedSessionView: View {
  @ObservedObject var history: SessionHistory
  @ObservedObject var viewModel: RealtimeTranslateViewModel
  let id: String
  @Environment(\.dismiss) private var dismiss
  @AppStorage("relais.summaryModelConsent.v1") private var consent = false
  @State private var showConsent = false
  @State private var tab = 0
  @State private var focus: String?
  @State private var renaming = false
  @State private var deleting = false
  @State private var title = ""
  @ScaledMetric(relativeTo: .body) private var translationSize = 16.0
  @ScaledMetric(relativeTo: .caption) private var originalSize = 13.0
  private var session: SavedSession? { history.sessions.first { $0.id == id } }

  var body: some View {
    Group {
      if let session {
        VStack(alignment: .leading, spacing: 12) {
          VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: session.title).font(.title3.bold())
            HStack { Text(session.startedAt, format: .dateTime.day().month().hour().minute()); Text(verbatim: session.durationText) }.font(.caption)
            Text("history.turnCount \(session.turns.count)").font(.caption).foregroundStyle(DesignToken.textSecondary)
          }.padding(.horizontal, 16)
          Picker("history.content", selection: $tab) {
            Text("history.summary").tag(0); Text("history.transcript").tag(1)
          }.pickerStyle(.segmented).padding(.horizontal, 16)
          if tab == 0 { summaryContent(session) } else { transcript(session) }
        }
      } else { Text("history.empty") }
    }
    .navigationTitle(Text("history.session"))
    .navigationBarTitleDisplayMode(.inline)
    .toolbar { ToolbarItem(placement: .navigationBarTrailing) {
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
    .alert("history.modelTitle", isPresented: $showConsent) {
      Button("history.download") { consent = true; viewModel.summarizeSession(id) }
      Button("history.later", role: .cancel) {}
    } message: { Text("history.modelDetail") }
    .onAppear {
      if consent, session?.summaryStatus == .pending { viewModel.summarizeSession(id) }
    }
  }

  private func generate() { if consent { viewModel.summarizeSession(id) } else { showConsent = true } }

  private func summaryContent(_ session: SavedSession) -> some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 20) {
        Text("history.aiNotice").font(.caption).foregroundStyle(DesignToken.textSecondary)
        if session.summaryStatus == .ready, let summary = session.summary {
          section("history.essentials", summary.essentials, session)
          section("history.decisions", summary.decisions, session)
          section("history.actions", summary.actions, session)
          section("history.questions", summary.questions, session)
        } else if session.summaryStatus == .generating {
          ProgressView(value: history.progress)
          Text("history.generating")
          Text("history.transcriptSafe").font(.subheadline)
          Button("history.stopSummary", action: history.interrupt)
        } else {
          Text(LocalizedStringKey(session.summaryStatus == .failed ? "history.summaryFailed" : "history.summaryPending")).font(.headline)
          Text("history.transcriptSafe").font(.subheadline)
          Button(LocalizedStringKey(session.summaryStatus == .failed ? "history.retry" : "history.generate"), action: generate)
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isSessionLive || viewModel.canCancelModelPreparation || history.storageFailed)
          if viewModel.isSessionLive { Text("history.endFirst").font(.caption) }
        }
        Button("history.readTranscript") { tab = 1 }
        if history.storageFailed { Text("history.storageError").foregroundStyle(DesignToken.error) }
      }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
    }
  }

  private func section(_ title: LocalizedStringKey, _ points: [SummaryPoint], _ session: SavedSession) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(title).font(.headline)
      if points.isEmpty { Text("history.noneStated").font(.subheadline).foregroundStyle(DesignToken.textSecondary) }
      ForEach(Array(points.enumerated()), id: \.offset) { _, point in
        VStack(alignment: .leading, spacing: 2) {
          Text(verbatim: point.text).font(.body)
          // Each reference is a full touch target even when its visible number is small.
          HStack(spacing: 4) {
            ForEach(Array(Set(point.sources)).sorted(), id: \.self) { source in
              Button { focus = source; tab = 1 } label: {
                Text(verbatim: "[\((session.turns.firstIndex { $0.id == source } ?? 0) + 1)]")
                  .font(.caption.bold()).frame(minWidth: 44, minHeight: 44)
              }.accessibilityLabel(Text("history.source \((session.turns.firstIndex { $0.id == source } ?? 0) + 1)"))
            }
          }
        }
      }
    }
  }

  private func transcript(_ session: SavedSession) -> some View {
    ScrollViewReader { proxy in
      ScrollView {
        LazyVStack(spacing: 8) {
          ForEach(Array(session.turns.enumerated()), id: \.element.id) { index, turn in
            VStack(alignment: .leading, spacing: 6) {
              Text(verbatim: "\(turn.speaker) · \(turn.targetLanguage.uppercased()) · \(index + 1)")
                .font(.caption).foregroundStyle(DesignToken.textSecondary)
              if let translation = turn.translation { Text(verbatim: translation).font(.system(size: translationSize, weight: .bold)) }
              else { Text("history.translationUnavailable").font(.caption) }
              Divider()
              Text(verbatim: turn.original).font(.system(size: originalSize)).textSelection(.enabled)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(12)
              .background(turn.speaker == "A" ? DesignToken.tintA : DesignToken.tintB)
              .clipShape(RoundedRectangle(cornerRadius: 12))
              .overlay(RoundedRectangle(cornerRadius: 12).stroke(focus == turn.id ? DesignToken.accentB : .clear, lineWidth: 2))
              .padding(turn.speaker == "A" ? .trailing : .leading, 16).id(turn.id)
          }
        }.padding(8)
      }.onAppear { if let focus { DispatchQueue.main.async { proxy.scrollTo(focus, anchor: .center) } } }
    }
  }
}

struct HistoryStorageNotice: View {
  @ObservedObject var history: SessionHistory
  var body: some View {
    if history.storageFailed {
      VStack {
        Text("history.storageError").font(.footnote)
        Button("history.retry", action: history.retryStorage)
      }.padding().background(DesignToken.surface).foregroundStyle(DesignToken.error)
    }
  }
}
