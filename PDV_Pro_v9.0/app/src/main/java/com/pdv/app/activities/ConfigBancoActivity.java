package com.pdv.app.activities;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.ErrorHandler;

public class ConfigBancoActivity extends BaseActivity {
    private static final String SENHA_CONFIG = "4872";

    private EditText etHost, etPorta, etDatabase, etUsername, etPassword;
    private Button btnTestar, btnSalvar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_banco);

        etHost = findViewById(R.id.etHost);
        etPorta = findViewById(R.id.etPorta);
        etDatabase = findViewById(R.id.etDatabase);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnTestar = findViewById(R.id.btnTestar);
        btnSalvar = findViewById(R.id.btnSalvar);
        TextView tvLocalIp = findViewById(R.id.tvLocalIp);
        tvLocalIp.setText("IP deste aparelho: " + com.pdv.app.utils.NetworkUtils.getLocalIpv4());

        // Load current config
        DatabaseHelper db = DatabaseHelper.getInstance(this);
        etHost.setText(db.getHost());
        etPorta.setText(String.valueOf(db.getPort()));
        etDatabase.setText(db.getDatabase());
        etUsername.setText(db.getUsername());
        etPassword.setText(db.getPassword());

        AnimUtils.animateItems(etHost, etPorta, etDatabase, etUsername, etPassword, btnTestar, btnSalvar);

        btnTestar.setOnClickListener(v -> testarConexao());
        btnSalvar.setOnClickListener(v -> pedirSenhaParaSalvar());
    }

    private void testarConexao() {
        String host = etHost.getText().toString().trim();
        String porta = etPorta.getText().toString().trim();
        String database = etDatabase.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
            showError("Existem campos obrigatorios nao preenchidos.\n\nPor favor, preencha todos os campos e tente novamente.");
            return;
        }

        int port = 3306;
        try { port = Integer.parseInt(porta); } catch (Exception ignored) {}

        showLoading("Testando conexao...");

        final int finalPort = port;
        new Thread(() -> {
            boolean success = DatabaseHelper.getInstance(this)
                    .testConnection(host, database, username, password, finalPort);
            hideLoading();
            if (success) {
                showSuccess("Conexao realizada com sucesso!");
            } else {
                showError("Nao foi possivel conectar ao servidor.\n\nVerifique se os dados de conexao estao corretos:\n- Endereco do servidor\n- Nome do banco\n- Usuario e senha\n- Porta");
            }
        }).start();
    }

    /**
     * Exibe um dialogo solicitando a senha de seguranca antes de salvar
     * as configuracoes do banco de dados.
     */
    private void pedirSenhaParaSalvar() {
        EditText etSenha = new EditText(this);
        etSenha.setHint("Digite a senha de seguranca");
        etSenha.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etSenha.setTextColor(0xFFFFFFFF);
        etSenha.setHintTextColor(0xFF90A4AE);
        etSenha.setBackgroundResource(R.drawable.input_bg);
        etSenha.setPadding(32, 24, 32, 24);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 32, 48, 0);
        container.addView(etSenha);

        new AlertDialog.Builder(this)
                .setTitle("Senha de Seguranca")
                .setMessage("Para salvar as configuracoes do banco de dados, digite a senha de seguranca:")
                .setView(container)
                .setPositiveButton("Confirmar", (dialog, which) -> {
                    String senhaDigitada = etSenha.getText().toString().trim();
                    if (SENHA_CONFIG.equals(senhaDigitada)) {
                        salvarConfig();
                    } else {
                        showError("A senha informada esta incorreta.\n\nAs configuracoes nao foram salvas por seguranca.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarConfig() {
        String host = etHost.getText().toString().trim();
        String porta = etPorta.getText().toString().trim();
        String database = etDatabase.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (host.isEmpty() || database.isEmpty() || username.isEmpty()) {
            showError("Existem campos obrigatorios nao preenchidos.\n\nPor favor, preencha todos os campos e tente novamente.");
            return;
        }

        int port = 3306;
        try { port = Integer.parseInt(porta); } catch (Exception ignored) {}

        DatabaseHelper.getInstance(this).saveConfig(host, database, username, password, port);

        // v6.9.2 - Invalidar cache de inicializacao da splash
        // Quando as configuracoes do banco mudam, a proxima inicializacao
        // deve refazer a verificacao completa de tabelas e colunas
        SplashActivity.invalidateInitCache(this);

        showToast("Configuracao salva com sucesso!");
        finish();
    }
}
