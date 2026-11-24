package io.github.gohoski.notpipe.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.R;

/**
 * Created on 28.10.2026.
 * Single place that knows where downloads live, how they are named and how they are opened.
 */
public final class DownloadsStore {
    /** Folder on the SD card, relative to the external storage root. */
    public static final String DIRECTORY = "notPipe/downloads";
    /** Maximum length of the title part of a file name. */
    private static final int MAX_TITLE_LENGTH = 60;

    private DownloadsStore() {
    }

    /**
     * Returns the downloads folder, creating it when needed.
     *
     * @return The downloads directory
     */
    public static File directory() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIRECTORY);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Lists downloaded files, newest first.
     *
     * @return Mutable list of files
     */
    public static List<File> list() {
        File[] files = directory().listFiles();
        List<File> result = new ArrayList<File>();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                // Files still being written carry the .part suffix, and zero-length files
                // are leftovers of a failed start; neither should be listed.
                if (files[i].isFile() && files[i].length() > 0
                        && !files[i].getName().endsWith(".part")) {
                    result.add(files[i]);
                }
            }
        }
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                long diff = right.lastModified() - left.lastModified();
                return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
            }
        });
        return result;
    }

    /**
     * Builds a unique file for the given video, keeping the title readable.
     *
     * @param title Video title
     * @param videoId YouTube video ID
     * @param audio True for an audio-only file
     * @param h263 True when the file was converted to H.263 (3GP container)
     * @return A path that does not exist yet
     */
    public static File createFile(String title, String videoId, boolean audio, boolean h263) {
        File dir = directory();
        String name = fileName(title, videoId, audio, h263);

        File file = new File(dir, name);
        if (!file.exists()) return file;

        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        while (file.exists()) {
            file = new File(dir, stem + " (" + index + ")" + extension);
            index++;
        }
        return file;
    }

    /**
     * Builds the display file name without touching the file system.
     *
     * @param title Video title
     * @param videoId YouTube video ID
     * @param audio True for an audio-only file
     * @param h263 True when the file was converted to H.263 (3GP container)
     * @return File name such as "Title [id].mp4"
     */
    public static String fileName(String title, String videoId, boolean audio, boolean h263) {
        String base = sanitize(title);
        if (base.length() == 0) base = videoId;
        if (base.length() > MAX_TITLE_LENGTH) {
            base = base.substring(0, MAX_TITLE_LENGTH);
            char last = base.charAt(base.length() - 1);
            if (last >= 0xD800 && last <= 0xDBFF) { // do not cut a surrogate pair in half
                base = base.substring(0, base.length() - 1);
            }
        }

        String extension;
        if (audio) {
            extension = ".m4a";
        } else if (h263) {
            extension = ".3gp";
        } else {
            extension = ".mp4";
        }
        return base + " [" + videoId + "]" + extension;
    }

    /**
     * Removes characters that are not allowed in FAT file names.
     *
     * @param name Source name
     * @return Safe file name
     */
    public static String sanitize(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String result = sb.toString().trim();
        while (result.length() > 0 && (result.endsWith(".") || result.endsWith(" "))) {
            result = result.substring(0, result.length() - 1).trim();
        }
        return result;
    }

    /**
     * Removes leftover and broken files: zero-length files, .part files older than a day and
     * media files without a valid MP4 header.
     *
     * @return Number of removed files
     */
    public static int cleanupBroken() {
        File[] files = directory().listFiles();
        if (files == null) return 0;

        int removed = 0;
        long day = 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (!file.isFile()) continue;

            String name = file.getName().toLowerCase();

            if (name.endsWith(".part")) {
                if (now - file.lastModified() > day && file.delete()) removed++;
                continue;
            }

            if (file.length() == 0) {
                if (file.delete()) removed++;
                continue;
            }

            if ((name.endsWith(".mp4") || name.endsWith(".m4a") || name.endsWith(".3gp"))
                    && !hasValidHeader(file)) {
                if (file.delete()) removed++;
            }
        }
        return removed;
    }

    /**
     * Checks the first bytes of an MP4/M4A/3GP file for the mandatory "ftyp" box.
     * Unreadable files are treated as valid to avoid deleting user data by mistake.
     */
    private static boolean hasValidHeader(File file) {
        java.io.RandomAccessFile raf = null;
        try {
            raf = new java.io.RandomAccessFile(file, "r");
            // A real media file is always longer than the ftyp header itself.
            if (raf.length() < 12) return false;
            byte[] header = new byte[12];
            raf.readFully(header);
            return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
        } catch (Exception e) {
            return true;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Rough download size estimate used to warn before starting.
     *
     * @param lengthSeconds Video length in seconds, -1 if unknown
     * @param quality Requested quality ("360", "480", "720")
     * @param audio True for audio-only download
     * @return Estimated size in bytes, 0 if it cannot be estimated
     */
    public static long estimateBytes(int lengthSeconds, String quality, boolean audio) {
        if (lengthSeconds <= 0) return 0;

        int kbPerSecond;
        if (audio) {
            kbPerSecond = 16; // ~128 kbps
        } else if ("720".equals(quality)) {
            kbPerSecond = 320; // ~2.5 Mbps
        } else if ("480".equals(quality)) {
            kbPerSecond = 130; // ~1 Mbps
        } else {
            kbPerSecond = 65; // ~0.5 Mbps
        }
        return (long) lengthSeconds * kbPerSecond * 1024L;
    }

    /**
     * Detects the MIME type by extension so that players pick the right decoder.
     *
     * @param file Downloaded file
     * @return MIME type
     */
    public static String mime(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".m4a") || name.endsWith(".aac")) return "audio/mp4";
        if (name.endsWith(".3gp")) return "video/3gpp";
        return "video/mp4";
    }

    /**
     * Tells whether the file is an audio-only download.
     *
     * @param file Downloaded file
     * @return true for M4A/AAC files
     */
    public static boolean isAudio(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".m4a") || name.endsWith(".aac");
    }

    /**
     * Returns a URI that can be handed to a player.
     * Android 7.0+ forbids file:// URIs leaving the app, so newer systems are served
     * through {@link DownloadsProvider} instead.
     *
     * @param file Downloaded file
     * @return content:// URI on Android 7+, file:// URI on older versions
     */
    public static Uri uriFor(File file) {
        if (NotPipe.SDK >= 24) {
            return Uri.withAppendedPath(
                    Uri.parse("content://" + DownloadsProvider.AUTHORITY), file.getName());
        }
        return Uri.fromFile(file);
    }

    /**
     * Opens a downloaded file in whatever player the device has.
     *
     * @param context Activity context
     * @param file File to open
     */
    public static void openExternal(Context context, File file) {
        Uri uri = uriFor(file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime(file));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, R.string.no_player, Toast.LENGTH_LONG).show();
        }
    }
}
