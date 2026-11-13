using System.Collections.Generic;
using System.Threading.Tasks;
using CrimTube.UWP.Models;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Общий интерфейс провайдера: Piped и Invidious умеют одно и то же,
    /// поэтому экраны не должны знать, к какому типу относятся.
    /// </summary>
    public interface IVideoClient
    {
        string BaseUrl { get; }

        Task<IReadOnlyList<VideoItem>> GetPopularAsync();

        Task<IReadOnlyList<VideoItem>> SearchAsync(string query);

        Task<VideoDetails> GetVideoAsync(string videoId);

        Task<bool> PingAsync();
    }
}
