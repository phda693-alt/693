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

public class CadastroEntregadorActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.ENTREGADORES_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Entregadores");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.ENTREGADORES_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar este entregador?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.ENTREGADORES_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.ENTREGADORES_CRIAR)) {
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
                String sql = "SELECT * FROM entregadores";
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
                    String veiculo = safeStr(rs.getString("veiculo"));
                    String placa = safeStr(rs.getString("placa"));
                    map.put("line2", "Cel: " + safeStr(rs.getString("celular"))
                            + (!veiculo.isEmpty() ? " | Veiculo: " + veiculo : "")
                            + (!placa.isEmpty() ? " | Placa: " + placa : ""));
                    map.put("nome", rs.getString("nome"));
                    map.put("celular", rs.getString("celular"));
                    map.put("veiculo", rs.getString("veiculo"));
                    map.put("modelo", rs.getString("modelo"));
                    map.put("placa", rs.getString("placa"));
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

        EditText et_veiculo = criarCampo("Veiculo (ex: Moto, Carro)", record, "veiculo");
        container.addView(et_veiculo);
        EditText et_modelo = criarCampo("Marca/Modelo", record, "modelo");
        container.addView(et_modelo);
        EditText et_placa = criarCampo("Placa", record, "placa");
        et_placa.setAllCaps(true);
        container.addView(et_placa);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" : "Editar")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(record != null ? ((Number) record.get("id")).intValue() : 0,
                            et_nome.getText().toString().trim(), et_celular.getText().toString().trim(),
                            et_veiculo.getText().toString().trim(), et_modelo.getText().toString().trim(),
                            et_placa.getText().toString().trim().toUpperCase());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText criarCampo(String hint, Map<String, Object> record, String key) {
        EditText campo = new EditText(this);
        campo.setHint(hint);
        campo.setTextColor(0xFFFFFFFF);
        campo.setHintTextColor(0xFF90A4AE);
        campo.setBackgroundResource(R.drawable.input_bg);
        campo.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        campo.setLayoutParams(lp);
        if (record != null && record.get(key) != null) campo.setText(safeStr(record.get(key)));
        return campo;
    }

    private void saveRecord(int id, String val_nome, String val_celular,
                            String val_veiculo, String val_modelo, String val_placa) {
        if (val_nome.isEmpty()) { showError("Por favor, preencha o campo Nome antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO entregadores (nome,celular,veiculo,modelo,placa,ativo) VALUES (?,?,?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE entregadores SET nome=?,celular=?,veiculo=?,modelo=?,placa=? WHERE id=?");
                }
                ps.setString(1, val_nome);
                ps.setString(2, val_celular);
                ps.setString(3, val_veiculo);
                ps.setString(4, val_modelo);
                ps.setString(5, val_placa);
                if (id > 0) ps.setInt(6, id);
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
                PreparedStatement ps = conn.prepareStatement("UPDATE entregadores SET ativo = 0 WHERE id = ?");
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
