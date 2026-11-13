using CrimTube.UWP.Util;

namespace CrimTube.UWP.Models
{
    /// <summary>
    /// Одна строка списка видео: то, что Invidious отдаёт в поиске и в «Популярном».
    /// </summary>
    public sealed class VideoItem
    {
        public string VideoId { get; set; }
        public string Title { get; set; }
        public string Author { get; set; }
        public string AuthorId { get; set; }
        public string ThumbnailUrl { get; set; }
        public int DurationSeconds { get; set; }
        public long ViewCount { get; set; } = -1;
        public string PublishedText { get; set; }

        public string LengthText
        {
            get { return Format.Duration(DurationSeconds); }
        }

        public string ViewsText
        {
            get { return ViewCount < 0 ? string.Empty : Format.Views(ViewCount); }
        }
    }
}
