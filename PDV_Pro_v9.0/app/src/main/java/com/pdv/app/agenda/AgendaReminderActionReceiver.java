package com.pdv.app.agenda;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.pdv.app.database.DatabaseHelper;

import java.sql.PreparedStatement;

/** Recebe a confirmacao do lembrete diretamente pela notificacao. */
public class AgendaReminderActionReceiver extends BroadcastReceiver {
    public static final String ACTION_CONFIRMAR = "com.pdv.app.action.CONFIRMAR_LEMBRETE_AGENDA";
    public static final String EXTRA_AGENDA_ID = "agenda_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CONFIRMAR.equals(intent.getAction())) return;
        int agendaId = intent.getIntExtra(EXTRA_AGENDA_ID, 0);
        if (agendaId <= 0) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                PreparedStatement ps = DatabaseHelper.getInstance(context).getConnection()
                        .prepareStatement("UPDATE agenda_servicos SET alertado=1 WHERE id=?");
                ps.setInt(1, agendaId);
                ps.executeUpdate();
                ps.close();
                NotificationManager manager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) manager.cancel(310000 + agendaId);
            } catch (Exception ignored) {
            } finally {
                pending.finish();
            }
        }, "agenda-reminder-confirm").start();
    }
}
