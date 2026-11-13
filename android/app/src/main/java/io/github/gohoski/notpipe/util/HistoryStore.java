package io.github.gohoski.notpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created on 04.11.2026.
 * Local watch history. Videos are recorded when their page is opened, the newest entry
 * always goes first. Nothing leaves the device.
 */
public final class HistoryStore {
    private static final String PREFS = "notPipe_history";
    private static final String KEY = "items";
    /** Older entries are dropped once the limit is reached. */
    private static final int MAX_ITEMS = 200;

    private HistoryStore() {
    }

    /**
     * Returns watched videos, newest first.
     *
     * @param context Any context
     * @return Mutable list of videos
     */
    public static List<VideoInfo> list(Context context) {
        return VideoListCodec.decode(prefs(context).getString(KEY, ""));
    }

    /**
     * Records a video as watched. An existing entry is moved to the top.
     *
     * @param context Any context
     * @param video Video that was opened
     */
    public static void add(Context context, VideoInfo video) {
        if (video == null || video.id == null) return;

        List<VideoInfo> videos = list(context);
        for (int i = 0; i < videos.size(); i++) {
            if (video.id.equals(videos.get(i).id)) {
                videos.remove(i);
                break;
            }
        }
        videos.add(0, video);

        while (videos.size() > MAX_ITEMS) {
            videos.remove(videos.size() - 1);
        }
        save(context, videos);
    }

    /**
     * Removes one video from the history.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     */
    public static void remove(Context context, String videoId) {
        List<VideoInfo> videos = list(context);
        for (int i = 0; i < videos.size(); i++) {
            if (videoId.equals(videos.get(i).id)) {
                videos.remove(i);
                save(context, videos);
                return;
            }
        }
    }

    /**
     * Removes the whole history.
     *
     * @param context Any context
     */
    public static void clear(Context context) {
        prefs(context).edit().remove(KEY).commit();
    }

    private static void save(Context context, List<VideoInfo> videos) {
        prefs(context).edit().putString(KEY, VideoListCodec.encode(videos)).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
