package io.github.gohoski.notpipe.download;

/**
 * Created on 28.10.2026.
 * One queued download: which video, which quality and whether only the audio track is wanted.
 */
class DownloadRequest {
    final String videoId;
    final String title;
    final String quality;
    final boolean audio;
    final int length;

    DownloadRequest(String videoId, String title, boolean audio, String quality, int length) {
        this.videoId = videoId;
        this.title = title;
        this.audio = audio;
        this.quality = quality;
        this.length = length;
    }
}
