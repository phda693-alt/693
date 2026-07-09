package com.pdv.app.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.pdv.app.server.MariaDbServerManager;

public class PhServerAutoStartReceiver extends BroadcastReceiver {

    public static final String ACTION_SUPERVISE_PHSERVER =
            "com.pdv.app.action.SUPERVISE_PHSERVER_AUTOSTART";
    public static final String EXTRA_ATTEMPT = "attempt";

    private static final String TAG = "MariaDbAutoStart";
    private static final String PREFS = "phserver_autostart";
    private static final String KEY_LAST_RESULT = "last_result";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";
    private static final int MAX_EXTRA_ATTEMPTS = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        int attempt = intent != null ? intent.getIntExtra(EXTRA_ATTEMPT, 1) : 1;

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || ACTION_SUPERVISE_PHSERVER.equals(action)) {
            supervise(context, attempt);
        }
    }

    public static String getLastResult(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_RESULT, "Sem tentativa registrada nesta instalacao.");
    }

    private static void supervise(Context context, int attempt) {
        boolean installed = MariaDbServerManager.isInstalled(context);
        boolean started = installed && MariaDbServerManager.tryAutoStart(context, false);
        String result = "Tentativa " + attempt
                + " | integrado=" + installed
                + " | acionado=" + started
                + " | supervisor MariaDB ativo";

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_LAST_RESULT, result)
                .putInt(KEY_LAST_ATTEMPT, attempt)
                .apply();

        Log.d(TAG, result);

        if (installed && attempt < MAX_EXTRA_ATTEMPTS) {
            long delay = attempt == 1 ? 120_000L : 300_000L;
            MariaDbServerManager.scheduleAutoStartSupervisor(context, delay, attempt + 1);
        }
    }
}
