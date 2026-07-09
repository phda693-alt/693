package com.pdv.app.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.FormatUtils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ContasPagarActivity - Módulo completo de Contas a Pagar.
 * Permite visualizar, criar, pagar e cancelar contas a pagar.
 * Suporta seleção de caixa nominal para débito de pagamento.
 */
public class ContasPagarActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private Spinner spFiltroStatus;
    private TextView tvTotalPendente, tvTotalPago, tvQtdPendentes;
    private Button btnNovaConta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contas_pagar);

        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CONTAS_PAGAR_ACESSAR)) {
            return;
        }

        tvTotalPendente = findViewById(R.id.tvTotalPendente);
        tvTotalPago = findViewById(R.id.tvTotalPago);
        tvQtdPendentes = findViewById(R.id.tvQtdPendentes);
        spFiltroStatus = findViewById(R.id.spFiltroStatus);
        btnNovaConta = findViewById(R.id.btnNovaConta);

        String[] statusOpcoes = {"Todos", "Pendente", "Pago Parcial", "Pago", "Cancelado"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statusOpcoes);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFiltroStatus.setAdapter(statusAdapter);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_conta_pagar, (holder, item, pos) -> {
            String descricao = safeStr(item.get("descricao"));
            String fornecedor = safeStr(item.get("fornecedor_nome"));
            String status = safeStr(item.get("status"));
            double valorTotal = item.get("valor_total") != null ? ((Number) item.get("valor_total")).doubleValue() : 0;
            double valorPendente = item.get("valor_pendente") != null ? ((Number) item.get("valor_pendente")).doubleValue() : 0;
            double valorPago = item.get("valor_pago") != null ? ((Number) item.get("valor_pago")).doubleValue() : 0;
            String vencimento = safeStr(item.get("data_vencimento"));
            int parcela = item.get("parcela_numero") != null ? ((Number) item.get("parcela_numero")).intValue() : 1;
            int totalParcelas = item.get("total_parcelas") != null ? ((Number) item.get("total_parcelas")).intValue() : 1;

            TextView tvDescricao = holder.find(R.id.tvDescricao);
            TextView tvFornecedor = holder.find(R.id.tvFornecedor);
            TextView tvStatus = holder.find(R.id.tvStatus);
            TextView tvValorTotal = holder.find(R.id.tvValorTotal);
            TextView tvValorPendente = holder.find(R.id.tvValorPendente);
            TextView tvVencimento = holder.find(R.id.tvVencimento);
            TextView tvParcela = holder.find(R.id.tvParcela);

            if (tvDescricao != null) tvDescricao.setText(descricao);
            if (tvFornecedor != null) tvFornecedor.setText(fornecedor.isEmpty() ? "Sem fornecedor" : fornecedor);
            if (tvValorTotal != null) tvValorTotal.setText("Total: R$ " + FormatUtils.formatMoney(valorTotal));
            if (tvValorPendente != null) tvValorPendente.setText("Pendente: R$ " + FormatUtils.formatMoney(valorPendente));
            if (tvVencimento != null) tvVencimento.setText("Venc: " + FormatUtils.formatDate(vencimento));
            if (tvParcela != null) {
                if (totalParcelas > 1) {
                    tvParcela.setText("Parcela " + parcela + "/" + totalParcelas);
                    tvParcela.setVisibility(View.VISIBLE);
                } else {
                    tvParcela.setVisibility(View.GONE);
                }
            }

            if (tvStatus != null) {
                tvStatus.setText(status.toUpperCase().replace("_", " "));
                switch (status) {
                    case "pendente":
                        tvStatus.setTextColor(0xFFFF5252);
                        break;
                    case "pago_parcial":
                        tvStatus.setText("PARCIAL");
                        tvStatus.setTextColor(0xFFFFD740);
                        break;
                    case "pago":
                        tvStatus.setTextColor(0xFF00E676);
                        break;
                    case "cancelado":
                        tvStatus.setTextColor(0xFF90A4AE);
                        break;
                    default:
                        tvStatus.setTextColor(0xFFB0BEC5);
                }
            }
        });

        adapter.setOnItemClickListener((item, pos) -> showContaOptions(item));
        recyclerView.setAdapter(adapter);

        PermissionHelper.controlarVisibilidade(this, btnNovaConta, PermissionConstants.CONTAS_PAGAR_CRIAR);
        btnNovaConta.setOnClickListener(v -> showNovaConta());
        findViewById(R.id.btnFiltrar).setOnClickListener(v -> loadData());

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        showLoading("Carregando contas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                StringBuilder sql = new StringBuilder();
                sql.append("SELECT cp.*, cn.nome as caixa_nome FROM contas_pagar cp ");
                sql.append("LEFT JOIN caixas_nominais cn ON cp.caixa_nominal_id = cn.id WHERE 1=1 ");

                int statusPos = spFiltroStatus.getSelectedItemPosition();
                switch (statusPos) {
                    case 1: sql.append("AND cp.status = 'pendente' "); break;
                    case 2: sql.append("AND cp.status = 'pago_parcial' "); break;
                    case 3: sql.append("AND cp.status = 'pago' "); break;
                    case 4: sql.append("AND cp.status = 'cancelado' "); break;
                }
                sql.append("ORDER BY cp.status ASC, cp.data_vencimento ASC");

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql.toString());

                List<Map<String, Object>> list = new ArrayList<>();
                double totalPend = 0, totalPago = 0;
                int qtdPend = 0;

                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("descricao", rs.getString("descricao"));
                    m.put("fornecedor_id", rs.getInt("fornecedor_id"));
                    m.put("fornecedor_nome", rs.getString("fornecedor_nome"));
                    m.put("nota_entrada_id", rs.getInt("nota_entrada_id"));
                    m.put("valor_total", rs.getDouble("valor_total"));
                    m.put("valor_pago", rs.getDouble("valor_pago"));
                    m.put("valor_pendente", rs.getDouble("valor_pendente"));
                    m.put("data_emissao", rs.getString("data_emissao"));
                    m.put("data_vencimento", rs.getString("data_vencimento"));
                    m.put("data_pagamento", rs.getString("data_pagamento"));
                    m.put("status", rs.getString("status"));
                    m.put("forma_pagamento", rs.getString("forma_pagamento"));
                    m.put("caixa_nominal_id", rs.getInt("caixa_nominal_id"));
                    m.put("caixa_nome", rs.getString("caixa_nome"));
                    m.put("parcela_numero", rs.getInt("parcela_numero"));
                    m.put("total_parcelas", rs.getInt("total_parcelas"));
                    m.put("observacao", rs.getString("observacao"));
                    list.add(m);

                    String status = rs.getString("status");
                    if ("pendente".equals(status) || "pago_parcial".equals(status)) {
                        totalPend += rs.getDouble("valor_pendente");
                        qtdPend++;
                    }
                    totalPago += rs.getDouble("valor_pago");
                }
                rs.close();
                stmt.close();

                final double fTotalPend = totalPend;
                final double fTotalPago = totalPago;
                final int fQtdPend = qtdPend;

                hideLoading();
                runOnUiThread(() -> {
                    adapter.setItems(list);
                    tvTotalPendente.setText("R$ " + FormatUtils.formatMoney(fTotalPend));
                    tvTotalPago.setText("R$ " + FormatUtils.formatMoney(fTotalPago));
                    tvQtdPendentes.setText(String.valueOf(fQtdPend));
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showContaOptions(Map<String, Object> conta) {
        String status = safeStr(conta.get("status"));
        List<String> opcoes = new ArrayList<>();
        List<Runnable> acoes = new ArrayList<>();

        if ("pendente".equals(status) || "pago_parcial".equals(status)) {
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_PAGAR_PAGAR)) {
                opcoes.add("Registrar Pagamento");
                acoes.add(() -> showPagamentoDialog(conta));
                double pendente = conta.get("valor_pendente") != null ? ((Number) conta.get("valor_pendente")).doubleValue() : 0;
                opcoes.add("Pagar Total (R$ " + FormatUtils.formatMoney(pendente) + ")");
                acoes.add(() -> pagarTotal(conta));
            }
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_PAGAR_EDITAR)) {
                opcoes.add("Editar Conta");
                acoes.add(() -> showEditarConta(conta));
            }
        }

        opcoes.add("Historico de Pagamentos");
        acoes.add(() -> showHistoricoPagamentos(conta));

        if ("pendente".equals(status) || "pago_parcial".equals(status)) {
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_PAGAR_CANCELAR)) {
                opcoes.add("Cancelar Conta");
                acoes.add(() -> cancelarConta(conta));
            }
        }

        if (opcoes.isEmpty()) {
            showToast("Nenhuma acao disponivel.");
            return;
        }

        String titulo = safeStr(conta.get("descricao"));
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setItems(opcoes.toArray(new String[0]), (d, w) -> acoes.get(w).run())
                .show();
    }

    private void showPagamentoDialog(Map<String, Object> conta) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        double pendente = conta.get("valor_pendente") != null ? ((Number) conta.get("valor_pendente")).doubleValue() : 0;

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Conta: " + safeStr(conta.get("descricao"))
                + "\nValor Pendente: R$ " + FormatUtils.formatMoney(pendente));
        tvInfo.setTextColor(0xFFB0BEC5);
        tvInfo.setTextSize(13);
        tvInfo.setPadding(0, 0, 0, pad / 2);
        layout.addView(tvInfo);

        EditText etValor = new EditText(this);
        etValor.setHint("Valor do pagamento");
        etValor.setTextColor(0xFFFFFFFF);
        etValor.setHintTextColor(0xFF90A4AE);
        etValor.setBackgroundResource(R.drawable.input_bg);
        etValor.setPadding(32, 24, 32, 24);
        etValor.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etValor.setText(FormatUtils.formatMoney(pendente));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        etValor.setLayoutParams(lp);
        layout.addView(etValor);

        TextView tvFormaLabel = new TextView(this);
        tvFormaLabel.setText("Forma de Pagamento:");
        tvFormaLabel.setTextColor(0xFF00BCD4);
        tvFormaLabel.setTextSize(13);
        tvFormaLabel.setPadding(0, pad / 2, 0, 4);
        layout.addView(tvFormaLabel);

        Spinner spForma = new Spinner(this);
        spForma.setBackgroundResource(R.drawable.input_bg);
        spForma.setLayoutParams(lp);
        layout.addView(spForma);

        TextView tvCaixaLabel = new TextView(this);
        tvCaixaLabel.setText("Caixa para Debito:");
        tvCaixaLabel.setTextColor(0xFF00BCD4);
        tvCaixaLabel.setTextSize(13);
        tvCaixaLabel.setPadding(0, pad / 2, 0, 4);
        layout.addView(tvCaixaLabel);

        Spinner spCaixa = new Spinner(this);
        spCaixa.setBackgroundResource(R.drawable.input_bg);
        spCaixa.setLayoutParams(lp);
        layout.addView(spCaixa);

        EditText etObs = new EditText(this);
        etObs.setHint("Observacao (opcional)");
        etObs.setTextColor(0xFFFFFFFF);
        etObs.setHintTextColor(0xFF90A4AE);
        etObs.setBackgroundResource(R.drawable.input_bg);
        etObs.setPadding(32, 24, 32, 24);
        etObs.setLayoutParams(lp);
        layout.addView(etObs);

        final List<Integer> caixaIds = new ArrayList<>();
        final List<String> caixaNomes = new ArrayList<>();

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Formas de pagamento
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT descricao FROM formas_pagamento WHERE ativo = 1 ORDER BY descricao");
                List<String> formas = new ArrayList<>();
                while (rs.next()) formas.add(rs.getString("descricao"));
                rs.close();
                if (formas.isEmpty()) formas.add("Dinheiro");

                // Caixas nominais
                caixaIds.add(0);
                caixaNomes.add("-- Sem caixa especifico --");
                rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    caixaIds.add(rs.getInt("id"));
                    caixaNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                final List<String> fFormas = formas;
                runOnUiThread(() -> {
                    ArrayAdapter<String> formaAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, fFormas);
                    formaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spForma.setAdapter(formaAdapter);

                    ArrayAdapter<String> caixaAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    caixaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(caixaAdapter);

                    // Pré-selecionar caixa da conta se houver
                    int caixaIdAtual = conta.get("caixa_nominal_id") != null ?
                            ((Number) conta.get("caixa_nominal_id")).intValue() : 0;
                    for (int i = 0; i < caixaIds.size(); i++) {
                        if (caixaIds.get(i) == caixaIdAtual) {
                            spCaixa.setSelection(i);
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    List<String> fallback = new ArrayList<>();
                    fallback.add("Dinheiro");
                    ArrayAdapter<String> fa = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, fallback);
                    fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spForma.setAdapter(fa);

                    caixaIds.add(0);
                    caixaNomes.add("-- Sem caixa --");
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                });
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Registrar Pagamento")
                .setView(layout)
                .setPositiveButton("Pagar", (d, w) -> {
                    double valor = FormatUtils.parseMoney(etValor.getText().toString());
                    if (valor <= 0) {
                        showError("Informe um valor valido.");
                        return;
                    }
                    String forma = spForma.getSelectedItem() != null ? spForma.getSelectedItem().toString() : "Dinheiro";
                    int caixaPos = spCaixa.getSelectedItemPosition();
                    int caixaId = caixaPos >= 0 && caixaPos < caixaIds.size() ? caixaIds.get(caixaPos) : 0;
                    String obs = etObs.getText().toString().trim();
                    int contaId = ((Number) conta.get("id")).intValue();
                    registrarPagamento(contaId, valor, forma, caixaId, obs);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void registrarPagamento(int contaId, double valor, String forma, int caixaId, String obs) {
        showLoading("Registrando pagamento...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                int userId = prefs.getInt("user_id", 0);

                // Buscar dados atuais da conta
                PreparedStatement psGet = conn.prepareStatement(
                        "SELECT valor_total, valor_pago, valor_pendente FROM contas_pagar WHERE id = ?");
                psGet.setInt(1, contaId);
                ResultSet rs = psGet.executeQuery();
                double valorTotal = 0, valorPagoAtual = 0, valorPendenteAtual = 0;
                if (rs.next()) {
                    valorTotal = rs.getDouble("valor_total");
                    valorPagoAtual = rs.getDouble("valor_pago");
                    valorPendenteAtual = rs.getDouble("valor_pendente");
                }
                rs.close();
                psGet.close();

                double novoValorPago = valorPagoAtual + valor;
                double novoValorPendente = valorPendenteAtual - valor;
                if (novoValorPendente < 0) novoValorPendente = 0;

                String novoStatus;
                if (novoValorPendente <= 0.001) {
                    novoStatus = "pago";
                    novoValorPendente = 0;
                } else {
                    novoStatus = "pago_parcial";
                }

                // Inserir registro de pagamento
                PreparedStatement psPag = conn.prepareStatement(
                        "INSERT INTO pagamentos_conta_pagar (conta_pagar_id, valor, forma_pagamento, caixa_nominal_id, observacao, usuario_id) VALUES (?,?,?,?,?,?)");
                psPag.setInt(1, contaId);
                psPag.setDouble(2, valor);
                psPag.setString(3, forma);
                if (caixaId > 0) psPag.setInt(4, caixaId); else psPag.setNull(4, Types.INTEGER);
                psPag.setString(5, obs.isEmpty() ? null : obs);
                psPag.setInt(6, userId);
                psPag.executeUpdate();
                psPag.close();

                // Atualizar conta
                String dataPagamento = "pago".equals(novoStatus) ? ", data_pagamento = NOW()" : "";
                PreparedStatement psUpd = conn.prepareStatement(
                        "UPDATE contas_pagar SET valor_pago = ?, valor_pendente = ?, status = ?" + dataPagamento + " WHERE id = ?");
                psUpd.setDouble(1, novoValorPago);
                psUpd.setDouble(2, novoValorPendente);
                psUpd.setString(3, novoStatus);
                psUpd.setInt(4, contaId);
                psUpd.executeUpdate();
                psUpd.close();

                hideLoading();
                showToast("Pagamento registrado com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void pagarTotal(Map<String, Object> conta) {
        double pendente = conta.get("valor_pendente") != null ? ((Number) conta.get("valor_pendente")).doubleValue() : 0;
        showConfirm("Pagar Total",
                "Confirmar pagamento total de R$ " + FormatUtils.formatMoney(pendente) + "?",
                () -> {
                    int contaId = ((Number) conta.get("id")).intValue();
                    int caixaId = conta.get("caixa_nominal_id") != null ?
                            ((Number) conta.get("caixa_nominal_id")).intValue() : 0;
                    String forma = safeStr(conta.get("forma_pagamento"));
                    if (forma.isEmpty()) forma = "Dinheiro";
                    registrarPagamento(contaId, pendente, forma, caixaId, "Pagamento total");
                });
    }

    private void showEditarConta(Map<String, Object> conta) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText etDescricao = criarInput(layout, "Descricao", safeStr(conta.get("descricao")));
        EditText etVencimento = criarInput(layout, "Data Vencimento (dd/MM/yyyy)", FormatUtils.formatDate(safeStr(conta.get("data_vencimento"))));
        EditText etObs = criarInput(layout, "Observacao", safeStr(conta.get("observacao")));

        TextView tvCaixaLabel = new TextView(this);
        tvCaixaLabel.setText("Caixa para Debito:");
        tvCaixaLabel.setTextColor(0xFF00BCD4);
        tvCaixaLabel.setTextSize(13);
        tvCaixaLabel.setPadding(0, pad / 2, 0, 4);
        layout.addView(tvCaixaLabel);

        Spinner spCaixa = new Spinner(this);
        spCaixa.setBackgroundResource(R.drawable.input_bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        spCaixa.setLayoutParams(lp);
        layout.addView(spCaixa);

        final List<Integer> caixaIds = new ArrayList<>();
        final List<String> caixaNomes = new ArrayList<>();

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                caixaIds.add(0);
                caixaNomes.add("-- Sem caixa especifico --");
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    caixaIds.add(rs.getInt("id"));
                    caixaNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    ArrayAdapter<String> caixaAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    caixaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(caixaAdapter);

                    int caixaIdAtual = conta.get("caixa_nominal_id") != null ?
                            ((Number) conta.get("caixa_nominal_id")).intValue() : 0;
                    for (int i = 0; i < caixaIds.size(); i++) {
                        if (caixaIds.get(i) == caixaIdAtual) {
                            spCaixa.setSelection(i);
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    caixaIds.add(0);
                    caixaNomes.add("-- Sem caixa --");
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                });
            }
        }).start();

        int contaId = ((Number) conta.get("id")).intValue();

        new AlertDialog.Builder(this)
                .setTitle("Editar Conta")
                .setView(layout)
                .setPositiveButton("Salvar", (d, w) -> {
                    String descricao = etDescricao.getText().toString().trim();
                    String vencimento = etVencimento.getText().toString().trim();
                    String obs = etObs.getText().toString().trim();
                    int caixaPos = spCaixa.getSelectedItemPosition();
                    int caixaId = caixaPos >= 0 && caixaPos < caixaIds.size() ? caixaIds.get(caixaPos) : 0;

                    if (descricao.isEmpty()) {
                        showError("Informe a descricao.");
                        return;
                    }
                    salvarEdicaoConta(contaId, descricao, vencimento, obs, caixaId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarEdicaoConta(int id, String descricao, String vencimento, String obs, int caixaId) {
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                String dataVenc = null;
                if (!vencimento.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        java.util.Date d = sdf.parse(vencimento);
                        SimpleDateFormat sdfOut = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        dataVenc = sdfOut.format(d);
                    } catch (Exception ignored) {}
                }

                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE contas_pagar SET descricao = ?, data_vencimento = ?, observacao = ?, caixa_nominal_id = ? WHERE id = ?");
                ps.setString(1, descricao);
                if (dataVenc != null) ps.setString(2, dataVenc); else ps.setNull(2, Types.VARCHAR);
                ps.setString(3, obs.isEmpty() ? null : obs);
                if (caixaId > 0) ps.setInt(4, caixaId); else ps.setNull(4, Types.INTEGER);
                ps.setInt(5, id);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Conta atualizada!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void showHistoricoPagamentos(Map<String, Object> conta) {
        int contaId = ((Number) conta.get("id")).intValue();
        showLoading("Carregando historico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT p.*, cn.nome as caixa_nome, u.nome as usuario_nome " +
                        "FROM pagamentos_conta_pagar p " +
                        "LEFT JOIN caixas_nominais cn ON p.caixa_nominal_id = cn.id " +
                        "LEFT JOIN usuarios u ON p.usuario_id = u.id " +
                        "WHERE p.conta_pagar_id = ? ORDER BY p.data_pagamento DESC");
                ps.setInt(1, contaId);
                ResultSet rs = ps.executeQuery();

                StringBuilder sb = new StringBuilder();
                sb.append("Historico de Pagamentos\n");
                sb.append("Conta: ").append(safeStr(conta.get("descricao"))).append("\n\n");

                boolean temPag = false;
                while (rs.next()) {
                    temPag = true;
                    sb.append("Data: ").append(FormatUtils.formatDate(rs.getString("data_pagamento"))).append("\n");
                    sb.append("Valor: R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor"))).append("\n");
                    sb.append("Forma: ").append(safeStr(rs.getString("forma_pagamento"))).append("\n");
                    String caixaNome = rs.getString("caixa_nome");
                    if (caixaNome != null && !caixaNome.isEmpty()) {
                        sb.append("Caixa: ").append(caixaNome).append("\n");
                    }
                    String usuarioNome = rs.getString("usuario_nome");
                    if (usuarioNome != null && !usuarioNome.isEmpty()) {
                        sb.append("Usuario: ").append(usuarioNome).append("\n");
                    }
                    String obs = rs.getString("observacao");
                    if (obs != null && !obs.isEmpty()) {
                        sb.append("Obs: ").append(obs).append("\n");
                    }
                    sb.append("---\n");
                }
                rs.close();
                ps.close();

                if (!temPag) sb.append("Nenhum pagamento registrado.");

                hideLoading();
                final String texto = sb.toString();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Historico")
                            .setMessage(texto)
                            .setPositiveButton("Fechar", null)
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void cancelarConta(Map<String, Object> conta) {
        showConfirm("Cancelar Conta", "Deseja cancelar esta conta a pagar?", () -> {
            showLoading("Cancelando...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    int contaId = ((Number) conta.get("id")).intValue();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE contas_pagar SET status = 'cancelado' WHERE id = ? AND status IN ('pendente','pago_parcial')");
                    ps.setInt(1, contaId);
                    int rows = ps.executeUpdate();
                    ps.close();
                    hideLoading();
                    if (rows > 0) {
                        showToast("Conta cancelada!");
                    } else {
                        showError("Nao foi possivel cancelar.");
                    }
                    loadData();
                } catch (Exception e) {
                    hideLoading();
                    showErrorFromException(e, ErrorHandler.CTX_SALVAR);
                }
            }).start();
        });
    }

    private void showNovaConta() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText etDescricao = criarInput(layout, "Descricao *", "");
        EditText etValor = criarInput(layout, "Valor Total *", "");
        etValor.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etVencimento = criarInput(layout, "Data Vencimento (dd/MM/yyyy)", "");
        EditText etObs = criarInput(layout, "Observacao", "");

        TextView tvCaixaLabel = new TextView(this);
        tvCaixaLabel.setText("Caixa para Debito:");
        tvCaixaLabel.setTextColor(0xFF00BCD4);
        tvCaixaLabel.setTextSize(13);
        tvCaixaLabel.setPadding(0, pad / 2, 0, 4);
        layout.addView(tvCaixaLabel);

        Spinner spCaixa = new Spinner(this);
        spCaixa.setBackgroundResource(R.drawable.input_bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        spCaixa.setLayoutParams(lp);
        layout.addView(spCaixa);

        final List<Integer> caixaIds = new ArrayList<>();
        final List<String> caixaNomes = new ArrayList<>();

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                caixaIds.add(0);
                caixaNomes.add("-- Sem caixa especifico --");
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    caixaIds.add(rs.getInt("id"));
                    caixaNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    ArrayAdapter<String> caixaAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    caixaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(caixaAdapter);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    caixaIds.add(0);
                    caixaNomes.add("-- Sem caixa --");
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                });
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Nova Conta a Pagar")
                .setView(layout)
                .setPositiveButton("Salvar", (d, w) -> {
                    String descricao = etDescricao.getText().toString().trim();
                    String valorStr = etValor.getText().toString().trim();
                    String vencimento = etVencimento.getText().toString().trim();
                    String obs = etObs.getText().toString().trim();
                    int caixaPos = spCaixa.getSelectedItemPosition();
                    int caixaId = caixaPos >= 0 && caixaPos < caixaIds.size() ? caixaIds.get(caixaPos) : 0;

                    if (descricao.isEmpty()) {
                        showError("Informe a descricao.");
                        return;
                    }
                    double valor = FormatUtils.parseMoney(valorStr);
                    if (valor <= 0) {
                        showError("Informe um valor valido.");
                        return;
                    }
                    criarContaManual(descricao, valor, vencimento, obs, caixaId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void criarContaManual(String descricao, double valor, String vencimento, String obs, int caixaId) {
        showLoading("Criando conta...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                int userId = prefs.getInt("user_id", 0);

                String dataVenc = null;
                if (!vencimento.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        java.util.Date d = sdf.parse(vencimento);
                        SimpleDateFormat sdfOut = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        dataVenc = sdfOut.format(d);
                    } catch (Exception ignored) {}
                }

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO contas_pagar (descricao, valor_total, valor_pendente, data_vencimento, observacao, caixa_nominal_id, usuario_id, status) VALUES (?,?,?,?,?,?,?,'pendente')");
                ps.setString(1, descricao);
                ps.setDouble(2, valor);
                ps.setDouble(3, valor);
                if (dataVenc != null) ps.setString(4, dataVenc); else ps.setNull(4, Types.VARCHAR);
                ps.setString(5, obs.isEmpty() ? null : obs);
                if (caixaId > 0) ps.setInt(6, caixaId); else ps.setNull(6, Types.INTEGER);
                ps.setInt(7, userId);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Conta criada com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * Método público para criar contas a pagar a partir de nota de entrada.
     * Chamado pela EntradaNotasActivity ao confirmar nota.
     */
    public static void criarContasPorParcelamento(android.content.Context context,
            int notaEntradaId, String fornecedorNome, int fornecedorId,
            double valorTotal, List<String> datasVencimento, int caixaId) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(context);
                Connection conn = db.getConnection();
                android.content.SharedPreferences prefs = context.getSharedPreferences("session", android.content.Context.MODE_PRIVATE);
                int userId = prefs.getInt("user_id", 0);

                // Evita duplicar contas quando a nota ja foi enviada/confirmada antes.
                PreparedStatement psCheck = conn.prepareStatement(
                        "SELECT COUNT(*) FROM contas_pagar WHERE nota_entrada_id = ? AND status <> 'cancelado'");
                psCheck.setInt(1, notaEntradaId);
                ResultSet rsCheck = psCheck.executeQuery();
                if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                    rsCheck.close();
                    psCheck.close();
                    android.util.Log.i("ContasPagar", "Nota #" + notaEntradaId + " ja possui contas a pagar geradas.");
                    return;
                }
                rsCheck.close();
                psCheck.close();

                List<String> vencimentos = new ArrayList<>();
                if (datasVencimento != null) {
                    for (String data : datasVencimento) {
                        if (data != null && !data.trim().isEmpty()) {
                            vencimentos.add(data.trim());
                        }
                    }
                }

                // Nota a vista ou sem parcelas: gera UMA conta com vencimento hoje.
                // Antes ficava lista vazia, o loop nao executava e nada era registrado.
                if (vencimentos.isEmpty()) {
                    vencimentos.add(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date()));
                }

                int totalParcelas = vencimentos.size();
                double valorAcumulado = 0;

                for (int i = 0; i < totalParcelas; i++) {
                    String dataVenc = normalizarDataMysql(vencimentos.get(i));
                    int parcelaNum = i + 1;
                    double valorParcela;
                    if (parcelaNum == totalParcelas) {
                        valorParcela = Math.round((valorTotal - valorAcumulado) * 100.0) / 100.0;
                    } else {
                        valorParcela = Math.round((valorTotal / totalParcelas) * 100.0) / 100.0;
                        valorAcumulado += valorParcela;
                    }

                    String descricao = "Nota #" + notaEntradaId + " - " + fornecedorNome
                            + (totalParcelas > 1 ? " (" + parcelaNum + "/" + totalParcelas + ")" : "");

                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO contas_pagar (descricao, fornecedor_id, fornecedor_nome, nota_entrada_id, " +
                            "valor_total, valor_pendente, data_vencimento, caixa_nominal_id, parcela_numero, total_parcelas, " +
                            "usuario_id, status) VALUES (?,?,?,?,?,?,?,?,?,?,?,'pendente')");
                    ps.setString(1, descricao);
                    if (fornecedorId > 0) ps.setInt(2, fornecedorId); else ps.setNull(2, Types.INTEGER);
                    ps.setString(3, fornecedorNome);
                    ps.setInt(4, notaEntradaId);
                    ps.setDouble(5, valorParcela);
                    ps.setDouble(6, valorParcela);
                    if (dataVenc != null && !dataVenc.isEmpty()) ps.setString(7, dataVenc); else ps.setNull(7, Types.VARCHAR);
                    if (caixaId > 0) ps.setInt(8, caixaId); else ps.setNull(8, Types.INTEGER);
                    ps.setInt(9, parcelaNum);
                    ps.setInt(10, totalParcelas);
                    ps.setInt(11, userId);
                    ps.executeUpdate();
                    ps.close();
                }
                android.util.Log.i("ContasPagar", "Contas geradas para nota #" + notaEntradaId + ": " + totalParcelas + " parcela(s).");
            } catch (Exception e) {
                android.util.Log.e("ContasPagar", "Erro ao criar contas: " + e.getMessage(), e);
            }
        }).start();
    }

    private static String normalizarDataMysql(String data) {
        if (data == null) return null;
        String valor = data.trim();
        if (valor.isEmpty()) return null;
        try {
            if (valor.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return valor.length() >= 10 ? valor.substring(0, 10) : valor;
            }
            if (valor.matches("\\d{2}/\\d{2}/\\d{4}")) {
                java.util.Date parsed = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(valor);
                return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(parsed);
            }
        } catch (Exception ignored) {}
        return valor;
    }

    private EditText criarInput(android.widget.LinearLayout container, String hint, String valor) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF90A4AE);
        et.setBackgroundResource(R.drawable.input_bg);
        et.setPadding(32, 24, 32, 24);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        et.setLayoutParams(lp);
        if (!valor.isEmpty()) et.setText(valor);
        container.addView(et);
        return et;
    }

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
