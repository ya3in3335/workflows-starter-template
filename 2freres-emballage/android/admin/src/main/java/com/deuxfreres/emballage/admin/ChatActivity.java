package com.deuxfreres.emballage.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Discussion avec un client. Chaque réponse arrive aussi en notification chez ce client seulement. */
public class ChatActivity extends AppCompatActivity {
    static final String EXTRA_USER = "userId", EXTRA_NAME = "username";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JSONObject> items = new ArrayList<>();
    private int userId;
    private RecyclerView list;
    private TextView empty;
    private EditText input;
    private View send;
    private final Runnable poll = new Runnable() {
        @Override public void run() { load(); main.postDelayed(this, 7000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        userId = getIntent().getIntExtra(EXTRA_USER, 0);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("👤 " + getIntent().getStringExtra(EXTRA_NAME));
        toolbar.setNavigationOnClickListener(v -> finish());
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setAdapter(new Adapter());
        send.setOnClickListener(v -> reply());
    }

    @Override protected void onResume() { super.onResume(); main.post(poll); }
    @Override protected void onPause() { super.onPause(); main.removeCallbacks(poll); }

    private void load() {
        Api.async(this, "GET", "/api/admin/support/" + userId, null, r -> show(r.optJSONArray("messages")), null);
    }

    private void show(JSONArray arr) {
        if (isDestroyed() || arr == null) return;
        boolean grew = arr.length() != items.size();
        items.clear();
        for (int i = 0; i < arr.length(); i++) items.add(arr.optJSONObject(i));
        list.getAdapter().notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        if (grew && !items.isEmpty()) list.scrollToPosition(items.size() - 1);
    }

    private void reply() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        send.setEnabled(false);
        JSONObject b = new JSONObject();
        try { b.put("text", text).put("notify", true); } catch (Exception ignored) {}
        Api.async(this, "POST", "/api/admin/support/" + userId, b, r -> {
            send.setEnabled(true);
            input.setText("");
            show(r.optJSONArray("messages"));
        }, e -> { send.setEnabled(true); Ui.error(this, e); });
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_message, p, false)) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            JSONObject m = items.get(pos);
            boolean mine = m.optBoolean("fromAdmin");
            ((LinearLayout) h.itemView.findViewById(R.id.row)).setGravity(mine ? Gravity.END : Gravity.START);
            h.itemView.findViewById(R.id.bubble).setBackgroundResource(mine ? R.drawable.bg_bubble_me : R.drawable.bg_bubble_other);
            TextView text = h.itemView.findViewById(R.id.text);
            TextView time = h.itemView.findViewById(R.id.time);
            text.setText(m.optString("text"));
            text.setTextColor(mine ? 0xFFFFFFFF : 0xFF212121);
            time.setTextColor(mine ? 0xB3FFFFFF : 0xFF9E9E9E);
            time.setText(Ui.ago(m.optLong("createdAt")));
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
