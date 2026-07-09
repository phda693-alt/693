package com.pdv.app.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime MariaDB embutido no PDV Pro.
 *
 * <p>Usa o MariaDB oficial para Android empacotado em jniLibs e inicia o
 * banco local sem instalar outro APK.</p>
 */
public final class EmbeddedMariaDbManager {

    public interface StatusCallback {
        void onStatus(String message);
    }

    private static final String TAG = "EmbeddedMariaDb";
    private static final String RUNTIME_VERSION = "mariadb-12.3.2-termux-pdvpro-2";
    private static final String PREFS = "embedded_mariadb";
    private static final String KEY_STATUS = "last_status";
    private static final String KEY_LOCAL_CONFIG_APPLIED = "local_config_applied";
    private static final int MYSQL_PORT = 3306;
    private static final Object LOCK = new Object();

    private static Process mariaDbProcess;

    private EmbeddedMariaDbManager() { /* utility */ }

    public static boolean isEmbeddedAvailable(Context context) {
        File exe = getMysqldExecutable(context);
        return exe.isFile();
    }

    public static String getLastStatus(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATUS, "Servidor MariaDB interno ainda nao inicializado.");
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, EmbeddedMariaDbService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static boolean ensureStarted(Context context, StatusCallback callback) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            try {
                ensureLocalDatabaseConfig(app, false);

                if (isPortOpen("127.0.0.1", MYSQL_PORT, 500)) {
                    status(app, callback, "MariaDB interno ja esta ativo na porta 3306.");
                    return true;
                }

                File mysqld = getMysqldExecutable(app);
                if (!mysqld.isFile()) {
                    status(app, callback, "Binario mysqld interno nao encontrado.");
                    return false;
                }
                mysqld.setExecutable(true, false);

                File root = getRootDir(app);
                File runtime = new File(root, "runtime");
                File data = new File(root, "data");
                File run = new File(root, "run");
                File tmp = new File(root, "tmp");
                File logs = new File(root, "logs");
                ensureDir(data);
                ensureDir(run);
                ensureDir(tmp);
                ensureDir(logs);

                prepareRuntimeAssets(app, runtime, callback);
                bootstrapIfNeeded(app, mysqld, runtime, data, tmp, logs, callback);

                status(app, callback, "Iniciando MariaDB interno...");
                List<String> cmd = buildServerCommand(mysqld, runtime, data, run, tmp, logs);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(root);
                pb.redirectErrorStream(true);
                applyEnvironment(pb, tmp, mysqld.getParentFile());

                mariaDbProcess = pb.start();
                consumeToLog(mariaDbProcess.getInputStream(), new File(logs, "mysqld.out.log"));

                boolean ready = waitForPort("127.0.0.1", MYSQL_PORT, 45_000L);
                if (ready) {
                    status(app, callback, "MariaDB interno iniciado com sucesso.");
                } else {
                    status(app, callback, "MariaDB interno nao respondeu na porta 3306.");
                }
                return ready;
            } catch (Exception e) {
                Log.e(TAG, "Falha ao iniciar MariaDB interno", e);
                status(app, callback, "Falha ao iniciar MariaDB interno: " + e.getMessage());
                return false;
            }
        }
    }

    public static void provisionPdvDatabase(Context context, StatusCallback callback) {
        Context app = context.getApplicationContext();
        try {
            ensureLocalDatabaseConfig(app, false);
            status(app, callback, "Criando banco e usuario padrao do PDV...");
            DatabaseHelper db = DatabaseHelper.getInstance(app);
            String report = db.executarMigracaoInicialObrigatoria(message ->
                    status(app, callback, message));
            Log.d(TAG, "Provisionamento PDV:\n" + report);
            status(app, callback, "Banco do PDV pronto para uso.");
        } catch (Exception e) {
            Log.e(TAG, "Falha no provisionamento do banco PDV", e);
            status(app, callback, "Falha ao preparar banco do PDV: " + e.getMessage());
        }
    }

    public static void ensureLocalDatabaseConfig(Context context, boolean force) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences("db_config", Context.MODE_PRIVATE);
        SharedPreferences own = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        boolean alreadyApplied = own.getBoolean(KEY_LOCAL_CONFIG_APPLIED, false);
        String host = prefs.getString("host", "");
        boolean looksDefault = host.length() == 0
                || "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host);

        if (force || !alreadyApplied || looksDefault) {
            DatabaseHelper db = DatabaseHelper.getInstance(app);
            db.saveConfig("127.0.0.1", "banco", "usuario", "senha", MYSQL_PORT);
            own.edit().putBoolean(KEY_LOCAL_CONFIG_APPLIED, true).apply();
        }
    }

    private static void prepareRuntimeAssets(Context context, File runtime, StatusCallback callback)
            throws IOException {
        File marker = new File(runtime, ".runtime-version");
        String current = marker.isFile() ? readSmallFile(marker) : "";
        if (RUNTIME_VERSION.equals(current)) {
            return;
        }

        status(context, callback, "Preparando arquivos do MariaDB interno...");
        deleteChildren(runtime);
        ensureDir(runtime);
        copyAssetTree(context.getAssets(), "mariadb", runtime);
        writeFile(marker, RUNTIME_VERSION);
    }

    private static void bootstrapIfNeeded(Context context, File mysqld, File runtime, File data,
                                          File tmp, File logs, StatusCallback callback)
            throws IOException, InterruptedException {
        File marker = new File(data, ".bootstrapped");
        File privilegeTable = new File(new File(data, "mysql"), "global_priv.MAI");
        String installedVersion = marker.isFile() ? readSmallFile(marker).trim() : "";
        if (RUNTIME_VERSION.equals(installedVersion) && privilegeTable.isFile()) {
            return;
        }

        status(context, callback, "Inicializando tabelas internas do MariaDB...");
        preservePreviousData(data, context, callback);
        ensureDir(data);

        String sql = buildBootstrapSql(context);
        List<String> cmd = buildBootstrapCommand(mysqld, runtime, data, tmp);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(getRootDir(context));
        pb.redirectErrorStream(true);
        applyEnvironment(pb, tmp, mysqld.getParentFile());

        Process process = pb.start();
        OutputStream stdin = process.getOutputStream();
        stdin.write(sql.getBytes(StandardCharsets.UTF_8));
        stdin.flush();
        stdin.close();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(process.getInputStream(), output);
        int code = process.waitFor();
        writeFile(new File(logs, "bootstrap.log"), output.toString("UTF-8"));
        if (code != 0 || !privilegeTable.isFile()) {
            throw new IOException("bootstrap MariaDB falhou. codigo=" + code);
        }
        writeFile(marker, RUNTIME_VERSION);
    }

    private static String buildBootstrapSql(Context context) throws IOException {
        StringBuilder sql = new StringBuilder(1024 * 1024);
        sql.append("CREATE DATABASE IF NOT EXISTS mysql;\n");
        sql.append("USE mysql;\n");
        sql.append("SET @auth_root_socket = NULL;\n");
        sql.append("SET @skip_auth_root_nopasswd = NULL;\n");
        appendAsset(context, sql, "sql/mariadb_system_tables.sql");
        appendAsset(context, sql, "sql/mariadb_performance_tables.sql");
        appendAsset(context, sql, "sql/mariadb_system_tables_data.sql");
        appendAsset(context, sql, "sql/fill_help_tables.sql");
        appendAsset(context, sql, "sql/maria_add_gis_sp_bootstrap.sql");
        appendAsset(context, sql, "sql/mariadb_sys_schema.sql");
        sql.append("\nFLUSH PRIVILEGES;\n");
        return sql.toString();
    }

    private static List<String> buildBootstrapCommand(File mysqld, File runtime, File data, File tmp) {
        List<String> cmd = new ArrayList<>();
        cmd.add(mysqld.getAbsolutePath());
        cmd.add("--no-defaults");
        cmd.add("--bootstrap");
        cmd.add("--basedir=" + runtime.getAbsolutePath());
        cmd.add("--datadir=" + data.getAbsolutePath());
        cmd.add("--tmpdir=" + tmp.getAbsolutePath());
        cmd.add("--lc-messages-dir=" + new File(runtime, "share").getAbsolutePath());
        cmd.add("--character-sets-dir=" + new File(runtime, "share/charsets").getAbsolutePath());
        cmd.add("--plugin-dir=" + new File(runtime, "lib/plugin").getAbsolutePath());
        cmd.add("--enforce-storage-engine=");
        cmd.add("--max-allowed-packet=8M");
        cmd.add("--net-buffer-length=16K");
        cmd.add("--character-set-server=utf8mb4");
        cmd.add("--collation-server=utf8mb4_unicode_ci");
        return cmd;
    }

    private static List<String> buildServerCommand(File mysqld, File runtime, File data,
                                                   File run, File tmp, File logs) {
        List<String> cmd = new ArrayList<>();
        cmd.add(mysqld.getAbsolutePath());
        cmd.add("--no-defaults");
        cmd.add("--basedir=" + runtime.getAbsolutePath());
        cmd.add("--datadir=" + data.getAbsolutePath());
        cmd.add("--port=" + MYSQL_PORT);
        cmd.add("--bind-address=0.0.0.0");
        cmd.add("--socket=" + new File(run, "mysql.sock").getAbsolutePath());
        cmd.add("--pid-file=" + new File(run, "mysqld.pid").getAbsolutePath());
        cmd.add("--tmpdir=" + tmp.getAbsolutePath());
        cmd.add("--lc-messages-dir=" + new File(runtime, "share").getAbsolutePath());
        cmd.add("--character-sets-dir=" + new File(runtime, "share/charsets").getAbsolutePath());
        cmd.add("--plugin-dir=" + new File(runtime, "lib/plugin").getAbsolutePath());
        cmd.add("--log-error=" + new File(logs, "mysqld.err.log").getAbsolutePath());
        cmd.add("--skip-name-resolve");
        cmd.add("--character-set-server=utf8mb4");
        cmd.add("--collation-server=utf8mb4_unicode_ci");
        // v8.0.23.0 - Parametros de performance para MariaDB embutido
        cmd.add("--innodb-buffer-pool-size=32M");   // Pool de buffer InnoDB
        cmd.add("--innodb-log-buffer-size=4M");      // Buffer de log InnoDB
        cmd.add("--innodb-flush-log-at-trx-commit=2"); // Flush menos frequente (ok para PDV)
        cmd.add("--query-cache-size=0");             // Desabilita query cache (deprecated)
        cmd.add("--query-cache-type=0");             // Desabilita query cache type
        cmd.add("--table-open-cache=256");           // Cache de tabelas abertas
        cmd.add("--thread-cache-size=8");            // Cache de threads
        cmd.add("--max-connections=50");             // Maximo de conexoes (PDV local)
        cmd.add("--wait-timeout=600");               // Timeout de conexao ociosa (10min)
        cmd.add("--interactive-timeout=600");        // Timeout interativo
        cmd.add("--net-read-timeout=30");            // Timeout de leitura de rede
        cmd.add("--net-write-timeout=30");           // Timeout de escrita de rede
        cmd.add("--skip-external-locking");          // Desabilita lock externo
        cmd.add("--performance-schema=OFF");         // Desabilita performance schema (economiza RAM)
        return cmd;
    }

    private static void applyEnvironment(ProcessBuilder pb, File tmp, File nativeLibraryDir) {
        pb.environment().put("TMPDIR", tmp.getAbsolutePath());
        pb.environment().put("HOME", tmp.getAbsolutePath());
        pb.environment().put("LD_LIBRARY_PATH", nativeLibraryDir.getAbsolutePath());
    }

    private static void preservePreviousData(File data, Context context,
                                             StatusCallback callback) throws IOException {
        File[] children = data.listFiles();
        if (children == null || children.length == 0) {
            return;
        }

        File backup = new File(data.getParentFile(),
                "data_backup_before_12_3_2_" + System.currentTimeMillis());
        if (!data.renameTo(backup)) {
            throw new IOException("Nao foi possivel preservar o banco anterior: " + data);
        }
        status(context, callback, "Banco anterior preservado para migracao: " + backup.getName());
    }

    private static boolean waitForPort(String host, int port, long timeoutMs) {
        // v8.0.23.0 - Intervalo reduzido de 700ms para 400ms para detectar
        // subida do servidor mais rapidamente, sem sobrecarregar a CPU
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(host, port, 400)) {
                return true;
            }
            try { Thread.sleep(400); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isPortOpen(String host, int port, int timeoutMs) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static File getRootDir(Context context) {
        return new File(context.getFilesDir(), "embedded_mariadb");
    }

    private static File getMysqldExecutable(Context context) {
        return new File(context.getApplicationInfo().nativeLibraryDir, "libpdv_mysqld.so");
    }

    private static void appendAsset(Context context, StringBuilder out, String path) throws IOException {
        out.append("\n-- ").append(path).append("\n");
        InputStream in = context.getAssets().open(path);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            copy(in, bytes);
        } finally {
            in.close();
        }
        out.append(bytes.toString("UTF-8")).append('\n');
    }

    private static void copyAssetTree(AssetManager assets, String assetPath, File target)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            ensureDir(target.getParentFile());
            InputStream in = assets.open(assetPath);
            FileOutputStream out = new FileOutputStream(target);
            try {
                copy(in, out);
            } finally {
                try { in.close(); } catch (IOException ignored) {}
                try { out.close(); } catch (IOException ignored) {}
            }
            return;
        }

        ensureDir(target);
        for (String child : children) {
            copyAssetTree(assets, assetPath + "/" + child, new File(target, child));
        }
    }

    private static void consumeToLog(final InputStream in, final File logFile) {
        Thread t = new Thread(() -> {
            FileOutputStream out = null;
            try {
                ensureDir(logFile.getParentFile());
                out = new FileOutputStream(logFile, true);
                copy(in, out);
            } catch (Exception e) {
                Log.w(TAG, "Falha ao gravar log do MariaDB: " + e.getMessage());
            } finally {
                try { if (out != null) out.close(); } catch (IOException ignored) {}
                try { in.close(); } catch (IOException ignored) {}
            }
        }, "EmbeddedMariaDb-log");
        t.setDaemon(true);
        t.start();
    }

    private static void status(Context context, StatusCallback callback, String message) {
        Log.d(TAG, message);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATUS, message)
                .apply();
        if (callback != null) {
            callback.onStatus(message);
        }
    }

    private static void ensureDir(File dir) throws IOException {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Nao foi possivel criar diretorio: " + dir);
        }
    }

    private static void deleteChildren(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            deleteChildren(file);
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Nao foi possivel remover: " + file);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private static String readSmallFile(File file) throws IOException {
        InputStream in = new java.io.FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            copy(in, out);
        } finally {
            in.close();
        }
        return out.toString("UTF-8");
    }

    private static void writeFile(File file, String content) throws IOException {
        ensureDir(file.getParentFile());
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }
}
