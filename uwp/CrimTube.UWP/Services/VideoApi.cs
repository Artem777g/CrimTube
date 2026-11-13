using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using CrimTube.UWP.Models;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Точка входа к видеоданным: перебирает Invidious и Piped из настроек,
    /// пока какой-нибудь не ответит, и запоминает рабочий.
    /// </summary>
    public static class VideoApi
    {
        public static Task<IReadOnlyList<VideoItem>> GetPopularAsync()
        {
            return WithFallbackAsync(client => client.GetPopularAsync());
        }

        public static Task<IReadOnlyList<VideoItem>> SearchAsync(string query)
        {
            return WithFallbackAsync(client => client.SearchAsync(query));
        }

        public static Task<VideoDetails> GetVideoAsync(string videoId)
        {
            return WithFallbackAsync(client => client.GetVideoAsync(videoId));
        }

        private static async Task<T> WithFallbackAsync<T>(Func<IVideoClient, Task<T>> action)
        {
            Exception last = null;
            var errors = new List<string>();

            foreach (var endpoint in AppSettings.Candidates())
            {
                try
                {
                    var result = await action(CreateClient(endpoint));

                    // Запоминаем рабочий адрес, чтобы следующий запуск начинал с него.
                    var current = AppSettings.Current;
                    if (!string.Equals(endpoint.Url, current.Url, StringComparison.OrdinalIgnoreCase) ||
                        endpoint.IsInvidious != current.IsInvidious)
                    {
                        AppSettings.SaveWorking(endpoint);
                    }

                    return result;
                }
                catch (Exception ex)
                {
                    last = ex;
                    var message = ex.Message ?? "ошибка";
                    if (message.Length > 110) message = message.Substring(0, 110) + "…";
                    errors.Add((endpoint.IsInvidious ? "Invidious" : "Piped") + ": " + message);
                }
            }

            throw new InvalidOperationException(
                "Ни один инстанс не ответил. " + string.Join(" | ", errors.ToArray()));
        }

        private static IVideoClient CreateClient(Endpoint endpoint)
        {
            return endpoint.IsInvidious
                ? (IVideoClient)new InvidiousClient(endpoint.Url)
                : new PipedClient(endpoint.Url, endpoint.ProxyUrl);
        }
    }
}
