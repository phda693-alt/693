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

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

public class CadastroVendedorActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.VENDEDORES_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Vendedores");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.VENDEDORES_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar este vendedor?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.VENDEDORES_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.VENDEDORES_CRIAR)) {
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
                String sql = "SELECT * FROM vendedores";
                String busca = etBusca.getText().toString().trim();
                sql += " WHERE ativo = 1";
                sql += " ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", rs.getString("nome"));
                    map.put("line2", "Cel: " + safeStr(rs.getString("celular")) + " | Comissao: " + rs.getDouble("comissao") + "%");
                    map.put("nome", rs.getString("nome"));
                    map.put("celular", rs.getString("celular"));
                    map.put("comissao", rs.getDouble("comissao"));
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
        et_nome.setHint("Nome");
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

        EditText et_comissao = new EditText(this);
        et_comissao.setHint("Comissao %");
        et_comissao.setTextColor(0xFFFFFFFF);
        et_comissao.setHintTextColor(0xFF90A4AE);
        et_comissao.setBackgroundResource(R.drawable.input_bg);
        et_comissao.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_comissao = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_comissao.setMargins(0, 8, 0, 8);
        et_comissao.setLayoutParams(lp_comissao);
        if (record != null && record.get("comissao") != null) et_comissao.setText(safeStr(record.get("comissao")));
        container.addView(et_comissao);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" : "Editar")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(record != null ? ((Number) record.get("id")).intValue() : 0, et_nome.getText().toString().trim(), et_celular.getText().toString().trim(), et_comissao.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_nome, String val_celular, String val_comissao) {
        if (val_nome.isEmpty()) { showError("Por favor, preencha o campo Nome antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO vendedores (nome,celular,comissao,ativo) VALUES (?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE vendedores SET nome=?,celular=?,comissao=? WHERE id=?");
                }
                ps.setString(1, val_nome);
                ps.setString(2, val_celular);
                ps.setDouble(3, FormatUtils.parseMoney(val_comissao));
                if (id > 0) ps.setInt(4, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Salvo com sucesso!");
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
                PreparedStatement ps = conn.prepareStatement("UPDATE vendedores SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Inativado com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
