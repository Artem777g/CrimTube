package io.github.gohoski.notpipe.download;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created on 28.10.2026.
 * Read-only provider that exposes downloaded files to external players on Android 7+,
 * where file:// URIs are no longer allowed.
 */
public class DownloadsProvider extends ContentProvider {
    public static final String AUTHORITY = "io.github.gohoski.notpipe.downloads";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Many players query OpenableColumns before opening a content:// URI and give up
        // when the provider returns null, which makes the tap look like it did nothing.
        if (projection == null) {
            projection = new String[]{ OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        }

        File file = resolve(uri);
        MatrixCursor cursor = new MatrixCursor(projection, 1);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(projection[i])) {
                row[i] = Long.valueOf(file.length());
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return DownloadsStore.mime(resolve(uri));
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * Maps a content URI back to a file inside the downloads folder.
     * Path traversal outside of that folder is rejected.
     */
    private File resolve(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) name = "";
        File dir = DownloadsStore.directory();
        File file = new File(dir, name);
        try {
            String dirPath = dir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (!filePath.startsWith(dirPath + File.separator)) {
                throw new IllegalArgumentException("Path outside of downloads folder");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Bad download path");
        }
        return file;
    }
}
