package io.github.gohoski.notpipe.download;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.widget.RemoteViews;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import io.github.gohoski.notpipe.DownloadsActivity;
import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.Utils;
import io.github.gohoski.notpipe.api.ContentUnavailableException;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.VideoTooLongException;

/**
 * Created on 28.10.2026.
 * Background download service: keeps a small queue, survives the video screen being closed and
 * reports progress through a notification and app-local broadcasts.
 * <p>
 * Downloads are retried a few times on network errors, because instances often answer with a
 * timeout on the first try and succeed on the second one.
 */
public class DownloadService extends Service {
    public static final String ACTION_ENQUEUE = "io.github.gohoski.notpipe.download.ENQUEUE";
    public static final String ACTION_CANCEL = "io.github.gohoski.notpipe.download.CANCEL";
    public static final String ACTION_PROGRESS = "io.github.gohoski.notpipe.download.PROGRESS";

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AUDIO = "audio";
    public static final String EXTRA_QUALITY = "quality";
    public static final String EXTRA_LENGTH = "length";

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_PERCENT = "percent";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_ERROR = "error";

    public static final int STATE_RUNNING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_FAILED = 3;
    public static final int STATE_CANCELED = 4;

    private static final int NOTIFICATION_ID = 4801;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY = 1500L;

    private final Object queueLock = new Object();
    private final ArrayList<DownloadRequest> queue = new ArrayList<DownloadRequest>();
    private volatile boolean cancelRequested = false;
    private volatile boolean running = false;
    private volatile DownloadRequest current;
    private Thread worker;
    private long lastPublish;

    /**
     * Adds a download to the queue, starting the service when needed.
     *
     * @param context Any context
     * @param videoId YouTube video ID
     * @param title Video title used for the file name
     * @param audio True to save only the audio track
     * @param quality Requested quality ("360", "480", "720"), may be null for audio
     * @param length Video length in seconds, used to pick a timeout
     */
    public static void enqueue(Context context, String videoId, String title, boolean audio,
                               String quality, int length) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_ENQUEUE);
        intent.putExtra(EXTRA_VIDEO_ID, videoId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_AUDIO, audio);
        intent.putExtra(EXTRA_QUALITY, quality);
        intent.putExtra(EXTRA_LENGTH, length);
        context.startService(intent);
    }

    /**
     * Cancels the active download and drops everything that is still queued.
     *
     * @param context Any context
     */
    public static void cancel(Context context) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelRequested = true;
            synchronized (queueLock) {
                queue.clear();
            }
            if (!running) {
                stopSelf();
            } else if (worker != null) {
                // Interrupts retry sleeps and the wait-for-network loop.
                worker.interrupt();
            }
            return START_NOT_STICKY;
        }

        if (intent != null && intent.getStringExtra(EXTRA_VIDEO_ID) != null) {
            DownloadRequest request = new DownloadRequest(
                    intent.getStringExtra(EXTRA_VIDEO_ID),
                    intent.getStringExtra(EXTRA_TITLE),
                    intent.getBooleanExtra(EXTRA_AUDIO, false),
                    intent.getStringExtra(EXTRA_QUALITY),
                    intent.getIntExtra(EXTRA_LENGTH, -1));
            synchronized (queueLock) {
                queue.add(request);
            }
            startWorker();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        cancelRequested = true;
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    private void startWorker() {
        synchronized (queueLock) {
            if (running) return;
            running = true;
        }
        cancelRequested = false;
        showOngoing(getString(R.string.downloads), getString(R.string.download_preparing), true);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                processQueue();
            }
        }, "notPipe-download");
        worker.start();
    }

    private void processQueue() {
        try {
            while (true) {
                DownloadRequest request;
                synchronized (queueLock) {
                    if (cancelRequested || queue.isEmpty()) break;
                    request = queue.remove(0);
                }
                current = request;
                handleRequest(request);
                current = null;
            }
        } finally {
            running = false;

            // A request may have arrived between the empty-queue check and this point.
            boolean moreWork;
            synchronized (queueLock) {
                moreWork = !cancelRequested && !queue.isEmpty();
            }
            if (moreWork) {
                startWorker();
                return;
            }

            if (cancelRequested) {
                notificationManager().cancel(NOTIFICATION_ID);
            }
            if (NotPipe.SDK >= 5) {
                stopForeground(false);
            }
            stopSelf();
        }
    }

    private void handleRequest(DownloadRequest request) {
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            fail(request, null, getString(R.string.sd_card));
            return;
        }
        if (availableBytes() < 50L * 1024L * 1024L) {
            fail(request, null, getString(R.string.sd_card_space));
            return;
        }
        if (!Utils.hasConnection(this)) {
            fail(request, null, getString(R.string.no_connection));
            return;
        }

        Config config = ConfigManager.getInstance().getConfig();
        boolean h263 = config.isConvertVideos() && config.getConvertCodec() == 1;
        File target = DownloadsStore.createFile(request.title, request.videoId, request.audio, h263);
        // Download into a .part file so that half-written videos never appear in the list.
        File part = new File(target.getAbsolutePath() + ".part");

        try {
            showOngoing(request.title, getString(R.string.download_preparing), true);
            downloadWithRetries(request, part);

            if (cancelRequested) {
                if (part.exists()) part.delete();
                broadcast(request, STATE_CANCELED, target, null);
                return;
            }
            if (!part.renameTo(target)) {
                throw new IOException(getString(R.string.finish_failed));
            }

            scanFile(target);
            showFinal(request.title, getString(R.string.download_saved, target.getName()));
            broadcast(request, STATE_DONE, target, null);
        } catch (ContentUnavailableException e) {
            fail(request, part, getString(R.string.content_unavailable));
        } catch (VideoTooLongException e) {
            fail(request, part, getString(R.string.video_too_long_download));
        } catch (Exception e) {
            e.printStackTrace();
            String message = e.getMessage();
            if (message == null || message.length() == 0) message = e.toString();
            fail(request, part, message);
        }
    }

    private void downloadWithRetries(DownloadRequest request, File target) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelRequested) return;

            try {
                String url = resolveUrl(request);
                if (cancelRequested) return;
                HttpClient.downloadToFile(url, target.getAbsolutePath(), timeoutFor(request),
                        progressListener(request));
                return;
            } catch (ContentUnavailableException e) {
                throw e;
            } catch (VideoTooLongException e) {
                throw e;
            } catch (IOException e) {
                if (cancelRequested) return;
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    showOngoing(request.title, getString(R.string.download_retry, attempt), true);
                    sleep(RETRY_DELAY * attempt);
                }
            }
        }
        if (lastError != null) throw lastError;
    }

    private String resolveUrl(DownloadRequest request) throws IOException {
        Config config = ConfigManager.getInstance().getConfig();
        if (request.audio) {
            return Manager.getInstance().getAudioUrl(request.videoId, 30000);
        }
        if (config.isConvertVideos()) {
            return Manager.getInstance().getConvUrl(request.videoId, request.quality,
                    config.getConvertCodec());
        }
        return Manager.getInstance().getVideoUrl(request.videoId, request.quality,
                timeoutFor(request));
    }

    private int timeoutFor(DownloadRequest request) {
        Config config = ConfigManager.getInstance().getConfig();
        if (request.audio) return HttpClient.VIDEO_TIMEOUT;
        if (config.isConvertVideos()) return HttpClient.CONVERSION_TIMEOUT;
        if ("360".equals(request.quality)) return 60000;
        return request.length >= 1800 ? 360000 : 180000;
    }

    private HttpClient.DownloadProgressListener progressListener(final DownloadRequest request) {
        return new HttpClient.DownloadProgressListener() {
            private long lastTime = 0;
            private long lastBytes = 0;

            @Override
            public boolean onProgress(long bytesDownloaded, long totalBytes) {
                if (cancelRequested) return false;

                long now = System.currentTimeMillis();
                if (lastTime == 0) {
                    lastTime = now;
                    lastBytes = bytesDownloaded;
                    return true;
                }
                long elapsed = now - lastTime;
                if (elapsed < 700) return true;

                long interval = bytesDownloaded - lastBytes;
                int speed = elapsed > 0 ? (int) (interval * 1000 / (elapsed * 1024)) : 0;
                int percent = totalBytes > 0 ? (int) (bytesDownloaded * 100 / totalBytes) : -1;
                publish(request, percent, speed, bytesDownloaded);

                lastTime = now;
                lastBytes = bytesDownloaded;
                return true;
            }
        };
    }

    private void publish(DownloadRequest request, int percent, int speedKb, long bytes) {
        String text;
        if (percent >= 0) {
            text = getString(R.string.notif_progress, percent, speedKb);
        } else {
            text = getString(R.string.notif_progress_unknown, bytes / 1048576.0, speedKb);
        }
        notificationManager().notify(NOTIFICATION_ID,
                buildNotification(request.title, text, true, true));

        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_VIDEO_ID, request.videoId);
        intent.putExtra(EXTRA_STATE, STATE_RUNNING);
        intent.putExtra(EXTRA_PERCENT, percent);
        intent.putExtra(EXTRA_SPEED, speedKb);
        sendBroadcast(intent);
        lastPublish = System.currentTimeMillis();
    }

    private void broadcast(DownloadRequest request, int state, File file, String error) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_VIDEO_ID, request.videoId);
        intent.putExtra(EXTRA_STATE, state);
        if (file != null) intent.putExtra(EXTRA_PATH, file.getAbsolutePath());
        if (error != null) intent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(intent);
    }

    private void fail(DownloadRequest request, File target, String message) {
        if (target != null && target.exists()) target.delete();
        showFinal(request.title, getString(R.string.download_failed, message));
        broadcast(request, STATE_FAILED, null, message);
    }

    private void showOngoing(String title, String text, boolean cancelAction) {
        Notification notification = buildNotification(title, text, true, cancelAction);
        if (NotPipe.SDK >= 5) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            notificationManager().notify(NOTIFICATION_ID, notification);
        }
    }

    private void showFinal(String title, String text) {
        if (NotPipe.SDK >= 5) {
            stopForeground(false);
        }
        notificationManager().notify(NOTIFICATION_ID, buildNotification(title, text, false, false));
    }

    private Notification buildNotification(String title, String text, boolean ongoing,
                                           boolean cancelAction) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, DownloadsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        if (NotPipe.SDK >= 11) {
            Notification.Builder builder = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.main_icon)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(contentIntent)
                    .setOngoing(ongoing);
            if (cancelAction && NotPipe.SDK >= 16) {
                builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.cancel),
                        PendingIntent.getService(this, 1,
                                new Intent(this, DownloadService.class).setAction(ACTION_CANCEL), 0));
            }
            return NotPipe.SDK >= 16 ? builder.build() : builder.getNotification();
        }

        // Android 1.5–2.3: setLatestEventInfo is not part of the public SDK stubs,
        // so the notification content is inflated from a layout instead.
        Notification notification = new Notification(R.drawable.main_icon, title,
                System.currentTimeMillis());
        if (ongoing) notification.flags |= Notification.FLAG_ONGOING_EVENT;

        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_download);
        views.setTextViewText(R.id.notif_title, title);
        views.setTextViewText(R.id.notif_text, text);
        views.setImageViewResource(R.id.notif_icon, R.drawable.main_icon);
        notification.contentView = views;
        notification.contentIntent = contentIntent;
        return notification;
    }

    private NotificationManager notificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private long availableBytes() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            return (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    private void scanFile(File file) {
        try {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
