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
import com.pdv.app.models.ContaReceber;
import com.pdv.app.utils.*;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity para gerenciamento de Contas a Receber.
 * Exibe as contas pendentes dos clientes, permite filtrar por cliente/status,
 * registrar recebimentos (baixas) parciais ou totais, e visualizar extrato do cliente.
 */
public class ContasReceberActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<ContaReceber> adapter;
    private Spinner spFiltroCliente, spFiltroStatus;
    private TextView tvTotalPendente, tvTotalRecebido, tvQtdPendentes;

    private List<int[]> clienteIds = new ArrayList<>(); // [id] para mapear spinner
    private List<String> clienteNomes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contas_receber);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CONTAS_RECEBER_ACESSAR)) {
            return;
        }

        tvTotalPendente = findViewById(R.id.tvTotalPendente);
        tvTotalRecebido = findViewById(R.id.tvTotalRecebido);
        tvQtdPendentes = findViewById(R.id.tvQtdPendentes);
        spFiltroCliente = findViewById(R.id.spFiltroCliente);
        spFiltroStatus = findViewById(R.id.spFiltroStatus);

        // Setup filtro de status
        String[] statusOpcoes = {"Todos", "Pendente", "Pago Parcial", "Pago", "Cancelado"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, statusOpcoes);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFiltroStatus.setAdapter(statusAdapter);

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_conta_receber, (holder, item, pos) -> {
            TextView tvClienteNome = holder.find(R.id.tvClienteNome);
            TextView tvStatus = holder.find(R.id.tvStatus);
            TextView tvVendaData = holder.find(R.id.tvVendaData);
            TextView tvValorOriginal = holder.find(R.id.tvValorOriginal);
            TextView tvValorPago = holder.find(R.id.tvValorPago);
            TextView tvValorPendente = holder.find(R.id.tvValorPendente);

            tvClienteNome.setText(FormatUtils.safeString(item.getClienteNome()));

            // Status com cor
            String status = FormatUtils.safeString(item.getStatus());
            tvStatus.setText(status.toUpperCase());
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

            tvVendaData.setText("Venda #" + item.getVendaId() + " | " + FormatUtils.formatDate(item.getDataVenda()));
            tvValorOriginal.setText("R$ " + FormatUtils.formatMoney(item.getValorOriginal()));
            tvValorPago.setText("R$ " + FormatUtils.formatMoney(item.getValorPago()));
            tvValorPendente.setText("R$ " + FormatUtils.formatMoney(item.getValorPendente()));
        });

        adapter.setOnItemClickListener((item, pos) -> showContaOptions(item));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnFiltrar).setOnClickListener(v -> loadData());

        carregarClientes();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    /**
     * Carrega a lista de clientes para o filtro Spinner.
     */
    private void carregarClientes() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT DISTINCT c.id, c.nome FROM clientes c "
                        + "INNER JOIN contas_receber cr ON c.id = cr.cliente_id "
                        + "ORDER BY c.nome");

                clienteIds.clear();
                clienteNomes.clear();
                clienteNomes.add("Todos os Clientes");
                clienteIds.add(new int[]{0});

                while (rs.next()) {
                    clienteIds.add(new int[]{rs.getInt("id")});
                    clienteNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    ArrayAdapter<String> clienteAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, clienteNomes);
                    clienteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spFiltroCliente.setAdapter(clienteAdapter);
                });
            } catch (Exception e) {
                // Silencioso - se nao houver contas, o spinner fica vazio
            }
        }).start();
    }

    /**
     * Carrega as contas a receber com base nos filtros selecionados.
     */
    private void loadData() {
        showLoading("Carregando contas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Montar query com filtros
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT cr.*, c.nome as cliente_nome FROM contas_receber cr ");
                sql.append("LEFT JOIN clientes c ON cr.cliente_id = c.id WHERE 1=1 ");

                // Filtro de cliente
                int clientePos = spFiltroCliente.getSelectedItemPosition();
                int clienteIdFiltro = 0;
                if (clientePos > 0 && clientePos < clienteIds.size()) {
                    clienteIdFiltro = clienteIds.get(clientePos)[0];
                    sql.append("AND cr.cliente_id = ").append(clienteIdFiltro).append(" ");
                }

                // Filtro de status
                int statusPos = spFiltroStatus.getSelectedItemPosition();
                switch (statusPos) {
                    case 1: sql.append("AND cr.status = 'pendente' "); break;
                    case 2: sql.append("AND cr.status = 'pago_parcial' "); break;
                    case 3: sql.append("AND cr.status = 'pago' "); break;
                    case 4: sql.append("AND cr.status = 'cancelado' "); break;
                    // 0 = Todos, sem filtro
                }

                sql.append("ORDER BY cr.status ASC, cr.data_venda DESC");

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql.toString());

                List<ContaReceber> list = new ArrayList<>();
                double totalPend = 0, totalReceb = 0;
                int qtdPend = 0;

                while (rs.next()) {
                    ContaReceber cr = new ContaReceber();
                    cr.setId(rs.getInt("id"));
                    cr.setClienteId(rs.getInt("cliente_id"));
                    cr.setVendaId(rs.getInt("venda_id"));
                    cr.setClienteNome(rs.getString("cliente_nome"));
                    cr.setDataVenda(rs.getString("data_venda"));
                    cr.setDataVencimento(rs.getString("data_vencimento"));
                    cr.setDataPagamento(rs.getString("data_pagamento"));
                    cr.setValorOriginal(rs.getDouble("valor_original"));
                    cr.setValorPago(rs.getDouble("valor_pago"));
                    cr.setValorPendente(rs.getDouble("valor_pendente"));
                    cr.setStatus(rs.getString("status"));
                    cr.setObservacao(rs.getString("observacao"));
                    list.add(cr);

                    if ("pendente".equals(cr.getStatus()) || "pago_parcial".equals(cr.getStatus())) {
                        totalPend += cr.getValorPendente();
                        qtdPend++;
                    }
                    totalReceb += cr.getValorPago();
                }
                rs.close();
                stmt.close();

                final double fTotalPend = totalPend;
                final double fTotalReceb = totalReceb;
                final int fQtdPend = qtdPend;

                hideLoading();
                runOnUiThread(() -> {
                    adapter.setItems(list);
                    tvTotalPendente.setText("R$ " + FormatUtils.formatMoney(fTotalPend));
                    tvTotalRecebido.setText("R$ " + FormatUtils.formatMoney(fTotalReceb));
                    tvQtdPendentes.setText(String.valueOf(fQtdPend));
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    /**
     * Exibe opcoes para uma conta a receber selecionada.
     */
    private void showContaOptions(ContaReceber conta) {
        List<String> opcoes = new ArrayList<>();
        List<Runnable> acoes = new ArrayList<>();

        // Opcao: Registrar Recebimento (apenas para contas pendentes)
        if ("pendente".equals(conta.getStatus()) || "pago_parcial".equals(conta.getStatus())) {
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_RECEBER_RECEBER)) {
                opcoes.add("Registrar Recebimento");
                acoes.add(() -> showRecebimentoDialog(conta));
            }

            // Opcao: Receber Valor Total
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_RECEBER_RECEBER)) {
                opcoes.add("Receber Valor Total (R$ " + FormatUtils.formatMoney(conta.getValorPendente()) + ")");
                acoes.add(() -> receberTotal(conta));
            }
        }

        // Opcao: Ver Extrato do Cliente
        opcoes.add("Extrato do Cliente");
        acoes.add(() -> showExtratoCliente(conta.getClienteId(), conta.getClienteNome()));

        // Opcao: Ver Historico de Recebimentos
        opcoes.add("Historico de Recebimentos");
        acoes.add(() -> showHistoricoRecebimentos(conta));

        // Opcao: Cancelar conta (apenas para contas pendentes)
        if ("pendente".equals(conta.getStatus()) || "pago_parcial".equals(conta.getStatus())) {
            if (PermissionHelper.temPermissao(this, PermissionConstants.CONTAS_RECEBER_CANCELAR)) {
                opcoes.add("Cancelar Conta");
                acoes.add(() -> cancelarConta(conta));
            }
        }

        if (opcoes.isEmpty()) {
            showToast("Nenhuma acao disponivel para esta conta.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(conta.getClienteNome() + " - Venda #" + conta.getVendaId())
                .setItems(opcoes.toArray(new String[0]), (d, w) -> acoes.get(w).run())
                .show();
    }

    /**
     * Exibe dialog para registrar recebimento parcial ou total.
     */
    private void showRecebimentoDialog(ContaReceber conta) {
        View view = getLayoutInflater().inflate(R.layout.dialog_recebimento, null);
        TextView tvInfoConta = view.findViewById(R.id.tvInfoConta);
        TextView tvPendenteDialog = view.findViewById(R.id.tvPendenteDialog);
        EditText etValor = view.findViewById(R.id.etValorRecebimento);
        Spinner spForma = view.findViewById(R.id.spFormaPagRecebimento);
        EditText etObs = view.findViewById(R.id.etObsRecebimento);

        tvInfoConta.setText("Cliente: " + conta.getClienteNome()
                + "\nVenda #" + conta.getVendaId()
                + "\nValor Original: R$ " + FormatUtils.formatMoney(conta.getValorOriginal()));
        tvPendenteDialog.setText("Valor Pendente: R$ " + FormatUtils.formatMoney(conta.getValorPendente()));
        etValor.setText(FormatUtils.formatMoney(conta.getValorPendente()));

        // Carregar formas de pagamento (exceto conta_receber)
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT descricao FROM formas_pagamento WHERE ativo = 1 AND tipo != 'conta_receber' ORDER BY descricao");
                List<String> formas = new ArrayList<>();
                while (rs.next()) {
                    formas.add(rs.getString("descricao"));
                }
                rs.close();
                stmt.close();

                if (formas.isEmpty()) formas.add("Dinheiro");

                final List<String> fFormas = formas;
                runOnUiThread(() -> {
                    ArrayAdapter<String> formaAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, fFormas);
                    formaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spForma.setAdapter(formaAdapter);
                });
            } catch (Exception e) {
                // Fallback
                runOnUiThread(() -> {
                    List<String> fallback = new ArrayList<>();
                    fallback.add("Dinheiro");
                    ArrayAdapter<String> fa = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, fallback);
                    fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spForma.setAdapter(fa);
                });
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Registrar Recebimento")
                .setView(view)
                .setPositiveButton("Receber", (d, w) -> {
                    double valor = FormatUtils.parseMoney(etValor.getText().toString());
                    String formaPag = spForma.getSelectedItem() != null ? spForma.getSelectedItem().toString() : "Dinheiro";
                    String obs = etObs.getText().toString().trim();

                    if (valor <= 0) {
                        showError("Informe um valor valido para o recebimento.");
                        return;
                    }
                    if (valor > conta.getValorPendente() + 0.01) {
                        showError("O valor informado (R$ " + FormatUtils.formatMoney(valor)
                                + ") e maior que o valor pendente (R$ "
                                + FormatUtils.formatMoney(conta.getValorPendente()) + ").");
                        return;
                    }

                    registrarRecebimento(conta, valor, formaPag, obs);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Registra o recebimento no banco de dados e atualiza a conta.
     */
    private void registrarRecebimento(ContaReceber conta, double valor, String formaPag, String obs) {
        showLoading("Registrando recebimento...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Inserir recebimento
                PreparedStatement psRec = conn.prepareStatement(
                        "INSERT INTO recebimentos_conta (conta_receber_id, valor, data_recebimento, forma_pagamento, observacao) "
                        + "VALUES (?, ?, NOW(), ?, ?)");
                psRec.setInt(1, conta.getId());
                psRec.setDouble(2, valor);
                psRec.setString(3, formaPag);
                psRec.setString(4, obs.isEmpty() ? null : obs);
                psRec.executeUpdate();
                psRec.close();

                // Atualizar conta
                double novoValorPago = conta.getValorPago() + valor;
                double novoValorPendente = conta.getValorOriginal() - novoValorPago;
                if (novoValorPendente < 0.01) novoValorPendente = 0;

                String novoStatus;
                if (novoValorPendente <= 0.01) {
                    novoStatus = "pago";
                } else if (novoValorPago > 0) {
                    novoStatus = "pago_parcial";
                } else {
                    novoStatus = "pendente";
                }

                PreparedStatement psUpd = conn.prepareStatement(
                        "UPDATE contas_receber SET valor_pago = ?, valor_pendente = ?, status = ?, "
                        + "data_pagamento = " + ("pago".equals(novoStatus) ? "NOW()" : "NULL") + " WHERE id = ?");
                psUpd.setDouble(1, novoValorPago);
                psUpd.setDouble(2, novoValorPendente);
                psUpd.setString(3, novoStatus);
                psUpd.setInt(4, conta.getId());
                psUpd.executeUpdate();
                psUpd.close();

                hideLoading();
                showToast("Recebimento de R$ " + FormatUtils.formatMoney(valor) + " registrado!");
                loadData();
                carregarClientes();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * Recebe o valor total pendente de uma conta de uma vez.
     */
    private void receberTotal(ContaReceber conta) {
        showConfirm("Receber Total",
                "Confirma o recebimento de R$ " + FormatUtils.formatMoney(conta.getValorPendente())
                + " do cliente " + conta.getClienteNome() + "?",
                () -> registrarRecebimento(conta, conta.getValorPendente(), "Dinheiro", "Recebimento total"));
    }

    /**
     * Cancela uma conta a receber.
     */
    private void cancelarConta(ContaReceber conta) {
        showConfirm("Cancelar Conta",
                "Deseja cancelar a conta #" + conta.getId() + " do cliente "
                + conta.getClienteNome() + "?\n\nValor pendente: R$ "
                + FormatUtils.formatMoney(conta.getValorPendente()),
                () -> {
                    showLoading("Cancelando...");
                    new Thread(() -> {
                        try {
                            DatabaseHelper db = DatabaseHelper.getInstance(this);
                            Connection conn = db.getConnection();
                            PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE contas_receber SET status = 'cancelado' WHERE id = ?");
                            ps.setInt(1, conta.getId());
                            ps.executeUpdate();
                            ps.close();
                            hideLoading();
                            showToast("Conta cancelada!");
                            loadData();
                        } catch (Exception e) {
                            hideLoading();
                            showErrorFromException(e, ErrorHandler.CTX_SALVAR);
                        }
                    }).start();
                });
    }

    /**
     * Exibe o extrato completo de contas a receber de um cliente.
     */
    private void showExtratoCliente(int clienteId, String clienteNome) {
        showLoading("Gerando extrato...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();

                // Resumo do cliente
                ResultSet rs = stmt.executeQuery(
                        "SELECT SUM(valor_original) as total_compras, "
                        + "SUM(valor_pago) as total_pago, "
                        + "SUM(valor_pendente) as total_pendente, "
                        + "COUNT(*) as qtd_contas "
                        + "FROM contas_receber WHERE cliente_id = " + clienteId
                        + " AND status != 'cancelado'");

                StringBuilder sb = new StringBuilder();
                sb.append("=== EXTRATO - ").append(clienteNome).append(" ===\n\n");

                if (rs.next()) {
                    sb.append("Total em Compras: R$ ").append(FormatUtils.formatMoney(rs.getDouble("total_compras"))).append("\n");
                    sb.append("Total Pago: R$ ").append(FormatUtils.formatMoney(rs.getDouble("total_pago"))).append("\n");
                    sb.append("Total Pendente: R$ ").append(FormatUtils.formatMoney(rs.getDouble("total_pendente"))).append("\n");
                    sb.append("Quantidade de Contas: ").append(rs.getInt("qtd_contas")).append("\n");
                }
                rs.close();

                sb.append("\n--- DETALHAMENTO ---\n\n");

                // Listar contas
                rs = stmt.executeQuery(
                        "SELECT cr.*, v.data_venda as data_v FROM contas_receber cr "
                        + "LEFT JOIN vendas v ON cr.venda_id = v.id "
                        + "WHERE cr.cliente_id = " + clienteId
                        + " ORDER BY cr.data_venda DESC");

                while (rs.next()) {
                    sb.append("Venda #").append(rs.getInt("venda_id"));
                    sb.append(" | ").append(FormatUtils.formatDate(rs.getString("data_venda")));
                    sb.append("\n  Original: R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor_original")));
                    sb.append(" | Pago: R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor_pago")));
                    sb.append(" | Pendente: R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor_pendente")));
                    sb.append(" | Status: ").append(rs.getString("status"));
                    sb.append("\n\n");
                }
                rs.close();
                stmt.close();

                hideLoading();
                final String extrato = sb.toString();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Extrato - " + clienteNome)
                            .setMessage(extrato)
                            .setPositiveButton("Fechar", null)
                            .setNeutralButton("WhatsApp", (d, w) -> {
                                WhatsAppHelper.enviarCupom(this, extrato, null);
                            })
                            .setNegativeButton("Imprimir", (d, w) -> {
                                new Thread(() -> {
                                    PrinterManager pm = new PrinterManager(this);
                                    if (pm.isImpressoraConfigurada()) {
                                        pm.imprimirTexto(extrato);
                                    } else {
                                        showToast("Impressora nao configurada");
                                    }
                                }).start();
                            })
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    /**
     * Exibe o historico de recebimentos de uma conta especifica.
     */
    private void showHistoricoRecebimentos(ContaReceber conta) {
        showLoading("Carregando historico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT * FROM recebimentos_conta WHERE conta_receber_id = " + conta.getId()
                        + " ORDER BY data_recebimento DESC");

                StringBuilder sb = new StringBuilder();
                sb.append("=== RECEBIMENTOS ===\n");
                sb.append("Venda #").append(conta.getVendaId()).append(" - ").append(conta.getClienteNome()).append("\n");
                sb.append("Valor Original: R$ ").append(FormatUtils.formatMoney(conta.getValorOriginal())).append("\n\n");

                boolean temRecebimentos = false;
                while (rs.next()) {
                    temRecebimentos = true;
                    sb.append(FormatUtils.formatDate(rs.getString("data_recebimento")));
                    sb.append(" | R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor")));
                    sb.append(" | ").append(FormatUtils.safeString(rs.getString("forma_pagamento")));
                    String obsRec = rs.getString("observacao");
                    if (obsRec != null && !obsRec.isEmpty()) {
                        sb.append("\n  Obs: ").append(obsRec);
                    }
                    sb.append("\n\n");
                }
                rs.close();
                stmt.close();

                if (!temRecebimentos) {
                    sb.append("Nenhum recebimento registrado.");
                }

                sb.append("\nPago: R$ ").append(FormatUtils.formatMoney(conta.getValorPago()));
                sb.append("\nPendente: R$ ").append(FormatUtils.formatMoney(conta.getValorPendente()));

                hideLoading();
                final String historico = sb.toString();
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Historico de Recebimentos")
                            .setMessage(historico)
                            .setPositiveButton("Fechar", null)
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }
}
