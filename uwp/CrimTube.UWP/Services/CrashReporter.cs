using System;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using Windows.ApplicationModel;
using Windows.Storage;
using Windows.UI.Xaml;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Сохраняет отчёт о необработанном исключении и отправляет его на сервер.
    /// </summary>
    public static class CrashReporter
    {
        public static void Install(Application app)
        {
            app.UnhandledException += OnUnhandledException;
        }

        private static void OnUnhandledException(object sender, UnhandledExceptionEventArgs e)
        {
            var version = Package.Current.Id.Version;
            var report = "CrimTube UWP crash report\n"
                         + "time: " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "\n"
                         + "version: " + version.Major + "." + version.Minor + "." + version.Build + "." + version.Revision + "\n"
                         + "device: " + (string)Windows.System.Profile.AnalyticsInfo.VersionInfo.DeviceFamily + "\n\n"
                         + (e.Exception == null ? "unknown" : e.Exception.ToString());

            try
            {
                Task.Run(async () =>
                {
                    var file = await ApplicationData.Current.LocalFolder.CreateFileAsync(
                        "last_crash.txt", CreationCollisionOption.ReplaceExisting);
                    await FileIO.WriteTextAsync(file, report);
                    await Send(report);
                }).Wait(3000);
            }
            catch
            {
            }
        }

        private static async Task Send(string report)
        {
            var url = AppSettings.ReportUrl;
            if (string.IsNullOrEmpty(url)) return;

            using (var http = new HttpClient())
            {
                http.Timeout = TimeSpan.FromSeconds(5);
                var content = new StringContent(report, Encoding.UTF8, "text/plain");
                await http.PostAsync(url, content);
            }
        }
    }
}
