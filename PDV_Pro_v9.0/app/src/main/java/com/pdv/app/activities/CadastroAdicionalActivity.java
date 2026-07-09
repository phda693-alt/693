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

public class CadastroAdicionalActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.ADICIONAIS_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Adicionais");

        etBusca = findViewById(R.id.etBusca);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, safeStr(item.get("line1")));
            holder.setText(R.id.tvLine2, safeStr(item.get("line2")));
            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            // Botao Duplicar
            Button btnDuplicar = holder.find(R.id.btnDuplicar);
            if (btnDuplicar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.ADICIONAIS_CRIAR)) {
                    btnDuplicar.setVisibility(View.VISIBLE);
                    btnDuplicar.setOnClickListener(v -> {
                        Map<String, Object> duplicate = new LinkedHashMap<>(item);
                        duplicate.remove("id");
                        duplicate.put("descricao", safeStr(item.get("descricao")) + " (Copia)");
                        showEditDialog(duplicate);
                    });
                } else {
                    btnDuplicar.setVisibility(View.GONE);
                }
            }

            // Botao Editar
            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.ADICIONAIS_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar este adicional?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.ADICIONAIS_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.ADICIONAIS_CRIAR)) {
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
                String sql = "SELECT * FROM adicionais WHERE ativo = 1 ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", rs.getString("descricao"));
                    map.put("line2", "Preco: R$ " + FormatUtils.formatMoney(rs.getDouble("preco")));
                    map.put("descricao", rs.getString("descricao"));
                    map.put("preco", rs.getDouble("preco"));
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
        et_descricao.setHint("Descricao do Adicional");
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

        EditText et_preco = new EditText(this);
        et_preco.setHint("Preco (R$)");
        et_preco.setTextColor(0xFFFFFFFF);
        et_preco.setHintTextColor(0xFF90A4AE);
        et_preco.setBackgroundResource(R.drawable.input_bg);
        et_preco.setPadding(32, 24, 32, 24);
        et_preco.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams lp_preco = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_preco.setMargins(0, 8, 0, 8);
        et_preco.setLayoutParams(lp_preco);
        if (record != null && record.get("preco") != null) et_preco.setText(String.valueOf(record.get("preco")));
        container.addView(et_preco);

        final int recordId = getRecordId(record);
        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo Adicional" :
                        (recordId == 0 ? "Duplicar Adicional" : "Editar Adicional"))
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    String descricao = et_descricao.getText().toString().trim();
                    String precoStr = et_preco.getText().toString().trim().replace(",", ".");
                    double preco = 0;
                    try { preco = Double.parseDouble(precoStr); } catch (Exception ignored) {}
                    saveRecord(recordId, descricao, preco);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int getRecordId(Map<String, Object> record) {
        if (record == null) return 0;
        Object value = record.get("id");
        if (value instanceof Number) return ((Number) value).intValue();
        try { return value != null ? Integer.parseInt(value.toString()) : 0; }
        catch (Exception ignored) { return 0; }
    }

    private void saveRecord(int id, String val_descricao, double val_preco) {
        if (val_descricao.isEmpty()) { showError("Por favor, preencha a descricao do adicional."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO adicionais (descricao, preco, ativo) VALUES (?, ?, 1)");
                } else {
                    ps = conn.prepareStatement("UPDATE adicionais SET descricao=?, preco=? WHERE id=?");
                }
                ps.setString(1, val_descricao);
                ps.setDouble(2, val_preco);
                if (id > 0) ps.setInt(3, id);
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
                PreparedStatement ps = conn.prepareStatement("UPDATE adicionais SET ativo = 0 WHERE id = ?");
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
