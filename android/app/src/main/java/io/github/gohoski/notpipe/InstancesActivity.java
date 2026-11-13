package io.github.gohoski.notpipe;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpException;
import io.github.gohoski.notpipe.http.HttpRequest;

/**
 * Created on 04.11.2026.
 * Diagnostics screen: every configured instance, its capabilities, measured latency,
 * failure count and a manual ping.
 */
public class InstancesActivity extends Activity {
    private final List<Manager.InstanceInfo> instances = new ArrayList<Manager.InstanceInfo>();
    private final Map<String, PingResult> pingResults = new HashMap<String, PingResult>();
    private ListView listView;
    private InstanceAdapter adapter;
    private PingTask pingTask;

    private static class PingResult {
        final String host;
        final int latency;
        final boolean ok;

        PingResult(String host, int latency, boolean ok) {
            this.host = host;
            this.latency = latency;
            this.ok = ok;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instances);

        listView = (ListView) findViewById(R.id.instances_list);
        listView.setEmptyView(findViewById(R.id.instances_empty));
        adapter = new InstanceAdapter();
        listView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pingTask != null) pingTask.cancel(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_instances, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_ping) {
            startPing();
            return true;
        }
        if (item.getItemId() == R.id.action_reset_stats) {
            Manager.getInstance().resetStats();
            pingResults.clear();
            reload();
            Toast.makeText(this, R.string.stats_reset, Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        instances.clear();
        instances.addAll(Manager.getInstance().allInstancesInfo());
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void startPing() {
        if (pingTask != null) pingTask.cancel(true);
        pingResults.clear();
        pingTask = new PingTask(new ArrayList<Manager.InstanceInfo>(instances));
        pingTask.execute();
        Toast.makeText(this, R.string.pinging, Toast.LENGTH_SHORT).show();
    }

    private String latencyText(Manager.InstanceInfo info) {
        PingResult result = pingResults.get(info.host);
        if (result != null) {
            if (!result.ok) return getString(R.string.ping_failed);
            return getString(R.string.ping_ms, result.latency);
        }
        int latency = Manager.getInstance().latencyOf(info.host);
        if (latency >= 0) return getString(R.string.ping_ms, latency);
        return getString(R.string.no_stats);
    }

    private String capabilities(Manager.InstanceInfo info) {
        StringBuilder sb = new StringBuilder();
        if (info.supportsMetadata) appendCap(sb, getString(R.string.cap_meta));
        if (info.supportsVideo) appendCap(sb, getString(R.string.cap_video));
        if (info.supportsHq) appendCap(sb, getString(R.string.cap_hq));
        if (info.supportsConversion) appendCap(sb, getString(R.string.cap_conversion));
        if (info.supportsChannels) appendCap(sb, getString(R.string.cap_channels));
        if (info.supportsAudio) appendCap(sb, getString(R.string.cap_audio));
        return sb.toString();
    }

    private void appendCap(StringBuilder sb, String value) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(value);
    }

    private class PingTask extends AsyncTask<Void, PingResult, Void> {
        private final List<Manager.InstanceInfo> snapshot;
        private ExecutorService pool;

        PingTask(List<Manager.InstanceInfo> snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        protected Void doInBackground(Void... params) {
            final CountDownLatch latch = new CountDownLatch(snapshot.size());
            pool = Executors.newFixedThreadPool(3);

            for (int i = 0; i < snapshot.size(); i++) {
                final Manager.InstanceInfo info = snapshot.get(i);
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String baseUrl = info.baseUrl;
                            if (baseUrl == null || baseUrl.length() == 0) {
                                baseUrl = "http://" + info.host;
                            }
                            long started = System.currentTimeMillis();
                            boolean ok;
                            try {
                                HttpClient.executeToString(new HttpRequest(baseUrl, "/"), 6000);
                                ok = true;
                            } catch (HttpException e) {
                                // Any HTTP answer means the server is alive.
                                ok = true;
                            } catch (Exception e) {
                                ok = false;
                            }
                            publishProgress(new PingResult(info.host,
                                    (int) (System.currentTimeMillis() - started), ok));
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }
            pool.shutdown();

            try {
                latch.await();
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(PingResult... values) {
            if (values.length > 0) {
                PingResult result = values[0];
                pingResults.put(result.host, result);
                adapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void onCancelled() {
            if (pool != null) pool.shutdownNow();
        }
    }

    private class InstanceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return instances.size();
        }

        @Override
        public Object getItem(int position) {
            return instances.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_instance, parent, false);
            }

            Manager.InstanceInfo info = instances.get(position);
            TextView name = (TextView) view.findViewById(R.id.instance_name);
            TextView host = (TextView) view.findViewById(R.id.instance_host);
            TextView latency = (TextView) view.findViewById(R.id.instance_latency);
            TextView failures = (TextView) view.findViewById(R.id.instance_failures);
            TextView caps = (TextView) view.findViewById(R.id.instance_caps);

            name.setText(info.name != null ? info.name : info.host);
            host.setText(info.host);
            latency.setText(latencyText(info));
            failures.setText(getString(R.string.failures_short,
                    Manager.getInstance().failuresOf(info.host)));
            caps.setText(capabilities(info));
            return view;
        }
    }
}
