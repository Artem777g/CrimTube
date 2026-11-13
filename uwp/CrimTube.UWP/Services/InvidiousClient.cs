using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Net.Http;
using System.Threading.Tasks;
using CrimTube.UWP.Models;
using Windows.Data.Json;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Клиент к API Invidious. Ключевое отличие от Piped: видео запрашивается
    /// с параметром local=true, поэтому сам инстанс проксирует поток через себя.
    /// Это обходит блокировку googlevideo у провайдера.
    /// </summary>
    public sealed class InvidiousClient : IVideoClient
    {
        private static readonly HttpClient Http = CreateHttpClient();

        private readonly string _baseUrl;

        public InvidiousClient(string baseUrl)
        {
            _baseUrl = AppSettings.Normalize(baseUrl);
        }

        public string BaseUrl
        {
            get { return _baseUrl; }
        }

        public async Task<IReadOnlyList<VideoItem>> GetPopularAsync()
        {
            // Сначала русский регион, чтобы список не был на чужом языке.
            var videos = ParseItems(await GetAsync("/api/v1/popular?region=RU"));
            if (videos.Count == 0)
            {
                videos = ParseItems(await GetAsync("/api/v1/popular"));
            }
            if (videos.Count == 0)
            {
                videos = ParseItems(await GetAsync("/api/v1/popular?region=US"));
            }
            return videos;
        }

        public async Task<IReadOnlyList<VideoItem>> SearchAsync(string query)
        {
            var json = await GetAsync("/api/v1/search?q=" + Uri.EscapeDataString(query ?? string.Empty) + "&type=video");
            return ParseItems(json);
        }

        public async Task<VideoDetails> GetVideoAsync(string videoId)
        {
            // local=true — принципиально: ссылки на поток будут с домена инстанса.
            var json = await GetAsync("/api/v1/videos/" + Uri.EscapeDataString(videoId) + "?local=true");
            var root = JsonObject.Parse(json);

            ThrowIfError(root);

            var details = new VideoDetails
            {
                VideoId = videoId,
                DashBaseUrl = _baseUrl,
                Title = Str(root, "title") ?? string.Empty,
                Author = Str(root, "author") ?? string.Empty,
                AuthorId = Str(root, "authorId"),
                Description = Str(root, "description") ?? string.Empty,
                LengthSeconds = (int)Num(root, "lengthSeconds"),
                ViewCount = (long)Num(root, "viewCount", -1),
                LikeCount = (long)Num(root, "likeCount", -1),
                ThumbnailUrl = Thumbnail(root)
            };

            ReadStreams(root, details);
            ReadRelated(root, details);

            if (details.Streams.Count == 0)
            {
                throw new InvalidOperationException(
                    "Инстанс не отдал совмещённый MP4-поток. Попробуйте другой инстанс.");
            }

            return details;
        }

        public async Task<bool> PingAsync()
        {
            try
            {
                await GetAsync("/api/v1/popular");
                return true;
            }
            catch (Exception)
            {
                return false;
            }
        }

        private void ReadStreams(JsonObject root, VideoDetails details)
        {
            IJsonValue value;
            if (!root.TryGetValue("formatStreams", out value) || value.ValueType != JsonValueType.Array)
            {
                return;
            }

            foreach (var item in value.GetArray())
            {
                if (item.ValueType != JsonValueType.Object) continue;

                var stream = item.GetObject();
                var url = ResolveStreamUrl(Str(stream, "url"));
                if (string.IsNullOrEmpty(url)) continue;

                var container = Str(stream, "container") ?? string.Empty;
                var type = Str(stream, "type") ?? string.Empty;
                if (container.IndexOf("mp4", StringComparison.OrdinalIgnoreCase) < 0 &&
                    type.IndexOf("mp4", StringComparison.OrdinalIgnoreCase) < 0)
                {
                    continue;
                }

                details.Streams.Add(new StreamOption
                {
                    Url = url,
                    MimeType = type.Length > 0 ? type : "video/mp4",
                    QualityLabel = Str(stream, "qualityLabel") ?? Str(stream, "quality") ?? "MP4",
                    IsCombined = true
                });
            }

            ReadAdaptiveStreams(root, details);

            details.Streams.Sort(delegate (StreamOption a, StreamOption b)
            {
                if (a.IsCombined != b.IsCombined) return a.IsCombined ? -1 : 1;
                return Rank(a).CompareTo(Rank(b));
            });
        }

        /// <summary>
        /// Добавляет высокие качества из adaptiveFormats. Они идут без звука,
        /// но это единственный способ получить 720/1080 на Windows 10 Mobile.
        /// </summary>
        private void ReadAdaptiveStreams(JsonObject root, VideoDetails details)
        {
            IJsonValue value;
            if (!root.TryGetValue("adaptiveFormats", out value) || value.ValueType != JsonValueType.Array)
            {
                return;
            }

            var used = new HashSet<string>();
            for (var i = 0; i < details.Streams.Count; i++)
            {
                if (!string.IsNullOrEmpty(details.Streams[i].QualityLabel))
                {
                    used.Add(details.Streams[i].QualityLabel);
                }
            }

            foreach (var item in value.GetArray())
            {
                if (item.ValueType != JsonValueType.Object) continue;

                var stream = item.GetObject();
                var type = Str(stream, "type") ?? string.Empty;

                // Отдельная аудиодорожка: AAC/mp4, чтобы Windows 10 Mobile её декодировал.
                if (type.IndexOf("audio/mp4", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (details.DashAudio == null)
                    {
                        var audioUrl = ResolveStreamUrl(Str(stream, "url"));
                        details.AudioUrl = audioUrl;
                        details.DashAudio = new DashTrack
                        {
                            Label = "audio",
                            Url = audioUrl,
                            InitRange = Str(stream, "init"),
                            IndexRange = Str(stream, "index"),
                            Codecs = Codecs(type),
                            MimeType = "audio/mp4",
                            Bandwidth = (long)Num(stream, "bitrate", 0),
                            AudioSampleRate = (int)Num(stream, "audioSampleRate", 0),
                            AudioChannels = (int)Num(stream, "audioChannels", 0)
                        };
                    }
                    continue;
                }

                if (type.IndexOf("video/mp4", StringComparison.OrdinalIgnoreCase) < 0) continue;

                var label = Str(stream, "qualityLabel");
                if (string.IsNullOrEmpty(label))
                {
                    var height = (int)Num(stream, "height", 0);
                    if (height > 0) label = height + "p";
                }
                if (string.IsNullOrEmpty(label) || used.Contains(label)) continue;

                var heightValue = (int)Num(stream, "height", 0);
                if (heightValue > 0 && heightValue < 480) continue;

                var url = ResolveStreamUrl(Str(stream, "url"));
                if (string.IsNullOrEmpty(url)) continue;

                var heightParsed = heightValue;
                if (heightParsed <= 0)
                {
                    int parsedHeight;
                    var digits = new string(label.TakeWhile(char.IsDigit).ToArray());
                    if (int.TryParse(digits, out parsedHeight)) heightParsed = parsedHeight;
                }

                details.Streams.Add(new StreamOption
                {
                    Url = url,
                    MimeType = type,
                    QualityLabel = label,
                    IsCombined = false,
                    IsDash = true
                });

                details.DashVideo[label] = new DashTrack
                {
                    Label = label,
                    Url = url,
                    InitRange = Str(stream, "init"),
                    IndexRange = Str(stream, "index"),
                    Codecs = Codecs(type),
                    MimeType = "video/mp4",
                    Bandwidth = (long)Num(stream, "bitrate", 0),
                    Height = heightParsed
                };
                used.Add(label);
            }
        }

        private static string Codecs(string type)
        {
            var marker = "codecs=\"";
            var start = type.IndexOf(marker, StringComparison.OrdinalIgnoreCase);
            if (start < 0) return string.Empty;
            start += marker.Length;
            var end = type.IndexOf('"', start);
            return end < 0 ? string.Empty : type.Substring(start, end - start);
        }

        private static int Rank(StreamOption stream)
        {
            var label = stream.QualityLabel ?? string.Empty;
            if (label.StartsWith("360", StringComparison.Ordinal)) return 0;
            if (label.StartsWith("480", StringComparison.Ordinal)) return 1;
            if (label.StartsWith("720", StringComparison.Ordinal)) return 2;
            if (label.StartsWith("1080", StringComparison.Ordinal)) return 3;
            return 4;
        }

        private void ReadRelated(JsonObject root, VideoDetails details)
        {
            IJsonValue value;
            if (!root.TryGetValue("recommendedVideos", out value) || value.ValueType != JsonValueType.Array)
            {
                return;
            }

            var index = 0;
            foreach (var item in value.GetArray())
            {
                if (index++ >= 10) break;
                if (item.ValueType != JsonValueType.Object) continue;

                var video = ParseItem(item.GetObject());
                if (video != null) details.Related.Add(video);
            }
        }

        private List<VideoItem> ParseItems(string json)
        {
            var result = new List<VideoItem>();
            var array = JsonArray.Parse(json);

            foreach (var value in array)
            {
                if (value.ValueType != JsonValueType.Object) continue;
                var item = ParseItem(value.GetObject());
                if (item != null) result.Add(item);
            }

            return result;
        }

        private VideoItem ParseItem(JsonObject o)
        {
            var id = Str(o, "videoId");
            if (string.IsNullOrEmpty(id)) return null;

            return new VideoItem
            {
                VideoId = id,
                Title = Str(o, "title") ?? string.Empty,
                Author = Str(o, "author") ?? string.Empty,
                AuthorId = Str(o, "authorId"),
                // Прямой JPEG вместо возможного WebP с инстанса.
                ThumbnailUrl = Thumb(id),
                DurationSeconds = (int)Num(o, "lengthSeconds"),
                ViewCount = (long)Num(o, "viewCount", -1),
                PublishedText = Str(o, "publishedText") ?? string.Empty
            };
        }

        private static string Thumb(string videoId)
        {
            return string.IsNullOrEmpty(videoId)
                ? null
                : "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
        }

        private string Thumbnail(JsonObject o)
        {
            IJsonValue value;
            if (!o.TryGetValue("videoThumbnails", out value) || value.ValueType != JsonValueType.Array)
            {
                return null;
            }

            string best = null;
            double bestWidth = -1;

            foreach (var item in value.GetArray())
            {
                if (item.ValueType != JsonValueType.Object) continue;

                var thumb = item.GetObject();
                var url = Str(thumb, "url");
                if (string.IsNullOrEmpty(url)) continue;

                var width = Num(thumb, "width", 0);
                if (best == null || width > bestWidth)
                {
                    best = url;
                    bestWidth = width;
                }
            }

            return ResolveUrl(best);
        }

        /// <summary>
        /// Приводит относительный URL к абсолютному. Если инстанс всё же вернул
        /// прямой googlevideo (старая версия без local=true), подменяем хост на
        /// инстанс — он умеет проксировать /videoplayback.
        /// </summary>
        private string ResolveStreamUrl(string url)
        {
            if (string.IsNullOrEmpty(url)) return null;

            if (url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                Uri uri;
                if (Uri.TryCreate(url, UriKind.Absolute, out uri) &&
                    (uri.Host.IndexOf("googlevideo.com", StringComparison.OrdinalIgnoreCase) >= 0 ||
                     uri.Host.Equals("localhost", StringComparison.OrdinalIgnoreCase) ||
                     uri.Host == "127.0.0.1" ||
                     uri.Host.StartsWith("192.168.") ||
                     uri.Host.StartsWith("10.")))
                {
                    return _baseUrl + uri.PathAndQuery;
                }
                return url;
            }

            return ResolveUrl(url);
        }

        private string ResolveUrl(string url)
        {
            if (string.IsNullOrEmpty(url)) return null;
            if (url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                return url;
            }
            if (url.StartsWith("/")) return _baseUrl + url;
            return _baseUrl + "/" + url;
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
                Timeout = TimeSpan.FromSeconds(12)
            };

            client.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent", "CrimTube-UWP/0.3");
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
    }
}
