package io.github.gohoski.notpipe;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import io.github.gohoski.notpipe.download.DownloadsStore;

/**
 * Created on 24.11.2026.
 * Современный экран загрузок.
 */
public class ModernDownloadsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_list);
        ((TextView) findViewById(R.id.modern_list_title)).setText(R.string.downloads);
        findViewById(R.id.modern_list_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        final List<File> files = DownloadsStore.list();
        ListView list = (ListView) findViewById(R.id.modern_list);
        list.setAdapter(new FileAdapter(files));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DownloadsStore.openExternal(ModernDownloadsActivity.this, files.get(position));
            }
        });

        if (files.isEmpty()) {
            TextView empty = (TextView) findViewById(R.id.modern_list_empty);
            empty.setText(R.string.downloads_empty);
            empty.setVisibility(View.VISIBLE);
        }
    }

    private class FileAdapter extends BaseAdapter {
        private final List<File> files;

        FileAdapter(List<File> files) {
            this.files = files;
        }

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
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(ModernDownloadsActivity.this)
                        .inflate(R.layout.item_modern_download, parent, false);
            }
            File file = files.get(position);
            ((TextView) row.findViewById(R.id.download_title)).setText(file.getName());
            ((TextView) row.findViewById(R.id.download_meta)).setText(
                    (DownloadsStore.isAudio(file) ? getString(R.string.type_audio) : getString(R.string.type_video))
                            + " · " + Formatter.formatFileSize(ModernDownloadsActivity.this, file.length()));
            return row;
        }
    }
}
