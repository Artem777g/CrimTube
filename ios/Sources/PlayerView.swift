import AVKit
import SwiftUI

struct PlayerView: View {
    let item: VideoItem

    @State private var details: VideoDetails?
    @State private var streams: [FormatStream] = []
    @State private var selected: FormatStream?
    @State private var player: AVPlayer?
    @State private var error: String?
    @State private var loading = true
    @State private var downloadMessage: String?

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
            }
            .padding()
        }
        .navigationTitle("Видео")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .onDisappear { player?.pause() }
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
        player?.pause()
        let newPlayer = AVPlayer(url: url)
        player = newPlayer
        newPlayer.play()
    }

    private func download() {
        guard let stream = selected else { return }
        DownloadManager.shared.download(item: item, stream: stream)
        downloadMessage = "Скачивание началось. Смотрите вкладку «Загрузки»."
    }
}
