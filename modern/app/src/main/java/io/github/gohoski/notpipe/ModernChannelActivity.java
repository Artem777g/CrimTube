package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.api.ChannelApi;
import io.github.gohoski.notpipe.api.ContentUnavailableException;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.util.ImageLoader;
import io.github.gohoski.notpipe.util.SubscriptionsStore;

/**
 * Created on 25.11.2026.
 * Современный экран канала: шапка с аватаром, названием, счётчиком подписчиков,
 * описанием и кнопкой подписки, под ней — прокручиваемый список видео канала.
 * Принимает ID канала в extra "ID", видео открывает через ModernPlayerActivity.
 */
public class ModernChannelActivity extends Activity {
    private String channelId;
    private Channel channel;
    private Metadata metadata;
    private ChannelApi channelApi;

    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private ChannelVideoAdapter adapter;

    private ListView list;
    private ProgressBar loading;
    private TextView emptyView;
    private View headerView;
    private ImageView avatarView;
    private TextView headerTitle;
    private TextView subscribersView;
    private TextView descriptionView;
    private TextView subscribeButton;

    private boolean destroyed;
    private boolean contentUnavailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_channel);
        if (NotPipe.SDK >= 21) {
            getWindow().setStatusBarColor(0xFF0B0D10);
            getWindow().setNavigationBarColor(0xFF0E1014);
        }

        channelId = getIntent().getStringExtra("ID");
        list = (ListView) findViewById(R.id.modern_channel_list);
        loading = (ProgressBar) findViewById(R.id.modern_channel_loading);
        emptyView = (TextView) findViewById(R.id.modern_channel_empty);

        findViewById(R.id.modern_channel_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setupHeader();

        adapter = new ChannelVideoAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openVideo(position);
            }
        });

        emptyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retry();
            }
        });

        if (channelId == null || channelId.length() == 0) {
            showError(R.string.modern_channel_error);
            return;
        }
        loadChannel();
    }

    /**
     * Collects the views of the channel header. The header itself stays hidden until
     * the channel metadata arrives, so an error state shows only the message.
     */
    private void setupHeader() {
        headerView = findViewById(R.id.modern_channel_header);
        if (headerView == null) {
            return;
        }
        avatarView = (ImageView) headerView.findViewById(R.id.modern_channel_avatar);
        headerTitle = (TextView) headerView.findViewById(R.id.modern_channel_title);
        subscribersView = (TextView) headerView.findViewById(R.id.modern_channel_subscribers);
        descriptionView = (TextView) headerView.findViewById(R.id.modern_channel_description);
        subscribeButton = (TextView) headerView.findViewById(R.id.modern_channel_subscribe);

        subscribeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSubscription();
            }
        });
    }

    /**
     * Requests the channel metadata. Runs on a background thread, video cards are
     * requested right after the header is bound.
     */
    private void loadChannel() {
        loading.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        contentUnavailable = false;
        new LoadChannelTask().execute(channelId);
    }

    /**
     * Requests the channel video list. Existing cards stay on screen while refreshing.
     */
    private void loadVideos() {
        if (channelId == null || channelId.length() == 0) {
            return;
        }
        if (videos.isEmpty()) {
            loading.setVisibility(View.VISIBLE);
        }
        emptyView.setVisibility(View.GONE);
        new LoadVideosTask().execute(channelId);
    }

    /**
     * Retries the failed request: the channel metadata first, then the videos.
     */
    private void retry() {
        if (channel == null) {
            if (channelId != null && channelId.length() > 0) {
                loadChannel();
            }
        } else {
            loadVideos();
        }
    }

    /**
     * Fills the header with the loaded channel data and shows it.
     */
    private void bindChannel(Channel fetched) {
        channel = fetched;
        if (fetched.id != null && fetched.id.length() > 0) {
            channelId = fetched.id;
        }

        String title = fetched.title == null ? "" : fetched.title;
        headerTitle.setText(title);

        if (fetched.subscriberCount > 0) {
            subscribersView.setText(getString(R.string.subscribers,
                    Utils.formatNumber(this, fetched.subscriberCount)));
            subscribersView.setVisibility(View.VISIBLE);
        } else {
            subscribersView.setVisibility(View.GONE);
        }

        if (fetched.description != null && fetched.description.length() > 0) {
            descriptionView.setText(fetched.description);
            descriptionView.setVisibility(View.VISIBLE);
        } else {
            descriptionView.setVisibility(View.GONE);
        }

        avatarView.setImageBitmap(null);
        ImageLoader.loadImage(fetched.thumbnail, avatarView);

        subscribeButton.setVisibility(View.VISIBLE);
        updateSubscribeButton();

        headerView.setVisibility(View.VISIBLE);

        if (fetched.videos != null && !fetched.videos.isEmpty()) {
            videos.clear();
            videos.addAll(fetched.videos);
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Updates the subscribe button label: "Подписаться" or "Вы подписаны".
     */
    private void updateSubscribeButton() {
        if (channel == null || subscribeButton == null) {
            return;
        }
        boolean subscribed = SubscriptionsStore.isSubscribed(this, channel.id);
        subscribeButton.setText(subscribed ? R.string.modern_channel_subscribed : R.string.subscribe);
        subscribeButton.setTextColor(subscribed ? 0xFF9AA0A6 : 0xFFFFFFFF);
    }

    /**
     * Adds or removes the current channel from the local subscriptions list.
     */
    private void toggleSubscription() {
        if (channel == null || channel.id == null || channel.id.length() == 0) {
            Toast.makeText(this, R.string.subscription_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (SubscriptionsStore.isSubscribed(this, channel.id)) {
            SubscriptionsStore.remove(this, channel.id);
            Toast.makeText(this, R.string.unsubscribed, Toast.LENGTH_SHORT).show();
        } else {
            SubscriptionsStore.add(this, channel.id, channel.title, channel.thumbnail);
            Toast.makeText(this, R.string.subscribed, Toast.LENGTH_SHORT).show();
        }
        updateSubscribeButton();
    }

    /**
     * Opens the player for the tapped video card.
     *
     * @param index Position inside the video list
     */
    private void openVideo(int index) {
        if (index < 0 || index >= videos.size()) {
            return;
        }
        VideoInfo video = videos.get(index);
        if (video == null || video.id == null || video.id.length() == 0) {
            return;
        }
        ImageLoader.clearCache();
        System.gc();
        Intent intent = new Intent(ModernChannelActivity.this, ModernPlayerActivity.class);
        intent.putExtra("ID", video.id);
        startActivity(intent);
    }

    private void showError(int messageRes) {
        loading.setVisibility(View.GONE);
        emptyView.setClickable(true);
        emptyView.setText(messageRes);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showEmpty(int messageRes) {
        loading.setVisibility(View.GONE);
        emptyView.setClickable(false);
        emptyView.setText(messageRes);
        emptyView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSubscribeButton();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ImageLoader.clearCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        ImageLoader.clearCache();
    }

    /**
     * Loads the channel metadata through the Manager metadata API.
     */
    private class LoadChannelTask extends AsyncTask<String, Void, Channel> {
        @Override
        protected Channel doInBackground(String... params) {
            if (isCancelled()) {
                return null;
            }
            try {
                Manager manager = Manager.getInstance();
                if (!manager.hasMetadataInstances()) {
                    return null;
                }
                metadata = manager.getMetadata();
                try {
                    channelApi = manager.getChannelApi();
                } catch (IllegalStateException e) {
                    channelApi = null;
                }
                return metadata.getChannel(params[0]);
            } catch (ContentUnavailableException e) {
                e.printStackTrace();
                contentUnavailable = true;
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Channel fetched) {
            if (isCancelled() || destroyed) {
                return;
            }
            loading.setVisibility(View.GONE);
            if (fetched == null) {
                if (contentUnavailable) {
                    Toast.makeText(ModernChannelActivity.this,
                            R.string.content_unavailable, Toast.LENGTH_SHORT).show();
                }
                showError(R.string.modern_channel_error);
                return;
            }
            bindChannel(fetched);
            loadVideos();
        }
    }

    /**
     * Loads channel videos: Metadata.getChannelVideos first, ChannelApi as a fallback.
     */
    private class LoadVideosTask extends AsyncTask<String, Void, List<VideoInfo>> {
        @Override
        protected List<VideoInfo> doInBackground(String... params) {
            if (isCancelled()) {
                return null;
            }
            String id = params[0];
            List<VideoInfo> result = null;

            if (metadata != null) {
                try {
                    result = metadata.getChannelVideos(id, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if ((result == null || result.isEmpty()) && channelApi != null) {
                try {
                    result = channelApi.getChannelVideos(id, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<VideoInfo> result) {
            if (isCancelled() || destroyed) {
                return;
            }
            loading.setVisibility(View.GONE);
            if (result == null) {
                if (videos.isEmpty()) {
                    showError(R.string.modern_channel_videos_error);
                } else {
                    Toast.makeText(ModernChannelActivity.this,
                            R.string.error_all_instances, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            if (!result.isEmpty()) {
                videos.clear();
                videos.addAll(result);
                adapter.notifyDataSetChanged();
            }
            if (videos.isEmpty()) {
                showEmpty(R.string.no_videos);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Adapter of the channel video cards, reuses the shared modern video item layout.
     */
    private class ChannelVideoAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return videos.size();
        }

        @Override
        public Object getItem(int position) {
            return videos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.item_modern_video, parent, false);
            }
            VideoInfo video = videos.get(position);

            ((TextView) row.findViewById(R.id.video_title)).setText(video.title);

            TextView duration = (TextView) row.findViewById(R.id.duration);
            if (video.duration != null && video.duration.length() > 0) {
                duration.setText(video.duration);
                duration.setVisibility(View.VISIBLE);
            } else {
                duration.setVisibility(View.GONE);
            }

            TextView meta = (TextView) row.findViewById(R.id.channel_title);
            if (meta != null) {
                String metaText = "";
                if (video.views > 0) {
                    metaText = getString(R.string.views, Utils.formatNumber(ModernChannelActivity.this, video.views));
                }
                if (video.publishedAt != null) {
                    String timeAgo = Utils.formatTimeAgo(ModernChannelActivity.this, video.publishedAt);
                    metaText = metaText.length() > 0 ? metaText + " \u00B7 " + timeAgo : timeAgo;
                }
                if (metaText.length() > 0) {
                    meta.setText(metaText);
                    meta.setVisibility(View.VISIBLE);
                } else {
                    meta.setVisibility(View.GONE);
                }
            }

            ImageView thumbnail = (ImageView) row.findViewById(R.id.video_thumbnail);
            thumbnail.setImageBitmap(null);
            ImageLoader.loadImage(video.thumbnail, thumbnail);
            return row;
        }
    }
}
