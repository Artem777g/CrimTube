package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.HistoryStore;

/**
 * Created on 04.11.2026.
 * Local watch history: everything that was opened, newest first.
 */
public class HistoryActivity extends Activity {
    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private ListView listView;
    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        listView = (ListView) findViewById(R.id.history_list);
        listView.setEmptyView(findViewById(R.id.history_empty));

        adapter = new VideoAdapter(this, R.layout.item_video_row, videos);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= videos.size()) return;
                Intent intent = new Intent(HistoryActivity.this, VideoActivity.class);
                intent.putExtra("ID", videos.get(position).id);
                startActivity(intent);
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < videos.size()) {
                    confirmRemove(videos.get(position));
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_history) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        videos.clear();
        videos.addAll(HistoryStore.list(this));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void confirmRemove(final VideoInfo video) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_history)
                .setMessage(video.title)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        HistoryStore.remove(HistoryActivity.this, video.id);
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        if (videos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_history)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        HistoryStore.clear(HistoryActivity.this);
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
