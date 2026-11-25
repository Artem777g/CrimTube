package io.github.gohoski.notpipe.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

import java.io.IOException;

/**
 * Created on 25.11.2026.
 * Video view на TextureView: в отличие от SurfaceView не даёт чёрный экран
 * внутри сложных layout и корректно масштабирует кадр.
 */
public class TextureVideoView extends FrameLayout {

    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private Surface surface;
    private Uri uri;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean surfaceReady;
    private int videoWidth;
    private int videoHeight;
    private MediaPlayer.OnPreparedListener preparedListener;
    private MediaPlayer.OnCompletionListener completionListener;
    private MediaPlayer.OnErrorListener errorListener;

    public TextureVideoView(Context context) {
        super(context);
        init(context);
    }

    public TextureVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        textureView = new TextureView(context);
        addView(textureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                surfaceReady = true;
                attachSurface(surfaceTexture);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                applyTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                surfaceReady = false;
                releaseSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        });
    }

    public void setVideoURI(Uri uri) {
        this.uri = uri;
        playWhenReady = false;
        prepared = false;
        videoWidth = 0;
        videoHeight = 0;
        releasePlayer();
        if (uri != null) {
            prepareAsync();
        }
    }

    private void prepareAsync() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (surfaceReady && surface != null) {
            mediaPlayer.setSurface(surface);
        }
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                videoWidth = mp.getVideoWidth();
                videoHeight = mp.getVideoHeight();
                applyTransform();
                if (surfaceReady && textureView.getSurfaceTexture() != null) {
                    mp.setSurface(surface);
                }
                if (preparedListener != null) {
                    preparedListener.onPrepared(mp);
                }
                if (playWhenReady) {
                    mp.start();
                }
            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playWhenReady = false;
                if (completionListener != null) {
                    completionListener.onCompletion(mp);
                }
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                if (errorListener != null) {
                    return errorListener.onError(mp, what, extra);
                }
                return false;
            }
        });
        try {
            mediaPlayer.setDataSource(getContext(), uri);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            if (errorListener != null) {
                errorListener.onError(mediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
        }
    }

    private void attachSurface(SurfaceTexture surfaceTexture) {
        releaseSurface();
        surface = new Surface(surfaceTexture);
        if (mediaPlayer != null) {
            mediaPlayer.setSurface(surface);
        }
    }

    private void releaseSurface() {
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    public void start() {
        playWhenReady = true;
        if (prepared && mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        playWhenReady = false;
        if (prepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public boolean isPlaying() {
        try {
            return prepared && mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getCurrentPosition() {
        try {
            return prepared && mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getDuration() {
        try {
            return prepared && mediaPlayer != null ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public void seekTo(int position) {
        if (prepared && mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public void stopPlayback() {
        playWhenReady = false;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        releasePlayer();
    }

    private void releasePlayer() {
        releaseSurface();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        prepared = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        releasePlayer();
        super.onDetachedFromWindow();
    }

    private void applyTransform() {
        if (videoWidth <= 0 || videoHeight <= 0 || textureView == null) {
            return;
        }
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        float viewAspect = (float) viewWidth / viewHeight;
        float videoAspect = (float) videoWidth / videoHeight;
        float scaleX = 1f;
        float scaleY = 1f;
        if (viewAspect > videoAspect) {
            scaleX = videoAspect / viewAspect;
        } else {
            scaleY = viewAspect / videoAspect;
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(matrix);
    }

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) {
        this.preparedListener = listener;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
        this.completionListener = listener;
    }

    public void setOnErrorListener(MediaPlayer.OnErrorListener listener) {
        this.errorListener = listener;
    }
}
