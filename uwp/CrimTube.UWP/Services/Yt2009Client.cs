using System;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading.Tasks;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Мини-клиент yt2009: он умеет отдавать готовый MP4 со звуком
    /// (/get_480), но только для видео, которые уже есть в его кэше.
    /// </summary>
    public static class Yt2009Client
    {
        public static readonly string[] Bases = new[]
        {
            "http://femboy.email:730",
            "http://179.254.152.239:8081"
        };

        /// <summary>
        /// Проверяет, отдаёт ли инстанс поток нужного качества.
        /// Возвращает прямой URL эндпоинта или null.
        /// </summary>
        public static async Task<string> TryGetStreamAsync(string baseUrl, string videoId, string quality)
        {
            var path = quality == "480"
                ? "/get_480?video_id=" + Uri.EscapeDataString(videoId)
                : quality == "1080"
                    ? "/exp_hd?video_id=" + Uri.EscapeDataString(videoId) + "&fhd=1"
                    : "/exp_hd?video_id=" + Uri.EscapeDataString(videoId);

            var url = baseUrl.TrimEnd('/') + path;

            try
            {
                var handler = new HttpClientHandler();
                handler.AllowAutoRedirect = false;

                using (var client = new HttpClient(handler))
                {
                    client.Timeout = TimeSpan.FromSeconds(8);
                    client.DefaultRequestHeaders.UserAgent.TryParseAdd("CrimTube-UWP/0.3");

                    using (var request = new HttpRequestMessage(HttpMethod.Get, url))
                    {
                        request.Headers.Range = new RangeHeaderValue(0, 1023);
                        using (var response = await client.SendAsync(
                            request, HttpCompletionOption.ResponseHeadersRead))
                        {
                            var code = (int)response.StatusCode;
                            if (code != 200 && code != 206) return null;

                            var type = response.Content.Headers.ContentType;
                            if (type == null || type.MediaType == null) return null;
                            if (type.MediaType.IndexOf("video", StringComparison.OrdinalIgnoreCase) < 0)
                            {
                                return null;
                            }
                        }
                    }
                }

                return url;
            }
            catch (Exception)
            {
                return null;
            }
        }
    }
}
