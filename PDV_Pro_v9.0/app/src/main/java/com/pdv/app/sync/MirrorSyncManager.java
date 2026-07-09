package com.pdv.app.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Replica estrutura e dados do banco principal para um MySQL externo. */
public final class MirrorSyncManager {
    private static final String TAG = "MirrorSync";
    private static final String PREFS = "mirror_db_config";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static volatile long lastScheduledAt = 0L;

    public interface Callback {
        void onProgress(String message);
        void onComplete(boolean success, String message);
    }

    private MirrorSyncManager() {}

    public static void saveConfig(Context context, boolean enabled, String host, int port,
                                  String database, String user, String password) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", enabled)
                .putString("host", host)
                .putInt("port", port)
                .putString("database", database)
                .putString("user", user)
                .putString("password", password)
                .apply();
    }

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void scheduleSync(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.getBoolean("enabled", false)) return;
        long now = System.currentTimeMillis();
        if (now - lastScheduledAt < 30_000L || RUNNING.get()) return;
        lastScheduledAt = now;
        syncNow(context.getApplicationContext(), null);
    }

    public static void syncNow(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            if (callback != null) callback.onComplete(false, "Ja existe uma sincronizacao em andamento.");
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                SyncResult result = performSync(app, callback);
                getPrefs(app).edit()
                        .putLong("last_sync", System.currentTimeMillis())
                        .putString("last_status", result.message)
                        .apply();
                if (callback != null) callback.onComplete(true, result.message);
            } catch (Exception e) {
                String message = "Banco principal preservado. Espelho indisponivel: " + friendly(e);
                Log.e(TAG, message, e);
                getPrefs(app).edit().putString("last_status", message).apply();
                if (callback != null) callback.onComplete(false, message);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    public static boolean testConnection(Context context) {
        Connection conn = null;
        try {
            ensureDatabase(context);
            conn = openMirror(context);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            boolean ok = rs.next();
            rs.close();
            stmt.close();
            return ok;
        } catch (Exception e) {
            Log.w(TAG, "Teste do espelho falhou: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    private static SyncResult performSync(Context context, Callback callback) throws Exception {
        ensureDatabase(context);
        Connection source = DatabaseHelper.getInstance(context).getConnection();
        Connection target = openMirror(context);
        int tables = 0;
        long rows = 0;
        try {
            Statement settings = target.createStatement();
            settings.execute("SET FOREIGN_KEY_CHECKS=0");
            settings.execute("SET NAMES utf8mb4");
            settings.close();

            List<String> tableNames = new ArrayList<>();
            Statement listStmt = source.createStatement();
            ResultSet tableRs = listStmt.executeQuery("SHOW TABLES");
            while (tableRs.next()) tableNames.add(tableRs.getString(1));
            tableRs.close();
            listStmt.close();

            for (String table : tableNames) {
                if (callback != null) callback.onProgress("Sincronizando " + table + "...");
                createRemoteTable(source, target, table);
                rows += syncTable(source, target, table);
                tables++;
            }
            Statement done = target.createStatement();
            done.execute("SET FOREIGN_KEY_CHECKS=1");
            done.close();
        } finally {
            try { target.close(); } catch (Exception ignored) {}
        }
        return new SyncResult(tables + " tabelas e " + rows + " registros sincronizados.");
    }

    private static void createRemoteTable(Connection source, Connection target, String table) throws Exception {
        Statement stmt = source.createStatement();
        ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE " + quote(table));
        if (rs.next()) {
            String ddl = rs.getString(2).replaceFirst("CREATE TABLE", "CREATE TABLE IF NOT EXISTS");
            Statement remote = target.createStatement();
            remote.executeUpdate(ddl);
            remote.close();
        }
        rs.close();
        stmt.close();
    }

    private static long syncTable(Connection source, Connection target, String table) throws Exception {
        Statement read = source.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        read.setFetchSize(200);
        ResultSet rs = read.executeQuery("SELECT * FROM " + quote(table));
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        if (columns == 0) { rs.close(); read.close(); return 0; }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(quote(table)).append(" (");
        StringBuilder values = new StringBuilder();
        StringBuilder updates = new StringBuilder();
        for (int i = 1; i <= columns; i++) {
            String col = md.getColumnName(i);
            if (i > 1) { sql.append(','); values.append(','); }
            sql.append(quote(col));
            values.append('?');
            if (i > 1) updates.append(',');
            updates.append(quote(col)).append("=VALUES(").append(quote(col)).append(')');
        }
        sql.append(") VALUES (").append(values).append(") ON DUPLICATE KEY UPDATE ").append(updates);

        PreparedStatement write = target.prepareStatement(sql.toString());
        int batch = 0;
        long count = 0;
        while (rs.next()) {
            for (int i = 1; i <= columns; i++) write.setObject(i, rs.getObject(i));
            write.addBatch();
            batch++;
            count++;
            if (batch >= 200) { write.executeBatch(); batch = 0; }
        }
        if (batch > 0) write.executeBatch();
        write.close();
        rs.close();
        read.close();
        return count;
    }

    private static void ensureDatabase(Context context) throws Exception {
        SharedPreferences p = getPrefs(context);
        String host = p.getString("host", "").trim();
        String db = p.getString("database", "").trim();
        String user = p.getString("user", "").trim();
        if (host.isEmpty() || db.isEmpty() || user.isEmpty()) {
            throw new IllegalStateException("Preencha host, banco e usuario do MySQL espelho.");
        }
        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://" + host + ":" + p.getInt("port", 3306)
                + "/?useSSL=false&connectTimeout=8000&socketTimeout=15000"
                + "&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
        Connection server = DriverManager.getConnection(url, user, p.getString("password", ""));
        Statement stmt = server.createStatement();
        stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + quote(db)
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        stmt.close();
        server.close();
    }

    private static Connection openMirror(Context context) throws Exception {
        SharedPreferences p = getPrefs(context);
        String url = "jdbc:mysql://" + p.getString("host", "").trim() + ":"
                + p.getInt("port", 3306) + "/" + p.getString("database", "").trim()
                + "?useSSL=false&connectTimeout=8000&socketTimeout=30000"
                + "&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true"
                + "&rewriteBatchedStatements=true&serverTimezone=America/Sao_Paulo";
        return DriverManager.getConnection(url, p.getString("user", ""), p.getString("password", ""));
    }

    private static String quote(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static String friendly(Exception e) {
        String msg = e.getMessage();
        return msg == null || msg.trim().isEmpty() ? e.getClass().getSimpleName() : msg;
    }

    private static final class SyncResult {
        final String message;
        SyncResult(String message) { this.message = message; }
    }
}
