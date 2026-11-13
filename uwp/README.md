# CrimTube для Windows 10 Mobile (UWP-прототип)

Отдельное приложение на C#/XAML. Код Android-версии не переносился: общими остались
только идеи и правила (какой поток брать, как показывать длительность и просмотры).

## Что уже умеет

- «Популярное» и поиск через **Invidious** (`?local=true`) или **Piped** — с перебором
  инстансов, если один не отвечает.
- Экран видео: заголовок, автор, просмотры, длительность, описание.
- Воспроизведение совмещённого MP4-потока (360p) через `MediaPlayerElement`.
  Invidious отдаёт поток через сам инстанс, поэтому блокировка `googlevideo` у
  провайдера не мешает.
- Список похожих видео и переход по ним.
- Настройки: тип инстанса (Invidious/Piped), адрес, проверка связи, готовые адреса.

Чего пока нет: подписок, «Смотреть позже», истории, каналов, комментариев, загрузок,
выбора качества, продолжения просмотра с места остановки.

## Требования к сборке

- Visual Studio 2019 Build Tools с нагрузкой **Universal Windows Platform Build Tools**
  (`Microsoft.VisualStudio.Workload.UniversalBuildTools`); она же ставит Windows 10 SDK.
- NuGet-пакет `Microsoft.NETCore.UniversalWindowsPlatform` восстанавливается при сборке.

## Сборка

```powershell
.\build-uwp.ps1                       # Debug|ARM
.\build-uwp.ps1 -Configuration Release
```

Или напрямую:

```powershell
& 'C:\BuildTools2019\MSBuild\Current\Bin\MSBuild.exe' CrimTube.UWP.sln /restore `
    /p:Configuration=Release /p:Platform=ARM /p:AppxBundle=Never
```

Готовый пакет: `CrimTube.UWP\AppPackages\CrimTube.UWP_0.3.5.0_ARM_<Configuration>_Test\*.appx`.

## Готовый пакет для телефона

`dist\` — то, что нужно ставить на W10M:

| Файл | Что это |
|---|---|
| `CrimTube.UWP_0.1.0.0_ARM.appx` | приложение, Release, ARM, .NET Native (673 698 байт, SHA-256 `B93AB72FDBD7DF9D6E025211AF23B1B3D9CBBFE1D65612B6FA4063A5F94893DA`) |
| `Dependencies\arm\*.appx` | framework-пакеты .NET Native 1.7 + VCLibs 14.0 (нужны один раз) |
| `CrimTube.UWP.cer` | сертификат подписи, ставится в «Доверенные люди» |
| `README.txt` | пошаговая установка и коды ошибок |

Release-сборка компилируется .NET Native (`UseDotNetNativeToolchain=true`), поэтому
на телефоне не нужен пакет `Microsoft.NET.CoreRuntime`; Debug-сборка без .NET Native
зависит от него.

## Установка на телефон

1. На телефоне: **Параметры → Обновление и безопасность → Для разработчиков →
   Режим разработчика** (и, если нужно, Device Portal).
2. Поставить сертификат `CrimTube.UWP.cer` в хранилище «Доверенные издатели»
   (файл можно скинуть на телефон или открыть через Device Portal).
3. Установить `.appx`:
   - через Device Portal (`http://<ip-телефона>:80`) в разделе Apps, или
   - через `WinAppDeployCmd.exe install -file <путь>.appx -ip <ip>`, или
   - скопировать `.appx` на телефон и запустить (если разрешена установка из файла).
4. Пароль временного ключа подписи: `crimtube` (файл `CrimTube.UWP_TemporaryKey.pfx`).

## Известные ограничения Windows 10 Mobile

- Платформа снята с поддержки, Store закрыт — только сайдлоад.
- Телефон может не доверять современным корневым сертификатам (Let's Encrypt).
  Если HTTPS-инстанс не открывается, попробуйте HTTP-инстанс или поставьте
  корневой сертификат на телефон.
- XAML на старых Lumia работает небыстро: интерфейс намеренно простой.
- Сборка под ARM; x86/x64 — только для проверки на ПК через эмулятор.

## Структура

```
CrimTube.UWP/
  App.xaml(.cs)              — точка входа, общий шаблон списка видео
  MainPage.xaml(.cs)         — популярное и поиск
  VideoPage.xaml(.cs)        — плеер, описание, похожие
  SettingsPage.xaml(.cs)     — инстанс Invidious
  Models/                    — VideoItem, VideoDetails, StreamOption
  Services/InvidiousClient   — HTTP + разбор JSON (Windows.Data.Json)
  Services/AppSettings       — адрес инстанса в LocalSettings
  Util/Format.cs             — длительность, просмотры, «3 дня назад»
```
