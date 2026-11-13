package io.github.gohoski.notpipe.download;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for file naming and size estimation (no file system or Android APIs involved).
 */
public class DownloadsStoreTest {

    @Test
    public void sanitizeReplacesIllegalCharacters() {
        assertEquals("a_b_c_d", DownloadsStore.sanitize("a/b:c?d"));
        assertEquals("x_y", DownloadsStore.sanitize("x\\y"));
        assertEquals("name", DownloadsStore.sanitize("name..."));
        assertEquals("", DownloadsStore.sanitize(null));
    }

    @Test
    public void fileNameHasReadableStemAndExtension() {
        assertEquals("Title [abc].mp4", DownloadsStore.fileName("Title", "abc", false, false));
        assertEquals("Title [abc].m4a", DownloadsStore.fileName("Title", "abc", true, false));
        assertEquals("Title [abc].3gp", DownloadsStore.fileName("Title", "abc", false, true));
    }

    @Test
    public void emptyTitleFallsBackToVideoId() {
        assertEquals("abc [abc].mp4", DownloadsStore.fileName("", "abc", false, false));
    }

    @Test
    public void longTitleIsTruncated() {
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 80; i++) title.append('a');

        String name = DownloadsStore.fileName(title.toString(), "id", false, false);
        String stem = name.substring(0, name.indexOf(" ["));

        assertEquals(60, stem.length());
        assertTrue(name.endsWith(" [id].mp4"));
    }

    @Test
    public void sizeEstimateGrowsWithQuality() {
        long audio = DownloadsStore.estimateBytes(3600, null, true);
        long sd = DownloadsStore.estimateBytes(3600, "360", false);
        long hd = DownloadsStore.estimateBytes(3600, "480", false);
        long fullHd = DownloadsStore.estimateBytes(3600, "720", false);

        assertTrue(audio > 0);
        assertTrue(audio < sd);
        assertTrue(sd < hd);
        assertTrue(hd < fullHd);
        assertEquals(0, DownloadsStore.estimateBytes(0, "360", false));
        assertEquals(0, DownloadsStore.estimateBytes(-1, "720", false));
    }
}
