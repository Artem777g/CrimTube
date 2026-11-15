import Foundation

struct DubJob: Codable {
    let status: String
    let progress: Int?
    let message: String?
    let error: String?
}

enum DubError: LocalizedError {
    case server(String)
    case badURL

    var errorDescription: String? {
        switch self {
        case .server(let text): return text
        case .badURL: return "Некорректный адрес mux-сервера."
        }
    }
}

final class DubClient {
    static let shared = DubClient()

    private func baseURL(_ raw: String) -> String {
        var base = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while base.hasSuffix("/") { base.removeLast() }
        return base
    }

    func prepare(base: String, videoId: String, lang: String = "ru") async throws -> DubJob {
        try await request(base: base, path: "/dub/prepare/" + videoId + "?lang=" + lang)
    }

    func status(base: String, videoId: String, lang: String = "ru") async throws -> DubJob {
        try await request(base: base, path: "/dub/status/" + videoId + "?lang=" + lang)
    }

    private func request(base: String, path: String) async throws -> DubJob {
        let trimmed = baseURL(base)
        guard let url = URL(string: trimmed + path) else { throw DubError.badURL }
        var request = URLRequest(url: url)
        request.timeoutInterval = 25
        request.setValue("CrimTube-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw DubError.server("Сервер ответил ошибкой \(http.statusCode).")
        }
        return try JSONDecoder().decode(DubJob.self, from: data)
    }
}
