using System;
using System.Threading.Tasks;
using CrimTube.UWP.Services;
using Windows.System;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace CrimTube.UWP
{
    /// <summary>
    /// Главный экран: «Популярное» с выбранного инстанса и поиск по видео.
    /// </summary>
    public sealed partial class MainPage : Page
    {
        private bool _loaded;
        private string _loadedInstance;

        public MainPage()
        {
            InitializeComponent();
        }

        protected override async void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);

            // Тихая проверка обновлений, если URL задан в настройках.
            _ = UpdateService.CheckAsync(false);

            // Перезагружаем, если пользователь сменил инстанс в настройках.
            var instance = AppSettings.InstanceUrl;
            if (_loaded && instance == _loadedInstance) return;

            _loaded = true;
            _loadedInstance = instance;
            await LoadAsync(null);
        }

        private async Task LoadAsync(string query)
        {
            SetBusy(true);
            ShowStatus("Загрузка…", false);

            try
            {
                var videos = string.IsNullOrEmpty(query)
                    ? await VideoApi.GetPopularAsync()
                    : await VideoApi.SearchAsync(query);

                VideoList.ItemsSource = videos;

                if (videos.Count == 0)
                {
                    ShowStatus(string.IsNullOrEmpty(query)
                        ? "Инстанс не вернул ни одного видео. Проверьте адрес в настройках."
                        : "Ничего не найдено.", true);
                }
                else
                {
                    ShowStatus(string.Empty, false);
                }
            }
            catch (Exception ex)
            {
                VideoList.ItemsSource = null;
                ShowStatus("Не удалось загрузить список.\n" + ex.Message, true);
            }
            finally
            {
                SetBusy(false);
            }
        }

        private async void OnSearchClick(object sender, RoutedEventArgs e)
        {
            await RunSearchAsync();
        }

        private async void OnSearchKeyDown(object sender, Windows.UI.Xaml.Input.KeyRoutedEventArgs e)
        {
            if (e.Key != VirtualKey.Enter) return;

            e.Handled = true;
            await RunSearchAsync();
        }

        private async Task RunSearchAsync()
        {
            var query = (SearchBox.Text ?? string.Empty).Trim();

            // Скрываем клавиатуру, чтобы она не закрывала результаты.
            SearchBox.IsEnabled = false;
            SearchBox.IsEnabled = true;

            await LoadAsync(query.Length == 0 ? null : query);
        }

        private async void OnRefreshClick(object sender, RoutedEventArgs e)
        {
            var query = (SearchBox.Text ?? string.Empty).Trim();
            await LoadAsync(query.Length == 0 ? null : query);
        }

        private void OnSettingsClick(object sender, RoutedEventArgs e)
        {
            Frame.Navigate(typeof(SettingsPage));
        }

        private void OnVideoClick(object sender, ItemClickEventArgs e)
        {
            var video = e.ClickedItem as Models.VideoItem;
            if (video == null) return;

            Frame.Navigate(typeof(VideoPage), video.VideoId);
        }

        private void SetBusy(bool busy)
        {
            BusyBar.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
        }

        private void ShowStatus(string text, bool visible)
        {
            StatusText.Text = text;
            StatusText.Visibility = visible ? Visibility.Visible : Visibility.Collapsed;
        }
    }
}
