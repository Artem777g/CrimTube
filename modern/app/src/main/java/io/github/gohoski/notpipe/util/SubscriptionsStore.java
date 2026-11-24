package io.github.gohoski.notpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

/**
 * Created on 29.10.2026.
 * Local (no account) list of subscribed channels stored in SharedPreferences.
 */
public final class SubscriptionsStore {
    private static final String PREFS = "notPipe_subscriptions";
    private static final String KEY = "channels";

    private SubscriptionsStore() {
    }

    /**
     * One subscribed channel as it is shown on the subscriptions screen.
     */
    public static class SubscribedChannel {
        public final String id;
        public final String title;
        public final String avatar;

        public SubscribedChannel(String id, String title, String avatar) {
            this.id = id;
            this.title = title;
            this.avatar = avatar;
        }
    }

    /**
     * Returns all subscriptions in the order they were added.
     *
     * @param context Any context
     * @return Mutable list of subscriptions
     */
    public static List<SubscribedChannel> list(Context context) {
        return decode(prefs(context).getString(KEY, ""));
    }

    /**
     * Serializes subscriptions to JSON. Kept free of Android APIs so it can be unit tested.
     *
     * @param channels Subscriptions to save
     * @return JSON array as a string
     */
    public static String encode(List<SubscribedChannel> channels) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < channels.size(); i++) {
            SubscribedChannel channel = channels.get(i);
            JSONObject item = new JSONObject();
            item.put("id", channel.id);
            item.put("title", channel.title);
            item.put("avatar", channel.avatar);
            array.add(item);
        }
        return array.build();
    }

    /**
     * Parses subscriptions from JSON, skipping entries without an ID.
     *
     * @param raw JSON array string, may be empty
     * @return Parsed subscriptions
     */
    public static List<SubscribedChannel> decode(String raw) {
        List<SubscribedChannel> result = new ArrayList<SubscribedChannel>();
        if (raw == null || raw.length() == 0) return result;

        try {
            JSONArray array = JSON.getArray(raw);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getObject(i);
                String id = item.getString("id", "");
                if (id.length() == 0) continue;
                result.add(new SubscribedChannel(id, item.getString("title", ""),
                        item.getString("avatar", "")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Tells whether the given channel is subscribed.
     *
     * @param context Any context
     * @param channelId Channel ID
     * @return true if subscribed
     */
    public static boolean isSubscribed(Context context, String channelId) {
        if (channelId == null) return false;
        List<SubscribedChannel> channels = list(context);
        for (int i = 0; i < channels.size(); i++) {
            if (channelId.equals(channels.get(i).id)) return true;
        }
        return false;
    }

    /**
     * Adds a channel to subscriptions, replacing the stored title/avatar if it already exists.
     *
     * @param context Any context
     * @param channelId Channel ID
     * @param title Channel title
     * @param avatar Channel avatar URL, may be empty
     */
    public static void add(Context context, String channelId, String title, String avatar) {
        if (channelId == null || channelId.length() == 0) return;

        List<SubscribedChannel> channels = list(context);
        for (int i = 0; i < channels.size(); i++) {
            if (channelId.equals(channels.get(i).id)) {
                channels.set(i, new SubscribedChannel(channelId, title, avatar));
                save(context, channels);
                return;
            }
        }
        channels.add(new SubscribedChannel(channelId, title, avatar));
        save(context, channels);
    }

    /**
     * Removes a channel from subscriptions.
     *
     * @param context Any context
     * @param channelId Channel ID
     */
    public static void remove(Context context, String channelId) {
        List<SubscribedChannel> channels = list(context);
        for (int i = 0; i < channels.size(); i++) {
            if (channelId.equals(channels.get(i).id)) {
                channels.remove(i);
                save(context, channels);
                return;
            }
        }
    }

    private static void save(Context context, List<SubscribedChannel> channels) {
        prefs(context).edit().putString(KEY, encode(channels)).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
