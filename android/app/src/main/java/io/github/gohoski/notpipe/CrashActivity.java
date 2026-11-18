package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created on 18.11.2026.
 * Экран после падения: показать отчёт, отправить на сервер или почтой.
 */
public class CrashActivity extends Activity {
    private String report = "";
    private EditText urlField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        TextView text = (TextView) findViewById(R.id.crash_text);
        urlField = (EditText) findViewById(R.id.crash_report_url);
        SharedPreferences prefs = getSharedPreferences(CrashHandler.PREFS, MODE_PRIVATE);
        urlField.setText(prefs.getString(CrashHandler.KEY_URL, ""));

        String path = getIntent().getStringExtra("path");
        if (path != null) {
            try {
                File file = new File(path);
                FileInputStream in = new FileInputStream(file);
                byte[] buffer = new byte[(int) file.length()];
                in.read(buffer);
                in.close();
                report = new String(buffer, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        if (report.length() == 0) {
            report = "CrimTube crash report\nversion: " + BuildConfig.VERSION_NAME
                    + "\n\n(отчёт недоступен)";
        }
        text.setText(report);

        Button send = (Button) findViewById(R.id.crash_send);
        Button email = (Button) findViewById(R.id.crash_email);
        Button close = (Button) findViewById(R.id.crash_close);

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendReport();
            }
        });
        email.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEmail();
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        String savedUrl = urlField.getText().toString().trim();
        if (savedUrl.length() > 0) {
            sendReport();
        }
    }

    private void sendReport() {
        final String url = urlField.getText().toString().trim();
        if (url.length() == 0) {
            Toast.makeText(this, R.string.report_url_hint, Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(CrashHandler.PREFS, MODE_PRIVATE)
                .edit().putString(CrashHandler.KEY_URL, url).commit();

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                    byte[] body = report.getBytes("UTF-8");
                    connection.setFixedLengthStreamingMode(body.length);
                    OutputStream out = connection.getOutputStream();
                    out.write(body);
                    out.close();
                    int code = connection.getResponseCode();
                    connection.disconnect();
                    return code >= 200 && code < 300;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean ok) {
                Toast.makeText(CrashActivity.this,
                        ok ? R.string.report_sent : R.string.report_failed,
                        Toast.LENGTH_LONG).show();
                if (ok) finish();
            }
        }.execute();
    }

    private void sendEmail() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"artm.golovkov.00@inbox.ru"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "CrimTube crash " + BuildConfig.VERSION_NAME);
        intent.putExtra(Intent.EXTRA_TEXT, report);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.send_email)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.report_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
