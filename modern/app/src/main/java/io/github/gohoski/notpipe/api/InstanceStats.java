package io.github.gohoski.notpipe.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Gleb on 27.10.2026.
 * Keeps track of how fast every API instance answers.
 * <p>
 * Instances are picked at random by the Manager, so before this class a slow or half-dead
 * instance could be tried first and the user had to wait for a full timeout before the next
 * one was used. Remembering the latest response time allows the Manager to try the fastest
 * known instances first and to start a backup request early when the first one stalls.
 */
final class InstanceStats {
    /** Latency used for instances that were never tried yet. */
    private static final int UNKNOWN_LATENCY = 5000;
    /** Latency is clamped to this value so one slow answer can not ruin the ranking forever. */
    private static final int MAX_LATENCY = 30000;
    /** Added to the score for every consecutive failure. */
    private static final int FAILURE_PENALTY = 15000;
    /** Weight of a new measurement in the moving average. */
    private static final int SMOOTHING = 4;

    private static final Map<String, Integer> latency = new HashMap<String, Integer>();
    private static final Map<String, Integer> failures = new HashMap<String, Integer>();

    private InstanceStats() {
    }

    /**
     * Registers a successful answer of the given instance.
     *
     * @param host The instance host
     * @param elapsedMs Time in milliseconds the instance needed to answer
     */
    static synchronized void recordSuccess(String host, long elapsedMs) {
        if (host == null) return;
        int measured = (int) Math.min(elapsedMs, MAX_LATENCY);
        Integer previous = latency.get(host);
        int value = previous == null ? measured : (previous * (SMOOTHING - 1) + measured) / SMOOTHING;
        latency.put(host, Integer.valueOf(value));

        Integer failed = failures.get(host);
        if (failed != null) {
            int left = failed.intValue() - 1;
            if (left <= 0) failures.remove(host);
            else failures.put(host, Integer.valueOf(left));
        }
    }

    /**
     * Registers a failed answer of the given instance.
     *
     * @param host The instance host
     */
    static synchronized void recordFailure(String host) {
        if (host == null) return;
        Integer previous = failures.get(host);
        int value = previous == null ? 1 : previous.intValue() + 1;
        if (value > 8) value = 8;
        failures.put(host, Integer.valueOf(value));
    }

    /**
     * Returns a lower-is-better score of the given instance. Unknown instances sit between
     * known fast and known slow ones, which keeps the rotation healthy instead of always
     * hammering a single server.
     *
     * @param host The instance host
     * @return The score in milliseconds
     */
    static synchronized int scoreOf(String host) {
        int score = UNKNOWN_LATENCY;
        Integer known = latency.get(host);
        if (known != null) score = known.intValue();

        Integer failed = failures.get(host);
        if (failed != null) score += failed.intValue() * FAILURE_PENALTY;

        return score;
    }

    /**
     * Returns the last measured latency of an instance.
     *
     * @param host The instance host
     * @return Latency in milliseconds, or -1 if the instance was never tried
     */
    static synchronized int latencyOf(String host) {
        Integer value = latency.get(host);
        return value == null ? -1 : value.intValue();
    }

    /**
     * Returns how many consecutive failures are currently attributed to an instance.
     *
     * @param host The instance host
     * @return Number of failures, 0 if none
     */
    static synchronized int failuresOf(String host) {
        Integer value = failures.get(host);
        return value == null ? 0 : value.intValue();
    }

    /**
     * Removes all collected data. Used when the instance list is refreshed.
     */
    static synchronized void clear() {
        latency.clear();
        failures.clear();
    }
}
