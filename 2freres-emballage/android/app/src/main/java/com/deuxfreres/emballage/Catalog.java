package com.deuxfreres.emballage;

import java.util.ArrayList;
import java.util.List;

/** Contenu renvoyé par l'API : produits + notifications. */
public class Catalog {
    public final List<Product> products = new ArrayList<>();
    public final List<AppNotification> notifications = new ArrayList<>();

    long maxNotificationId() {
        long max = 0;
        for (AppNotification n : notifications) max = Math.max(max, n.id);
        return max;
    }

    Product product(int id) {
        for (Product p : products) if (p.id == id) return p;
        return null;
    }
}
