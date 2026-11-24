package io.github.gohoski.notpipe;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created on 18.11.2026.
 * Сохраняет отчёт о падении и открывает экран отправки.
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    public static final String PREFS = "notpipe_crash";
    public static final String KEY_URL = "report_url";

    private static boolean handling;
    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashHandler(Context context, Thread.UncaughtExceptionHandler previous) {
        this.context = context;
        this.previous = previous;
    }

    public static void install(Context context) {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (!(current instanceof CrashHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context.getApplicationContext(), current));
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        if (handling) {
            if (previous != null) previous.uncaughtException(thread, throwable);
            return;
        }
        handling = true;

        try {
            String report = buildReport(throwable);
            File dir = new File(context.getFilesDir(), "crashes");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "last_crash.txt");
            FileWriter writer = new FileWriter(file, false);
            writer.write(report);
            writer.close();

            Intent intent = new Intent(context, CrashActivity.class);
            intent.putExtra("path", file.getAbsolutePath());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {
        }

        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {
        }
        Process.killProcess(Process.myPid());
        System.exit(10);
    }

    private String buildReport(Throwable throwable) {
        StringWriter stack = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stack));
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        return "CrimTube crash report\n"
                + "time: " + date + "\n"
                + "version: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "thread: " + Thread.currentThread().getName() + "\n\n"
                + stack.toString();
    }
}
