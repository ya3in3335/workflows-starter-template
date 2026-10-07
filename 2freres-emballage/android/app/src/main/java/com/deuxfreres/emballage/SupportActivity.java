package com.deuxfreres.emballage;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Discussion privée entre le client et le magasin. */
public class SupportActivity extends AppCompatActivity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<JSONObject> items = new ArrayList<>();
    private RecyclerView list;
    private TextView empty;
    private EditText input;
    private View send;
    private final Runnable poll = new Runnable() {
        @Override public void run() { load(); main.postDelayed(this, 8000); }
    };

    private final ActivityResultLauncher<Intent> login =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (!Session.loggedIn(this)) finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setSubtitle(Config.STORE_NAME);
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setAdapter(new Adapter());
        send.setOnClickListener(v -> sendMessage());

        if (!Session.loggedIn(this)) {
            Toast.makeText(this, R.string.login_to_chat, Toast.LENGTH_SHORT).show();
            login.launch(new Intent(this, LoginActivity.class));
        }
    }

    @Override protected void onResume() { super.onResume(); main.post(poll); }
    @Override protected void onPause() { super.onPause(); main.removeCallbacks(poll); }

    private void load() {
        String token = Session.token(this);
        if (token == null) return;
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject r = ApiClient.call("GET", "/api/support", null, token);
                main.post(() -> show(r.optJSONArray("messages")));
            } catch (ApiClient.ApiException e) {
                if (e.status == 401) main.post(() -> { Session.clear(this); login.launch(new Intent(this, LoginActivity.class)); });
            } catch (Exception ignored) {
            }
        });
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

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        String token = Session.token(this);
        if (token == null) { login.launch(new Intent(this, LoginActivity.class)); return; }
        send.setEnabled(false);
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject r = ApiClient.call("POST", "/api/support", new JSONObject().put("text", text), token);
                main.post(() -> { send.setEnabled(true); input.setText(""); show(r.optJSONArray("messages")); });
            } catch (Exception e) {
                main.post(() -> {
                    send.setEnabled(true);
                    boolean banned = e instanceof ApiClient.ApiException && "banned".equals(((ApiClient.ApiException) e).code);
                    Toast.makeText(this, banned ? R.string.err_banned : R.string.offline, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_message, p, false)) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            JSONObject m = items.get(pos);
            boolean mine = !m.optBoolean("fromAdmin");
            LinearLayout row = h.itemView.findViewById(R.id.row);
            View bubble = h.itemView.findViewById(R.id.bubble);
            TextView text = h.itemView.findViewById(R.id.text);
            TextView time = h.itemView.findViewById(R.id.time);
            row.setGravity(mine ? Gravity.END : Gravity.START);
            bubble.setBackgroundResource(mine ? R.drawable.bg_bubble_me : R.drawable.bg_bubble_other);
            text.setText(m.optString("text"));
            text.setTextColor(mine ? 0xFFFFFFFF : 0xFF212121);
            time.setTextColor(mine ? 0xB3FFFFFF : 0xFF9E9E9E);
            time.setText(DateUtils.getRelativeTimeSpanString(m.optLong("createdAt"), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
