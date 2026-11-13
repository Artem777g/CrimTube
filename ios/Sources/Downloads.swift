import Foundation

struct DownloadedVideo: Codable, Identifiable, Hashable {
    let id: String
    let title: String
    let author: String
    let fileName: String
    let date: Date
}

final class DownloadManager: ObservableObject {
    static let shared = DownloadManager()

    @Published private(set) var items: [DownloadedVideo] = []
    @Published private(set) var active: Set<String> = []
    @Published var lastError: String?

    private let fileManager = FileManager.default

    private var documents: URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    private var indexURL: URL {
        documents.appendingPathComponent("downloads.json")
    }

    private init() {
        load()
    }

    func isDownloading(_ id: String) -> Bool {
        active.contains(id)
    }

    func localURL(for item: DownloadedVideo) -> URL {
        documents.appendingPathComponent(item.fileName)
    }

    func download(item: VideoItem, stream: FormatStream, fileExtension: String = "mp4") {
        guard !active.contains(item.videoId) else { return }
        guard let url = URL(string: stream.url) else { return }

        active.insert(item.videoId)
        lastError = nil

        Task {
            do {
                let (temporary, response) = try await URLSession.shared.download(from: url)
                if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
                    throw APIError.http(http.statusCode)
                }

                let fileName = item.videoId + "." + fileExtension
                let destination = self.documents.appendingPathComponent(fileName)
                try? self.fileManager.removeItem(at: destination)
                try self.fileManager.moveItem(at: temporary, to: destination)

                let downloaded = DownloadedVideo(
                    id: item.videoId,
                    title: item.title,
                    author: item.author,
                    fileName: fileName,
                    date: Date()
                )
                await MainActor.run {
                    self.items.insert(downloaded, at: 0)
                    self.save()
                }
            } catch {
                let message = (error as? LocalizedError)?.errorDescription ?? "Не удалось скачать видео."
                await MainActor.run {
                    self.lastError = message
                }
            }
            await MainActor.run {
                self.active.remove(item.videoId)
            }
        }
    }

    func delete(_ item: DownloadedVideo) {
        try? fileManager.removeItem(at: localURL(for: item))
        items.removeAll { $0.id == item.id }
        save()
    }

    func deleteAll() {
        for item in items {
            try? fileManager.removeItem(at: localURL(for: item))
        }
        items.removeAll()
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: indexURL) else { return }
        items = (try? JSONDecoder().decode([DownloadedVideo].self, from: data)) ?? []
    }

    private func save() {
        let data = try? JSONEncoder().encode(items)
        try? data?.write(to: indexURL, options: .atomic)
    }
}
