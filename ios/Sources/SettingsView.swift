import SwiftUI

struct SettingsView: View {
    @AppStorage("instanceURL") private var instance = "https://inv.nadeko.net"

    var body: some View {
        NavigationView {
            Form {
                Section("Инстанс Invidious") {
                    TextField("https://inv.nadeko.net", text: $instance)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                    Text("Если видео не открывается — смените инстанс. Публичные серверы часто блокируются YouTube.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("О приложении") {
                    HStack {
                        Text("Версия")
                        Spacer()
                        Text("0.1.0").foregroundColor(.secondary)
                    }
                    Text("CrimTube для iOS. Сделано для AltStore/сайдлоада.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    Text("Связаться: artm.golovkov.00@inbox.ru")
                        .font(.footnote)
                }

                Section("Загрузки") {
                    Text("Скачанные видео лежат в папке приложения и доступны без интернета. Удалить можно свайпом во вкладке «Загрузки».")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("Настройки")
        }
        .navigationViewStyle(.stack)
    }
}
