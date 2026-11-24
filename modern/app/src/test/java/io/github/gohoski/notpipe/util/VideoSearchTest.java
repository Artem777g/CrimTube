package io.github.gohoski.notpipe.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.gohoski.notpipe.data.VideoInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the in-channel title filtering.
 */
public class VideoSearchTest {

    private static VideoInfo video(String id, String title) {
        return new VideoInfo(id, title, "", "", "", "", "", -1, null);
    }

    @Test
    public void emptyQueryReturnsTheSameList() {
        List<VideoInfo> source = Arrays.asList(video("1", "Rose"), video("2", "Potato"));
        assertSame(source, VideoSearch.filter(source, ""));
        assertSame(source, VideoSearch.filter(source, null));
    }

    @Test
    public void filterIsCaseInsensitive() {
        List<VideoInfo> source = Arrays.asList(
                video("1", "Quirky Love Rosee"),
                video("2", "Potato Please"),
                video("3", "ROSE bud"));

        List<VideoInfo> result = VideoSearch.filter(source, "rose");
        assertEquals(2, result.size());
        assertEquals("1", result.get(0).id);
        assertEquals("3", result.get(1).id);
    }

    @Test
    public void videosWithoutTitleAreSkipped() {
        List<VideoInfo> source = Arrays.asList(video("1", null), video("2", "Rose"));
        assertEquals(1, VideoSearch.filter(source, "rose").size());
    }

    @Test
    public void noMatchesGivesEmptyList() {
        List<VideoInfo> source = Arrays.asList(video("1", "Potato"));
        assertTrue(VideoSearch.filter(source, "rose").isEmpty());
    }

    @Test
    public void nullSourceGivesEmptyList() {
        assertTrue(VideoSearch.filter(null, "rose").isEmpty());
        assertTrue(VideoSearch.filter(new ArrayList<VideoInfo>(), "rose").isEmpty());
    }
}
