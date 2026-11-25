package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.api.Metadata;
import io.github.gohoski.notpipe.data.VideoInfo;
import io.github.gohoski.notpipe.ui.VideoAdapter;

/**
 * Created on 22.11.2026.
 * Современный экран для актуальных Android: карточки и нижняя навигация.
 */
public class ModernMainActivity extends Activity {
    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private GridView grid;
    private ProgressBar loading;
    private TextView empty;
    private EditText search;
    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_main);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        if (NotPipe.SDK >= 21) {
            getWindow().setStatusBarColor(0xFF0B0D10);
            getWindow().setNavigationBarColor(0xFF0E1014);
        }

        grid = (GridView) findViewById(R.id.modern_grid);
        loading = (ProgressBar) findViewById(R.id.modern_loading);
        empty = (TextView) findViewById(R.id.modern_empty);
        search = (EditText) findViewById(R.id.modern_search);

        adapter = new VideoAdapter(this, R.layout.item_modern_video, videos);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                VideoInfo video = videos.get(position);
                Intent intent = new Intent(ModernMainActivity.this, ModernPlayerActivity.class);
                intent.putExtra("ID", video.id);
                startActivity(intent);
            }
        });

        Button searchGo = (Button) findViewById(R.id.modern_search_go);
        searchGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runSearch();
            }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                runSearch();
                return true;
            }
        });

        findViewById(R.id.modern_subscriptions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernMainActivity.this, ModernSubscriptionsActivity.class));
            }
        });
        findViewById(R.id.modern_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernMainActivity.this, ModernHistoryActivity.class));
            }
        });
        findViewById(R.id.modern_later).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernMainActivity.this, ModernWatchLaterActivity.class));
            }
        });
        findViewById(R.id.modern_nav_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search.clearFocus();
        loadPopular();
            }
        });
        findViewById(R.id.modern_nav_downloads).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernMainActivity.this, ModernDownloadsActivity.class));
            }
        });
        findViewById(R.id.modern_nav_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernMainActivity.this, ModernSettingsActivity.class));
            }
        });

        search.clearFocus();
        loadPopular();
    }

    private void runSearch() {
        String query = search.getText().toString().trim();
        if (query.length() == 0) {
            Toast.makeText(this, R.string.modern_search_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        new SearchTask().execute(query);
    }

    private void loadPopular() {
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        new PopularTask().execute();
    }

    private class PopularTask extends AsyncTask<Void, Void, List<VideoInfo>> {
        @Override
        protected List<VideoInfo> doInBackground(Void... params) {
            try {
                Metadata popular = Manager.getInstance().getPopularMetadata();
                if (popular != null) {
                    return popular.getPopularVideos();
                }
                return Manager.getInstance().getMetadata().getPopularVideos();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<VideoInfo> result) {
            loading.setVisibility(View.GONE);
            if (result == null || result.isEmpty()) {
                empty.setText(R.string.error_all_instances);
                empty.setVisibility(View.VISIBLE);
                return;
            }
            videos.clear();
            videos.addAll(result);
            adapter.notifyDataSetChanged();
        }
    }

    private class SearchTask extends AsyncTask<String, Void, List<VideoInfo>> {
        @Override
        protected List<VideoInfo> doInBackground(String... params) {
            try {
                return Manager.getInstance().getMetadata().search(params[0]);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<VideoInfo> result) {
            loading.setVisibility(View.GONE);
            if (result == null) {
                Toast.makeText(ModernMainActivity.this, R.string.error_all_instances, Toast.LENGTH_SHORT).show();
                return;
            }
            videos.clear();
            videos.addAll(result);
            adapter.notifyDataSetChanged();
            if (videos.isEmpty()) {
                empty.setText(R.string.no_search_results);
                empty.setVisibility(View.VISIBLE);
            }
        }
    }
}
