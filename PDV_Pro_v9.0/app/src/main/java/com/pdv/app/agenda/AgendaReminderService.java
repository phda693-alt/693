package com.pdv.app.agenda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.pdv.app.R;
import com.pdv.app.activities.AgendaActivity;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.FormatUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mantem a agenda sendo verificada a cada 30 segundos mesmo com o aplicativo fora da tela.
 * O servico e START_STICKY e tambem e restaurado pelo BootReceiver.
 */
public class AgendaReminderService extends Service {
    public static final long INTERVALO_MS = 30_000L;
    private static final String CANAL_SERVICO = "agenda_servico_persistente";
    private static final String CANAL_LEMBRETES = "agenda_lembretes_insistentes";
    private static final int NOTIFICACAO_SERVICO = 309900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean verificando = new AtomicBoolean(false);
    private final Runnable verificador = new Runnable() {
        @Override public void run() {
            verificarLembretes();
            handler.postDelayed(this, INTERVALO_MS);
        }
    };

    public static void iniciar(Context context) {
        Intent intent = new Intent(context, AgendaReminderService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context.getApplicationContext(), intent);
            } else {
                context.getApplicationContext().startService(intent);
            }
        } catch (Exception ignored) {
            // O START_STICKY/BootReceiver tentara novamente na proxima oportunidade permitida.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanais();
        startForeground(NOTIFICACAO_SERVICO, criarNotificacaoServico());
        handler.post(verificador);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(verificador);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void criarCanais() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel servico = new NotificationChannel(CANAL_SERVICO,
                "Monitor da agenda", NotificationManager.IMPORTANCE_LOW);
        servico.setDescription("Mantem os lembretes da agenda ativos em segundo plano");
        manager.createNotificationChannel(servico);

        NotificationChannel lembretes = new NotificationChannel(CANAL_LEMBRETES,
                "Lembretes insistentes da agenda", NotificationManager.IMPORTANCE_HIGH);
        lembretes.setDescription("Repete os lembretes pendentes a cada 30 segundos ate a confirmacao");
        lembretes.enableVibration(true);
        manager.createNotificationChannel(lembretes);
    }

    private Notification criarNotificacaoServico() {
        Intent abrir = new Intent(this, AgendaActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 309900, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CANAL_SERVICO)
                .setSmallIcon(R.drawable.ic_history)
                .setContentTitle("Agenda protegida")
                .setContentText("Lembretes verificados a cada 30 segundos")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void verificarLembretes() {
        if (!verificando.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT a.id,a.titulo,a.data_hora,a.observacao,COALESCE(c.nome,'') cliente "
                                + "FROM agenda_servicos a LEFT JOIN clientes c ON a.cliente_id=c.id "
                                + "WHERE a.status='agendado' AND a.alertado=0 "
                                + "AND NOW()>=DATE_SUB(a.data_hora,INTERVAL a.alerta_minutos MINUTE) "
                                + "ORDER BY a.data_hora LIMIT 20");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    publicarLembrete(rs.getInt("id"), rs.getString("titulo"),
                            rs.getString("cliente"), rs.getString("data_hora"), rs.getString("observacao"));
                }
                rs.close();
                ps.close();
            } catch (Exception ignored) {
                // Banco/servidor pode ainda estar iniciando; nova tentativa ocorre em 30 segundos.
            } finally {
                verificando.set(false);
            }
        }, "agenda-reminder-check").start();
    }

    private void publicarLembrete(int id, String titulo, String cliente, String dataHora, String observacao) {
        Intent abrir = new Intent(this, AgendaActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, id, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent confirmar = new Intent(this, AgendaReminderActionReceiver.class);
        confirmar.setAction(AgendaReminderActionReceiver.ACTION_CONFIRMAR);
        confirmar.putExtra(AgendaReminderActionReceiver.EXTRA_AGENDA_ID, id);
        PendingIntent confirmarIntent = PendingIntent.getBroadcast(this, 400000 + id, confirmar,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        StringBuilder mensagem = new StringBuilder();
        if (cliente != null && !cliente.trim().isEmpty()) mensagem.append(cliente.trim()).append(" - ");
        mensagem.append(FormatUtils.formatDate(dataHora));
        if (observacao != null && !observacao.trim().isEmpty()) mensagem.append("\n").append(observacao.trim());

        Notification notificacao = new NotificationCompat.Builder(this, CANAL_LEMBRETES)
                .setSmallIcon(R.drawable.ic_history)
                .setContentTitle("Agenda: " + titulo)
                .setContentText(mensagem.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensagem.toString()))
                .setContentIntent(content)
                .addAction(R.drawable.ic_check_circle, "CONFIRMAR LEMBRETE", confirmarIntent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(false)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Cancelar antes de republicar faz som/vibracao insistirem em cada ciclo de 30 segundos.
            manager.cancel(310000 + id);
            manager.notify(310000 + id, notificacao);
        }
    }
}
