package io.github.gohoski.notpipe.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import io.github.gohoski.notpipe.util.PlaybackPositionsStore.Entry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the "resume playback" position store.
 */
public class PlaybackPositionsStoreTest {

    @Test
    public void roundTripPreservesPositions() {
        List<Entry> restored = PlaybackPositionsStore.decode(PlaybackPositionsStore.encode(
                Arrays.asList(new Entry("abc", 120000, 600000, 1700000000000L))));

        assertEquals(1, restored.size());
        assertEquals("abc", restored.get(0).id);
        assertEquals(120000, restored.get(0).position);
        assertEquals(600000, restored.get(0).duration);
        assertEquals(1700000000000L, restored.get(0).updated);
    }

    @Test
    public void entriesWithoutIdAreSkipped() {
        assertTrue(PlaybackPositionsStore.decode("[{\"position\":50000}]").isEmpty());
    }

    @Test
    public void brokenJsonReturnsEmptyList() {
        assertTrue(PlaybackPositionsStore.decode("nope").isEmpty());
        assertTrue(PlaybackPositionsStore.decode("").isEmpty());
        assertTrue(PlaybackPositionsStore.decode(null).isEmpty());
    }

    @Test
    public void tooEarlyPositionIsNotResumed() {
        assertFalse(PlaybackPositionsStore.shouldResume(
                new Entry("id", 10000, 600000, 0), 600000));
    }

    @Test
    public void normalPositionIsResumed() {
        assertTrue(PlaybackPositionsStore.shouldResume(
                new Entry("id", 120000, 600000, 0), 600000));
    }

    @Test
    public void positionNearTheEndIsNotResumed() {
        assertFalse(PlaybackPositionsStore.shouldResume(
                new Entry("id", 570000, 600000, 0), 600000));
    }

    @Test
    public void unknownDurationAllowsResume() {
        assertTrue(PlaybackPositionsStore.shouldResume(
                new Entry("id", 120000, -1, 0), -1));
    }
}
