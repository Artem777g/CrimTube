using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using CrimTube.UWP.Models;
using CrimTube.UWP.Services;
using CrimTube.UWP.Util;
using System.Text;
using Windows.Media.Core;
using Windows.Media.Playback;
using Windows.Media.Streaming.Adaptive;
using Windows.Storage.Streams;
using Windows.System;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace CrimTube.UWP
{
    /// <summary>
    /// Экран видео: проигрывание совмещённого потока (360p), описание и похожие видео.
    /// </summary>
    public sealed partial class VideoPage : Page
    {
        private string _videoId;
        private VideoDetails _details;
        private bool _applying;
        private MediaPlayer _audioPlayer;
        private DispatcherTimer _syncTimer;
        private bool _audioReady;
        private AdaptiveMediaSource _adaptiveSource;
        private string _dashStatus;

        public VideoPage()
        {
            InitializeComponent();
        }

        protected override async void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            _videoId = e.Parameter as string;
            if (string.IsNullOrEmpty(_videoId))
            {
                ShowError("Видео не выбрано.");
                return;
            }

            await LoadAsync();
        }

        protected override void OnNavigatedFrom(NavigationEventArgs e)
        {
            base.OnNavigatedFrom(e);

            // Освобождаем декодер, иначе следующее видео может не запуститься на телефоне.
            if (Player.MediaPlayer != null)
            {
                Player.MediaPlayer.Pause();
            }

            StopAudio();
            Player.Source = null;
        }

        private async Task LoadAsync()
        {
            SetBusy(true);
            ShowError(null);
            RetryButton.Visibility = Visibility.Collapsed;
            TitleText.Text = "Загрузка…";

            try
            {
                var details = await VideoApi.GetVideoAsync(_videoId);
                Apply(details);
                await AddYt2009StreamsAsync(details);
            }
            catch (Exception ex)
            {
                ShowError(ErrorText.Open(ex.Message));
                TitleText.Text = _videoId;
                RetryButton.Visibility = Visibility.Visible;
            }
            finally
            {
                SetBusy(false);
            }
        }

        private void Apply(VideoDetails details)
        {
            TitleText.Text = details.Title;

            var meta = details.Author;
            if (details.ViewCount >= 0) meta += " • " + Format.Views(details.ViewCount);
            if (details.LengthSeconds > 0) meta += " • " + Format.Duration(details.LengthSeconds);
            MetaText.Text = meta;

            DescriptionText.Text = details.Description ?? string.Empty;
            RelatedList.ItemsSource = details.Related;

            _details = details;
            QualityPanel.Visibility = details.Streams.Count > 0 ? Visibility.Visible : Visibility.Collapsed;

            if (details.Streams.Count == 0)
            {
                ShowError("Инстанс не вернул поток для этого видео.");
                RetryButton.Visibility = Visibility.Visible;
                return;
            }

            _applying = true;
            QualityBox.ItemsSource = details.Streams;

            var index = 0;
            var preferred = AppSettings.PreferredQuality;
            for (var i = 0; i < details.Streams.Count; i++)
            {
                if (string.Equals(details.Streams[i].QualityLabel, preferred,
                        StringComparison.OrdinalIgnoreCase))
                {
                    index = i;
                    break;
                }
            }
            QualityBox.SelectedIndex = index;
            _applying = false;

            PlayStream(details.Streams[index]);
        }

        private async void PlayStream(StreamOption stream)
        {
            if (stream == null || string.IsNullOrEmpty(stream.Url)) return;

            try
            {
                StopAudio();
                if (_adaptiveSource != null)
                {
                    _adaptiveSource.Dispose();
                    _adaptiveSource = null;
                }

                // Если настроен mux-сервер, высокие качества играем одним файлом,
                // который сервер уже склеил из видео и звука.
                var server = AppSettings.ServerUrl;
                if (!string.IsNullOrEmpty(server) && !stream.IsCombined)
                {
                    var height = QualityHeight(stream.QualityLabel);
                    if (height >= 480)
                    {
                        var serverUrl = server + "/live/" + Uri.EscapeDataString(_videoId) +
                                        "?q=" + height;
                        Player.Source = MediaSource.CreateFromUri(new Uri(serverUrl));
                        if (Player.MediaPlayer != null)
                        {
                            Player.MediaPlayer.MediaEnded -= OnVideoEnded;
                            Player.MediaPlayer.MediaEnded += OnVideoEnded;
                            Player.MediaPlayer.Play();
                        }
                        ShowError("Сервер склеивает поток, подождите…");
                        RetryButton.Visibility = Visibility.Collapsed;
                        return;
                    }
                }

                // Adaptive-потоки играем через DASH: видео и аудио склеивает
                // сам Windows, поэтому синхронизация не требует второго плеера.
                if (stream.IsDash && _details != null && _details.DashAudio != null &&
                    _details.DashVideo.ContainsKey(stream.QualityLabel))
                {
                    var mpd = BuildMpd(_details.DashVideo[stream.QualityLabel], _details.DashAudio);
                    var adaptive = await CreateAdaptiveAsync(mpd);
                    if (adaptive != null)
                    {
                        _adaptiveSource = adaptive;
                        Player.Source = MediaSource.CreateFromAdaptiveMediaSource(adaptive);
                        if (Player.MediaPlayer != null)
                        {
                            Player.MediaPlayer.MediaEnded -= OnVideoEnded;
                            Player.MediaPlayer.MediaEnded += OnVideoEnded;
                            Player.MediaPlayer.Play();
                        }
                        ShowError(null);
                        RetryButton.Visibility = Visibility.Collapsed;
                        return;
                    }

                    // DASH не собрался: не оставляем пользователя без звука,
                    // включаем совмещённый 360p и показываем причину.
                    var fallback = _details.Streams.FirstOrDefault(s => s.IsCombined);
                    if (fallback != null && fallback != stream)
                    {
                        _applying = true;
                        QualityBox.SelectedItem = fallback;
                        _applying = false;
                        PlayStream(fallback);
                        ShowError("DASH не удался (" + (_dashStatus ?? "ошибка") +
                                  "). Включён 360p со звуком.");
                        return;
                    }

                    ShowError("DASH не удался (" + (_dashStatus ?? "ошибка") + ").");
                    return;
                }

                Player.Source = MediaSource.CreateFromUri(new Uri(stream.Url));
                if (Player.MediaPlayer != null)
                {
                    Player.MediaPlayer.MediaEnded -= OnVideoEnded;
                    Player.MediaPlayer.MediaEnded += OnVideoEnded;
                    Player.MediaPlayer.Play();
                }

                ShowError(null);
                RetryButton.Visibility = Visibility.Collapsed;
            }
            catch (Exception ex)
            {
                ShowError(ErrorText.Playback(ex.Message));
                RetryButton.Visibility = Visibility.Visible;
            }
        }

        private string BuildMpd(DashTrack video, DashTrack audio)
        {
            var duration = _details != null ? _details.LengthSeconds : 0;

            var sb = new StringBuilder();
            sb.Append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            sb.Append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\"");
            if (duration > 0) sb.Append(" mediaPresentationDuration=\"PT").Append(duration).Append("S\"");
            sb.Append(" minBufferTime=\"PT2S\"");
            sb.Append(" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\">");
            sb.Append("<Period>");

            sb.Append("<AdaptationSet mimeType=\"video/mp4\" segmentAlignment=\"true\" startWithSAP=\"1\">");
            AppendRepresentation(sb, "v", video, true);
            sb.Append("</AdaptationSet>");

            sb.Append("<AdaptationSet mimeType=\"audio/mp4\" segmentAlignment=\"true\">");
            AppendRepresentation(sb, "a", audio, false);
            sb.Append("</AdaptationSet>");

            sb.Append("</Period></MPD>");
            return sb.ToString();
        }

        private static void AppendRepresentation(StringBuilder sb, string id, DashTrack track, bool isVideo)
        {
            sb.Append("<Representation id=\"").Append(id).Append("\"");
            if (track.Bandwidth > 0) sb.Append(" bandwidth=\"").Append(track.Bandwidth).Append("\"");
            if (isVideo)
            {
                if (track.Width > 0) sb.Append(" width=\"").Append(track.Width).Append("\"");
                if (track.Height > 0) sb.Append(" height=\"").Append(track.Height).Append("\"");
            }
            else
            {
                if (track.AudioSampleRate > 0)
                {
                    sb.Append(" audioSamplingRate=\"").Append(track.AudioSampleRate).Append("\"");
                }
                if (track.AudioChannels > 0)
                {
                    sb.Append(" audioChannels=\"").Append(track.AudioChannels).Append("\"");
                }
            }
            if (!string.IsNullOrEmpty(track.Codecs))
            {
                sb.Append(" codecs=\"").Append(EscapeXml(track.Codecs)).Append("\"");
            }
            sb.Append(">");
            sb.Append("<BaseURL>").Append(EscapeXml(track.Url)).Append("</BaseURL>");

            if (!string.IsNullOrEmpty(track.IndexRange))
            {
                sb.Append("<SegmentBase indexRange=\"").Append(track.IndexRange).Append("\">");
                if (!string.IsNullOrEmpty(track.InitRange))
                {
                    sb.Append("<Initialization range=\"").Append(track.InitRange).Append("\"/>");
                }
                sb.Append("</SegmentBase>");
            }
            sb.Append("</Representation>");
        }

        private static string EscapeXml(string value)
        {
            if (string.IsNullOrEmpty(value)) return string.Empty;
            return value.Replace("&", "&amp;")
                        .Replace("<", "&lt;")
                        .Replace(">", "&gt;")
                        .Replace("\"", "&quot;");
        }

        private async Task<AdaptiveMediaSource> CreateAdaptiveAsync(string mpd)
        {
            try
            {
                var stream = new InMemoryRandomAccessStream();
                var writer = new DataWriter(stream);
                writer.WriteString(mpd);
                await writer.StoreAsync();
                await writer.FlushAsync();
                writer.DetachStream();
                stream.Seek(0);

                var baseUri = new Uri(string.IsNullOrEmpty(_details.DashBaseUrl)
                    ? "http://localhost/"
                    : _details.DashBaseUrl);

                var result = await AdaptiveMediaSource.CreateFromStreamAsync(
                    stream, baseUri, "application/dash+xml");

                if (result.Status == AdaptiveMediaSourceCreationStatus.Success)
                {
                    return result.MediaSource;
                }

                _dashStatus = result.Status.ToString();
                return null;
            }
            catch (Exception ex)
            {
                _dashStatus = ErrorText.Playback(ex.Message);
                return null;
            }
        }

        /// <summary>
        /// Добавляет качества yt2009 (готовый MP4 со звуком) к уже загруженному видео.
        /// </summary>
        private async Task AddYt2009StreamsAsync(VideoDetails details)
        {
            if (details == null) return;

            var added = new List<StreamOption>();
            foreach (var baseUrl in Yt2009Client.Bases)
            {
                foreach (var quality in new[] { "480", "1080" })
                {
                    var url = await Yt2009Client.TryGetStreamAsync(baseUrl, details.VideoId, quality);
                    if (string.IsNullOrEmpty(url)) continue;

                    added.Add(new StreamOption
                    {
                        Url = url,
                        MimeType = "video/mp4",
                        QualityLabel = quality + "p (yt2009)",
                        IsCombined = true
                    });
                }
            }

            if (added.Count == 0) return;

            var selected = QualityBox.SelectedItem as StreamOption;
            details.Streams.AddRange(added);
            details.Streams.Sort(delegate (StreamOption a, StreamOption b)
            {
                if (a.IsCombined != b.IsCombined) return a.IsCombined ? -1 : 1;
                return QualityRank(a.QualityLabel).CompareTo(QualityRank(b.QualityLabel));
            });

            _applying = true;
            QualityBox.ItemsSource = null;
            QualityBox.ItemsSource = details.Streams;

            var index = 0;
            if (selected != null && !string.IsNullOrEmpty(selected.QualityLabel))
            {
                for (var i = 0; i < details.Streams.Count; i++)
                {
                    if (string.Equals(details.Streams[i].QualityLabel, selected.QualityLabel,
                            StringComparison.OrdinalIgnoreCase))
                    {
                        index = i;
                        break;
                    }
                }
            }
            QualityBox.SelectedIndex = index;
            _applying = false;
        }

        private static int QualityHeight(string label)
        {
            if (string.IsNullOrEmpty(label)) return 0;

            var digits = new StringBuilder();
            for (var i = 0; i < label.Length && char.IsDigit(label[i]); i++)
            {
                digits.Append(label[i]);
            }

            int height;
            return int.TryParse(digits.ToString(), out height) ? height : 0;
        }

        private static int QualityRank(string label)
        {
            label = label ?? string.Empty;
            if (label.StartsWith("360", StringComparison.Ordinal)) return 0;
            if (label.StartsWith("480", StringComparison.Ordinal)) return 1;
            if (label.StartsWith("720", StringComparison.Ordinal)) return 2;
            if (label.StartsWith("1080", StringComparison.Ordinal)) return 3;
            return 4;
        }

        private void OnVideoEnded(MediaPlayer sender, object args)
        {
            StopAudio();
        }

        private void StartAudio(string url)
        {
            try
            {
                _audioReady = false;
                _audioPlayer = new MediaPlayer();
                _audioPlayer.MediaOpened += OnAudioOpened;
                _audioPlayer.MediaFailed += OnAudioFailed;
                _audioPlayer.Source = MediaSource.CreateFromUri(new Uri(url));

                if (Player.MediaPlayer != null)
                {
                    Player.MediaPlayer.PlaybackSession.PlaybackStateChanged += OnVideoStateChanged;
                    Player.MediaPlayer.PlaybackSession.SeekCompleted += OnVideoSeekCompleted;
                    Player.MediaPlayer.MediaFailed -= OnVideoFailed;
                    Player.MediaPlayer.MediaFailed += OnVideoFailed;
                }

                _syncTimer = new DispatcherTimer();
                _syncTimer.Interval = TimeSpan.FromMilliseconds(300);
                _syncTimer.Tick += SyncAudio;
                _syncTimer.Start();
            }
            catch (Exception)
            {
                StopAudio();
            }
        }

        private void OnAudioOpened(MediaPlayer sender, object args)
        {
            _audioReady = true;
            // Первый запуск: ставим позицию на паузе, иначе W10M может её проигнорировать.
            AlignAudio(true);
        }

        private void OnAudioFailed(MediaPlayer sender, MediaPlayerFailedEventArgs args)
        {
            _audioReady = false;
            ShowError("Аудиодорожка не декодируется. " + ErrorText.Playback(args.ErrorMessage));
        }

        private void OnVideoFailed(MediaPlayer sender, MediaPlayerFailedEventArgs args)
        {
            StopAudio();
            ShowError("Ошибка декодирования потока при перемотке. Переключите качество или откройте видео заново.");
        }

        private void OnVideoSeekCompleted(MediaPlaybackSession sender, object args)
        {
            AlignAudio(true);
            if (_audioReady && _audioPlayer != null &&
                sender.PlaybackState == MediaPlaybackState.Playing)
            {
                try { _audioPlayer.Play(); } catch (Exception) { }
            }
        }

        /// <summary>
        /// Выравнивает аудио по видео. На W10M позиция у играющего плеера
        /// применяется не всегда, поэтому при коррекции ставим паузу.
        /// </summary>
        private void AlignAudio(bool pauseFirst)
        {
            if (!_audioReady || _audioPlayer == null || Player.MediaPlayer == null) return;

            try
            {
                if (pauseFirst)
                {
                    _audioPlayer.Pause();
                }

                // Небольшое опережение компенсирует задержку старта аудиоконвейера.
                var target = Player.MediaPlayer.PlaybackSession.Position +
                             TimeSpan.FromMilliseconds(150);
                _audioPlayer.PlaybackSession.Position = target;

                if (Player.MediaPlayer.PlaybackSession.PlaybackState == MediaPlaybackState.Playing)
                {
                    _audioPlayer.Play();
                }
            }
            catch (Exception)
            {
            }
        }

        private void OnVideoStateChanged(MediaPlaybackSession sender, object args)
        {
            if (_audioPlayer == null || !_audioReady) return;

            try
            {
                if (sender.PlaybackState == MediaPlaybackState.Playing)
                {
                    AlignAudio(true);
                    _audioPlayer.Play();
                }
                else
                {
                    // Любая пауза/буферизация видео должна останавливать звук.
                    _audioPlayer.Pause();
                }
            }
            catch (Exception)
            {
            }
        }

        private void SyncAudio(object sender, object args)
        {
            if (_audioPlayer == null || !_audioReady || Player.MediaPlayer == null) return;

            try
            {
                var video = Player.MediaPlayer;
                var videoState = video.PlaybackSession.PlaybackState;

                if (Math.Abs(_audioPlayer.Volume - video.Volume) > 0.01)
                {
                    _audioPlayer.Volume = video.Volume;
                }
                if (_audioPlayer.IsMuted != video.IsMuted)
                {
                    _audioPlayer.IsMuted = video.IsMuted;
                }

                if (videoState != MediaPlaybackState.Playing)
                {
                    if (_audioPlayer.PlaybackSession.PlaybackState == MediaPlaybackState.Playing)
                    {
                        _audioPlayer.Pause();
                    }
                    return;
                }

                var drift = (video.PlaybackSession.Position -
                             _audioPlayer.PlaybackSession.Position).TotalMilliseconds;
                if (Math.Abs(drift) > 200)
                {
                    AlignAudio(true);
                }

                if (_audioPlayer.PlaybackSession.PlaybackState != MediaPlaybackState.Playing)
                {
                    _audioPlayer.Play();
                }
            }
            catch (Exception)
            {
            }
        }

        private void StopAudio()
        {
            if (_syncTimer != null)
            {
                _syncTimer.Stop();
                _syncTimer = null;
            }

            if (Player.MediaPlayer != null)
            {
                Player.MediaPlayer.PlaybackSession.PlaybackStateChanged -= OnVideoStateChanged;
                Player.MediaPlayer.PlaybackSession.SeekCompleted -= OnVideoSeekCompleted;
                Player.MediaPlayer.MediaFailed -= OnVideoFailed;
            }

            _audioReady = false;
            if (_audioPlayer != null)
            {
                _audioPlayer.MediaOpened -= OnAudioOpened;
                _audioPlayer.MediaFailed -= OnAudioFailed;
                try
                {
                    _audioPlayer.Pause();
                }
                catch (Exception)
                {
                }
                _audioPlayer.Dispose();
                _audioPlayer = null;
            }
        }

        private void OnQualityChanged(object sender, SelectionChangedEventArgs e)
        {
            if (_applying || _details == null) return;

            var stream = QualityBox.SelectedItem as StreamOption;
            if (stream == null) return;

            AppSettings.PreferredQuality = stream.QualityLabel;
            PlayStream(stream);
        }

        private void OnRelatedClick(object sender, ItemClickEventArgs e)
        {
            var video = e.ClickedItem as VideoItem;
            if (video == null) return;

            Frame.Navigate(typeof(VideoPage), video.VideoId);
        }

        private async void OnOpenInBrowserClick(object sender, RoutedEventArgs e)
        {
            if (string.IsNullOrEmpty(_videoId)) return;

            await Launcher.LaunchUriAsync(new Uri("https://youtu.be/" + _videoId));
        }

        private void SetBusy(bool busy)
        {
            BusyBar.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        }

        private void ShowError(string message)
        {
            if (string.IsNullOrEmpty(message))
            {
                StatusText.Text = string.Empty;
                StatusText.Visibility = Visibility.Collapsed;
                return;
            }

            StatusText.Text = message;
            StatusText.Visibility = Visibility.Visible;
        }
    }
}
