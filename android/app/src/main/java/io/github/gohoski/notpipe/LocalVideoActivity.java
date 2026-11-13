package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

/**
 * Created on 28.10.2026.
 * Minimal built-in player for downloaded files.
 * <p>
 * The Downloads screen opens files here instead of relying on an external player:
 * on many legacy devices no player is installed, and some players silently refuse
 * content:// URIs, which looked like "nothing happens" when a row was tapped.
 */
public class LocalVideoActivity extends Activity {
    private VideoView videoView;
    private int startPosition = 0;
    private boolean prepared = false;
    private boolean wasPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_video);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        videoView = (VideoView) findViewById(R.id.local_video);

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        String title = getIntent().getStringExtra("title");
        if (title != null && title.length() > 0) setTitle(title);

        if (savedInstanceState != null) {
            startPosition = savedInstanceState.getInt("position", 0);
        }

        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                prepared = true;
                if (startPosition > 0) {
                    videoView.seekTo(startPosition);
                }
                videoView.start();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                prepared = false;
                Toast.makeText(LocalVideoActivity.this, R.string.play_failed, Toast.LENGTH_LONG).show();
                return true;
            }
        });

        videoView.setVideoURI(uri);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (prepared) {
            try {
                int position = videoView.getCurrentPosition();
                if (position > 0) outState.putInt("position", position);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (prepared) {
            try {
                wasPlaying = videoView.isPlaying();
                if (wasPlaying) videoView.pause();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prepared && wasPlaying) {
            wasPlaying = false;
            videoView.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            try {
                videoView.stopPlayback();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Convenience factory used by the Downloads screen.
     *
     * @param activity Host activity
     * @param uri File URI (content:// on Android 7+)
     * @param title Display title
     * @return Intent that starts this player
     */
    public static Intent intentFor(Activity activity, Uri uri, String title) {
        Intent intent = new Intent(activity, LocalVideoActivity.class);
        intent.setData(uri);
        intent.putExtra("title", title);
        return intent;
    }
}
