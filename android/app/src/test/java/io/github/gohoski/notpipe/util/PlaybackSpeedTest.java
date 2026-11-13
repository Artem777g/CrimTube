package io.github.gohoski.notpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for playback speed options.
 */
public class PlaybackSpeedTest {

    @Test
    public void speedControlNeedsAndroidSix() {
        assertFalse(PlaybackSpeed.isSupported(22));
        assertTrue(PlaybackSpeed.isSupported(23));
        assertTrue(PlaybackSpeed.isSupported(25));
    }

    @Test
    public void defaultSpeedIsNormal() {
        assertEquals(1.0f, PlaybackSpeed.DEFAULT, 0.001f);
        assertEquals(2, PlaybackSpeed.indexOf(PlaybackSpeed.DEFAULT));
    }

    @Test
    public void optionListIsOrderedAndComplete() {
        assertEquals(6, PlaybackSpeed.count());
        assertEquals(0.5f, PlaybackSpeed.getSpeed(0), 0.001f);
        assertEquals(0.75f, PlaybackSpeed.getSpeed(1), 0.001f);
        assertEquals(1.25f, PlaybackSpeed.getSpeed(3), 0.001f);
        assertEquals(1.5f, PlaybackSpeed.getSpeed(4), 0.001f);
        assertEquals(2.0f, PlaybackSpeed.getSpeed(5), 0.001f);
    }

    @Test
    public void indexOfFindsEveryOption() {
        for (int i = 0; i < PlaybackSpeed.count(); i++) {
            assertEquals(i, PlaybackSpeed.indexOf(PlaybackSpeed.getSpeed(i)));
        }
    }

    @Test
    public void unknownSpeedFallsBackToDefault() {
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.normalize(3.5f), 0.001f);
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.normalize(0.0f), 0.001f);
        assertEquals(2, PlaybackSpeed.indexOf(3.5f));
    }

    @Test
    public void outOfRangeIndexFallsBackToDefault() {
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.getSpeed(-1), 0.001f);
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.getSpeed(99), 0.001f);
    }
}
