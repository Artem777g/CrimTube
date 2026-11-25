package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.gohoski.notpipe.ui.TextureVideoView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.data.Comment;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.playback.ManagerStreamProvider;
import io.github.gohoski.notpipe.playback.StreamResolver;
import io.github.gohoski.notpipe.ui.AdapterLinearLayout;
import io.github.gohoski.notpipe.ui.CommentAdapter;
import io.github.gohoski.notpipe.ui.DownloadLauncher;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.ChannelIconResolver;
import io.github.gohoski.notpipe.util.HistoryStore;
import io.github.gohoski.notpipe.util.ImageLoader;
import io.github.gohoski.notpipe.util.PlaybackPolicy;
import io.github.gohoski.notpipe.util.PlaybackPositionsStore;
import io.github.gohoski.notpipe.util.PlaybackSpeed;
import io.github.gohoski.notpipe.util.SubscriptionsStore;
import io.github.gohoski.notpipe.util.WatchLaterStore;

/**
 * Created on 24.11.2026.
 * Современный полноэкранный плеер для актуальных Android.
 * <p>
 * Умеет: воспроизведение во всю ширину с правильным соотношением сторон, выбор качества,
 * скорость, жесты (-10/+10, яркость/громкость), полноэкранный режим, карточку канала с
 * подпиской и «Позже», лайки/дизлайки (только счётчики), «Поделиться», «Скачать»,
 * вкладки «Похожие» и «Комментарии», переход на канал и продолжение просмотра с
 * сохранённой позиции.
 */
public class ModernPlayerActivity extends Activity {
    private static final String QUALITY_DEFAULT = "360";
    private static final String ICON_PLAY = "\u25B6";
    private static final String ICON_PAUSE = "\u275A\u275A";
    private static final int TAB_COLOR_ACTIVE = 0xFFFFFFFF;
    private static final int TAB_COLOR_INACTIVE = 0xFF9AA0A6;

    private Video video;
    private String videoId;
    private String quality = QUALITY_DEFAULT;
    private String videoUrl;
    private String videoTitle;
    private int videoPosition = 0;
    private boolean videoPrepared = false;
    private boolean playWhenReady = true;
    private boolean fullscreen = false;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private TextureVideoView videoView;
    private TextView playbackInfoView;
    private Manager.InstanceInfo selectedInstance;
    private final StreamResolver resolver = new StreamResolver(new ManagerStreamProvider());
    private ImageView posterView;
    private TextView posterPlayView;
    private MediaPlayer mediaPlayer;
    private FrameLayout videoFrame;
    private int videoFrameHeight;
    private LinearLayout topBar;
    private ScrollView scrollView;
    private ProgressBar loading;
    private TextView titleView;
    private TextView viewsView;
    private TextView channelView;
    private TextView subscribersView;
    private ImageView channelAvatar;
    private TextView descriptionView;
    private TextView qualityView;
    private TextView playView;
    private TextView speedView;
    private TextView statusView;
    private TextView subscribeView;
    private TextView laterView;
    private TextView likeView;
    private TextView relatedTab;
    private TextView commentsTab;
    private LinearLayout relatedSection;
    private LinearLayout commentsSection;
    private ProgressBar relatedLoading;
    private ProgressBar commentsLoading;
    private TextView noRelatedView;
    private TextView noCommentsView;
    private AdapterLinearLayout relatedList;
    private AdapterLinearLayout commentsList;

    private final List<VideoInfo> relatedVideos = new ArrayList<VideoInfo>();
    private final List<Comment> comments = new ArrayList<Comment>();
    private VideoAdapter relatedAdapter;
    private CommentAdapter commentsAdapter;

    private boolean relatedLoaded = false;
    private boolean commentsLoaded = false;
    private boolean showingComments = false;
    private int streamRetryCount = 0;
    private boolean activityStopped = false;

    private DownloadLauncher downloadLauncher;
    private LoadVideoTask loadVideoTask;
    private StreamTask streamTask;
    private RelatedTask relatedTask;
    private CommentsTask commentsTask;

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
        if (videoId == null || videoId.length() == 0) {
            Toast.makeText(this, R.string.content_unavailable, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!Manager.getInstance().hasMetadataInstances()) {
            Toast.makeText(this, R.string.metadata_fail, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Config config = ConfigManager.getInstance().getConfig();
        quality = config.getPreferredQuality();
        if (quality == null || quality.length() == 0) {
            quality = QUALITY_DEFAULT;
        }

        bindViews();
        setupAdapters();
        setupListeners();
        setupVideoListeners();
        updateQualityLabel();
        updateSpeedLabel();
        if (config.isConvertVideos()) {
            qualityView.setVisibility(View.GONE);
        }

        loadVideoTask = new LoadVideoTask();
        loadVideoTask.execute(videoId);
    }

    private void bindViews() {
        videoFrame = (FrameLayout) findViewById(R.id.modern_player_video_frame);
        topBar = (LinearLayout) findViewById(R.id.modern_player_top);
        scrollView = (ScrollView) findViewById(R.id.modern_player_scroll);
        videoView = (TextureVideoView) findViewById(R.id.modern_player_video);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        videoFrameHeight = Math.min((int) (metrics.widthPixels * 9.0f / 16.0f),
                (int) (metrics.heightPixels * 0.45f));
        LinearLayout.LayoutParams frameParams = (LinearLayout.LayoutParams) videoFrame.getLayoutParams();
        frameParams.height = videoFrameHeight;
        videoFrame.setLayoutParams(frameParams);
        loading = (ProgressBar) findViewById(R.id.modern_player_loading);
        playbackInfoView = (TextView) findViewById(R.id.modern_player_playback_info);
        posterView = (ImageView) findViewById(R.id.modern_player_poster);
        posterPlayView = (TextView) findViewById(R.id.modern_player_poster_play);
        titleView = (TextView) findViewById(R.id.modern_player_title);
        viewsView = (TextView) findViewById(R.id.modern_player_views);
        channelView = (TextView) findViewById(R.id.modern_player_channel);
        subscribersView = (TextView) findViewById(R.id.modern_player_subscribers);
        channelAvatar = (ImageView) findViewById(R.id.modern_player_channel_avatar);
        descriptionView = (TextView) findViewById(R.id.modern_player_description);
        qualityView = (TextView) findViewById(R.id.modern_player_quality);
        playView = (TextView) findViewById(R.id.modern_player_play);
        speedView = (TextView) findViewById(R.id.modern_player_speed);
        statusView = (TextView) findViewById(R.id.modern_player_status);
        subscribeView = (TextView) findViewById(R.id.modern_player_subscribe);
        laterView = (TextView) findViewById(R.id.modern_player_later);
        likeView = (TextView) findViewById(R.id.modern_player_like);
        relatedTab = (TextView) findViewById(R.id.modern_player_tab_related);
        commentsTab = (TextView) findViewById(R.id.modern_player_tab_comments);
        relatedSection = (LinearLayout) findViewById(R.id.modern_player_related_section);
        commentsSection = (LinearLayout) findViewById(R.id.modern_player_comments_section);
        relatedLoading = (ProgressBar) findViewById(R.id.modern_player_related_loading);
        commentsLoading = (ProgressBar) findViewById(R.id.modern_player_comments_loading);
        noRelatedView = (TextView) findViewById(R.id.modern_player_no_related);
        noCommentsView = (TextView) findViewById(R.id.modern_player_no_comments);
        relatedList = (AdapterLinearLayout) findViewById(R.id.modern_player_related_list);
        commentsList = (AdapterLinearLayout) findViewById(R.id.modern_player_comments_list);

        if (!PlaybackSpeed.isSupported(NotPipe.SDK)) {
            speedView.setVisibility(View.GONE);
        }
    }

    private void setupAdapters() {
        relatedAdapter = new VideoAdapter(this, R.layout.item_modern_video, relatedVideos);
        relatedAdapter.setChannelIconListener(new VideoAdapter.ChannelIconListener() {
            @Override
            public String getResolvedIcon(String channelId) {
                return ChannelIconResolver.getResolved(channelId);
            }

            @Override
            public void onRequestFallbackIcon(final String channelId) {
                ChannelIconResolver.requestFallback(channelId, new ChannelIconResolver.OnIconResolvedListener() {
                    @Override
                    public void onIconResolved(String resolvedId, String url) {
                        if (url != null && url.length() > 0) {
                            loadRelatedImages();
                        }
                    }
                });
            }
        });
        relatedList.setAdapter(relatedAdapter);
        relatedList.setOnItemClickListener(new AdapterLinearLayout.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterLinearLayout parent, View view, int position, long id) {
                openRelatedVideo(position);
            }
        });

        commentsAdapter = new CommentAdapter(this, R.layout.item_comment, comments);
        commentsList.setAdapter(commentsAdapter);
    }

    private void setupListeners() {
        findViewById(R.id.modern_player_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        playbackInfoView.setText(getString(R.string.playback_via, getString(R.string.loading_)));
        playbackInfoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstanceChooser();
            }
        });
        playView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (posterPlayView.getVisibility() == View.VISIBLE) {
                    startFromPoster();
                } else {
                    togglePlay();
                }
            }
        });
        posterPlayView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFromPoster();
            }
        });
        posterView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFromPoster();
            }
        });
        findViewById(R.id.modern_player_back10).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBy(-10000);
            }
        });
        findViewById(R.id.modern_player_forward10).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBy(10000);
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
        speedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedDialog();
            }
        });
        likeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLikesFeedback();
            }
        });
        findViewById(R.id.modern_player_dislike).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ModernPlayerActivity.this, R.string.modern_player_dislike_toast,
                        Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.modern_player_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareVideo();
            }
        });
        findViewById(R.id.modern_player_download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDownloadFlow();
            }
        });
        laterView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWatchLater();
            }
        });
        subscribeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSubscribe();
            }
        });
        findViewById(R.id.modern_player_channel_row).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openChannel();
            }
        });
        descriptionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDescriptionDialog();
            }
        });
        relatedTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRelatedTab();
            }
        });
        commentsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCommentsTab();
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
                        seekBy(dx > 0 ? 10000 : -10000);
                        Toast.makeText(ModernPlayerActivity.this, dx > 0 ? "+10 сек" : "-10 сек",
                                Toast.LENGTH_SHORT).show();
                    } else if (Math.abs(dy) > 80) {
                        adjustGesture(dy, event.getX() > v.getWidth() / 2);
                    } else {
                        togglePlay();
                    }
                }
                return true;
            }
        });
    }

    private void setupVideoListeners() {
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                onVideoPrepared(mp);
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                videoPrepared = false;
                videoPosition = 0;
                mediaPlayer = null;
                playWhenReady = false;
                playView.setText(ICON_PLAY);
                PlaybackPositionsStore.remove(ModernPlayerActivity.this, videoId);
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                videoPrepared = false;
                mediaPlayer = null;
                playView.setText(ICON_PLAY);
                if (streamRetryCount < 1 && videoId != null) {
                    streamRetryCount++;
                    loading.setVisibility(View.VISIBLE);
                    streamTask = new StreamTask();
                    streamTask.execute(videoId, quality);
                    return true;
                }
                showError(R.string.error_playback_generic);
                return true;
            }
        });
    }

    /**
     * Действия из меню «Качество».
     */
    private void showQualityDialog() {
        if (video == null) {
            Toast.makeText(this, R.string.error_wait_loading, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] display = getResources().getStringArray(R.array.qualities);
        final String[] values = new String[display.length];
        int checked = 0;
        for (int i = 0; i < display.length; i++) {
            values[i] = display[i].replace("p", "");
            if (values[i].equals(quality)) {
                checked = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.modern_player_quality_dialog)
                .setSingleChoiceItems(display, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        final String selected = values[which];
                        if (selected.equals(quality)) {
                            return;
                        }
                        if ("1080".equals(selected)) {
                            new AlertDialog.Builder(ModernPlayerActivity.this)
                                    .setTitle(R.string.modern_player_quality_dialog)
                                    .setMessage(R.string.experimental_1080)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int w) {
                                            changeQuality(selected);
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        } else {
                            changeQuality(selected);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Переключает качество, сохраняет текущую позицию и запускает поиск нового потока.
     */
    private void changeQuality(String newQuality) {
        videoPosition = getCurrentPosition();
        quality = newQuality;
        updateQualityLabel();

        Config config = ConfigManager.getInstance().getConfig();
        config.setPreferredQuality(newQuality);
        ConfigManager.getInstance().saveConfig(config);

        if (streamTask != null) {
            streamTask.cancel(true);
        }

        if (!config.isConvertVideos() && video != null && QUALITY_DEFAULT.equals(newQuality)
                && video.videoUrl != null && video.videoUrl.length() > 0) {
            playUrl(video.videoUrl);
            return;
        }

        streamTask = new StreamTask();
        streamTask.execute(videoId, newQuality);
    }

    private void showSpeedDialog() {
        if (!PlaybackSpeed.isSupported(NotPipe.SDK)) {
            Toast.makeText(this, R.string.modern_player_speed_unsupported, Toast.LENGTH_LONG).show();
            return;
        }

        final String[] labels = getResources().getStringArray(R.array.playback_speeds);
        int checked = PlaybackSpeed.indexOf(
                ConfigManager.getInstance().getConfig().getPlaybackSpeed());

        new AlertDialog.Builder(this)
                .setTitle(R.string.playback_speed)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        float speed = PlaybackSpeed.getSpeed(which);
                        Config speedConfig = ConfigManager.getInstance().getConfig();
                        speedConfig.setPlaybackSpeed(speed);
                        ConfigManager.getInstance().saveConfig(speedConfig);
                        updateSpeedLabel();
                        applyPlaybackSpeed(true);
                        Toast.makeText(ModernPlayerActivity.this,
                                getString(R.string.modern_player_speed_selected, labels[which]),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateQualityLabel() {
        if (quality == null || quality.length() == 0) {
            quality = QUALITY_DEFAULT;
        }
        if (qualityView != null) {
            qualityView.setText(quality + "p");
        }
    }

    private void updateSpeedLabel() {
        if (speedView == null) return;
        String[] labels = getResources().getStringArray(R.array.playback_speeds);
        int index = PlaybackSpeed.indexOf(ConfigManager.getInstance().getConfig().getPlaybackSpeed());
        if (index >= 0 && index < labels.length) {
            speedView.setText(labels[index]);
        } else {
            speedView.setText("1×");
        }
    }

    /**
     * Применяет выбранную скорость. MediaPlayer.setPlaybackParams() появился только в
     * Android 6.0, поэтому вызывается через reflection и молча игнорируется на старых версиях.
     *
     * @param notify true, чтобы сообщать пользователю о неподдерживаемом устройстве
     */
    private void applyPlaybackSpeed(boolean notify) {
        if (!PlaybackSpeed.isSupported(NotPipe.SDK)) {
            if (notify) {
                Toast.makeText(this, R.string.modern_player_speed_unsupported, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (mediaPlayer == null) {
            if (notify) {
                Toast.makeText(this, R.string.error_wait_loading, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final float speed = PlaybackSpeed.normalize(
                ConfigManager.getInstance().getConfig().getPlaybackSpeed());
        if (!notify && speed == PlaybackSpeed.DEFAULT) {
            return;
        }

        boolean wasPlaying = false;
        try {
            wasPlaying = mediaPlayer.isPlaying();
        } catch (Exception ignored) {
        }

        try {
            Class<?> paramsClass = Class.forName("android.media.PlaybackParams");
            Object params = paramsClass.getConstructor().newInstance();
            paramsClass.getMethod("setSpeed", float.class).invoke(params, Float.valueOf(speed));
            MediaPlayer.class.getMethod("setPlaybackParams", paramsClass).invoke(mediaPlayer, params);
        } catch (Exception e) {
            e.printStackTrace();
            if (notify) {
                Toast.makeText(this, R.string.modern_player_speed_unsupported, Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (!wasPlaying) {
            try {
                mediaPlayer.pause();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Обновляет интерфейс после загрузки метаданных.
     */
    private void onVideoLoaded(Video fetchedVideo) {
        video = fetchedVideo;
        videoTitle = video.title;

        HistoryStore.add(this, video);
        if (video.channelId != null && video.channelThumbnail != null
                && video.channelThumbnail.length() > 0) {
            ChannelIconResolver.setResolved(video.channelId, video.channelThumbnail);
        }

        populateMeta();
        playbackInfoView.setText(getString(R.string.playback_via, selectedInstance != null ? selectedInstance.host : "Авто"));
        if (video.thumbnail != null && video.thumbnail.length() > 0) {
            ImageLoader.loadImage(video.thumbnail, posterView);
            posterView.setVisibility(View.VISIBLE);
            posterPlayView.setVisibility(View.VISIBLE);
        }
        restoreSavedPosition();
        updateQualityLabel();
        updateLikeLabel();
        updateLaterButton();
        updateSubscribeButton();
        loadRelated();
        if (showingComments) {
            loadComments();
        }
    }

    private void populateMeta() {
        titleView.setText(video.title);

        String viewsText = getString(R.string.modern_player_views,
                Utils.formatNumber(this, video.views));
        if (video.publishedAt != null) {
            viewsText = viewsText + "   " + Utils.formatTimeAgo(this, video.publishedAt);
        }
        viewsView.setText(viewsText);

        channelView.setText(video.channel == null ? "" : video.channel);
        if (video.subscribers > 0) {
            subscribersView.setText(getString(R.string.modern_player_subscribers,
                    Utils.formatNumber(this, video.subscribers)));
            subscribersView.setVisibility(View.VISIBLE);
        } else {
            subscribersView.setVisibility(View.GONE);
        }

        if (video.description != null && video.description.length() > 0) {
            descriptionView.setText(video.description);
        } else {
            descriptionView.setText(R.string.modern_player_no_description);
        }

        loadChannelAvatar();
    }

    private void loadChannelAvatar() {
        if (video == null || video.channelId == null || video.channelId.length() == 0) {
            return;
        }
        String url = video.channelThumbnail;
        if (url == null || url.length() == 0) {
            String resolved = ChannelIconResolver.getResolved(video.channelId);
            if (resolved != null && !ChannelIconResolver.FAILED.equals(resolved)) {
                url = resolved;
            }
        }
        if (url != null && url.length() > 0) {
            ImageLoader.loadImage(url, channelAvatar, false);
            return;
        }
        ChannelIconResolver.requestFallback(video.channelId,
                new ChannelIconResolver.OnIconResolvedListener() {
                    @Override
                    public void onIconResolved(String channelId, String iconUrl) {
                        if (iconUrl != null && iconUrl.length() > 0) {
                            ImageLoader.loadImage(iconUrl, channelAvatar, true);
                        }
                    }
                });
    }

    private void updateLikeLabel() {
        if (likeView == null) return;
        if (video != null && video.likes > 0) {
            likeView.setText(getString(R.string.modern_player_like) + "   "
                    + Utils.formatNumber(this, video.likes));
        } else {
            likeView.setText(R.string.modern_player_like);
        }
    }

    private void showLikesFeedback() {
        if (video != null && video.likes > 0) {
            Toast.makeText(this, getString(R.string.modern_player_like_toast,
                    Utils.formatNumber(this, video.likes)), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.modern_player_like_toast_empty, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSubscribeButton() {
        if (subscribeView == null) return;
        if (video == null || video.channelId == null || video.channelId.length() == 0) {
            subscribeView.setVisibility(View.GONE);
            return;
        }
        boolean subscribed = SubscriptionsStore.isSubscribed(this, video.channelId);
        subscribeView.setText(subscribed
                ? R.string.modern_player_subscribed : R.string.modern_player_subscribe);
        subscribeView.setTextColor(subscribed ? 0xFFE53935 : 0xFFFFFFFF);
        subscribeView.setVisibility(View.VISIBLE);
    }

    private void toggleSubscribe() {
        if (video == null || video.channelId == null || video.channelId.length() == 0) {
            Toast.makeText(this, R.string.modern_player_channel_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (SubscriptionsStore.isSubscribed(this, video.channelId)) {
            SubscriptionsStore.remove(this, video.channelId);
            Toast.makeText(this, getString(R.string.modern_player_unsubscribed_toast, channelName()),
                    Toast.LENGTH_SHORT).show();
        } else {
            SubscriptionsStore.add(this, video.channelId, video.channel, video.channelThumbnail);
            Toast.makeText(this, getString(R.string.modern_player_subscribed_toast, channelName()),
                    Toast.LENGTH_SHORT).show();
        }
        updateSubscribeButton();
    }

    private String channelName() {
        if (video == null || video.channel == null) return "";
        return video.channel;
    }

    private void updateLaterButton() {
        if (laterView == null) return;
        boolean saved = WatchLaterStore.contains(this, videoId);
        laterView.setText(saved
                ? R.string.modern_player_later_remove : R.string.modern_player_later_add);
        laterView.setTextColor(saved ? 0xFFE53935 : 0xFFDDDDDD);
    }

    private void toggleWatchLater() {
        if (video == null) {
            Toast.makeText(this, R.string.error_wait_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        if (WatchLaterStore.contains(this, videoId)) {
            WatchLaterStore.remove(this, videoId);
            Toast.makeText(this, R.string.removed_watch_later, Toast.LENGTH_SHORT).show();
        } else {
            WatchLaterStore.add(this, video);
            Toast.makeText(this, R.string.added_watch_later, Toast.LENGTH_SHORT).show();
        }
        updateLaterButton();
    }

    private void shareVideo() {
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, "https://youtu.be/" + videoId);
            if (videoTitle != null && videoTitle.length() > 0) {
                send.putExtra(Intent.EXTRA_SUBJECT, videoTitle);
            }
            startActivity(Intent.createChooser(send, getString(R.string.modern_player_share)));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void startDownloadFlow() {
        if (video == null) {
            Toast.makeText(this, R.string.metadata_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        downloadLauncher = new DownloadLauncher(this, videoId, video);
        downloadLauncher.start();
    }

    /**
     * Android 6.0+ спрашивает разрешение на запись во время загрузки; старые версии этот
     * метод не вызывают, поэтому приложение остаётся загружаемым на Android 1.5.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (downloadLauncher != null && downloadLauncher.handlePermissions(requestCode, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void openChannel() {
        if (video == null || video.channelId == null || video.channelId.length() == 0) {
            Toast.makeText(this, R.string.modern_player_channel_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ModernChannelActivity.class);
        intent.putExtra("ID", video.channelId);
        startActivity(intent);
    }

    private void showDescriptionDialog() {
        if (video == null || video.description == null || video.description.length() == 0) {
            return;
        }
        TextView text = new TextView(this);
        text.setText(video.description);
        text.setTextSize(14);
        text.setPadding(16, 16, 16, 16);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);

        new AlertDialog.Builder(this)
                .setTitle(R.string.desc)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void openRelatedVideo(int position) {
        if (position < 0 || position >= relatedVideos.size()) {
            return;
        }
        VideoInfo item = relatedVideos.get(position);
        if (item == null || item.id == null || item.id.length() == 0) {
            return;
        }
        Intent intent = new Intent(this, ModernPlayerActivity.class);
        intent.putExtra("ID", item.id);
        startActivity(intent);
    }

    private void showRelatedTab() {
        showingComments = false;
        relatedSection.setVisibility(View.VISIBLE);
        commentsSection.setVisibility(View.GONE);
        relatedTab.setTextColor(TAB_COLOR_ACTIVE);
        commentsTab.setTextColor(TAB_COLOR_INACTIVE);
    }

    private void showCommentsTab() {
        showingComments = true;
        relatedSection.setVisibility(View.GONE);
        commentsSection.setVisibility(View.VISIBLE);
        relatedTab.setTextColor(TAB_COLOR_INACTIVE);
        commentsTab.setTextColor(TAB_COLOR_ACTIVE);
        loadComments();
    }

    private void loadRelated() {
        if (relatedLoaded) return;
        if (relatedTask != null && relatedTask.getStatus() != AsyncTask.Status.FINISHED) return;
        relatedLoading.setVisibility(View.VISIBLE);
        noRelatedView.setVisibility(View.GONE);

        if (video != null && video.related != null && !video.related.isEmpty()) {
            setRelatedVideos(video.related);
            return;
        }
        relatedTask = new RelatedTask();
        relatedTask.execute(videoId);
    }

    private void setRelatedVideos(List<VideoInfo> items) {
        relatedVideos.clear();
        if (items != null) {
            relatedVideos.addAll(items);
        }
        relatedAdapter.notifyDataSetChanged();
        relatedLoaded = true;
        relatedLoading.setVisibility(View.GONE);
        noRelatedView.setVisibility(relatedVideos.isEmpty() ? View.VISIBLE : View.GONE);
        loadRelatedImages();
    }

    private void loadRelatedImages() {
        if (relatedAdapter == null || relatedList == null) return;
        for (int i = 0; i < relatedList.getChildCount(); i++) {
            relatedAdapter.loadImagesForView(relatedList.getChildAt(i));
        }
    }

    private void loadComments() {
        if (commentsLoaded) return;
        if (commentsTask != null && commentsTask.getStatus() != AsyncTask.Status.FINISHED) return;
        commentsLoading.setVisibility(View.VISIBLE);
        noCommentsView.setVisibility(View.GONE);

        if (video != null && video.comments != null && !video.comments.isEmpty()) {
            setComments(video.comments);
            return;
        }
        commentsTask = new CommentsTask();
        commentsTask.execute(videoId);
    }

    private void setComments(List<Comment> items) {
        comments.clear();
        if (items != null) {
            comments.addAll(items);
        }
        commentsAdapter.notifyDataSetChanged();
        commentsLoaded = true;
        commentsLoading.setVisibility(View.GONE);
        noCommentsView.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
        loadCommentImages();
    }

    private void loadCommentImages() {
        if (commentsAdapter == null || commentsList == null) return;
        for (int i = 0; i < commentsList.getChildCount(); i++) {
            commentsAdapter.loadImagesForView(commentsList.getChildAt(i));
        }
    }

    /**
     * Сохранённая позиция просмотра: предлагаем продолжить с прошлого места.
     */
    private void restoreSavedPosition() {
        if (video == null || video.length <= 0) return;
        PlaybackPositionsStore.Entry saved = PlaybackPositionsStore.get(this, videoId);
        if (PlaybackPositionsStore.shouldResume(saved, video.length * 1000)) {
            videoPosition = saved.position;
            Toast.makeText(this, getString(R.string.modern_player_resume,
                    Utils.formatDuration(saved.position / 1000)), Toast.LENGTH_SHORT).show();
        }
    }

    private void savePosition() {
        if (!videoPrepared || video == null || videoId == null) return;
        try {
            int position = videoView.getCurrentPosition();
            if (position > 0) {
                videoPosition = position;
            }
            PlaybackPositionsStore.save(this, videoId, videoPosition, video.length * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int getCurrentPosition() {
        if (videoPrepared) {
            try {
                return videoView.getCurrentPosition();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return videoPosition;
    }

    private void startPlayback() {
        if (video == null) return;
        statusView.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        Config config = ConfigManager.getInstance().getConfig();
        streamTask = new StreamTask();
        streamTask.execute(videoId, quality);
    }

    private void openLegacyPlayer() {
        if (videoId == null) return;
        try {
            Intent intent = new Intent(this, VideoActivity.class);
            intent.putExtra("ID", videoId);
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.error_playback_generic, Toast.LENGTH_LONG).show();
        }
    }

    private void playUrl(String url) {
        if (url == null || url.length() == 0) {
            showError(R.string.error_playback_generic);
            return;
        }
        if (activityStopped) {
            videoUrl = url;
            return;
        }
        int position = getCurrentPosition();
        if (position > 0) {
            videoPosition = position;
        }
        videoUrl = url;
        Log.d("ModernPlayer", "playUrl " + url + " length=" + url.length());
        videoPrepared = false;
        mediaPlayer = null;
        statusView.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);
        playView.setText(ICON_PLAY);
        try {
            videoView.setVideoURI(Uri.parse(url));
            videoView.requestFocus();
        } catch (Exception e) {
            e.printStackTrace();
            showError(R.string.error_playback_generic);
        }
    }

    private void onVideoPrepared(MediaPlayer mp) {
        if (activityStopped) {
            return;
        }
        mediaPlayer = mp;
        videoPrepared = true;
        streamRetryCount = 0;
        videoWidth = mp.getVideoWidth();
        videoHeight = mp.getVideoHeight();
        Log.d("ModernPlayer", "prepared w=" + videoWidth + " h=" + videoHeight + " dur=" + mp.getDuration());

        if (videoWidth > 0 && videoHeight > 0) {
        }
        loading.setVisibility(View.GONE);
        statusView.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (videoPosition > 0) {
            videoView.seekTo(videoPosition);
        }
        if (playWhenReady) {
            videoView.start();
            playView.setText(ICON_PAUSE);
        } else {
            playView.setText(ICON_PLAY);
        }
        applyPlaybackSpeed(false);
    }

    private void showInstanceChooser() {
        final List<Manager.InstanceInfo> instances = Manager.getInstance().videoInstancesInfo();
        if (instances.isEmpty()) {
            Toast.makeText(this, R.string.error_all_instances, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[instances.size()];
        for (int i = 0; i < instances.size(); i++) {
            Manager.InstanceInfo info = instances.get(i);
            labels[i] = info.name + "\n" + info.host;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_ins)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedInstance = instances.get(which);
                        playbackInfoView.setText(getString(R.string.playback_via, selectedInstance.host));
                        videoPosition = getCurrentPosition();
                        startPlayback();
                    }
                }).show();
    }

    private void startFromPoster() {
        posterView.setVisibility(View.GONE);
        posterPlayView.setVisibility(View.GONE);
        playWhenReady = true;
        startPlayback();
    }

    private void togglePlay() {
        if (!videoPrepared) {
            playWhenReady = true;
            startPlayback();
            return;
        }
        try {
            if (videoView.isPlaying()) {
                videoView.pause();
                playWhenReady = false;
                playView.setText(ICON_PLAY);
            } else {
                videoView.start();
                playWhenReady = true;
                playView.setText(ICON_PAUSE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void togglePlayInternal() {
        try {
            if (videoView.isPlaying()) {
                videoView.pause();
                playWhenReady = false;
                playView.setText(ICON_PLAY);
            } else {
                videoView.start();
                playWhenReady = true;
                playView.setText(ICON_PAUSE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void seekBy(int deltaMs) {
        int position = Math.max(0, getCurrentPosition() + deltaMs);
        videoPosition = position;
        try {
            videoView.seekTo(position);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            params.height = videoFrameHeight;
            params.weight = 0;
            topBar.setVisibility(View.VISIBLE);
            scrollView.setVisibility(View.VISIBLE);
            if (statusView.getText() != null && statusView.getText().length() > 0) {
                statusView.setVisibility(View.VISIBLE);
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (NotPipe.SDK >= 11) {
                getWindow().getDecorView().setSystemUiVisibility(0);
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        videoFrame.setLayoutParams(params);
        if (videoWidth > 0 && videoHeight > 0) {
        }
        videoView.requestLayout();
        videoView.invalidate();
    }

    private void adjustGesture(float dy, boolean rightSide) {
        if (rightSide) {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio == null) return;
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

    private void showError(int messageRes) {
        loading.setVisibility(View.GONE);
        statusView.setText(messageRes);
        statusView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoView == null) return;
        if (videoWidth > 0 && videoHeight > 0) {
        }
        videoView.requestLayout();
        videoView.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            if (videoPrepared && videoView.isPlaying()) {
                videoView.pause();
                playView.setText(ICON_PLAY);
            }
        } catch (Exception ignored) {
        }
        savePosition();
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStopped = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        activityStopped = true;
        savePosition();
        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }
        videoPrepared = false;
        mediaPlayer = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLaterButton();
        updateSubscribeButton();
        if (videoUrl != null && !videoPrepared
                && (streamTask == null || streamTask.getStatus() == AsyncTask.Status.FINISHED)) {
            playUrl(videoUrl);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        savePosition();
        if (loadVideoTask != null) loadVideoTask.cancel(true);
        if (streamTask != null) streamTask.cancel(true);
        if (relatedTask != null) relatedTask.cancel(true);
        if (commentsTask != null) commentsTask.cancel(true);
        try {
            videoView.stopPlayback();
        } catch (Exception ignored) {
        }
        mediaPlayer = null;
        ImageLoader.clearCache();
    }

    private class LoadVideoTask extends AsyncTask<String, Void, Video> {
        @Override
        protected Video doInBackground(String... params) {
            try {
                return Manager.getInstance().getVideo(params[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Video result) {
            loading.setVisibility(View.GONE);
            if (result == null) {
                showError(R.string.error_all_instances);
                return;
            }
            onVideoLoaded(result);
        }
    }

    private class StreamTask extends AsyncTask<String, Void, String> {
        private String requestedQuality;

        @Override
        protected void onPreExecute() {
            loading.setVisibility(View.VISIBLE);
            statusView.setVisibility(View.GONE);
        }

        @Override
        protected String doInBackground(String... params) {
            requestedQuality = params.length > 1 ? params[1] : QUALITY_DEFAULT;
            try {
                Config config = ConfigManager.getInstance().getConfig();
                int timeout = PlaybackPolicy.timeoutForQuality(
                        video != null ? video.length : -1, requestedQuality);
                if (config.isConvertVideos()) {
                    return Manager.getInstance().getConvUrl(params[0], requestedQuality,
                            config.getConvertCodec());
                }
                StreamResolver.Result result = resolver.resolve(params[0], requestedQuality, timeout,
                        config.isConvertVideos(), config.getConvertCodec(),
                        selectedInstance != null ? selectedInstance.instance : null, null, null);
                return result.url;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String url) {
            if (url == null) {
                showError(QUALITY_DEFAULT.equals(requestedQuality)
                        ? R.string.error_all_instances : R.string.modern_player_error_no_quality);
                return;
            }
            playUrl(url);
        }
    }

    private class RelatedTask extends AsyncTask<String, Void, List<VideoInfo>> {
        @Override
        protected List<VideoInfo> doInBackground(String... params) {
            try {
                return Manager.getInstance().getMetadata().getRelated(params[0]);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<VideoInfo> result) {
            relatedLoading.setVisibility(View.GONE);
            if (result == null) {
                noRelatedView.setVisibility(View.VISIBLE);
                return;
            }
            setRelatedVideos(result);
        }
    }

    private class CommentsTask extends AsyncTask<String, Void, List<Comment>> {
        @Override
        protected List<Comment> doInBackground(String... params) {
            List<Comment> result = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    result = Manager.getInstance().getMetadata().getComments(params[0]);
                    if (result != null && !result.isEmpty()) return result;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            List<Manager.InstanceInfo> instances = Manager.getInstance().allInstancesInfo();
            for (int i = 0; i < instances.size(); i++) {
                Manager.InstanceInfo info = instances.get(i);
                if (!info.supportsMetadata || !(info.instance instanceof Metadata)) continue;
                try {
                    result = ((Metadata) info.instance).getComments(params[0]);
                    if (result != null && !result.isEmpty()) return result;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<Comment> result) {
            commentsLoading.setVisibility(View.GONE);
            if (result == null) {
                noCommentsView.setVisibility(View.VISIBLE);
                return;
            }
            setComments(result);
        }
    }
}
