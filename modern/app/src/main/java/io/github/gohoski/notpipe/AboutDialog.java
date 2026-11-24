package io.github.gohoski.notpipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created on 18.11.2026.
 * О приложении: версия, автор, почта и лицензия.
 */
public final class AboutDialog {
    private AboutDialog() {
    }

    public static void show(final Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(15, 15, 15, 15);

        final TextView app = new TextView(activity);
        app.setText(activity.getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        app.setTypeface(null, Typeface.BOLD);
        app.setTextSize(20);
        app.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(app);

        final ImageView imageView = new ImageView(activity);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.FILL_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        imageView.setImageResource(R.drawable.main_icon);
        layout.addView(imageView);

        final TextView text = new TextView(activity);
        text.setText(activity.getString(R.string.about_));
        layout.addView(text);

        final TextView contact = new TextView(activity);
        contact.setText(activity.getString(R.string.contact_email, "artm.golovkov.00@inbox.ru"));
        contact.setTextSize(15);
        contact.setPadding(0, 12, 0, 0);
        contact.setTextColor(0xFF4FC3F7);
        contact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent mail = new Intent(Intent.ACTION_SENDTO,
                            android.net.Uri.parse("mailto:artm.golovkov.00@inbox.ru"));
                    activity.startActivity(mail);
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        layout.addView(contact);

        final TextView license = new TextView(activity);
        license.setText("\n\nMIT License\n" +
                "\n" +
                "Copyright (c) 2021-2025 Arman Jussupgaliyev\n" +
                "\n" +
                "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                "of this software and associated documentation files (the \"Software\"), to deal\n" +
                "in the Software without restriction, including without limitation the rights\n" +
                "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
                "copies of the Software, and to permit persons to whom the Software is\n" +
                "furnished to do so, subject to the following conditions:\n" +
                "\n" +
                "The above copyright notice and this permission notice shall be included in all\n" +
                "copies or substantial portions of the Software.\n" +
                "\n" +
                "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                "SOFTWARE.");
        license.setTextSize(12);
        layout.addView(license);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(layout);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.app_name)
                .setView(scrollView)
                .setNeutralButton(android.R.string.ok, null).show();
    }
}
