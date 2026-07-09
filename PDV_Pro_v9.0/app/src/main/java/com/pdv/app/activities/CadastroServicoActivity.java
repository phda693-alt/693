package com.pdv.app.activities;

import android.os.Bundle;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela de cadastro e gerenciamento de servicos.
 *
 * Permite criar, editar e inativar servicos oferecidos pelo estabelecimento.
 * Campos principais:
 * - nome
 * - descricao
 * - valor
 *
 * v8.0.0 - Modulo de Cadastro de Servicos
 */
public class CadastroServicoActivity extends BaseActivity {

    private static final String TAG = "CadastroServico";

    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;
    private TextView tvTitle;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private boolean dadosCarregados = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.SERVICOS_ACESSAR)) {
            return;
        }

        setContentView(R.layout.activity_cadastro_lista);

        tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Cadastro de Servicos");

        etBusca = findViewById(R.id.etBusca);
        etBusca.setHint("Buscar por nome ou descricao...");

        recyclerView = findViewById(R.id.recyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, safeStr(item.get("line1")));
            holder.setText(R.id.tvLine2, safeStr(item.get("line2")));

            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.SERVICOS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.SERVICOS_EXCLUIR)) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setOnClickListener(v ->
                        showConfirm("Inativar", "Deseja inativar este servico?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()))
                    );
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }
        });

        recyclerView.setAdapter(adapter);

        Button btnNovo = findViewById(R.id.btnNovo);
        if (btnNovo != null) {
            if (PermissionHelper.verificarSilencioso(this, PermissionConstants.SERVICOS_CRIAR)) {
                btnNovo.setVisibility(View.VISIBLE);
                btnNovo.setOnClickListener(v -> showEditDialog(null));
            } else {
                btnNovo.setVisibility(View.GONE);
            }
        }

        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });

        ensureTableAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dadosCarregados) {
            loadData();
        }
    }

    /**
     * Carrega os dados do modulo.
     * A tabela 'servicos' e criada/verificada pelo DatabaseHelper na inicializacao do sistema.
     * Nao e necessario criar a tabela aqui.
     */
    private void ensureTableAndLoad() {
        dadosCarregados = true;
        loadData();
    }

    private void loadData() {
        final String busca = etBusca != null ? etBusca.getText().toString().trim() : "";
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (busca != null && !busca.isEmpty()) {
                    ps = conn.prepareStatement(
                            "SELECT id, nome, descricao, valor FROM servicos WHERE ativo = 1 " +
                            "AND (nome LIKE ? OR descricao LIKE ?) ORDER BY nome ASC");
                    String like = "%" + busca + "%";
                    ps.setString(1, like);
                    ps.setString(2, like);
                } else {
                    ps = conn.prepareStatement(
                            "SELECT id, nome, descricao, valor FROM servicos WHERE ativo = 1 ORDER BY nome ASC");
                }

                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> lista = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("nome", rs.getString("nome"));
                    item.put("descricao", rs.getString("descricao") != null ? rs.getString("descricao") : "");
                    item.put("valor", rs.getDouble("valor"));
                    item.put("line1", rs.getString("nome"));
                    String descr = rs.getString("descricao") != null ? rs.getString("descricao") : "";
                    double valor = rs.getDouble("valor");
                    String line2 = (descr.isEmpty() ? "" : descr + " | ") +
                            String.format("Valor: R$ %.2f", valor);
                    item.put("line2", line2);
                    lista.add(item);
                }
                rs.close();
                ps.close();

                final int total = lista.size();
                runOnUiThread(() -> {
                    adapter.setItems(lista);
                    if (tvTitle != null) {
                        tvTitle.setText("Cadastro de Servicos (" + total + ")");
                    }
                });
            } catch (Exception e) {
                if (ps != null) {
                    try { ps.close(); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> showError("Erro ao carregar servicos: " + e.getMessage()));
            }
        });
    }

    private void showEditDialog(Map<String, Object> record) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 24);

        // Campo: Nome
        TextView lblNome = new TextView(this);
        lblNome.setText("Nome *");
        lblNome.setTextColor(0xFFCDDC39);
        container.addView(lblNome);

        EditText etNome = new EditText(this);
        etNome.setHint("Nome do servico");
        etNome.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        etNome.setBackgroundResource(R.drawable.input_bg);
        etNome.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lpNome = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpNome.setMargins(0, 8, 0, 16);
        etNome.setLayoutParams(lpNome);
        if (record != null) etNome.setText(safeStr(record.get("nome")));
        container.addView(etNome);

        // Campo: Descricao
        TextView lblDescricao = new TextView(this);
        lblDescricao.setText("Descricao");
        lblDescricao.setTextColor(0xFFCDDC39);
        container.addView(lblDescricao);

        EditText etDescricao = new EditText(this);
        etDescricao.setHint("Descricao do servico (opcional)");
        etDescricao.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etDescricao.setLines(2);
        etDescricao.setMaxLines(4);
        etDescricao.setBackgroundResource(R.drawable.input_bg);
        etDescricao.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lpDescricao = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpDescricao.setMargins(0, 8, 0, 16);
        etDescricao.setLayoutParams(lpDescricao);
        if (record != null && record.get("descricao") != null) {
            etDescricao.setText(safeStr(record.get("descricao")));
        }
        container.addView(etDescricao);

        // Campo: Valor
        TextView lblValor = new TextView(this);
        lblValor.setText("Valor (R$)");
        lblValor.setTextColor(0xFFCDDC39);
        container.addView(lblValor);

        EditText etValor = new EditText(this);
        etValor.setHint("0,00");
        etValor.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etValor.setBackgroundResource(R.drawable.input_bg);
        etValor.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lpValor = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpValor.setMargins(0, 8, 0, 8);
        etValor.setLayoutParams(lpValor);
        if (record != null && record.get("valor") != null) {
            etValor.setText(String.format("%.2f", ((Number) record.get("valor")).doubleValue()));
        }
        container.addView(etValor);

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo Servico" : "Editar Servico")
                .setView(container)
                .setPositiveButton("Salvar", (d, w) -> {
                    int id = (record != null) ? ((Number) record.get("id")).intValue() : 0;
                    saveRecord(id,
                            etNome.getText().toString().trim(),
                            etDescricao.getText().toString().trim(),
                            etValor.getText().toString().trim());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String nome, String descricao, String valorStr) {
        if (nome.isEmpty()) {
            showError("Por favor, informe o nome do servico.");
            return;
        }

        double valor = 0;
        if (!valorStr.isEmpty()) {
            try {
                valor = Double.parseDouble(valorStr.replace(",", "."));
            } catch (NumberFormatException e) {
                showError("Valor invalido. Use apenas numeros.");
                return;
            }
        }

        final double valorFinal = valor;
        showLoading("Salvando...");
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (id == 0) {
                    PreparedStatement psCheck = conn.prepareStatement(
                            "SELECT COUNT(*) as cnt FROM servicos WHERE nome = ? AND ativo = 1");
                    psCheck.setString(1, nome);
                    ResultSet rsCheck = psCheck.executeQuery();
                    if (rsCheck.next() && rsCheck.getInt("cnt") > 0) {
                        rsCheck.close();
                        psCheck.close();
                        hideLoading();
                        runOnUiThread(() -> showError("Ja existe um servico ativo com o nome \"" + nome + "\"."));
                        return;
                    }
                    rsCheck.close();
                    psCheck.close();

                    ps = conn.prepareStatement(
                            "INSERT INTO servicos (nome, descricao, valor, ativo) VALUES (?, ?, ?, 1)");
                } else {
                    ps = conn.prepareStatement(
                            "UPDATE servicos SET nome = ?, descricao = ?, valor = ?, " +
                            "data_atualizacao = CURRENT_TIMESTAMP WHERE id = ?");
                }

                ps.setString(1, nome);
                ps.setString(2, descricao.isEmpty() ? null : descricao);
                ps.setDouble(3, valorFinal);
                if (id > 0) ps.setInt(4, id);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Servico salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                if (ps != null) {
                    try { ps.close(); } catch (Exception ignored) {}
                }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        });
    }

    private void inativarRecord(int id) {
        showLoading("Inativando...");
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                ps = conn.prepareStatement("UPDATE servicos SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Servico inativado com sucesso!");
                loadData();
            } catch (Exception e) {
                if (ps != null) {
                    try { ps.close(); } catch (Exception ignored) {}
                }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        });
    }

    private String safeStr(Object o) {
        return o != null ? o.toString() : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }
}
