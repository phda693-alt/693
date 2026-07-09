package com.pdv.app.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.*;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.ErrorHandler;

public class PagamentoActivity extends BaseActivity {
    private TextView tvTotalVenda, tvSaldoDevedor, tvTroco;
    private TextView tvTotalPago, tvValorRestante, tvPorcentagem, tvLabelRestante;
    private ProgressBar progressPagamento;
    private LinearLayout layoutTroco, cardValorRestante;
    private RecyclerView rvPagamentos;
    private GenericAdapter<PagamentoVenda> pagAdapter;
    private Button btnAddPagamento, btnFinalizar, btnDividirPagamento, btnParaEntrega;
    private EditText etValorRecebido;
    private CheckBox cbImprimirCanhotoSenha, cbImprimirDuasViasCupom, cbExibirSenhaNoCupom;

    // v8.0.12.3 - Persistencia da opcao do canhoto de senha
    private static final String PREFS_PAGAMENTO = "pdv_pagamento_prefs";
    private static final String PREF_IMPRIMIR_CANHOTO_SENHA = "imprimir_canhoto_senha";
    private static final String PREF_IMPRIMIR_DUAS_VIAS_CUPOM = "imprimir_duas_vias_cupom";
    private static final String PREF_EXIBIR_SENHA_NO_CUPOM = "exibir_senha_no_cupom";

    // Entrega
    private LinearLayout layoutEntregaInfo;
    private TextView tvTaxaEntregaValor, tvBairroEntrega, tvEnderecoEntrega;
    private boolean paraEntrega = false;
    private double taxaEntrega = 0;
    private String bairroEntrega = "";
    private String enderecoEntrega = "";
    private int bairroEntregaId = 0;

    private List<PagamentoVenda> pagamentos = new ArrayList<>();
    private List<FormaPagamento> formasList = new ArrayList<>();
    private double totalLiquido;
    private double totalLiquidoOriginal; // sem taxa entrega

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pagamento);

        tvTotalVenda = findViewById(R.id.tvTotalVenda);
        tvSaldoDevedor = findViewById(R.id.tvSaldoDevedor);
        tvTroco = findViewById(R.id.tvTroco);
        tvTotalPago = findViewById(R.id.tvTotalPago);
        tvValorRestante = findViewById(R.id.tvValorRestante);
        tvPorcentagem = findViewById(R.id.tvPorcentagem);
        tvLabelRestante = findViewById(R.id.tvLabelRestante);
        progressPagamento = findViewById(R.id.progressPagamento);
        layoutTroco = findViewById(R.id.layoutTroco);
        cardValorRestante = findViewById(R.id.cardValorRestante);
        rvPagamentos = findViewById(R.id.rvPagamentos);
        btnAddPagamento = findViewById(R.id.btnAddPagamento);
        btnDividirPagamento = findViewById(R.id.btnDividirPagamento);
        btnParaEntrega = findViewById(R.id.btnParaEntrega);
        btnFinalizar = findViewById(R.id.btnFinalizar);
        etValorRecebido = findViewById(R.id.etValorRecebido);
        cbImprimirCanhotoSenha = findViewById(R.id.cbImprimirCanhotoSenha);
        cbImprimirDuasViasCupom = findViewById(R.id.cbImprimirDuasViasCupom);
        cbExibirSenhaNoCupom = findViewById(R.id.cbExibirSenhaNoCupom);
        inicializarPersistenciaOpcoesImpressao();

        // Entrega
        layoutEntregaInfo = findViewById(R.id.layoutEntregaInfo);
        tvTaxaEntregaValor = findViewById(R.id.tvTaxaEntregaValor);
        tvBairroEntrega = findViewById(R.id.tvBairroEntrega);
        tvEnderecoEntrega = findViewById(R.id.tvEnderecoEntrega);

        totalLiquido = getIntent().getDoubleExtra("total_liquido", 0);
        totalLiquidoOriginal = totalLiquido;
        tvTotalVenda.setText("R$ " + FormatUtils.formatMoney(totalLiquido));

        rvPagamentos.setLayoutManager(new LinearLayoutManager(this));
        pagAdapter = new GenericAdapter<>(R.layout.item_pagamento, (holder, item, pos) -> {
            holder.setText(R.id.tvForma, item.getFormaDescricao());
            holder.setText(R.id.tvValorPag, "R$ " + FormatUtils.formatMoney(item.getValor()));
            String parcTxt = item.getParcelas() > 1 ? item.getParcelas() + "x" : "A vista";
            holder.setText(R.id.tvParcelas, parcTxt);
        });
        pagAdapter.setOnItemLongClickListener((item, pos) -> {
            new AlertDialog.Builder(this)
                .setTitle("Remover Pagamento")
                .setMessage("Deseja remover o pagamento " + item.getFormaDescricao()
                    + " de R$ " + FormatUtils.formatMoney(item.getValor()) + "?")
                .setPositiveButton("Remover", (d, w) -> {
                    pagamentos.remove(pos);
                    pagAdapter.setItems(pagamentos);
                    atualizarSaldo();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });
        rvPagamentos.setAdapter(pagAdapter);

        btnAddPagamento.setOnClickListener(v -> adicionarPagamento());
        btnDividirPagamento.setOnClickListener(v -> dividirPagamento());
        btnParaEntrega.setOnClickListener(v -> toggleParaEntrega());
        btnFinalizar.setOnClickListener(v -> finalizarVenda());

        carregarFormas();
        atualizarSaldo();
    }

    /**
     * v8.0.12.5 - Mantem todas as escolhas dos checkboxes de impressao
     * mesmo ao sair da tela ou fechar e abrir o sistema novamente.
     */
    private void inicializarPersistenciaOpcoesImpressao() {
        boolean imprimirCanhoto = getSharedPreferences(PREFS_PAGAMENTO, MODE_PRIVATE)
                .getBoolean(PREF_IMPRIMIR_CANHOTO_SENHA, false);
        boolean imprimirDuasVias = getSharedPreferences(PREFS_PAGAMENTO, MODE_PRIVATE)
                .getBoolean(PREF_IMPRIMIR_DUAS_VIAS_CUPOM, false);
        boolean exibirSenhaNoCupom = getSharedPreferences(PREFS_PAGAMENTO, MODE_PRIVATE)
                .getBoolean(PREF_EXIBIR_SENHA_NO_CUPOM, false);

        if (cbImprimirCanhotoSenha != null) {
            cbImprimirCanhotoSenha.setChecked(imprimirCanhoto);
            cbImprimirCanhotoSenha.setOnCheckedChangeListener((buttonView, isChecked) -> salvarPreferenciasOpcoesImpressao());
        }
        if (cbImprimirDuasViasCupom != null) {
            cbImprimirDuasViasCupom.setChecked(imprimirDuasVias);
            cbImprimirDuasViasCupom.setOnCheckedChangeListener((buttonView, isChecked) -> salvarPreferenciasOpcoesImpressao());
        }
        if (cbExibirSenhaNoCupom != null) {
            cbExibirSenhaNoCupom.setChecked(exibirSenhaNoCupom);
            cbExibirSenhaNoCupom.setOnCheckedChangeListener((buttonView, isChecked) -> salvarPreferenciasOpcoesImpressao());
        }
    }

    private void salvarPreferenciasOpcoesImpressao() {
        getSharedPreferences(PREFS_PAGAMENTO, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_IMPRIMIR_CANHOTO_SENHA, cbImprimirCanhotoSenha != null && cbImprimirCanhotoSenha.isChecked())
                .putBoolean(PREF_IMPRIMIR_DUAS_VIAS_CUPOM, cbImprimirDuasViasCupom != null && cbImprimirDuasViasCupom.isChecked())
                .putBoolean(PREF_EXIBIR_SENHA_NO_CUPOM, cbExibirSenhaNoCupom != null && cbExibirSenhaNoCupom.isChecked())
                .apply();
    }

    @Override
    protected void onPause() {
        salvarPreferenciasOpcoesImpressao();
        super.onPause();
    }

    // =====================================================================
    // PARA ENTREGA - Selecionar bairro e aplicar taxa
    // =====================================================================

    private void toggleParaEntrega() {
        if (paraEntrega) {
            // Desativar entrega
            paraEntrega = false;
            taxaEntrega = 0;
            bairroEntrega = "";
            enderecoEntrega = "";
            bairroEntregaId = 0;
            totalLiquido = totalLiquidoOriginal;
            tvTotalVenda.setText("R$ " + FormatUtils.formatMoney(totalLiquido));
            layoutEntregaInfo.setVisibility(View.GONE);
            btnParaEntrega.setText("PARA ENTREGA");
            btnParaEntrega.setBackgroundResource(R.drawable.btn_delivery);
            atualizarSaldo();
            showToast("Entrega desativada");
            return;
        }

        // Carregar bairros disponiveis
        showLoading("Carregando bairros...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM taxa_entrega_bairro WHERE ativo = 1 ORDER BY bairro");
                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                List<Double> taxas = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    nomes.add(rs.getString("bairro"));
                    taxas.add(rs.getDouble("taxa"));
                }
                rs.close();
                stmt.close();
                hideLoading();

                if (nomes.isEmpty()) {
                    showError("Nenhum bairro cadastrado.\n\nCadastre bairros e taxas de entrega no menu principal antes de usar esta funcao.");
                    return;
                }

                // Buscar endereco do cliente
                int clienteId = getIntent().getIntExtra("cliente_id", 0);
                String clienteEndereco = "";
                if (clienteId > 0) {
                    Statement stmtCli = conn.createStatement();
                    ResultSet rsCli = stmtCli.executeQuery("SELECT endereco, numero, bairro, cidade, uf FROM clientes WHERE id = " + clienteId);
                    if (rsCli.next()) {
                        String end = safeStr(rsCli.getString("endereco"));
                        String num = safeStr(rsCli.getString("numero"));
                        String bai = safeStr(rsCli.getString("bairro"));
                        String cid = safeStr(rsCli.getString("cidade"));
                        String uf = safeStr(rsCli.getString("uf"));
                        clienteEndereco = end;
                        if (!num.isEmpty()) clienteEndereco += ", " + num;
                        if (!bai.isEmpty()) clienteEndereco += " - " + bai;
                        if (!cid.isEmpty()) clienteEndereco += " - " + cid;
                        if (!uf.isEmpty()) clienteEndereco += "/" + uf;
                    }
                    rsCli.close();
                    stmtCli.close();
                }

                final String finalEnderecoCliente = clienteEndereco;

                // Montar lista com nome + taxa
                String[] opcoes = new String[nomes.size()];
                for (int i = 0; i < nomes.size(); i++) {
                    opcoes[i] = nomes.get(i) + " - R$ " + FormatUtils.formatMoney(taxas.get(i));
                }

                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("Selecione o Bairro de Entrega")
                        .setItems(opcoes, (d, w) -> {
                            paraEntrega = true;
                            bairroEntregaId = ids.get(w);
                            bairroEntrega = nomes.get(w);
                            taxaEntrega = taxas.get(w);
                            enderecoEntrega = finalEnderecoCliente;

                            // Atualizar total com taxa
                            totalLiquido = totalLiquidoOriginal + taxaEntrega;
                            tvTotalVenda.setText("R$ " + FormatUtils.formatMoney(totalLiquido));

                            // Mostrar info entrega
                            layoutEntregaInfo.setVisibility(View.VISIBLE);
                            tvTaxaEntregaValor.setText("Taxa: R$ " + FormatUtils.formatMoney(taxaEntrega));
                            tvBairroEntrega.setText("Bairro: " + bairroEntrega);
                            if (!enderecoEntrega.isEmpty()) {
                                tvEnderecoEntrega.setVisibility(View.VISIBLE);
                                tvEnderecoEntrega.setText("Endereco: " + enderecoEntrega);
                            } else {
                                tvEnderecoEntrega.setVisibility(View.GONE);
                            }

                            btnParaEntrega.setText("CANCELAR\nENTREGA");
                            atualizarSaldo();
                            showToast("Entrega ativada - " + bairroEntrega);
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_PAGAMENTO);
            }
        }).start();
    }

    private String safeStr(String s) { return s != null ? s : ""; }

    private void carregarFormas() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM formas_pagamento WHERE ativo = 1 ORDER BY descricao");
                formasList.clear();
                while (rs.next()) {
                    FormaPagamento fp = new FormaPagamento();
                    fp.setId(rs.getInt("id"));
                    fp.setDescricao(rs.getString("descricao"));
                    fp.setTipo(rs.getString("tipo"));
                    fp.setPermiteParcelamento(rs.getInt("permite_parcelamento") == 1);
                    formasList.add(fp);
                }
                rs.close();
                stmt.close();
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_PAGAMENTO);
            }
        }).start();
    }

    private double calcularTotalPago() {
        double totalPago = 0;
        for (PagamentoVenda p : pagamentos) totalPago += p.getValor();
        return totalPago;
    }

    private void atualizarSaldo() {
        double totalPago = calcularTotalPago();
        double saldo = totalLiquido - totalPago;
        double troco = saldo < 0 ? Math.abs(saldo) : 0;
        if (saldo < 0) saldo = 0;

        tvSaldoDevedor.setText("Saldo Devedor: R$ " + FormatUtils.formatMoney(saldo));
        tvTotalPago.setText("R$ " + FormatUtils.formatMoney(totalPago));
        tvValorRestante.setText("R$ " + FormatUtils.formatMoney(saldo));

        int progresso = 0;
        if (totalLiquido > 0) {
            progresso = (int) ((totalPago / totalLiquido) * 1000);
            if (progresso > 1000) progresso = 1000;
        }
        progressPagamento.setProgress(progresso);

        int percentual = 0;
        if (totalLiquido > 0) {
            percentual = (int) ((totalPago / totalLiquido) * 100);
            if (percentual > 100) percentual = 100;
        }
        tvPorcentagem.setText(percentual + "% pago");

        if (saldo <= 0.01 && totalPago > 0) {
            tvValorRestante.setText("R$ 0.00");
            tvValorRestante.setTextColor(0xFF00E676);
            tvLabelRestante.setText("PAGO:");
            tvLabelRestante.setTextColor(0xFF00E676);
            cardValorRestante.setBackgroundResource(R.drawable.card_valor_pago);
        } else {
            tvValorRestante.setTextColor(0xFFFF5252);
            tvLabelRestante.setText("VALOR RESTANTE:");
            tvLabelRestante.setTextColor(0xFFFF5252);
            cardValorRestante.setBackgroundResource(R.drawable.card_valor_restante);
        }

        if (troco > 0.01) {
            layoutTroco.setVisibility(View.VISIBLE);
            tvTroco.setText("R$ " + FormatUtils.formatMoney(troco));
        } else {
            layoutTroco.setVisibility(View.GONE);
        }
    }

    // =====================================================================
    // ADICIONAR PAGAMENTO INDIVIDUAL (funcionalidade existente)
    // =====================================================================

    private void adicionarPagamento() {
        if (formasList.isEmpty()) {
            showError("Nenhuma forma de pagamento encontrada.\n\nCadastre formas de pagamento no menu principal antes de realizar vendas.");
            return;
        }

        double totalPago = calcularTotalPago();
        if (totalPago >= totalLiquido) {
            showError("O valor total da venda ja foi coberto.\n\nVoce ja adicionou pagamentos suficientes. Finalize a venda ou remova um pagamento para alterar.");
            return;
        }

        String[] nomes = new String[formasList.size()];
        for (int i = 0; i < formasList.size(); i++) nomes[i] = formasList.get(i).getDescricao();

        new AlertDialog.Builder(this)
                .setTitle("Forma de Pagamento")
                .setItems(nomes, (d, w) -> {
                    FormaPagamento forma = formasList.get(w);
                    showValorPagamentoDialog(forma);
                })
                .show();
    }

    private void showValorPagamentoDialog(FormaPagamento forma) {
        // Validar: Contas a Receber exige cliente informado
        if (forma.isContaReceber()) {
            int clienteId = getIntent().getIntExtra("cliente_id", 0);
            String clienteNome = getIntent().getStringExtra("cliente_nome");
            if (clienteId <= 0 || clienteNome == null || clienteNome.isEmpty()
                    || "Cliente nao informado".equals(clienteNome)) {
                showError("Para utilizar Contas a Receber, e obrigatorio selecionar um cliente na venda.\n\nVolte a tela de venda e selecione um cliente antes de usar esta forma de pagamento.");
                return;
            }

            // Verificar se ja existe um pagamento do tipo conta_receber
            for (PagamentoVenda p : pagamentos) {
                if (isFormaPagamentoContaReceber(p.getFormaPagamentoId())) {
                    showError("Ja existe um pagamento do tipo Contas a Receber nesta venda.\n\nSo e permitido um pagamento deste tipo por venda.");
                    return;
                }
            }
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_valor_pagamento, null);
        EditText etValor = view.findViewById(R.id.etValorPagamento);
        Spinner spParcelas = view.findViewById(R.id.spParcelas);
        TextView tvRestanteDialog = view.findViewById(R.id.tvRestanteDialog);

        double totalPago = calcularTotalPago();
        double saldo = totalLiquido - totalPago;
        if (saldo < 0) saldo = 0;

        tvRestanteDialog.setText("R$ " + FormatUtils.formatMoney(saldo));
        if (saldo <= 0.01) {
            tvRestanteDialog.setTextColor(0xFF00E676);
        } else {
            tvRestanteDialog.setTextColor(0xFFFF5252);
        }

        etValor.setText(FormatUtils.formatMoney(saldo > 0 ? saldo : 0));

        if (forma.isPermiteParcelamento()) {
            spParcelas.setVisibility(View.VISIBLE);
            String[] parcelas = new String[12];
            for (int i = 0; i < 12; i++) parcelas[i] = (i + 1) + "x";
            spParcelas.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, parcelas));
        } else {
            spParcelas.setVisibility(View.GONE);
        }

        String titulo = forma.getDescricao();
        if (forma.isContaReceber()) {
            titulo = forma.getDescricao() + " (" + getIntent().getStringExtra("cliente_nome") + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(view)
                .setPositiveButton("Adicionar", (d2, w2) -> {
                    double valor = FormatUtils.parseMoney(etValor.getText().toString());
                    if (valor <= 0) { showError("O valor informado e invalido.\n\nDigite um valor maior que zero."); return; }

                    PagamentoVenda pag = new PagamentoVenda();
                    pag.setFormaPagamentoId(forma.getId());
                    pag.setFormaDescricao(forma.getDescricao());
                    pag.setValor(valor);
                    pag.setParcelas(forma.isPermiteParcelamento() ? spParcelas.getSelectedItemPosition() + 1 : 1);

                    pagamentos.add(pag);
                    pagAdapter.setItems(pagamentos);
                    atualizarSaldo();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Verifica se uma forma de pagamento (pelo ID) e do tipo conta_receber.
     */
    private boolean isFormaPagamentoContaReceber(int formaPagamentoId) {
        for (FormaPagamento fp : formasList) {
            if (fp.getId() == formaPagamentoId && fp.isContaReceber()) return true;
        }
        return false;
    }

    /**
     * Verifica se algum pagamento na lista e do tipo Contas a Receber.
     */
    private boolean temPagamentoContaReceber() {
        for (PagamentoVenda p : pagamentos) {
            if (isFormaPagamentoContaReceber(p.getFormaPagamentoId())) return true;
        }
        return false;
    }

    /**
     * Retorna o valor total dos pagamentos do tipo Contas a Receber.
     */
    private double getValorContaReceber() {
        double total = 0;
        for (PagamentoVenda p : pagamentos) {
            if (isFormaPagamentoContaReceber(p.getFormaPagamentoId())) {
                total += p.getValor();
            }
        }
        return total;
    }

    // =====================================================================
    // DIVIDIR PAGAMENTO AUTOMATICAMENTE (nova funcionalidade)
    // =====================================================================

    private void dividirPagamento() {
        if (formasList.isEmpty()) {
            showError("Nenhuma forma de pagamento encontrada.\n\nCadastre formas de pagamento no menu principal antes de realizar vendas.");
            return;
        }

        if (formasList.size() < 2) {
            showError("E necessario ter pelo menos 2 formas de pagamento cadastradas para usar a divisao automatica.");
            return;
        }

        double totalPago = calcularTotalPago();
        double saldoRestante = totalLiquido - totalPago;
        if (saldoRestante <= 0.01) {
            showError("O valor total da venda ja foi coberto.\n\nNao ha valor a dividir.");
            return;
        }

        showDividirDialog(saldoRestante);
    }

    private void showDividirDialog(double valorADividir) {
        View view = getLayoutInflater().inflate(R.layout.dialog_dividir_pagamento, null);

        TextView tvValorDividir = view.findViewById(R.id.tvValorDividir);
        RadioGroup rgTipoDivisao = view.findViewById(R.id.rgTipoDivisao);
        RadioButton rbIgual = view.findViewById(R.id.rbIgual);
        RadioButton rbPersonalizado = view.findViewById(R.id.rbPersonalizado);
        LinearLayout layoutFormas = view.findViewById(R.id.layoutFormasDivisao);
        LinearLayout layoutResumo = view.findViewById(R.id.layoutResumoDivisao);
        LinearLayout layoutResumoItens = view.findViewById(R.id.layoutResumoItens);
        TextView tvTotalDistribuido = view.findViewById(R.id.tvTotalDistribuido);
        LinearLayout layoutDiferenca = view.findViewById(R.id.layoutDiferenca);
        TextView tvDiferenca = view.findViewById(R.id.tvDiferenca);

        tvValorDividir.setText("R$ " + FormatUtils.formatMoney(valorADividir));

        // Listas para controlar os checkboxes e campos de valor
        List<CheckBox> checkboxes = new ArrayList<>();
        List<EditText> editTexts = new ArrayList<>();
        List<FormaPagamento> formasRef = new ArrayList<>();

        LayoutInflater inflater = getLayoutInflater();

        // Criar um item para cada forma de pagamento
        for (int i = 0; i < formasList.size(); i++) {
            FormaPagamento forma = formasList.get(i);
            View itemView = inflater.inflate(R.layout.item_forma_divisao, layoutFormas, false);

            CheckBox cb = itemView.findViewById(R.id.cbForma);
            EditText et = itemView.findViewById(R.id.etValorForma);

            cb.setText(forma.getDescricao());
            cb.setTag(i);

            checkboxes.add(cb);
            editTexts.add(et);
            formasRef.add(forma);

            layoutFormas.addView(itemView);
        }

        // Runnable para recalcular a divisao
        Runnable recalcularDivisao = new Runnable() {
            @Override
            public void run() {
                List<Integer> selecionados = new ArrayList<>();
                for (int i = 0; i < checkboxes.size(); i++) {
                    if (checkboxes.get(i).isChecked()) {
                        selecionados.add(i);
                    }
                }

                if (selecionados.isEmpty()) {
                    layoutResumo.setVisibility(View.GONE);
                    for (EditText et : editTexts) {
                        et.setVisibility(View.GONE);
                        et.setEnabled(false);
                    }
                    return;
                }

                layoutResumo.setVisibility(View.VISIBLE);

                boolean isIgual = rbIgual.isChecked();

                if (isIgual) {
                    // Divisao em partes iguais
                    int numFormas = selecionados.size();
                    double valorPorForma = Math.floor((valorADividir / numFormas) * 100.0) / 100.0;
                    double sobra = valorADividir - (valorPorForma * numFormas);
                    // Arredondar sobra para 2 casas
                    sobra = Math.round(sobra * 100.0) / 100.0;

                    for (int i = 0; i < checkboxes.size(); i++) {
                        EditText et = editTexts.get(i);
                        if (checkboxes.get(i).isChecked()) {
                            et.setVisibility(View.VISIBLE);
                            et.setEnabled(false);
                            // Primeiro selecionado recebe a sobra do arredondamento
                            if (i == selecionados.get(0)) {
                                et.setText(FormatUtils.formatMoney(valorPorForma + sobra));
                            } else {
                                et.setText(FormatUtils.formatMoney(valorPorForma));
                            }
                        } else {
                            et.setVisibility(View.GONE);
                            et.setText("");
                        }
                    }
                } else {
                    // Divisao personalizada - habilitar edicao
                    for (int i = 0; i < checkboxes.size(); i++) {
                        EditText et = editTexts.get(i);
                        if (checkboxes.get(i).isChecked()) {
                            et.setVisibility(View.VISIBLE);
                            et.setEnabled(true);
                            // Se o campo estiver vazio, sugerir divisao igual como ponto de partida
                            if (et.getText().toString().trim().isEmpty() || et.getText().toString().equals("0.00")) {
                                int numFormas = selecionados.size();
                                double sugestao = Math.floor((valorADividir / numFormas) * 100.0) / 100.0;
                                if (i == selecionados.get(0)) {
                                    double sobra = valorADividir - (sugestao * numFormas);
                                    sobra = Math.round(sobra * 100.0) / 100.0;
                                    et.setText(FormatUtils.formatMoney(sugestao + sobra));
                                } else {
                                    et.setText(FormatUtils.formatMoney(sugestao));
                                }
                            }
                        } else {
                            et.setVisibility(View.GONE);
                            et.setText("");
                        }
                    }
                }

                // Atualizar resumo
                atualizarResumoDivisao(layoutResumoItens, tvTotalDistribuido,
                    layoutDiferenca, tvDiferenca, checkboxes, editTexts, formasRef, valorADividir);
            }
        };

        // Listener para checkboxes
        for (CheckBox cb : checkboxes) {
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> recalcularDivisao.run());
        }

        // Listener para mudanca de tipo de divisao
        rgTipoDivisao.setOnCheckedChangeListener((group, checkedId) -> {
            // Limpar valores dos campos para recalcular
            for (EditText et : editTexts) {
                et.setText("");
            }
            recalcularDivisao.run();
        });

        // TextWatcher para campos personalizados - atualizar resumo em tempo real
        for (EditText et : editTexts) {
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (rbPersonalizado.isChecked()) {
                        atualizarResumoDivisao(layoutResumoItens, tvTotalDistribuido,
                            layoutDiferenca, tvDiferenca, checkboxes, editTexts, formasRef, valorADividir);
                    }
                }
            });
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Dividir Pagamento")
                .setView(view)
                .setPositiveButton("Aplicar Divisao", null) // null para controlar manualmente
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button btnAplicar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnAplicar.setOnClickListener(v -> {
                // Validar e aplicar
                List<Integer> selecionados = new ArrayList<>();
                for (int i = 0; i < checkboxes.size(); i++) {
                    if (checkboxes.get(i).isChecked()) selecionados.add(i);
                }

                if (selecionados.isEmpty()) {
                    showError("Selecione pelo menos uma forma de pagamento.");
                    return;
                }

                // Coletar valores
                double totalDistribuido = 0;
                List<Double> valores = new ArrayList<>();
                for (int idx : selecionados) {
                    double val = FormatUtils.parseMoney(editTexts.get(idx).getText().toString());
                    if (val <= 0) {
                        showError("O valor para " + formasRef.get(idx).getDescricao() + " e invalido.\n\nTodos os valores devem ser maiores que zero.");
                        return;
                    }
                    valores.add(val);
                    totalDistribuido += val;
                }

                // Verificar se cobre o valor
                if (totalDistribuido < valorADividir - 0.01) {
                    double falta = valorADividir - totalDistribuido;
                    showError("O total distribuido (R$ " + FormatUtils.formatMoney(totalDistribuido)
                        + ") nao cobre o valor a dividir.\n\nFaltam R$ " + FormatUtils.formatMoney(falta)
                        + ". Ajuste os valores.");
                    return;
                }

                // Adicionar os pagamentos
                for (int i = 0; i < selecionados.size(); i++) {
                    int idx = selecionados.get(i);
                    FormaPagamento forma = formasRef.get(idx);

                    PagamentoVenda pag = new PagamentoVenda();
                    pag.setFormaPagamentoId(forma.getId());
                    pag.setFormaDescricao(forma.getDescricao());
                    pag.setValor(valores.get(i));
                    pag.setParcelas(1);

                    pagamentos.add(pag);
                }

                pagAdapter.setItems(pagamentos);
                atualizarSaldo();
                dialog.dismiss();

                // Feedback ao usuario
                showToast("Pagamento dividido em " + selecionados.size() + " formas");
            });
        });

        dialog.show();
    }

    private void atualizarResumoDivisao(LinearLayout layoutResumoItens, TextView tvTotalDistribuido,
            LinearLayout layoutDiferenca, TextView tvDiferenca,
            List<CheckBox> checkboxes, List<EditText> editTexts,
            List<FormaPagamento> formasRef, double valorADividir) {

        layoutResumoItens.removeAllViews();
        double totalDistribuido = 0;

        for (int i = 0; i < checkboxes.size(); i++) {
            if (checkboxes.get(i).isChecked()) {
                double val = FormatUtils.parseMoney(editTexts.get(i).getText().toString());
                totalDistribuido += val;

                // Criar linha de resumo
                LinearLayout linha = new LinearLayout(this);
                linha.setOrientation(LinearLayout.HORIZONTAL);
                linha.setPadding(0, 2, 0, 2);

                TextView tvNome = new TextView(this);
                tvNome.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                tvNome.setText(formasRef.get(i).getDescricao());
                tvNome.setTextColor(0xFFB0BEC5);
                tvNome.setTextSize(13);

                TextView tvVal = new TextView(this);
                tvVal.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                tvVal.setText("R$ " + FormatUtils.formatMoney(val));
                tvVal.setTextColor(0xFFFFFFFF);
                tvVal.setTextSize(13);

                linha.addView(tvNome);
                linha.addView(tvVal);
                layoutResumoItens.addView(linha);
            }
        }

        tvTotalDistribuido.setText("R$ " + FormatUtils.formatMoney(totalDistribuido));

        double diferenca = valorADividir - totalDistribuido;
        if (Math.abs(diferenca) > 0.01) {
            layoutDiferenca.setVisibility(View.VISIBLE);
            if (diferenca > 0) {
                tvDiferenca.setText("- R$ " + FormatUtils.formatMoney(diferenca));
                tvDiferenca.setTextColor(0xFFFF5252); // Vermelho - falta
            } else {
                tvDiferenca.setText("+ R$ " + FormatUtils.formatMoney(Math.abs(diferenca)));
                tvDiferenca.setTextColor(0xFFFFD700); // Dourado - troco
            }
        } else {
            layoutDiferenca.setVisibility(View.GONE);
        }

        // Mudar cor do total distribuido conforme status
        if (totalDistribuido >= valorADividir - 0.01) {
            tvTotalDistribuido.setTextColor(0xFF00E676); // Verde
        } else {
            tvTotalDistribuido.setTextColor(0xFFFF5252); // Vermelho
        }
    }

    // =====================================================================
    // FINALIZAR VENDA
    // =====================================================================

    private void finalizarVenda() {
        if (pagamentos.isEmpty()) {
            showError("Nenhum pagamento adicionado.\n\nSelecione pelo menos uma forma de pagamento e informe o valor.");
            return;
        }

        double totalPago = calcularTotalPago();

        if (totalPago < totalLiquido - 0.01) {
            double falta = totalLiquido - totalPago;
            showError("Ainda existe saldo pendente nesta venda.\n\nValor restante: R$ " + FormatUtils.formatMoney(falta) + "\n\nAdicione mais formas de pagamento ate cobrir o valor total.");
            return;
        }

        double valorRecebido = totalPago;
        try {
            String vrText = etValorRecebido.getText().toString().trim();
            if (!vrText.isEmpty()) valorRecebido = FormatUtils.parseMoney(vrText);
        } catch (Exception ignored) {}

        double troco = valorRecebido - totalLiquido;
        if (troco < 0) troco = 0;

        final double finalTroco = troco;
        final double finalValorRecebido = valorRecebido;

        // v6.0.0 - Verificacao OBRIGATORIA de caixa aberto antes de registrar a venda
        showLoading("Verificando caixa...");
        new Thread(() -> {
            boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
            if (!caixaAberto) {
                hideLoading();
                runOnUiThread(() -> PermissionHelper.mostrarCaixaFechado(this));
                return;
            }
            // Caixa aberto - prosseguir
            runOnUiThread(() -> executarFinalizacaoVenda(finalTroco, finalValorRecebido));
        }).start();
    }

    /**
     * v6.0.0 - Executa a finalizacao da venda apos confirmar que o caixa esta aberto.
     */
    private void executarFinalizacaoVenda(double finalTroco, double finalValorRecebido) {
        final boolean imprimirCanhotoSenhaSelecionado = cbImprimirCanhotoSenha != null && cbImprimirCanhotoSenha.isChecked();
        final boolean imprimirDuasViasCupomSelecionado = cbImprimirDuasViasCupom != null && cbImprimirDuasViasCupom.isChecked();
        final boolean exibirSenhaNoCupomSelecionado = cbExibirSenhaNoCupom != null && cbExibirSenhaNoCupom.isChecked();
        salvarPreferenciasOpcoesImpressao();
        showLoading("Finalizando venda...");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                double totalBruto = getIntent().getDoubleExtra("total_bruto", 0);
                double desconto = getIntent().getDoubleExtra("desconto", 0);
                double acrescimo = getIntent().getDoubleExtra("acrescimo", 0);
                String descontoTipo = getIntent().getStringExtra("desconto_tipo");
                String acrescimoTipo = getIntent().getStringExtra("acrescimo_tipo");
                int clienteId = getIntent().getIntExtra("cliente_id", 0);
                int vendedorId = getIntent().getIntExtra("vendedor_id", 0);
                int entregadorId = getIntent().getIntExtra("entregador_id", 0);
                int garcomId = getIntent().getIntExtra("garcom_id", 0);
                String observacao = getIntent().getStringExtra("observacao");
                int osIdParaFechar = getIntent().getIntExtra("os_id_fechar", 0);
                boolean isEstacionamento = getIntent().getBooleanExtra("is_estacionamento", false);
                int estacionamentoId = getIntent().getIntExtra("estacionamento_id", 0);

                int caixaId = 0;
                int caixaNominalPreferido = getSharedPreferences("caixa_config", MODE_PRIVATE)
                        .getInt("caixa_nominal_id_selecionado", 0);
                PreparedStatement psCaixa;
                if (caixaNominalPreferido > 0) {
                    psCaixa = conn.prepareStatement(
                            "SELECT id FROM caixa WHERE status = 'aberto' AND caixa_nominal_id = ? ORDER BY id DESC LIMIT 1");
                    psCaixa.setInt(1, caixaNominalPreferido);
                } else {
                    psCaixa = conn.prepareStatement(
                            "SELECT id FROM caixa WHERE status = 'aberto' ORDER BY id DESC LIMIT 1");
                }
                ResultSet rsCaixa = psCaixa.executeQuery();
                if (rsCaixa.next()) caixaId = rsCaixa.getInt("id");
                rsCaixa.close();
                psCaixa.close();

                if (caixaId <= 0) {
                    String caixaNome = getSharedPreferences("caixa_config", MODE_PRIVATE)
                            .getString("caixa_nominal_nome_selecionado", "caixa selecionado");
                    hideLoading();
                    runOnUiThread(() -> showError("Nao existe caixa aberto para " + caixaNome
                            + ". Abra este caixa no modulo Caixa ou escolha outro caixa no dropdown."));
                    return;
                }

                if (clienteId == 0) {
                    Statement stmtCli = conn.createStatement();
                    ResultSet rsCli = stmtCli.executeQuery("SELECT id FROM clientes WHERE nome = 'Cliente nao informado' LIMIT 1");
                    if (rsCli.next()) clienteId = rsCli.getInt("id");
                    rsCli.close();
                    stmtCli.close();
                }

                // Salvar um snapshot da comissao vigente. Assim, alteracoes
                // futuras no cadastro do vendedor nao mudam relatorios antigos.
                double comissaoPercentual = 0;
                if (vendedorId > 0) {
                    PreparedStatement psComissao = conn.prepareStatement(
                            "SELECT COALESCE(comissao, 0) FROM vendedores WHERE id = ?");
                    psComissao.setInt(1, vendedorId);
                    ResultSet rsComissao = psComissao.executeQuery();
                    if (rsComissao.next()) comissaoPercentual = rsComissao.getDouble(1);
                    rsComissao.close();
                    psComissao.close();
                }
                comissaoPercentual = Math.max(0, Math.min(100, comissaoPercentual));
                double comissaoValor = Math.round(totalLiquido * comissaoPercentual) / 100.0;

                // Mesas mantem o garcom na ocupacao. Recupera antes de a mesa
                // ser encerrada para preservar o historico da porcentagem.
                if (garcomId <= 0) {
                    int ocupacaoOrigem = getIntent().getIntExtra("ocupacao_id", 0);
                    if (ocupacaoOrigem > 0) {
                        PreparedStatement psGarcomOcupacao = conn.prepareStatement(
                                "SELECT COALESCE(garcom_id,0) FROM ocupacao_mesa WHERE id=?");
                        psGarcomOcupacao.setInt(1, ocupacaoOrigem);
                        ResultSet rsGarcomOcupacao = psGarcomOcupacao.executeQuery();
                        if (rsGarcomOcupacao.next()) garcomId = rsGarcomOcupacao.getInt(1);
                        rsGarcomOcupacao.close();
                        psGarcomOcupacao.close();
                    }
                }
                double garcomPercentual = 0;
                if (garcomId > 0) {
                    PreparedStatement psGarcom = conn.prepareStatement(
                            "SELECT COALESCE(porcentagem,0) FROM garcons WHERE id=?");
                    psGarcom.setInt(1, garcomId);
                    ResultSet rsGarcom = psGarcom.executeQuery();
                    if (rsGarcom.next()) garcomPercentual = rsGarcom.getDouble(1);
                    rsGarcom.close();
                    psGarcom.close();
                }
                garcomPercentual = Math.max(0, Math.min(100, garcomPercentual));
                double garcomValor = Math.round(totalLiquido * garcomPercentual) / 100.0;

                // v7.0.3 - Incluir uso_armario_id na venda quando for armario sauna
                int usoArmarioIdVenda = getIntent().getIntExtra("uso_armario_id", 0);

                PreparedStatement psVenda = conn.prepareStatement(
                        "INSERT INTO vendas (cliente_id,vendedor_id,entregador_id,caixa_id,data_venda,"
                                + "total_bruto,desconto_tipo,desconto_valor,acrescimo_tipo,acrescimo_valor,"
                                + "total_liquido,valor_recebido,troco,observacao,status,"
                                + "para_entrega,taxa_entrega,bairro_entrega,endereco_entrega,uso_armario_id,"
                                + "comissao_percentual,comissao_valor,garcom_id,garcom_percentual,garcom_valor) "
                                + "VALUES (?,?,?,?,NOW(),?,?,?,?,?,?,?,?,?,'finalizada',?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                psVenda.setInt(1, clienteId);
                psVenda.setInt(2, vendedorId);
                psVenda.setInt(3, entregadorId);
                psVenda.setInt(4, caixaId);
                psVenda.setDouble(5, totalBruto);
                psVenda.setString(6, descontoTipo);
                psVenda.setDouble(7, desconto);
                psVenda.setString(8, acrescimoTipo);
                psVenda.setDouble(9, acrescimo);
                psVenda.setDouble(10, totalLiquido);
                psVenda.setDouble(11, finalValorRecebido);
                psVenda.setDouble(12, finalTroco);
                psVenda.setString(13, observacao);
                psVenda.setInt(14, paraEntrega ? 1 : 0);
                psVenda.setDouble(15, taxaEntrega);
                psVenda.setString(16, bairroEntrega);
                psVenda.setString(17, enderecoEntrega);
                psVenda.setInt(18, usoArmarioIdVenda);
                psVenda.setDouble(19, comissaoPercentual);
                psVenda.setDouble(20, comissaoValor);
                if (garcomId > 0) psVenda.setInt(21, garcomId); else psVenda.setNull(21, java.sql.Types.INTEGER);
                psVenda.setDouble(22, garcomPercentual);
                psVenda.setDouble(23, garcomValor);
                psVenda.executeUpdate();

                // v8.0.5 - Obter vendaId ANTES de usar (correcao de bug: estava sendo usado antes de ser declarado)
                ResultSet keys = psVenda.getGeneratedKeys();
                int vendaId = 0;
                if (keys.next()) vendaId = keys.getInt(1);
                keys.close();
                psVenda.close();

                // v8.0.5 - Se for fechamento de OS, vincular a venda e atualizar status da OS
                // v8.0.8 - Registrar usuario que fechou a OS
                if (osIdParaFechar > 0 && vendaId > 0) {
                    int osFechUserId = getIntent().getIntExtra("os_usuario_fechamento_id", 0);
                    String osFechUserNome = getIntent().getStringExtra("os_usuario_fechamento_nome");
                    if (osFechUserNome == null) osFechUserNome = "";
                    PreparedStatement psOS = conn.prepareStatement(
                            "UPDATE ordens_servico SET venda_id = ?, status = 'Concluida', usuario_fechamento_id = ?, usuario_fechamento_nome = ? WHERE id = ?");
                    psOS.setInt(1, vendaId);
                    if (osFechUserId > 0) psOS.setInt(2, osFechUserId); else psOS.setNull(2, java.sql.Types.INTEGER);
                    psOS.setString(3, osFechUserNome.isEmpty() ? null : osFechUserNome);
                    psOS.setInt(4, osIdParaFechar);
                    psOS.executeUpdate();
                    psOS.close();
                }

                // v8.0.5 - Suportar tanto "num_itens" (VendaActivity) quanto "itens_count" (OrdemServicoActivity)
                int numItens = getIntent().getIntExtra("num_itens", getIntent().getIntExtra("itens_count", 0));
                for (int i = 0; i < numItens; i++) {
                    int produtoId = getIntent().getIntExtra("item_produto_id_" + i, 0);
                    String desc = getIntent().getStringExtra("item_descricao_" + i);
                    double qtd = getIntent().getDoubleExtra("item_qtd_" + i, 0);
                    double preco = getIntent().getDoubleExtra("item_preco_" + i, 0);
                    double total = getIntent().getDoubleExtra("item_total_" + i, 0);

                    // v6.7.5 - Incluir adicionais na descricao do item
                    int numAdicionais = getIntent().getIntExtra("item_num_adicionais_" + i, 0);
                    StringBuilder adDesc = new StringBuilder();
                    if (numAdicionais > 0) {
                        for (int j = 0; j < numAdicionais; j++) {
                            String adDescStr = getIntent().getStringExtra("item_" + i + "_ad_desc_" + j);
                            if (adDesc.length() > 0) adDesc.append(", ");
                            adDesc.append(adDescStr);
                        }
                        desc = desc + " [" + adDesc.toString() + "]";
                    }

                    PreparedStatement psItem = conn.prepareStatement(
                            "INSERT INTO itens_venda (venda_id,produto_id,descricao_produto,quantidade,preco_unitario,total) VALUES (?,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS);
                    psItem.setInt(1, vendaId);
                    psItem.setInt(2, produtoId);
                    psItem.setString(3, desc);
                    psItem.setDouble(4, qtd);
                    psItem.setDouble(5, preco);
                    psItem.setDouble(6, total);
                    psItem.executeUpdate();

                    // v6.7.5 - Obter ID do item inserido para salvar adicionais
                    int itemVendaId = 0;
                    ResultSet itemKeys = psItem.getGeneratedKeys();
                    if (itemKeys.next()) itemVendaId = itemKeys.getInt(1);
                    itemKeys.close();
                    psItem.close();

                    // v6.7.5 - Salvar adicionais na tabela itens_venda_adicionais
                    if (itemVendaId > 0 && numAdicionais > 0) {
                        for (int j = 0; j < numAdicionais; j++) {
                            int adId = getIntent().getIntExtra("item_" + i + "_ad_id_" + j, 0);
                            String adDescItem = getIntent().getStringExtra("item_" + i + "_ad_desc_" + j);
                            double adPreco = getIntent().getDoubleExtra("item_" + i + "_ad_preco_" + j, 0);

                            PreparedStatement psAd = conn.prepareStatement(
                                    "INSERT INTO itens_venda_adicionais (item_venda_id, adicional_id, descricao_adicional, preco) VALUES (?, ?, ?, ?)");
                            psAd.setInt(1, itemVendaId);
                            psAd.setInt(2, adId);
                            psAd.setString(3, adDescItem);
                            psAd.setDouble(4, adPreco);
                            psAd.executeUpdate();
                            psAd.close();
                        }
                    }

                    PreparedStatement psEstoque = conn.prepareStatement(
                            "UPDATE produtos SET estoque = estoque - ? WHERE id = ?");
                    psEstoque.setDouble(1, qtd);
                    psEstoque.setInt(2, produtoId);
                    psEstoque.executeUpdate();
                    psEstoque.close();
                }

                for (PagamentoVenda pag : pagamentos) {
                    PreparedStatement psPag = conn.prepareStatement(
                            "INSERT INTO pagamentos_venda (venda_id,forma_pagamento_id,valor,parcelas) VALUES (?,?,?,?)");
                    psPag.setInt(1, vendaId);
                    psPag.setInt(2, pag.getFormaPagamentoId());
                    psPag.setDouble(3, pag.getValor());
                    psPag.setInt(4, pag.getParcelas());
                    psPag.executeUpdate();
                    psPag.close();
                }

                // ============================================================
                // CONTAS A RECEBER: Se houver pagamento do tipo conta_receber,
                // criar registro na tabela contas_receber
                // ============================================================
                if (temPagamentoContaReceber()) {
                    double valorCR = getValorContaReceber();
                    PreparedStatement psCR = conn.prepareStatement(
                            "INSERT INTO contas_receber (cliente_id, venda_id, valor_original, valor_pago, valor_pendente, "
                            + "data_venda, status, observacao) VALUES (?, ?, ?, 0, ?, NOW(), 'pendente', ?)");
                    psCR.setInt(1, clienteId);
                    psCR.setInt(2, vendaId);
                    psCR.setDouble(3, valorCR);
                    psCR.setDouble(4, valorCR);
                    psCR.setString(5, "Venda #" + vendaId + " - Contas a Receber");
                    psCR.executeUpdate();
                    psCR.close();
                }

                boolean isComanda = getIntent().getBooleanExtra("is_comanda", false);
                int comandaIdParam = getIntent().getIntExtra("comanda_id", 0);
                if (isComanda && comandaIdParam > 0) {
                    try {
                        PreparedStatement psComanda = conn.prepareStatement(
                                "UPDATE comandas SET status = 'fechada', data_fechamento = NOW() WHERE id = ?");
                        psComanda.setInt(1, comandaIdParam);
                        psComanda.executeUpdate();
                        psComanda.close();
                    } catch (Exception ex) {
                        android.util.Log.w("PagamentoActivity", "Erro ao fechar comanda: " + ex.getMessage());
                    }
                }

                // v6.7.7 - Encerrar mesa apos pagamento
                boolean isMesa = getIntent().getBooleanExtra("is_mesa", false);
                int mesaOcupacaoId = getIntent().getIntExtra("ocupacao_id", 0);
                if (isMesa && mesaOcupacaoId > 0) {
                    try {
                        // Remover adicionais dos itens da mesa
                        try {
                            PreparedStatement psAdMesa = conn.prepareStatement(
                                    "DELETE FROM itens_mesa_adicionais WHERE item_mesa_id IN (SELECT id FROM itens_mesa WHERE ocupacao_id = ?)");
                            psAdMesa.setInt(1, mesaOcupacaoId);
                            psAdMesa.executeUpdate();
                            psAdMesa.close();
                        } catch (Exception ignored) {}

                        // Remover itens da mesa
                        PreparedStatement psMesaItens = conn.prepareStatement(
                                "DELETE FROM itens_mesa WHERE ocupacao_id = ?");
                        psMesaItens.setInt(1, mesaOcupacaoId);
                        psMesaItens.executeUpdate();
                        psMesaItens.close();

                        // Encerrar ocupacao da mesa
                        PreparedStatement psMesaOcup = conn.prepareStatement(
                                "UPDATE ocupacao_mesa SET status = 'encerrada', data_fechamento = NOW() WHERE id = ?");
                        psMesaOcup.setInt(1, mesaOcupacaoId);
                        psMesaOcup.executeUpdate();
                        psMesaOcup.close();
                    } catch (Exception ex) {
                        android.util.Log.w("PagamentoActivity", "Erro ao encerrar mesa: " + ex.getMessage());
                    }
                }

                // v6.9.7 - Encerrar armario sauna apos pagamento
                boolean isArmarioSauna = getIntent().getBooleanExtra("is_armario_sauna", false);
                int usoArmarioId = getIntent().getIntExtra("uso_armario_id", 0);
                if (isArmarioSauna && usoArmarioId > 0) {
                    try {
                        // Remover adicionais dos itens do armario
                        try {
                            PreparedStatement psAdArm = conn.prepareStatement(
                                    "DELETE FROM itens_armario_sauna_adicionais WHERE item_armario_id IN (SELECT id FROM itens_armario_sauna WHERE uso_armario_id = ?)");
                            psAdArm.setInt(1, usoArmarioId);
                            psAdArm.executeUpdate();
                            psAdArm.close();
                        } catch (Exception ignored) {}

                        // Remover itens do armario
                        PreparedStatement psArmItens = conn.prepareStatement(
                                "DELETE FROM itens_armario_sauna WHERE uso_armario_id = ?");
                        psArmItens.setInt(1, usoArmarioId);
                        psArmItens.executeUpdate();
                        psArmItens.close();

                        // Encerrar uso do armario
                        PreparedStatement psArmUso = conn.prepareStatement(
                                "UPDATE uso_armario_sauna SET status = 'encerrado', data_saida = NOW() WHERE id = ?");
                        psArmUso.setInt(1, usoArmarioId);
                        psArmUso.executeUpdate();
                        psArmUso.close();
                    } catch (Exception ex) {
                        android.util.Log.w("PagamentoActivity", "Erro ao encerrar armario: " + ex.getMessage());
                    }
                }

                if (isEstacionamento && estacionamentoId > 0 && vendaId > 0) {
                    try {
                        PreparedStatement psEst = conn.prepareStatement(
                                "UPDATE estacionamento SET saida=NOW(), status='FECHADO', valor_total=?, atualizado_em=NOW() WHERE id=? AND status='ABERTO'");
                        psEst.setDouble(1, totalLiquido);
                        psEst.setInt(2, estacionamentoId);
                        psEst.executeUpdate();
                        psEst.close();
                    } catch (Exception ex) {
                        android.util.Log.w("PagamentoActivity", "Erro ao fechar estacionamento: " + ex.getMessage());
                    }
                }

                final int finalVendaId = vendaId;

                // v8.0.12.4 - Registrar a senha no painel de chamados de senhas.
                // A senha usa o proprio numero da venda e fica persistida mesmo fechando o app.
                if (imprimirCanhotoSenhaSelecionado && finalVendaId > 0) {
                    String clienteNomeSenha = getIntent().getStringExtra("cliente_nome");
                    SenhaChamadoStore.adicionarSenha(this, finalVendaId, clienteNomeSenha, totalLiquido);
                }

                hideLoading();

                runOnUiThread(() -> {
                    if (imprimirCanhotoSenhaSelecionado) {
                        imprimirCanhotoSenha(finalVendaId);
                    }
                    if (isEstacionamento) {
                        imprimirComprovanteEntregaEstacionamento(finalVendaId);
                    }
                    String tituloMsg = isEstacionamento ? "Estacionamento Pago!" : (isComanda ? "Comanda Fechada!" : (isMesa ? "Mesa Encerrada!" : (isArmarioSauna ? "Armario Encerrado!" : "Venda Finalizada!")));
                    new AlertDialog.Builder(this)
                            .setTitle(tituloMsg)
                            .setMessage("Venda #" + finalVendaId + " realizada com sucesso!\n"
                                    + "Total: R$ " + FormatUtils.formatMoney(totalLiquido) + "\n"
                                    + "Troco: R$ " + FormatUtils.formatMoney(finalTroco))
                            .setPositiveButton("Imprimir", (d, w) -> {
                                imprimirCupom(finalVendaId, exibirSenhaNoCupomSelecionado, imprimirDuasViasCupomSelecionado);
                                setResult(RESULT_OK);
                                finish();
                            })
                            .setNeutralButton("WhatsApp", (d, w) -> {
                                enviarWhatsApp(finalVendaId);
                                setResult(RESULT_OK);
                                finish();
                            })
                            .setNegativeButton("Fechar", (d, w) -> {
                                setResult(RESULT_OK);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_PAGAMENTO);
            }
        }).start();
    }


    private void imprimirComprovanteEntregaEstacionamento(int vendaId) {
        new Thread(() -> {
            try {
                PrinterManager pm = new PrinterManager(this);
                String texto = gerarComprovanteEntregaEstacionamento(vendaId);
                boolean ok = pm.imprimirTexto(texto);
                runOnUiThread(() -> showToast(ok ? "Comprovante de entrega do veículo impresso." : "Pagamento finalizado. Falha ao imprimir comprovante de entrega."));
            } catch (Exception e) {
                runOnUiThread(() -> showToast("Pagamento finalizado. Falha ao imprimir entrega: " + e.getMessage()));
            }
        }).start();
    }

    private String gerarComprovanteEntregaEstacionamento(int vendaId) {
        String ticket = getIntent().getStringExtra("estacionamento_ticket");
        String placa = getIntent().getStringExtra("estacionamento_placa");
        String veiculo = getIntent().getStringExtra("estacionamento_veiculo");
        String condutor = getIntent().getStringExtra("estacionamento_condutor");
        String telefone = getIntent().getStringExtra("estacionamento_telefone");
        String vaga = getIntent().getStringExtra("estacionamento_vaga");
        String tipo = getIntent().getStringExtra("estacionamento_tipo");
        String entrada = getIntent().getStringExtra("estacionamento_entrada");
        int minutos = getIntent().getIntExtra("estacionamento_minutos", 0);
        double valorHora = getIntent().getDoubleExtra("estacionamento_valor_hora", 0);
        String saida = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String linha = "--------------------------------\n";
        StringBuilder sb = new StringBuilder();
        sb.append("<b>COMPROVANTE DE ENTREGA</b>\n");
        sb.append("ESTACIONAMENTO\n");
        sb.append(linha);
        sb.append("Venda: #").append(vendaId).append("\n");
        sb.append("Ticket: ").append(safeTxt(ticket)).append("\n");
        sb.append("Placa: <b>").append(safeTxt(placa)).append("</b>\n");
        sb.append("Veiculo: ").append(safeTxt(veiculo)).append("\n");
        sb.append("Tipo: ").append(safeTxt(tipo)).append("\n");
        sb.append("Vaga: ").append(safeTxt(vaga)).append("\n");
        sb.append("Condutor: ").append(safeTxt(condutor)).append("\n");
        sb.append("Telefone: ").append(safeTxt(telefone)).append("\n");
        sb.append(linha);
        sb.append("Entrada: ").append(safeTxt(entrada)).append("\n");
        sb.append("Saida: ").append(saida).append("\n");
        sb.append("Permanencia: ").append(formatTempoPagamento(minutos)).append("\n");
        sb.append("Valor/hora: R$ ").append(FormatUtils.formatMoney(valorHora)).append("\n");
        sb.append("Total pago: <b>R$ ").append(FormatUtils.formatMoney(totalLiquido)).append("</b>\n");
        sb.append(linha);
        sb.append("Veiculo entregue ao cliente.\n");
        sb.append("Obrigado pela preferencia!\n\n\n");
        return sb.toString();
    }

    private String safeTxt(String s) {
        return s == null || s.trim().isEmpty() ? "-" : s.trim();
    }

    private String formatTempoPagamento(int minutos) {
        if (minutos < 0) minutos = 0;
        int h = minutos / 60;
        int m = minutos % 60;
        if (h <= 0) return m + " min";
        return h + "h " + m + "min";
    }

    private void imprimirCupom(int vendaId) {
        boolean exibirSenhaNoCupom = cbExibirSenhaNoCupom != null && cbExibirSenhaNoCupom.isChecked();
        boolean imprimirDuasVias = cbImprimirDuasViasCupom != null && cbImprimirDuasViasCupom.isChecked();
        imprimirCupom(vendaId, exibirSenhaNoCupom, imprimirDuasVias);
    }

    private void imprimirCupom(int vendaId, boolean exibirSenhaNoCupom, boolean imprimirDuasVias) {
        new Thread(() -> {
            CupomGenerator gen = new CupomGenerator(this);
            String cupom = gen.gerarCupom(vendaId, exibirSenhaNoCupom);
            PrinterManager pm = new PrinterManager(this);
            if (pm.isImpressoraConfigurada()) {
                int vias = imprimirDuasVias ? 2 : 1;
                for (int i = 0; i < vias; i++) {
                    pm.imprimirTexto(cupom);
                    try { Thread.sleep(300); } catch (Exception ignored) {}
                }
                // Multiimpressoras: quando ativado, envia tambem os produtos separados
                // por categoria/tipo para suas impressoras especificas.
                try {
                    MultiPrinterManager multi = new MultiPrinterManager(this);
                    int enviados = multi.imprimirVendaPorCategorias(vendaId);
                    if (enviados > 0) {
                        android.util.Log.i("PagamentoActivity", "Multiimpressoras enviadas: " + enviados);
                    }
                } catch (Exception e) {
                    android.util.Log.e("PagamentoActivity", "Falha na multiimpressao", e);
                }
            } else {
                showToast("Impressora nao configurada");
            }
        }).start();
    }

    /**
     * v8.0.12.2 - Imprime um canhoto pequeno de senha quando a opcao
     * "Imprimir canhoto de senha" estiver marcada na tela de pagamento.
     * O numero da senha usa o ID da venda, garantindo que cada canhoto seja unico.
     */
    private void imprimirCanhotoSenha(int vendaId) {
        new Thread(() -> {
            try {
                CupomGenerator gen = new CupomGenerator(this);
                String canhoto = gen.gerarCanhotoSenha(vendaId);
                PrinterManager pm = new PrinterManager(this);
                if (pm.isImpressoraConfigurada()) {
                    boolean ok = pm.imprimirTexto(canhoto);
                    if (!ok) showToast("Nao foi possivel imprimir o canhoto de senha");
                } else {
                    showToast("Impressora nao configurada");
                }
            } catch (Exception e) {
                android.util.Log.e("PagamentoActivity", "Erro ao imprimir canhoto de senha", e);
                showToast("Erro ao imprimir canhoto de senha");
            }
        }).start();
    }

    private void enviarWhatsApp(int vendaId) {
        new Thread(() -> {
            CupomGenerator gen = new CupomGenerator(this);
            String cupom = gen.gerarCupom(vendaId);

            // v6.2.0 - Buscar celular do cliente para envio automatico
            String celularCliente = null;
            try {
                int clienteId = getIntent().getIntExtra("cliente_id", 0);
                if (clienteId > 0) {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT celular FROM clientes WHERE id = ?");
                    ps.setInt(1, clienteId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        celularCliente = rs.getString("celular");
                    }
                    rs.close();
                    ps.close();
                }
            } catch (Exception e) {
                android.util.Log.w("PagamentoActivity", "Erro ao buscar celular do cliente: " + e.getMessage());
            }

            final String celular = celularCliente;
            runOnUiThread(() -> WhatsAppHelper.enviarCupom(this, cupom, celular));
        }).start();
    }
}
