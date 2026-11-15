import AVFoundation
import AVKit
import SwiftUI

struct PlayerView: View {
    let item: VideoItem

    @AppStorage("muxServer") private var muxServer = ""

    @ObservedObject private var watchLater = WatchLaterStore.shared
    @ObservedObject private var subscriptions = SubscriptionStore.shared

    @State private var details: VideoDetails?
    @State private var streams: [FormatStream] = []
    @State private var serverHeights: [Int] = []
    @State private var selected: FormatStream?
    @State private var player: AVPlayer?
    @State private var error: String?
    @State private var loading = true
    @State private var downloadMessage: String?
    @State private var timeObserver: Any?
    @State private var fullscreen = false
    @State private var comments: [Comment] = []
    @State private var dubJob: DubJob?
    @State private var dubPreparing = false
    @State private var dubError: String?
    @State private var speed: Double = 1.0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let player = player {
                    ZStack(alignment: .topTrailing) {
                        VideoPlayer(player: player)
                            .frame(height: 230)
                            .background(Color.black)
                            .cornerRadius(8)
                        Button {
                            fullscreen = true
                        } label: {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .padding(8)
                                .background(Color.black.opacity(0.6))
                                .foregroundColor(.white)
                                .clipShape(Circle())
                        }
                        .padding(8)
                    }
                } else {
                    ZStack {
                        Color.black
                        if loading {
                            ProgressView().tint(.white)
                        } else if let error = error {
                            Text(error)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                                .padding()
                        }
                    }
                    .frame(height: 230)
                    .cornerRadius(8)
                }

                Text(item.title)
                    .font(.headline)
                if let channel = channel {
                    NavigationLink(destination: ChannelView(channel: channel)) {
                        Text(item.author)
                            .font(.subheadline)
                            .foregroundColor(.accentColor)
                    }
                } else {
                    Text(item.author)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                HStack(spacing: 12) {
                    Menu {
                        ForEach(streams) { stream in
                            Button(stream.qualityLabel ?? "Поток") {
                                select(stream)
                            }
                        }
                        if !muxServer.isEmpty {
                            ForEach(serverHeights, id: \.self) { height in
                                Button("\(height)p — через сервер") {
                                    selectServer(height)
                                }
                            }
                        }
                    } label: {
                        Label(selected?.qualityLabel ?? "Качество", systemImage: "4k.tv")
                    }
                    .disabled(streams.isEmpty && (muxServer.isEmpty || serverHeights.isEmpty))

                    Button {
                        download()
                    } label: {
                        Label("Скачать", systemImage: "arrow.down.circle")
                    }
                    .disabled(selected == nil || DownloadManager.shared.isDownloading(item.videoId))

                    if let audio = audioStream {
                        Button {
                            DownloadManager.shared.download(item: item, stream: audio, fileExtension: "m4a")
                            downloadMessage = "Аудио скачивается. Смотрите вкладку «Загрузки»."
                        } label: {
                            Label("Аудио", systemImage: "music.note")
                        }
                    }

                    Menu {
                        ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { value in
                            Button(speedLabel(value)) { setSpeed(value) }
                        }
                    } label: {
                        Label(speedLabel(speed), systemImage: "speedometer")
                    }
                }

                HStack(spacing: 12) {
                    Button {
                        watchLater.toggle(item)
                    } label: {
                        Label(watchLater.contains(item) ? "В списке" : "Позже",
                              systemImage: watchLater.contains(item) ? "clock.fill" : "clock")
                    }

                    if let channel = channel {
                        Button {
                            subscriptions.toggle(channel)
                        } label: {
                            Label(subscriptions.contains(channel) ? "Вы подписаны" : "Подписаться",
                                  systemImage: subscriptions.contains(channel) ? "checkmark.circle.fill" : "person.badge.plus")
                        }
                    }
                }
                .font(.footnote)

                if !muxServer.isEmpty {
                    Button {
                        startDubbing()
                    } label: {
                        Label(dubPreparing ? "Готовлю озвучку…" : "Озвучить на русском",
                              systemImage: "waveform")
                    }
                    .disabled(dubPreparing)
                }

                if let job = dubJob {
                    Text("Озвучка: " + (job.message ?? "") + " " + String(job.progress ?? 0) + "%")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                if let dubError = dubError {
                    Text(dubError)
                        .font(.footnote)
                        .foregroundColor(.orange)
                }

                if let message = downloadMessage {
                    Text(message)
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                if let error = error, player != nil {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(.orange)
                }

                if !comments.isEmpty {
                    Divider()
                    Text("Комментарии").font(.headline)
                    ForEach(Array(comments.prefix(10).enumerated()), id: \.offset) { _, comment in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(comment.author).font(.caption).bold()
                                Spacer()
                                if let likes = comment.likeCount, likes > 0 {
                                    Text("\(likes) ♥").font(.caption2).foregroundColor(.secondary)
                                }
                            }
                            Text(comment.content).font(.footnote).lineLimit(4)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Видео")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onDisappear {
            saveCurrent()
            player?.pause()
        }
        .fullScreenCover(isPresented: $fullscreen) {
            if let player = player {
                FullscreenPlayer(player: player)
            }
        }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            let loaded = try await InvidiousAPI.shared.video(id: item.videoId)
            details = loaded
            comments = (try? await InvidiousAPI.shared.comments(videoId: item.videoId)) ?? []
            let usable = (loaded.formatStreams ?? []).filter { URL(string: $0.url) != nil }
            streams = usable.sorted { $0.height > $1.height }

            var heights = Set<Int>()
            for stream in (loaded.adaptiveFormats ?? []) where (stream.type ?? "").contains("video") {
                if let label = stream.qualityLabel, let value = Int(label.prefix { $0.isNumber }) {
                    heights.insert(value)
                }
            }
            serverHeights = heights.sorted(by: >)

            if let best = streams.first {
                select(best)
            } else if !muxServer.isEmpty, let height = serverHeights.first {
                selectServer(height)
            } else {
                throw APIError.noStream
            }
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Не удалось открыть видео."
        }
        loading = false
    }

    private func select(_ stream: FormatStream) {
        guard let url = URL(string: stream.url) else { return }
        selected = stream
        saveCurrent()
        player?.pause()

        let newPlayer = AVPlayer(url: url)
        newPlayer.isMuted = false
        newPlayer.volume = 1.0
        let saved = HistoryStore.shared.position(for: item.videoId)
        player = newPlayer
        if saved > 5 {
            newPlayer.seek(to: CMTime(seconds: saved, preferredTimescale: 600))
        }
        newPlayer.play()
        newPlayer.rate = Float(speed)

        timeObserver = newPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 5, preferredTimescale: 600),
            queue: .main
        ) { time in
            HistoryStore.shared.record(video: item, position: time.seconds)
        }
    }

    private func setSpeed(_ value: Double) {
        speed = value
        if let player = player, player.timeControlStatus == .playing {
            player.rate = Float(value)
        }
    }

    private func speedLabel(_ value: Double) -> String {
        if value == 1.0 { return "1×" }
        return String(format: "%.2g×", value)
    }

    private func saveCurrent() {
        guard let player = player else { return }
        let seconds = player.currentTime().seconds
        if seconds.isFinite {
            HistoryStore.shared.record(video: item, position: seconds)
        }
        if let observer = timeObserver {
            player.removeTimeObserver(observer)
            timeObserver = nil
        }
    }

    private var audioStream: FormatStream? {
        details?.adaptiveFormats?.first { ($0.type ?? "").contains("audio") }
    }

    private var channel: Channel? {
        guard let details = details, let authorId = details.authorId else { return nil }
        return Channel(authorId: authorId, author: details.author, authorThumbnails: details.authorThumbnails)
    }

    private func selectServer(_ height: Int) {
        var base = muxServer.trimmingCharacters(in: .whitespacesAndNewlines)
        while base.hasSuffix("/") { base.removeLast() }
        guard let url = URL(string: base + "/live/" + item.videoId + "?q=\(height)") else { return }
        let stream = FormatStream(url: url.absoluteString, qualityLabel: "\(height)p", type: "video/mp4", itag: "server-\(height)")
        select(stream)
    }

    private func startDubbing() {
        guard !dubPreparing else { return }
        dubPreparing = true
        dubError = nil
        dubJob = nil
        let base = muxServer
        let videoId = item.videoId

        Task {
            do {
                var job = try await DubClient.shared.prepare(base: base, videoId: videoId)
                for _ in 0..<600 {
                    await MainActor.run { dubJob = job }
                    if job.status == "ready" {
                        await MainActor.run { playDubbed(base: base) }
                        break
                    }
                    if job.status == "error" {
                        throw DubError.server(job.error ?? "Не удалось озвучить видео.")
                    }
                    try await Task.sleep(nanoseconds: 2_000_000_000)
                    job = try await DubClient.shared.status(base: base, videoId: videoId)
                }
                await MainActor.run { dubPreparing = false }
            } catch {
                await MainActor.run {
                    dubError = (error as? LocalizedError)?.errorDescription ?? "Ошибка озвучки."
                    dubPreparing = false
                }
            }
        }
    }

    private func playDubbed(base: String) {
        var urlBase = base.trimmingCharacters(in: .whitespacesAndNewlines)
        while urlBase.hasSuffix("/") { urlBase.removeLast() }
        guard let url = URL(string: urlBase + "/dub/" + item.videoId + "?lang=ru") else { return }
        let stream = FormatStream(url: url.absoluteString, qualityLabel: "RU", type: "video/mp4", itag: "dub")
        select(stream)
    }

    private func download() {
        guard let stream = selected else { return }
        if let itag = stream.itag, itag.hasPrefix("server-") {
            var base = muxServer.trimmingCharacters(in: .whitespacesAndNewlines)
            while base.hasSuffix("/") { base.removeLast() }
            if let url = URL(string: base + "/mux/" + item.videoId + "?q=\(stream.height)") {
                let downloadStream = FormatStream(url: url.absoluteString, qualityLabel: stream.qualityLabel, type: "video/mp4", itag: "server-\(stream.height)")
                DownloadManager.shared.download(item: item, stream: downloadStream)
                downloadMessage = "Сервер склеивает файл. Смотрите вкладку «Загрузки»."
                return
            }
        }
        DownloadManager.shared.download(item: item, stream: stream)
        downloadMessage = "Скачивание началось. Смотрите вкладку «Загрузки»."
    }
}


struct FullscreenPlayer: View {
    let player: AVPlayer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            VideoPlayer(player: player)
                .ignoresSafeArea()
            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(.white.opacity(0.85))
                    .padding()
            }
        }
        .statusBar(hidden: true)
        .onAppear { player.play() }
    }
}
