package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import io.github.gohoski.notpipe.http.HttpClient;
import io.github.gohoski.notpipe.http.HttpRequest;

/**
 * Created on 13.11.2026.
 * Проверка обновлений через latest.json (GitHub raw/Pages) и установка APK.
 */
public final class UpdateChecker {
    private static final String PREFS = "notPipe_update";
    private static final String KEY_URL = "manifest_url";
    private static final String KEY_LAST = "last_check";
    private static final long CHECK_INTERVAL = 24L * 60L * 60L * 1000L;
    private static final String APK_NAME = "update.apk";

    private UpdateChecker() {
    }

    public static String getManifestUrl(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "");
    }

    public static void setManifestUrl(Context context, String url) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URL, url == null ? "" : url.trim()).commit();
    }

    /**
     * Проверяет обновления. При manual=true показывает результат даже без обновления.
     */
    public static void check(Context context, boolean manual) {
        String url = getManifestUrl(context);
        if (url == null || url.length() == 0) {
            if (manual) {
                Toast.makeText(context, R.string.update_no_url, Toast.LENGTH_LONG).show();
            }
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST, 0);
        if (!manual && System.currentTimeMillis() - last < CHECK_INTERVAL) {
            return;
        }
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).commit();

        new CheckTask(context, manual).execute(url);
    }

    /**
     * Диалог настройки URL и ручной проверки.
     */
    public static void showSettingsDialog(final Activity activity) {
        final EditText input = new EditText(activity);
        input.setSingleLine(false);
        input.setText(getManifestUrl(activity));
        input.setHint(R.string.update_url_hint);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.updates)
                .setMessage(activity.getString(R.string.update_current, BuildConfig.VERSION_NAME))
                .setView(input)
                .setPositiveButton(R.string.update_check, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setManifestUrl(activity, input.getText().toString());
                        check(activity, true);
                    }
                })
                .setNeutralButton(R.string.update_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setManifestUrl(activity, input.getText().toString());
                        Toast.makeText(activity, R.string.update_saved, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class CheckTask extends AsyncTask<String, Void, JSONObject> {
        private final Context context;
        private final boolean manual;
        private String error;

        CheckTask(Context context, boolean manual) {
            this.context = context;
            this.manual = manual;
        }

        @Override
        protected JSONObject doInBackground(String... params) {
            try {
                String body = HttpClient.executeToString(new HttpRequest(params[0]));
                return JSON.getObject(body).getObject("android");
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(final JSONObject data) {
            if (data == null) {
                if (manual) {
                    Toast.makeText(context, context.getString(R.string.update_error, error),
                            Toast.LENGTH_LONG).show();
                }
                return;
            }

            final int code = data.getInt("versionCode", 0);
            final String url = data.getString("url", "");
            String changelog = data.getString("changelog", "");

            if (code <= BuildConfig.VERSION_CODE) {
                if (manual) {
                    Toast.makeText(context, R.string.update_current,
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }

            new AlertDialog.Builder(context)
                    .setTitle(R.string.update_available)
                    .setMessage(changelog != null && changelog.length() > 0
                            ? changelog : context.getString(R.string.update_default_changelog))
                    .setPositiveButton(R.string.update_download, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new DownloadTask(context, url).execute();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    private static class DownloadTask extends AsyncTask<Void, Void, File> {
        private final Context context;
        private final String url;

        DownloadTask(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        protected void onPreExecute() {
            Toast.makeText(context, R.string.update_downloading, Toast.LENGTH_LONG).show();
        }

        @Override
        protected File doInBackground(Void... params) {
            try {
                File file = new File(context.getFilesDir(), APK_NAME);
                HttpClient.downloadToFile(url, file.getAbsolutePath());
                return file;
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(File file) {
            if (file == null) {
                Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            install(context, file);
        }
    }

    private static void install(Context context, File file) {
        try {
            Uri uri;
            if (NotPipe.SDK >= 24) {
                uri = Uri.parse("content://" + UpdateProvider.AUTHORITY + "/" + APK_NAME);
            } else {
                uri = Uri.fromFile(file);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show();
        }
    }
}
