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

public class CadastroClienteActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CLIENTES_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Clientes");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.CLIENTES_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar este cliente?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.CLIENTES_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.CLIENTES_CRIAR)) {
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
                String sql = "SELECT * FROM clientes";
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
                    map.put("line2", "CPF: " + safeStr(rs.getString("cpf_cnpj")) + " | Cel: " + safeStr(rs.getString("celular")));
                    map.put("nome", rs.getString("nome"));
                    map.put("cpf_cnpj", rs.getString("cpf_cnpj"));
                    map.put("celular", rs.getString("celular"));
                    map.put("email", rs.getString("email"));
                    map.put("endereco", rs.getString("endereco"));
                    map.put("numero", rs.getString("numero"));
                    map.put("bairro", rs.getString("bairro"));
                    map.put("cidade", rs.getString("cidade"));
                    map.put("uf", rs.getString("uf"));
                    map.put("cep", rs.getString("cep"));
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

    /**
     * Carrega a lista de bairros cadastrados na tabela taxa_entrega_bairro (ativos).
     * Retorna a lista de nomes de bairros para uso no Spinner.
     */
    private List<String> carregarBairrosCadastrados() {
        List<String> bairros = new ArrayList<>();
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT bairro FROM taxa_entrega_bairro WHERE ativo = 1 ORDER BY bairro");
            while (rs.next()) {
                String bairro = rs.getString("bairro");
                if (bairro != null && !bairro.trim().isEmpty()) {
                    bairros.add(bairro.trim());
                }
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            android.util.Log.e("CadastroCliente", "Erro ao carregar bairros: " + e.getMessage());
        }
        return bairros;
    }

    private void showEditDialog(Map<String, Object> record) {
        // Carregar bairros em background antes de montar o dialog
        showLoading("Carregando...");
        new Thread(() -> {
            List<String> bairrosCadastrados = carregarBairrosCadastrados();
            hideLoading();
            runOnUiThread(() -> montarDialogCliente(record, bairrosCadastrados));
        }).start();
    }

    /**
     * Monta o dialog de cadastro/edicao de cliente com Spinner de bairros.
     */
    private void montarDialogCliente(Map<String, Object> record, List<String> bairrosCadastrados) {
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

        EditText et_cpf_cnpj = new EditText(this);
        et_cpf_cnpj.setHint("CPF/CNPJ");
        et_cpf_cnpj.setTextColor(0xFFFFFFFF);
        et_cpf_cnpj.setHintTextColor(0xFF90A4AE);
        et_cpf_cnpj.setBackgroundResource(R.drawable.input_bg);
        et_cpf_cnpj.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_cpf_cnpj = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_cpf_cnpj.setMargins(0, 8, 0, 8);
        et_cpf_cnpj.setLayoutParams(lp_cpf_cnpj);
        if (record != null && record.get("cpf_cnpj") != null) et_cpf_cnpj.setText(safeStr(record.get("cpf_cnpj")));
        container.addView(et_cpf_cnpj);

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

        // =====================================================================
        // SPINNER DE BAIRROS - Substitui o EditText por um Spinner com os
        // bairros ja cadastrados na tabela taxa_entrega_bairro
        // =====================================================================

        // Label "Bairro"
        TextView tvBairroLabel = new TextView(this);
        tvBairroLabel.setText("Bairro");
        tvBairroLabel.setTextColor(0xFF90A4AE);
        tvBairroLabel.setTextSize(13);
        LinearLayout.LayoutParams lp_label = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_label.setMargins(8, 16, 0, 0);
        tvBairroLabel.setLayoutParams(lp_label);
        container.addView(tvBairroLabel);

        // Montar lista do Spinner: primeiro item vazio ("Selecione o bairro") + bairros cadastrados
        List<String> opcoesSpinner = new ArrayList<>();
        opcoesSpinner.add("Selecione o bairro");
        opcoesSpinner.addAll(bairrosCadastrados);

        Spinner spinnerBairro = new Spinner(this);
        spinnerBairro.setBackgroundResource(R.drawable.input_bg);
        spinnerBairro.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_spinner = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_spinner.setMargins(0, 8, 0, 8);
        spinnerBairro.setLayoutParams(lp_spinner);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, opcoesSpinner) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(16);
                tv.setPadding(8, 16, 8, 16);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(0xFF000000);
                tv.setTextSize(16);
                tv.setPadding(32, 24, 32, 24);
                tv.setBackgroundColor(0xFFFFFFFF);
                return view;
            }
        };
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBairro.setAdapter(spinnerAdapter);

        // Se estiver editando, selecionar o bairro atual do cliente
        if (record != null && record.get("bairro") != null) {
            String bairroAtual = safeStr(record.get("bairro")).trim();
            if (!bairroAtual.isEmpty()) {
                int pos = opcoesSpinner.indexOf(bairroAtual);
                if (pos >= 0) {
                    spinnerBairro.setSelection(pos);
                } else {
                    // Bairro do cliente nao esta na lista de cadastrados
                    // Adicionar temporariamente para nao perder o dado
                    opcoesSpinner.add(bairroAtual);
                    spinnerAdapter.notifyDataSetChanged();
                    spinnerBairro.setSelection(opcoesSpinner.size() - 1);
                }
            }
        }

        container.addView(spinnerBairro);

        // Mensagem informativa se nao houver bairros cadastrados
        if (bairrosCadastrados.isEmpty()) {
            TextView tvAviso = new TextView(this);
            tvAviso.setText("Nenhum bairro cadastrado. Cadastre bairros em Taxa Entrega por Bairro.");
            tvAviso.setTextColor(0xFFFF9800);
            tvAviso.setTextSize(12);
            LinearLayout.LayoutParams lp_aviso = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp_aviso.setMargins(8, 4, 0, 8);
            tvAviso.setLayoutParams(lp_aviso);
            container.addView(tvAviso);
        }

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

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" : "Editar")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    // Obter bairro selecionado no Spinner
                    String bairroSelecionado = "";
                    int spinnerPos = spinnerBairro.getSelectedItemPosition();
                    if (spinnerPos > 0) {
                        bairroSelecionado = opcoesSpinner.get(spinnerPos);
                    }

                    saveRecord(
                        record != null ? ((Number) record.get("id")).intValue() : 0,
                        et_nome.getText().toString().trim(),
                        et_cpf_cnpj.getText().toString().trim(),
                        et_celular.getText().toString().trim(),
                        et_email.getText().toString().trim(),
                        et_endereco.getText().toString().trim(),
                        et_numero.getText().toString().trim(),
                        bairroSelecionado,
                        et_cidade.getText().toString().trim(),
                        et_uf.getText().toString().trim(),
                        et_cep.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_nome, String val_cpf_cnpj, String val_celular, String val_email, String val_endereco, String val_numero, String val_bairro, String val_cidade, String val_uf, String val_cep) {
        if (val_nome.isEmpty()) { showError("Por favor, preencha o campo Nome antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO clientes (nome,cpf_cnpj,celular,email,endereco,numero,bairro,cidade,uf,cep,ativo) VALUES (?,?,?,?,?,?,?,?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE clientes SET nome=?,cpf_cnpj=?,celular=?,email=?,endereco=?,numero=?,bairro=?,cidade=?,uf=?,cep=? WHERE id=?");
                }
                ps.setString(1, val_nome);
                ps.setString(2, val_cpf_cnpj);
                ps.setString(3, val_celular);
                ps.setString(4, val_email);
                ps.setString(5, val_endereco);
                ps.setString(6, val_numero);
                ps.setString(7, val_bairro);
                ps.setString(8, val_cidade);
                ps.setString(9, val_uf);
                ps.setString(10, val_cep);
                if (id > 0) ps.setInt(11, id);
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
                PreparedStatement ps = conn.prepareStatement("UPDATE clientes SET ativo = 0 WHERE id = ?");
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
