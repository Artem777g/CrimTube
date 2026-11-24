package io.github.gohoski.notpipe.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created on 08.11.2026.
 * Case-insensitive title filtering used by the in-channel search.
 */
public final class VideoSearch {
    private VideoSearch() {
    }

    /**
     * Filters videos whose title contains the query.
     *
     * @param source Videos to filter
     * @param query Search query, may be empty
     * @return Filtered list; the source list itself when the query is empty
     */
    public static List<VideoInfo> filter(List<VideoInfo> source, String query) {
        if (source == null) return new ArrayList<VideoInfo>();
        if (query == null || query.length() == 0) return source;

        String needle = query.toLowerCase(Locale.getDefault());
        List<VideoInfo> result = new ArrayList<VideoInfo>();
        for (int i = 0; i < source.size(); i++) {
            VideoInfo video = source.get(i);
            if (video.title != null
                    && video.title.toLowerCase(Locale.getDefault()).contains(needle)) {
                result.add(video);
            }
        }
        return result;
    }
}
