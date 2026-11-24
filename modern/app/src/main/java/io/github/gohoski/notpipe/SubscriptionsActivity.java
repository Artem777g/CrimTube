package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.data.Channel;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.ImageLoader;
import io.github.gohoski.notpipe.util.SubscriptionsStore;
import io.github.gohoski.notpipe.util.SubscriptionsStore.SubscribedChannel;

/**
 * Created on 29.10.2026.
 * Local subscriptions: a feed of the latest videos from subscribed channels plus the
 * channel list itself. Everything is stored on the device, no account is used.
 */
public class SubscriptionsActivity extends Activity {
    private static final int MODE_FEED = 0;
    private static final int MODE_CHANNELS = 1;
    /** How many channels are asked for new videos in one refresh. */
    private static final int MAX_FEED_CHANNELS = 12;
    /** Upper bound for the merged feed. */
    private static final int MAX_FEED_ITEMS = 120;

    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private final List<SubscribedChannel> channels = new ArrayList<SubscribedChannel>();

    private int mode = MODE_FEED;
    private int feedChannelCount = -1;
    private ListView listView;
    private TextView emptyView;
    private TextView progressView;
    private VideoAdapter videoAdapter;
    private SubscriptionAdapter channelAdapter;
    private LoadFeedTask feedTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscriptions);

        listView = (ListView) findViewById(R.id.subscriptions_list);
        emptyView = (TextView) findViewById(R.id.subscriptions_empty);
        progressView = (TextView) findViewById(R.id.subscriptions_progress);
        listView.setEmptyView(emptyView);

        videoAdapter = new VideoAdapter(this, R.layout.item_video_row, videos);
        channelAdapter = new SubscriptionAdapter();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mode == MODE_FEED) {
                    if (position < 0 || position >= videos.size()) return;
                    Intent intent = new Intent(SubscriptionsActivity.this, VideoActivity.class);
                    intent.putExtra("ID", videos.get(position).id);
                    startActivity(intent);
                } else {
                    if (position < 0 || position >= channels.size()) return;
                    Intent intent = new Intent(SubscriptionsActivity.this, ChannelActivity.class);
                    intent.putExtra("ID", channels.get(position).id);
                    startActivity(intent);
                }
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (mode == MODE_FEED) {
                    if (position < 0 || position >= videos.size()) return false;
                    String channelId = videos.get(position).channelId;
                    if (channelId != null && channelId.length() > 0) {
                        Intent intent = new Intent(SubscriptionsActivity.this, ChannelActivity.class);
                        intent.putExtra("ID", channelId);
                        startActivity(intent);
                        return true;
                    }
                    return false;
                }
                if (position >= 0 && position < channels.size()) {
                    confirmUnsubscribe(channels.get(position));
                    return true;
                }
                return false;
            }
        });

        applyMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int before = channels.size();
        reloadChannels();
        if (mode == MODE_FEED && (videos.isEmpty() || before != channels.size()
                || feedChannelCount != channels.size())) {
            loadFeed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (feedTask != null) feedTask.cancel(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // Inside the channel list Back returns to the feed instead of leaving the screen.
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0
                && mode == MODE_CHANNELS) {
            mode = MODE_FEED;
            applyMode();
            if (videos.isEmpty() && !channels.isEmpty()) loadFeed();
            if (NotPipe.SDK >= 11) invalidateOptionsMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_subscriptions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem switchItem = menu.findItem(R.id.action_switch_view);
        if (switchItem != null) {
            switchItem.setTitle(mode == MODE_FEED ? R.string.channels : R.string.videos);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_view) {
            mode = mode == MODE_FEED ? MODE_CHANNELS : MODE_FEED;
            applyMode();
            if (mode == MODE_FEED && videos.isEmpty()) loadFeed();
            if (NotPipe.SDK >= 11) invalidateOptionsMenu();
            return true;
        }
        if (item.getItemId() == R.id.action_refresh) {
            reloadChannels();
            if (mode == MODE_FEED) loadFeed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyMode() {
        if (mode == MODE_FEED) {
            listView.setAdapter(videoAdapter);
            emptyView.setText(R.string.no_feed);
        } else {
            listView.setAdapter(channelAdapter);
            emptyView.setText(R.string.no_subscriptions);
        }
    }

    private void reloadChannels() {
        channels.clear();
        channels.addAll(SubscriptionsStore.list(this));
        if (channelAdapter != null) channelAdapter.notifyDataSetChanged();
    }

    private void loadFeed() {
        if (channels.isEmpty()) {
            videos.clear();
            feedChannelCount = 0;
            if (videoAdapter != null) videoAdapter.notifyDataSetChanged();
            return;
        }
        if (feedTask != null) feedTask.cancel(true);
        feedTask = new LoadFeedTask();
        feedTask.execute();
    }

    private void confirmUnsubscribe(final SubscribedChannel channel) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.remove_subscription, channel.title))
                .setPositiveButton(R.string.unsubscribe, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SubscriptionsStore.remove(SubscriptionsActivity.this, channel.id);
                        reloadChannels();
                        videos.clear();
                        if (videoAdapter != null) videoAdapter.notifyDataSetChanged();
                        feedChannelCount = -1;
                        if (mode == MODE_FEED) loadFeed();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Fetches the latest videos of every subscribed channel and merges them into one feed.
     */
    private class LoadFeedTask extends AsyncTask<Void, Integer, List<VideoInfo>> {
        /** Workers fetch different channels at the same time; 2 threads are safer on old devices. */
        private static final int MAX_THREADS = 3;

        private int total;
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicInteger doneCount = new AtomicInteger();
        private ExecutorService pool;

        @Override
        protected void onPreExecute() {
            progressView.setVisibility(View.VISIBLE);
            progressView.setText(R.string.feed_loading_start);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected List<VideoInfo> doInBackground(Void... params) {
            final List<SubscribedChannel> subscriptions = SubscriptionsStore.list(SubscriptionsActivity.this);
            total = Math.min(subscriptions.size(), MAX_FEED_CHANNELS);
            if (total == 0) return new ArrayList<VideoInfo>();

            final List<VideoInfo>[] perChannel = new List[total];
            final CountDownLatch latch = new CountDownLatch(total);

            int threads = NotPipe.SDK >= 11 ? MAX_THREADS : 2;
            if (threads > total) threads = total;
            pool = Executors.newFixedThreadPool(threads);

            long started = System.currentTimeMillis();

            for (int i = 0; i < total; i++) {
                final int index = i;
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (isCancelled()) return;
                            long channelStarted = System.currentTimeMillis();
                            // Each worker needs its own proxy: the Manager keeps a current instance.
                            Metadata api = Manager.getInstance().getMetadata();
                            Channel channel = api.getChannel(subscriptions.get(index).id);
                            if (channel != null) perChannel[index] = channel.videos;
                            android.util.Log.d("notPipe-feed", subscriptions.get(index).id + " in "
                                    + (System.currentTimeMillis() - channelStarted) + " ms");
                        } catch (Exception e) {
                            failures.incrementAndGet();
                            e.printStackTrace();
                        } finally {
                            publishProgress(Integer.valueOf(doneCount.incrementAndGet()));
                            latch.countDown();
                        }
                    }
                });
            }
            pool.shutdown();

            try {
                latch.await();
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }

            android.util.Log.d("notPipe-feed", "loaded " + total + " channels in "
                    + (System.currentTimeMillis() - started) + " ms");

            List<VideoInfo> result = new ArrayList<VideoInfo>();
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < total; i++) {
                List<VideoInfo> channelVideos = perChannel[i];
                if (channelVideos == null) continue;
                String fallbackChannel = subscriptions.get(i).title;
                for (int j = 0; j < channelVideos.size(); j++) {
                    VideoInfo video = channelVideos.get(j);
                    if (video == null || video.id == null) continue;
                    if (video.channel == null || video.channel.length() == 0) {
                        // getChannel() often leaves the per-video channel name empty.
                        video = new VideoInfo(video.id, video.title, video.thumbnail,
                                fallbackChannel, video.channelThumbnail, video.channelId,
                                video.duration, video.views, video.publishedAt);
                    }
                    if (seen.add(video.id)) result.add(video);
                }
            }

            Collections.sort(result, new Comparator<VideoInfo>() {
                @Override
                public int compare(VideoInfo left, VideoInfo right) {
                    long leftTime = left.publishedAt != null ? left.publishedAt.getTime() : 0;
                    long rightTime = right.publishedAt != null ? right.publishedAt.getTime() : 0;
                    return rightTime < leftTime ? -1 : (rightTime > leftTime ? 1 : 0);
                }
            });

            if (result.size() > MAX_FEED_ITEMS) {
                result = new ArrayList<VideoInfo>(result.subList(0, MAX_FEED_ITEMS));
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values.length > 0 && total > 0) {
                progressView.setText(getString(R.string.feed_loading, values[0], total));
            }
        }

        @Override
        protected void onPostExecute(List<VideoInfo> result) {
            progressView.setVisibility(View.GONE);
            if (isCancelled()) return;

            videos.clear();
            videos.addAll(result);
            feedChannelCount = channels.size();
            videoAdapter.notifyDataSetChanged();
            emptyView.setText(R.string.no_feed);
            if (mode == MODE_FEED) listView.setAdapter(videoAdapter);

            if (videos.isEmpty() && failures.get() > 0) {
                Toast.makeText(SubscriptionsActivity.this, R.string.feed_failed,
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            if (pool != null) pool.shutdownNow();
            progressView.setVisibility(View.GONE);
        }
    }

    private class SubscriptionAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return channels.size();
        }

        @Override
        public Object getItem(int position) {
            return channels.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_subscription, parent, false);
            }

            SubscribedChannel channel = channels.get(position);
            TextView title = (TextView) view.findViewById(R.id.subscription_title);
            ImageView avatar = (ImageView) view.findViewById(R.id.subscription_avatar);

            title.setText(channel.title);
            if (channel.avatar != null && channel.avatar.length() > 0) {
                ImageLoader.loadImage(channel.avatar, avatar, false);
            } else {
                avatar.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            return view;
        }
    }
}
