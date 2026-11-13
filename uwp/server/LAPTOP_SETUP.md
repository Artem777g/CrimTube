# Перенос mux-сервера на старый ноутбук

Сервер склеивает видео и звук в один MP4 и отдаёт его телефону.
Транскодинга нет (`-c copy`), поэтому даже Core 2 Duo справится; всё упирается
в диск, сеть и скорость инстанса.

## 1. Что нужно

- **64-битная система**: Windows 10 x64 или Debian/Ubuntu x64.
  На Windows 7 **32-bit** сервер не запустится: современные сборки ffmpeg только x64.
- **Python 3.8+**.
- **ffmpeg** (Windows-сборка x64 или пакет `ffmpeg` в Linux).
- Ноутбук и телефон в **одной Wi-Fi/LAN-сети**.
- Свободное место: кэш 720/1080 может занимать гигабайты.

## 2. Скопировать файлы

Скопируй папку `CrimTube-UWP\server` на ноутбук, например в `C:\CrimTubeServer`.
В ней должны быть:

```
mux_server.py
start_mux_server.bat
```

## 3. Установить Python (Windows)

1. Скачай Python 3.11/3.12 x64 с python.org.
2. При установке включи **Add python.exe to PATH**.
3. Проверь в cmd: `python --version`.

## 4. Установить ffmpeg (Windows)

1. Скачай `ffmpeg-release-essentials.zip` с https://www.gyan.dev/ffmpeg/builds/
2. Распакуй в `C:\ffmpeg` так, чтобы был путь `C:\ffmpeg\bin\ffmpeg.exe`.
3. Проверь: `C:\ffmpeg\bin\ffmpeg.exe -version`.

Если положил в другое место — поправь строку `set "FFMPEG=..."` в `start_mux_server.bat`.

## 5. Запуск

Двойной клик по `start_mux_server.bat`.

В окне должно появиться:

```
CrimTube mux server on http://0.0.0.0:8765
```

Проверка с этого же ноутбука в браузере:

```
http://127.0.0.1:8765/mux/dQw4w9WgXcQ?q=360
```

Должен начаться MP4 (первый раз — с задержкой на склейку).

## 6. Открыть порт в firewall

В cmd от администратора:

```bat
netsh advfirewall firewall add rule name="CrimTube mux 8765" dir=in action=allow protocol=TCP localport=8765
```

## 7. Узнать IP ноутбука

```bat
ipconfig
```

Нужен IPv4 вида `192.168.31.xx` (адрес Wi-Fi/Ethernet).
Если адрес меняется — закрепи его в настройках роутера (DHCP reservation).

## 8. Настроить приложение

CrimTube → Настройки → **Mux-сервер**:

```
http://192.168.31.xx:8765
```

Сохранить. Затем открыть видео и выбрать 720p/1080p.

## 9. Автозапуск (Windows)

### Вариант А: Планировщик заданий

1. Открой «Планировщик заданий» → «Создать задачу».
2. Триггер: **При входе в систему**.
3. Действие: «Запуск программы»:
   - программа: `C:\CrimTubeServer\start_mux_server.bat`
   - рабочая папка: `C:\CrimTubeServer`
4. Поставь «Выполнять с наивысшими правами» при необходимости.

### Вариант Б: папка автозагрузки

Создай ярлык на `start_mux_server.bat` и положи в:

```
%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
```

## 10. Автозапуск (Linux, если поставишь Debian)

Установка:

```bash
sudo apt update
sudo apt install -y python3 ffmpeg
```

Проверка:

```bash
python3 mux_server.py --ffmpeg /usr/bin/ffmpeg --port 8765
```

Сервис systemd `/etc/systemd/system/crimtube-mux.service`:

```ini
[Unit]
Description=CrimTube mux server
After=network-online.target

[Service]
WorkingDirectory=/opt/crimtube-server
ExecStart=/usr/bin/python3 /opt/crimtube-server/mux_server.py --ffmpeg /usr/bin/ffmpeg --port 8765
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now crimtube-mux
```

## 11. Обслуживание кэша

Кэш лежит в `server/cache`. Удалять можно в любой момент, когда сервер остановлен:

```bat
rmdir /s /q C:\CrimTubeServer\cache
```

Или в Linux: `rm -rf /opt/crimtube-server/cache`.

## 12. Если не работает

- **502 на телефоне** — сервер не смог получить поток с инстанса. Смотри `mux_server.log`; попробуй другое видео.
- **Телефон не открывает адрес** — проверь один Wi-Fi, firewall, что сервер запущен, и IP не сменился.
- **`ffmpeg not found`** — неверный путь в bat или ffmpeg не установлен.
- **`python not found`** — Python не в PATH.
- **Долго первый запуск** — нормально: сервер скачивает и склеивает файл. Повторный — из кэша.
- **Перемотка не работает во время первой склейки** — поток ещё не докачан; после завершения файл кэшируется и Range работает.
