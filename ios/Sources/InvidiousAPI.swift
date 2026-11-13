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

    private let defaultInstance = "https://inv.nadeko.net"
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
        components?.queryItems = [URLQueryItem(name: "q", value: query)]
        guard let url = components?.url else { throw APIError.badURL }
        return try await request(url)
    }

    func video(id: String) async throws -> VideoDetails {
        try await get("/api/v1/videos/" + id + "?local=true")
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        guard let url = URL(string: instanceURL.absoluteString + path) else {
            throw APIError.badURL
        }
        return try await request(url)
    }

    private func request<T: Decodable>(_ url: URL) async throws -> T {
        var request = URLRequest(url: url)
        request.setValue("CrimTube-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw APIError.http(http.statusCode)
        }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIError.decoding
        }
    }
}
