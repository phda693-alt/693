package com.pdv.app.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import com.pdv.app.receivers.PhServerAutoStartReceiver;

/**
 * Fachada de compatibilidade para o servidor MariaDB do PDV Pro.
 *
 * <p>Nas versoes anteriores esta classe controlava o APK PHSERVER separado.
 * Agora ela aponta para o MariaDB embutido, mantendo as chamadas antigas do
 * app sem exigir instalacao de outro aplicativo.</p>
 */
public final class MariaDbServerManager {

    private static final String TAG = "MariaDbServerManager";

    private MariaDbServerManager() { /* utility */ }

    public static boolean isInstalled(Context ctx) {
        return EmbeddedMariaDbManager.isEmbeddedAvailable(ctx);
    }

    public static boolean openServer(Context ctx) {
        return tryAutoStart(ctx, false);
    }

    public static boolean openCreateDatabase(Context ctx) {
        try {
            Intent intent = new Intent(ctx, com.phda.phserver.CreateDatabaseActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir tela interna de banco", e);
            return false;
        }
    }

    public static boolean openManageUsers(Context ctx) {
        try {
            Intent intent = new Intent(ctx, com.phda.phserver.ManageUsersActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir tela interna de usuarios MySQL", e);
            return false;
        }
    }

    /**
     * Mantido por compatibilidade: nao instala outro APK; inicia o servidor interno.
     */
    public static boolean installFromAssets(Context ctx) {
        return tryAutoStart(ctx, false);
    }

    public static boolean sendStartBroadcast(Context ctx) {
        return tryAutoStart(ctx, false);
    }

    public static boolean tryAutoStart(Context ctx, boolean allowUiLaunch) {
        try {
            EmbeddedMariaDbManager.ensureLocalDatabaseConfig(ctx, false);
            EmbeddedMariaDbManager.startService(ctx);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar MariaDB interno", e);
            return false;
        }
    }

    public static boolean ensureReadyBlocking(Context ctx,
            EmbeddedMariaDbManager.StatusCallback callback) {
        return EmbeddedMariaDbManager.ensureStarted(ctx, callback);
    }

    public static String getLastStatus(Context ctx) {
        return EmbeddedMariaDbManager.getLastStatus(ctx);
    }

    public static void scheduleAutoStartSupervisor(Context ctx, long delayMillis, int attempt) {
        try {
            AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(ctx, PhServerAutoStartReceiver.class);
            intent.setAction(PhServerAutoStartReceiver.ACTION_SUPERVISE_PHSERVER);
            intent.putExtra(PhServerAutoStartReceiver.EXTRA_ATTEMPT, attempt);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(ctx, 9100 + attempt, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            long triggerAt = SystemClock.elapsedRealtime() + Math.max(5_000L, delayMillis);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            }
            Log.d(TAG, "Supervisor MariaDB interno agendado. tentativa=" + attempt);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao agendar supervisor MariaDB interno", e);
        }
    }

    public static void scheduleBootAutoStart(Context ctx) {
        tryAutoStart(ctx, false);
        scheduleAutoStartSupervisor(ctx, 20_000L, 1);
        scheduleAutoStartSupervisor(ctx, 120_000L, 2);
        scheduleAutoStartSupervisor(ctx, 300_000L, 3);
    }

    public static Intent buildBatterySettingsIntent(Context ctx) {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        details.setData(Uri.parse("package:" + ctx.getPackageName()));
        details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return details;
    }
}
