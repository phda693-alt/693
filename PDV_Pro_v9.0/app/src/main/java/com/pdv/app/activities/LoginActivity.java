package com.pdv.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionDatabaseSetup;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.security.AdminAuthGuard;
import com.pdv.app.security.BiometricAuthHelper;
import com.pdv.app.security.BiometricCredentialStore;
import com.pdv.app.server.MariaDbServerManager;
import com.pdv.app.ui.AdminPasswordDialog;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.LicencaManager;
import com.pdv.app.utils.UserActionLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.utils.ErrorHandler;

/**
 * Tela de login do PDV Pro.
 * 
 * v6.9.2 - Otimizacoes de inicializacao:
 * - Animacoes com delays reduzidos (100-500ms em vez de 100-900ms)
 * - Removida inicializacao redundante de createTables() no doLogin()
 *   (ja feita pela SplashActivity com cache inteligente)
 * - PermissionDatabaseSetup.setup() removido do doLogin()
 *   (ja feita pela SplashActivity)
 * - Verificacao isDatabaseInitialized() antes de chamar createTables()
 * - Login mais rapido: menos overhead de inicializacao
 */
public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";
    private Spinner spinnerUsuario;
    private EditText etSenha;
    private Button btnEntrar, btnEntrarBiometria, btnCadastrarBiometria;
    private ImageButton btnToggleSenha;
    private TextView tvConfigBanco, tvTrocarSenha, tvEsqueciSenha, tvNovaLicenca, tvAtualizarBanco;
    private boolean senhaVisivel = false;

    // Listas paralelas: nomes exibidos no spinner e logins correspondentes
    private List<String> nomesUsuarios = new ArrayList<>();
    private List<String> loginsUsuarios = new ArrayList<>();

    // v8.0.23.0 - Controle de carregamento para evitar reload desnecessario no onResume
    private boolean usuariosCarregados = false;
    private long ultimoCarregamentoMs = 0L;
    private static final long DEBOUNCE_RELOAD_MS = 3000L; // Evita reload em menos de 3s

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        spinnerUsuario = findViewById(R.id.spinnerUsuario);
        etSenha = findViewById(R.id.etSenha);
        btnEntrar = findViewById(R.id.btnEntrar);
        btnEntrarBiometria = findViewById(R.id.btnEntrarBiometria);
        btnCadastrarBiometria = findViewById(R.id.btnCadastrarBiometria);
        btnToggleSenha = findViewById(R.id.btnToggleSenha);
        tvConfigBanco = findViewById(R.id.tvConfigBanco);
        tvTrocarSenha = findViewById(R.id.tvTrocarSenha);
        tvEsqueciSenha = findViewById(R.id.tvEsqueciSenha);
        tvNovaLicenca = findViewById(R.id.tvNovaLicenca);
        tvAtualizarBanco = findViewById(R.id.tvAtualizarBanco);

        // Configurar botao olho para mostrar/ocultar senha
        btnToggleSenha.setOnClickListener(v -> toggleSenhaVisibilidade());

        View logo = findViewById(R.id.ivLogo);
        View tvTitle = findViewById(R.id.tvTitle);
        View tvLabelUsuario = findViewById(R.id.tvLabelUsuario);
        View spinnerContainer = findViewById(R.id.spinnerContainer);

        // v6.9.2 - Animacoes com delays reduzidos para abertura mais rapida
        AnimUtils.scaleIn(logo, 50);
        AnimUtils.fadeIn(tvTitle, 150);
        AnimUtils.slideUp(tvLabelUsuario, 200);
        AnimUtils.slideUp(spinnerContainer, 200);
        AnimUtils.slideUp(etSenha, 250);
        AnimUtils.slideUp(btnEntrar, 300);
        AnimUtils.slideUp(btnEntrarBiometria, 330);
        AnimUtils.slideUp(btnCadastrarBiometria, 350);
        AnimUtils.fadeIn(tvConfigBanco, 380);
        AnimUtils.fadeIn(tvTrocarSenha, 430);
        AnimUtils.fadeIn(tvEsqueciSenha, 480);
        AnimUtils.fadeIn(tvNovaLicenca, 530);
        AnimUtils.fadeIn(tvAtualizarBanco, 580);

        btnEntrar.setOnClickListener(v -> doLogin());
        btnEntrarBiometria.setOnClickListener(v -> iniciarLoginBiometrico());
        btnCadastrarBiometria.setOnClickListener(v -> cadastrarBiometriaUsuarioSelecionado());

        tvConfigBanco.setOnClickListener(v -> {
            startActivity(new Intent(this, ConfigBancoActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        tvTrocarSenha.setOnClickListener(v -> {
            startActivity(new Intent(this, TrocaSenhaActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        tvEsqueciSenha.setOnClickListener(v -> {
            startActivity(new Intent(this, EsqueciSenhaActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        tvNovaLicenca.setOnClickListener(v -> solicitarSenhaAdmin(
                "Inserir Nova Licença",
                "Esta operação requer senha de administrador.\nDigite a senha para continuar:",
                () -> {
                    startActivity(new Intent(this, LicencaActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }));
        tvAtualizarBanco.setOnClickListener(v -> solicitarSenhaAdmin(
                "Atualizar Banco de Dados",
                "Esta operação requer senha de administrador.\nDigite a senha para continuar:",
                this::showConfirmAtualizarBanco));

        // Botao Servidor MySQL (8.0.15) - protegido por senha admin 4872
        Button btnServidorMySQL = findViewById(R.id.btnServidorMySQL);
        if (btnServidorMySQL != null) {
            AnimUtils.slideUp(btnServidorMySQL, 600);
            btnServidorMySQL.setOnClickListener(v -> solicitarSenhaAdmin(
                    "Servidor MySQL Integrado",
                    "Esta operação requer senha de administrador.\nDigite a senha para gerenciar o servidor MySQL:",
                    this::abrirServidorMySQLIntegrado));
        }

        // Exibir versao real do app na tela de login
        TextView tvVersao = findViewById(R.id.tvVersao);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersao.setText("v" + pInfo.versionName);
        } catch (Exception e) {
            tvVersao.setText("v8.0.0");
        }

        // Carregar usuarios do banco ao abrir a tela
        carregarUsuarios();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // v8.0.23.0 - Recarregar apenas se nao foi carregado recentemente
        // Evita double-load no onCreate->onResume e em retornos rapidos
        long agora = System.currentTimeMillis();
        if (!usuariosCarregados || (agora - ultimoCarregamentoMs) > DEBOUNCE_RELOAD_MS) {
            carregarUsuarios();
        }
    }

    /**
     * Senha administrativa requerida para acoes sensiveis na tela de login,
     * como inserir nova licenca e atualizar tabelas/colunas do banco de dados.
     *
     * <p>Mantida por compatibilidade com qualquer codigo legado que possa
     * referencia-la. O valor real e centralizado em
     * {@link AdminAuthGuard#ADMIN_PASSWORD} para auditoria,
     * lockout progressivo e validacao em tempo constante.</p>
     */
    @SuppressWarnings("unused")
    private static final String SENHA_ADMIN_LOGIN = AdminAuthGuard.ADMIN_PASSWORD;

    /**
     * Exibe um dialogo profissional solicitando a senha administrativa.
     *
     * <p>Esta versao delega a {@link AdminPasswordDialog}, que oferece:
     * shake animation em caso de erro, feedback haptico, lockout
     * progressivo apos tentativas erradas, contador regressivo
     * visual e trilha de auditoria persistente.</p>
     *
     * <p>O contrato externo e identico ao da versao anterior:
     * apenas executa a {@code acao} se a senha for {@code 4872}.</p>
     *
     * @param titulo   titulo do dialogo
     * @param mensagem mensagem explicativa exibida acima do campo de senha
     * @param acao     acao a ser executada apos validacao bem-sucedida
     */
    private void solicitarSenhaAdmin(String titulo, String mensagem, Runnable acao) {
        AdminPasswordDialog.show(this, titulo, mensagem, acao);
    }

    private void showConfirmAtualizarBanco() {
        new AlertDialog.Builder(this)
                .setTitle("Atualizar Banco de Dados")
                .setMessage("Deseja verificar e atualizar as tabelas e colunas do banco de dados agora?\n\nIsso garantirá que o sistema tenha todos os campos necessários para funcionar corretamente.")
                .setPositiveButton("Sim, Atualizar", (d, w) -> realizarAtualizacaoBanco())
                .setNegativeButton("Não", null)
                .show();
    }

    /**
     * Abre o servidor MySQL (PHSERVER) ou oferece instalar a partir do APK
     * embutido em assets/phserver/PHSERVER.apk se ainda não estiver instalado.
     *
     * <p>Se o PHSERVER já estiver instalado, abre direto a tela principal dele.
     * Caso contrário, mostra um diálogo profissional perguntando se o usuário
     * deseja instalar agora — um clique no "Instalar" extrai e dispara o
     * {@link android.content.Intent#ACTION_VIEW} do APK incluído.</p>
     */
    private void abrirServidorMySQLIntegrado() {
        showLoading("Iniciando servidor MySQL integrado...");
        new Thread(() -> {
            boolean ok = MariaDbServerManager.ensureReadyBlocking(this,
                    message -> runOnUiThread(() -> showLoading(message)));
            hideLoading();
            runOnUiThread(() -> {
                if (ok) {
                    showInfo("Servidor MySQL Integrado",
                            "MariaDB interno ativo em 127.0.0.1:3306.\n\n"
                                    + "Banco: banco\nUsuario: usuario\nSenha: senha\n\n"
                                    + "Nao e necessario instalar o PHSERVER separado.");
                } else {
                    showError("Nao foi possivel iniciar o servidor MySQL integrado.\n\n"
                            + MariaDbServerManager.getLastStatus(this));
                }
            });
        }, "PDV-Start-Embedded-MySQL").start();
    }

    private void abrirServidorMySQL() {
        if (com.pdv.app.server.MariaDbServerManager.isInstalled(this)) {
            com.pdv.app.server.MariaDbServerManager.scheduleBootAutoStart(this);
            boolean ok = com.pdv.app.server.MariaDbServerManager.tryAutoStart(this, true);
            if (!ok) {
                showError("Não foi possível abrir o Servidor MySQL.\n\n" +
                        "Tente abrir manualmente o app PHSERVER pelo lançador.");
            }
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Servidor MySQL (PHSERVER)")
                    .setMessage("O servidor MariaDB (PHSERVER) ainda não está instalado neste dispositivo.\n\n" +
                            "Deseja instalar agora? O APK do servidor está incluído no PDV Pro " +
                            "e a instalação será iniciada imediatamente.\n\n" +
                            "Depois da instalacao, abra o PHSERVER uma vez e permita execucao em segundo plano/autostart.")
                    .setPositiveButton("Instalar agora", (d, w) -> {
                        boolean started = com.pdv.app.server.MariaDbServerManager
                                .installFromAssets(this);
                        if (!started) {
                            showError("Não foi possível iniciar a instalação.\n\n" +
                                    "Verifique se o PDV Pro tem permissão para instalar " +
                                    "aplicativos de fontes desconhecidas.");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    private void realizarAtualizacaoBanco() {
        showLoading("Atualizando banco de dados...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                // Forçar inicialização completa ignorando cache
                String relatorio = db.initializeDatabase(message -> runOnUiThread(() -> showLoading(message)));
                
                // Garantir que as permissões também estejam atualizadas
                PermissionDatabaseSetup.setup(this);
                
                // Invalidar cache da splash para que ela saiba que o banco está OK
                SplashActivity.invalidateInitCache(this);
                
                hideLoading();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Atualização Concluída")
                            .setMessage("O banco de dados foi verificado e atualizado com sucesso!\n\n" + relatorio)
                            .setPositiveButton("OK", (d, w) -> carregarUsuarios())
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_GENERICO);
            }
        }).start();
    }

    /**
     * Alterna a visibilidade da senha entre texto visivel e oculto.
     * Atualiza o icone do botao olho de acordo.
     */
    private void toggleSenhaVisibilidade() {
        senhaVisivel = !senhaVisivel;
        if (senhaVisivel) {
            // Mostrar senha
            etSenha.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            btnToggleSenha.setImageResource(R.drawable.ic_eye_on);
        } else {
            // Ocultar senha
            etSenha.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            btnToggleSenha.setImageResource(R.drawable.ic_eye_off);
        }
        // Manter o cursor no final do texto
        etSenha.setSelection(etSenha.getText().length());
    }

    /**
     * Carrega a lista de usuarios ativos do banco de dados e popula o Spinner.
     * Exibe nome (login) no dropdown para facil identificacao.
     *
     * v8.0.23.0 - Otimizado:
     * - Debounce de 3s para evitar reload desnecessario em onCreate->onResume
     * - Verifica isDatabaseInitialized() antes de chamar createTables()
     * - Usa flag usuariosCarregados para controle de estado
     */
    private void carregarUsuarios() {
        ultimoCarregamentoMs = System.currentTimeMillis();
        new Thread(() -> {
            List<String> nomes = new ArrayList<>();
            List<String> logins = new ArrayList<>();

            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);

                // v8.0.23.0 - Apenas inicializa se a SplashActivity nao fez
                // (evita overhead de verificacao de tabelas em cada reload)
                if (!db.isDatabaseInitialized()) {
                    db.createTables();
                }

                Connection conn = db.getConnection();

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT login, nome FROM usuarios WHERE ativo = 1 ORDER BY nome ASC");

                while (rs.next()) {
                    String login = rs.getString("login");
                    String nome = rs.getString("nome");
                    // Exibir "Nome (login)" para facilitar identificacao
                    if (nome != null && !nome.trim().isEmpty() && !nome.equalsIgnoreCase(login)) {
                        nomes.add(nome + " (" + login + ")");
                    } else {
                        nomes.add(login);
                    }
                    logins.add(login);
                }
                rs.close();
                stmt.close();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar usuarios para o spinner", e);
            }

            runOnUiThread(() -> {
                nomesUsuarios.clear();
                loginsUsuarios.clear();

                if (nomes.isEmpty()) {
                    // Se nao conseguiu carregar, colocar item padrao
                    nomesUsuarios.add("Nenhum usuario encontrado");
                    loginsUsuarios.add("");
                } else {
                    nomesUsuarios.addAll(nomes);
                    loginsUsuarios.addAll(logins);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        LoginActivity.this,
                        R.layout.spinner_item_login,
                        nomesUsuarios
                );
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_login);
                spinnerUsuario.setAdapter(adapter);

                // Tentar selecionar o ultimo usuario logado
                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                String ultimoLogin = prefs.getString("user_login", "");
                if (!ultimoLogin.isEmpty()) {
                    int idx = loginsUsuarios.indexOf(ultimoLogin);
                    if (idx >= 0) {
                        spinnerUsuario.setSelection(idx);
                    }
                }

                // v8.0.23.0 - Marcar como carregado para evitar reload desnecessario
                usuariosCarregados = !nomes.isEmpty();
            });
        }).start();
    }

    private String getLoginUsuarioSelecionado() {
        int pos = spinnerUsuario.getSelectedItemPosition();
        if (pos < 0 || pos >= loginsUsuarios.size()) return "";
        return loginsUsuarios.get(pos);
    }

    private void cadastrarBiometriaUsuarioSelecionado() {
        String error = BiometricAuthHelper.getFingerprintAvailabilityError(this);
        if (error != null) {
            showError(error);
            return;
        }

        String login = getLoginUsuarioSelecionado();
        String senha = etSenha.getText().toString().trim();
        if (login.isEmpty()) {
            showError("Selecione um usuario antes de cadastrar a digital.");
            return;
        }
        if (senha.isEmpty()) {
            showError("Digite a senha do usuario selecionado para vincular a digital.");
            return;
        }

        EditText etDescricao = new EditText(this);
        etDescricao.setHint("Ex: Indicador direito");
        etDescricao.setSingleLine(true);
        etDescricao.setTextColor(0xFFFFFFFF);
        etDescricao.setHintTextColor(0xFF90A4AE);
        etDescricao.setPadding(24, 12, 24, 12);

        new AlertDialog.Builder(this)
                .setTitle("Cadastrar Digital")
                .setMessage("A senha sera validada e, em seguida, o aparelho pedira a digital para criar o vinculo.")
                .setView(etDescricao)
                .setPositiveButton("Continuar", (d, w) -> {
                    String descricao = etDescricao.getText().toString().trim();
                    if (descricao.isEmpty()) descricao = "Digital";
                    validarSenhaEAutenticarCadastro(login, senha, descricao);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void validarSenhaEAutenticarCadastro(String login, String senha, String descricao) {
        showLoading("Validando usuario...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                BiometricCredentialStore.SessionUser user =
                        BiometricCredentialStore.findActiveUserByPassword(conn, login, senha);

                hideLoading();
                if (user == null) {
                    showError("Usuario ou senha invalidos. Confira a senha antes de cadastrar a digital.");
                    return;
                }

                runOnUiThread(() -> BiometricAuthHelper.authenticate(
                        this,
                        "Cadastrar Digital",
                        "Confirme a digital para vincular " + user.nome + " (" + user.login + ")",
                        new BiometricAuthHelper.Callback() {
                            @Override
                            public void onSuccess() {
                                salvarVinculoBiometrico(user, senha, descricao);
                            }

                            @Override
                            public void onError(String message) {
                                tratarErroBiometria(message);
                            }
                        }));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LOGIN);
            }
        }).start();
    }

    private void salvarVinculoBiometrico(BiometricCredentialStore.SessionUser user,
                                         String senha, String descricao) {
        showLoading("Salvando digital...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                int id = BiometricCredentialStore.addCredential(
                        conn, user.id, descricao, user.login, senha);
                hideLoading();
                showSuccess("Digital cadastrada com sucesso!\n\n"
                        + "Usuario: " + user.nome + "\n"
                        + "Vinculo: " + descricao + "\n"
                        + "Codigo: " + id);
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void iniciarLoginBiometrico() {
        String error = BiometricAuthHelper.getFingerprintAvailabilityError(this);
        if (error != null) {
            showError(error);
            return;
        }

        showLoading("Carregando digitais...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                List<BiometricCredentialStore.Credential> credenciais =
                        BiometricCredentialStore.listActive(conn);
                hideLoading();

                if (credenciais.isEmpty()) {
                    showError("Nenhuma digital cadastrada.\n\n"
                            + "Selecione um usuario, digite a senha e toque em Cadastrar Digital.");
                    return;
                }

                runOnUiThread(() -> mostrarVinculosBiometricos(credenciais));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LOGIN);
            }
        }).start();
    }

    private void mostrarVinculosBiometricos(List<BiometricCredentialStore.Credential> credenciais) {
        CharSequence[] items = new CharSequence[credenciais.size()];
        for (int i = 0; i < credenciais.size(); i++) {
            BiometricCredentialStore.Credential c = credenciais.get(i);
            items[i] = c.label() + "\n" + c.detail();
        }

        new AlertDialog.Builder(this)
                .setTitle("Entrar com Digital")
                .setItems(items, (d, which) -> autenticarVinculoBiometrico(credenciais.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void autenticarVinculoBiometrico(BiometricCredentialStore.Credential credential) {
        BiometricAuthHelper.authenticate(
                this,
                "Entrar com Digital",
                credential.label(),
                new BiometricAuthHelper.Callback() {
                    @Override
                    public void onSuccess() {
                        concluirLoginBiometrico(credential.id);
                    }

                    @Override
                    public void onError(String message) {
                        tratarErroBiometria(message);
                    }
                });
    }

    private void concluirLoginBiometrico(int credentialId) {
        showLoading("Conectando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                LicenseStatus statusLicenca = verificarStatusLicenca();
                BiometricCredentialStore.SessionUser user =
                        BiometricCredentialStore.getSessionUserForCredential(conn, credentialId);
                if (user == null) {
                    hideLoading();
                    showError("Este vinculo biometrico nao e mais valido.\n\n"
                            + "A senha pode ter sido alterada ou o usuario foi inativado. "
                            + "Cadastre a digital novamente.");
                    return;
                }

                BiometricCredentialStore.markUsed(conn, credentialId);
                try {
                    PermissionManager.getInstance(this).carregarPermissoes(user.id);
                    Log.d(TAG, "Permissoes carregadas para login biometrico do usuario " + user.id);
                } catch (Exception permEx) {
                    Log.e(TAG, "Erro ao carregar permissoes no login biometrico (nao-critico)", permEx);
                }

                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                prefs.edit()
                        .putInt("user_id", user.id)
                        .putString("user_nome", user.nome)
                        .putString("user_nivel", user.nivel)
                        .putString("user_login", user.login)
                        .apply();
                UserActionLogger.log(this, "LOGIN_BIOMETRIA", "Autenticacao",
                        "Login realizado com digital cadastrada");

                hideLoading();
                runOnUiThread(() -> abrirProximaTelaAposLogin(statusLicenca));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LOGIN);
            }
        }).start();
    }

    private void tratarErroBiometria(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Autenticacao biometrica nao concluida.";
        }
        String lower = message.toLowerCase();
        if (lower.contains("cancel")) {
            showToast(message);
        } else {
            showError(message);
        }
    }

    private LicenseStatus verificarStatusLicenca() {
        LicenseStatus status = new LicenseStatus();
        try {
            status.valida = LicencaManager.verificarLicenca(this);
            Log.d(TAG, "Verificacao de licenca: " + (status.valida ? "VALIDA" : "INVALIDA"));
            if (!status.valida) {
                status.expirada = LicencaManager.isLicencaExpirada(this);
                if (status.expirada) {
                    status.dataExpiracao = LicencaManager.getDataExpiracao(this);
                    Log.d(TAG, "Licenca VENCIDA em: " + status.dataExpiracao);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na verificacao de licenca", e);
        }
        return status;
    }

    private void abrirProximaTelaAposLogin(LicenseStatus status) {
        if (!status.valida) {
            if (status.expirada) {
                String msg = "Sua licenca esta vencida!";
                if (status.dataExpiracao != null) {
                    msg += "\n\nData de expiracao: " + status.dataExpiracao;
                }
                msg += "\n\nPor favor, insira uma nova licenca valida para continuar utilizando o sistema.";

                new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("Licenca Vencida")
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("Renovar Licenca", (dialog, which) -> {
                            startActivity(new Intent(LoginActivity.this, LicencaActivity.class));
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                        })
                        .show();
            } else {
                startActivity(new Intent(this, LicencaActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        } else {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        }
    }

    private static class LicenseStatus {
        boolean valida = false;
        boolean expirada = false;
        String dataExpiracao;
    }

    /**
     * Realiza o login do usuario.
     * 
     * v6.9.2 - Otimizado:
     * - Removida chamada redundante a createTables() (ja feita pela SplashActivity)
     * - Removida chamada redundante a PermissionDatabaseSetup.setup() (ja feita pela SplashActivity)
     * - Login mais rapido: apenas autentica, verifica licenca e carrega permissoes
     */
    private void doLogin() {
        int pos = spinnerUsuario.getSelectedItemPosition();
        if (pos < 0 || pos >= loginsUsuarios.size()) {
            showError("Por favor, selecione um usuario.");
            return;
        }

        String usuario = loginsUsuarios.get(pos);
        String senha = etSenha.getText().toString().trim();

        if (usuario.isEmpty()) {
            showError("Nenhum usuario disponivel. Verifique a conexao com o banco de dados.");
            return;
        }

        if (senha.isEmpty()) {
            showError("Por favor, digite a senha para entrar.");
            return;
        }

        showLoading("Conectando...");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v6.9.2 - Removido: db.createTables() redundante
                // v6.9.2 - Removido: PermissionDatabaseSetup.setup() redundante
                // Ambos ja foram feitos pela SplashActivity com cache inteligente

                // Check license - agora com cache local robusto
                boolean licencaValida = false;
                boolean licencaExpirada = false;
                String dataExpiracao = null;
                try {
                    licencaValida = LicencaManager.verificarLicenca(this);
                    Log.d(TAG, "Verificacao de licenca: " + (licencaValida ? "VALIDA" : "INVALIDA"));

                    // Se a licenca nao e valida, verificar se e porque esta vencida
                    if (!licencaValida) {
                        licencaExpirada = LicencaManager.isLicencaExpirada(this);
                        if (licencaExpirada) {
                            dataExpiracao = LicencaManager.getDataExpiracao(this);
                            Log.d(TAG, "Licenca VENCIDA em: " + dataExpiracao);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro na verificacao de licenca", e);
                }

                // Authenticate user
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, nome, nivel FROM usuarios WHERE login = ? AND senha = ? AND ativo = 1");
                ps.setString(1, usuario);
                ps.setString(2, senha);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("id");
                    String nome = rs.getString("nome");
                    String nivel = rs.getString("nivel");
                    rs.close();
                    ps.close();

                    // Carregar permissoes do usuario logado
                    try {
                        PermissionManager.getInstance(this).carregarPermissoes(userId);
                        Log.d(TAG, "Permissoes carregadas para usuario " + userId +
                                " (perfil: " + PermissionManager.getInstance(this).getPerfilNome() + ")");
                    } catch (Exception permEx) {
                        Log.e(TAG, "Erro ao carregar permissoes (nao-critico)", permEx);
                    }

                    // Save session
                    SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                    prefs.edit()
                            .putInt("user_id", userId)
                            .putString("user_nome", nome)
                            .putString("user_nivel", nivel)
                            .putString("user_login", usuario)
                            .apply();
                    UserActionLogger.log(this, "LOGIN", "Autenticacao", "Login realizado com sucesso");

                    hideLoading();

                    final boolean licencaFinal = licencaValida;
                    final boolean expiradaFinal = licencaExpirada;
                    final String dataExpFinal = dataExpiracao;
                    runOnUiThread(() -> {
                        if (!licencaFinal) {
                            if (expiradaFinal) {
                                // Licenca existe mas esta VENCIDA - mostrar mensagem e redirecionar
                                String msg = "Sua licenca esta vencida!";
                                if (dataExpFinal != null) {
                                    msg += "\n\nData de expiracao: " + dataExpFinal;
                                }
                                msg += "\n\nPor favor, insira uma nova licenca valida para continuar utilizando o sistema.";

                                new AlertDialog.Builder(LoginActivity.this)
                                        .setTitle("Licenca Vencida")
                                        .setMessage(msg)
                                        .setCancelable(false)
                                        .setPositiveButton("Renovar Licenca", (dialog, which) -> {
                                            startActivity(new Intent(LoginActivity.this, LicencaActivity.class));
                                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                                        })
                                        .show();
                            } else {
                                // Nao tem licenca nenhuma - redirecionar direto
                                startActivity(new Intent(this, LicencaActivity.class));
                                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            }
                        } else {
                            startActivity(new Intent(this, MainActivity.class));
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            finish();
                        }
                    });
                } else {
                    rs.close();
                    ps.close();
                    hideLoading();
                    showError("Senha incorreta.\n\nVerifique sua senha e tente novamente.");
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LOGIN);
            }
        }).start();
    }
}
