import AVKit
import SwiftUI

struct DownloadsView: View {
    @ObservedObject private var manager = DownloadManager.shared
    @State private var confirmDeleteAll = false

    var body: some View {
        NavigationView {
            List {
                ForEach(manager.items) { item in
                    NavigationLink(destination: OfflinePlayerView(download: item)) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title).font(.system(size: 15, weight: .semibold))
                            Text(item.author).font(.caption).foregroundColor(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .onDelete { indexSet in
                    for index in indexSet {
                        manager.delete(manager.items[index])
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Загрузки")
            .toolbar {
                if !manager.items.isEmpty {
                    Button(role: .destructive) {
                        confirmDeleteAll = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
            .confirmationDialog("Удалить все загрузки?", isPresented: $confirmDeleteAll) {
                Button("Удалить все", role: .destructive) { manager.deleteAll() }
            }
            .overlay {
                if manager.items.isEmpty {
                    Text("Пока ничего не скачано")
                        .foregroundColor(.secondary)
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}

struct OfflinePlayerView: View {
    let download: DownloadedVideo
    @State private var player: AVPlayer

    init(download: DownloadedVideo) {
        self.download = download
        let url = DownloadManager.shared.localURL(for: download)
        let player = AVPlayer(url: url)
        player.isMuted = false
        player.volume = 1.0
        _player = State(initialValue: player)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VideoPlayer(player: player)
                .frame(height: 230)
                .background(Color.black)
                .cornerRadius(8)
            Text(download.title).font(.headline)
            Text(download.author).font(.subheadline).foregroundColor(.secondary)
            Spacer()
        }
        .padding()
        .navigationTitle("Офлайн")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { player.play() }
        .onDisappear { player.pause() }
    }
}
