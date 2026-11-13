import SwiftUI

struct SearchView: View {
    @State private var query = ""
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
            .navigationTitle("Поиск")
            .searchable(text: $query, prompt: "Название или канал")
            .onSubmit(of: .search) {
                Task { await search() }
            }
            .overlay {
                if loading {
                    ProgressView("Поиск…")
                } else if let error = error {
                    Text(error).multilineTextAlignment(.center).padding()
                } else if items.isEmpty && !query.isEmpty {
                    Text("Ничего не найдено").foregroundColor(.secondary)
                }
            }
        }
        .navigationViewStyle(.stack)
    }

    private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        loading = true
        error = nil
        do {
            items = try await InvidiousAPI.shared.search(text)
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Поиск не удался."
        }
        loading = false
    }
}
