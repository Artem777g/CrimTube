package io.github.gohoski.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import io.github.gohoski.notpipe.util.ImageLoader;
import io.github.gohoski.notpipe.util.SubscriptionsStore;

/**
 * Created on 24.11.2026.
 * Современный список подписок.
 */
public class ModernSubscriptionsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modern_list);
        ((TextView) findViewById(R.id.modern_list_title)).setText(R.string.subscriptions);
        findViewById(R.id.modern_list_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        final List<SubscriptionsStore.SubscribedChannel> channels = SubscriptionsStore.list(this);
        ListView list = (ListView) findViewById(R.id.modern_list);
        list.setAdapter(new ChannelAdapter(channels));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(ModernSubscriptionsActivity.this, ChannelActivity.class);
                intent.putExtra("ID", channels.get(position).id);
                startActivity(intent);
            }
        });

        if (channels.isEmpty()) {
            TextView empty = (TextView) findViewById(R.id.modern_list_empty);
            empty.setText(R.string.subscriptions_empty);
            empty.setVisibility(View.VISIBLE);
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        private final List<SubscriptionsStore.SubscribedChannel> channels;

        ChannelAdapter(List<SubscriptionsStore.SubscribedChannel> channels) {
            this.channels = channels;
        }

        @Override
        public int getCount() {
            return channels.size();
        }

        @Override
        public Object getItem(int position) {
            return channels.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(ModernSubscriptionsActivity.this)
                        .inflate(R.layout.item_modern_channel, parent, false);
            }
            SubscriptionsStore.SubscribedChannel channel = channels.get(position);
            ((TextView) row.findViewById(R.id.channel_name)).setText(channel.title);
            ImageView avatar = (ImageView) row.findViewById(R.id.channel_avatar);
            avatar.setImageBitmap(null);
            ImageLoader.loadImage(channel.avatar, avatar);
            return row;
        }
    }
}
