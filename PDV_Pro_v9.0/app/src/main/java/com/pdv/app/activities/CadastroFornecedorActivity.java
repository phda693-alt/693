package com.pdv.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.UserActionLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cadastro e pesquisa de fornecedores usados nas notas de entrada. */
public class CadastroFornecedorActivity extends BaseActivity {
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.FORNECEDORES_ACESSAR)) return;

        ((TextView) findViewById(R.id.tvTitle)).setText("Fornecedores");
        etBusca = findViewById(R.id.etBusca);
        etBusca.setHint("Buscar por nome, documento ou contato...");

        RecyclerView recycler = findViewById(R.id.recyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, String.valueOf(item.get("nome"))
                    + (((Number) item.get("ativo")).intValue() == 1 ? "" : " (Inativo)"));
            String documento = texto(item.get("documento"));
            String telefone = texto(item.get("telefone"));
            String contato = texto(item.get("contato"));
            String detalhe = (documento.isEmpty() ? "" : documento)
                    + (contato.isEmpty() ? "" : (documento.isEmpty() ? "" : " | ") + contato)
                    + (telefone.isEmpty() ? "" : ((documento.isEmpty() && contato.isEmpty()) ? "" : " | ") + telefone);
            holder.setText(R.id.tvLine2, detalhe.isEmpty() ? "Sem dados de contato" : detalhe);
            ImageView foto = holder.find(R.id.ivFoto);
            if (foto != null) foto.setVisibility(View.GONE);

            Button editar = holder.find(R.id.btnEditar);
            if (editar != null) {
                editar.setVisibility(PermissionHelper.temPermissao(this, PermissionConstants.FORNECEDORES_EDITAR)
                        ? View.VISIBLE : View.GONE);
                editar.setOnClickListener(v -> abrirEditor(item));
            }

            Button inativar = holder.find(R.id.btnInativar);
            if (inativar != null) {
                boolean ativo = ((Number) item.get("ativo")).intValue() == 1;
                inativar.setText(ativo ? "Inativar" : "Ativar");
                inativar.setVisibility(PermissionHelper.temPermissao(this, PermissionConstants.FORNECEDORES_EXCLUIR)
                        ? View.VISIBLE : View.GONE);
                inativar.setOnClickListener(v -> alterarStatus(((Number) item.get("id")).intValue(), !ativo));
            }
        });
        recycler.setAdapter(adapter);

        View btnNovo = findViewById(R.id.btnNovo);
        PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.FORNECEDORES_CRIAR);
        btnNovo.setOnClickListener(v -> abrirEditor(null));
        findViewById(R.id.btnBuscar).setOnClickListener(v -> carregar());
        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { carregar(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        carregar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    private void carregar() {
        String termo = etBusca == null ? "" : etBusca.getText().toString().trim();
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id,nome,razao_social,documento,contato,telefone,email,endereco,ativo "
                                + "FROM fornecedores WHERE nome LIKE ? OR razao_social LIKE ? "
                                + "OR documento LIKE ? OR contato LIKE ? ORDER BY ativo DESC,nome");
                String like = "%" + termo + "%";
                for (int i = 1; i <= 4; i++) ps.setString(i, like);
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> itens = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("nome", rs.getString("nome"));
                    item.put("razao_social", rs.getString("razao_social"));
                    item.put("documento", rs.getString("documento"));
                    item.put("contato", rs.getString("contato"));
                    item.put("telefone", rs.getString("telefone"));
                    item.put("email", rs.getString("email"));
                    item.put("endereco", rs.getString("endereco"));
                    item.put("ativo", rs.getInt("ativo"));
                    itens.add(item);
                }
                rs.close();
                ps.close();
                runOnUiThread(() -> adapter.setItems(itens));
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void abrirEditor(Map<String, Object> item) {
        String permissao = item == null ? PermissionConstants.FORNECEDORES_CRIAR : PermissionConstants.FORNECEDORES_EDITAR;
        if (!PermissionHelper.verificar(this, permissao)) return;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        form.setPadding(pad, pad / 2, pad, 0);
        EditText nome = campo("Nome do fornecedor *", item, "nome");
        EditText razao = campo("Razao social", item, "razao_social");
        EditText documento = campo("CNPJ ou CPF", item, "documento");
        EditText contato = campo("Pessoa de contato", item, "contato");
        EditText telefone = campo("Telefone", item, "telefone");
        EditText email = campo("E-mail", item, "email");
        EditText endereco = campo("Endereco", item, "endereco");
        form.addView(nome); form.addView(razao); form.addView(documento); form.addView(contato);
        form.addView(telefone); form.addView(email); form.addView(endereco);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item == null ? "Novo Fornecedor" : "Editar Fornecedor")
                .setView(form)
                .setPositiveButton("Salvar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nomeValor = nome.getText().toString().trim();
            if (nomeValor.isEmpty()) {
                nome.setError("Informe o nome do fornecedor");
                return;
            }
            dialog.dismiss();
            salvar(item == null ? 0 : ((Number) item.get("id")).intValue(), nomeValor,
                    razao.getText().toString().trim(), documento.getText().toString().trim(),
                    contato.getText().toString().trim(), telefone.getText().toString().trim(),
                    email.getText().toString().trim(), endereco.getText().toString().trim());
        }));
        dialog.show();
    }

    private EditText campo(String hint, Map<String, Object> item, String chave) {
        EditText campo = new EditText(this);
        campo.setHint(hint);
        campo.setTextColor(0xFFFFFFFF);
        campo.setHintTextColor(0xFF90A4AE);
        campo.setBackgroundResource(R.drawable.input_bg);
        campo.setPadding(24, 18, 24, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 10);
        campo.setLayoutParams(lp);
        if (item != null && item.get(chave) != null) campo.setText(String.valueOf(item.get(chave)));
        return campo;
    }

    private void salvar(int id, String nome, String razao, String documento, String contato,
                        String telefone, String email, String endereco) {
        showLoading("Salvando fornecedor...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO fornecedores "
                            + "(nome,razao_social,documento,contato,telefone,email,endereco,ativo) VALUES (?,?,?,?,?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE fornecedores SET nome=?,razao_social=?,documento=?,"
                            + "contato=?,telefone=?,email=?,endereco=?,atualizado_em=NOW() WHERE id=?");
                }
                ps.setString(1, nome); ps.setString(2, vazioComoNull(razao)); ps.setString(3, vazioComoNull(documento));
                ps.setString(4, vazioComoNull(contato)); ps.setString(5, vazioComoNull(telefone));
                ps.setString(6, vazioComoNull(email)); ps.setString(7, vazioComoNull(endereco));
                if (id != 0) ps.setInt(8, id);
                ps.executeUpdate();
                ps.close();
                UserActionLogger.log(this, id == 0 ? "CRIAR" : "EDITAR", "Fornecedores", nome);
                hideLoading();
                showToast("Fornecedor salvo.");
                carregar();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void alterarStatus(int id, boolean ativar) {
        new Thread(() -> {
            try {
                PreparedStatement ps = DatabaseHelper.getInstance(this).getConnection()
                        .prepareStatement("UPDATE fornecedores SET ativo=?,atualizado_em=NOW() WHERE id=?");
                ps.setInt(1, ativar ? 1 : 0);
                ps.setInt(2, id);
                ps.executeUpdate();
                ps.close();
                carregar();
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    private static String texto(Object valor) { return valor == null ? "" : String.valueOf(valor).trim(); }
    private static String vazioComoNull(String valor) { return valor == null || valor.isEmpty() ? null : valor; }
}
