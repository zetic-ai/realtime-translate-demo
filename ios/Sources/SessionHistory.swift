import Foundation

struct SavedTurn: Codable, Equatable, Identifiable {
  let id: String
  let speaker: String
  let original: String
  let translation: String?
  let sourceLanguage: String
  let targetLanguage: String
}

struct SavedSession: Codable, Equatable, Identifiable {
  let schemaVersion: Int
  let id: String
  var title: String
  let startedAt: Date
  var endedAt: Date?
  var turns: [SavedTurn]
  var durationText: String {
    guard let endedAt else { return "" }
    let seconds = max(0, Int(endedAt.timeIntervalSince(startedAt)))
    return String(format: "%d:%02d", seconds / 60, seconds % 60)
  }
}

/// One atomically replaced file per session; no transcript in defaults, logs, or cloud backup.
/// A damaged file is retained and reported, never silently replaced by an empty history.
final class SessionFileStore {
  let directory: URL
  init(directory: URL) { self.directory = directory }

  func load() throws -> [SavedSession] {
    guard FileManager.default.fileExists(atPath: directory.path) else { return [] }
    return try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
      .filter { $0.pathExtension == "json" }
      .map { try JSONDecoder().decode(SavedSession.self, from: Data(contentsOf: $0)) }
      .map { session in
        guard session.schemaVersion == 1, UUID(uuidString: session.id) != nil else { throw CocoaError(.fileReadCorruptFile) }
        return session
      }
      .sorted { $0.startedAt > $1.startedAt }
  }

  func save(_ session: SavedSession) throws {
    guard UUID(uuidString: session.id) != nil else { throw CocoaError(.fileWriteInvalidFileName) }
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    var excluded = directory
    var values = URLResourceValues(); values.isExcludedFromBackup = true
    try excluded.setResourceValues(values)
    try JSONEncoder().encode(session).write(to: url(session.id), options: [.atomic, .completeFileProtection])
  }

  func delete(_ id: String) throws { try FileManager.default.removeItem(at: url(id)) }
  private func url(_ id: String) -> URL { directory.appendingPathComponent(id).appendingPathExtension("json") }

  static func live() -> SessionFileStore {
    let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
    return SessionFileStore(directory: base.appendingPathComponent("RelaisSessions", isDirectory: true))
  }
}

@MainActor final class SessionHistory: ObservableObject {
  @Published private(set) var sessions: [SavedSession] = []
  @Published private(set) var storageFailed = false
  private let store: SessionFileStore?
  var savedSessions: [SavedSession] { sessions.filter { $0.id != activeID } }
  private var activeID: String?
  private var captureStartedAt: Date?
  func beginCapture() { if activeID == nil { captureStartedAt = Date() } }
  init(store: SessionFileStore? = nil) {
    self.store = store
    do { sessions = try store?.load() ?? [] }
    catch { storageFailed = true }
  }

  @discardableResult func checkpoint(_ turns: [SavedTurn], ending: Bool = false) -> String? {
    guard !turns.isEmpty else { return nil }
    // Refuse writes after an unreadable history: retain the current transcript for retry.
    guard !storageFailed else { return nil }
    let existing = sessions.first { $0.id == activeID }
    var session = existing ?? SavedSession(
      schemaVersion: 1, id: UUID().uuidString,
      title: String(turns[0].original.prefix(60)), startedAt: captureStartedAt ?? Date(), endedAt: nil,
      turns: [])
    session.turns = turns.map { turn in
      let source = existing?.turns.first { $0.id == turn.id }?.sourceLanguage ?? turn.sourceLanguage
      return SavedTurn(id: turn.id, speaker: turn.speaker, original: turn.original, translation: turn.translation,
                       sourceLanguage: source, targetLanguage: turn.targetLanguage)
    }
    if !ending, existing?.turns == session.turns { return session.id }
    if ending { session.endedAt = Date() }
    guard persist(session) else { return nil }
    activeID = ending ? nil : session.id
    if ending { captureStartedAt = nil }
    return session.id
  }

  func rename(_ id: String, title: String) {
    let trimmed = String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80))
    guard !trimmed.isEmpty, var session = sessions.first(where: { $0.id == id }) else { return }
    session.title = trimmed; _ = persist(session)
  }

  func delete(_ id: String) {
    guard id != activeID else { return }
    do { try store?.delete(id); sessions.removeAll { $0.id == id } }
    catch { storageFailed = true }
  }

  func retryStorage() {
    do {
      let loaded = try store?.load() ?? []
      // Preserve this process's active snapshot; its next checkpoint retries the atomic write.
      for session in loaded where !sessions.contains(where: { $0.id == session.id }) { sessions.append(session) }
      storageFailed = false
    } catch { storageFailed = true }
  }

  @discardableResult private func persist(_ session: SavedSession) -> Bool {
    do {
      try store?.save(session)
      sessions.removeAll { $0.id == session.id }; sessions.append(session)
      sessions.sort { $0.startedAt > $1.startedAt }
      return true
    } catch { storageFailed = true; return false }
  }

}
