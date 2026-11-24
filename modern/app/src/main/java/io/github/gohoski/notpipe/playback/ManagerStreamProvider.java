package io.github.gohoski.notpipe.playback;

import java.io.IOException;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.VideoStream;

/**
 * Created on 04.11.2026.
 * Connects {@link StreamResolver} to the singleton Manager.
 */
public class ManagerStreamProvider implements StreamResolver.Provider {
    @Override
    public String getVideoUrl(String id, String quality, int timeout,
                              VideoStream preferred, VideoStream[] successful) throws IOException {
        return Manager.getInstance().getVideoUrl(id, quality, timeout, preferred, successful);
    }

    @Override
    public String getConvUrl(String id, String quality, int codec,
                             VideoStream preferred, VideoStream[] successful) throws IOException {
        return Manager.getInstance().getConvUrl(id, quality, codec, preferred, successful);
    }
}
