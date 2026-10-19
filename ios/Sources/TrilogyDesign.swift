import SwiftUI
import UIKit

/// Native type sizes and Dynamic Type, using the same Trilogy faces as Aperçu.
enum TrilogyFont {
  static func text(_ style: Font.TextStyle, emphasized: Bool = false) -> Font {
    let size: CGFloat
    let heading: Bool
    switch style {
    case .largeTitle: (size, heading) = (32, true)
    case .title: (size, heading) = (28, true)
    case .title2: (size, heading) = (24, true)
    case .title3: (size, heading) = (20, true)
    case .headline: (size, heading) = (16, false)
    case .subheadline: (size, heading) = (15, false)
    case .footnote: (size, heading) = (14, false)
    case .caption, .caption2: (size, heading) = (12, false)
    default: (size, heading) = (16, false)
    }
    let face = heading ? "BouyguesSpeak-Bold"
      : emphasized || style == .headline ? "BouyguesRead-Medium" : "BouyguesRead-Regular"
    return .custom(face, size: size, relativeTo: style)
  }

  static let wordmark = Font.custom("BouyguesSpeak-Bold", size: 24, relativeTo: .title2)
  static let translation = Font.custom("BouyguesRead-Medium", size: 16, relativeTo: .body).bold()
  static let original = Font.custom("BouyguesRead-Regular", size: 13, relativeTo: .caption)

  static func configureNavigation() {
    let appearance = UINavigationBarAppearance()
    appearance.configureWithOpaqueBackground()
    appearance.backgroundColor = .white
    let ink = UIColor(red: 37/255, green: 70/255, blue: 95/255, alpha: 1)
    appearance.titleTextAttributes = [.foregroundColor: ink, .font: UIFontMetrics(forTextStyle: .headline).scaledFont(for: UIFont(name: "BouyguesSpeak-Bold", size: 20) ?? .boldSystemFont(ofSize: 20))]
    appearance.largeTitleTextAttributes = [.foregroundColor: ink, .font: UIFontMetrics(forTextStyle: .largeTitle).scaledFont(for: UIFont(name: "BouyguesSpeak-Bold", size: 32) ?? .boldSystemFont(ofSize: 32))]
    UINavigationBar.appearance().standardAppearance = appearance
    UINavigationBar.appearance().scrollEdgeAppearance = appearance
  }
}

/// Functional primary actions are navy; complementary actions use an outline.
struct TrilogyActionStyle: ButtonStyle {
  var primary = true
  @Environment(\.isEnabled) private var enabled

  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .font(TrilogyFont.text(.body, emphasized: true))
      .multilineTextAlignment(.center)
      .fixedSize(horizontal: false, vertical: true)
      .padding(.horizontal, 16).padding(.vertical, 12)
      .frame(maxWidth: .infinity, minHeight: Layout.actionHeight)
      .foregroundStyle(primary && enabled ? DesignToken.surface : DesignToken.textPrimary)
      .background(primary && enabled ? DesignToken.accent : enabled ? DesignToken.surface : DesignToken.surfaceSubtle)
      .clipShape(RoundedRectangle(cornerRadius: Layout.control))
      .overlay(RoundedRectangle(cornerRadius: Layout.control).stroke(primary ? .clear : DesignToken.divider, lineWidth: 1))
      .opacity(enabled ? configuration.isPressed ? 0.8 : 1 : 0.5)
  }
}

struct TrilogySessionTabs: View {
  @Binding var selection: Int
  private let labels: [LocalizedStringKey] = ["history.summary", "history.transcript"]

  var body: some View {
    HStack(spacing: 4) {
      ForEach(labels.indices, id: \.self) { index in
        Button { selection = index } label: {
          Text(labels[index]).font(TrilogyFont.text(.footnote, emphasized: true))
            .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
            .padding(8).frame(maxWidth: .infinity, minHeight: Layout.actionHeight)
            .foregroundStyle(selection == index ? DesignToken.surface : DesignToken.textPrimary)
            .background(selection == index ? DesignToken.accent : DesignToken.surface)
            .clipShape(RoundedRectangle(cornerRadius: Layout.control))
        }.buttonStyle(.plain)
          .accessibilityAddTraits(selection == index ? .isSelected : [])
      }
    }.padding(4)
      .overlay(RoundedRectangle(cornerRadius: Layout.control).stroke(DesignToken.divider, lineWidth: 1))
      .accessibilityElement(children: .contain).accessibilityLabel(Text("history.content"))
  }
}

/// Measure the bubble at its natural width, capped at 84% of the transcript viewport.
/// The row occupies the full width so short A/B replies keep their left/right alignment.
struct ChatBubbleRow: SwiftUI.Layout {
  var isTrailing: Bool

  private func measure(_ proposedWidth: CGFloat?, subviews: Subviews) -> (row: CGFloat, bubble: CGSize) {
    guard let bubble = subviews.first else { return (0, .zero) }
    let idealWidth = bubble.sizeThatFits(.unspecified).width
    let available = proposedWidth ?? idealWidth
    let rowWidth = available.isFinite ? max(1, available) : 320
    // Both transcript lists have 16pt horizontal content insets.
    let limit = min(rowWidth, (rowWidth + 32) * 0.84)
    let width = max(1, min(idealWidth, limit))
    let size = bubble.sizeThatFits(ProposedViewSize(width: width, height: nil))
    return (rowWidth, CGSize(width: width, height: size.height))
  }

  func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
    let size = measure(proposal.width, subviews: subviews)
    return CGSize(width: size.row, height: size.bubble.height)
  }

  func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
    guard let bubble = subviews.first else { return }
    let size = measure(bounds.width, subviews: subviews).bubble
    bubble.place(at: CGPoint(x: isTrailing ? bounds.maxX - size.width : bounds.minX, y: bounds.minY),
                 anchor: .topLeading, proposal: ProposedViewSize(size))
  }
}
