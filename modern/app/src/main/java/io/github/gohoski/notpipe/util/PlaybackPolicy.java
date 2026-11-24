package io.github.gohoski.notpipe.util;

/**
 * Created on 04.11.2026.
 * Quality and timeout rules for playback and downloads.
 * Pure Java so the rules can be unit tested without a device.
 */
public final class PlaybackPolicy {
    public static final int REASON_NONE = 0;
    public static final int REASON_LONG_VIDEO = 1;
    public static final int REASON_EXPERIMENTAL_1080 = 2;

    private PlaybackPolicy() {
    }

    /**
     * Result of choosing a quality for a video.
     */
    public static class Decision {
        public final String quality;
        public final int reason;

        Decision(String quality, int reason) {
            this.quality = quality;
            this.reason = reason;
        }
    }

    /**
     * Chooses the quality to request. Long videos fall back to 360p, 1080p is experimental
     * but still allowed.
     *
     * @param preferred Preferred quality from settings, may be null
     * @param videoLength Video length in seconds, -1 if unknown
     * @return Chosen quality and the reason it was changed
     */
    public static Decision decide(String preferred, int videoLength) {
        String quality = preferred != null ? preferred : "360";
        int reason = REASON_NONE;

        if (!"360".equals(quality) && videoLength > 0) {
            if (videoLength > 3600) {
                quality = "360";
                reason = REASON_LONG_VIDEO;
            } else if ("1080".equals(quality)) {
                reason = REASON_EXPERIMENTAL_1080;
            }
        }
        return new Decision(quality, reason);
    }

    /**
     * Timeout for stream resolution: 360p is quick, higher qualities need more time.
     *
     * @param videoLength Video length in seconds, -1 if unknown
     * @param quality Requested quality
     * @return Timeout in milliseconds
     */
    public static int timeoutForQuality(int videoLength, String quality) {
        if (videoLength <= 0 || "360".equals(quality)) return 60000;
        return videoLength < 1800 ? 180000 : 360000;
    }
}
