package com.pdv.app.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.AnimUtils;

import java.sql.*;
import com.pdv.app.utils.ErrorHandler;

public class TrocaSenhaActivity extends BaseActivity {
    private EditText etSenhaAtual, etNovaSenha, etConfirmarSenha;
    private Button btnTrocar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_troca_senha);

        etSenhaAtual = findViewById(R.id.etSenhaAtual);
        etNovaSenha = findViewById(R.id.etNovaSenha);
        etConfirmarSenha = findViewById(R.id.etConfirmarSenha);
        btnTrocar = findViewById(R.id.btnTrocar);

        AnimUtils.animateItems(etSenhaAtual, etNovaSenha, etConfirmarSenha, btnTrocar);

        btnTrocar.setOnClickListener(v -> trocarSenha());
    }

    private void trocarSenha() {
        String senhaAtual = etSenhaAtual.getText().toString().trim();
        String novaSenha = etNovaSenha.getText().toString().trim();
        String confirmar = etConfirmarSenha.getText().toString().trim();

        if (senhaAtual.isEmpty() || novaSenha.isEmpty() || confirmar.isEmpty()) {
            showError("Existem campos obrigatorios nao preenchidos.\n\nPor favor, preencha todos os campos e tente novamente.");
            return;
        }
        if (!novaSenha.equals(confirmar)) {
            showError("A nova senha e a confirmacao nao sao iguais.\n\nDigite a mesma senha nos dois campos.");
            return;
        }

        showLoading("Alterando senha...");

        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                String login = prefs.getString("user_login", "");

                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM usuarios WHERE login = ? AND senha = ?");
                ps.setString(1, login);
                ps.setString(2, senhaAtual);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("id");
                    rs.close();
                    ps.close();

                    ps = conn.prepareStatement("UPDATE usuarios SET senha = ? WHERE id = ?");
                    ps.setString(1, novaSenha);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                    ps.close();

                    hideLoading();
                    showSuccess("Senha alterada com sucesso!");
                    runOnUiThread(() -> finish());
                } else {
                    rs.close();
                    ps.close();
                    hideLoading();
                    showError("A senha atual informada esta incorreta.\n\nVerifique e tente novamente.");
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }
}
