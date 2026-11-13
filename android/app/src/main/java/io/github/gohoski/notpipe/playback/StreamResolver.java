package io.github.gohoski.notpipe.playback;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import io.github.gohoski.notpipe.api.Conversion;
import io.github.gohoski.notpipe.api.VideoStream;

/**
 * Created on 04.11.2026.
 * Resolves a playable URL for a video: cached file first, then the preferred instance,
 * then the Manager fallback.
 * <p>
 * The class has no Android dependencies. The Manager is hidden behind {@link Provider},
 * which makes the policy testable with fake instances.
 */
public class StreamResolver {
    /**
     * Access to the multi-instance Manager.
     */
    public interface Provider {
        String getVideoUrl(String id, String quality, int timeout,
                           VideoStream preferred, VideoStream[] successful) throws IOException;

        String getConvUrl(String id, String quality, int codec,
                          VideoStream preferred, VideoStream[] successful) throws IOException;
    }

    /**
     * Result of one resolution attempt.
     */
    public static class Result {
        public final String url;
        public final boolean fileNotFound;
        public final String error;
        /** Instance that failed before the Manager fallback, may be null. */
        public final VideoStream failedPreferred;

        Result(String url, boolean fileNotFound, String error, VideoStream failedPreferred) {
            this.url = url;
            this.fileNotFound = fileNotFound;
            this.error = error;
            this.failedPreferred = failedPreferred;
        }
    }

    private final Provider provider;

    public StreamResolver(Provider provider) {
        this.provider = provider;
    }

    /**
     * Resolves the stream URL.
     *
     * @param id Video ID
     * @param quality Requested quality
     * @param timeout Timeout in milliseconds
     * @param convert True when a converted (MPEG-4/H.263) stream is wanted
     * @param codec Conversion codec
     * @param preferred Instance to try first, may be null
     * @param successful Out-parameter filled with the instance that answered
     * @param cachedFile Already downloaded cache file, may be null
     * @return Resolution result
     */
    public Result resolve(String id, String quality, int timeout, boolean convert, int codec,
                          VideoStream preferred, VideoStream[] successful, File cachedFile) {
        try {
            if (cachedFile != null && cachedFile.exists()) {
                return new Result(cachedFile.getAbsolutePath(), false, null, null);
            }

            if (preferred != null) {
                try {
                    if (convert && preferred instanceof Conversion) {
                        return new Result(((Conversion) preferred).getConvUrl(id, codec),
                                false, null, null);
                    }
                    return new Result(preferred.getVideoUrl(id, quality, timeout),
                            false, null, null);
                } catch (FileNotFoundException e) {
                    if (!convert) {
                        return new Result(null, true, e.getMessage(), null);
                    }
                } catch (Exception e) {
                    if (e instanceof FileNotFoundException && !convert) {
                        return new Result(null, true, e.getMessage(), null);
                    }
                }

                // The preferred instance failed: let the Manager pick another one.
                String fallback = convert
                        ? provider.getConvUrl(id, quality, codec, null, successful)
                        : provider.getVideoUrl(id, quality, timeout, null, successful);
                return new Result(fallback, false, null, preferred);
            }

            String url = convert
                    ? provider.getConvUrl(id, quality, codec, null, successful)
                    : provider.getVideoUrl(id, quality, timeout, null, successful);
            return new Result(url, false, null, null);
        } catch (FileNotFoundException e) {
            return new Result(null, true, e.getMessage(), null);
        } catch (Exception e) {
            return new Result(null, false, e.getMessage(), null);
        }
    }
}
