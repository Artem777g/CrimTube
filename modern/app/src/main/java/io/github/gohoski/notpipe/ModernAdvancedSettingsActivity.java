package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.Toast;

import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;

/**
 * Created on 24.11.2026.
 * Современный экран основных настроек вместо старых списков.
 */
public class ModernAdvancedSettingsActivity extends Activity {
    private Spinner quality;
    private CheckBox external;
    private CheckBox stream;
    private CheckBox convert;
    private Config config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_advanced);

        final ConfigManager manager = ConfigManager.getInstance();
        config = manager.getConfig();

        findViewById(R.id.modern_adv_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        quality = (Spinner) findViewById(R.id.modern_adv_quality);
        final String[] labels = new String[]{"360p", "720p", "1080p"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quality.setAdapter(adapter);
        quality.setSelection(indexOf(labels, config.getPreferredQuality() + "p"));

        external = (CheckBox) findViewById(R.id.modern_adv_external);
        stream = (CheckBox) findViewById(R.id.modern_adv_stream);
        convert = (CheckBox) findViewById(R.id.modern_adv_convert);
        external.setChecked(config.isUseExternalPlayer());
        stream.setChecked(config.isStreamPlayback());
        convert.setChecked(config.isConvertVideos());

        Button instances = (Button) findViewById(R.id.modern_adv_instances);
        instances.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernAdvancedSettingsActivity.this, InstancesActivity.class));
            }
        });

        Button save = (Button) findViewById(R.id.modern_adv_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                config.setPreferredQuality(labels[quality.getSelectedItemPosition()].replace("p", ""));
                config.setUseExternalPlayer(external.isChecked());
                config.setStreamPlayback(stream.isChecked());
                config.setConvertVideos(convert.isChecked());
                manager.saveConfig(config);
                Toast.makeText(ModernAdvancedSettingsActivity.this, R.string.update_saved, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }
}
