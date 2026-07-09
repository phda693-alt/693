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
 * Tela de cadastro e gerenciamento de mesas.
 * Permite criar, editar e inativar mesas do sistema.
 */
public class CadastroMesaActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.MESAS_ACESSAR)) {
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Cadastro de Mesas");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.MESAS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            // Botao Inativar
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                boolean podeExcluir = PermissionHelper.verificarSilencioso(this, PermissionConstants.MESAS_EXCLUIR);
                btnInativar.setVisibility(podeExcluir ? View.VISIBLE : View.GONE);
                if (podeExcluir) btnInativar.setOnClickListener(v ->
                        showConfirm("Inativar", "Deseja inativar esta mesa?",
                                () -> inativarRecord(((Number) item.get("id")).intValue())));
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.MESAS_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.MESAS_CRIAR)) {
                showEditDialog(null);
            }
        });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });
        addAssistenteButton();

        loadData();
    }

    @Override
    protected void onResume() { super.onResume(); loadData(); }

    private String safeStr(Object o) { return o != null ? o.toString() : ""; }

    private void addAssistenteButton() {
        Button btnNovo = findViewById(R.id.btnNovo);
        if (btnNovo == null) return;
        LinearLayout parent = (LinearLayout) btnNovo.getParent();
        Button assistente = new Button(this);
        assistente.setText("ASSISTENTE");
        assistente.setTextSize(11);
        assistente.setTextColor(getResources().getColor(R.color.dark_bg));
        assistente.setBackgroundResource(R.drawable.btn_gold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int) (40 * getResources().getDisplayMetrics().density));
        lp.setMargins(8, 0, 0, 0);
        assistente.setLayoutParams(lp);
        assistente.setVisibility(PermissionHelper.verificarSilencioso(this, PermissionConstants.MESAS_CRIAR)
                ? View.VISIBLE : View.GONE);
        assistente.setOnClickListener(v -> showAssistenteMesas());
        parent.addView(assistente);
    }

    private EditText novoCampo(String hint, boolean numerico) {
        EditText campo = new EditText(this);
        campo.setHint(hint);
        campo.setTextColor(0xFFFFFFFF);
        campo.setHintTextColor(0xFF90A4AE);
        campo.setBackgroundResource(R.drawable.input_bg);
        campo.setPadding(32, 24, 32, 24);
        if (numerico) campo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 8);
        campo.setLayoutParams(lp);
        return campo;
    }

    private void showAssistenteMesas() {
        if (!PermissionHelper.verificar(this, PermissionConstants.MESAS_CRIAR)) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = view.findViewById(R.id.formContainer);
        TextView info = new TextView(this);
        info.setText("Crie uma sequencia de mesas de uma vez. Numeros ja cadastrados serao ignorados.");
        info.setTextColor(0xFFB0BEC5);
        info.setPadding(8, 8, 8, 16);
        container.addView(info);

        Spinner porte = new Spinner(this);
        String[] opcoes = {"Pequena - 5 mesas", "Media - 20 mesas", "Grande - 50 mesas", "Personalizada"};
        porte.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcoes));
        container.addView(porte);
        EditText inicio = novoCampo("Numero inicial", true);
        inicio.setText("1");
        EditText quantidade = novoCampo("Quantidade", true);
        quantidade.setText("5");
        EditText capacidade = novoCampo("Capacidade por mesa", true);
        capacidade.setText("4");
        EditText descricao = novoCampo("Descricao padrao (opcional)", false);
        container.addView(inicio);
        container.addView(quantidade);
        container.addView(capacidade);
        container.addView(descricao);
        porte.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) quantidade.setText("5");
                else if (pos == 1) quantidade.setText("20");
                else if (pos == 2) quantidade.setText("50");
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        new AlertDialog.Builder(this).setTitle("Assistente de Criacao de Mesas")
                .setView(view).setPositiveButton("Criar", (d, w) -> {
                    try {
                        int primeiro = Integer.parseInt(inicio.getText().toString().trim());
                        int qtd = Integer.parseInt(quantidade.getText().toString().trim());
                        int cap = Integer.parseInt(capacidade.getText().toString().trim());
                        if (primeiro <= 0 || qtd <= 0 || qtd > 500 || cap <= 0) {
                            showError("Use numeros positivos. O limite e de 500 mesas por vez.");
                            return;
                        }
                        criarMesasEmLote(primeiro, qtd, cap, descricao.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        showError("Preencha numero inicial, quantidade e capacidade corretamente.");
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void criarMesasEmLote(int inicio, int quantidade, int capacidade, String descricao) {
        showLoading("Criando mesas...");
        new Thread(() -> {
            int criadas = 0;
            int ignoradas = 0;
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement check = conn.prepareStatement("SELECT COUNT(*) FROM mesas WHERE numero=? AND ativa=1");
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO mesas (numero,descricao,capacidade,ativa) VALUES (?,?,?,1)");
                for (int i = 0; i < quantidade; i++) {
                    int numero = inicio + i;
                    check.setInt(1, numero);
                    ResultSet rs = check.executeQuery();
                    boolean existe = rs.next() && rs.getInt(1) > 0;
                    rs.close();
                    if (existe) { ignoradas++; continue; }
                    insert.setInt(1, numero);
                    insert.setString(2, descricao.isEmpty() ? null : descricao);
                    insert.setInt(3, capacidade);
                    insert.addBatch();
                    criadas++;
                }
                insert.executeBatch();
                check.close();
                insert.close();
                final int ok = criadas, dup = ignoradas;
                hideLoading();
                runOnUiThread(() -> {
                    showSuccess(ok + " mesas criadas" + (dup > 0 ? " e " + dup + " ja existentes ignoradas." : "."));
                    loadData();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void loadData() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String sql = "SELECT * FROM mesas WHERE ativa = 1 ORDER BY numero ASC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("line1", "Mesa " + rs.getInt("numero"));
                    String desc = rs.getString("descricao");
                    map.put("line2", "Capacidade: " + rs.getInt("capacidade") + " pessoas"
                            + (desc != null && !desc.isEmpty() ? " | " + desc : ""));
                    map.put("numero", rs.getInt("numero"));
                    map.put("descricao", rs.getString("descricao"));
                    map.put("capacidade", rs.getInt("capacidade"));
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

        EditText et_numero = new EditText(this);
        et_numero.setHint("Numero da Mesa");
        et_numero.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et_numero.setTextColor(0xFFFFFFFF);
        et_numero.setHintTextColor(0xFF90A4AE);
        et_numero.setBackgroundResource(R.drawable.input_bg);
        et_numero.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_numero = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_numero.setMargins(0, 8, 0, 8);
        et_numero.setLayoutParams(lp_numero);
        if (record != null && record.get("numero") != null) et_numero.setText(String.valueOf(record.get("numero")));
        container.addView(et_numero);

        EditText et_descricao = new EditText(this);
        et_descricao.setHint("Descricao (opcional)");
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

        EditText et_capacidade = new EditText(this);
        et_capacidade.setHint("Capacidade (pessoas)");
        et_capacidade.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et_capacidade.setTextColor(0xFFFFFFFF);
        et_capacidade.setHintTextColor(0xFF90A4AE);
        et_capacidade.setBackgroundResource(R.drawable.input_bg);
        et_capacidade.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_capacidade = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_capacidade.setMargins(0, 8, 0, 8);
        et_capacidade.setLayoutParams(lp_capacidade);
        if (record != null && record.get("capacidade") != null) et_capacidade.setText(String.valueOf(record.get("capacidade")));
        container.addView(et_capacidade);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Nova Mesa" : "Editar Mesa")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(
                        record != null ? ((Number) record.get("id")).intValue() : 0,
                        et_numero.getText().toString().trim(),
                        et_descricao.getText().toString().trim(),
                        et_capacidade.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String val_numero, String val_descricao, String val_capacidade) {
        if (val_numero.isEmpty()) {
            showError("Por favor, preencha o numero da mesa antes de salvar.");
            return;
        }
        int numero;
        try {
            numero = Integer.parseInt(val_numero);
        } catch (NumberFormatException e) {
            showError("Numero da mesa invalido.");
            return;
        }
        int capacidade = 4; // padrao
        try {
            if (!val_capacidade.isEmpty()) capacidade = Integer.parseInt(val_capacidade);
        } catch (NumberFormatException e) {
            // usa padrao
        }

        final int finalCapacidade = capacidade;
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO mesas (numero,descricao,capacidade,ativa) VALUES (?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE mesas SET numero=?,descricao=?,capacidade=? WHERE id=?");
                }
                ps.setInt(1, numero);
                ps.setString(2, val_descricao);
                ps.setInt(3, finalCapacidade);
                if (id > 0) ps.setInt(4, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Mesa salva com sucesso!");
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
                PreparedStatement ps = conn.prepareStatement("UPDATE mesas SET ativa = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Mesa inativada com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
