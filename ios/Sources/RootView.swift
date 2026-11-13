import AVFoundation
import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            PopularView()
                .tabItem { Label("Популярное", systemImage: "flame") }
            SearchView()
                .tabItem { Label("Поиск", systemImage: "magnifyingglass") }
            DownloadsView()
                .tabItem { Label("Загрузки", systemImage: "arrow.down.circle") }
            LibraryView()
                .tabItem { Label("Библиотека", systemImage: "books.vertical") }
            SettingsView()
                .tabItem { Label("Настройки", systemImage: "gearshape") }
        }
        .preferredColorScheme(.dark)
    }
}

@main
struct CrimTubeApp: App {
    init() {
        AudioSession.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
