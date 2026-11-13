namespace CrimTube.UWP.Models
{
    /// <summary>
    /// Вариант воспроизведения, который отдал инстанс.
    /// </summary>
    public sealed class StreamOption
    {
        public string Url { get; set; }
        public string QualityLabel { get; set; }
        public string MimeType { get; set; }

        /// <summary>
        /// Поток содержит и видео, и звук: только такие проигрываются MediaPlayerElement.
        /// </summary>
        public bool IsCombined { get; set; }

        /// <summary>Поток воспроизводится через DASH-манифест.</summary>
        public bool IsDash { get; set; }

        public string DisplayName
        {
            get
            {
                if (string.IsNullOrEmpty(QualityLabel)) return "Поток";
                return QualityLabel;
            }
        }
    }
}
