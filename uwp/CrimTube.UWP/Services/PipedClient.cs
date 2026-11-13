using System;
using System.Collections.Generic;
using System.Globalization;
using System.Net.Http;
using System.Threading.Tasks;
using CrimTube.UWP.Models;
using Windows.Data.Json;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Клиент к API Piped: тренды, поиск, подробности видео и ссылки на потоки.
    /// Piped сам проксирует видеопоток через свой сервер, поэтому на телефоне
    /// не нужен прямой доступ к googlevideo.com (в РФ он заблокирован).
    /// </summary>
    public sealed class PipedClient : IVideoClient
    {
        private static readonly HttpClient Http = CreateHttpClient();

        private readonly string _baseUrl;
        private readonly string _proxyUrl;

        public PipedClient(string baseUrl)
            : this(baseUrl, null)
        {
        }

        public PipedClient(string baseUrl, string proxyUrl)
        {
            _baseUrl = AppSettings.Normalize(baseUrl);
            _proxyUrl = string.IsNullOrEmpty(proxyUrl) ? null : AppSettings.Normalize(proxyUrl);
        }

        public string BaseUrl
        {
            get { return _baseUrl; }
        }

        public async Task<IReadOnlyList<VideoItem>> GetPopularAsync()
        {
            // Тренды бывают пустыми для некоторых регионов — пробуем RU, затем US.
            var videos = ParseItems(await GetAsync("/trending?region=RU"));
            if (videos.Count == 0)
            {
                videos = ParseItems(await GetAsync("/trending?region=US"));
            }

            return videos;
        }

        public async Task<IReadOnlyList<VideoItem>> SearchAsync(string query)
        {
            var json = await GetAsync("/search?q=" + Uri.EscapeDataString(query ?? string.Empty) + "&filter=videos");
            var root = JsonObject.Parse(json);

            ThrowIfError(root);

            IJsonValue items;
            if (!root.TryGetValue("items", out items) || items.ValueType != JsonValueType.Array)
            {
                return new List<VideoItem>();
            }

            return ParseItems(items.GetArray());
        }

        /// <summary>
        /// Подробности видео. Поток берём уже проксированный инстансом (поле url),
        /// поэтому дополнительных запросов к YouTube телефон не делает.
        /// </summary>
        public async Task<VideoDetails> GetVideoAsync(string videoId)
        {
            var json = await GetAsync("/streams/" + Uri.EscapeDataString(videoId));
            var root = JsonObject.Parse(json);

            ThrowIfError(root);

            var details = new VideoDetails
            {
                VideoId = videoId,
                Title = Str(root, "title") ?? string.Empty,
                Author = Str(root, "uploader") ?? string.Empty,
                AuthorId = ChannelId(Str(root, "uploaderUrl")),
                Description = Str(root, "description") ?? string.Empty,
                LengthSeconds = (int)Num(root, "duration"),
                ViewCount = (long)Num(root, "views", -1),
                LikeCount = (long)Num(root, "likes", -1),
                ThumbnailUrl = Str(root, "thumbnailUrl")
            };

            ReadVideoStreams(root, details);
            ReadRelated(root, details);

            if (details.Streams.Count == 0)
            {
                throw new InvalidOperationException("Инстанс не отдал ни одного потока для этого видео");
            }

            return details;
        }

        /// <summary>Проверка, что инстанс отвечает (используется на экране настроек).</summary>
        public async Task<bool> PingAsync()
        {
            try
            {
                await GetAsync("/trending?region=RU");
                return true;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private void ReadVideoStreams(JsonObject root, VideoDetails details)
        {
            IJsonValue streamsValue;
            if (!root.TryGetValue("videoStreams", out streamsValue) ||
                streamsValue.ValueType != JsonValueType.Array)
            {
                return;
            }

            foreach (var value in streamsValue.GetArray())
            {
                if (value.ValueType != JsonValueType.Object) continue;

                var stream = value.GetObject();
                var url = Str(stream, "url");
                var mime = Str(stream, "mimeType") ?? string.Empty;
                var quality = Str(stream, "quality") ?? string.Empty;

                if (string.IsNullOrEmpty(url)) continue;
                if (!mime.StartsWith("video/mp4", StringComparison.OrdinalIgnoreCase)) continue;

                // Как в Android-версии: если Piped вернул прямой googlevideo,
                // перенаправляем запрос на proxy-хост инстанса.
                url = Proxy(url);

                // Только совмещённые потоки: MediaPlayerElement на Windows 10 Mobile
                // не умеет отдельно подхватывать аудиодорожку.
                var videoOnly = Bool(stream, "videoOnly");
                if (videoOnly) continue;

                details.Streams.Add(new StreamOption
                {
                    Url = url,
                    MimeType = mime,
                    QualityLabel = quality,
                    IsCombined = true
                });
            }

            details.Streams.Sort(delegate (StreamOption a, StreamOption b)
            {
                return Rank(a).CompareTo(Rank(b));
            });
        }

        private static int Rank(StreamOption stream)
        {
            var label = stream.QualityLabel ?? string.Empty;

            // Обычный 360p — самый совместимый с телефоном.
            if (label.StartsWith("360", StringComparison.Ordinal)) return 0;

            // LBRY-копии лежат на сторонних CDN, которые в РФ часто недоступны.
            if (label.StartsWith("LBRY", StringComparison.OrdinalIgnoreCase)) return 2;

            return 1;
        }

        private void ReadRelated(JsonObject root, VideoDetails details)
        {
            IJsonValue relatedValue;
            if (!root.TryGetValue("relatedStreams", out relatedValue) ||
                relatedValue.ValueType != JsonValueType.Array)
            {
                return;
            }

            var index = 0;
            foreach (var value in relatedValue.GetArray())
            {
                if (index++ >= 10) break;
                if (value.ValueType != JsonValueType.Object) continue;

                var item = ParseItem(value.GetObject());
                if (item != null) details.Related.Add(item);
            }
        }

        private static List<VideoItem> ParseItems(string json)
        {
            return ParseItems(JsonArray.Parse(json));
        }

        private static List<VideoItem> ParseItems(JsonArray array)
        {
            var videos = new List<VideoItem>();

            foreach (var value in array)
            {
                if (value.ValueType != JsonValueType.Object) continue;

                var item = ParseItem(value.GetObject());
                if (item != null) videos.Add(item);
            }

            return videos;
        }

        private static VideoItem ParseItem(JsonObject o)
        {
            var id = VideoId(Str(o, "url"));
            if (string.IsNullOrEmpty(id)) return null;

            var duration = (int)Num(o, "duration");

            return new VideoItem
            {
                VideoId = id,
                Title = Str(o, "title") ?? string.Empty,
                Author = Str(o, "uploaderName") ?? string.Empty,
                AuthorId = ChannelId(Str(o, "uploaderUrl")),
                // Прямой JPEG: прокси Piped отдаёт WebP, который Windows 10 Mobile
                // не умеет декодировать, поэтому картинки были пустыми.
                ThumbnailUrl = Thumb(id),
                DurationSeconds = duration > 0 ? duration : 0,
                ViewCount = (long)Num(o, "views", -1),
                PublishedText = Str(o, "uploadedDate") ?? string.Empty
            };
        }

        /// <summary>Прямая JPEG-обложка, совместимая с Windows 10 Mobile.</summary>
        /// <summary>Переписывает абсолютный URL на proxy-хост Piped.</summary>
        private string Proxy(string url)
        {
            if (string.IsNullOrEmpty(_proxyUrl) || string.IsNullOrEmpty(url)) return url;

            if (url.StartsWith("/")) return _proxyUrl + url;

            var scheme = url.IndexOf("://", StringComparison.Ordinal);
            if (scheme > 0)
            {
                var path = url.IndexOf('/', scheme + 3);
                if (path > 0) return _proxyUrl + url.Substring(path);
            }
            return url;
        }

        private static string Thumb(string videoId)
        {
            return string.IsNullOrEmpty(videoId)
                ? null
                : "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
        }

        /// <summary>Из "/watch?v=abc123&amp;t=10" достаёт "abc123".</summary>
        private static string VideoId(string url)
        {
            if (string.IsNullOrEmpty(url)) return null;

            const string marker = "v=";
            var start = url.IndexOf(marker, StringComparison.Ordinal);
            if (start < 0) return null;

            start += marker.Length;
            var end = url.IndexOf('&', start);
            return end < 0 ? url.Substring(start) : url.Substring(start, end - start);
        }

        /// <summary>Из "/channel/UC..." достаёт "UC...".</summary>
        private static string ChannelId(string url)
        {
            if (string.IsNullOrEmpty(url)) return null;

            const string marker = "/channel/";
            var start = url.IndexOf(marker, StringComparison.Ordinal);
            if (start < 0) return null;

            start += marker.Length;
            var end = url.IndexOf('?', start);
            return end < 0 ? url.Substring(start) : url.Substring(start, end - start);
        }

        private static void ThrowIfError(JsonObject root)
        {
            var error = Str(root, "error");
            if (!string.IsNullOrEmpty(error))
            {
                throw new InvalidOperationException(error);
            }
        }

        private async Task<string> GetAsync(string path)
        {
            try
            {
                using (var response = await Http.GetAsync(_baseUrl + path))
                {
                    if (!response.IsSuccessStatusCode)
                    {
                        var text = await response.Content.ReadAsStringAsync();
                        throw new InvalidOperationException(
                            "Инстанс ответил " + (int)response.StatusCode + ShortError(text));
                    }

                    return await response.Content.ReadAsStringAsync();
                }
            }
            catch (TaskCanceledException)
            {
                throw new InvalidOperationException("Инстанс не ответил вовремя. Проверьте адрес в настройках.");
            }
            catch (HttpRequestException e)
            {
                throw new InvalidOperationException("Нет связи с инстансом: " + e.Message);
            }
        }

        /// <summary>Достаёт короткую причину из JSON-ошибки Piped.</summary>
        private static string ShortError(string body)
        {
            if (string.IsNullOrEmpty(body)) return string.Empty;

            try
            {
                var root = JsonObject.Parse(body);
                var error = Str(root, "error");
                if (string.IsNullOrEmpty(error)) return string.Empty;
                if (error.Length > 120) error = error.Substring(0, 120) + "…";
                return ": " + error;
            }
            catch (Exception)
            {
                return string.Empty;
            }
        }

        private static HttpClient CreateHttpClient()
        {
            var client = new HttpClient
            {
                Timeout = TimeSpan.FromSeconds(15)
            };

            client.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent", "CrimTube-UWP/0.1");
            client.DefaultRequestHeaders.TryAddWithoutValidation("Accept", "application/json");
            return client;
        }

        private static string Str(JsonObject o, string key)
        {
            IJsonValue value;
            if (!o.TryGetValue(key, out value)) return null;

            switch (value.ValueType)
            {
                case JsonValueType.String:
                    return value.GetString();
                case JsonValueType.Number:
                    return value.GetNumber().ToString(CultureInfo.InvariantCulture);
                default:
                    return null;
            }
        }

        private static double Num(JsonObject o, string key, double fallback = 0)
        {
            IJsonValue value;
            if (!o.TryGetValue(key, out value)) return fallback;

            switch (value.ValueType)
            {
                case JsonValueType.Number:
                    return value.GetNumber();
                case JsonValueType.String:
                    double parsed;
                    return double.TryParse(value.GetString(), NumberStyles.Any, CultureInfo.InvariantCulture, out parsed)
                        ? parsed
                        : fallback;
                default:
                    return fallback;
            }
        }

        private static bool Bool(JsonObject o, string key)
        {
            IJsonValue value;
            if (!o.TryGetValue(key, out value)) return false;
            if (value.ValueType != JsonValueType.Boolean) return false;

            return value.GetBoolean();
        }
    }
}
