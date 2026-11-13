import SwiftUI

struct VideoRow: View {
    let item: VideoItem

    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: item.thumbnailURL) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fill)
                default:
                    Color.black.opacity(0.35)
                }
            }
            .frame(width: 150, height: 84)
            .clipped()
            .cornerRadius(6)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.title)
                    .font(.system(size: 15, weight: .semibold))
                    .lineLimit(3)
                Text(item.author)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                if !item.durationText.isEmpty {
                    Text(item.durationText)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 4)
    }
}
