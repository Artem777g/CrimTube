import SwiftUI

struct PopularView: View {
    @State private var items: [VideoItem] = []
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        NavigationView {
            List {
                ForEach(items) { item in
                    NavigationLink(destination: PlayerView(item: item)) {
                        VideoRow(item: item)
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Популярное")
            .refreshable { await load() }
            .overlay {
                if loading && items.isEmpty {
                    ProgressView("Загрузка…")
                } else if let error = error, items.isEmpty {
                    VStack(spacing: 12) {
                        Text(error).multilineTextAlignment(.center).padding()
                        Button("Повторить") { Task { await load() } }
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .task {
            if items.isEmpty { await load() }
        }
    }

    private func load() async {
        loading = true
        error = nil
        do {
            items = try await InvidiousAPI.shared.popular()
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Не удалось загрузить."
        }
        loading = false
    }
}
