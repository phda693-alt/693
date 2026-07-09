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

public class CadastroFormaPagamentoActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.FORMAS_PAGAMENTO_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Formas de Pagamento");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.FORMAS_PAGAMENTO_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar esta forma de pagamento?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.FORMAS_PAGAMENTO_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.FORMAS_PAGAMENTO_CRIAR)) {
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
                String sql = "SELECT * FROM formas_pagamento";
                String busca = etBusca.getText().toString().trim();
                sql += " WHERE ativo = 1";
                sql += " ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", rs.getString("descricao"));
                    map.put("line2", "Tipo: " + safeStr(rs.getString("tipo")) + " | Parcela: " + (rs.getInt("permite_parcelamento") == 1 ? "Sim" : "Nao"));
                    map.put("descricao", rs.getString("descricao"));
                    map.put("tipo", rs.getString("tipo"));
                    map.put("permite_parcelamento", rs.getInt("permite_parcelamento"));
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

        EditText et_descricao = new EditText(this);
        et_descricao.setHint("Descricao");
        et_descricao.setTextColor(0xFFFFFFFF);
        et_descricao.setHintTextColor(0xFF90A4AE);
        et_descricao.setBackgroundResource(R.drawable.input_bg);
        et_descricao.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_descricao = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_descricao.setMargins(0, 8, 0, 8);
        et_descricao.setLayoutParams(lp_descricao);
        if (record != null && record.get("descricao") != null) et_descricao.setText(safeStr(record.get("descricao")));
        container.addView(et_descricao);

        EditText et_tipo = new EditText(this);
        et_tipo.setHint("Tipo (dinheiro/cartao/pix/outros)");
        et_tipo.setTextColor(0xFFFFFFFF);
        et_tipo.setHintTextColor(0xFF90A4AE);
        et_tipo.setBackgroundResource(R.drawable.input_bg);
        et_tipo.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_tipo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_tipo.setMargins(0, 8, 0, 8);
        et_tipo.setLayoutParams(lp_tipo);
        if (record != null && record.get("tipo") != null) et_tipo.setText(safeStr(record.get("tipo")));
        container.addView(et_tipo);

        EditText et_permite_parcelamento = new EditText(this);
        et_permite_parcelamento.setHint("Permite Parcelamento (1=Sim, 0=Nao)");
        et_permite_parcelamento.setTextColor(0xFFFFFFFF);
        et_permite_parcelamento.setHintTextColor(0xFF90A4AE);
        et_permite_parcelamento.setBackgroundResource(R.drawable.input_bg);
        et_permite_parcelamento.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_permite_parcelamento = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_permite_parcelamento.setMargins(0, 8, 0, 8);
        et_permite_parcelamento.setLayoutParams(lp_permite_parcelamento);
        if (record != null && record.get("permite_parcelamento") != null) et_permite_parcelamento.setText(safeStr(record.get("permite_parcelamento")));
        container.addView(et_permite_parcelamento);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" : "Editar")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(record != null ? ((Number) record.get("id")).intValue() : 0, et_descricao.getText().toString().trim(), et_tipo.getText().toString().trim(), et_permite_parcelamento.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_descricao, String val_tipo, String val_permite_parcelamento) {
        if (val_descricao.isEmpty()) { showError("Por favor, preencha o campo Descricao antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES (?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE formas_pagamento SET descricao=?,tipo=?,permite_parcelamento=? WHERE id=?");
                }
                ps.setString(1, val_descricao);
                ps.setString(2, val_tipo);
                try { ps.setInt(3, Integer.parseInt(val_permite_parcelamento)); } catch (Exception e) { ps.setInt(3, 0); }
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
                PreparedStatement ps = conn.prepareStatement("UPDATE formas_pagamento SET ativo = 0 WHERE id = ?");
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
