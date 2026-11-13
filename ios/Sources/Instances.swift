import Foundation
import SwiftUI

struct InstancePing {
    var milliseconds: Int?
    var ok: Bool
    var error: String?
}

@MainActor
final class InstanceStore: ObservableObject {
    static let shared = InstanceStore()

    let presets = [
        "http://5.253.247.182:3000",
        "http://82.69.47.60:31008",
        "http://41.193.14.3:3000",
        "http://73.26.148.72:3000",
        "http://14.225.212.141:3000",
        "http://60.241.100.198:31008",
        "http://185.231.102.75:14120",
        "http://157.254.18.46:14120",
        "http://www.invidious.frantisekfiala.eu",
        "http://14.224.215.173:3000",
        "http://61.221.6.111",
        "http://71.115.211.25:3067",
        "http://74.102.17.26:8203",
        "http://82.67.118.109:3000",
        "http://160.236.192.102:3000",
        "http://88.210.10.145:3001",
        "http://213.55.216.127:21388",
        "http://videos.nathanle.co",
        "http://nate-stockhaus.amishcrabs.pics",
        "http://inv.stappsworld.com",
        "http://inv.tekforge.de",
        "http://vid.justanas.xyz",
        "http://invid.seandrade.com.br",
        "http://inv2.k-live.com.tw",
        "http://yt.pipiska.fun:6767",
        "http://77.168.217.209:3000",
        "http://yt.tysstech.com",
        "http://inv.violethair.org",
        "http://youtube.eduardomiguel13.pt",
        "http://watch.dredgy.au",
        "http://invidious.culturegremlin.club",
        "http://invidiousvt.v4l3nt1nvs.ch",
        "http://utube.cyrus-ng.com",
        "http://yt.easys-web.de",
        "http://45.158.116.183"
    ]

    @Published var pings: [String: InstancePing] = [:]
    @Published var pinging = false

    private init() {}

    func pingAll() async {
        guard !pinging else { return }
        pinging = true
        let urls = presets

        await withTaskGroup(of: (String, InstancePing).self) { group in
            for url in urls {
                group.addTask { await Self.ping(url) }
            }
            for await result in group {
                pings[result.0] = result.1
            }
        }
        pinging = false
    }

    nonisolated private static func ping(_ url: String) async -> (String, InstancePing) {
        guard let requestURL = URL(string: url + "/api/v1/popular") else {
            return (url, InstancePing(milliseconds: nil, ok: false, error: "bad url"))
        }
        var request = URLRequest(url: requestURL)
        request.timeoutInterval = 8
        request.setValue("CrimTube-iOS/0.1", forHTTPHeaderField: "User-Agent")
        let start = Date()
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            let ms = Int(Date().timeIntervalSince(start) * 1000)
            if let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) {
                return (url, InstancePing(milliseconds: ms, ok: true, error: nil))
            }
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            return (url, InstancePing(milliseconds: ms, ok: false, error: "HTTP \(code)"))
        } catch {
            return (url, InstancePing(milliseconds: nil, ok: false, error: "нет ответа"))
        }
    }
}

struct InstancesView: View {
    @ObservedObject private var store = InstanceStore.shared
    @AppStorage("instanceURL") private var current = "http://5.253.247.182:3000"

    var body: some View {
        List {
            ForEach(store.presets, id: \.self) { url in
                Button {
                    current = url
                } label: {
                    HStack(spacing: 10) {
                        Circle()
                            .fill(color(for: store.pings[url]))
                            .frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(url).font(.system(size: 13)).lineLimit(1)
                            if let ping = store.pings[url] {
                                Text(ping.ok ? "\(ping.milliseconds ?? 0) мс" : (ping.error ?? "ошибка"))
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        Spacer()
                        if current == url {
                            Image(systemName: "checkmark.circle.fill").foregroundColor(.green)
                        }
                    }
                }
                .foregroundColor(.primary)
            }
        }
        .navigationTitle("Инстансы")
        .toolbar {
            Button(store.pinging ? "Пинг…" : "Пинг") {
                Task { await store.pingAll() }
            }
            .disabled(store.pinging)
        }
        .refreshable { await store.pingAll() }
        .task {
            if store.pings.isEmpty { await store.pingAll() }
        }
    }

    private func color(for ping: InstancePing?) -> Color {
        guard let ping = ping else { return .gray }
        return ping.ok ? .green : .red
    }
}
