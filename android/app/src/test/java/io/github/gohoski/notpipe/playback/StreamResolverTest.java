package io.github.gohoski.notpipe.playback;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import io.github.gohoski.notpipe.api.Conversion;
import io.github.gohoski.notpipe.api.VideoStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the stream resolution policy: cache, preferred instance and Manager fallback.
 */
public class StreamResolverTest {

    private static class FakeStream implements VideoStream {
        String url = "http://preferred";
        IOException error;

        @Override
        public String getName() {
            return "fake";
        }

        @Override
        public String getHost() {
            return "fake";
        }

        @Override
        public String getVideoUrl(String id, String quality, int timeout) throws IOException {
            if (error != null) throw error;
            return url;
        }
    }

    private static class FakeConversion extends FakeStream implements Conversion {
        String convUrl = "http://converted";
        IOException convError;

        @Override
        public String getConvUrl(String id, int codec) throws IOException {
            if (convError != null) throw convError;
            return convUrl;
        }
    }

    private static class FakeProvider implements StreamResolver.Provider {
        String videoResult = "http://fallback";
        String convResult = "http://conv-fallback";
        IOException videoError;
        int videoCalls;
        int convCalls;
        VideoStream lastPreferred;

        @Override
        public String getVideoUrl(String id, String quality, int timeout,
                                  VideoStream preferred, VideoStream[] successful) throws IOException {
            videoCalls++;
            lastPreferred = preferred;
            if (videoError != null) throw videoError;
            return videoResult;
        }

        @Override
        public String getConvUrl(String id, String quality, int codec,
                                 VideoStream preferred, VideoStream[] successful) throws IOException {
            convCalls++;
            lastPreferred = preferred;
            return convResult;
        }
    }

    @Test
    public void cachedFileWinsWithoutNetwork() throws Exception {
        File cached = File.createTempFile("notpipe", ".mp4");
        FakeProvider provider = new FakeProvider();
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, false, 0,
                null, new VideoStream[1], cached);

        assertEquals(cached.getAbsolutePath(), result.url);
        assertEquals(0, provider.videoCalls);
        cached.delete();
    }

    @Test
    public void preferredInstanceIsUsedFirst() {
        FakeStream stream = new FakeStream();
        FakeProvider provider = new FakeProvider();
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, false, 0,
                stream, new VideoStream[1], null);

        assertEquals("http://preferred", result.url);
        assertEquals(0, provider.videoCalls);
        assertNull(result.failedPreferred);
    }

    @Test
    public void failedPreferredFallsBackToManager() {
        FakeStream stream = new FakeStream();
        stream.error = new IOException("timeout");
        FakeProvider provider = new FakeProvider();
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, false, 0,
                stream, new VideoStream[1], null);

        assertEquals("http://fallback", result.url);
        assertEquals(1, provider.videoCalls);
        assertSame(stream, result.failedPreferred);
        assertNull(provider.lastPreferred);
    }

    @Test
    public void missingVideoIsReportedAsNotFound() {
        FakeProvider provider = new FakeProvider();
        provider.videoError = new FileNotFoundException("404");
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, false, 0,
                null, new VideoStream[1], null);

        assertNull(result.url);
        assertTrue(result.fileNotFound);
    }

    @Test
    public void conversionPreferredUsesConvertedUrl() {
        FakeConversion stream = new FakeConversion();
        FakeProvider provider = new FakeProvider();
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, true, 1,
                stream, new VideoStream[1], null);

        assertEquals("http://converted", result.url);
        assertFalse(result.fileNotFound);
        assertEquals(0, provider.convCalls);
    }

    @Test
    public void managerFailureReturnsErrorText() {
        FakeProvider provider = new FakeProvider();
        provider.videoError = new IOException("connection reset");
        StreamResolver resolver = new StreamResolver(provider);

        StreamResolver.Result result = resolver.resolve("id", "360", 1000, false, 0,
                null, new VideoStream[1], null);

        assertNull(result.url);
        assertFalse(result.fileNotFound);
        assertEquals("connection reset", result.error);
    }
}
