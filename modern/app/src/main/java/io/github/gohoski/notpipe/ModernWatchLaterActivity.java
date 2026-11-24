package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.VideoAdapter;
import io.github.gohoski.notpipe.util.WatchLaterStore;

/**
 * Created on 24.11.2026.
 * Современный список «Смотреть позже».
 */
public class ModernWatchLaterActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_list);
        ((TextView) findViewById(R.id.modern_list_title)).setText(R.string.watch_later);
        findViewById(R.id.modern_list_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        List<VideoInfo> videos = new ArrayList<VideoInfo>(WatchLaterStore.list(this));
        ListView list = (ListView) findViewById(R.id.modern_list);
        list.setAdapter(new VideoAdapter(this, R.layout.item_modern_video, videos));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                VideoInfo video = (VideoInfo) parent.getItemAtPosition(position);
                Intent intent = new Intent(ModernWatchLaterActivity.this, ModernPlayerActivity.class);
                intent.putExtra("ID", video.id);
                startActivity(intent);
            }
        });

        if (videos.isEmpty()) {
            TextView empty = (TextView) findViewById(R.id.modern_list_empty);
            empty.setText(R.string.watch_later_empty);
            empty.setVisibility(View.VISIBLE);
        }
    }
}
