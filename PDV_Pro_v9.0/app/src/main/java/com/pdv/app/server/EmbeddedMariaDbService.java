package com.pdv.app.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.pdv.app.R;
import com.pdv.app.activities.MainActivity;

public class EmbeddedMariaDbService extends Service {

    private static final String TAG = "EmbeddedMariaDbService";
    private static final String CHANNEL_ID = "pdv_mariadb_service";
    private static final int NOTIFICATION_ID = 3306;

    private volatile boolean workerStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando banco local..."));
        if (!workerStarted) {
            workerStarted = true;
            Thread worker = new Thread(() -> {
                try {
                    boolean started = EmbeddedMariaDbManager.ensureStarted(this, message ->
                            updateNotification(message));
                    if (started) {
                        EmbeddedMariaDbManager.provisionPdvDatabase(this, message ->
                                updateNotification(message));
                        updateNotification("Banco local ativo e pronto.");
                    } else {
                        updateNotification("Banco local nao iniciou. Verifique diagnostico.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Falha no servico MariaDB embutido", e);
                    updateNotification("Falha no banco local: " + e.getMessage());
                }
            }, "EmbeddedMariaDb-worker");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("PDV Pro - Banco local")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Banco local PDV Pro",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantem o MariaDB interno do PDV Pro em execucao.");
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }
}
