import Foundation

struct HistoryItem: Codable, Identifiable {
    let video: VideoItem
    var position: Double
    var date: Date

    var id: String { video.videoId }
}

final class HistoryStore: ObservableObject {
    static let shared = HistoryStore()

    @Published private(set) var items: [HistoryItem] = []

    private let fileURL: URL = {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("history.json")
    }()

    private init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([HistoryItem].self, from: data) {
            items = saved
        }
    }

    func position(for videoId: String) -> Double {
        items.first { $0.video.videoId == videoId }?.position ?? 0
    }

    func record(video: VideoItem, position: Double) {
        guard position > 0 else { return }
        if let index = items.firstIndex(where: { $0.video.videoId == video.videoId }) {
            items[index].position = position
            items[index].date = Date()
            let item = items.remove(at: index)
            items.insert(item, at: 0)
        } else {
            items.insert(HistoryItem(video: video, position: position, date: Date()), at: 0)
        }
        if items.count > 100 {
            items = Array(items.prefix(100))
        }
        save()
    }

    func remove(_ item: HistoryItem) {
        items.removeAll { $0.id == item.id }
        save()
    }

    func clear() {
        items.removeAll()
        save()
    }

    private func save() {
        let data = try? JSONEncoder().encode(items)
        try? data?.write(to: fileURL, options: .atomic)
    }
}

final class WatchLaterStore: ObservableObject {
    static let shared = WatchLaterStore()

    @Published private(set) var items: [VideoItem] = []

    private let fileURL: URL = {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("watchlater.json")
    }()

    private init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([VideoItem].self, from: data) {
            items = saved
        }
    }

    func contains(_ video: VideoItem) -> Bool {
        items.contains { $0.videoId == video.videoId }
    }

    func toggle(_ video: VideoItem) {
        if let index = items.firstIndex(where: { $0.videoId == video.videoId }) {
            items.remove(at: index)
        } else {
            items.insert(video, at: 0)
        }
        save()
    }

    func remove(_ video: VideoItem) {
        items.removeAll { $0.videoId == video.videoId }
        save()
    }

    private func save() {
        let data = try? JSONEncoder().encode(items)
        try? data?.write(to: fileURL, options: .atomic)
    }
}

final class SubscriptionStore: ObservableObject {
    static let shared = SubscriptionStore()

    @Published private(set) var items: [Channel] = []

    private let fileURL: URL = {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("subscriptions.json")
    }()

    private init() {
        if let data = try? Data(contentsOf: fileURL),
           let saved = try? JSONDecoder().decode([Channel].self, from: data) {
            items = saved
        }
    }

    func contains(_ channel: Channel) -> Bool {
        items.contains { $0.authorId == channel.authorId }
    }

    func toggle(_ channel: Channel) {
        if let index = items.firstIndex(where: { $0.authorId == channel.authorId }) {
            items.remove(at: index)
        } else {
            items.insert(channel, at: 0)
        }
        save()
    }

    func remove(_ channel: Channel) {
        items.removeAll { $0.authorId == channel.authorId }
        save()
    }

    private func save() {
        let data = try? JSONEncoder().encode(items)
        try? data?.write(to: fileURL, options: .atomic)
    }
}
