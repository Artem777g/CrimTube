# Промт для другого ИИ: проект CrimTube

Ты продолжаешь разработку **CrimTube** — YouTube-клиента для старых Android,
Android TV, Windows 10 Mobile и iOS (AltStore). Пользователь — Артём,
общается по-русски, хочет готовые файлы (APK/APPX/IPA), а не советы.
Отвечай кратко и по делу, сначала делай, потом объясняй.

## 1. Главное

- **Android package:** `io.github.gohoski.notpipe` (менять нельзя).
- **Отображаемое имя:** CrimTube; версия на экране — `0.3.1`.
- **versionCode:** сейчас **40** (увеличивать при каждой сборке).
- **UWP:** `CrimTube.UWP`, версия **0.5.0.0**, ARM, Windows 10 Mobile.
- **iOS:** SwiftUI, версия **0.1.7**, iOS 15+, только AltStore/сайдлоад.
- **Сервер:** Python + ffmpeg, умеет mux, обновления, отчёты и русскую озвучку.
- **GitHub:** `https://github.com/Artem777g/CrimTube` (публичный).

Не делай PR в upstream `gohoski/notPipe` — это не репозиторий пользователя.

## 2. Где что лежит

```
C:\Users\Artem\OneDrive\Документы\deepseek-harness\default-workspace\
  notPipe-main\              исходники Android
  CrimTube-UWP\              исходники UWP + сервер
  CrimTube\                  единый релизный репозиторий:
    android\                 копия Android
    uwp\                     копия UWP + uwp\server
    ios\                     iOS-проект (XcodeGen)
    docs\                    иконки
    dist\                    готовые файлы (gitignored)
    update\latest.json       манифест автообновления
  CrimTube-0.3.1-debug.apk   текущий Android APK
  CrimTube-TV-0.3.1.apk      тот же APK для TV
  notpipe-debug.keystore     ключ подписи
```

Git-репозиторий — только `CrimTube`; исходники Android/UWP синхронизируются
копированием. Всегда проверяй `git -C CrimTube status` и не перезаписывай
чужие правки.

## 3. Что уже реализовано

### Android

- Популярное, поиск, каналы, подписки, «Смотреть позже», история,
  продолжение просмотра, поиск по каналу.
- Загрузки видео/аудио: очередь, `.part`, повторные попытки, сортировка,
  фильтр, удаление, очистка битых файлов.
- Экран «Инстансы»: пинг, ошибки, выбор, обновление списка.
- Автообновление через `latest.json`; для Android 4.2 — HTTP-манифест
  (`/update/android.json`), потому что GitHub TLS не поддерживается.
- **TV-режим:** `TvMainActivity` + `LEANBACK_LAUNCHER`, баннер,
  сетка 4 колонки, D-pad-фокус, TV-поиск с экранной клавиатурой,
  кнопки «Помощь»/«Настройки», управление плеером с пульта.
- Настройки переупорядочены: в главном меню только подписки/позже/история/
  загрузки/настройки; «Инстансы», «Обновления», «Как пользоваться»,
  «О приложении» — внутри настроек.
- Перехват падений: `CrashHandler` → `CrashActivity`; отчёт можно отправить
  на сервер `POST /report` или почтой.
- Новый значок CrimTube применён.

### UWP

- Invidious/Piped-клиент, перебор инстансов, пинг, качество.
- Mux-сервер для 720p/1080p.
- Проверка обновлений, скачивание APPX и запуск установщика.
- «Как пользоваться» и поле «Отчёты об ошибках» в настройках.
- `CrashReporter` сохраняет отчёт и отправляет на сервер.
- Версия 0.5.0 собрана, но **на телефон не установлена** (телефон был offline).

### iOS (AltStore)

- SwiftUI: популярное, поиск (видео/каналы), канал, подписки,
  «Смотреть позже», история, продолжение, загрузки видео/аудио,
  комментарии, скорость, полный экран.
- Лента подписок, пинг инстансов, mux-сервер, качество 480/720/1080.
- Озвучка: кнопка «Озвучить на русском», сервер собирает MP4.
- Иконка обновлена; IPA нужно пересобрать.

### Сервер

`uwp/server/mux_server.py` + `dubbing.py`:

- `/mux/{id}?q=720` — склеенный MP4 (ждёт полной сборки);
- `/live/{id}?q=720` — потоковый fragmented MP4;
- `/update/latest.json`, `/update/android.json`, `/update/*.apk`;
- `POST /report` — отчёты в `server/reports/`;
- `/dub/prepare/{id}`, `/dub/status/{id}`, `/dub/{id}` — озвучка.

Пайплайн озвучки:

1. Invidious captions; если YouTube отдаёт «Sorry» — Piped TTML.
2. Google Translate (`translate.googleapis.com`).
3. Google TTS (`translate.google.com/translate_tts`).
4. ffmpeg `adelay`/`amix`, mux с видео.
5. Кэш `server/cache/dub`.

Запуск:

```powershell
cd uwp\server
start_mux_server.bat
```

Внутри bat путь `C:\ffmpeg\bin\ffmpeg.exe`; локально использовался
`CrimTube\server\bin\ffmpeg.exe` (бинарник не коммитится).

## 4. Сборка

### Android

JDK 8, Android SDK 25, build-tools 25.0.0, Gradle 3.3, AGP 2.3.2.
Java только 1.5-совместимый синтаксис. Проект **не любит путь с кириллицей**:
собирать в ASCII-каталоге, например `%TEMP%\androidbuild6`.

```powershell
gradle assembleDebug -g <gradle-home>
```

Подпись:

```powershell
java -jar apksigner.jar sign --ks notpipe-debug.keystore `
  --ks-pass pass:android --key-pass pass:android `
  --ks-key-alias androiddebugkey --out CrimTube-0.3.1-debug.apk <input.apk>
```

Разрешения Android 8+: неизвестные источники.

### UWP

MSBuild 2019 BuildTools, ARM, сертификат `CN=CrimTube`
thumbprint `A0B6AE0F4B74895A19A1C348AB9D5E1B85F2E6CA`.

```powershell
MSBuild CrimTube.UWP.sln /restore /t:Build /p:Configuration=Release `
  /p:Platform=ARM /p:AppxBundle=Never /p:AppxPackageSigningEnabled=true `
  /p:PackageCertificateKeyFile="" /p:PackageCertificatePassword="" `
  /p:PackageCertificateThumbprint=A0B6AE0F4B74895A19A1C348AB9D5E1B85F2E6CA
```

Установка на HP Elite x3 — Device Portal `https://192.168.31.36`
(без auth), multipart `file`, `package` в query. Старый пакет удалять,
если сработал лимит sideload-пакетов.

### iOS

XcodeGen + GitHub Actions (`ios.yml`) → unsigned IPA. Mac нет.
`macos-14` ARM не поддерживается, `macos-13` снят; сейчас
`macos-15-intel`. Fallback — `codemagic.yaml` на codemagic.io.

## 5. GitHub и обновления

- Релиз `v0.4.7` содержит старые APK 0.3.1 (versionCode 31) и APPX 0.4.7.
- `update/latest.json` уже с ссылками `Artem777g/CrimTube`.
- Android 4.2 не умеет TLS 1.2/SNI GitHub → использует HTTP:
  `http://<server>:8765/update/android.json` и APK с сервера.
- UWP/iOS используют GitHub `latest.json`.
- Пуш из CLI не проходит (нет credentials); пользователь жмёт
  **Push origin** в GitHub Desktop.

## 6. Устройства и проверка

- Планшет 1: `JBQ4DINFIRQG7TFE`, IdeaTab S6000-H, Android 4.2.2,
  1280×800. APK 40 установлен.
- Планшет 2: serial `B10K2312FR01231`, ADB `unauthorized`,
  окно «Разрешить отладку» не появляется — USB-установка вручную.
- HP Elite x3: Windows 10 Mobile 15254.603, `192.168.31.36`.
- Проектор F30: Android 12, 1 ГБ RAM, китайская прошивка; APK ставится
  с флешки через «Медиацентр». Режим разработчика вырезан.
- iPhone 7, iOS 15.8, AltStore, бесплатная подпись 7 дней.

## 7. Текущие проблемы / ограничения

- UWP 0.5.0 не установлена: телефон offline.
- iOS 0.1.7 не собран в IPA; нужен успешный CI/Codemagic.
- Android TV: второстепенные экраны и плеер — phone-layout; нужен
  отдельный TV-плеер с крупными контролами.
- Озвучка требует субтитров; если их нет — ошибка. Google Translate/TTS
  могут блокироваться.
- Android 4.2: GitHub-обновления только через HTTP-сервер.
- 4PDA-пост был подготовлен, но раздел разработки не подходит;
  нужен раздел «Android — Программы» и отдельная тема W10M.
- В iOS не добавлены отчёты об ошибках и экран помощи.

## 8. Что делать дальше (по приоритету)

1. **Push origin** всех коммитов; проверить GitHub Actions/Codemagic.
2. Установить UWP 0.5.0, когда телефон онлайн; проверить help/отчёты.
3. Пересобрать IPA 0.1.7 и установить через AltStore.
4. Проверить crash-отчёт на Android (`adb shell am crash ...`),
   убедиться, что `POST /report` пишет файл.
5. Сделать TV-плеер: полноэкранный `VideoView`, крупные кнопки,
   play/pause/seek/quality, фокус.
6. Довести паритет iOS: плейлисты, очередь/автопереход, жесты,
   очередь загрузок с отменой.
7. Выпустить новый релиз `v0.5.0` с APK 40 / APPX 0.5.0 / IPA 0.1.7,
   обновить `latest.json` и `dist`.
8. Подготовить корректный пост на 4PDA.

## 9. Стиль работы

- Русский язык, кратко, без воды.
- Сначала проверь файлы, потом меняй; не перезаписывай чужие правки.
- Для кириллицы — UTF-8 (`write`), не PowerShell `Set-Content`.
- Сборки и установку доводи до конца, если устройство доступно.
- Пользователь ценит работающие файлы и честные ограничения.
- Секреты (keystore, p12) не коммитить; `dist`/`cache`/`ffmpeg` в `.gitignore`.

## 10. Первый шаг

Прочитай `CrimTube/README.md`, `CrimTube/GITHUB_DESKTOP.md`,
`CrimTube/ios/ALTSTORE.md`, `CrimTube-UWP/GITHUB_UPDATES.md`.
Затем `git -C CrimTube status` и `git -C CrimTube log --oneline -10`.
Спроси пользователя, что делать первым: **UWP 0.5.0**, **iOS IPA**
или **TV-плеер**.
