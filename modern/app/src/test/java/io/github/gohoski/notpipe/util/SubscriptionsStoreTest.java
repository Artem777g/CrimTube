package io.github.gohoski.notpipe.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.gohoski.notpipe.util.SubscriptionsStore.SubscribedChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the subscriptions JSON serialization.
 */
public class SubscriptionsStoreTest {

    @Test
    public void roundTripPreservesChannels() {
        List<SubscribedChannel> channels = Arrays.asList(
                new SubscribedChannel("UC1", "Первый канал", "http://a"),
                new SubscribedChannel("UC2", "Second \"quoted\"", ""));

        List<SubscribedChannel> restored = SubscriptionsStore.decode(
                SubscriptionsStore.encode(channels));

        assertEquals(2, restored.size());
        assertEquals("UC1", restored.get(0).id);
        assertEquals("Первый канал", restored.get(0).title);
        assertEquals("http://a", restored.get(0).avatar);
        assertEquals("Second \"quoted\"", restored.get(1).title);
        assertEquals("", restored.get(1).avatar);
    }

    @Test
    public void channelsWithoutIdAreSkipped() {
        assertTrue(SubscriptionsStore.decode("[{\"title\":\"x\"}]").isEmpty());
    }

    @Test
    public void brokenJsonReturnsEmptyList() {
        assertTrue(SubscriptionsStore.decode("oops").isEmpty());
        assertTrue(SubscriptionsStore.decode("").isEmpty());
        assertTrue(SubscriptionsStore.decode(null).isEmpty());
    }

    @Test
    public void emptyListRoundTrips() {
        assertTrue(SubscriptionsStore.decode(
                SubscriptionsStore.encode(new ArrayList<SubscribedChannel>())).isEmpty());
    }
}
