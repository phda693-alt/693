package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Registra navegacao e acoes que nao produzem alteracao direta no banco. */
public final class UserActionLogger {
    private static final String TAG = "UserActionLogger";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UserActionLogger() {}

    public static void log(Context context, String acao, String modulo, String detalhes) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences session = app.getSharedPreferences("session", Context.MODE_PRIVATE);
        final int userId = session.getInt("user_id", 0);
        final String userName = session.getString("user_nome", "Sistema");
        EXECUTOR.execute(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(app).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO auditoria_acoes "
                                + "(usuario_id,usuario_nome,acao,modulo,detalhes,ip,data_acao) "
                                + "VALUES (?,?,?,?,?,?,NOW())");
                if (userId > 0) ps.setInt(1, userId); else ps.setNull(1, java.sql.Types.INTEGER);
                ps.setString(2, userName);
                ps.setString(3, acao);
                ps.setString(4, modulo);
                ps.setString(5, detalhes);
                ps.setString(6, NetworkUtils.getLocalIpv4());
                ps.executeUpdate();
                ps.close();
            } catch (Exception e) {
                Log.d(TAG, "Auditoria ainda indisponivel: " + e.getMessage());
            }
        });
    }
}
