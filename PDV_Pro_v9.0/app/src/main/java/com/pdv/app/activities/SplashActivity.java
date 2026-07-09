package com.pdv.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionDatabaseSetup;
import com.pdv.app.server.MariaDbServerManager;
import com.pdv.app.utils.AnimUtils;

/**
 * Tela de splash com inicializacao inteligente e otimizada.
 * 
 * v6.9.2 - Otimizacoes de inicializacao:
 * 
 * 1. Tempo minimo de splash REDUZIDO de 2000ms para 800ms
 * 2. Sistema de cache de inicializacao:
 *    - Na PRIMEIRA execucao: faz inicializacao completa (banco, tabelas, colunas, dados)
 *    - Nas execucoes SEGUINTES: pula verificacao se o banco ja foi inicializado com sucesso
 *    - O cache e invalidado quando as configuracoes do banco mudam
 * 3. Animacoes mais rapidas (delays reduzidos)
 * 4. Conexao MySQL com timeouts otimizados
 * 5. Verificacao de permissoes em paralelo (nao-bloqueante)
 * 
 * v7.0.0 - Versao exibida dinamicamente a partir do build.gradle (PackageInfo)
 * 
 * Resultado: inicializacao 3-5x mais rapida em execucoes subsequentes.
 */
public class SplashActivity extends BaseActivity {
    private static final String TAG = "SplashActivity";
    private static final long MIN_SPLASH_TIME = 800; // Reduzido de 2000ms para 800ms
    private static final String PREFS_INIT_CACHE = "db_init_cache";
    private static final String KEY_INIT_DONE = "init_done";
    private static final String KEY_INIT_VERSION = "init_version";
    private static final String KEY_INIT_HOST = "init_host";
    private static final String KEY_INIT_DB = "init_db";
    // v8.0.23.0 - Alinhado com SCHEMA_VERSION do DatabaseHelper
    // Incrementar sempre que a estrutura do banco mudar para forcar re-migracao
    private static final int CURRENT_INIT_VERSION = 26;

    private TextView tvStatus;
    private boolean dbInitDone = false;
    private boolean minTimeDone = false;
    private boolean dbInitSuccess = false;
    private boolean failureDialogShown = false;
    private boolean aguardandoConfiguracao = false;
    private long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        startTime = System.currentTimeMillis();

        View logo = findViewById(R.id.ivLogo);
        TextView tvName = findViewById(R.id.tvAppName);
        TextView tvVersion = findViewById(R.id.tvVersion);
        ProgressBar progress = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);

        // v7.0.0 - Exibir versao real do app dinamicamente a partir do build.gradle
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("v" + pInfo.versionName);
        } catch (Exception e) {
            // Fallback para texto do layout (v7.0.0)
        }

        // v8.0.0 - Animacoes desativadas para garantir inicializacao estavel
        if (logo != null) logo.setAlpha(1f);
        if (tvName != null) tvName.setAlpha(1f);
        if (tvVersion != null) tvVersion.setAlpha(1f);
        if (progress != null) progress.setAlpha(1f);
        if (tvStatus != null) tvStatus.setAlpha(1f);
        Log.d(TAG, "Layout da Splash configurado");

        // Timer minimo de exibicao da splash (reduzido)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            minTimeDone = true;
            tryNavigateToLogin();
        }, MIN_SPLASH_TIME);

        // Inicializacao do banco em background com cache inteligente
        iniciarInicializacao();
    }

    private void iniciarInicializacao() {
        dbInitDone = false;
        failureDialogShown = false;
        new Thread(() -> {
            dbInitSuccess = initializeDatabaseSmart();
            dbInitDone = true;
            runOnUiThread(this::tryNavigateToLogin);
        }, "PDV-Database-Preflight").start();
    }

    /**
     * Inicializacao inteligente com cache.
     * Se o banco ja foi inicializado com sucesso anteriormente (mesma config),
     * pula a verificacao completa e apenas testa a conexao.
     */
    private boolean initializeDatabaseSmart() {
        try {
            updateStatus("Iniciando banco local...");
            MariaDbServerManager.ensureReadyBlocking(this, this::updateStatus);

            DatabaseHelper db = DatabaseHelper.getInstance(this);

            // v8.0.12.1 - MIGRACAO OBRIGATORIA SEM PULAR POR CACHE
            // O sistema agora sempre verifica tabelas e colunas antes de qualquer tela.
            // Isso permite abrir bancos antigos e atualizar a estrutura automaticamente
            // antes de login, vendas, estacionamento, web, OS, impressao e permissoes.
            doFullInitialization(db);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Inicializacao obrigatoria do banco falhou: " + e.getMessage(), e);
            updateStatus("Banco precisa ser atualizado antes de continuar");

            // Aguarda um pouco para o usuario ver a mensagem (reduzido de 800 para 400ms)
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            return false;
        }
    }

    /**
     * Executa a inicializacao completa do banco de dados.
     * Chamada na primeira execucao ou quando o cache e invalido.
     */
    private void doFullInitialization(DatabaseHelper db) {
        updateStatus("Verificando banco de dados...");

        // Inicializacao completa: banco + tabelas + colunas + dados padrao
        String report = db.executarMigracaoInicialObrigatoria(message -> {
            updateStatus(message);
        });

        Log.d(TAG, "Resultado da inicializacao:\n" + report);
        if (report == null || report.contains("[ERRO]")) {
            throw new IllegalStateException("Nao foi possivel validar todas as tabelas e colunas obrigatorias.");
        }

        // Setup do sistema de permissoes (perfis, permissoes, vinculacoes)
        updateStatus("Configurando permissoes...");
        try {
            PermissionDatabaseSetup.setup(this);
            Log.d(TAG, "Sistema de permissoes configurado");
        } catch (Exception permEx) {
            Log.e(TAG, "Erro ao configurar permissoes (nao-critico): " + permEx.getMessage());
        }

        // Salvar cache de inicializacao
        saveInitCache(db);

        updateStatus("Pronto!");
        Log.d(TAG, "Inicializacao completa em " + (System.currentTimeMillis() - startTime) + "ms");
    }

    /**
     * Verifica se o cache de inicializacao e valido.
     * O cache e invalidado quando:
     * - As configuracoes do banco mudam (host, database)
     * - A versao de inicializacao muda (nova estrutura de tabelas)
     * - O cache nunca foi salvo
     */
    private boolean isInitCacheValid(DatabaseHelper db) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_INIT_CACHE, MODE_PRIVATE);
            boolean done = prefs.getBoolean(KEY_INIT_DONE, false);
            int version = prefs.getInt(KEY_INIT_VERSION, 0);
            String cachedHost = prefs.getString(KEY_INIT_HOST, "");
            String cachedDb = prefs.getString(KEY_INIT_DB, "");

            if (!done || version != CURRENT_INIT_VERSION) {
                return false;
            }

            // Verificar se as configuracoes do banco mudaram
            return cachedHost.equals(db.getHost()) && cachedDb.equals(db.getDatabase());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Salva o cache de inicializacao apos sucesso.
     */
    private void saveInitCache(DatabaseHelper db) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_INIT_CACHE, MODE_PRIVATE);
            prefs.edit()
                    .putBoolean(KEY_INIT_DONE, true)
                    .putInt(KEY_INIT_VERSION, CURRENT_INIT_VERSION)
                    .putString(KEY_INIT_HOST, db.getHost())
                    .putString(KEY_INIT_DB, db.getDatabase())
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao salvar cache de inicializacao: " + e.getMessage());
        }
    }

    /**
     * Invalida o cache de inicializacao.
     * Deve ser chamado quando as configuracoes do banco mudam.
     */
    public static void invalidateInitCache(android.content.Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_INIT_CACHE, MODE_PRIVATE);
            prefs.edit().clear().apply();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao invalidar cache: " + e.getMessage());
        }
    }

    /**
     * Atualiza o texto de status na UI thread.
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (tvStatus != null && !isFinishing() && !isDestroyed()) {
                tvStatus.setText(message);
            }
        });
    }

    /**
     * Navega para a tela de login apenas quando AMBAS as condicoes forem atendidas:
     * 1. O tempo minimo de splash passou
     * 2. A inicializacao do banco terminou (sucesso ou erro)
     */
    private void tryNavigateToLogin() {
        if (minTimeDone && dbInitDone) {
            if (!isFinishing() && !isDestroyed()) {
                if (!dbInitSuccess) {
                    mostrarFalhaInicializacao();
                    return;
                }
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left);
                finish();
            }
        }
    }

    private void mostrarFalhaInicializacao() {
        if (failureDialogShown || isFinishing()) return;
        failureDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Banco de dados indisponivel")
                .setMessage("O PDV verificou a estrutura antes de iniciar, mas nao conseguiu concluir a atualizacao. " +
                        "Nenhuma operacao foi liberada para evitar inconsistencias.\n\n" +
                        "Confira o servidor e tente novamente ou ajuste a conexao.")
                .setCancelable(false)
                .setPositiveButton("Tentar novamente", (d, w) -> iniciarInicializacao())
                .setNegativeButton("Configurar conexao", (d, w) -> {
                    aguardandoConfiguracao = true;
                    startActivity(new Intent(this, ConfigBancoActivity.class));
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aguardandoConfiguracao) {
            aguardandoConfiguracao = false;
            iniciarInicializacao();
        }
    }
}
