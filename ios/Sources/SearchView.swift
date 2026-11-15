import SwiftUI

struct SearchView: View {
    @State private var query = ""
    @State private var mode = 0
    @State private var videos: [VideoItem] = []
    @State private var channels: [Channel] = []
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                Picker("Тип", selection: $mode) {
                    Text("Видео").tag(0)
                    Text("Каналы").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 6)

                List {
                    if mode == 0 {
                        ForEach(videos) { item in
                            NavigationLink(destination: PlayerView(item: item)) {
                                VideoRow(item: item)
                            }
                        }
                    } else {
                        ForEach(channels) { channel in
                            NavigationLink(destination: ChannelView(channel: channel)) {
                                HStack(spacing: 10) {
                                    AsyncImage(url: channel.thumbnailURL) { phase in
                                        switch phase {
                                        case .success(let image):
                                            image.resizable().aspectRatio(contentMode: .fill)
                                        default:
                                            Color.gray.opacity(0.3)
                                        }
                                    }
                                    .frame(width: 44, height: 44)
                                    .clipShape(Circle())
                                    Text(channel.author).font(.system(size: 15, weight: .semibold))
                                }
                                .padding(.vertical, 3)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle("Поиск")
            .searchable(text: $query, prompt: "Название или канал")
            .onSubmit(of: .search) {
                Task { await search() }
            }
            .onChange(of: mode) { _ in
                if !query.isEmpty {
                    Task { await search() }
                }
            }
            .overlay {
                if loading {
                    ProgressView("Поиск…")
                } else if let error = error {
                    Text(error).multilineTextAlignment(.center).padding()
                } else if currentIsEmpty && !query.isEmpty {
                    Text("Ничего не найдено").foregroundColor(.secondary)
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private var currentIsEmpty: Bool {
        mode == 0 ? videos.isEmpty : channels.isEmpty
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        loading = true
        error = nil
        do {
            if mode == 0 {
                videos = try await InvidiousAPI.shared.search(text)
                channels = []
            } else {
                channels = try await InvidiousAPI.shared.searchChannels(text)
                videos = []
            }
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Поиск не удался."
        }
        loading = false
    }
}
