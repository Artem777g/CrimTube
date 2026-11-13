using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using CrimTube.UWP.Services;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace CrimTube.UWP
{
    /// <summary>
    /// Строка списка инстансов: адрес и результат пинга.
    /// </summary>
    public sealed class EndpointStatus : INotifyPropertyChanged
    {
        private string _pingText = "—";

        public EndpointStatus(Endpoint endpoint)
        {
            Endpoint = endpoint;
        }

        public Endpoint Endpoint { get; private set; }

        public string Title
        {
            get { return Endpoint.DisplayName; }
        }

        public string PingText
        {
            get { return _pingText; }
            private set
            {
                _pingText = value;
                var handler = PropertyChanged;
                if (handler != null) handler(this, new PropertyChangedEventArgs("PingText"));
            }
        }

        public void SetPing(string text)
        {
            PingText = text;
        }

        public event PropertyChangedEventHandler PropertyChanged;
    }

    /// <summary>
    /// Настройки: тип инстанса, адрес, проверка связи и пинг всех адресов.
    /// </summary>
    public sealed partial class SettingsPage : Page
    {
        private readonly List<EndpointStatus> _statuses = new List<EndpointStatus>();
        private readonly SemaphoreSlim _pingGate = new SemaphoreSlim(8);

        public SettingsPage()
        {
            InitializeComponent();

            foreach (var preset in AppSettings.Presets)
            {
                _statuses.Add(new EndpointStatus(preset));
            }
            PresetList.ItemsSource = _statuses;
        }

        protected override void OnNavigatedTo(NavigationEventArgs e)
        {
            base.OnNavigatedTo(e);
            InstanceBox.Text = AppSettings.InstanceUrl;
            KindBox.SelectedIndex = AppSettings.InstanceIsInvidious ? 0 : 1;
            ServerBox.Text = AppSettings.ServerUrl;
            UpdateBox.Text = AppSettings.UpdateUrl;
            ShowStatus(string.Empty);
        }

        private void OnPresetClick(object sender, ItemClickEventArgs e)
        {
            var status = e.ClickedItem as EndpointStatus;
            if (status == null) return;

            InstanceBox.Text = status.Endpoint.Url;
            KindBox.SelectedIndex = status.Endpoint.IsInvidious ? 0 : 1;
        }

        private void OnSaveClick(object sender, Windows.UI.Xaml.RoutedEventArgs e)
        {
            var url = AppSettings.Normalize(InstanceBox.Text);
            var isInvidious = KindBox.SelectedIndex == 0;
            AppSettings.Save(url, isInvidious);
            AppSettings.ServerUrl = ServerBox.Text;
            AppSettings.UpdateUrl = UpdateBox.Text;
            ServerBox.Text = AppSettings.ServerUrl;
            UpdateBox.Text = AppSettings.UpdateUrl;
            InstanceBox.Text = url;
            ShowStatus("Сохранено: " + url + ". Список на главном экране обновится при возврате.");
        }

        private async void OnCheckClick(object sender, Windows.UI.Xaml.RoutedEventArgs e)
        {
            var url = AppSettings.Normalize(InstanceBox.Text);
            var isInvidious = KindBox.SelectedIndex == 0;
            ShowStatus("Проверяю " + url + "…");

            try
            {
                var client = CreateClient(url, isInvidious);
                var ok = await client.PingAsync();
                ShowStatus(ok
                    ? "Инстанс отвечает. Можно сохранять."
                    : "Инстанс не ответил. Проверьте адрес или попробуйте другой.");
            }
            catch (Exception ex)
            {
                ShowStatus("Ошибка: " + ex.Message);
            }
        }

        private async void OnUpdateCheckClick(object sender, Windows.UI.Xaml.RoutedEventArgs e)
        {
            AppSettings.UpdateUrl = UpdateBox.Text;
            UpdateBox.Text = AppSettings.UpdateUrl;
            await UpdateService.CheckAsync(true);
        }

        private async void OnPingAllClick(object sender, Windows.UI.Xaml.RoutedEventArgs e)
        {
            PingAllButton.IsEnabled = false;
            foreach (var status in _statuses)
            {
                status.SetPing("…");
            }

            await Task.WhenAll(_statuses.Select(PingAsync));
            PingAllButton.IsEnabled = true;
        }

        private async void OnPingOneClick(object sender, Windows.UI.Xaml.RoutedEventArgs e)
        {
            var button = sender as Button;
            var status = button != null ? button.DataContext as EndpointStatus : null;
            if (status == null) return;

            status.SetPing("…");
            await PingAsync(status);
        }

        private async Task PingAsync(EndpointStatus status)
        {
            await _pingGate.WaitAsync();
            try
            {
                var watch = Stopwatch.StartNew();
                bool ok;

                try
                {
                    IVideoClient client = status.Endpoint.IsInvidious
                        ? (IVideoClient)new InvidiousClient(status.Endpoint.Url)
                        : new PipedClient(status.Endpoint.Url, status.Endpoint.ProxyUrl);
                    ok = await client.PingAsync();
                }
                catch (Exception)
                {
                    ok = false;
                }

                watch.Stop();
                status.SetPing(ok
                    ? watch.ElapsedMilliseconds + " мс"
                    : "нет ответа");
            }
            finally
            {
                _pingGate.Release();
            }
        }

        private static IVideoClient CreateClient(string url, bool isInvidious)
        {
            if (isInvidious) return new InvidiousClient(url);

            // Для Piped допускаем формат «api, proxy».
            var comma = url.IndexOf(',');
            if (comma > 0)
            {
                return new PipedClient(url.Substring(0, comma), url.Substring(comma + 1).Trim());
            }
            return new PipedClient(url);
        }

        private void ShowStatus(string message)
        {
            StatusText.Text = message ?? string.Empty;
            StatusText.Visibility = string.IsNullOrEmpty(message)
                ? Windows.UI.Xaml.Visibility.Collapsed
                : Windows.UI.Xaml.Visibility.Visible;
        }
    }
}
