package com.pdv.app.agenda;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.FormatUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Consulta compromissos pendentes e insiste ate o usuario confirmar o lembrete. */
public final class AgendaAlertManager {
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean DIALOG_SHOWING = new AtomicBoolean(false);
    private static volatile long lastCheck = 0L;

    private AgendaAlertManager() {}

    public static void checkAndShow(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        long now = System.currentTimeMillis();
        if (DIALOG_SHOWING.get() || now - lastCheck < 25_000L
                || !CHECKING.compareAndSet(false, true)) return;
        lastCheck = now;
        new Thread(() -> {
            StringBuilder alerts = new StringBuilder();
            List<Integer> ids = new ArrayList<>();
            try {
                Connection conn = DatabaseHelper.getInstance(activity).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT a.id,a.titulo,a.data_hora,a.observacao,COALESCE(c.nome,'') cliente "
                                + "FROM agenda_servicos a LEFT JOIN clientes c ON a.cliente_id=c.id "
                                + "WHERE a.status='agendado' AND a.alertado=0 "
                                + "AND NOW()>=DATE_SUB(a.data_hora,INTERVAL a.alerta_minutos MINUTE) "
                                + "ORDER BY a.data_hora LIMIT 10");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    alerts.append("• ").append(rs.getString("titulo"));
                    String cliente = rs.getString("cliente");
                    if (cliente != null && !cliente.isEmpty()) alerts.append(" - ").append(cliente);
                    alerts.append("\n  ").append(FormatUtils.formatDate(rs.getString("data_hora"))).append("\n\n");
                }
                rs.close();
                ps.close();
            } catch (Exception ignored) {
            } finally {
                CHECKING.set(false);
            }
            if (alerts.length() > 0) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()
                            || !DIALOG_SHOWING.compareAndSet(false, true)) return;
                    AlertDialog dialog = new AlertDialog.Builder(activity)
                            .setTitle("Agenda: lembrete pendente")
                            .setMessage(alerts.toString())
                            .setPositiveButton("Confirmar lembrete", (d, w) -> confirmar(activity, ids))
                            .setNegativeButton("Lembrar em 30 segundos", null)
                            .setCancelable(false)
                            .create();
                    dialog.setOnDismissListener(d -> DIALOG_SHOWING.set(false));
                    dialog.show();
                });
            }
        }, "agenda-dialog-check").start();
    }

    private static void confirmar(Context context, List<Integer> ids) {
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(context).getConnection();
                PreparedStatement mark = conn.prepareStatement("UPDATE agenda_servicos SET alertado=1 WHERE id=?");
                NotificationManager manager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                for (Integer id : ids) {
                    mark.setInt(1, id);
                    mark.addBatch();
                    if (manager != null) manager.cancel(310000 + id);
                }
                mark.executeBatch();
                mark.close();
            } catch (Exception ignored) {}
        }, "agenda-dialog-confirm").start();
    }
}
