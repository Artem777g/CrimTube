package io.github.gohoski.notpipe.util;

import android.content.Intent;
import android.net.Uri;

/**
 * Created on 04.11.2026.
 * Extracts a YouTube video ID from the intent that opened the video screen.
 * Kept separate from the activity so the parsing rules have a single place.
 */
public final class VideoIntentParser {
    private VideoIntentParser() {
    }

    /**
     * Returns the video ID from an "ID" extra, a youtube.com/watch link or a youtu.be link.
     *
     * @param intent Intent that started the activity
     * @return Video ID or null if it cannot be determined
     */
    public static String parse(Intent intent) {
        if (intent == null) return null;

        Uri uri = intent.getData();
        if (uri == null) {
            return emptyToNull(intent.getStringExtra("ID"));
        }

        String host = uri.getHost();
        String id;
        if (host != null && host.contains("youtube.com")) {
            id = uri.getQueryParameter("v");
        } else {
            id = uri.getLastPathSegment();
        }
        return emptyToNull(id);
    }

    private static String emptyToNull(String value) {
        return value == null || value.length() == 0 ? null : value;
    }
}
