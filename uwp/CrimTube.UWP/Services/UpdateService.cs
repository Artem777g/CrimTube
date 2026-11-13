using System;
using System.Net.Http;
using System.Threading.Tasks;
using Windows.ApplicationModel;
using Windows.Data.Json;
using Windows.Storage;
using Windows.System;
using Windows.UI.Xaml.Controls;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Проверка обновлений по latest.json (GitHub raw/Pages) и запуск установщика appx.
    /// UWP не может ставиться молча: пользователь подтверждает установку.
    /// </summary>
    public static class UpdateService
    {
        public static async Task CheckAsync(bool manual)
        {
            var url = AppSettings.UpdateUrl;
            if (string.IsNullOrEmpty(url))
            {
                if (manual) await ShowMessage("URL обновлений не задан. Укажите его в настройках.");
                return;
            }

            try
            {
                using (var http = new HttpClient())
                {
                    http.Timeout = TimeSpan.FromSeconds(20);
                    var json = await http.GetStringAsync(url);
                    var root = JsonObject.Parse(json);

                    IJsonValue value;
                    if (!root.TryGetValue("uwp", out value) || value.ValueType != JsonValueType.Object)
                    {
                        if (manual) await ShowMessage("В latest.json нет раздела uwp.");
                        return;
                    }

                    var data = value.GetObject();
                    var versionText = Str(data, "version") ?? string.Empty;
                    var download = Str(data, "url") ?? string.Empty;
                    var changelog = Str(data, "changelog") ?? string.Empty;

                    Version latest;
                    if (!Version.TryParse(versionText, out latest))
                    {
                        if (manual) await ShowMessage("Некорректная версия в latest.json: " + versionText);
                        return;
                    }

                    var current = Package.Current.Id.Version;
                    var currentVersion = new Version(current.Major, current.Minor, current.Build, current.Revision);

                    if (latest <= currentVersion)
                    {
                        if (manual) await ShowMessage("Установлена актуальная версия " + versionText + ".");
                        return;
                    }

                    var dialog = new ContentDialog
                    {
                        Title = "Доступно обновление " + versionText,
                        Content = string.IsNullOrEmpty(changelog)
                            ? "Скачать и установить новую версию?"
                            : changelog,
                        PrimaryButtonText = "Скачать",
                        CloseButtonText = "Позже"
                    };

                    if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
                    if (string.IsNullOrEmpty(download))
                    {
                        await ShowMessage("В latest.json не указан url для appx.");
                        return;
                    }

                    var file = await ApplicationData.Current.LocalFolder.CreateFileAsync(
                        "CrimTube-update.appx", CreationCollisionOption.ReplaceExisting);

                    using (var response = await http.GetAsync(download))
                    {
                        response.EnsureSuccessStatusCode();
                        var bytes = await response.Content.ReadAsByteArrayAsync();
                        await FileIO.WriteBytesAsync(file, bytes);
                    }

                    await Launcher.LaunchFileAsync(file);
                }
            }
            catch (Exception ex)
            {
                if (manual) await ShowMessage("Не удалось проверить обновления: " + ex.Message);
            }
        }

        private static async Task ShowMessage(string message)
        {
            await new ContentDialog
            {
                Title = "Обновления",
                Content = message,
                CloseButtonText = "OK"
            }.ShowAsync();
        }

        private static string Str(JsonObject o, string key)
        {
            IJsonValue value;
            if (!o.TryGetValue(key, out value)) return null;
            return value.ValueType == JsonValueType.String ? value.GetString() : null;
        }
    }
}
