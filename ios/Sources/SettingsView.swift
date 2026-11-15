import SwiftUI

struct SettingsView: View {
    @AppStorage("instanceURL") private var instance = "http://5.253.247.182:3000"
    @AppStorage("muxServer") private var muxServer = ""

    private let instances = [
        "http://5.253.247.182:3000",
        "http://82.69.47.60:31008",
        "http://41.193.14.3:3000",
        "http://73.26.148.72:3000",
        "http://14.225.212.141:3000",
        "http://60.241.100.198:31008",
        "http://185.231.102.75:14120",
        "http://157.254.18.46:14120",
        "http://www.invidious.frantisekfiala.eu",
        "http://14.224.215.173:3000",
        "http://61.221.6.111",
        "http://71.115.211.25:3067",
        "http://74.102.17.26:8203",
        "http://82.67.118.109:3000",
        "http://160.236.192.102:3000",
        "http://88.210.10.145:3001",
        "http://213.55.216.127:21388",
        "http://videos.nathanle.co",
        "http://nate-stockhaus.amishcrabs.pics",
        "http://inv.stappsworld.com",
        "http://inv.tekforge.de",
        "http://vid.justanas.xyz",
        "http://invid.seandrade.com.br",
        "http://inv2.k-live.com.tw",
        "http://yt.pipiska.fun:6767",
        "http://77.168.217.209:3000",
        "http://yt.tysstech.com",
        "http://inv.violethair.org",
        "http://youtube.eduardomiguel13.pt",
        "http://watch.dredgy.au",
        "http://invidious.culturegremlin.club",
        "http://invidiousvt.v4l3nt1nvs.ch",
        "http://utube.cyrus-ng.com",
        "http://yt.easys-web.de",
        "http://45.158.116.183"
    ]

    var body: some View {
        NavigationView {
            Form {
                Section("Инстанс Invidious") {
                    Picker("Живой инстанс", selection: $instance) {
                        ForEach(instances, id: \.self) { url in
                            Text(url).font(.caption)
                        }
                    }
                    NavigationLink("Список инстансов с пингом", destination: InstancesView())
                    TextField("http://5.253.247.182:3000", text: $instance)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                    Text("Если видео не открывается — смените инстанс. Публичные серверы часто блокируются YouTube.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("Mux-сервер") {
                    TextField("http://192.168.31.50:8765", text: $muxServer)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .keyboardType(.URL)
                    Text("Нужен для 480p/720p/1080p: сервер склеивает видео и звук в один MP4. Без него доступно 360p.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }

                Section("О приложении") {
                    HStack {
                        Text("Версия")
                        Spacer()
                        Text("0.1.6").foregroundColor(.secondary)
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
