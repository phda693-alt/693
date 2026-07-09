package com.pdv.app.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.pdv.app.R;
import com.pdv.app.utils.BackupManager;

public class BackupService extends Service {
    private static final String TAG = "BackupService";
    private static final String CHANNEL_ID = "backup_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("Realizando backup automatico...");
        startForeground(1, notification);

        new Thread(() -> {
            try {
                BackupManager manager = new BackupManager(getApplicationContext());

                if (manager.isAutoBackupEnabled()) {
                    // Backup JSON (formato original - sempre executado quando auto backup ativo)
                    boolean jsonOk = manager.realizarBackupJson();
                    Log.d(TAG, "Backup automatico JSON: " + (jsonOk ? "sucesso" : "falha"));

                    // Backup SQL (dump MySQL - executado se habilitado nas configuracoes)
                    if (manager.isSqlBackupEnabled()) {
                        updateNotification("Gerando dump SQL e enviando ao FTP...");
                        boolean sqlOk = manager.realizarBackupSql();
                        Log.d(TAG, "Backup automatico SQL: " + (sqlOk ? "sucesso" : "falha"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro no backup automatico", e);
            } finally {
                stopForeground(true);
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Backup PDV", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificacoes de backup automatico");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.notify(1, buildNotification(text));
            }
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel atualizar notificacao: " + e.getMessage());
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("PDV Pro - Backup")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .build();
    }
}
