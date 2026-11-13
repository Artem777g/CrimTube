package io.github.gohoski.notpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

/**
 * Created on 08.11.2026.
 * Remembers where the user stopped watching each video so it can be resumed later.
 * The JSON part is free of Android APIs and covered by unit tests.
 */
public final class PlaybackPositionsStore {
    private static final String PREFS = "notPipe_playback_positions";
    private static final String KEY = "positions";
    private static final int MAX_ENTRIES = 200;

    /** Positions shorter than this are not worth resuming. */
    public static final int MIN_RESUME_MS = 30000;
    /** Do not resume when the video is this close to the end. */
    public static final int END_MARGIN_MS = 60000;

    private PlaybackPositionsStore() {
    }

    /**
     * One saved playback position.
     */
    public static class Entry {
        public final String id;
        public final int position;
        public final int duration;
        public final long updated;

        public Entry(String id, int position, int duration, long updated) {
            this.id = id;
            this.position = position;
            this.duration = duration;
            this.updated = updated;
        }
    }

    /**
     * Serializes positions to JSON.
     *
     * @param entries Positions to save
     * @return JSON array string
     */
    public static String encode(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            JSONObject item = new JSONObject();
            item.put("id", entry.id);
            item.put("position", entry.position);
            item.put("duration", entry.duration);
            item.put("updated", entry.updated);
            array.add(item);
        }
        return array.build();
    }

    /**
     * Parses positions from JSON, skipping entries without an ID.
     *
     * @param raw JSON string, may be empty
     * @return Parsed positions
     */
    public static List<Entry> decode(String raw) {
        List<Entry> result = new ArrayList<Entry>();
        if (raw == null || raw.length() == 0) return result;

        try {
            JSONArray array = JSON.getArray(raw);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getObject(i);
                String id = item.getString("id", "");
                if (id.length() == 0) continue;
                result.add(new Entry(id, item.getInt("position", 0),
                        item.getInt("duration", -1), item.getLong("updated", 0)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Tells whether the saved position is worth resuming.
     *
     * @param entry Saved entry
     * @param durationMs Video duration in milliseconds, -1 if unknown
     * @return true when playback should continue from the saved position
     */
    public static boolean shouldResume(Entry entry, int durationMs) {
        if (entry == null || entry.position < MIN_RESUME_MS) return false;
        return durationMs <= 0 || entry.position <= durationMs - END_MARGIN_MS;
    }

    /**
     * Returns the saved position of a video or null.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     * @return Saved entry or null
     */
    public static Entry get(Context context, String videoId) {
        if (videoId == null) return null;
        List<Entry> entries = all(context);
        for (int i = 0; i < entries.size(); i++) {
            if (videoId.equals(entries.get(i).id)) return entries.get(i);
        }
        return null;
    }

    /**
     * Saves the position, replacing any previous value for the same video.
     * Positions that are too early or too close to the end remove the entry instead.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     * @param positionMs Current position in milliseconds
     * @param durationMs Video duration in milliseconds, -1 if unknown
     */
    public static void save(Context context, String videoId, int positionMs, int durationMs) {
        if (videoId == null) return;

        List<Entry> entries = all(context);
        removeFrom(entries, videoId);

        if (positionMs >= MIN_RESUME_MS
                && (durationMs <= 0 || positionMs <= durationMs - END_MARGIN_MS)) {
            entries.add(0, new Entry(videoId, positionMs, durationMs, System.currentTimeMillis()));
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
        }
        persist(context, entries);
    }

    /**
     * Forgets the saved position of a video.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     */
    public static void remove(Context context, String videoId) {
        if (videoId == null) return;
        List<Entry> entries = all(context);
        removeFrom(entries, videoId);
        persist(context, entries);
    }

    private static List<Entry> all(Context context) {
        return decode(prefs(context).getString(KEY, ""));
    }

    private static void persist(Context context, List<Entry> entries) {
        prefs(context).edit().putString(KEY, encode(entries)).commit();
    }

    private static void removeFrom(List<Entry> entries, String videoId) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (videoId.equals(entries.get(i).id)) entries.remove(i);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
