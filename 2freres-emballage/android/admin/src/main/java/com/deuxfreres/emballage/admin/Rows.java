package com.deuxfreres.emballage.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Liste générique (image, titre, sous-titre, info, pastille) utilisée par tous les écrans. */
final class Rows extends RecyclerView.Adapter<Rows.VH> {

    static final class Row {
        String title = "", subtitle = "", meta = "", image = "", badge = "";
        int badgeColor = 0xFFE0457B;
        int icon = 0;
        JSONObject data;
    }

    interface Click { void on(Row r); }

    private final List<Row> rows = new ArrayList<>();
    private final Click click, longClick;

    Rows(Click click, Click longClick) { this.click = click; this.longClick = longClick; }

    void submit(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_row, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Row r = rows.get(i);
        h.title.setText(r.title);
        h.subtitle.setText(r.subtitle);
        h.subtitle.setVisibility(r.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        h.meta.setText(r.meta);
        h.meta.setVisibility(r.meta.isEmpty() ? View.GONE : View.VISIBLE);
        h.badge.setText(r.badge);
        h.badge.setVisibility(r.badge.isEmpty() ? View.GONE : View.VISIBLE);
        h.badge.getBackground().mutate().setTint(r.badgeColor);
        if (!r.image.isEmpty()) {
            h.image.setVisibility(View.VISIBLE);
            h.image.setPadding(0, 0, 0, 0);
            Glide.with(h.image).load(r.image).centerCrop().into(h.image);
        } else if (r.icon != 0) {
            h.image.setVisibility(View.VISIBLE);
            int pad = (int) (14 * h.image.getResources().getDisplayMetrics().density);
            h.image.setPadding(pad, pad, pad, pad);
            Glide.with(h.image).clear(h.image);
            h.image.setImageResource(r.icon);
        } else {
            h.image.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> { if (click != null) click.on(r); });
        h.itemView.setOnLongClickListener(v -> { if (longClick == null) return false; longClick.on(r); return true; });
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView image; final TextView title, subtitle, meta, badge;
        VH(View v) {
            super(v);
            image = v.findViewById(R.id.image);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            meta = v.findViewById(R.id.meta);
            badge = v.findViewById(R.id.badge);
        }
    }
}
