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
import io.github.gohoski.notpipe.util.WatchLaterStore;

/**
 * Created on 04.11.2026.
 * Shows videos saved locally with "Watch later".
 */
public class WatchLaterActivity extends Activity {
    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private ListView listView;
    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_later);

        listView = (ListView) findViewById(R.id.watch_later_list);
        listView.setEmptyView(findViewById(R.id.watch_later_empty));

        adapter = new VideoAdapter(this, R.layout.item_video_row, videos);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= videos.size()) return;
                Intent intent = new Intent(WatchLaterActivity.this, VideoActivity.class);
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
        getMenuInflater().inflate(R.menu.menu_watch_later, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_watch_later) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        videos.clear();
        videos.addAll(WatchLaterStore.list(this));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void confirmRemove(final VideoInfo video) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_watch_later)
                .setMessage(video.title)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        WatchLaterStore.remove(WatchLaterActivity.this, video.id);
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClear() {
        if (videos.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_watch_later)
                .setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        WatchLaterStore.clear(WatchLaterActivity.this);
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
