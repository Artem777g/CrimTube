import Foundation

enum APIError: LocalizedError {
    case badURL
    case http(Int)
    case decoding
    case noStream

    var errorDescription: String? {
        switch self {
        case .badURL:
            return "Некорректный адрес инстанса."
        case .http(let code) where code == 403 || code == 401:
            return "YouTube блокирует этот инстанс. Выберите другой."
        case .http(let code) where code == 404:
            return "Видео не найдено."
        case .http(let code):
            return "Инстанс ответил ошибкой \(code). Попробуйте другой."
        case .decoding:
            return "Инстанс вернул неожиданный ответ."
        case .noStream:
            return "У этого видео нет подходящего потока."
        }
    }
}

final class InvidiousAPI {
    static let shared = InvidiousAPI()

    private let defaultInstance = "http://5.253.247.182:3000"
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 25
        config.timeoutIntervalForResource = 60
        session = URLSession(configuration: config)
    }

    var instanceURL: URL {
        var stored = UserDefaults.standard.string(forKey: "instanceURL") ?? defaultInstance
        while stored.hasSuffix("/") { stored.removeLast() }
        return URL(string: stored) ?? URL(string: defaultInstance)!
    }

    func popular() async throws -> [VideoItem] {
        try await get("/api/v1/popular")
    }

    func search(_ query: String) async throws -> [VideoItem] {
        var components = URLComponents(string: instanceURL.absoluteString + "/api/v1/search")
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: "video")
        ]
        guard let url = components?.url else { throw APIError.badURL }

        let data = try await data(for: url)
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw APIError.decoding
        }
        let videos = array.filter { ($0["type"] as? String) == "video" }
        guard let filtered = try? JSONSerialization.data(withJSONObject: videos) else {
            throw APIError.decoding
        }
        do {
            return try JSONDecoder().decode([VideoItem].self, from: filtered)
        } catch {
            throw APIError.decoding
        }
    }

    func video(id: String) async throws -> VideoDetails {
        try await get("/api/v1/videos/" + id + "?local=true")
    }

    func searchChannels(_ query: String) async throws -> [Channel] {
        var components = URLComponents(string: instanceURL.absoluteString + "/api/v1/search")
        components?.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "type", value: "channel")
        ]
        guard let url = components?.url else { throw APIError.badURL }

        let data = try await data(for: url)
        guard let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw APIError.decoding
        }
        let channels = array.filter { ($0["type"] as? String) == "channel" }
        guard let filtered = try? JSONSerialization.data(withJSONObject: channels) else {
            throw APIError.decoding
        }
        do {
            return try JSONDecoder().decode([Channel].self, from: filtered)
        } catch {
            throw APIError.decoding
        }
    }

    func channelVideos(id: String) async throws -> [VideoItem] {
        let response: ChannelVideosResponse = try await get("/api/v1/channels/" + id + "/videos")
        return response.videos ?? []
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        guard let url = URL(string: instanceURL.absoluteString + path) else {
            throw APIError.badURL
        }
        return try await request(url)
    }

    private func request<T: Decodable>(_ url: URL) async throws -> T {
        let data = try await data(for: url)
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }

    private func data(for url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.setValue("CrimTube-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw APIError.http(http.statusCode)
        }
        return data
    }
}
