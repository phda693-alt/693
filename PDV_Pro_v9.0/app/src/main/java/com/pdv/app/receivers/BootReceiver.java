package com.pdv.app.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.pdv.app.server.MariaDbServerManager;
import com.pdv.app.agenda.AgendaReminderService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            scheduleBackup(context);
            MariaDbServerManager.scheduleBootAutoStart(context);
            AgendaReminderService.iniciar(context);
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            MariaDbServerManager.scheduleBootAutoStart(context);
            AgendaReminderService.iniciar(context);
        }
    }

    public static void scheduleBackup(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, BackupAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Schedule every hour
        long interval = AlarmManager.INTERVAL_HOUR;
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + interval, interval, pendingIntent);
    }
}
