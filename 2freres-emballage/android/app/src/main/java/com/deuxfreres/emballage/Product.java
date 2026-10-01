package com.deuxfreres.emballage;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.Locale;

public class Product implements Serializable {
    public int id;
    public String name;
    public double price;
    public double oldPrice;
    public String category;
    public String description;
    public String image;
    public boolean isOffer;

    static Product from(JSONObject o) {
        Product p = new Product();
        p.id = o.optInt("id");
        p.name = o.optString("name");
        p.price = o.optDouble("price", 0);
        p.oldPrice = o.optDouble("oldPrice", 0);
        p.category = o.optString("category").trim();
        p.description = o.optString("description").trim();
        p.image = o.optString("image");
        p.isOffer = o.optBoolean("isOffer");
        return p;
    }

    boolean hasDiscount() {
        return isOffer && oldPrice > price;
    }

    String searchText() {
        return (name + " " + category + " " + description).toLowerCase(Locale.ROOT);
    }
}
