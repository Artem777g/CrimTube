@echo off
setlocal
cd /d "%~dp0"

rem Путь к ffmpeg. Если ffmpeg уже в PATH, можно оставить "ffmpeg".
set "FFMPEG=C:\ffmpeg\bin\ffmpeg.exe"
if not exist "%FFMPEG%" set "FFMPEG=ffmpeg"

where python >nul 2>&1
if errorlevel 1 (
    echo Python не найден. Установите Python 3 и добавьте его в PATH.
    pause
    exit /b 1
)

echo Запуск CrimTube mux server на порту 8765...
echo Лог пишется в mux_server.log
python mux_server.py --ffmpeg "%FFMPEG%" --port 8765 >> mux_server.log 2>&1
pause
