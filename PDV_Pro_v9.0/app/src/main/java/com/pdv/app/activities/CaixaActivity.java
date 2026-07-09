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
import com.pdv.app.models.ValeDebito;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.ui.AdminUserPasswordDialog;
import com.pdv.app.utils.ErrorHandler;

public class CaixaActivity extends BaseActivity {
    private TextView tvStatus, tvValorAbertura, tvTotalVendas, tvTotalVales, tvSubtotalCaixa;
    private Button btnAbrir, btnFechar, btnAddVale, btnReimprimirFechamento;
    private RecyclerView rvVales;
    private GenericAdapter<ValeDebito> valesAdapter;
    private Spinner spCaixaOperacao;
    private final List<Integer> caixaNominalIds = new ArrayList<>();
    private final List<String> caixaNominalNomes = new ArrayList<>();
    private int caixaId = 0;
    private int caixaNominalSelecionadoId = 0;
    private String caixaNominalSelecionadoNome = "Automatico / ultimo caixa aberto";
    private boolean carregandoDropdownCaixas = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caixa);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CAIXA_ACESSAR)) {
            return;
        }

        tvStatus = findViewById(R.id.tvStatus);
        tvValorAbertura = findViewById(R.id.tvValorAbertura);
        tvTotalVendas = findViewById(R.id.tvTotalVendas);
        tvTotalVales = findViewById(R.id.tvTotalVales);
        tvSubtotalCaixa = findViewById(R.id.tvSubtotalCaixa);
        btnAbrir = findViewById(R.id.btnAbrir);
        btnFechar = findViewById(R.id.btnFechar);
        btnAddVale = findViewById(R.id.btnAddVale);
        btnReimprimirFechamento = findViewById(R.id.btnReimprimirFechamento);
        spCaixaOperacao = findViewById(R.id.spCaixaOperacao);
        rvVales = findViewById(R.id.rvVales);

        rvVales.setLayoutManager(new LinearLayoutManager(this));
        valesAdapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, item.getDescricao());
            String centro = item.getCentroCustoNome();
            holder.setText(R.id.tvLine2, "R$ " + FormatUtils.formatMoney(item.getValor())
                    + ((centro != null && !centro.isEmpty()) ? " | Centro: " + centro : "")
                    + ((item.getUsuarioNome() != null && !item.getUsuarioNome().isEmpty())
                    ? " | Usuario: " + item.getUsuarioNome() : ""));
            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);
            Button btnEditarVale = holder.find(R.id.btnEditar);
            if (btnEditarVale != null) btnEditarVale.setVisibility(View.GONE);
            Button btnExcluirVale = holder.find(R.id.btnInativar);
            if (btnExcluirVale != null) {
                btnExcluirVale.setText("Excluir");
                btnExcluirVale.setVisibility(View.VISIBLE);
                btnExcluirVale.setOnClickListener(v -> solicitarExclusaoVale(item));
            }
        });
        rvVales.setAdapter(valesAdapter);

        // Controlar habilitacao baseado em permissoes
        PermissionHelper.controlarHabilitacao(this, btnAbrir, PermissionConstants.CAIXA_ABRIR);
        PermissionHelper.controlarHabilitacao(this, btnFechar, PermissionConstants.CAIXA_FECHAR);
        PermissionHelper.controlarHabilitacao(this, btnAddVale, PermissionConstants.CAIXA_VALE_DEBITO);
        PermissionHelper.controlarVisibilidade(this, btnReimprimirFechamento,
                PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO);

        btnAbrir.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.CAIXA_ABRIR)) abrirCaixa();
        });
        btnFechar.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.CAIXA_FECHAR)) fecharCaixa();
        });
        btnAddVale.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.CAIXA_VALE_DEBITO)) adicionarVale();
        });
        btnReimprimirFechamento.setOnClickListener(v -> abrirReimpressaoFechamento());

        Button btnGerenciarCaixas = findViewById(R.id.btnGerenciarCaixas);
        if (btnGerenciarCaixas != null) {
            btnGerenciarCaixas.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, CadastroCaixasNominaisActivity.class);
                startActivity(intent);
            });
        }

        configurarDropdownCaixas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (spCaixaOperacao != null) {
            configurarDropdownCaixas();
        } else {
            loadCaixa();
        }
    }

    private void configurarDropdownCaixas() {
        if (spCaixaOperacao == null) {
            loadCaixa();
            return;
        }
        carregandoDropdownCaixas = true;
        caixaNominalIds.clear();
        caixaNominalNomes.clear();
        caixaNominalIds.add(0);
        caixaNominalNomes.add("Automatico / ultimo caixa aberto");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    caixaNominalIds.add(rs.getInt("id"));
                    caixaNominalNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
            } catch (Exception ignored) {}

            runOnUiThread(() -> {
                ArrayAdapter<String> adapterCaixas = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, caixaNominalNomes);
                adapterCaixas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spCaixaOperacao.setAdapter(adapterCaixas);

                int prefId = getSharedPreferences("caixa_config", MODE_PRIVATE)
                        .getInt("caixa_nominal_id_selecionado", 0);
                int pos = 0;
                for (int i = 0; i < caixaNominalIds.size(); i++) {
                    if (caixaNominalIds.get(i) == prefId) { pos = i; break; }
                }
                spCaixaOperacao.setSelection(pos);
                caixaNominalSelecionadoId = caixaNominalIds.get(pos);
                caixaNominalSelecionadoNome = caixaNominalNomes.get(pos);
                carregandoDropdownCaixas = false;

                spCaixaOperacao.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position < 0 || position >= caixaNominalIds.size()) return;
                        caixaNominalSelecionadoId = caixaNominalIds.get(position);
                        caixaNominalSelecionadoNome = caixaNominalNomes.get(position);
                        getSharedPreferences("caixa_config", MODE_PRIVATE).edit()
                                .putInt("caixa_nominal_id_selecionado", caixaNominalSelecionadoId)
                                .putString("caixa_nominal_nome_selecionado", caixaNominalSelecionadoNome)
                                .apply();
                        if (!carregandoDropdownCaixas) loadCaixa();
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
                loadCaixa();
            });
        }).start();
    }

    private void loadCaixa() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();

                PreparedStatement psCaixa;
                if (caixaNominalSelecionadoId > 0) {
                    psCaixa = conn.prepareStatement(
                            "SELECT c.*, COALESCE(cn.nome, 'Caixa sem nome') AS caixa_nome " +
                            "FROM caixa c LEFT JOIN caixas_nominais cn ON c.caixa_nominal_id = cn.id " +
                            "WHERE c.status = 'aberto' AND c.caixa_nominal_id = ? ORDER BY c.id DESC LIMIT 1");
                    psCaixa.setInt(1, caixaNominalSelecionadoId);
                } else {
                    psCaixa = conn.prepareStatement(
                            "SELECT c.*, COALESCE(cn.nome, 'Caixa sem nome') AS caixa_nome " +
                            "FROM caixa c LEFT JOIN caixas_nominais cn ON c.caixa_nominal_id = cn.id " +
                            "WHERE c.status = 'aberto' ORDER BY c.id DESC LIMIT 1");
                }
                ResultSet rs = psCaixa.executeQuery();
                if (rs.next()) {
                    caixaId = rs.getInt("id");
                    double valorAbertura = rs.getDouble("valor_abertura");
                    String caixaNomeAberto = rs.getString("caixa_nome");
                    if (caixaNomeAberto == null || caixaNomeAberto.trim().isEmpty()) {
                        caixaNomeAberto = caixaNominalSelecionadoNome;
                    }
                    rs.close();
                    psCaixa.close();

                    // Total vendas
                    ResultSet rsV = stmt.executeQuery("SELECT COALESCE(SUM(total_liquido),0) as total FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                    double totalVendas = 0;
                    if (rsV.next()) totalVendas = rsV.getDouble("total");
                    rsV.close();

                    // Total vales
                    ResultSet rsVl = stmt.executeQuery("SELECT COALESCE(SUM(valor),0) as total FROM vales_debito WHERE caixa_id = " + caixaId);
                    double totalVales = 0;
                    if (rsVl.next()) totalVales = rsVl.getDouble("total");
                    rsVl.close();

                    // Vales list
                    ResultSet rsVales = stmt.executeQuery(
                            "SELECT vd.*, COALESCE(cc.nome, 'Sem centro') AS centro_custo_nome, "
                                    + "COALESCE(u.nome, 'Usuario antigo') AS usuario_nome "
                                    + "FROM vales_debito vd LEFT JOIN centros_custo cc ON vd.centro_custo_id = cc.id "
                                    + "LEFT JOIN usuarios u ON vd.usuario_id = u.id "
                                    + "WHERE vd.caixa_id = " + caixaId + " ORDER BY vd.id DESC");
                    List<ValeDebito> vales = new ArrayList<>();
                    while (rsVales.next()) {
                        ValeDebito vale = new ValeDebito();
                        vale.setId(rsVales.getInt("id"));
                        vale.setDescricao(rsVales.getString("descricao"));
                        vale.setValor(rsVales.getDouble("valor"));
                        vale.setCentroCustoId(rsVales.getInt("centro_custo_id"));
                        vale.setCentroCustoNome(rsVales.getString("centro_custo_nome"));
                        vale.setUsuarioId(rsVales.getInt("usuario_id"));
                        vale.setUsuarioNome(rsVales.getString("usuario_nome"));
                        vales.add(vale);
                    }
                    rsVales.close();

                    final double fVA = valorAbertura, fTV = totalVendas, fTVl = totalVales;
                    final String fCaixaNomeAberto = caixaNomeAberto;
                    final List<ValeDebito> fVales = vales;

                    hideLoading();
                    runOnUiThread(() -> {
                        tvStatus.setText("CAIXA ABERTO" + (fCaixaNomeAberto != null && !fCaixaNomeAberto.isEmpty()
                                ? " - " + fCaixaNomeAberto + " (#" + caixaId + ")" : ""));
                        tvStatus.setBackgroundResource(R.drawable.status_open);
                        tvValorAbertura.setText("Abertura: R$ " + FormatUtils.formatMoney(fVA));
                        tvTotalVendas.setText("Vendas: R$ " + FormatUtils.formatMoney(fTV));
                        tvTotalVales.setText("Vales: R$ " + FormatUtils.formatMoney(fTVl));
                        tvSubtotalCaixa.setText("Subtotal em caixa: R$ "
                                + FormatUtils.formatMoney(fVA + fTV - fTVl));
                        btnAbrir.setVisibility(View.GONE);
                        btnFechar.setVisibility(View.VISIBLE);
                        btnAddVale.setVisibility(View.VISIBLE);
                        valesAdapter.setItems(fVales);
                    });
                } else {
                    rs.close();
                    psCaixa.close();
                    caixaId = 0;
                    final String fSelecionado = caixaNominalSelecionadoNome;
                    hideLoading();
                    runOnUiThread(() -> {
                        tvStatus.setText("CAIXA FECHADO" + (caixaNominalSelecionadoId > 0 ? " - " + fSelecionado : ""));
                        tvStatus.setBackgroundResource(R.drawable.status_closed);
                        tvValorAbertura.setText("");
                        tvTotalVendas.setText("");
                        tvTotalVales.setText("");
                        tvSubtotalCaixa.setText("");
                        btnAbrir.setVisibility(View.VISIBLE);
                        btnFechar.setVisibility(View.GONE);
                        btnAddVale.setVisibility(View.GONE);
                        valesAdapter.setItems(new ArrayList<>());
                    });
                }
                stmt.close();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CAIXA);
            }
        }).start();
    }

    private void abrirCaixa() {
        View view = getLayoutInflater().inflate(R.layout.dialog_quantidade, null);
        EditText etValor = view.findViewById(R.id.etQuantidade);
        etValor.setHint("Valor de abertura");
        etValor.setText("0.00");

        new AlertDialog.Builder(this)
                .setTitle("Abrir Caixa")
                .setView(view)
                .setPositiveButton("Abrir", (d, w) -> {
                    double valor = FormatUtils.parseMoney(etValor.getText().toString());
                    showLoading("Abrindo caixa...");
                    new Thread(() -> {
                        try {
                            SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                            int userId = prefs.getInt("user_id", 0);
                            String userName = prefs.getString("user_nome", "");

                            DatabaseHelper db = DatabaseHelper.getInstance(this);
                            Connection conn = db.getConnection();

                            PreparedStatement psAberto;
                            if (caixaNominalSelecionadoId > 0) {
                                psAberto = conn.prepareStatement(
                                        "SELECT id FROM caixa WHERE status='aberto' AND caixa_nominal_id=? ORDER BY id DESC LIMIT 1");
                                psAberto.setInt(1, caixaNominalSelecionadoId);
                            } else {
                                psAberto = conn.prepareStatement(
                                        "SELECT id FROM caixa WHERE status='aberto' AND caixa_nominal_id IS NULL ORDER BY id DESC LIMIT 1");
                            }
                            ResultSet rsAberto = psAberto.executeQuery();
                            if (rsAberto.next()) {
                                caixaId = rsAberto.getInt(1);
                                rsAberto.close();
                                psAberto.close();
                                hideLoading();
                                showError("Este caixa ja esta aberto. Selecione outro caixa no dropdown ou feche o caixa atual.");
                                loadCaixa();
                                return;
                            }
                            rsAberto.close();
                            psAberto.close();

                            PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO caixa (usuario_id, caixa_nominal_id, data_abertura, valor_abertura, status) VALUES (?, ?, NOW(), ?, 'aberto')");
                            ps.setInt(1, userId);
                            if (caixaNominalSelecionadoId > 0) ps.setInt(2, caixaNominalSelecionadoId); else ps.setNull(2, Types.INTEGER);
                            ps.setDouble(3, valor);
                            ps.executeUpdate();
                            ps.close();

                            // Gerar comprovante de abertura
                            String dataHora = FormatUtils.getCurrentDateTime();
                            String comprovanteAbertura = gerarComprovanteAbertura(valor, userName, dataHora);

                            hideLoading();

                            // Perguntar se deseja imprimir ou enviar por WhatsApp
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(this)
                                        .setTitle("Caixa Aberto com Sucesso!")
                                        .setMessage("Caixa: " + caixaNominalSelecionadoNome
                                                + "\nFundo de Caixa: R$ " + FormatUtils.formatMoney(valor)
                                                + "\nOperador: " + userName
                                                + "\nData/Hora: " + dataHora
                                                + "\n\nDeseja imprimir ou enviar o comprovante?")
                                        .setPositiveButton("Imprimir", (d2, w2) -> {
                                            imprimirComprovante(comprovanteAbertura);
                                            loadCaixa();
                                        })
                                        .setNeutralButton("WhatsApp", (d2, w2) -> {
                                            enviarWhatsApp(comprovanteAbertura);
                                            loadCaixa();
                                        })
                                        .setNegativeButton("Fechar", (d2, w2) -> {
                                            loadCaixa();
                                        })
                                        .setCancelable(false)
                                        .show();
                            });
                        } catch (Exception e) {
                            hideLoading();
                            showErrorFromException(e, ErrorHandler.CTX_CAIXA);
                        }
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void fecharCaixa() {
        showConfirm("Fechar Caixa", "Deseja fechar o caixa atual?", () -> {
            showLoading("Fechando caixa...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();

                    SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                    String userName = prefs.getString("user_nome", "");

                    // ==================== COLETA DE DADOS DETALHADOS ====================

                    Statement stmt = conn.createStatement();

                    // 1. Dados do caixa (abertura)
                    ResultSet rs = stmt.executeQuery("SELECT c.*, u.nome as usuario_nome FROM caixa c "
                            + "LEFT JOIN usuarios u ON c.usuario_id = u.id WHERE c.id = " + caixaId);
                    double valorAbertura = 0;
                    String dataAbertura = "";
                    String operadorAbertura = "";
                    if (rs.next()) {
                        valorAbertura = rs.getDouble("valor_abertura");
                        dataAbertura = FormatUtils.formatDate(rs.getString("data_abertura"));
                        operadorAbertura = rs.getString("usuario_nome") != null ? rs.getString("usuario_nome") : "";
                    }
                    rs.close();

                    // 2. Total de vendas finalizadas
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(total_liquido),0) as total, "
                            + "COALESCE(COUNT(*),0) as qtd FROM vendas WHERE caixa_id = " + caixaId
                            + " AND status = 'finalizada'");
                    double totalVendas = 0;
                    int qtdVendas = 0;
                    if (rs.next()) {
                        totalVendas = rs.getDouble("total");
                        qtdVendas = rs.getInt("qtd");
                    }
                    rs.close();

                    // 3. Total de vendas canceladas
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(total_liquido),0) as total, "
                            + "COALESCE(COUNT(*),0) as qtd FROM vendas WHERE caixa_id = " + caixaId
                            + " AND status = 'cancelada'");
                    double totalCanceladas = 0;
                    int qtdCanceladas = 0;
                    if (rs.next()) {
                        totalCanceladas = rs.getDouble("total");
                        qtdCanceladas = rs.getInt("qtd");
                    }
                    rs.close();

                    // 4. Total de descontos concedidos
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(desconto_valor),0) as total "
                            + "FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                    double totalDescontos = 0;
                    if (rs.next()) totalDescontos = rs.getDouble("total");
                    rs.close();

                    // 5. Total de acrescimos
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(acrescimo_valor),0) as total "
                            + "FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                    double totalAcrescimos = 0;
                    if (rs.next()) totalAcrescimos = rs.getDouble("total");
                    rs.close();

                    // 6. Total bruto (subtotal dos itens)
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(total_bruto),0) as total "
                            + "FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                    double totalBruto = 0;
                    if (rs.next()) totalBruto = rs.getDouble("total");
                    rs.close();

                    // 7. Detalhamento por forma de pagamento
                    rs = stmt.executeQuery("SELECT fp.descricao, COALESCE(SUM(pv.valor),0) as total, "
                            + "COUNT(pv.id) as qtd "
                            + "FROM pagamentos_venda pv "
                            + "JOIN formas_pagamento fp ON pv.forma_pagamento_id = fp.id "
                            + "JOIN vendas v ON pv.venda_id = v.id "
                            + "WHERE v.caixa_id = " + caixaId + " AND v.status = 'finalizada' "
                            + "GROUP BY fp.id, fp.descricao ORDER BY total DESC");
                    List<String[]> formasPagamento = new ArrayList<>();
                    while (rs.next()) {
                        formasPagamento.add(new String[]{
                                rs.getString("descricao") != null ? rs.getString("descricao") : "N/A",
                                FormatUtils.formatMoney(rs.getDouble("total")),
                                String.valueOf(rs.getInt("qtd"))
                        });
                    }
                    rs.close();

                    // 8. Total de vales/debitos
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(valor),0) as total, "
                            + "COALESCE(COUNT(*),0) as qtd FROM vales_debito WHERE caixa_id = " + caixaId);
                    double totalVales = 0;
                    int qtdVales = 0;
                    if (rs.next()) {
                        totalVales = rs.getDouble("total");
                        qtdVales = rs.getInt("qtd");
                    }
                    rs.close();

                    // 9. Lista detalhada de vales/debitos
                    rs = stmt.executeQuery("SELECT vd.descricao, vd.valor, "
                            + "COALESCE(cc.nome, 'Sem centro') AS centro_custo_nome "
                            + "FROM vales_debito vd LEFT JOIN centros_custo cc ON vd.centro_custo_id = cc.id "
                            + "WHERE vd.caixa_id = " + caixaId + " ORDER BY vd.id");
                    List<String[]> listaVales = new ArrayList<>();
                    while (rs.next()) {
                        listaVales.add(new String[]{
                                rs.getString("descricao") != null ? rs.getString("descricao") : "",
                                FormatUtils.formatMoney(rs.getDouble("valor")),
                                rs.getString("centro_custo_nome")
                        });
                    }
                    rs.close();

                    // 10. Total de troco devolvido
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(troco),0) as total "
                            + "FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                    double totalTroco = 0;
                    if (rs.next()) totalTroco = rs.getDouble("total");
                    rs.close();

                    // 11. Ticket medio
                    double ticketMedio = qtdVendas > 0 ? totalVendas / qtdVendas : 0;

                    // 12. Quantidade total de itens vendidos
                    rs = stmt.executeQuery("SELECT COALESCE(SUM(iv.quantidade),0) as total "
                            + "FROM itens_venda iv "
                            + "JOIN vendas v ON iv.venda_id = v.id "
                            + "WHERE v.caixa_id = " + caixaId + " AND v.status = 'finalizada'");
                    double qtdItensVendidos = 0;
                    if (rs.next()) qtdItensVendidos = rs.getDouble("total");
                    rs.close();

                    // 13. Produtos mais vendidos (Top 10)
                    rs = stmt.executeQuery("SELECT iv.descricao_produto, SUM(iv.quantidade) as qtd, "
                            + "SUM(iv.total) as total "
                            + "FROM itens_venda iv "
                            + "JOIN vendas v ON iv.venda_id = v.id "
                            + "WHERE v.caixa_id = " + caixaId + " AND v.status = 'finalizada' "
                            + "GROUP BY iv.descricao_produto ORDER BY qtd DESC LIMIT 10");
                    List<String[]> topProdutos = new ArrayList<>();
                    while (rs.next()) {
                        topProdutos.add(new String[]{
                                rs.getString("descricao_produto") != null ? rs.getString("descricao_produto") : "",
                                FormatUtils.formatQuantidade(rs.getDouble("qtd")),
                                FormatUtils.formatMoney(rs.getDouble("total"))
                        });
                    }
                    rs.close();

                    // 14. Vendas por vendedor
                    rs = stmt.executeQuery("SELECT COALESCE(vd.nome, 'Sem vendedor') as vendedor, "
                            + "COUNT(v.id) as qtd, COALESCE(SUM(v.total_liquido),0) as total "
                            + "FROM vendas v "
                            + "LEFT JOIN vendedores vd ON v.vendedor_id = vd.id "
                            + "WHERE v.caixa_id = " + caixaId + " AND v.status = 'finalizada' "
                            + "GROUP BY vd.id, vd.nome ORDER BY total DESC");
                    List<String[]> vendasPorVendedor = new ArrayList<>();
                    while (rs.next()) {
                        vendasPorVendedor.add(new String[]{
                                rs.getString("vendedor"),
                                String.valueOf(rs.getInt("qtd")),
                                FormatUtils.formatMoney(rs.getDouble("total"))
                        });
                    }
                    rs.close();

                    // 15. Contas a receber geradas neste caixa
                    double totalContasReceber = 0;
                    int qtdContasReceber = 0;
                    try {
                        rs = stmt.executeQuery("SELECT COALESCE(SUM(cr.valor_original),0) as total, "
                                + "COALESCE(COUNT(*),0) as qtd "
                                + "FROM contas_receber cr "
                                + "JOIN vendas v ON cr.venda_id = v.id "
                                + "WHERE v.caixa_id = " + caixaId);
                        if (rs.next()) {
                            totalContasReceber = rs.getDouble("total");
                            qtdContasReceber = rs.getInt("qtd");
                        }
                        rs.close();
                    } catch (Exception ignored) {}

                    // 16. Total de taxas de entrega
                    double totalTaxasEntrega = 0;
                    int qtdEntregas = 0;
                    try {
                        rs = stmt.executeQuery("SELECT COALESCE(SUM(taxa_entrega),0) as total, "
                                + "COALESCE(SUM(CASE WHEN para_entrega = 1 THEN 1 ELSE 0 END),0) as qtd "
                                + "FROM vendas WHERE caixa_id = " + caixaId + " AND status = 'finalizada'");
                        if (rs.next()) {
                            totalTaxasEntrega = rs.getDouble("total");
                            qtdEntregas = rs.getInt("qtd");
                        }
                        rs.close();
                    } catch (Exception ignored) {}

                    stmt.close();

                    // ==================== CALCULAR VALOR DE FECHAMENTO ====================
                    double valorFechamento = valorAbertura + totalVendas - totalVales;
                    String dataFechamento = FormatUtils.getCurrentDateTime();

                    // ==================== ATUALIZAR BANCO ====================
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE caixa SET data_fechamento = NOW(), valor_fechamento = ?, status = 'fechado' WHERE id = ?");
                    ps.setDouble(1, valorFechamento);
                    ps.setInt(2, caixaId);
                    ps.executeUpdate();
                    ps.close();

                    // ==================== GERAR COMPROVANTE DETALHADO ====================
                    String comprovanteFechamento = gerarComprovanteFechamento(
                            caixaId, valorAbertura, dataAbertura, operadorAbertura,
                            valorFechamento, dataFechamento, userName,
                            totalVendas, qtdVendas, totalCanceladas, qtdCanceladas,
                            totalBruto, totalDescontos, totalAcrescimos, totalTroco,
                            ticketMedio, qtdItensVendidos,
                            formasPagamento, totalVales, qtdVales, listaVales,
                            topProdutos, vendasPorVendedor,
                            totalContasReceber, qtdContasReceber,
                            totalTaxasEntrega, qtdEntregas);

                    PreparedStatement psRelatorio = conn.prepareStatement(
                            "UPDATE caixa SET relatorio_fechamento=? WHERE id=?");
                    psRelatorio.setString(1, comprovanteFechamento);
                    psRelatorio.setInt(2, caixaId);
                    psRelatorio.executeUpdate();
                    psRelatorio.close();

                    hideLoading();

                    // Variáveis final para uso no lambda
                    final int fQtdVendas = qtdVendas;
                    final double fTotalVendas = totalVendas;
                    final int fQtdVales = qtdVales;
                    final double fTotalVales = totalVales;
                    final double fValorFechamento = valorFechamento;

                    // Perguntar se deseja imprimir ou enviar por WhatsApp
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Caixa Fechado com Sucesso!")
                                .setMessage("Caixa #" + caixaId
                                        + "\nValor Fechamento: R$ " + FormatUtils.formatMoney(fValorFechamento)
                                        + "\nVendas: " + fQtdVendas + " (R$ " + FormatUtils.formatMoney(fTotalVendas) + ")"
                                        + "\nVales: " + fQtdVales + " (R$ " + FormatUtils.formatMoney(fTotalVales) + ")"
                                        + "\n\nDeseja imprimir ou enviar o relatorio?")
                                .setPositiveButton("Imprimir", (d2, w2) -> {
                                    imprimirComprovante(comprovanteFechamento);
                                    loadCaixa();
                                })
                                .setNeutralButton("WhatsApp", (d2, w2) -> {
                                    enviarWhatsApp(comprovanteFechamento);
                                    loadCaixa();
                                })
                                .setNegativeButton("Fechar", (d2, w2) -> {
                                    loadCaixa();
                                })
                                .setCancelable(false)
                                .show();
                    });
                } catch (Exception e) {
                    hideLoading();
                    showErrorFromException(e, ErrorHandler.CTX_CAIXA);
                }
            }).start();
        });
    }

    // ==================== COMPROVANTE DE ABERTURA (SIMPLES) ====================

    /**
     * Gera comprovante simples de abertura de caixa.
     * Contem: dados da empresa, valor do fundo de caixa, operador, data/hora e espaco para assinatura.
     */
    private String gerarComprovanteAbertura(double valorAbertura, String operador, String dataHora) {
        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        String line = repeat("-", colunas);
        StringBuilder sb = new StringBuilder();

        // Cabecalho da empresa
        String nomeEmpresa = getEmpresaInfo("nome");
        String cnpjEmpresa = getEmpresaInfo("cnpj");

        if (!nomeEmpresa.isEmpty()) {
            sb.append(center(nomeEmpresa, colunas)).append("\n");
        }
        if (!cnpjEmpresa.isEmpty()) {
            sb.append(center("CNPJ: " + cnpjEmpresa, colunas)).append("\n");
        }
        sb.append(line).append("\n");

        // Titulo
        sb.append(center("COMPROVANTE DE ABERTURA", colunas)).append("\n");
        sb.append(center("DE CAIXA", colunas)).append("\n");
        sb.append(line).append("\n");

        // Dados da abertura
        sb.append("Data/Hora: " + dataHora).append("\n");
        sb.append("Operador: " + (operador != null && !operador.isEmpty() ? operador : "N/A")).append("\n");
        sb.append(line).append("\n");

        // Valor do fundo de caixa
        sb.append("\n");
        sb.append(center("FUNDO DE CAIXA", colunas)).append("\n");
        sb.append(center("R$ " + FormatUtils.formatMoney(valorAbertura), colunas)).append("\n");
        sb.append("\n");
        sb.append(line).append("\n");

        // Espaco para assinatura
        sb.append("\n\n\n");
        sb.append(center("_______________________________", colunas)).append("\n");
        sb.append(center("Assinatura do Operador", colunas)).append("\n");
        sb.append("\n");
        sb.append(line).append("\n");

        // Rodape
        sb.append(center("PDV Pro v8.0.20.2", colunas)).append("\n");

        return sb.toString();
    }

    // ==================== COMPROVANTE DE FECHAMENTO (DETALHADO) ====================

    /**
     * Gera comprovante detalhado e fiel de fechamento de caixa.
     * Contem todas as informacoes financeiras do periodo.
     */
    private String gerarComprovanteFechamento(
            int caixaId, double valorAbertura, String dataAbertura, String operadorAbertura,
            double valorFechamento, String dataFechamento, String operadorFechamento,
            double totalVendas, int qtdVendas, double totalCanceladas, int qtdCanceladas,
            double totalBruto, double totalDescontos, double totalAcrescimos, double totalTroco,
            double ticketMedio, double qtdItensVendidos,
            List<String[]> formasPagamento, double totalVales, int qtdVales, List<String[]> listaVales,
            List<String[]> topProdutos, List<String[]> vendasPorVendedor,
            double totalContasReceber, int qtdContasReceber,
            double totalTaxasEntrega, int qtdEntregas) {

        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        String line = repeat("-", colunas);
        String doubleLine = repeat("=", colunas);
        StringBuilder sb = new StringBuilder();

        // ========== CABECALHO DA EMPRESA ==========
        String nomeEmpresa = getEmpresaInfo("nome");
        String cnpjEmpresa = getEmpresaInfo("cnpj");
        String enderecoEmpresa = getEmpresaInfo("endereco");
        String telefoneEmpresa = getEmpresaInfo("telefone");

        if (!nomeEmpresa.isEmpty()) {
            sb.append(center(nomeEmpresa, colunas)).append("\n");
        }
        if (!cnpjEmpresa.isEmpty()) {
            sb.append(center("CNPJ: " + cnpjEmpresa, colunas)).append("\n");
        }
        if (!enderecoEmpresa.isEmpty()) {
            sb.append(center(enderecoEmpresa, colunas)).append("\n");
        }
        if (!telefoneEmpresa.isEmpty()) {
            sb.append(center("Tel: " + telefoneEmpresa, colunas)).append("\n");
        }
        sb.append(doubleLine).append("\n");

        // ========== TITULO ==========
        sb.append(center("RELATORIO DE FECHAMENTO", colunas)).append("\n");
        sb.append(center("DE CAIXA", colunas)).append("\n");
        sb.append(center("Caixa #" + caixaId, colunas)).append("\n");
        sb.append(doubleLine).append("\n");

        // ========== DADOS DO CAIXA ==========
        sb.append("ABERTURA:").append("\n");
        sb.append("  Data/Hora: " + dataAbertura).append("\n");
        sb.append("  Operador: " + (operadorAbertura != null && !operadorAbertura.isEmpty() ? operadorAbertura : "N/A")).append("\n");
        sb.append("  Fundo de Caixa: R$ " + FormatUtils.formatMoney(valorAbertura)).append("\n");
        sb.append(line).append("\n");
        sb.append("FECHAMENTO:").append("\n");
        sb.append("  Data/Hora: " + dataFechamento).append("\n");
        sb.append("  Operador: " + (operadorFechamento != null && !operadorFechamento.isEmpty() ? operadorFechamento : "N/A")).append("\n");
        sb.append(doubleLine).append("\n");

        // ========== RESUMO GERAL DE VENDAS ==========
        sb.append(center("RESUMO GERAL DE VENDAS", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append("Vendas Finalizadas: " + qtdVendas).append("\n");
        sb.append("Vendas Canceladas: " + qtdCanceladas).append("\n");
        sb.append("Qtd Itens Vendidos: " + FormatUtils.formatQuantidade(qtdItensVendidos)).append("\n");
        sb.append("Ticket Medio: R$ " + FormatUtils.formatMoney(ticketMedio)).append("\n");
        sb.append(line).append("\n");
        sb.append(rightAlign("Subtotal (Bruto): R$ " + FormatUtils.formatMoney(totalBruto), colunas)).append("\n");
        if (totalDescontos > 0) {
            sb.append(rightAlign("(-) Descontos: R$ " + FormatUtils.formatMoney(totalDescontos), colunas)).append("\n");
        }
        if (totalAcrescimos > 0) {
            sb.append(rightAlign("(+) Acrescimos: R$ " + FormatUtils.formatMoney(totalAcrescimos), colunas)).append("\n");
        }
        sb.append(rightAlign("TOTAL VENDAS: R$ " + FormatUtils.formatMoney(totalVendas), colunas)).append("\n");
        if (totalCanceladas > 0) {
            sb.append(rightAlign("Total Canceladas: R$ " + FormatUtils.formatMoney(totalCanceladas), colunas)).append("\n");
        }
        if (totalTroco > 0) {
            sb.append(rightAlign("Total Troco: R$ " + FormatUtils.formatMoney(totalTroco), colunas)).append("\n");
        }
        sb.append(doubleLine).append("\n");

        // ========== DETALHAMENTO POR FORMA DE PAGAMENTO ==========
        sb.append(center("FORMAS DE PAGAMENTO", colunas)).append("\n");
        sb.append(line).append("\n");
        if (formasPagamento.isEmpty()) {
            sb.append(center("Nenhum pagamento registrado", colunas)).append("\n");
        } else {
            double totalFormas = 0;
            for (String[] fp : formasPagamento) {
                String forma = fp[0];
                String valor = fp[1];
                String qtd = fp[2];
                sb.append(forma + " (" + qtd + "x)").append("\n");
                sb.append(rightAlign("R$ " + valor, colunas)).append("\n");
                try {
                    totalFormas += FormatUtils.parseMoney(valor);
                } catch (Exception ignored) {}
            }
            sb.append(line).append("\n");
            sb.append(rightAlign("TOTAL: R$ " + FormatUtils.formatMoney(totalFormas), colunas)).append("\n");
        }
        sb.append(doubleLine).append("\n");

        // ========== ENTREGAS ==========
        if (qtdEntregas > 0) {
            sb.append(center("ENTREGAS", colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append("Qtd Entregas: " + qtdEntregas).append("\n");
            sb.append("Total Taxas Entrega: R$ " + FormatUtils.formatMoney(totalTaxasEntrega)).append("\n");
            sb.append(doubleLine).append("\n");
        }

        // ========== VALES E DEBITOS ==========
        sb.append(center("VALES E DEBITOS", colunas)).append("\n");
        sb.append(line).append("\n");
        if (listaVales.isEmpty()) {
            sb.append(center("Nenhum vale/debito registrado", colunas)).append("\n");
        } else {
            for (String[] vale : listaVales) {
                sb.append("  " + vale[0]).append("\n");
                if (vale.length > 2 && vale[2] != null) {
                    sb.append("  Centro: " + vale[2]).append("\n");
                }
                sb.append(rightAlign("R$ " + vale[1], colunas)).append("\n");
            }
            sb.append(line).append("\n");
            sb.append(rightAlign("TOTAL VALES: R$ " + FormatUtils.formatMoney(totalVales) + " (" + qtdVales + "x)", colunas)).append("\n");
        }
        sb.append(doubleLine).append("\n");

        // ========== CONTAS A RECEBER ==========
        if (qtdContasReceber > 0) {
            sb.append(center("CONTAS A RECEBER", colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append("Qtd Contas Geradas: " + qtdContasReceber).append("\n");
            sb.append("Total Contas: R$ " + FormatUtils.formatMoney(totalContasReceber)).append("\n");
            sb.append(doubleLine).append("\n");
        }

        // ========== VENDAS POR VENDEDOR ==========
        if (!vendasPorVendedor.isEmpty()) {
            sb.append(center("VENDAS POR VENDEDOR", colunas)).append("\n");
            sb.append(line).append("\n");
            for (String[] vv : vendasPorVendedor) {
                sb.append(vv[0] + " (" + vv[1] + " vendas)").append("\n");
                sb.append(rightAlign("R$ " + vv[2], colunas)).append("\n");
            }
            sb.append(doubleLine).append("\n");
        }

        // ========== TOP 10 PRODUTOS MAIS VENDIDOS ==========
        if (!topProdutos.isEmpty()) {
            sb.append(center("TOP PRODUTOS VENDIDOS", colunas)).append("\n");
            sb.append(line).append("\n");
            int pos = 1;
            for (String[] prod : topProdutos) {
                String descProd = prod[0];
                if (descProd.length() > (colunas - 8)) {
                    descProd = descProd.substring(0, colunas - 8);
                }
                sb.append(pos + ". " + descProd).append("\n");
                sb.append(rightAlign("Qtd: " + prod[1] + " | R$ " + prod[2], colunas)).append("\n");
                pos++;
            }
            sb.append(doubleLine).append("\n");
        }

        // ========== APURACAO FINAL DO CAIXA ==========
        sb.append(center("*** APURACAO FINAL ***", colunas)).append("\n");
        sb.append(doubleLine).append("\n");
        sb.append(rightAlign("Fundo de Caixa: R$ " + FormatUtils.formatMoney(valorAbertura), colunas)).append("\n");
        sb.append(rightAlign("(+) Vendas: R$ " + FormatUtils.formatMoney(totalVendas), colunas)).append("\n");
        sb.append(rightAlign("(-) Vales/Debitos: R$ " + FormatUtils.formatMoney(totalVales), colunas)).append("\n");
        sb.append(doubleLine).append("\n");
        sb.append(rightAlign("VALOR EM CAIXA: R$ " + FormatUtils.formatMoney(valorFechamento), colunas)).append("\n");
        sb.append(doubleLine).append("\n");

        // ========== ESPACO PARA ASSINATURA ==========
        sb.append("\n\n\n");
        sb.append(center("_______________________________", colunas)).append("\n");
        sb.append(center("Assinatura do Operador", colunas)).append("\n");
        sb.append("\n\n");
        sb.append(center("_______________________________", colunas)).append("\n");
        sb.append(center("Assinatura do Gerente", colunas)).append("\n");
        sb.append("\n");
        sb.append(line).append("\n");

        // ========== RODAPE ==========
        sb.append(center("PDV Pro v8.0.20.2", colunas)).append("\n");
        sb.append(center("Relatorio gerado em:", colunas)).append("\n");
        sb.append(center(dataFechamento, colunas)).append("\n");

        return sb.toString();
    }

    // ==================== METODOS AUXILIARES ====================

    /**
     * Busca informacoes da empresa do banco de dados.
     */
    private String getEmpresaInfo(String campo) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM empresa LIMIT 1");
            String resultado = "";
            if (rs.next()) {
                switch (campo) {
                    case "nome":
                        String nomeFantasia = rs.getString("nome_fantasia");
                        String razaoSocial = rs.getString("razao_social");
                        resultado = (nomeFantasia != null && !nomeFantasia.isEmpty()) ? nomeFantasia :
                                (razaoSocial != null ? razaoSocial : "");
                        break;
                    case "cnpj":
                        resultado = rs.getString("cnpj") != null ? rs.getString("cnpj") : "";
                        break;
                    case "endereco":
                        String end = rs.getString("endereco") != null ? rs.getString("endereco") : "";
                        String num = rs.getString("numero") != null ? rs.getString("numero") : "";
                        String bairro = rs.getString("bairro") != null ? rs.getString("bairro") : "";
                        String cidade = rs.getString("cidade") != null ? rs.getString("cidade") : "";
                        String uf = rs.getString("uf") != null ? rs.getString("uf") : "";
                        if (!end.isEmpty()) {
                            resultado = end;
                            if (!num.isEmpty()) resultado += ", " + num;
                            if (!bairro.isEmpty()) resultado += " - " + bairro;
                            if (!cidade.isEmpty()) resultado += " - " + cidade;
                            if (!uf.isEmpty()) resultado += "/" + uf;
                        }
                        break;
                    case "telefone":
                        resultado = rs.getString("telefone") != null ? rs.getString("telefone") : "";
                        break;
                }
            }
            rs.close();
            stmt.close();
            return resultado;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Imprime o comprovante na impressora configurada.
     */
    private void imprimirComprovante(String comprovante) {
        new Thread(() -> {
            PrinterManager pm = new PrinterManager(this);
            if (pm.isImpressoraConfigurada()) {
                boolean ok = pm.imprimirTexto(comprovante);
                if (ok) {
                    showToast("Comprovante impresso com sucesso!");
                } else {
                    showToast("Falha ao imprimir. Verifique a impressora.");
                }
            } else {
                showToast("Impressora nao configurada");
            }
        }).start();
    }

    /**
     * Envia o comprovante via WhatsApp.
     */
    private void enviarWhatsApp(String comprovante) {
        runOnUiThread(() -> WhatsAppHelper.enviarCupomManual(this, comprovante, null));
    }

    private void abrirReimpressaoFechamento() {
        showLoading("Carregando fechamentos...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT c.id,DATE(c.data_fechamento) data_ref,c.data_fechamento,"
                                + "COALESCE(u.nome,'Sem usuario') usuario,c.valor_fechamento "
                                + "FROM caixa c LEFT JOIN usuarios u ON c.usuario_id=u.id "
                                + "WHERE c.status='fechado' ORDER BY c.id DESC LIMIT 500");
                List<String[]> boxes = new ArrayList<>();
                List<String> dates = new ArrayList<>();
                dates.add("Todas as datas");
                while (rs.next()) {
                    String date = rs.getString("data_ref");
                    if (date != null && !dates.contains(date)) dates.add(date);
                    boxes.add(new String[]{String.valueOf(rs.getInt("id")), date,
                            "Caixa #" + rs.getInt("id") + " | "
                                    + FormatUtils.formatDate(rs.getString("data_fechamento")) + " | "
                                    + rs.getString("usuario") + " | R$ "
                                    + FormatUtils.formatMoney(rs.getDouble("valor_fechamento"))});
                }
                rs.close(); stmt.close(); hideLoading();
                runOnUiThread(() -> mostrarReimpressaoFechamento(boxes, dates));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    private void mostrarReimpressaoFechamento(List<String[]> boxes, List<String> dates) {
        if (boxes.isEmpty()) { showInfo("Reimpressao", "Nenhum caixa fechado foi encontrado."); return; }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);
        TextView dateLabel = new TextView(this); dateLabel.setText("Data do fechamento");
        dateLabel.setTypeface(null, android.graphics.Typeface.BOLD); layout.addView(dateLabel);
        Spinner dateSpinner = new Spinner(this);
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dates);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dateSpinner.setAdapter(dateAdapter); layout.addView(dateSpinner);
        TextView boxLabel = new TextView(this); boxLabel.setText("Numero do caixa");
        boxLabel.setTypeface(null, android.graphics.Typeface.BOLD); boxLabel.setPadding(0, pad, 0, 0);
        layout.addView(boxLabel);
        Spinner boxSpinner = new Spinner(this); layout.addView(boxSpinner);
        List<Integer> filteredIds = new ArrayList<>();
        List<String> filteredLabels = new ArrayList<>();
        ArrayAdapter<String> boxAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, filteredLabels);
        boxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        boxSpinner.setAdapter(boxAdapter);

        Runnable refresh = () -> {
            String selectedDate = dateSpinner.getSelectedItemPosition() == 0
                    ? null : dates.get(dateSpinner.getSelectedItemPosition());
            filteredIds.clear(); filteredLabels.clear();
            for (String[] box : boxes) {
                if (selectedDate == null || selectedDate.equals(box[1])) {
                    filteredIds.add(Integer.parseInt(box[0])); filteredLabels.add(box[2]);
                }
            }
            boxAdapter.notifyDataSetChanged();
        };
        dateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { refresh.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        refresh.run();

        new AlertDialog.Builder(this).setTitle("Reimprimir Fechamento")
                .setView(layout)
                .setPositiveButton("Reimprimir", (d, w) -> {
                    int pos = boxSpinner.getSelectedItemPosition();
                    if (pos >= 0 && pos < filteredIds.size()) reimprimirFechamento(filteredIds.get(pos));
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void reimprimirFechamento(int id) {
        UserActionLogger.log(this, "REIMPRIMIR_FECHAMENTO", "Caixa", "Caixa #" + id);
        showLoading("Preparando fechamento #" + id + "...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT c.*,COALESCE(u.nome,'Sem usuario') usuario FROM caixa c "
                                + "LEFT JOIN usuarios u ON c.usuario_id=u.id WHERE c.id=? AND c.status='fechado'");
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new SQLException("Fechamento nao encontrado.");
                String report = rs.getString("relatorio_fechamento");
                if (report == null || report.trim().isEmpty()) {
                    double sales = 0, vouchers = 0;
                    Statement totals = conn.createStatement();
                    ResultSet totalRs = totals.executeQuery(
                            "SELECT COALESCE(SUM(total_liquido),0) FROM vendas WHERE caixa_id=" + id + " AND status='finalizada'");
                    if (totalRs.next()) sales = totalRs.getDouble(1); totalRs.close();
                    totalRs = totals.executeQuery("SELECT COALESCE(SUM(valor),0) FROM vales_debito WHERE caixa_id=" + id);
                    if (totalRs.next()) vouchers = totalRs.getDouble(1); totalRs.close(); totals.close();
                    report = "REIMPRESSAO - FECHAMENTO DE CAIXA\n================================\n"
                            + "Caixa #" + id + "\nOperador: " + rs.getString("usuario")
                            + "\nAbertura: " + FormatUtils.formatDate(rs.getString("data_abertura"))
                            + "\nFechamento: " + FormatUtils.formatDate(rs.getString("data_fechamento"))
                            + "\nFundo: R$ " + FormatUtils.formatMoney(rs.getDouble("valor_abertura"))
                            + "\nVendas: R$ " + FormatUtils.formatMoney(sales)
                            + "\nVales: R$ " + FormatUtils.formatMoney(vouchers)
                            + "\nTOTAL: R$ " + FormatUtils.formatMoney(rs.getDouble("valor_fechamento"));
                }
                rs.close(); ps.close(); hideLoading();
                imprimirComprovante(report);
            } catch (Exception e) {
                hideLoading(); showErrorFromException(e, ErrorHandler.CTX_IMPRESSAO);
            }
        }).start();
    }

    private void adicionarVale() {
        showLoading("Carregando centros de custos...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                List<Integer> ids = new ArrayList<>();
                List<String> nomes = new ArrayList<>();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, nome FROM centros_custo WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    nomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                if (ids.isEmpty()) {
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO centros_custo (nome, ativo) VALUES ('Geral', 1)",
                            Statement.RETURN_GENERATED_KEYS);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    if (keys.next()) {
                        ids.add(keys.getInt(1));
                        nomes.add("Geral");
                    }
                    keys.close();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> mostrarDialogVale(ids, nomes));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CAIXA);
            }
        }).start();
    }

    private void solicitarExclusaoVale(ValeDebito vale) {
        Runnable confirmar = () -> showConfirm("Excluir lancamento",
                "Deseja excluir o vale/debito \"" + vale.getDescricao() + "\" no valor de R$ "
                        + FormatUtils.formatMoney(vale.getValor()) + "?\n\nEsta acao ficara registrada na auditoria.",
                () -> excluirVale(vale.getId()));
        if (PermissionManager.getInstance(this).isAdministrador()) {
            confirmar.run();
        } else {
            AdminUserPasswordDialog.show(this, "Excluir lancamento do caixa",
                    "Seu perfil nao e Administrador. Informe a senha de um usuario Administrador para continuar.",
                    confirmar);
        }
    }

    private void excluirVale(int valeId) {
        showLoading("Excluindo lancamento...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM vales_debito WHERE id=? AND caixa_id=?");
                ps.setInt(1, valeId);
                ps.setInt(2, caixaId);
                int rows = ps.executeUpdate();
                ps.close();
                hideLoading();
                if (rows > 0) {
                    showToast("Lancamento excluido com autorizacao administrativa.");
                    loadCaixa();
                } else {
                    showError("O lancamento nao foi encontrado ou ja foi excluido.");
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    private void mostrarDialogVale(List<Integer> centroIds, List<String> centroNomes) {
        View view = getLayoutInflater().inflate(R.layout.dialog_vale, null);
        EditText etDesc = view.findViewById(R.id.etDescricaoVale);
        EditText etValor = view.findViewById(R.id.etValorVale);
        Spinner spinner = view.findViewById(R.id.spCentroCustoVale);
        Button btnNovoCentro = view.findViewById(R.id.btnNovoCentroCustoVale);

        ArrayAdapter<String> centroAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, centroNomes);
        centroAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(centroAdapter);

        btnNovoCentro.setOnClickListener(v ->
                mostrarNovoCentroCusto(centroIds, centroNomes, centroAdapter, spinner));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Novo Vale de Debito")
                .setView(view)
                .setPositiveButton("Adicionar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String desc = etDesc.getText().toString().trim();
                    double valor = FormatUtils.parseMoney(etValor.getText().toString());
                    int posicao = spinner.getSelectedItemPosition();
                    if (desc.isEmpty() || valor <= 0 || posicao < 0 || posicao >= centroIds.size()) {
                        showError("Preencha a descricao, o valor e selecione o centro de custos.");
                        return;
                    }
                    int centroId = centroIds.get(posicao);
                    showLoading("Salvando...");
                    new Thread(() -> {
                        try {
                            DatabaseHelper db = DatabaseHelper.getInstance(this);
                            Connection conn = db.getConnection();
                            int usuarioId = getSharedPreferences("session", MODE_PRIVATE)
                                    .getInt("user_id", 0);
                            PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO vales_debito (caixa_id, centro_custo_id, usuario_id, descricao, valor) "
                                            + "VALUES (?, ?, ?, ?, ?)");
                            ps.setInt(1, caixaId);
                            ps.setInt(2, centroId);
                            if (usuarioId > 0) ps.setInt(3, usuarioId); else ps.setNull(3, java.sql.Types.INTEGER);
                            ps.setString(4, desc);
                            ps.setDouble(5, valor);
                            ps.executeUpdate();
                            ps.close();
                            hideLoading();
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                showToast("Vale adicionado!");
                                loadCaixa();
                            });
                        } catch (Exception e) {
                            hideLoading();
                            showErrorFromException(e, ErrorHandler.CTX_CAIXA);
                        }
                    }).start();
                }));
        dialog.show();
    }

    private void mostrarNovoCentroCusto(List<Integer> centroIds, List<String> centroNomes,
                                         ArrayAdapter<String> adapter, Spinner spinner) {
        EditText input = new EditText(this);
        input.setHint("Nome do centro de custos");
        input.setSingleLine(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Novo Centro de Custos")
                .setView(input)
                .setPositiveButton("Criar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String nome = input.getText().toString().trim();
                    if (nome.isEmpty()) {
                        input.setError("Informe o nome");
                        return;
                    }
                    showLoading("Criando centro de custos...");
                    new Thread(() -> {
                        try {
                            Connection conn = DatabaseHelper.getInstance(this).getConnection();
                            PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO centros_custo (nome, ativo) VALUES (?, 1)",
                                    Statement.RETURN_GENERATED_KEYS);
                            ps.setString(1, nome);
                            ps.executeUpdate();
                            ResultSet keys = ps.getGeneratedKeys();
                            int novoId = keys.next() ? keys.getInt(1) : 0;
                            keys.close();
                            ps.close();
                            if (novoId <= 0) throw new SQLException("Centro de custos nao foi criado.");
                            hideLoading();
                            final int idCriado = novoId;
                            runOnUiThread(() -> {
                                centroIds.add(idCriado);
                                centroNomes.add(nome);
                                adapter.notifyDataSetChanged();
                                spinner.setSelection(centroNomes.size() - 1);
                                dialog.dismiss();
                                showToast("Centro de custos criado!");
                            });
                        } catch (Exception e) {
                            hideLoading();
                            showErrorFromException(e, ErrorHandler.CTX_SALVAR);
                        }
                    }).start();
                }));
        dialog.show();
    }

    // ==================== UTILIDADES DE FORMATACAO ====================

    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = (width - text.length()) / 2;
        return repeat(" ", pad) + text;
    }

    private String rightAlign(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text;
        return repeat(" ", width - text.length()) + text;
    }

    private String repeat(String s, int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
