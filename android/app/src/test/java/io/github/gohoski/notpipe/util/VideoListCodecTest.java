package io.github.gohoski.notpipe.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.github.gohoski.notpipe.data.VideoInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the JSON codec shared by "Watch later" and history.
 */
public class VideoListCodecTest {

    @Test
    public void roundTripPreservesFields() {
        Date published = new Date(1700000000000L);
        VideoInfo video = new VideoInfo("abc123", "Заголовок \"quoted\"", "http://thumb",
                "Канал", "http://avatar", "UC123", "12:34", 12345L, published);

        List<VideoInfo> restored = VideoListCodec.decode(
                VideoListCodec.encode(Arrays.asList(video)));

        assertEquals(1, restored.size());
        VideoInfo item = restored.get(0);
        assertEquals("abc123", item.id);
        assertEquals("Заголовок \"quoted\"", item.title);
        assertEquals("http://thumb", item.thumbnail);
        assertEquals("Канал", item.channel);
        assertEquals("http://avatar", item.channelThumbnail);
        assertEquals("UC123", item.channelId);
        assertEquals("12:34", item.duration);
        assertEquals(12345L, item.views);
        assertEquals(published.getTime(), item.publishedAt.getTime());
    }

    @Test
    public void missingOptionalFieldsBecomeDefaults() {
        VideoInfo video = new VideoInfo("id1", null, null, null, null, null, null, -1, null);

        VideoInfo item = VideoListCodec.decode(
                VideoListCodec.encode(Arrays.asList(video))).get(0);

        assertEquals("", item.title);
        assertEquals("", item.thumbnail);
        assertEquals("", item.channel);
        assertEquals(-1L, item.views);
        assertNull(item.publishedAt);
    }

    @Test
    public void entriesWithoutIdAreSkipped() {
        List<VideoInfo> restored = VideoListCodec.decode("[{\"title\":\"no id\"}]");
        assertTrue(restored.isEmpty());
    }

    @Test
    public void brokenJsonReturnsEmptyList() {
        assertTrue(VideoListCodec.decode("{not a json").isEmpty());
        assertTrue(VideoListCodec.decode("").isEmpty());
        assertTrue(VideoListCodec.decode(null).isEmpty());
    }

    @Test
    public void emptyListRoundTrips() {
        List<VideoInfo> empty = new ArrayList<VideoInfo>();
        assertTrue(VideoListCodec.decode(VideoListCodec.encode(empty)).isEmpty());
    }
}
