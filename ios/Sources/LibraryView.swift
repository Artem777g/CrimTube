import SwiftUI

struct LibraryView: View {
    @ObservedObject private var history = HistoryStore.shared
    @ObservedObject private var watchLater = WatchLaterStore.shared
    @ObservedObject private var subscriptions = SubscriptionStore.shared

    @State private var section = 0

    var body: some View {
        NavigationView {
            VStack {
                Picker("Раздел", selection: $section) {
                    Text("История").tag(0)
                    Text("Позже").tag(1)
                    Text("Подписки").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)

                if section == 0 {
                    historyList
                } else if section == 1 {
                    watchLaterList
                } else {
                    subscriptionsList
                }
            }
            .navigationTitle("Библиотека")
        }
        .navigationViewStyle(.stack)
    }

    private var historyList: some View {
        List {
            ForEach(history.items) { item in
                NavigationLink(destination: PlayerView(item: item.video)) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.video.title).font(.system(size: 15, weight: .semibold)).lineLimit(2)
                        Text(item.video.author).font(.caption).foregroundColor(.secondary)
                        Text("Продолжить с " + timeText(item.position))
                            .font(.caption2)
                            .foregroundColor(.orange)
                    }
                    .padding(.vertical, 3)
                }
            }
            .onDelete { indexSet in
                for index in indexSet { history.remove(history.items[index]) }
            }
        }
        .listStyle(.plain)
        .toolbar {
            if !history.items.isEmpty {
                Button("Очистить") { history.clear() }
            }
        }
        .overlay {
            if history.items.isEmpty {
                Text("История пуста").foregroundColor(.secondary)
            }
        }
    }

    private var watchLaterList: some View {
        List {
            ForEach(watchLater.items) { item in
                NavigationLink(destination: PlayerView(item: item)) {
                    VideoRow(item: item)
                }
            }
            .onDelete { indexSet in
                for index in indexSet { watchLater.remove(watchLater.items[index]) }
            }
        }
        .listStyle(.plain)
        .overlay {
            if watchLater.items.isEmpty {
                Text("Список «Смотреть позже» пуст").foregroundColor(.secondary)
            }
        }
    }

    private var subscriptionsList: some View {
        List {
            ForEach(subscriptions.items) { channel in
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
                        .frame(width: 42, height: 42)
                        .clipShape(Circle())
                        Text(channel.author).font(.system(size: 15, weight: .semibold))
                    }
                }
            }
            .onDelete { indexSet in
                for index in indexSet { subscriptions.remove(subscriptions.items[index]) }
            }
        }
        .listStyle(.plain)
        .overlay {
            if subscriptions.items.isEmpty {
                Text("Подписок пока нет").foregroundColor(.secondary)
            }
        }
    }

    private func timeText(_ seconds: Double) -> String {
        let total = Int(seconds)
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

struct ChannelView: View {
    let channel: Channel

    @ObservedObject private var subscriptions = SubscriptionStore.shared

    @State private var items: [VideoItem] = []
    @State private var error: String?
    @State private var loading = true

    var body: some View {
        List {
            ForEach(items) { item in
                NavigationLink(destination: PlayerView(item: item)) {
                    VideoRow(item: item)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(channel.author)
        .toolbar {
            Button(subscriptions.contains(channel) ? "Вы подписаны" : "Подписаться") {
                subscriptions.toggle(channel)
            }
        }
        .overlay {
            if loading {
                ProgressView()
            } else if let error = error {
                Text(error).multilineTextAlignment(.center).padding()
            }
        }
        .task { await load() }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            items = try await InvidiousAPI.shared.channelVideos(id: channel.authorId)
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Не удалось загрузить канал."
        }
        loading = false
    }
}
