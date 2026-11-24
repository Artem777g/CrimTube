package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.github.gohoski.notpipe.download.DownloadService;
import io.github.gohoski.notpipe.download.DownloadsStore;

/**
 * Created on 28.10.2026.
 * Lists downloaded videos and audio, allows opening and deleting them and cancelling the
 * download that is currently running.
 */
public class DownloadsActivity extends Activity {
    private static final String PREFS = "notPipe_downloads_ui";
    private static final String KEY_SORT = "sort";
    private static final String KEY_FILTER = "filter";
    private static final int SORT_DATE = 0;
    private static final int SORT_SIZE = 1;
    private static final int SORT_NAME = 2;
    private static final int FILTER_ALL = 0;
    private static final int FILTER_VIDEO = 1;
    private static final int FILTER_AUDIO = 2;

    private final List<File> files = new ArrayList<File>();
    private ListView listView;
    private DownloadAdapter adapter;
    private boolean receiverRegistered = false;
    private int sortMode = SORT_DATE;
    private int filterMode = FILTER_ALL;
    private boolean cleanupStarted = false;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // The list only changes when a download finishes, so progress ticks are ignored.
            if (intent.getIntExtra(DownloadService.EXTRA_STATE, 0) != DownloadService.STATE_RUNNING) {
                refresh();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        listView = (ListView) findViewById(R.id.downloads_list);
        listView.setEmptyView(findViewById(R.id.downloads_empty));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sortMode = prefs.getInt(KEY_SORT, SORT_DATE);
        filterMode = prefs.getInt(KEY_FILTER, FILTER_ALL);

        adapter = new DownloadAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= files.size()) return;
                File file = files.get(position);
                startActivity(LocalVideoActivity.intentFor(DownloadsActivity.this,
                        DownloadsStore.uriFor(file), file.getName()));
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < files.size()) {
                    showFileMenu(files.get(position));
                    return true;
                }
                return false;
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            registerReceiver(progressReceiver, new IntentFilter(DownloadService.ACTION_PROGRESS));
            receiverRegistered = true;
        }
        startCleanupIfNeeded();
        refresh();
    }

    /**
     * Removes zero-length leftovers, stale .part files and media files without a valid header.
     */
    private void startCleanupIfNeeded() {
        if (cleanupStarted) return;
        cleanupStarted = true;

        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... params) {
                return Integer.valueOf(DownloadsStore.cleanupBroken());
            }

            @Override
            protected void onPostExecute(Integer removed) {
                if (removed != null && removed.intValue() > 0) {
                    Toast.makeText(DownloadsActivity.this,
                            getString(R.string.cleaned_broken, removed.intValue()),
                            Toast.LENGTH_LONG).show();
                    refresh();
                }
            }
        }.execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (receiverRegistered) {
            unregisterReceiver(progressReceiver);
            receiverRegistered = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_downloads, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_downloads_refresh) {
            refresh();
            return true;
        }
        if (item.getItemId() == R.id.menu_downloads_cancel) {
            DownloadService.cancel(this);
            return true;
        }
        if (item.getItemId() == R.id.menu_downloads_sort) {
            showSortDialog();
            return true;
        }
        if (item.getItemId() == R.id.menu_downloads_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSortDialog() {
        final String[] labels = new String[]{
                getString(R.string.sort_date),
                getString(R.string.sort_size),
                getString(R.string.sort_name)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_by)
                .setSingleChoiceItems(labels, sortMode, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sortMode = which;
                        saveUiPrefs();
                        refresh();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFilterDialog() {
        final String[] labels = new String[]{
                getString(R.string.filter_all),
                getString(R.string.filter_video),
                getString(R.string.filter_audio)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.filter)
                .setSingleChoiceItems(labels, filterMode, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filterMode = which;
                        saveUiPrefs();
                        refresh();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveUiPrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(KEY_SORT, sortMode)
                .putInt(KEY_FILTER, filterMode)
                .commit();
    }

    private void refresh() {
        files.clear();
        List<File> all = DownloadsStore.list();
        for (int i = 0; i < all.size(); i++) {
            File file = all.get(i);
            boolean audio = DownloadsStore.isAudio(file);
            if (filterMode == FILTER_VIDEO && audio) continue;
            if (filterMode == FILTER_AUDIO && !audio) continue;
            files.add(file);
        }

        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (sortMode == SORT_SIZE) {
                    long diff = right.length() - left.length();
                    return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                }
                if (sortMode == SORT_NAME) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
                long diff = right.lastModified() - left.lastModified();
                return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
            }
        });

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showFileMenu(final File file) {
        String[] items = new String[]{ getString(R.string.open_external), getString(R.string.delete) };
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            DownloadsStore.openExternal(DownloadsActivity.this, file);
                        } else {
                            confirmDelete(file);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final File file) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_file)
                .setMessage(file.getName())
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!file.delete()) {
                            Toast.makeText(DownloadsActivity.this, R.string.delete_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                        refresh();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String formatSize(long bytes) {
        if (bytes >= 1048576L) {
            return getString(R.string.size_mb, bytes / 1048576.0);
        }
        long kb = bytes / 1024L;
        if (kb < 1) kb = 1;
        return getString(R.string.size_kb, kb);
    }

    private class DownloadAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public Object getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_download, parent, false);
            }

            final File file = files.get(position);
            TextView name = (TextView) view.findViewById(R.id.download_name);
            TextView info = (TextView) view.findViewById(R.id.download_info);
            ImageButton delete = (ImageButton) view.findViewById(R.id.download_delete);

            name.setText(file.getName());
            String date = DateFormat.getDateFormat(DownloadsActivity.this)
                    .format(new Date(file.lastModified()));
            info.setText(formatSize(file.length()) + " · "
                    + getString(DownloadsStore.isAudio(file) ? R.string.type_audio : R.string.type_video)
                    + " · " + date);

            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(file);
                }
            });
            return view;
        }
    }
}
