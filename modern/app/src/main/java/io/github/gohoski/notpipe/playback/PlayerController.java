package io.github.gohoski.notpipe.playback;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

/**
 * Created on 04.11.2026.
 * Owns the mechanical part of playback: media controls, keep-screen-on and position
 * tracking. Prepared/error UI stays in the activity, which receives prepared callbacks
 * through its own listeners.
 */
public class PlayerController {
    /**
     * Receives the playback position while it is being tracked.
     */
    public interface Listener {
        void onPosition(int position);
    }

    private static final int TRACK_INTERVAL = 5000;

    private final Activity activity;
    private final Listener listener;
    private final Handler handler = new Handler();
    private final Runnable tracker = new Runnable() {
        @Override
        public void run() {
            trackPosition();
            handler.postDelayed(this, TRACK_INTERVAL);
        }
    };

    private VideoView view;
    private int position;

    public PlayerController(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    /**
     * Binds the controller to the video view.
     *
     * @param view Video view of the activity
     */
    public void attach(VideoView view) {
        this.view = view;
    }

    /**
     * Creates the standard media controller anchored to the video frame.
     *
     * @param anchor View the controls should be anchored to
     */
    public void setupControls(View anchor) {
        if (view == null) return;
        MediaController controller = new MediaController(activity);
        controller.setAnchorView(anchor);
        view.setMediaController(controller);
    }

    /**
     * Removes the media controller, ignoring legacy framework errors.
     */
    public void clearControls() {
        if (view == null) return;
        try {
            view.setMediaController(null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Seeks to a saved position.
     *
     * @param position Position in milliseconds
     */
    public void seekTo(int position) {
        if (view == null) return;
        try {
            view.seekTo(position);
        } catch (Exception ignored) {
        }
    }

    /**
     * Starts playback and position tracking.
     *
     * @param player Prepared media player
     */
    public void start(MediaPlayer player) {
        if (player != null) {
            player.start();
        }
        startTracking();
    }

    /**
     * Keeps the screen awake while the video is loading or playing.
     *
     * @param on true to keep the screen on
     */
    public void setKeepScreenOn(boolean on) {
        if (on) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void startTracking() {
        handler.removeCallbacks(tracker);
        handler.postDelayed(tracker, TRACK_INTERVAL);
    }

    public void stopTracking() {
        handler.removeCallbacks(tracker);
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /**
     * Stops tracking and releases the view reference.
     */
    public void release() {
        stopTracking();
        clearControls();
        view = null;
    }

    private void trackPosition() {
        if (view == null) return;
        try {
            if (view.isPlaying()) {
                int current = view.getCurrentPosition();
                if (current > 0) {
                    position = current;
                    if (listener != null) listener.onPosition(current);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
