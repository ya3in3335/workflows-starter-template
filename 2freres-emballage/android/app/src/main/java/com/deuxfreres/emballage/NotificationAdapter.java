package com.deuxfreres.emballage;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface OnClick { void onNotification(AppNotification n); }

    private final List<AppNotification> items = new ArrayList<>();
    private final OnClick onClick;
    private long lastSeen;

    public NotificationAdapter(OnClick onClick) { this.onClick = onClick; }

    public void submit(List<AppNotification> list, long lastSeen) {
        items.clear();
        items.addAll(list);
        this.lastSeen = lastSeen;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AppNotification n = items.get(pos);
        h.title.setText(n.title);
        h.body.setText(n.body);
        h.date.setText(n.createdAt > 0
                ? DateUtils.getRelativeTimeSpanString(n.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                : "");
        h.dot.setVisibility(n.id > lastSeen ? View.VISIBLE : View.INVISIBLE);
        if (n.image != null && !n.image.isEmpty()) {
            h.image.setPadding(0, 0, 0, 0);
            ImageViewCompat.setImageTintList(h.image, null);
            Glide.with(h.image).load(n.image).circleCrop().placeholder(R.drawable.ic_bell).into(h.image);
        } else {
            int pad = (int) (12 * h.image.getResources().getDisplayMetrics().density);
            h.image.setPadding(pad, pad, pad, pad);
            Glide.with(h.image).clear(h.image);
            h.image.setImageResource(R.drawable.ic_bell);
            ImageViewCompat.setImageTintList(h.image,
                    android.content.res.ColorStateList.valueOf(h.image.getResources().getColor(R.color.brand, null)));
        }
        h.itemView.setOnClickListener(v -> onClick.onNotification(n));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image; final TextView title, body, date; final View dot;
        VH(View v) {
            super(v);
            image = v.findViewById(R.id.image);
            title = v.findViewById(R.id.title);
            body = v.findViewById(R.id.body);
            date = v.findViewById(R.id.date);
            dot = v.findViewById(R.id.dot);
        }
    }
}
