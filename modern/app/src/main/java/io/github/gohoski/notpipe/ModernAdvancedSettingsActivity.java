package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import io.github.gohoski.notpipe.config.Config;
import io.github.gohoski.notpipe.config.ConfigManager;
import io.github.gohoski.notpipe.util.InstancesUpdater;

/**
 * Created on 24.11.2026.
 * Современный экран расширенных настроек. Возвращает возможности старого SettingsActivity:
 * обновление инстансов с URL, описания воспроизведения и конвертации, выбор кодека,
 * асинхронную установку video URI и поворот в ландшафт при полноэкранном режиме.
 */
public class ModernAdvancedSettingsActivity extends Activity implements InstancesUpdater.OnInstancesUpdatedListener {
    private Spinner quality;
    private CheckBox external;
    private CheckBox stream;
    private CheckBox convert;
    private CheckBox async;
    private CheckBox fullscreenRotate;
    private CheckBox updateInstances;
    private LinearLayout updateContainer;
    private EditText instancesUrl;
    private Spinner updateFrequency;
    private Spinner codec;
    private LinearLayout codecRow;

    private ConfigManager configManager;
    private Config config;
    private String[] qualityLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_advanced);

        configManager = ConfigManager.getInstance();
        config = configManager.getConfig();

        findViewById(R.id.modern_adv_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        quality = (Spinner) findViewById(R.id.modern_adv_quality);
        qualityLabels = new String[]{"360p", "720p", "1080p"};
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, qualityLabels);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quality.setAdapter(qualityAdapter);

        external = (CheckBox) findViewById(R.id.modern_adv_external);
        stream = (CheckBox) findViewById(R.id.modern_adv_stream);
        convert = (CheckBox) findViewById(R.id.modern_adv_convert);
        async = (CheckBox) findViewById(R.id.modern_adv_async);
        fullscreenRotate = (CheckBox) findViewById(R.id.modern_adv_landscape);
        updateInstances = (CheckBox) findViewById(R.id.modern_adv_update_chk);
        updateContainer = (LinearLayout) findViewById(R.id.modern_adv_update_container);
        instancesUrl = (EditText) findViewById(R.id.modern_adv_instances_url);
        updateFrequency = (Spinner) findViewById(R.id.modern_adv_update_spinner);
        codec = (Spinner) findViewById(R.id.modern_adv_codec);
        codecRow = (LinearLayout) findViewById(R.id.modern_adv_codec_row);

        updateInstances.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        convert.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                codecRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        loadConfigValues();

        Button instances = (Button) findViewById(R.id.modern_adv_instances);
        instances.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernAdvancedSettingsActivity.this, ModernInstancesActivity.class));
            }
        });

        Button update = (Button) findViewById(R.id.modern_adv_update_btn);
        update.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                new InstancesUpdater(ModernAdvancedSettingsActivity.this,
                        ModernAdvancedSettingsActivity.this).updateInstances();
            }
        });

        Button save = (Button) findViewById(R.id.modern_adv_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
                Toast.makeText(ModernAdvancedSettingsActivity.this, R.string.update_saved,
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Loads all values from the current configuration into the views.
     */
    private void loadConfigValues() {
        String preferredQuality = config.getPreferredQuality();
        quality.setSelection(indexOf(qualityLabels, preferredQuality == null ? "360p" : preferredQuality + "p"));

        external.setChecked(config.isUseExternalPlayer());
        stream.setChecked(config.isStreamPlayback());
        convert.setChecked(config.isConvertVideos());
        async.setChecked(config.isAsyncSetVideoUri());
        fullscreenRotate.setChecked(config.isFullscreenRotateLandscape());

        updateInstances.setChecked(config.isUpdateInstancesFromUrl());
        updateContainer.setVisibility(config.isUpdateInstancesFromUrl() ? View.VISIBLE : View.GONE);
        String updateUrl = config.getInstancesUpdateUrl();
        instancesUrl.setText(updateUrl == null ? "" : updateUrl);
        updateFrequency.setSelection(clampSelection(config.getUpdateFrequency(), updateFrequency.getCount()));

        codec.setSelection(clampSelection(config.getConvertCodec(), codec.getCount()));
        codecRow.setVisibility(config.isConvertVideos() ? View.VISIBLE : View.GONE);
    }

    /**
     * Writes all values from the views into the configuration and stores it.
     * The instance lists and the last update time are taken from the latest stored
     * configuration so that changes made in ModernInstancesActivity are not lost.
     */
    private void saveSettings() {
        config = configManager.getConfig();

        String preferredQuality = "360";
        int qualityPosition = quality.getSelectedItemPosition();
        if (qualityPosition >= 0 && qualityPosition < qualityLabels.length) {
            preferredQuality = qualityLabels[qualityPosition].replace("p", "");
        }

        config.setPreferredQuality(preferredQuality);
        config.setUseExternalPlayer(external.isChecked());
        config.setStreamPlayback(stream.isChecked());
        config.setConvertVideos(convert.isChecked());
        config.setConvertCodec(codec.getSelectedItemPosition());
        config.setAsyncSetVideoUri(async.isChecked());
        config.setFullscreenRotateLandscape(fullscreenRotate.isChecked());

        config.setUpdateInstancesFromUrl(updateInstances.isChecked());
        config.setInstancesUpdateUrl(instancesUrl.getText().toString().trim());
        config.setUpdateFrequency(updateFrequency.getSelectedItemPosition());

        configManager.saveConfig(config);
    }

    @Override
    public void onInstancesUpdated() {
        config = configManager.getConfig();
        Toast.makeText(this, R.string.ins_upd, Toast.LENGTH_SHORT).show();
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private int clampSelection(int position, int count) {
        if (position < 0) {
            return 0;
        }
        if (count > 0 && position >= count) {
            return count - 1;
        }
        return position;
    }
}
