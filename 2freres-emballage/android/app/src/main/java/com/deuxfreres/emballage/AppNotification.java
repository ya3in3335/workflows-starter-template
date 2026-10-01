package com.deuxfreres.emballage;

import org.json.JSONObject;

import java.io.Serializable;
import java.time.Instant;

/** Notification publiée par le magasin (nouveau produit, promo, message). */
public class AppNotification implements Serializable {
    public long id;
    public String title;
    public String body;
    public int productId;
    public String image;
    public long createdAt;

    static AppNotification from(JSONObject o) {
        AppNotification n = new AppNotification();
        n.id = o.optLong("id");
        n.title = o.optString("title");
        n.body = o.optString("body");
        n.productId = o.optInt("productId");
        n.image = o.optString("image");
        try {
            n.createdAt = Instant.parse(o.optString("createdAt")).toEpochMilli();
        } catch (Exception e) {
            n.createdAt = 0;
        }
        return n;
    }
}
