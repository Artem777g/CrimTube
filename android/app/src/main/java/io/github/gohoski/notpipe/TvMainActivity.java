package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
 * Created on 15.11.2026.
 * Телевизионный экран: крупные карточки и управление с пульта.
 */
public class TvMainActivity extends Activity {
    private final List<VideoInfo> videos = new ArrayList<VideoInfo>();
    private GridView grid;
    private ProgressBar loading;
    private TextView empty;
    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_main);

        grid = (GridView) findViewById(R.id.tv_grid);
        loading = (ProgressBar) findViewById(R.id.tv_loading);
        empty = (TextView) findViewById(R.id.tv_empty);

        adapter = new VideoAdapter(this, R.layout.item_tv_video, videos);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                VideoInfo video = videos.get(position);
                Intent intent = new Intent(TvMainActivity.this, VideoActivity.class);
                intent.putExtra("ID", video.id);
                startActivity(intent);
            }
        });

        Button search = (Button) findViewById(R.id.tv_search);
        Button subscriptions = (Button) findViewById(R.id.tv_subscriptions);
        Button history = (Button) findViewById(R.id.tv_history);
        Button downloads = (Button) findViewById(R.id.tv_downloads);

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSearchDialog();
            }
        });
        subscriptions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvMainActivity.this, SubscriptionsActivity.class));
            }
        });
        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvMainActivity.this, HistoryActivity.class));
            }
        });
        downloads.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(TvMainActivity.this, DownloadsActivity.class));
            }
        });

        loadPopular();
        search.requestFocus();
    }

    private void loadPopular() {
        loading.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        new PopularTask().execute();
    }

    private void showSearchDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.tv_search);

        new AlertDialog.Builder(this)
                .setTitle(R.string.tv_search)
                .setView(input)
                .setPositiveButton(R.string.tv_search, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String query = input.getText().toString().trim();
                        if (query.length() > 0) {
                            loading.setVisibility(View.VISIBLE);
                            empty.setVisibility(View.GONE);
                            new SearchTask().execute(query);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
            grid.requestFocus();
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
            if (!isFinishing() && result == null) {
                Toast.makeText(TvMainActivity.this, R.string.error_all_instances, Toast.LENGTH_SHORT).show();
                return;
            }
            if (result == null) return;
            videos.clear();
            videos.addAll(result);
            adapter.notifyDataSetChanged();
            if (videos.isEmpty()) {
                empty.setText(R.string.no_search_results);
                empty.setVisibility(View.VISIBLE);
            }
            grid.requestFocus();
        }
    }
}
