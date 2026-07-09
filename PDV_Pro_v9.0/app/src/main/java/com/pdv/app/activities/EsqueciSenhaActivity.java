package com.pdv.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.ErrorHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EsqueciSenhaActivity extends BaseActivity {
    private static final String TAG = "EsqueciSenhaActivity";

    private Spinner spinnerUsuarioRecuperar;
    private Spinner spinnerAdmin;
    private EditText etSenhaAdmin, etNovaSenha, etConfirmarNovaSenha;
    private Button btnRedefinir;
    private TextView tvVoltar;

    // Listas para o spinner de usuarios (todos ativos)
    private List<String> nomesUsuarios = new ArrayList<>();
    private List<String> loginsUsuarios = new ArrayList<>();

    // Listas para o spinner de admins
    private List<String> nomesAdmins = new ArrayList<>();
    private List<String> loginsAdmins = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esqueci_senha);

        spinnerUsuarioRecuperar = findViewById(R.id.spinnerUsuarioRecuperar);
        spinnerAdmin = findViewById(R.id.spinnerAdmin);
        etSenhaAdmin = findViewById(R.id.etSenhaAdmin);
        etNovaSenha = findViewById(R.id.etNovaSenha);
        etConfirmarNovaSenha = findViewById(R.id.etConfirmarNovaSenha);
        btnRedefinir = findViewById(R.id.btnRedefinir);
        tvVoltar = findViewById(R.id.tvVoltar);

        // Animacoes
        View tvTitulo = findViewById(R.id.tvTitulo);
        View tvDescricao = findViewById(R.id.tvDescricao);
        View tvPasso1 = findViewById(R.id.tvPasso1);
        View spinnerContainer = findViewById(R.id.spinnerContainerRecuperar);
        View tvPasso2 = findViewById(R.id.tvPasso2);
        View tvDescAdmin = findViewById(R.id.tvDescAdmin);
        View spinnerContainerAdmin = findViewById(R.id.spinnerContainerAdmin);
        View tvPasso3 = findViewById(R.id.tvPasso3);

        AnimUtils.fadeIn(tvTitulo, 100);
        AnimUtils.fadeIn(tvDescricao, 200);
        AnimUtils.slideUp(tvPasso1, 300);
        AnimUtils.slideUp(spinnerContainer, 350);
        AnimUtils.slideUp(tvPasso2, 400);
        AnimUtils.slideUp(tvDescAdmin, 420);
        AnimUtils.slideUp(spinnerContainerAdmin, 450);
        AnimUtils.slideUp(etSenhaAdmin, 500);
        AnimUtils.slideUp(tvPasso3, 550);
        AnimUtils.slideUp(etNovaSenha, 600);
        AnimUtils.slideUp(etConfirmarNovaSenha, 650);
        AnimUtils.slideUp(btnRedefinir, 700);
        AnimUtils.fadeIn(tvVoltar, 800);

        btnRedefinir.setOnClickListener(v -> redefinirSenha());
        tvVoltar.setOnClickListener(v -> finish());

        carregarUsuarios();
    }

    /**
     * Carrega todos os usuarios ativos e separa os administradores.
     */
    private void carregarUsuarios() {
        new Thread(() -> {
            List<String> nomes = new ArrayList<>();
            List<String> logins = new ArrayList<>();
            List<String> nomesAdm = new ArrayList<>();
            List<String> loginsAdm = new ArrayList<>();

            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Carregar todos os usuarios ativos
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT login, nome, nivel FROM usuarios WHERE ativo = 1 ORDER BY nome ASC");

                while (rs.next()) {
                    String login = rs.getString("login");
                    String nome = rs.getString("nome");
                    String nivel = rs.getString("nivel");

                    String displayName;
                    if (nome != null && !nome.trim().isEmpty() && !nome.equalsIgnoreCase(login)) {
                        displayName = nome + " (" + login + ")";
                    } else {
                        displayName = login;
                    }

                    // Adicionar a lista geral
                    nomes.add(displayName);
                    logins.add(login);

                    // Se for admin, adicionar tambem a lista de admins
                    if ("admin".equalsIgnoreCase(nivel)) {
                        nomesAdm.add(displayName);
                        loginsAdm.add(login);
                    }
                }
                rs.close();
                stmt.close();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar usuarios", e);
            }

            runOnUiThread(() -> {
                // Spinner de usuarios (todos)
                nomesUsuarios.clear();
                loginsUsuarios.clear();
                if (nomes.isEmpty()) {
                    nomesUsuarios.add("Nenhum usuario encontrado");
                    loginsUsuarios.add("");
                } else {
                    nomesUsuarios.addAll(nomes);
                    loginsUsuarios.addAll(logins);
                }

                ArrayAdapter<String> adapterUsuarios = new ArrayAdapter<>(
                        this, R.layout.spinner_item_login, nomesUsuarios);
                adapterUsuarios.setDropDownViewResource(R.layout.spinner_dropdown_item_login);
                spinnerUsuarioRecuperar.setAdapter(adapterUsuarios);

                // Spinner de admins
                nomesAdmins.clear();
                loginsAdmins.clear();
                if (nomesAdm.isEmpty()) {
                    nomesAdmins.add("Nenhum administrador encontrado");
                    loginsAdmins.add("");
                } else {
                    nomesAdmins.addAll(nomesAdm);
                    loginsAdmins.addAll(loginsAdm);
                }

                ArrayAdapter<String> adapterAdmins = new ArrayAdapter<>(
                        this, R.layout.spinner_item_login, nomesAdmins);
                adapterAdmins.setDropDownViewResource(R.layout.spinner_dropdown_item_login);
                spinnerAdmin.setAdapter(adapterAdmins);
            });
        }).start();
    }

    /**
     * Valida a autorizacao do admin e redefine a senha do usuario selecionado.
     */
    private void redefinirSenha() {
        // Validar usuario selecionado
        int posUsuario = spinnerUsuarioRecuperar.getSelectedItemPosition();
        if (posUsuario < 0 || posUsuario >= loginsUsuarios.size()) {
            showError("Por favor, selecione o usuario que deseja recuperar a senha.");
            return;
        }
        String loginUsuario = loginsUsuarios.get(posUsuario);
        if (loginUsuario.isEmpty()) {
            showError("Nenhum usuario disponivel.\n\nVerifique a conexao com o banco de dados.");
            return;
        }

        // Validar admin selecionado
        int posAdmin = spinnerAdmin.getSelectedItemPosition();
        if (posAdmin < 0 || posAdmin >= loginsAdmins.size()) {
            showError("Por favor, selecione o administrador que esta autorizando.");
            return;
        }
        String loginAdmin = loginsAdmins.get(posAdmin);
        if (loginAdmin.isEmpty()) {
            showError("Nenhum administrador encontrado no sistema.\n\nE necessario ter pelo menos um usuario com nivel 'admin' para autorizar a redefinicao de senha.");
            return;
        }

        // Validar senha do admin
        String senhaAdmin = etSenhaAdmin.getText().toString().trim();
        if (senhaAdmin.isEmpty()) {
            showError("Por favor, peca ao administrador para digitar a senha dele.");
            return;
        }

        // Validar nova senha
        String novaSenha = etNovaSenha.getText().toString().trim();
        String confirmarSenha = etConfirmarNovaSenha.getText().toString().trim();

        if (novaSenha.isEmpty()) {
            showError("Por favor, digite a nova senha.");
            return;
        }
        if (novaSenha.length() < 3) {
            showError("A nova senha deve ter pelo menos 3 caracteres.");
            return;
        }
        if (!novaSenha.equals(confirmarSenha)) {
            showError("A nova senha e a confirmacao nao sao iguais.\n\nDigite a mesma senha nos dois campos.");
            return;
        }

        showLoading("Verificando autorizacao...");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Verificar credenciais do admin
                PreparedStatement psAdmin = conn.prepareStatement(
                        "SELECT id FROM usuarios WHERE login = ? AND senha = ? AND nivel = 'admin' AND ativo = 1");
                psAdmin.setString(1, loginAdmin);
                psAdmin.setString(2, senhaAdmin);
                ResultSet rsAdmin = psAdmin.executeQuery();

                if (!rsAdmin.next()) {
                    rsAdmin.close();
                    psAdmin.close();
                    hideLoading();
                    showError("Senha do administrador incorreta.\n\nVerifique a senha e tente novamente.");
                    return;
                }
                rsAdmin.close();
                psAdmin.close();

                // Admin autorizado - atualizar a senha do usuario
                PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE usuarios SET senha = ? WHERE login = ? AND ativo = 1");
                psUpdate.setString(1, novaSenha);
                psUpdate.setString(2, loginUsuario);
                int rowsAffected = psUpdate.executeUpdate();
                psUpdate.close();

                hideLoading();

                if (rowsAffected > 0) {
                    runOnUiThread(() -> {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("\u2705  Senha Redefinida")
                                .setMessage("A senha do usuario foi redefinida com sucesso!\n\nVoce ja pode fazer login com a nova senha.")
                                .setCancelable(false)
                                .setPositiveButton("Voltar ao Login", (dialog, which) -> finish())
                                .show();
                    });
                } else {
                    showError("Nao foi possivel redefinir a senha.\n\nO usuario pode ter sido desativado. Tente novamente.");
                }

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }
}
