import Foundation

struct Thumbnail: Codable, Hashable {
    let url: String
    let width: Int?
    let height: Int?
}

struct VideoItem: Codable, Identifiable, Hashable {
    let videoId: String
    let title: String
    let author: String
    let lengthSeconds: Int?
    let videoThumbnails: [Thumbnail]?

    var id: String { videoId }

    var thumbnailURL: URL? {
        guard let thumbnails = videoThumbnails, !thumbnails.isEmpty else { return nil }
        let best = thumbnails.max { ($0.width ?? 0) < ($1.width ?? 0) }
        return URL(string: best?.url ?? thumbnails[0].url)
    }

    var durationText: String {
        guard let seconds = lengthSeconds, seconds > 0 else { return "" }
        let h = seconds / 3600
        let m = (seconds % 3600) / 60
        let s = seconds % 60
        if h > 0 {
            return String(format: "%d:%02d:%02d", h, m, s)
        }
        return String(format: "%d:%02d", m, s)
    }
}

struct FormatStream: Codable, Identifiable, Hashable {
    let url: String
    let qualityLabel: String?
    let type: String?
    let itag: String?

    var id: String { itag ?? url }

    var height: Int {
        guard let label = qualityLabel else { return 0 }
        let digits = label.prefix { $0.isNumber }
        return Int(digits) ?? 0
    }
}

struct VideoDetails: Codable {
    let videoId: String
    let title: String
    let author: String
    let lengthSeconds: Int?
    let formatStreams: [FormatStream]?
    let adaptiveFormats: [FormatStream]?
}
