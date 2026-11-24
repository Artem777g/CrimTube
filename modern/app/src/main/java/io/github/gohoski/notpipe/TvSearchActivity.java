package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 15.11.2026.
 * Поиск на TV: экранная клавиатура, управление пультом.
 */
public class TvSearchActivity extends Activity {
    private final StringBuilder query = new StringBuilder();
    private TextView queryView;
    private final List<String> keys = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tv_search);

        queryView = (TextView) findViewById(R.id.tv_query);
        queryView.setText(R.string.tv_search_hint);

        addKeys("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ");
        addKeys("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        addKeys("0123456789");
        keys.add("Пробел");
        keys.add("⌫");

        GridView keyboard = (GridView) findViewById(R.id.tv_keyboard);
        keyboard.setAdapter(new KeyAdapter());
        keyboard.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String key = keys.get(position);
                if ("⌫".equals(key)) {
                    if (query.length() > 0) {
                        query.deleteCharAt(query.length() - 1);
                    }
                } else if ("Пробел".equals(key)) {
                    query.append(' ');
                } else {
                    query.append(key);
                }
                showQuery();
            }
        });

        Button go = (Button) findViewById(R.id.tv_search_go);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = query.toString().trim();
                if (text.length() == 0) {
                    Toast.makeText(TvSearchActivity.this, R.string.tv_search_hint, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent result = new Intent();
                result.putExtra("query", text);
                setResult(RESULT_OK, result);
                finish();
            }
        });

        keyboard.requestFocus();
    }

    private void addKeys(String chars) {
        for (int i = 0; i < chars.length(); i++) {
            keys.add(String.valueOf(chars.charAt(i)));
        }
    }

    private void showQuery() {
        queryView.setText(query.length() == 0 ? getString(R.string.tv_search_hint) : query.toString());
    }

    private class KeyAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return keys.size();
        }

        @Override
        public Object getItem(int position) {
            return keys.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = (TextView) LayoutInflater.from(TvSearchActivity.this)
                        .inflate(R.layout.item_tv_key, parent, false);
            } else {
                view = (TextView) convertView;
            }
            view.setText(keys.get(position));
            return view;
        }
    }
}
