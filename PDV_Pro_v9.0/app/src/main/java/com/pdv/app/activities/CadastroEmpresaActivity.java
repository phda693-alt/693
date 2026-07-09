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

public class CadastroEmpresaActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.EMPRESA_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Empresa");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.EMPRESA_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            // Botao Inativar (Excluir para empresa)
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                btnInativar.setVisibility(View.VISIBLE);
                btnInativar.setOnClickListener(v -> {
                    showConfirm("Excluir", "Deseja excluir esta empresa?",
                            () -> deleteRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnNovo).setOnClickListener(v -> showEditDialog(null));
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
                String sql = "SELECT * FROM empresa";
                
                
                sql += " ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", rs.getString("razao_social"));
                    map.put("line2", "CNPJ: " + safeStr(rs.getString("cnpj")));
                    map.put("razao_social", rs.getString("razao_social"));
                    map.put("nome_fantasia", rs.getString("nome_fantasia"));
                    map.put("cnpj", rs.getString("cnpj"));
                    map.put("ie", rs.getString("ie"));
                    map.put("endereco", rs.getString("endereco"));
                    map.put("numero", rs.getString("numero"));
                    map.put("bairro", rs.getString("bairro"));
                    map.put("cidade", rs.getString("cidade"));
                    map.put("uf", rs.getString("uf"));
                    map.put("cep", rs.getString("cep"));
                    map.put("telefone", rs.getString("telefone"));
                    map.put("email", rs.getString("email"));
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

        EditText et_razao_social = new EditText(this);
        et_razao_social.setHint("Razao Social");
        et_razao_social.setTextColor(0xFFFFFFFF);
        et_razao_social.setHintTextColor(0xFF90A4AE);
        et_razao_social.setBackgroundResource(R.drawable.input_bg);
        et_razao_social.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_razao_social = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_razao_social.setMargins(0, 8, 0, 8);
        et_razao_social.setLayoutParams(lp_razao_social);
        if (record != null && record.get("razao_social") != null) et_razao_social.setText(safeStr(record.get("razao_social")));
        container.addView(et_razao_social);

        EditText et_nome_fantasia = new EditText(this);
        et_nome_fantasia.setHint("Nome Fantasia");
        et_nome_fantasia.setTextColor(0xFFFFFFFF);
        et_nome_fantasia.setHintTextColor(0xFF90A4AE);
        et_nome_fantasia.setBackgroundResource(R.drawable.input_bg);
        et_nome_fantasia.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_nome_fantasia = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_nome_fantasia.setMargins(0, 8, 0, 8);
        et_nome_fantasia.setLayoutParams(lp_nome_fantasia);
        if (record != null && record.get("nome_fantasia") != null) et_nome_fantasia.setText(safeStr(record.get("nome_fantasia")));
        container.addView(et_nome_fantasia);

        EditText et_cnpj = new EditText(this);
        et_cnpj.setHint("CNPJ");
        et_cnpj.setTextColor(0xFFFFFFFF);
        et_cnpj.setHintTextColor(0xFF90A4AE);
        et_cnpj.setBackgroundResource(R.drawable.input_bg);
        et_cnpj.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_cnpj = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_cnpj.setMargins(0, 8, 0, 8);
        et_cnpj.setLayoutParams(lp_cnpj);
        if (record != null && record.get("cnpj") != null) et_cnpj.setText(safeStr(record.get("cnpj")));
        container.addView(et_cnpj);

        EditText et_ie = new EditText(this);
        et_ie.setHint("IE");
        et_ie.setTextColor(0xFFFFFFFF);
        et_ie.setHintTextColor(0xFF90A4AE);
        et_ie.setBackgroundResource(R.drawable.input_bg);
        et_ie.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_ie = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_ie.setMargins(0, 8, 0, 8);
        et_ie.setLayoutParams(lp_ie);
        if (record != null && record.get("ie") != null) et_ie.setText(safeStr(record.get("ie")));
        container.addView(et_ie);

        EditText et_endereco = new EditText(this);
        et_endereco.setHint("Endereco");
        et_endereco.setTextColor(0xFFFFFFFF);
        et_endereco.setHintTextColor(0xFF90A4AE);
        et_endereco.setBackgroundResource(R.drawable.input_bg);
        et_endereco.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_endereco = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_endereco.setMargins(0, 8, 0, 8);
        et_endereco.setLayoutParams(lp_endereco);
        if (record != null && record.get("endereco") != null) et_endereco.setText(safeStr(record.get("endereco")));
        container.addView(et_endereco);

        EditText et_numero = new EditText(this);
        et_numero.setHint("Numero");
        et_numero.setTextColor(0xFFFFFFFF);
        et_numero.setHintTextColor(0xFF90A4AE);
        et_numero.setBackgroundResource(R.drawable.input_bg);
        et_numero.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_numero = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_numero.setMargins(0, 8, 0, 8);
        et_numero.setLayoutParams(lp_numero);
        if (record != null && record.get("numero") != null) et_numero.setText(safeStr(record.get("numero")));
        container.addView(et_numero);

        EditText et_bairro = new EditText(this);
        et_bairro.setHint("Bairro");
        et_bairro.setTextColor(0xFFFFFFFF);
        et_bairro.setHintTextColor(0xFF90A4AE);
        et_bairro.setBackgroundResource(R.drawable.input_bg);
        et_bairro.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_bairro = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_bairro.setMargins(0, 8, 0, 8);
        et_bairro.setLayoutParams(lp_bairro);
        if (record != null && record.get("bairro") != null) et_bairro.setText(safeStr(record.get("bairro")));
        container.addView(et_bairro);

        EditText et_cidade = new EditText(this);
        et_cidade.setHint("Cidade");
        et_cidade.setTextColor(0xFFFFFFFF);
        et_cidade.setHintTextColor(0xFF90A4AE);
        et_cidade.setBackgroundResource(R.drawable.input_bg);
        et_cidade.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_cidade = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_cidade.setMargins(0, 8, 0, 8);
        et_cidade.setLayoutParams(lp_cidade);
        if (record != null && record.get("cidade") != null) et_cidade.setText(safeStr(record.get("cidade")));
        container.addView(et_cidade);

        EditText et_uf = new EditText(this);
        et_uf.setHint("UF");
        et_uf.setTextColor(0xFFFFFFFF);
        et_uf.setHintTextColor(0xFF90A4AE);
        et_uf.setBackgroundResource(R.drawable.input_bg);
        et_uf.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_uf = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_uf.setMargins(0, 8, 0, 8);
        et_uf.setLayoutParams(lp_uf);
        if (record != null && record.get("uf") != null) et_uf.setText(safeStr(record.get("uf")));
        container.addView(et_uf);

        EditText et_cep = new EditText(this);
        et_cep.setHint("CEP");
        et_cep.setTextColor(0xFFFFFFFF);
        et_cep.setHintTextColor(0xFF90A4AE);
        et_cep.setBackgroundResource(R.drawable.input_bg);
        et_cep.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_cep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_cep.setMargins(0, 8, 0, 8);
        et_cep.setLayoutParams(lp_cep);
        if (record != null && record.get("cep") != null) et_cep.setText(safeStr(record.get("cep")));
        container.addView(et_cep);

        EditText et_telefone = new EditText(this);
        et_telefone.setHint("Telefone");
        et_telefone.setTextColor(0xFFFFFFFF);
        et_telefone.setHintTextColor(0xFF90A4AE);
        et_telefone.setBackgroundResource(R.drawable.input_bg);
        et_telefone.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_telefone = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_telefone.setMargins(0, 8, 0, 8);
        et_telefone.setLayoutParams(lp_telefone);
        if (record != null && record.get("telefone") != null) et_telefone.setText(safeStr(record.get("telefone")));
        container.addView(et_telefone);

        EditText et_email = new EditText(this);
        et_email.setHint("E-mail");
        et_email.setTextColor(0xFFFFFFFF);
        et_email.setHintTextColor(0xFF90A4AE);
        et_email.setBackgroundResource(R.drawable.input_bg);
        et_email.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_email = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_email.setMargins(0, 8, 0, 8);
        et_email.setLayoutParams(lp_email);
        if (record != null && record.get("email") != null) et_email.setText(safeStr(record.get("email")));
        container.addView(et_email);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" : "Editar")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(record != null ? ((Number) record.get("id")).intValue() : 0, et_razao_social.getText().toString().trim(), et_nome_fantasia.getText().toString().trim(), et_cnpj.getText().toString().trim(), et_ie.getText().toString().trim(), et_endereco.getText().toString().trim(), et_numero.getText().toString().trim(), et_bairro.getText().toString().trim(), et_cidade.getText().toString().trim(), et_uf.getText().toString().trim(), et_cep.getText().toString().trim(), et_telefone.getText().toString().trim(), et_email.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_razao_social, String val_nome_fantasia, String val_cnpj, String val_ie, String val_endereco, String val_numero, String val_bairro, String val_cidade, String val_uf, String val_cep, String val_telefone, String val_email) {
        if (val_razao_social.isEmpty()) { showError("Por favor, preencha o campo Razao Social antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO empresa (razao_social,nome_fantasia,cnpj,ie,endereco,numero,bairro,cidade,uf,cep,telefone,email) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
                } else {
                    ps = conn.prepareStatement("UPDATE empresa SET razao_social=?,nome_fantasia=?,cnpj=?,ie=?,endereco=?,numero=?,bairro=?,cidade=?,uf=?,cep=?,telefone=?,email=? WHERE id=?");
                }
                ps.setString(1, val_razao_social);
                ps.setString(2, val_nome_fantasia);
                ps.setString(3, val_cnpj);
                ps.setString(4, val_ie);
                ps.setString(5, val_endereco);
                ps.setString(6, val_numero);
                ps.setString(7, val_bairro);
                ps.setString(8, val_cidade);
                ps.setString(9, val_uf);
                ps.setString(10, val_cep);
                ps.setString(11, val_telefone);
                ps.setString(12, val_email);
                if (id > 0) ps.setInt(13, id);
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

    private void deleteRecord(int id) {
        showLoading("Excluindo...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM empresa WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Excluido!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
