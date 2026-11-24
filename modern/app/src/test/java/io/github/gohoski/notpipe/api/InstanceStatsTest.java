package io.github.gohoski.notpipe.api;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for latency/failure based ranking of instances.
 */
public class InstanceStatsTest {

    @Before
    public void setUp() {
        InstanceStats.clear();
    }

    @Test
    public void unknownInstanceHasDefaultScore() {
        assertEquals(5000, InstanceStats.scoreOf("unknown"));
        assertEquals(-1, InstanceStats.latencyOf("unknown"));
        assertEquals(0, InstanceStats.failuresOf("unknown"));
    }

    @Test
    public void successRecordsLatency() {
        InstanceStats.recordSuccess("host", 1200);
        assertEquals(1200, InstanceStats.latencyOf("host"));
        assertEquals(1200, InstanceStats.scoreOf("host"));
    }

    @Test
    public void failureAddsPenalty() {
        InstanceStats.recordSuccess("host", 1000);
        InstanceStats.recordFailure("host");
        assertEquals(1, InstanceStats.failuresOf("host"));
        assertEquals(16000, InstanceStats.scoreOf("host"));
    }

    @Test
    public void successRemovesOneFailure() {
        InstanceStats.recordFailure("host");
        InstanceStats.recordFailure("host");
        InstanceStats.recordSuccess("host", 500);

        assertEquals(1, InstanceStats.failuresOf("host"));
    }

    @Test
    public void clearResetsEverything() {
        InstanceStats.recordSuccess("host", 700);
        InstanceStats.recordFailure("host");
        InstanceStats.clear();

        assertEquals(-1, InstanceStats.latencyOf("host"));
        assertEquals(0, InstanceStats.failuresOf("host"));
        assertEquals(5000, InstanceStats.scoreOf("host"));
    }
}
