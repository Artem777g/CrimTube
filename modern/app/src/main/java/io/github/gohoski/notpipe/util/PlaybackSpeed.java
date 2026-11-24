package io.github.gohoski.notpipe.util;

/**
 * Created on 12.11.2026.
 * Playback speed options and the API level that supports them.
 * Pure Java so the rules can be unit tested without a device.
 */
public final class PlaybackSpeed {
    /** Speed used when nothing else is selected. */
    public static final float DEFAULT = 1.0f;
    /** MediaPlayer.setPlaybackParams() appeared in Android 6.0. */
    public static final int MIN_SDK = 23;

    private static final float[] SPEEDS = {0.5f, 0.75f, DEFAULT, 1.25f, 1.5f, 2.0f};

    private PlaybackSpeed() {
    }

    /**
     * Tells whether the platform can change the playback speed.
     *
     * @param sdk Device API level
     * @return true on Android 6.0 and newer
     */
    public static boolean isSupported(int sdk) {
        return sdk >= MIN_SDK;
    }

    /**
     * Number of available speed options.
     *
     * @return Option count
     */
    public static int count() {
        return SPEEDS.length;
    }

    /**
     * Returns the speed at the given option index.
     *
     * @param index Option index
     * @return Speed multiplier, or the default when the index is out of range
     */
    public static float getSpeed(int index) {
        if (index < 0 || index >= SPEEDS.length) return DEFAULT;
        return SPEEDS[index];
    }

    /**
     * Finds the option index of a speed.
     *
     * @param speed Speed multiplier
     * @return Matching index, or the index of the default speed
     */
    public static int indexOf(float speed) {
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == speed) return i;
        }
        return indexOf(DEFAULT);
    }

    /**
     * Converts an arbitrary value from preferences into a supported speed.
     *
     * @param speed Speed multiplier, may be unknown
     * @return Matching supported speed, or the default
     */
    public static float normalize(float speed) {
        for (int i = 0; i < SPEEDS.length; i++) {
            if (SPEEDS[i] == speed) return SPEEDS[i];
        }
        return DEFAULT;
    }
}
