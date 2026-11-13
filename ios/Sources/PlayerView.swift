import AVFoundation
import AVKit
import SwiftUI

struct PlayerView: View {
    let item: VideoItem

    @ObservedObject private var watchLater = WatchLaterStore.shared
    @ObservedObject private var subscriptions = SubscriptionStore.shared

    @State private var details: VideoDetails?
    @State private var streams: [FormatStream] = []
    @State private var selected: FormatStream?
    @State private var player: AVPlayer?
    @State private var error: String?
    @State private var loading = true
    @State private var downloadMessage: String?
    @State private var timeObserver: Any?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let player = player {
                    VideoPlayer(player: player)
                        .frame(height: 230)
                        .background(Color.black)
                        .cornerRadius(8)
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
                Text(item.author)
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                HStack(spacing: 12) {
                    Menu {
                        ForEach(streams) { stream in
                            Button(stream.qualityLabel ?? "Поток") {
                                select(stream)
                            }
                        }
                    } label: {
                        Label(selected?.qualityLabel ?? "Качество", systemImage: "4k.tv")
                    }
                    .disabled(streams.isEmpty)

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
    }

    private func load() async {
        loading = true
        error = nil
        do {
            let loaded = try await InvidiousAPI.shared.video(id: item.videoId)
            details = loaded
            let usable = (loaded.formatStreams ?? []).filter { URL(string: $0.url) != nil }
            streams = usable.sorted { $0.height > $1.height }
            guard let best = streams.first else {
                throw APIError.noStream
            }
            select(best)
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
        let saved = HistoryStore.shared.position(for: item.videoId)
        player = newPlayer
        if saved > 5 {
            newPlayer.seek(to: CMTime(seconds: saved, preferredTimescale: 600))
        }
        newPlayer.play()

        timeObserver = newPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 5, preferredTimescale: 600),
            queue: .main
        ) { time in
            HistoryStore.shared.record(video: item, position: time.seconds)
        }
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

    private func download() {
        guard let stream = selected else { return }
        DownloadManager.shared.download(item: item, stream: stream)
        downloadMessage = "Скачивание началось. Смотрите вкладку «Загрузки»."
    }
}
