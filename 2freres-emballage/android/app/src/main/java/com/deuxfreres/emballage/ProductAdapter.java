package com.deuxfreres.emballage;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {

    public interface OnClick { void onProduct(Product p); }

    private final List<Product> items = new ArrayList<>();
    private final OnClick onClick;

    public ProductAdapter(OnClick onClick) { this.onClick = onClick; }

    public void submit(List<Product> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Product p = items.get(pos);
        Glide.with(h.image).load(p.image).placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder).centerCrop().into(h.image);
        h.name.setText(p.name);
        h.price.setText(Utils.price(p.price));
        h.badge.setVisibility(p.isOffer ? View.VISIBLE : View.GONE);
        if (p.hasDiscount()) {
            h.oldPrice.setVisibility(View.VISIBLE);
            h.oldPrice.setText(Utils.price(p.oldPrice));
            h.oldPrice.setPaintFlags(h.oldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            h.oldPrice.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> onClick.onProduct(p));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image; final TextView name, price, oldPrice, badge;
        VH(View v) {
            super(v);
            image = v.findViewById(R.id.image);
            name = v.findViewById(R.id.name);
            price = v.findViewById(R.id.price);
            oldPrice = v.findViewById(R.id.oldPrice);
            badge = v.findViewById(R.id.badge);
        }
    }
}
