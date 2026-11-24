package io.github.gohoski.notpipe.ui;

import android.content.Context;

import io.github.gohoski.notpipe.R;

/**
 * Created on 13.11.2026.
 * Превращает технические ошибки сети и плеера в понятные пользователю сообщения.
 */
public final class ErrorMessages {
    private ErrorMessages() {
    }

    /**
     * Сообщение для ошибки воспроизведения/получения потока.
     */
    public static String playback(Context context, String raw) {
        String text = raw == null ? "" : raw.toLowerCase();
        if (contains(text, "ssl") || contains(text, "handshake") || contains(text, "certificate")) {
            return context.getString(R.string.error_tls);
        }
        if (contains(text, "403") || contains(text, "401") || contains(text, "forbidden")
                || contains(text, "unauthorized") || contains(text, "sign in")) {
            return context.getString(R.string.error_instance_blocked);
        }
        if (contains(text, "404") || contains(text, "not found") || contains(text, "unavailable")) {
            return context.getString(R.string.error_video_unavailable);
        }
        if (contains(text, "timeout") || contains(text, "timed out")) {
            return context.getString(R.string.error_timeout);
        }
        if (contains(text, "unknownhost") || contains(text, "connect")
                || contains(text, "network") || contains(text, "unreachable")
                || contains(text, "refused")) {
            return context.getString(R.string.error_instance_unavailable);
        }
        return context.getString(R.string.error_playback_generic);
    }

    /**
     * Сообщение для ошибки скачивания.
     */
    public static String download(Context context, String raw) {
        String text = raw == null ? "" : raw.toLowerCase();
        if (contains(text, "no space") || contains(text, "enospc") || contains(text, "not enough space")) {
            return context.getString(R.string.error_no_space);
        }
        if (contains(text, "403") || contains(text, "401")) {
            return context.getString(R.string.error_instance_blocked);
        }
        return context.getString(R.string.error_download_failed);
    }

    private static boolean contains(String text, String value) {
        return text.indexOf(value) != -1;
    }
}
