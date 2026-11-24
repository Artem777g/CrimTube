package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Created on 18.11.2026.
 * Краткая инструкция и настройка адреса для отчётов об ошибках.
 */
public class HelpActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        final SharedPreferences prefs = getSharedPreferences(CrashHandler.PREFS, MODE_PRIVATE);
        final EditText url = (EditText) findViewById(R.id.help_report_url);
        url.setText(prefs.getString(CrashHandler.KEY_URL, ""));

        Button save = (Button) findViewById(R.id.help_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putString(CrashHandler.KEY_URL, url.getText().toString().trim()).commit();
                Toast.makeText(HelpActivity.this, R.string.report_saved, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
