using System;
using Windows.ApplicationModel;
using Windows.ApplicationModel.Activation;
using Windows.UI.Core;
using Windows.UI.Xaml;
using Windows.UI.Xaml.Controls;
using Windows.UI.Xaml.Navigation;

namespace CrimTube.UWP
{
    /// <summary>
    /// Точка входа приложения CrimTube для Windows 10 Mobile.
    /// </summary>
    public sealed partial class App : Application
    {
        private Frame _rootFrame;

        public App()
        {
            InitializeComponent();
            CrimTube.UWP.Services.CrashReporter.Install(this);
            Suspending += OnSuspending;
        }

        protected override void OnLaunched(LaunchActivatedEventArgs e)
        {
            _rootFrame = Window.Current.Content as Frame;

            if (_rootFrame == null)
            {
                _rootFrame = new Frame();
                _rootFrame.NavigationFailed += OnNavigationFailed;
                Window.Current.Content = _rootFrame;
            }

            if (e.PrelaunchActivated) return;

            if (_rootFrame.Content == null)
            {
                _rootFrame.Navigate(typeof(MainPage), e.Arguments);
            }

            // Аппаратная кнопка «Назад» должна возвращать на предыдущий экран,
            // а не закрывать приложение с любого места.
            SystemNavigationManager.GetForCurrentView().BackRequested -= OnBackRequested;
            SystemNavigationManager.GetForCurrentView().BackRequested += OnBackRequested;

            Window.Current.Activate();
        }

        private void OnBackRequested(object sender, BackRequestedEventArgs e)
        {
            if (_rootFrame != null && _rootFrame.CanGoBack)
            {
                e.Handled = true;
                _rootFrame.GoBack();
            }
        }

        private void OnNavigationFailed(object sender, NavigationFailedEventArgs e)
        {
            throw new Exception("Не удалось открыть страницу " + e.SourcePageType.FullName);
        }

        private void OnSuspending(object sender, SuspendingEventArgs e)
        {
            // Сохранять пока нечего: настройки пишутся сразу при изменении.
        }
    }
}
