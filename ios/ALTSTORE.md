# CrimTube для iOS (AltStore)

Приложение для iPhone 7 и новее (iOS 15+). Собирается в облаке GitHub Actions,
ставится через AltStore с бесплатным Apple ID. App Store не требуется.

## 1. Собрать IPA

1. Запушь репозиторий на GitHub.
2. GitHub → **Actions** → **iOS IPA** → **Run workflow**.
3. Дождись зелёной галочки.
4. Скачай артефакт **CrimTube-ipa** → внутри `CrimTube.ipa`.

Сборка использует macOS-раннер GitHub, Mac не нужен.

## 2. Установить AltServer (Windows)

1. Скачай [AltServer](https://altstore.io/) для Windows.
2. Установи iTunes и iCloud (версии с сайта Apple, не из Microsoft Store).
3. Запусти AltServer, подключи iPhone кабелем, разблокируй и нажми «Доверять».
4. Иконка AltServer в трее → **Install AltStore** → выбери iPhone → введи Apple ID.

## 3. Установить CrimTube

1. На iPhone открой **AltStore**.
2. **My Apps** → **+** → выбери `CrimTube.ipa`.
3. Введи Apple ID (тот же, что для AltStore).
4. Приложение появится на домашнем экране.

Если iOS попросит доверять разработчику:
**Настройки → Основные → VPN и управление устройством → Доверять**.

## 4. Обновление подписи

Бесплатный Apple ID подписывает приложение на **7 дней**.

- Держи AltServer запущенным на ПК в той же Wi-Fi;
- AltStore → **My Apps** → **Refresh** (или включи Background Refresh);
- иначе приложение перестанет запускаться.

## 5. Настройка

При первом запуске открой **Настройки** и укажи рабочий инстанс Invidious,
например `https://inv.nadeko.net`. Если видео не открывается — смени инстанс.

## Ограничения

- Нет App Store и TestFlight — только AltStore.
- 7-дневная подпись, максимум 3 приложения на бесплатном Apple ID.
- Загрузки идут, пока приложение открыто; фоновая загрузка iOS ограничена.
- Если изменить bundle ID, IPA нужно собрать заново.

## Если сборка упала

- Проверь лог в Actions.
- Убедись, что `ios/project.yml` и `ios/Sources` на месте.
- XcodeGen ставится автоматически (`brew install xcodegen`).
