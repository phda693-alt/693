package com.pdv.app;

import android.app.Application;
import android.util.Log;
import androidx.multidex.MultiDex;
import android.content.Context;

import com.pdv.app.utils.CrashReportManager;

/**
 * Application class do PDV Pro.
 * 
 * v6.9.2 - Otimizacoes de inicializacao:
 * - Pre-carregamento do driver MySQL JDBC em background thread
 *   (evita delay de ~500ms na primeira conexao)
 * - Handler global de excecoes mantido para diagnostico
 */
public class PDVApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    private static final String TAG = "PDVApplication";
    private static PDVApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Auto-start do MariaDB interno do PDV Pro
        autoStartMariaDbServer();

        // v8.0.0 - Driver pre-load removido para evitar problemas de race condition no Android 14+
        Log.d(TAG, "PDVApplication iniciado");

        // Handler global para exceções não capturadas
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Erro nao capturado na thread " + thread.getName(), throwable);
            try {
                CrashReportManager.record(getApplicationContext(), thread, throwable);

                // Registrar o erro para diagnostico
                String errorMsg = throwable.getMessage();
                if (errorMsg == null) errorMsg = throwable.getClass().getSimpleName();
                Log.e(TAG, "Detalhes do erro fatal: " + errorMsg);

                // Tentar fechar conexoes abertas para evitar vazamento
                try {
                    com.pdv.app.database.DatabaseHelper dbHelper = com.pdv.app.database.DatabaseHelper.getInstance(getApplicationContext());
                    if (dbHelper != null) {
                        dbHelper.closeConnection();
                    }
                } catch (Exception dbEx) {
                    Log.w(TAG, "Erro ao fechar conexao durante crash: " + dbEx.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro no handler de excecoes", e);
            }

            // Delegar ao handler padrão do sistema
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }


    public static PDVApplication getInstance() {
        return instance;
    }

    /**
     * Tenta iniciar o MariaDB interno em background.
     * Silencioso: se falhar, nao impacta o PDV Pro de forma alguma.
     */
    private void autoStartMariaDbServer() {
        new Thread(() -> {
            try {
                com.pdv.app.server.MariaDbServerManager.sendStartBroadcast(this);
                Log.d(TAG, "MariaDB interno acionado");
            } catch (Exception e) {
                Log.w(TAG, "Falha no auto-start do MariaDB interno (nao-critico)", e);
            }
        }, "MariaDB-AutoStart").start();
    }
}
