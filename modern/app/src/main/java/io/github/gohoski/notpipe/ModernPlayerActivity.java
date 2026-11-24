package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.data.Video;

/**
 * Created on 24.11.2026.
 * Современный полноэкранный плеер для актуальных Android.
 */
public class ModernPlayerActivity extends Activity {
    private VideoView videoView;
    private ProgressBar loading;
    private TextView titleView;
    private TextView channelView;
    private TextView descriptionView;
    private TextView qualityView;
    private TextView playView;
    private TextView statusView;
    private String videoId;
    private String quality = "360";
    private FrameLayout videoFrame;
    private LinearLayout topBar;
    private ScrollView scrollView;
    private boolean fullscreen;
    private float downX, downY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_player);
        if (NotPipe.SDK >= 21) {
            getWindow().setStatusBarColor(0xFF0B0D10);
            getWindow().setNavigationBarColor(0xFF0E1014);
        }

        videoId = getIntent().getStringExtra("ID");
        videoFrame = (FrameLayout) findViewById(R.id.modern_player_video_frame);
        topBar = (LinearLayout) findViewById(R.id.modern_player_top);
        scrollView = (ScrollView) findViewById(R.id.modern_player_scroll);
        videoView = (VideoView) findViewById(R.id.modern_player_video);
        loading = (ProgressBar) findViewById(R.id.modern_player_loading);
        titleView = (TextView) findViewById(R.id.modern_player_title);
        channelView = (TextView) findViewById(R.id.modern_player_channel);
        descriptionView = (TextView) findViewById(R.id.modern_player_description);
        qualityView = (TextView) findViewById(R.id.modern_player_quality);
        playView = (TextView) findViewById(R.id.modern_player_play);
        statusView = (TextView) findViewById(R.id.modern_player_status);

        findViewById(R.id.modern_player_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        playView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        findViewById(R.id.modern_player_back10).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoView.seekTo(Math.max(0, videoView.getCurrentPosition() - 10000));
            }
        });
        findViewById(R.id.modern_player_forward10).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoView.seekTo(videoView.getCurrentPosition() + 10000);
            }
        });
        qualityView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showQualityDialog();
            }
        });
        findViewById(R.id.modern_player_fullscreen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFullscreen();
            }
        });
        videoFrame.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) > 80 && Math.abs(dx) > Math.abs(dy)) {
                        videoView.seekTo(Math.max(0, videoView.getCurrentPosition() + (dx > 0 ? 10000 : -10000)));
                        Toast.makeText(ModernPlayerActivity.this, dx > 0 ? "+10 сек" : "-10 сек", Toast.LENGTH_SHORT).show();
                    } else if (Math.abs(dy) > 80) {
                        adjustGesture(dy, event.getX() > v.getWidth() / 2);
                    } else {
                        togglePlay();
                    }
                }
                return true;
            }
        });

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                loading.setVisibility(View.GONE);
                videoView.start();
                playView.setText("❚❚");
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playView.setText("▶");
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                loading.setVisibility(View.GONE);
                statusView.setText(R.string.error_playback_generic);
                statusView.setVisibility(View.VISIBLE);
                return true;
            }
        });

        new LoadTask().execute(videoId);
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
        if (fullscreen) {
            params.height = 0;
            params.weight = 1;
            topBar.setVisibility(View.GONE);
            scrollView.setVisibility(View.GONE);
            statusView.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (NotPipe.SDK >= 19) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            params.height = 230;
            params.weight = 0;
            topBar.setVisibility(View.VISIBLE);
            scrollView.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(0);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        videoFrame.setLayoutParams(params);
    }

    private void adjustGesture(float dy, boolean rightSide) {
        if (rightSide) {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    dy > 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI);
        } else {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            float current = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
            lp.screenBrightness = Math.max(0.05f, Math.min(1f, current + (dy > 0 ? -0.1f : 0.1f)));
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
        } else {
            super.onBackPressed();
        }
    }

    private void togglePlay() {
        if (videoView.isPlaying()) {
            videoView.pause();
            playView.setText("▶");
        } else {
            videoView.start();
            playView.setText("❚❚");
        }
    }

    private void showQualityDialog() {
        final String[] values = new String[]{"360p", "720p", "1080p"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_quality)
                .setItems(values, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        quality = values[which].replace("p", "");
                        qualityView.setText(values[which]);
                        new QualityTask().execute(quality);
                    }
                })
                .show();
    }

    private class LoadTask extends AsyncTask<String, Void, String[]> {
        @Override
        protected String[] doInBackground(String... params) {
            try {
                Video video = Manager.getInstance().getVideo(params[0]);
                String url = video.videoUrl;
                if (url == null || url.length() == 0) {
                    url = Manager.getInstance().getVideoUrl(params[0], "360", 30000);
                }
                return new String[]{video.title, video.channel, video.description, url};
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] data) {
            if (data == null) {
                loading.setVisibility(View.GONE);
                statusView.setText(R.string.error_all_instances);
                statusView.setVisibility(View.VISIBLE);
                return;
            }
            titleView.setText(data[0]);
            channelView.setText(data[1]);
            descriptionView.setText(data[2]);
            play(data[3]);
        }
    }

    private class QualityTask extends AsyncTask<String, Void, String> {
        private int position;

        @Override
        protected void onPreExecute() {
            position = videoView.getCurrentPosition();
            loading.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                return Manager.getInstance().getVideoUrl(videoId, params[0], 30000);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String url) {
            if (url == null) {
                loading.setVisibility(View.GONE);
                statusView.setText(R.string.error_instance_blocked);
                statusView.setVisibility(View.VISIBLE);
                return;
            }
            play(url);
            videoView.seekTo(position);
        }
    }

    private void play(String url) {
        statusView.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(url));
        videoView.requestFocus();
        videoView.start();
    }

    @Override
    protected void onDestroy() {
        videoView.stopPlayback();
        super.onDestroy();
    }
}
