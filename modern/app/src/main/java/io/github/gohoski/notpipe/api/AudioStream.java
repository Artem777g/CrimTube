package io.github.gohoski.notpipe.api;

import java.io.IOException;

/**
 * Created on 27.10.2026.
 * Capability of an API instance that can resolve an audio-only stream.
 * <p>
 * Audio-only formats are not exposed by every backend: Invidious returns them in
 * {@code adaptiveFormats} and Piped in {@code audioStreams}, while yt2009 and YtAPILegacy
 * only serve muxed video files. Audio downloads therefore depend on at least one
 * Invidious or Piped instance being configured.
 */
public interface AudioStream {
    String getName();

    String getHost();

    /**
     * Resolves the best available audio-only stream for the given video.
     *
     * @param id The YouTube video ID
     * @return Direct URL of the audio stream (usually M4A/AAC)
     * @throws IOException If the instance can not provide an audio stream
     */
    String getAudioUrl(String id) throws IOException;
}
