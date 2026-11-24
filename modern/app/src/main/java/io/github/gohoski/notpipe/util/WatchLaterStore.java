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
 * Local "Watch later" list. Stores just enough video metadata to show the list and open
 * the video again without any account.
 */
public final class WatchLaterStore {
    private static final String PREFS = "notPipe_watch_later";
    private static final String KEY = "items";

    private WatchLaterStore() {
    }

    /**
     * Returns saved videos, newest first.
     *
     * @param context Any context
     * @return Mutable list of videos
     */
    public static List<VideoInfo> list(Context context) {
        return VideoListCodec.decode(prefs(context).getString(KEY, ""));
    }

    /**
     * Tells whether the video is already saved.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     * @return true if present
     */
    public static boolean contains(Context context, String videoId) {
        if (videoId == null) return false;
        List<VideoInfo> videos = list(context);
        for (int i = 0; i < videos.size(); i++) {
            if (videoId.equals(videos.get(i).id)) return true;
        }
        return false;
    }

    /**
     * Adds a video to the top of the list.
     *
     * @param context Any context
     * @param video Video to save
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
        save(context, videos);
    }

    /**
     * Removes a video from the list.
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
     * Removes everything from the list.
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
