namespace CrimTube.UWP.Models
{
    /// <summary>
    /// Одна дорожка для DASH-манифеста: видео или аудио.
    /// </summary>
    public sealed class DashTrack
    {
        public string Label { get; set; }
        public string Url { get; set; }
        public string InitRange { get; set; }
        public string IndexRange { get; set; }
        public string Codecs { get; set; }
        public string MimeType { get; set; }
        public long Bandwidth { get; set; }
        public int Width { get; set; }
        public int Height { get; set; }
        public int AudioSampleRate { get; set; }
        public int AudioChannels { get; set; }
    }
}
