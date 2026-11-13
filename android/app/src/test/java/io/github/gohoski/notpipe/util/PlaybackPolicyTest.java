package io.github.gohoski.notpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for quality selection and timeouts.
 */
public class PlaybackPolicyTest {

    @Test
    public void longVideoFallsBackTo360p() {
        PlaybackPolicy.Decision decision = PlaybackPolicy.decide("720", 4000);
        assertEquals("360", decision.quality);
        assertEquals(PlaybackPolicy.REASON_LONG_VIDEO, decision.reason);
    }

    @Test
    public void short1080pIsKeptButMarkedExperimental() {
        PlaybackPolicy.Decision decision = PlaybackPolicy.decide("1080", 100);
        assertEquals("1080", decision.quality);
        assertEquals(PlaybackPolicy.REASON_EXPERIMENTAL_1080, decision.reason);
    }

    @Test
    public void nullPreferenceMeans360p() {
        PlaybackPolicy.Decision decision = PlaybackPolicy.decide(null, 100);
        assertEquals("360", decision.quality);
        assertEquals(PlaybackPolicy.REASON_NONE, decision.reason);
    }

    @Test
    public void unknownLengthKeepsRequestedQuality() {
        PlaybackPolicy.Decision decision = PlaybackPolicy.decide("480", -1);
        assertEquals("480", decision.quality);
        assertEquals(PlaybackPolicy.REASON_NONE, decision.reason);
    }

    @Test
    public void timeoutsMatchQualityAndLength() {
        assertEquals(60000, PlaybackPolicy.timeoutForQuality(100, "360"));
        assertEquals(60000, PlaybackPolicy.timeoutForQuality(-1, "720"));
        assertEquals(180000, PlaybackPolicy.timeoutForQuality(100, "720"));
        assertEquals(360000, PlaybackPolicy.timeoutForQuality(2000, "720"));
    }
}
