package io.github.gohoski.notpipe.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.data.VideoInfo;

/**
 * Created on 04.11.2026.
 * JSON serialization for locally stored video lists (Watch later, history).
 * Contains no Android APIs, so it can be covered by plain JVM unit tests.
 */
public final class VideoListCodec {
    private VideoListCodec() {
    }

    /**
     * Serializes videos to a JSON array.
     *
     * @param videos Videos to store
     * @return JSON string
     */
    public static String encode(List<VideoInfo> videos) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < videos.size(); i++) {
            VideoInfo video = videos.get(i);
            JSONObject item = new JSONObject();
            item.put("id", video.id);
            item.put("title", video.title != null ? video.title : "");
            item.put("thumbnail", video.thumbnail != null ? video.thumbnail : "");
            item.put("channel", video.channel != null ? video.channel : "");
            item.put("channel_thumbnail", video.channelThumbnail != null ? video.channelThumbnail : "");
            item.put("channel_id", video.channelId != null ? video.channelId : "");
            item.put("duration", video.duration != null ? video.duration : "");
            item.put("views", video.views);
            item.put("date", video.publishedAt != null ? video.publishedAt.getTime() : 0);
            array.add(item);
        }
        return array.build();
    }

    /**
     * Parses videos from JSON, skipping entries without an ID.
     *
     * @param raw JSON string, may be empty
     * @return Parsed videos
     */
    public static List<VideoInfo> decode(String raw) {
        List<VideoInfo> result = new ArrayList<VideoInfo>();
        if (raw == null || raw.length() == 0) return result;

        try {
            JSONArray array = JSON.getArray(raw);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getObject(i);
                String id = item.getString("id", "");
                if (id.length() == 0) continue;

                long date = item.getLong("date", 0);
                result.add(new VideoInfo(
                        id,
                        item.getString("title", ""),
                        item.getString("thumbnail", ""),
                        item.getString("channel", ""),
                        item.getString("channel_thumbnail", ""),
                        item.getString("channel_id", ""),
                        item.getString("duration", ""),
                        item.getLong("views", -1),
                        date > 0 ? new Date(date) : null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
