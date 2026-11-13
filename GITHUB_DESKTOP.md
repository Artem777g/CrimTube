# Публикация CrimTube через GitHub Desktop

Как с часами: репозиторий без команд, готовые файлы прикладываются к релизу.

## 1. Добавить папку в GitHub Desktop

1. Установи GitHub Desktop и войди в аккаунт.
2. **File → Add local repository** → выбери папку `CrimTube`.
3. Если GitHub Desktop скажет, что это не репозиторий — согласись создать.
4. Сделай коммит (например, «CrimTube 0.4.7: Android + Windows Mobile»).
5. **Publish repository**, сними галочку **Keep this code private**.

`dist/` исключён в `.gitignore`, поэтому APK/APPX в репозиторий не попадут —
их прикладываем к релизу.

## 2. Создать релиз

1. На GitHub: **Releases → Draft a new release**.
2. Tag: `v0.4.7`, title: `CrimTube 0.4.7`.
3. Описание возьми из `RELEASE_NOTES.md`.
4. Прикрепи файлы из папки `dist`:
   - `CrimTube-0.3.1.apk`
   - `CrimTube.UWP_0.4.7.0_ARM.appx`
   - `CrimTube.UWP-dependencies-ARM.zip`
   - `CrimTube.UWP.cer`
   - `SHA256SUMS.txt`
   - `README.txt`
5. **Publish release**.

### Через GitHub CLI (если установлен)

```powershell
gh release create v0.4.7 `
  dist\CrimTube-0.3.1.apk `
  dist\CrimTube.UWP_0.4.7.0_ARM.appx `
  dist\CrimTube.UWP-dependencies-ARM.zip `
  dist\CrimTube.UWP.cer `
  dist\SHA256SUMS.txt `
  dist\README.txt `
  --title "CrimTube 0.4.7" `
  --notes-file RELEASE_NOTES.md
```

## 3. Включить автообновление

После публикации релиза подставь свой `USER/REPO` в `update/latest.json`:

```json
"url": "https://github.com/USER/REPO/releases/download/v0.4.7/CrimTube-0.3.1.apk"
```

и для APPX аналогично. Закоммить файл.

URL для приложений:

```
https://raw.githubusercontent.com/USER/REPO/main/update/latest.json
```

или быстрее через jsDelivr:

```
https://cdn.jsdelivr.net/gh/USER/REPO@main/update/latest.json
```

- Android: меню → «Обновления» → вставить URL → «Проверить».
- UWP: Настройки → «Обновления» → вставить URL → «Проверить обновления».

## 4. Следующие версии

1. Увеличь `versionCode` в `android/app/build.gradle`.
2. Увеличь `Version` в `uwp/CrimTube.UWP/Package.appxmanifest`.
3. Собери APK/APPX, подпиши тем же ключом/сертификатом.
4. Новый тег `v0.4.8`, приложи новые файлы, обнови `update/latest.json`.

Ссылка `[Releases](../../releases)` в README заработает сразу после первого релиза.
