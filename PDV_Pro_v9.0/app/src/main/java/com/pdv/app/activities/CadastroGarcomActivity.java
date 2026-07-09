package com.pdv.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.*;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Tela de cadastro e gerenciamento de garcons.
 * Permite criar, editar e inativar garcons do sistema.
 */
public class CadastroGarcomActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.GARCONS_ACESSAR)) {
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Garcons");

        etBusca = findViewById(R.id.etBusca);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, safeStr(item.get("line1")));
            holder.setText(R.id.tvLine2, safeStr(item.get("line2")));
            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            // Botao Editar
            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.GARCONS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            // Botao Inativar
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                btnInativar.setVisibility(View.VISIBLE);
                btnInativar.setOnClickListener(v -> {
                    showConfirm("Inativar", "Deseja inativar este garcom?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.GARCONS_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.GARCONS_CRIAR)) {
                showEditDialog(null);
            }
        });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });

        loadData();
    }

    @Override
    protected void onResume() { super.onResume(); loadData(); }

    private String safeStr(Object o) { return o != null ? o.toString() : ""; }

    private void loadData() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String sql = "SELECT * FROM garcons WHERE ativo = 1 ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", rs.getString("nome"));
                    map.put("line2", "Celular: " + safeStr(rs.getString("celular"))
                            + " | Porcentagem: " + rs.getDouble("porcentagem") + "%");
                    map.put("nome", rs.getString("nome"));
                    map.put("celular", rs.getString("celular"));
                    map.put("porcentagem", rs.getDouble("porcentagem"));
                    list.add(map);
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showEditDialog(Map<String, Object> record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        EditText et_nome = new EditText(this);
        et_nome.setHint("Nome do Garcom");
        et_nome.setTextColor(0xFFFFFFFF);
        et_nome.setHintTextColor(0xFF90A4AE);
        et_nome.setBackgroundResource(R.drawable.input_bg);
        et_nome.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_nome = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_nome.setMargins(0, 8, 0, 8);
        et_nome.setLayoutParams(lp_nome);
        if (record != null && record.get("nome") != null) et_nome.setText(safeStr(record.get("nome")));
        container.addView(et_nome);

        EditText et_celular = new EditText(this);
        et_celular.setHint("Celular");
        et_celular.setTextColor(0xFFFFFFFF);
        et_celular.setHintTextColor(0xFF90A4AE);
        et_celular.setBackgroundResource(R.drawable.input_bg);
        et_celular.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_celular = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_celular.setMargins(0, 8, 0, 8);
        et_celular.setLayoutParams(lp_celular);
        if (record != null && record.get("celular") != null) et_celular.setText(safeStr(record.get("celular")));
        container.addView(et_celular);

        EditText et_porcentagem = new EditText(this);
        et_porcentagem.setHint("Porcentagem (%)");
        et_porcentagem.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et_porcentagem.setTextColor(0xFFFFFFFF);
        et_porcentagem.setHintTextColor(0xFF90A4AE);
        et_porcentagem.setBackgroundResource(R.drawable.input_bg);
        et_porcentagem.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_porcentagem = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_porcentagem.setMargins(0, 8, 0, 8);
        et_porcentagem.setLayoutParams(lp_porcentagem);
        if (record != null && record.get("porcentagem") != null) {
            et_porcentagem.setText(String.valueOf(record.get("porcentagem")));
        }
        container.addView(et_porcentagem);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo Garcom" : "Editar Garcom")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(
                        record != null ? ((Number) record.get("id")).intValue() : 0,
                        et_nome.getText().toString().trim(),
                        et_celular.getText().toString().trim(),
                        et_porcentagem.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_nome, String val_celular, String val_porcentagem) {
        if (val_nome.isEmpty()) {
            showError("Por favor, preencha o nome do garcom antes de salvar.");
            return;
        }
        double porcentagem = FormatUtils.parseMoney(val_porcentagem);
        if (porcentagem < 0 || porcentagem > 100) {
            showError("A porcentagem do garcom deve estar entre 0 e 100.");
            return;
        }
        final double percentualFinal = porcentagem;
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO garcons (nome,celular,porcentagem,ativo) VALUES (?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE garcons SET nome=?,celular=?,porcentagem=? WHERE id=?");
                }
                ps.setString(1, val_nome);
                ps.setString(2, val_celular);
                ps.setDouble(3, percentualFinal);
                if (id > 0) ps.setInt(4, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Garcom salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void inativarRecord(int id) {
        showLoading("Inativando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE garcons SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Garcom inativado com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
