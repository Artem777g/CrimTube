package io.github.gohoski.notpipe.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StatFs;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.R;
import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.data.Video;
import io.github.gohoski.notpipe.download.DownloadService;
import io.github.gohoski.notpipe.download.DownloadsStore;

/**
 * Created on 04.11.2026.
 * The "Download" flow of the video screen: runtime permission, quality choice, free space
 * check and starting the background service.
 * <p>
 * Moved out of VideoActivity to keep the activity focused on playback.
 */
public class DownloadLauncher {
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private static final long SPACE_MARGIN = 30L * 1024L * 1024L;

    private final Activity activity;
    private final String videoId;
    private final Video video;
    private boolean permissionPending = false;
    private boolean optionsPending = false;

    public DownloadLauncher(Activity activity, String videoId, Video video) {
        this.activity = activity;
        this.videoId = videoId;
        this.video = video;
    }

    /**
     * Starts the flow: asks for the storage permission if needed, then shows the options.
     */
    public void start() {
        if (video == null) {
            Toast.makeText(activity, R.string.metadata_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasWritePermission()) {
            permissionPending = true;
            requestWritePermission();
            return;
        }
        showOptions();
    }

    /**
     * Handles the result of the runtime permission dialog.
     *
     * @param requestCode Request code from the activity
     * @param grantResults Grant results
     * @return true when the result belongs to the download flow
     */
    public boolean handlePermissions(int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_WRITE_STORAGE) return false;

        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        boolean pending = permissionPending;
        permissionPending = false;

        if (granted) {
            if (pending) showOptions();
        } else {
            Toast.makeText(activity, R.string.download_no_permission, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * Android 6.0+ checks the permission at runtime. Reflection keeps the app loadable on 1.5.
     */
    private boolean hasWritePermission() {
        if (NotPipe.SDK < 23) return true;
        try {
            Method check = Context.class.getMethod("checkSelfPermission", String.class);
            Object result = check.invoke(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return result != null && ((Integer) result).intValue()
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return true;
        }
    }

    private void requestWritePermission() {
        try {
            Method request = Activity.class.getMethod("requestPermissions", String[].class, int.class);
            request.invoke(activity, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    Integer.valueOf(REQUEST_WRITE_STORAGE));
        } catch (Exception e) {
            permissionPending = false;
            Toast.makeText(activity, R.string.download_no_permission, Toast.LENGTH_LONG).show();
        }
    }

    private void showOptions() {
        if (optionsPending) return;
        optionsPending = true;

        Config config = ConfigManager.getInstance().getConfig();
        final List<String> qualities = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();

        if (config.isConvertVideos()) {
            // Conversion always produces one server-side file, so quality choice is useless.
            labels.add(activity.getString(R.string.download_video_converted));
            qualities.add("360");
        } else {
            labels.add(activity.getString(R.string.download_video_quality, "360"));
            qualities.add("360");
            if (video.length <= 3600) {
                labels.add(activity.getString(R.string.download_video_quality, "480"));
                qualities.add("480");
                labels.add(activity.getString(R.string.download_video_quality, "720"));
                qualities.add("720");
            }
        }
        labels.add(activity.getString(R.string.download_audio));
        qualities.add(null);

        String[] items = new String[labels.size()];
        labels.toArray(items);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.download)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        optionsPending = false;
                        String quality = qualities.get(which);
                        if (quality == null) {
                            if (!Manager.getInstance().hasAudioInstances()) {
                                Toast.makeText(activity, R.string.download_no_audio,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            startDownload(true, null);
                        } else {
                            startDownload(false, quality);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        optionsPending = false;
                    }
                })
                .show();
    }

    private void startDownload(boolean audio, String quality) {
        long need = DownloadsStore.estimateBytes(video.length, quality, audio);
        if (need > 0) {
            long free = availableBytes();
            if (free < need + SPACE_MARGIN) {
                Toast.makeText(activity, activity.getString(R.string.not_enough_space,
                        need / 1048576L, free / 1048576L), Toast.LENGTH_LONG).show();
                return;
            }
        }
        DownloadService.enqueue(activity, videoId, video.title, audio, quality, video.length);
        Toast.makeText(activity, R.string.download_queued, Toast.LENGTH_SHORT).show();
    }

    private long availableBytes() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            return (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }
}
