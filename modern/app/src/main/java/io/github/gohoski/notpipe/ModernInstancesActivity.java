package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpRequest;
import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;

/**
 * Created on 25.11.2026.
 * Modern editor for API instances: shows, adds, edits and removes addresses for
 * Invidious, yt2009, Piped and YtApiLegacy. Every change is written to the config
 * immediately, no network requests are performed here.
 */
public class ModernInstancesActivity extends Activity {
    private static final int TYPE_INVIDIOUS = 0;
    private static final int TYPE_YT2009 = 1;
    private static final int TYPE_PIPED = 2;
    private static final int TYPE_YTAPI_LEGACY = 3;
    private static final int TYPE_COUNT = 4;

    private final TextView[] tabs = new TextView[TYPE_COUNT];

    private ConfigManager configManager;
    private Config config;
    private final Map<String, Integer> latencies = new HashMap<String, Integer>();
    private TextView pingStatus;
    private List<String> instances;
    private int selectedType = TYPE_INVIDIOUS;

    private EditText input;
    private LinearLayout listContainer;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_instances);

        configManager = ConfigManager.getInstance();
        config = configManager.getConfig();

        findViewById(R.id.modern_inst_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        tabs[0] = (TextView) findViewById(R.id.modern_inst_tab_0);
        tabs[1] = (TextView) findViewById(R.id.modern_inst_tab_1);
        tabs[2] = (TextView) findViewById(R.id.modern_inst_tab_2);
        tabs[3] = (TextView) findViewById(R.id.modern_inst_tab_3);
        String[] typeNames = getResources().getStringArray(R.array.modern_instances_types);
        for (int i = 0; i < TYPE_COUNT; i++) {
            tabs[i].setText(typeNames[i]);
            final int type = i;
            tabs[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectType(type);
                }
            });
        }

        input = (EditText) findViewById(R.id.modern_inst_input);
        input.clearFocus();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        listContainer = (LinearLayout) findViewById(R.id.modern_inst_list);
        emptyView = (TextView) findViewById(R.id.modern_inst_empty);

        Button addButton = (Button) findViewById(R.id.modern_inst_add);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addInstance();
            }
        });

        pingStatus = (TextView) findViewById(R.id.modern_inst_ping_status);
        Button pingButton = (Button) findViewById(R.id.modern_inst_ping);
        pingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startPing();
            }
        });
        Button saveButton = (Button) findViewById(R.id.modern_inst_save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                persist(R.string.modern_instances_saved);
            }
        });

        selectType(TYPE_INVIDIOUS);
    }

    private void selectType(int type) {
        selectedType = type;
        if (type == TYPE_YT2009) {
            instances = config.getYt2009Instances();
        } else if (type == TYPE_PIPED) {
            instances = config.getPipedInstances();
        } else if (type == TYPE_YTAPI_LEGACY) {
            instances = config.getYtApiLegacyInstances();
        } else {
            instances = config.getInvidiousInstances();
        }
        updateTabs();
        refreshList();
    }

    private void updateTabs() {
        for (int i = 0; i < TYPE_COUNT; i++) {
            if (i == selectedType) {
                tabs[i].setTextColor(0xFFFFFFFF);
                tabs[i].setTypeface(Typeface.DEFAULT_BOLD);
                tabs[i].setBackgroundResource(R.drawable.modern_chip_bg);
            } else {
                tabs[i].setTextColor(0xFF9AA0A6);
                tabs[i].setTypeface(Typeface.DEFAULT);
                tabs[i].setBackgroundColor(0x00000000);
            }
        }
    }

    private void refreshList() {
        listContainer.removeAllViews();
        for (int i = 0; i < instances.size(); i++) {
            listContainer.addView(createRow(instances.get(i)));
        }
        emptyView.setVisibility(instances.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startPing() {
        if (instances.isEmpty()) return;
        pingStatus.setText("Проверяю инстансы…");
        new PingTask().execute();
    }

    private class PingTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            latencies.clear();
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            for (int i = 0; i < instances.size(); i++) {
                final String url = instances.get(i);
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        String base = url;
                        int comma = base.indexOf(',');
                        if (comma >= 0) base = base.substring(0, comma);
                        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://" + base;
                        try {
                            long start = System.currentTimeMillis();
                            HttpClient.executeToString(new HttpRequest(base, "/"), 3000);
                            latencies.put(url, Integer.valueOf((int) (System.currentTimeMillis() - start)));
                        } catch (Exception e) {
                            latencies.put(url, Integer.valueOf(-1));
                        }
                    }
                });
            }
            pool.shutdown();
            try {
                pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            pingStatus.setText("Пинг завершён");
            refreshList();
        }
    }

    private View createRow(final String url) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.modern_card_bg);
        row.setPadding(dp(14), dp(8), dp(8), dp(8));
        row.setMinimumHeight(dp(52));
        row.setFocusable(true);
        row.setClickable(true);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        TextView address = new TextView(this);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        address.setLayoutParams(addressParams);
        Integer latency = latencies.get(url);
        address.setText(latency == null ? url : (latency.intValue() < 0 ? url + "  ·  нет ответа" : url + "  ·  " + latency + " мс"));
        address.setTextColor(0xFFFFFFFF);
        address.setTextSize(14);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(address);

        TextView remove = new TextView(this);
        remove.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(36)));
        remove.setGravity(Gravity.CENTER);
        remove.setText("\u2715");
        remove.setTextColor(0xFFFFFFFF);
        remove.setTextSize(14);
        remove.setBackgroundResource(R.drawable.modern_chip_bg);
        remove.setFocusable(true);
        remove.setClickable(true);
        row.addView(remove);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog(url);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDelete(url);
                return true;
            }
        });
        remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(url);
            }
        });
        return row;
    }

    private void addInstance() {
        String normalized = normalizeUrl(input.getText().toString());
        if (normalized == null) {
            Toast.makeText(this, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
            return;
        }
        if (instances.contains(normalized)) {
            Toast.makeText(this, R.string.instance_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        instances.add(normalized);
        input.setText("");
        refreshList();
        persist(R.string.modern_instances_added);
    }

    private void showEditDialog(final String original) {
        final EditText editor = new EditText(this);
        editor.setSingleLine(true);
        editor.setText(original);
        editor.setSelection(original.length());
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle(R.string.modern_instances_edit_title)
                .setView(editor)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyEdit(original, editor.getText().toString());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyEdit(String original, String raw) {
        String normalized = normalizeUrl(raw);
        if (normalized == null) {
            Toast.makeText(this, R.string.incorrect_url, Toast.LENGTH_SHORT).show();
            return;
        }
        int index = instances.indexOf(original);
        if (index < 0) {
            return;
        }
        if (!normalized.equals(original) && instances.contains(normalized)) {
            Toast.makeText(this, R.string.instance_exist, Toast.LENGTH_SHORT).show();
            return;
        }
        instances.set(index, normalized);
        refreshList();
        persist(R.string.modern_instances_updated);
    }

    private void confirmDelete(final String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.modern_instances_delete_title)
                .setMessage(getString(R.string.modern_instances_delete_message, url))
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (instances.remove(url)) {
                            refreshList();
                            persist(R.string.modern_instances_removed);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Saves the currently selected list into the config copy and persists it.
     * Also rebuilds Manager instances so playback picks up the new addresses.
     */
    private void persist(int messageRes) {
        if (selectedType == TYPE_INVIDIOUS) {
            config.setInvidiousInstances(instances);
        } else if (selectedType == TYPE_YT2009) {
            config.setYt2009Instances(instances);
        } else if (selectedType == TYPE_PIPED) {
            config.setPipedInstances(instances);
        } else {
            config.setYtApiLegacyInstances(instances);
        }
        configManager.saveConfig(config);
        Manager.getInstance().reloadInstances();
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    /**
     * Normalizes a user-entered address (or a comma separated base,proxy pair used by Piped).
     *
     * @return normalized address or null when the input is not a valid URL
     */
    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0) {
            return null;
        }
        String[] parts = value.split(",");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) {
                return null;
            }
            if (!part.startsWith("http://") && !part.startsWith("https://")) {
                part = "http://" + part;
            }
            while (part.endsWith("/")) {
                part = part.substring(0, part.length() - 1);
            }
            try {
                new java.net.URL(part);
            } catch (java.net.MalformedURLException e) {
                return null;
            }
            if (i > 0) {
                result.append(",");
            }
            result.append(part);
        }
        return result.toString();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
