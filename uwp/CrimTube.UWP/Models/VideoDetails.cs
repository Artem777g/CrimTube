using System.Collections.Generic;

namespace CrimTube.UWP.Models
{
    /// <summary>
    /// Подробности видео и доступные потоки.
    /// </summary>
    public sealed class VideoDetails
    {
        public string VideoId { get; set; }
        public string Title { get; set; }
        public string Author { get; set; }
        public string AuthorId { get; set; }
        public string ThumbnailUrl { get; set; }
        public string Description { get; set; }
        public int LengthSeconds { get; set; }
        public long ViewCount { get; set; } = -1;
        public long LikeCount { get; set; } = -1;

        public List<StreamOption> Streams { get; } = new List<StreamOption>();
        public List<VideoItem> Related { get; } = new List<VideoItem>();

        /// <summary>Аудиодорожка (AAC/mp4) для озвучивания видео без звука.</summary>
        public string AudioUrl { get; set; }

        /// <summary>DASH-дорожки видео по подписи качества (720p, 1080p...).</summary>
        public Dictionary<string, DashTrack> DashVideo { get; } =
            new Dictionary<string, DashTrack>();

        /// <summary>DASH-аудиодорожка (AAC).</summary>
        public DashTrack DashAudio { get; set; }

        /// <summary>Базовый адрес инстанса для манифеста.</summary>
        public string DashBaseUrl { get; set; }

        /// <summary>
        /// Поток по умолчанию: 360p, он же единственный совмещённый (видео + звук).
        /// </summary>
        public StreamOption DefaultStream
        {
            get { return Streams.Count > 0 ? Streams[0] : null; }
        }
    }
}
