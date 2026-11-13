using System;
using System.Collections.Generic;
using Windows.Storage;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Один адрес инстанса и его тип.
    /// </summary>
    public sealed class Endpoint
    {
        public Endpoint(string url, bool isInvidious, string proxyUrl = null)
        {
            Url = url;
            IsInvidious = isInvidious;
            ProxyUrl = proxyUrl;
        }

        public string Url { get; private set; }
        public bool IsInvidious { get; private set; }
        public string ProxyUrl { get; private set; }

        public string DisplayName
        {
            get
            {
                var clean = Url.Replace("http://", "").Replace("https://", "").TrimEnd('/');
                return (IsInvidious ? "Invidious — " : "Piped — ") + clean;
            }
        }
    }

    /// <summary>
    /// Настройки приложения: адрес инстанса и его тип.
    /// Хранятся в локальных настройках пакета, переживают перезапуск.
    /// </summary>
    public static class AppSettings
    {
        private const string KeyInstance = "instance_url";
        private const string KeyKind = "instance_kind";
        private const string KeyProxy = "instance_proxy";
        private const string KeyQuality = "preferred_quality";
        private const string KeyServer = "mux_server";
        private const string KeyUpdateUrl = "update_url";

        // Временный туннель для проверки; пользователь может заменить в настройках.
        private const string DefaultServer =
            "https://mostly-alignment-nottingham-structure.trycloudflare.com";

        /// <summary>
        /// Готовые адреса. Invidious идёт первым: он умеет local=true и
        /// проксирует поток через себя, поэтому не упирается в блокировку
        /// googlevideo у провайдера. Piped оставлен запасным.
        /// </summary>
        public static readonly IReadOnlyList<Endpoint> Presets = new[]
        {
            // Invidious: рабочие HTTP-адреса первыми, затем весь список с планшета.
            new Endpoint("http://5.253.247.182:3000", true),
            new Endpoint("http://82.69.47.60:31008", true),
            new Endpoint("http://41.193.14.3:3000", true),
            new Endpoint("http://73.26.148.72:3000", true),
            new Endpoint("http://14.225.212.141:3000", true),
            new Endpoint("http://60.241.100.198:31008", true),
            new Endpoint("http://185.231.102.75:14120", true),
            new Endpoint("http://157.254.18.46:14120", true),
            new Endpoint("http://www.invidious.frantisekfiala.eu", true),
            new Endpoint("http://37.114.50.170:3000", true),
            new Endpoint("http://14.224.215.173:3000", true),
            new Endpoint("http://61.221.6.111", true),
            new Endpoint("http://71.115.211.25:3067", true),
            new Endpoint("http://34.80.232.20", true),
            new Endpoint("http://74.102.17.26:8203", true),
            new Endpoint("http://82.67.118.109:3000", true),
            new Endpoint("http://160.236.192.102:3000", true),
            new Endpoint("http://88.210.10.145:3001", true),
            new Endpoint("http://213.55.216.127:21388", true),
            new Endpoint("http://invidious.keltechnas.com", true),
            new Endpoint("http://videos.nathanle.co", true),
            new Endpoint("http://nate-stockhaus.amishcrabs.pics", true),
            new Endpoint("http://inv.stappsworld.com", true),
            new Endpoint("http://inv.tekforge.de", true),
            new Endpoint("http://vid.justanas.xyz", true),
            new Endpoint("http://invid.seandrade.com.br", true),
            new Endpoint("http://109.66.233.145", true),
            new Endpoint("http://inv2.k-live.com.tw", true),
            new Endpoint("http://you.dwyu.tw", true),
            new Endpoint("http://bris.theawaybox.com:3000", true),
            new Endpoint("http://invidious.malooma.bzh", true),
            new Endpoint("http://yt.pipiska.fun:6767", true),
            new Endpoint("http://77.168.217.209:3000", true),
            new Endpoint("http://yt.tysstech.com", true),
            new Endpoint("http://95.182.97.252:8024", true),
            new Endpoint("http://inv.violethair.org", true),
            new Endpoint("http://youtube.eduardomiguel13.pt", true),
            new Endpoint("http://watch.dredgy.au", true),
            new Endpoint("http://invidious.culturegremlin.club", true),
            new Endpoint("http://invidiousvt.v4l3nt1nvs.ch", true),
            new Endpoint("http://utube.cyrus-ng.com", true),
            new Endpoint("http://yt.easys-web.de", true),
            new Endpoint("http://invidious.meldov.dk", true),
            new Endpoint("http://45.158.116.183", true),
            // Piped: пара «API, proxy» — видео проксируется через второй адрес.
            new Endpoint("http://82.24.19.217:8084", false, "http://82.24.19.217:8085"),
            new Endpoint("http://124.158.10.214:8081", false, "http://124.158.10.214:8383"),
            new Endpoint("http://167.114.169.34:8081", false, "http://167.114.169.34:8082"),
            new Endpoint("http://152.53.170.32:8091/api", false, "http://yt3.serverapollo.com"),
            new Endpoint("http://152.53.31.104:8091/api", false, "http://pp.dfjko.cc:8080"),
            new Endpoint("http://pipedapi.xorrito.info", false, "http://pipedproxy.xorrito.info"),
            new Endpoint("http://piped-api.freelancingpeter.eu:8081", false, "http://piped-proxy.freelancingpeter.eu:8082"),
            new Endpoint("http://pa.dfjko.cc:8080", false, "http://pp.dfjko.cc:8080"),
            new Endpoint("http://pipedapi.stellar.afs.ovh", false, "http://pipedproxy.stellar.afs.ovh"),
            new Endpoint("http://pipedapi.hilles.space", false, "http://124.158.10.214:8383"),
            new Endpoint("http://apiyoutube.famille-duquesne.fr", false, "http://proxyyoutube.famille-duquesne.fr"),
            new Endpoint("http://piped-api.lithome.fr", false, "http://piped-proxy.lithome.fr"),
            new Endpoint("http://api.piped.breidenstein24.de", false, "http://proxy.piped.breidenstein24.de"),
            new Endpoint("http://maxtube-api.lawrie.co.nz", false, "http://proxy.piped.breidenstein24.de"),
            new Endpoint("http://yt2.serverapollo.com", false, "http://yt3.serverapollo.com"),
            new Endpoint("http://be-j3h84i5n40780sbmmfzq.majeol.uk", false, "http://pi-j3h84i5n40780sbmmfzr.majeol.uk"),
            new Endpoint("http://pipedapi.hugoblomdahl.se", false, "http://pipedproxy.hugoblomdahl.se"),
            new Endpoint("http://pipedapi.r4vermind.org", false, "http://pipedproxy.r4vermind.org"),
        };

        public static Endpoint DefaultInstance
        {
            get { return Presets[0]; }
        }

        /// <summary>Текущий адрес и все запасные: в этом порядке идёт перебор.</summary>
        public static IEnumerable<Endpoint> Candidates()
        {
            var current = Current;
            yield return current;

            foreach (var preset in Presets)
            {
                if (!string.Equals(preset.Url, current.Url, StringComparison.OrdinalIgnoreCase) ||
                    preset.IsInvidious != current.IsInvidious ||
                    !string.Equals(preset.ProxyUrl, current.ProxyUrl, StringComparison.OrdinalIgnoreCase))
                {
                    yield return preset;
                }
            }
        }

        /// <summary>Текущий инстанс.</summary>
        public static Endpoint Current
        {
            get
            {
                var stored = ApplicationData.Current.LocalSettings.Values[KeyInstance] as string;
                var kind = ApplicationData.Current.LocalSettings.Values[KeyKind] as string;
                var proxy = ApplicationData.Current.LocalSettings.Values[KeyProxy] as string;

                if (string.IsNullOrEmpty(stored))
                {
                    return DefaultInstance;
                }

                return new Endpoint(Normalize(stored),
                    string.Equals(kind, "invidious", StringComparison.OrdinalIgnoreCase),
                    string.IsNullOrEmpty(proxy) ? null : Normalize(proxy));
            }
        }

        /// <summary>Текущий адрес инстанса без завершающего слэша.</summary>
        public static string InstanceUrl
        {
            get { return Current.Url; }
        }

        public static bool InstanceIsInvidious
        {
            get { return Current.IsInvidious; }
        }

        /// <summary>
        /// Адрес mux-сервера (склеивает видео и звук в один MP4).
        /// Пустая строка — сервер не используется.
        /// </summary>
        public static string ServerUrl
        {
            get
            {
                var stored = ApplicationData.Current.LocalSettings.Values[KeyServer] as string;
                return string.IsNullOrEmpty(stored) ? DefaultServer : stored.TrimEnd('/');
            }
            set
            {
                ApplicationData.Current.LocalSettings.Values[KeyServer] =
                    string.IsNullOrWhiteSpace(value) ? string.Empty : value.Trim().TrimEnd('/');
            }
        }

        /// <summary>URL latest.json для проверки обновлений (пусто = выключено).</summary>
        public static string UpdateUrl
        {
            get
            {
                var stored = ApplicationData.Current.LocalSettings.Values[KeyUpdateUrl] as string;
                return string.IsNullOrEmpty(stored) ? string.Empty : stored.Trim();
            }
            set
            {
                ApplicationData.Current.LocalSettings.Values[KeyUpdateUrl] =
                    string.IsNullOrWhiteSpace(value) ? string.Empty : value.Trim();
            }
        }

        /// <summary>Последнее выбранное качество, по умолчанию 360p.</summary>
        public static string PreferredQuality
        {
            get
            {
                var stored = ApplicationData.Current.LocalSettings.Values[KeyQuality] as string;
                return string.IsNullOrEmpty(stored) ? "360p" : stored;
            }
            set
            {
                ApplicationData.Current.LocalSettings.Values[KeyQuality] =
                    string.IsNullOrEmpty(value) ? "360p" : value;
            }
        }

        public static void Save(string url, bool isInvidious)
        {
            var api = url;
            string proxy = null;

            // Для Piped можно ввести «api, proxy» одной строкой, как в Android-версии.
            var comma = url.IndexOf(',');
            if (comma > 0)
            {
                api = url.Substring(0, comma);
                proxy = url.Substring(comma + 1).Trim();
            }

            ApplicationData.Current.LocalSettings.Values[KeyInstance] = Normalize(api);
            ApplicationData.Current.LocalSettings.Values[KeyKind] = isInvidious ? "invidious" : "piped";
            ApplicationData.Current.LocalSettings.Values[KeyProxy] =
                string.IsNullOrEmpty(proxy) ? null : Normalize(proxy);
        }

        /// <summary>Запоминает рабочий инстанс, чтобы следующий запуск начинал с него.</summary>
        public static void SaveWorking(Endpoint endpoint)
        {
            ApplicationData.Current.LocalSettings.Values[KeyInstance] = Normalize(endpoint.Url);
            ApplicationData.Current.LocalSettings.Values[KeyKind] = endpoint.IsInvidious ? "invidious" : "piped";
            ApplicationData.Current.LocalSettings.Values[KeyProxy] =
                string.IsNullOrEmpty(endpoint.ProxyUrl) ? null : Normalize(endpoint.ProxyUrl);
        }

        /// <summary>Приводит ввод пользователя к виду https://host без слэша на конце.</summary>
        public static string Normalize(string url)
        {
            if (string.IsNullOrWhiteSpace(url)) return DefaultInstance.Url;

            url = url.Trim();
            if (!url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) &&
                !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                url = "https://" + url;
            }

            return url.TrimEnd('/');
        }
    }
}
