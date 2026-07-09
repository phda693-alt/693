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

public class CadastroTipoProdutoActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.TIPOS_PRODUTO_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Tipos de Produto");

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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.TIPOS_PRODUTO_CRIAR)) {
                    btnDuplicar.setVisibility(View.VISIBLE);
                    btnDuplicar.setOnClickListener(v -> {
                        Map<String, Object> duplicate = new LinkedHashMap<>(item);
                        duplicate.put("origem_id", item.get("id"));
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
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.TIPOS_PRODUTO_EDITAR)) {
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
                    showConfirm("Inativar", "Deseja inativar este tipo de produto?",
                            () -> inativarRecord(((Number) item.get("id")).intValue()));
                });
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo baseado em permissao
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnNovo), PermissionConstants.TIPOS_PRODUTO_CRIAR);
        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.TIPOS_PRODUTO_CRIAR)) {
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
                String sql = "SELECT * FROM tipos_produto";
                String busca = etBusca.getText().toString().trim();
                sql += " WHERE ativo = 1";
                sql += " ORDER BY id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    int tipoId = rs.getInt("id");
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", tipoId);
                    map.put("line1", rs.getString("descricao"));
                    map.put("descricao", rs.getString("descricao"));

                    // Carregar adicionais vinculados a este tipo
                    String adicionaisStr = carregarAdicionaisDoTipo(conn, tipoId);
                    map.put("line2", adicionaisStr.isEmpty() ? "Sem adicionais" : "Adicionais: " + adicionaisStr);
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

    private String carregarAdicionaisDoTipo(Connection conn, int tipoId) {
        StringBuilder sb = new StringBuilder();
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT a.descricao, a.preco FROM tipo_produto_adicionais tpa " +
                "INNER JOIN adicionais a ON tpa.adicional_id = a.id " +
                "WHERE tpa.tipo_produto_id = ? AND a.ativo = 1 ORDER BY a.descricao");
            ps.setInt(1, tipoId);
            ResultSet rs = ps.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(", ");
                sb.append(rs.getString("descricao"));
                double preco = rs.getDouble("preco");
                if (preco > 0) {
                    sb.append(" (R$ ").append(FormatUtils.formatMoney(preco)).append(")");
                }
                first = false;
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            // Ignora erro silenciosamente
        }
        return sb.toString();
    }

    private void showEditDialog(Map<String, Object> record) {
        showLoading("Carregando adicionais...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Carregar todos os adicionais ativos
                List<Map<String, Object>> todosAdicionais = new ArrayList<>();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, descricao, preco FROM adicionais WHERE ativo = 1 ORDER BY descricao");
                while (rs.next()) {
                    Map<String, Object> ad = new LinkedHashMap<>();
                    ad.put("id", rs.getInt("id"));
                    ad.put("descricao", rs.getString("descricao"));
                    ad.put("preco", rs.getDouble("preco"));
                    todosAdicionais.add(ad);
                }
                rs.close();
                stmt.close();

                // Carregar adicionais ja vinculados a este tipo
                List<Integer> adicionaisVinculados = new ArrayList<>();
                if (record != null) {
                    int tipoId = getRecordId(record, "id");
                    if (tipoId == 0) tipoId = getRecordId(record, "origem_id");
                    PreparedStatement ps = conn.prepareStatement(
                        "SELECT adicional_id FROM tipo_produto_adicionais WHERE tipo_produto_id = ?");
                    ps.setInt(1, tipoId);
                    ResultSet rs2 = ps.executeQuery();
                    while (rs2.next()) {
                        adicionaisVinculados.add(rs2.getInt("adicional_id"));
                    }
                    rs2.close();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> showEditDialogUI(record, todosAdicionais, adicionaisVinculados));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showEditDialogUI(Map<String, Object> record, List<Map<String, Object>> todosAdicionais, List<Integer> adicionaisVinculados) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        // Campo descricao
        EditText et_descricao = new EditText(this);
        et_descricao.setHint("Descricao");
        et_descricao.setTextColor(0xFFFFFFFF);
        et_descricao.setHintTextColor(0xFF90A4AE);
        et_descricao.setBackgroundResource(R.drawable.input_bg);
        et_descricao.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp_descricao = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp_descricao.setMargins(0, 8, 0, 16);
        et_descricao.setLayoutParams(lp_descricao);
        if (record != null && record.get("descricao") != null) et_descricao.setText(safeStr(record.get("descricao")));
        container.addView(et_descricao);

        // Titulo da secao de adicionais
        TextView tvAdicionaisTitle = new TextView(this);
        tvAdicionaisTitle.setText("Adicionais vinculados:");
        tvAdicionaisTitle.setTextColor(0xFF00BCD4);
        tvAdicionaisTitle.setTextSize(14);
        tvAdicionaisTitle.setPadding(0, 16, 0, 8);
        container.addView(tvAdicionaisTitle);

        // Lista de adicionais ja vinculados (exibicao)
        final LinearLayout listaAdicionais = new LinearLayout(this);
        listaAdicionais.setOrientation(LinearLayout.VERTICAL);
        container.addView(listaAdicionais);

        // Lista interna para rastrear IDs dos adicionais selecionados
        final List<Integer> selecionados = new ArrayList<>(adicionaisVinculados);

        // Atualizar lista visual
        atualizarListaAdicionaisUI(listaAdicionais, todosAdicionais, selecionados);

        // Dropdown para adicionar novo adicional
        if (!todosAdicionais.isEmpty()) {
            TextView tvAdicionar = new TextView(this);
            tvAdicionar.setText("Adicionar adicional:");
            tvAdicionar.setTextColor(0xFFB0BEC5);
            tvAdicionar.setTextSize(12);
            tvAdicionar.setPadding(0, 16, 0, 4);
            container.addView(tvAdicionar);

            Spinner spinnerAdicionais = new Spinner(this);
            spinnerAdicionais.setBackgroundResource(R.drawable.input_bg);
            spinnerAdicionais.setPadding(32, 16, 32, 16);
            LinearLayout.LayoutParams lpSpinner = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpSpinner.setMargins(0, 4, 0, 8);
            spinnerAdicionais.setLayoutParams(lpSpinner);

            // Montar lista do spinner
            List<String> nomes = new ArrayList<>();
            nomes.add("-- Selecione um adicional --");
            for (Map<String, Object> ad : todosAdicionais) {
                String nome = safeStr(ad.get("descricao"));
                double preco = ((Number) ad.get("preco")).doubleValue();
                if (preco > 0) {
                    nome += " (R$ " + FormatUtils.formatMoney(preco) + ")";
                }
                nomes.add(nome);
            }
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, nomes);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAdicionais.setAdapter(spinnerAdapter);
            container.addView(spinnerAdicionais);

            Button btnAdicionar = new Button(this);
            btnAdicionar.setText("+ Adicionar");
            btnAdicionar.setTextColor(0xFF000000);
            btnAdicionar.setBackgroundColor(0xFF00E676);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBtn.setMargins(0, 4, 0, 8);
            btnAdicionar.setLayoutParams(lpBtn);
            btnAdicionar.setOnClickListener(v -> {
                int pos = spinnerAdicionais.getSelectedItemPosition();
                if (pos <= 0) {
                    showToast("Selecione um adicional");
                    return;
                }
                Map<String, Object> adSelecionado = todosAdicionais.get(pos - 1);
                int adId = ((Number) adSelecionado.get("id")).intValue();
                if (selecionados.contains(adId)) {
                    showToast("Este adicional ja foi adicionado");
                    return;
                }
                selecionados.add(adId);
                atualizarListaAdicionaisUI(listaAdicionais, todosAdicionais, selecionados);
                spinnerAdicionais.setSelection(0);
            });
            container.addView(btnAdicionar);
        } else {
            TextView tvSemAdicionais = new TextView(this);
            tvSemAdicionais.setText("Nenhum adicional cadastrado.\nCadastre adicionais no botao 'Adicionais' do menu principal.");
            tvSemAdicionais.setTextColor(0xFFFF5252);
            tvSemAdicionais.setTextSize(12);
            tvSemAdicionais.setPadding(0, 8, 0, 8);
            container.addView(tvSemAdicionais);
        }

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo" :
                        (getRecordId(record, "id") == 0 ? "Duplicar" : "Editar"))
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveRecord(getRecordId(record, "id"),
                            et_descricao.getText().toString().trim(), selecionados);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void atualizarListaAdicionaisUI(LinearLayout listaAdicionais, List<Map<String, Object>> todosAdicionais, List<Integer> selecionados) {
        listaAdicionais.removeAllViews();

        if (selecionados.isEmpty()) {
            TextView tvVazio = new TextView(this);
            tvVazio.setText("Nenhum adicional vinculado");
            tvVazio.setTextColor(0xFF90A4AE);
            tvVazio.setTextSize(12);
            tvVazio.setPadding(8, 4, 8, 4);
            listaAdicionais.addView(tvVazio);
            return;
        }

        for (int adId : new ArrayList<>(selecionados)) {
            // Encontrar dados do adicional
            String nomeAd = "Adicional #" + adId;
            double precoAd = 0;
            for (Map<String, Object> ad : todosAdicionais) {
                if (((Number) ad.get("id")).intValue() == adId) {
                    nomeAd = safeStr(ad.get("descricao"));
                    precoAd = ((Number) ad.get("preco")).doubleValue();
                    break;
                }
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(8, 6, 8, 6);
            row.setBackgroundColor(0x221A237E);

            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpRow.setMargins(0, 2, 0, 2);
            row.setLayoutParams(lpRow);

            TextView tvNome = new TextView(this);
            String textoAd = nomeAd;
            if (precoAd > 0) textoAd += " - R$ " + FormatUtils.formatMoney(precoAd);
            tvNome.setText(textoAd);
            tvNome.setTextColor(0xFFFFFFFF);
            tvNome.setTextSize(13);
            LinearLayout.LayoutParams lpNome = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            tvNome.setLayoutParams(lpNome);
            row.addView(tvNome);

            Button btnRemover = new Button(this);
            btnRemover.setText("X");
            btnRemover.setTextColor(0xFFFFFFFF);
            btnRemover.setBackgroundColor(0xFFFF5252);
            btnRemover.setTextSize(11);
            btnRemover.setPadding(16, 4, 16, 4);
            LinearLayout.LayoutParams lpRemover = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpRemover.setMargins(8, 0, 0, 0);
            btnRemover.setLayoutParams(lpRemover);
            final int removeId = adId;
            btnRemover.setOnClickListener(v -> {
                selecionados.remove(Integer.valueOf(removeId));
                atualizarListaAdicionaisUI(listaAdicionais, todosAdicionais, selecionados);
            });
            row.addView(btnRemover);

            listaAdicionais.addView(row);
        }
    }

    private void saveRecord(int id, String val_descricao, List<Integer> adicionaisIds) {
        if (val_descricao.isEmpty()) { showError("Por favor, preencha o campo Descricao antes de salvar."); return; }
        showLoading("Salvando...");
        new Thread(() -> {
            Connection conn = null;
            boolean oldAutoCommit = true;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                conn = db.getConnection();
                oldAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                PreparedStatement ps;
                int tipoId = id;

                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO tipos_produto (descricao,ativo) VALUES (?,1)", Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, val_descricao);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    if (keys.next()) tipoId = keys.getInt(1);
                    keys.close();
                    ps.close();
                    if (tipoId <= 0) throw new SQLException("Nao foi possivel obter o ID da categoria duplicada.");
                } else {
                    ps = conn.prepareStatement("UPDATE tipos_produto SET descricao=? WHERE id=?");
                    ps.setString(1, val_descricao);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                    ps.close();
                }

                // Atualizar adicionais vinculados
                // Primeiro remove todos os existentes
                ps = conn.prepareStatement("DELETE FROM tipo_produto_adicionais WHERE tipo_produto_id = ?");
                ps.setInt(1, tipoId);
                ps.executeUpdate();
                ps.close();

                // Depois insere os novos
                for (int adId : adicionaisIds) {
                    ps = conn.prepareStatement("INSERT INTO tipo_produto_adicionais (tipo_produto_id, adicional_id) VALUES (?, ?)");
                    ps.setInt(1, tipoId);
                    ps.setInt(2, adId);
                    ps.executeUpdate();
                    ps.close();
                }

                conn.commit();

                hideLoading();
                showToast("Salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private int getRecordId(Map<String, Object> record, String key) {
        if (record == null) return 0;
        Object value = record.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try { return value != null ? Integer.parseInt(value.toString()) : 0; }
        catch (Exception ignored) { return 0; }
    }

    private void inativarRecord(int id) {
        showLoading("Inativando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE tipos_produto SET ativo = 0 WHERE id = ?");
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
