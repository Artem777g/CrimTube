using System;

namespace CrimTube.UWP.Services
{
    /// <summary>
    /// Понятные пользователю сообщения вместо сырых исключений.
    /// </summary>
    public static class ErrorText
    {
        public static string Open(string raw)
        {
            return "Не удалось открыть видео. " + Playback(raw) +
                   "\n\nПубличные инстансы часто блокируются YouTube. Смените инстанс в настройках.";
        }

        public static string Playback(string raw)
        {
            var text = (raw ?? string.Empty).ToLowerInvariant();

            if (text.Contains("ssl") || text.Contains("handshake") || text.Contains("certificate"))
                return "Ошибка защищённого соединения. Попробуйте другой инстанс.";
            if (text.Contains("403") || text.Contains("401") || text.Contains("unauthorized"))
                return "YouTube блокирует этот инстанс. Выберите другой.";
            if (text.Contains("404") || text.Contains("not found"))
                return "Видео или поток не найден.";
            if (text.Contains("timeout") || text.Contains("timed out"))
                return "Инстанс не ответил вовремя. Попробуйте другой.";
            if (text.Contains("connect") || text.Contains("network") || text.Contains("unreachable"))
                return "Инстанс недоступен. Проверьте подключение или выберите другой.";

            return "Не удалось воспроизвести. Смените качество или инстанс.";
        }
    }
}
