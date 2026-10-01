package com.deuxfreres.emballage;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Vérifie régulièrement (≈15 min) s'il y a des nouveautés et notifie l'utilisateur. */
public class NotifyWorker extends Worker {

    public NotifyWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Catalog c = new ProductRepository(getApplicationContext()).fetch();
            Notifier.notifyNew(getApplicationContext(), c);
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
