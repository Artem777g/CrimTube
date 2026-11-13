using System;
using System.Globalization;

namespace CrimTube.UWP.Util
{
    /// <summary>
    /// Форматирование длительности и счётчиков в привычном для YouTube виде.
    /// </summary>
    public static class Format
    {
        /// <summary>Длительность в виде 12:34 или 1:02:03.</summary>
        public static string Duration(int seconds)
        {
            if (seconds <= 0) return string.Empty;

            var span = TimeSpan.FromSeconds(seconds);
            return span.TotalHours >= 1
                ? string.Format(CultureInfo.InvariantCulture, "{0}:{1:00}:{2:00}", (int)span.TotalHours, span.Minutes, span.Seconds)
                : string.Format(CultureInfo.InvariantCulture, "{0}:{1:00}", span.Minutes, span.Seconds);
        }

        /// <summary>Короткая запись числа: 1,2 тыс., 3,4 млн.</summary>
        public static string Compact(long value)
        {
            if (value < 0) return string.Empty;
            if (value < 1000) return value.ToString(CultureInfo.InvariantCulture);
            if (value < 1000000) return OneDecimal(value / 1000.0) + " тыс.";
            if (value < 1000000000) return OneDecimal(value / 1000000.0) + " млн";
            return OneDecimal(value / 1000000000.0) + " млрд";
        }

        /// <summary>Счётчик просмотров с правильным русским окончанием.</summary>
        public static string Views(long value)
        {
            if (value < 0) return string.Empty;
            if (value < 1000) return value.ToString(CultureInfo.InvariantCulture) + " " + Plural(value, "просмотр", "просмотра", "просмотров");
            return Compact(value) + " просмотров";
        }

        /// <summary>Выбор русского окончания по числу.</summary>
        public static string Plural(long value, string one, string few, string many)
        {
            var mod100 = Math.Abs(value) % 100;
            var mod10 = Math.Abs(value) % 10;

            if (mod100 >= 11 && mod100 <= 14) return many;
            if (mod10 == 1) return one;
            if (mod10 >= 2 && mod10 <= 4) return few;
            return many;
        }

        /// <summary>«3 дня назад» для дат публикации.</summary>
        public static string TimeAgo(DateTimeOffset published)
        {
            var delta = DateTimeOffset.Now - published;
            if (delta.TotalMinutes < 1) return "только что";
            if (delta.TotalMinutes < 60) return Ago((long)delta.TotalMinutes, "минуту", "минуты", "минут");
            if (delta.TotalHours < 24) return Ago((long)delta.TotalHours, "час", "часа", "часов");
            if (delta.TotalDays < 7) return Ago((long)delta.TotalDays, "день", "дня", "дней");
            if (delta.TotalDays < 30) return Ago((long)(delta.TotalDays / 7), "неделю", "недели", "недель");
            if (delta.TotalDays < 365) return Ago((long)(delta.TotalDays / 30), "месяц", "месяца", "месяцев");
            return Ago((long)(delta.TotalDays / 365), "год", "года", "лет");
        }

        private static string Ago(long value, string one, string few, string many)
        {
            return value + " " + Plural(value, one, few, many) + " назад";
        }

        private static string OneDecimal(double value)
        {
            return value.ToString("0.#", CultureInfo.InvariantCulture).Replace('.', ',');
        }
    }
}
