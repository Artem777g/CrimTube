package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

/**
 * Created on 24.11.2026.
 * Современный вход в настройки и служебные экраны.
 */
public class ModernSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_settings);

        findViewById(R.id.modern_settings_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.ms_instances).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernSettingsActivity.this, ModernInstancesActivity.class));
            }
        });
        findViewById(R.id.ms_updates).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateChecker.showSettingsDialog(ModernSettingsActivity.this);
            }
        });
        findViewById(R.id.ms_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernSettingsActivity.this, HelpActivity.class));
            }
        });
        findViewById(R.id.ms_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AboutDialog.show(ModernSettingsActivity.this);
            }
        });
        findViewById(R.id.ms_advanced).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ModernSettingsActivity.this, ModernAdvancedSettingsActivity.class));
            }
        });
    }
}
