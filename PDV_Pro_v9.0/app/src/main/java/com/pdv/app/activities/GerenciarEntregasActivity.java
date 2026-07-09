package com.pdv.app.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Entrega;
import com.pdv.app.utils.*;
import com.pdv.app.whatsbot.WhatsAppAutoSender;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity para gerenciamento completo de entregas.
 * Permite visualizar, filtrar, atribuir entregadores, alterar status,
 * notificar clientes via WhatsApp e rastrear entregadores.
 *
 * Trata especialmente pedidos oriundos do WhatsApp Bot.
 *
 * v6.2.0 - NOTIFICACOES AUTOMATICAS:
 * Ao clicar nos botoes de notificacao, a mensagem e enviada automaticamente
 * via WhatsApp sem que o lojista precise abrir o app ou procurar o contato.
 */
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

public class GerenciarEntregasActivity extends BaseActivity {
    private static final String TAG = "GerenciarEntregas";

    private RecyclerView recyclerView;
    private GenericAdapter<Entrega> adapter;
    private EditText etBusca;
    private TextView tvContador, tvResumoPendentes, tvResumoEmRota, tvResumoEntregues, tvResumoTotal;
    private String filtroAtual = "todas";
    private List<Entrega> todasEntregas = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gerenciar_entregas);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.ENTREGAS_ACESSAR)) {
            return;
        }
        initViews();
        setupAdapter();
        setupFilters();
        setupSearch();
        loadData();
    }

    private void initViews() {
        etBusca = findViewById(R.id.etBusca);
        tvContador = findViewById(R.id.tvContador);
        tvResumoPendentes = findViewById(R.id.tvResumoPendentes);
        tvResumoEmRota = findViewById(R.id.tvResumoEmRota);
        tvResumoEntregues = findViewById(R.id.tvResumoEntregues);
        tvResumoTotal = findViewById(R.id.tvResumoTotal);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.btnAtualizar).setOnClickListener(v -> loadData());
        findViewById(R.id.btnBuscar).setOnClickListener(v -> aplicarFiltro());
    }

    private void setupAdapter() {
        adapter = new GenericAdapter<>(R.layout.item_entrega, (holder, item, pos) -> {
            // ID e origem
            String idText = "#" + item.getVendaId();
            if (item.isOrigemWhatsApp()) {
                idText += " [WhatsApp]";
            }
            holder.setText(R.id.tvEntregaId, idText);

            // Status com cor
            TextView tvStatus = holder.find(R.id.tvEntregaStatus);
            if (tvStatus != null) {
                tvStatus.setText(item.getStatusFormatado());
                setStatusBackground(tvStatus, item.getStatus());
            }

            // Cliente
            String clienteText = FormatUtils.safeString(item.getClienteNome());
            if (clienteText.isEmpty()) clienteText = "Cliente nao informado";
            holder.setText(R.id.tvEntregaCliente, clienteText);

            // Valor
            holder.setText(R.id.tvEntregaValor, "R$ " + FormatUtils.formatMoney(item.getTotalLiquido()));

            // Data
            holder.setText(R.id.tvEntregaData, FormatUtils.formatDate(item.getDataVenda()));

            // Entregador
            String entregadorText = item.getEntregadorNome() != null && !item.getEntregadorNome().isEmpty()
                    ? item.getEntregadorNome() : "Sem entregador";
            holder.setText(R.id.tvEntregaEntregador, entregadorText);

            // Observacao (origem WhatsApp)
            TextView tvObs = holder.find(R.id.tvEntregaObs);
            if (tvObs != null) {
                String obs = item.getObservacao();
                if (obs != null && !obs.isEmpty()) {
                    tvObs.setText(obs);
                    tvObs.setVisibility(View.VISIBLE);
                } else {
                    tvObs.setVisibility(View.GONE);
                }
            }
        });

        adapter.setOnItemClickListener((item, pos) -> showEntregaOptions(item));
        adapter.setOnItemLongClickListener((item, pos) -> showQuickStatusChange(item));
        recyclerView.setAdapter(adapter);
    }

    /**
     * Define a cor de fundo do badge de status.
     */
    private void setStatusBackground(TextView tv, String status) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20f);
        if (status == null) status = "";
        switch (status) {
            case "pendente":
                bg.setColor(Color.parseColor("#FFD740"));
                tv.setTextColor(Color.parseColor("#1A1A1A"));
                break;
            case "em_rota":
                bg.setColor(Color.parseColor("#00BCD4"));
                tv.setTextColor(Color.parseColor("#FFFFFF"));
                break;
            case "entregue":
                bg.setColor(Color.parseColor("#00E676"));
                tv.setTextColor(Color.parseColor("#1A1A1A"));
                break;
            case "cancelada":
                bg.setColor(Color.parseColor("#FF5252"));
                tv.setTextColor(Color.parseColor("#FFFFFF"));
                break;
            case "finalizada":
                bg.setColor(Color.parseColor("#00E676"));
                tv.setTextColor(Color.parseColor("#1A1A1A"));
                break;
            default:
                bg.setColor(Color.parseColor("#607D8B"));
                tv.setTextColor(Color.parseColor("#FFFFFF"));
                break;
        }
        tv.setBackground(bg);
    }

    private void setupFilters() {
        findViewById(R.id.btnFiltroTodas).setOnClickListener(v -> { filtroAtual = "todas"; aplicarFiltro(); });
        findViewById(R.id.btnFiltroPendente).setOnClickListener(v -> { filtroAtual = "pendente"; aplicarFiltro(); });
        findViewById(R.id.btnFiltroEmRota).setOnClickListener(v -> { filtroAtual = "em_rota"; aplicarFiltro(); });
        findViewById(R.id.btnFiltroEntregue).setOnClickListener(v -> { filtroAtual = "entregue"; aplicarFiltro(); });
        findViewById(R.id.btnFiltroCancelada).setOnClickListener(v -> { filtroAtual = "cancelada"; aplicarFiltro(); });
        findViewById(R.id.btnFiltroWhatsApp).setOnClickListener(v -> { filtroAtual = "whatsapp"; aplicarFiltro(); });

        // Estilizar botao WhatsApp com cor verde
        Button btnWhats = findViewById(R.id.btnFiltroWhatsApp);
        GradientDrawable whatsappBg = new GradientDrawable();
        whatsappBg.setCornerRadius(24f);
        whatsappBg.setColor(Color.parseColor("#25D366"));
        btnWhats.setBackground(whatsappBg);
    }

    private void setupSearch() {
        etBusca.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                aplicarFiltro();
            }
        });
    }

    /**
     * Aplica o filtro de status e busca textual sobre a lista completa.
     */
    private void aplicarFiltro() {
        String busca = etBusca.getText().toString().trim().toLowerCase();
        List<Entrega> filtradas = new ArrayList<>();

        for (Entrega e : todasEntregas) {
            // Filtro de status
            boolean passaFiltro;
            switch (filtroAtual) {
                case "pendente":
                    passaFiltro = "pendente".equals(e.getStatus());
                    break;
                case "em_rota":
                    passaFiltro = "em_rota".equals(e.getStatus());
                    break;
                case "entregue":
                    passaFiltro = "entregue".equals(e.getStatus()) || "finalizada".equals(e.getStatus());
                    break;
                case "cancelada":
                    passaFiltro = "cancelada".equals(e.getStatus());
                    break;
                case "whatsapp":
                    passaFiltro = e.isOrigemWhatsApp();
                    break;
                default:
                    passaFiltro = true;
                    break;
            }

            if (!passaFiltro) continue;

            // Filtro de busca textual
            if (!busca.isEmpty()) {
                String idStr = String.valueOf(e.getVendaId());
                String cliente = e.getClienteNome() != null ? e.getClienteNome().toLowerCase() : "";
                String entregador = e.getEntregadorNome() != null ? e.getEntregadorNome().toLowerCase() : "";
                String obs = e.getObservacao() != null ? e.getObservacao().toLowerCase() : "";

                if (!idStr.contains(busca) && !cliente.contains(busca)
                        && !entregador.contains(busca) && !obs.contains(busca)) {
                    continue;
                }
            }

            filtradas.add(e);
        }

        adapter.setItems(filtradas);
        tvContador.setText(filtradas.size() + " entrega(s) encontrada(s)");
    }

    /**
     * Carrega todas as entregas do banco de dados.
     */
    private void loadData() {
        showLoading("Carregando entregas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Buscar vendas que sao entregas (com entregador ou oriundas do WhatsApp ou pendentes)
                String sql = "SELECT v.id, v.cliente_id, v.entregador_id, v.data_venda, "
                        + "v.total_liquido, v.status, v.observacao, "
                        + "c.nome as cliente_nome, c.celular as cliente_celular, "
                        + "c.endereco as cliente_endereco, c.numero as cliente_numero, "
                        + "c.bairro as cliente_bairro, c.cidade as cliente_cidade, "
                        + "e.nome as entregador_nome, e.celular as entregador_celular, "
                        + "COALESCE(r.latitude, 0) as latitude, "
                        + "COALESCE(r.longitude, 0) as longitude, "
                        + "r.data_hora as ultima_posicao, "
                        + "COALESCE(r.ativo, 0) as gps_ativo "
                        + "FROM vendas v "
                        + "LEFT JOIN clientes c ON v.cliente_id = c.id "
                        + "LEFT JOIN entregadores e ON v.entregador_id = e.id "
                        + "LEFT JOIN rastreamento_entregador r ON v.entregador_id = r.entregador_id "
                        + "WHERE v.entregador_id > 0 "
                        + "OR v.observacao LIKE '%WhatsApp%' "
                        + "OR v.status = 'pendente' "
                        + "OR v.status = 'em_rota' "
                        + "OR v.para_entrega = 1 "
                        + "ORDER BY v.id DESC "
                        + "LIMIT 200";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);

                List<Entrega> entregas = new ArrayList<>();
                int pendentes = 0, emRota = 0, entregues = 0;
                double totalValor = 0;

                while (rs.next()) {
                    Entrega ent = new Entrega();
                    ent.setVendaId(rs.getInt("id"));
                    ent.setClienteId(rs.getInt("cliente_id"));
                    ent.setEntregadorId(rs.getInt("entregador_id"));
                    ent.setDataVenda(rs.getString("data_venda"));
                    ent.setTotalLiquido(rs.getDouble("total_liquido"));
                    ent.setStatus(rs.getString("status"));
                    ent.setObservacao(rs.getString("observacao"));
                    ent.setClienteNome(rs.getString("cliente_nome"));
                    ent.setClienteCelular(rs.getString("cliente_celular"));

                    // Montar endereco
                    String endereco = FormatUtils.safeString(rs.getString("cliente_endereco"));
                    String numero = FormatUtils.safeString(rs.getString("cliente_numero"));
                    String bairro = FormatUtils.safeString(rs.getString("cliente_bairro"));
                    String cidade = FormatUtils.safeString(rs.getString("cliente_cidade"));
                    StringBuilder endFull = new StringBuilder();
                    if (!endereco.isEmpty()) endFull.append(endereco);
                    if (!numero.isEmpty()) endFull.append(", ").append(numero);
                    if (!bairro.isEmpty()) endFull.append(" - ").append(bairro);
                    if (!cidade.isEmpty()) endFull.append(" - ").append(cidade);
                    ent.setClienteEndereco(endFull.toString());

                    ent.setEntregadorNome(rs.getString("entregador_nome"));
                    ent.setEntregadorCelular(rs.getString("entregador_celular"));
                    ent.setLatitude(rs.getDouble("latitude"));
                    ent.setLongitude(rs.getDouble("longitude"));
                    ent.setUltimaPosicao(rs.getString("ultima_posicao"));
                    ent.setGpsAtivo(rs.getBoolean("gps_ativo"));

                    // Verificar se e de WhatsApp
                    String obs = ent.getObservacao();
                    if (obs != null && obs.toLowerCase().contains("whatsapp")) {
                        ent.setOrigemWhatsApp(true);
                        String contato = ent.extrairContatoWhatsApp();
                        if (contato != null) ent.setContatoWhatsApp(contato);
                    }

                    // Contadores
                    if ("pendente".equals(ent.getStatus())) pendentes++;
                    else if ("em_rota".equals(ent.getStatus())) emRota++;
                    else if ("entregue".equals(ent.getStatus()) || "finalizada".equals(ent.getStatus())) entregues++;
                    totalValor += ent.getTotalLiquido();

                    entregas.add(ent);
                }
                rs.close();
                stmt.close();

                final int fp = pendentes, fr = emRota, fe = entregues;
                final double ft = totalValor;

                hideLoading();
                runOnUiThread(() -> {
                    todasEntregas = entregas;
                    aplicarFiltro();
                    tvResumoPendentes.setText("Pendentes: " + fp);
                    tvResumoEmRota.setText("Em Rota: " + fr);
                    tvResumoEntregues.setText("Entregues: " + fe);
                    tvResumoTotal.setText("Total: R$ " + FormatUtils.formatMoney(ft));
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao carregar entregas", e);
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    /**
     * Menu de opcoes ao clicar em uma entrega.
     */
    private void showEntregaOptions(Entrega entrega) {
        List<String> opcoes = new ArrayList<>();
        List<Runnable> acoes = new ArrayList<>();

        // Ver detalhes sempre disponivel
        opcoes.add("Ver Detalhes");
        acoes.add(() -> showDetalhesEntrega(entrega));

        // Alterar status
        opcoes.add("Alterar Status");
        acoes.add(() -> showAlterarStatus(entrega));

        // Atribuir/trocar entregador
        opcoes.add("Atribuir Entregador");
        acoes.add(() -> showAtribuirEntregador(entrega));

        // Rastrear entregador (se tiver)
        if (entrega.getEntregadorId() > 0) {
            opcoes.add("Rastrear Entregador");
            acoes.add(() -> rastrearEntregador(entrega));
        }

        // v6.2.0 - Notificar cliente via WhatsApp (AUTOMATICO)
        if (entrega.getClienteCelular() != null && !entrega.getClienteCelular().isEmpty()) {
            opcoes.add("\u2709 Notificar Cliente (Auto)");
            acoes.add(() -> notificarClienteWhatsAppAuto(entrega));
        }

        // Responder contato WhatsApp (se for pedido do bot) - AUTOMATICO
        if (entrega.isOrigemWhatsApp() && entrega.getContatoWhatsApp() != null) {
            opcoes.add("\u2709 Responder WhatsApp (Auto)");
            acoes.add(() -> responderWhatsAppAuto(entrega));
        }

        // Enviar localizacao do entregador ao cliente - AUTOMATICO
        if (entrega.getEntregadorId() > 0 && entrega.getLatitude() != 0) {
            opcoes.add("\u2709 Enviar Localizacao (Auto)");
            acoes.add(() -> enviarLocalizacaoClienteAuto(entrega));
        }

        // Reimprimir cupom
        opcoes.add("Reimprimir Cupom");
        acoes.add(() -> reimprimirCupom(entrega.getVendaId()));

        // Enviar cupom via WhatsApp - AUTOMATICO
        opcoes.add("\u2709 Enviar Cupom (Auto)");
        acoes.add(() -> enviarCupomWhatsAppAuto(entrega));

        // Cancelar entrega
        if (!"cancelada".equals(entrega.getStatus()) && !"entregue".equals(entrega.getStatus())) {
            opcoes.add("Cancelar Entrega");
            acoes.add(() -> cancelarEntrega(entrega));
        }

        String[] items = opcoes.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Entrega #" + entrega.getVendaId())
                .setItems(items, (d, w) -> acoes.get(w).run())
                .setNegativeButton("Fechar", null)
                .show();
    }

    /**
     * Menu rapido de alteracao de status ao segurar (long click).
     */
    private void showQuickStatusChange(Entrega entrega) {
        String[] statusOptions = {"Pendente", "Em Rota", "Entregue", "Cancelada"};
        String[] statusValues = {"pendente", "em_rota", "entregue", "cancelada"};

        new AlertDialog.Builder(this)
                .setTitle("Alterar Status - #" + entrega.getVendaId())
                .setItems(statusOptions, (d, w) -> {
                    atualizarStatus(entrega, statusValues[w]);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Exibe os detalhes completos de uma entrega.
     */
    private void showDetalhesEntrega(Entrega entrega) {
        showLoading("Carregando detalhes...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Buscar itens da venda
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT descricao_produto, quantidade, preco_unitario, total FROM itens_venda WHERE venda_id = ?");
                ps.setInt(1, entrega.getVendaId());
                ResultSet rs = ps.executeQuery();

                StringBuilder itens = new StringBuilder();
                while (rs.next()) {
                    itens.append("  - ").append(rs.getString("descricao_produto"))
                            .append(" (").append(FormatUtils.formatQuantidade(rs.getDouble("quantidade")))
                            .append("x R$ ").append(FormatUtils.formatMoney(rs.getDouble("preco_unitario")))
                            .append(") = R$ ").append(FormatUtils.formatMoney(rs.getDouble("total")))
                            .append("\n");
                }
                rs.close();
                ps.close();

                // Buscar pagamentos
                ps = conn.prepareStatement(
                        "SELECT pv.valor, fp.descricao FROM pagamentos_venda pv "
                        + "LEFT JOIN formas_pagamento fp ON pv.forma_pagamento_id = fp.id "
                        + "WHERE pv.venda_id = ?");
                ps.setInt(1, entrega.getVendaId());
                rs = ps.executeQuery();

                StringBuilder pagamentos = new StringBuilder();
                while (rs.next()) {
                    pagamentos.append("  - ").append(FormatUtils.safeString(rs.getString("descricao")))
                            .append(": R$ ").append(FormatUtils.formatMoney(rs.getDouble("valor")))
                            .append("\n");
                }
                rs.close();
                ps.close();

                final String itensStr = itens.toString();
                final String pagStr = pagamentos.toString();

                hideLoading();
                runOnUiThread(() -> {
                    StringBuilder msg = new StringBuilder();
                    msg.append("PEDIDO #").append(entrega.getVendaId()).append("\n");
                    msg.append("Status: ").append(entrega.getStatusFormatado()).append("\n");
                    msg.append("Data: ").append(FormatUtils.formatDate(entrega.getDataVenda())).append("\n");
                    msg.append("Valor: R$ ").append(FormatUtils.formatMoney(entrega.getTotalLiquido())).append("\n\n");

                    msg.append("CLIENTE\n");
                    msg.append("Nome: ").append(FormatUtils.safeString(entrega.getClienteNome())).append("\n");
                    if (entrega.getClienteCelular() != null && !entrega.getClienteCelular().isEmpty()) {
                        msg.append("Celular: ").append(entrega.getClienteCelular()).append("\n");
                    }
                    if (entrega.getClienteEndereco() != null && !entrega.getClienteEndereco().isEmpty()) {
                        msg.append("Endereco: ").append(entrega.getClienteEndereco()).append("\n");
                    }
                    msg.append("\n");

                    msg.append("ENTREGADOR\n");
                    if (entrega.getEntregadorNome() != null && !entrega.getEntregadorNome().isEmpty()) {
                        msg.append("Nome: ").append(entrega.getEntregadorNome()).append("\n");
                        if (entrega.getEntregadorCelular() != null) {
                            msg.append("Celular: ").append(entrega.getEntregadorCelular()).append("\n");
                        }
                        if (entrega.isGpsAtivo() && entrega.getLatitude() != 0) {
                            msg.append("GPS: ATIVO\n");
                            msg.append("Ultima posicao: ").append(FormatUtils.safeString(entrega.getUltimaPosicao())).append("\n");
                        }
                    } else {
                        msg.append("Nenhum entregador atribuido\n");
                    }
                    msg.append("\n");

                    if (entrega.isOrigemWhatsApp()) {
                        msg.append("ORIGEM: WhatsApp Bot\n");
                        if (entrega.getContatoWhatsApp() != null) {
                            msg.append("Contato: ").append(entrega.getContatoWhatsApp()).append("\n");
                        }
                        msg.append("\n");
                    }

                    if (!itensStr.isEmpty()) {
                        msg.append("ITENS\n").append(itensStr).append("\n");
                    }

                    if (!pagStr.isEmpty()) {
                        msg.append("PAGAMENTOS\n").append(pagStr).append("\n");
                    }

                    if (entrega.getObservacao() != null && !entrega.getObservacao().isEmpty()) {
                        msg.append("OBSERVACAO\n").append(entrega.getObservacao()).append("\n");
                    }

                    new AlertDialog.Builder(GerenciarEntregasActivity.this)
                            .setTitle("Detalhes da Entrega #" + entrega.getVendaId())
                            .setMessage(msg.toString())
                            .setPositiveButton("Fechar", null)
                            .setNeutralButton("Compartilhar", (d, w) -> {
                                WhatsAppHelper.compartilharTexto(GerenciarEntregasActivity.this,
                                        msg.toString(), "Compartilhar detalhes da entrega");
                            })
                            .show();
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao carregar detalhes", e);
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    /**
     * Dialog para alterar o status da entrega.
     */
    private void showAlterarStatus(Entrega entrega) {
        String[] statusOptions = {"Pendente", "Em Rota", "Entregue", "Cancelada"};
        String[] statusValues = {"pendente", "em_rota", "entregue", "cancelada"};

        // Marcar o status atual
        int checkedItem = -1;
        for (int i = 0; i < statusValues.length; i++) {
            if (statusValues[i].equals(entrega.getStatus())) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Alterar Status - #" + entrega.getVendaId())
                .setSingleChoiceItems(statusOptions, checkedItem, null)
                .setPositiveButton("Confirmar", (d, w) -> {
                    int selected = ((AlertDialog) d).getListView().getCheckedItemPosition();
                    if (selected >= 0) {
                        atualizarStatus(entrega, statusValues[selected]);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Atualiza o status de uma entrega no banco de dados.
     * v6.2.0: Se o status mudar para 'em_rota' ou 'entregue', envia
     * notificacao AUTOMATICA ao cliente via WhatsApp.
     */
    private void atualizarStatus(Entrega entrega, String novoStatus) {
        showLoading("Atualizando status...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE vendas SET status = ? WHERE id = ?");
                ps.setString(1, novoStatus);
                ps.setInt(2, entrega.getVendaId());
                ps.executeUpdate();
                ps.close();

                hideLoading();
                String statusAnterior = entrega.getStatus();
                entrega.setStatus(novoStatus);

                runOnUiThread(() -> {
                    showToast("Status atualizado para: " + entrega.getStatusFormatado());
                    loadData();

                    // v6.2.0 - Enviar notificacao AUTOMATICA ao cliente
                    if (("em_rota".equals(novoStatus) || "entregue".equals(novoStatus))
                            && !novoStatus.equals(statusAnterior)) {
                        enviarNotificacaoAutomatica(entrega, novoStatus);
                    }
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao atualizar status", e);
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * v6.2.0 - Envia notificacao AUTOMATICA ao cliente quando o status muda.
     * Nao precisa abrir o WhatsApp nem procurar o contato.
     */
    private void enviarNotificacaoAutomatica(Entrega entrega, String novoStatus) {
        String celular = entrega.getClienteCelular();
        String contatoWhats = entrega.getContatoWhatsApp();
        String destino = (contatoWhats != null && !contatoWhats.isEmpty()) ? contatoWhats : celular;

        if (destino == null || destino.isEmpty()) return;

        // Enviar automaticamente sem perguntar
        WhatsAppAutoSender.notificarStatusEntrega(
                this,
                entrega.getVendaId(),
                novoStatus,
                destino,
                entrega.getEntregadorNome()
        );

        showToast("\u2709 Notificacao enviada automaticamente ao cliente!");
    }

    /**
     * Dialog para atribuir ou trocar o entregador de uma entrega.
     */
    private void showAtribuirEntregador(Entrega entrega) {
        showLoading("Carregando entregadores...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, nome, celular FROM entregadores WHERE ativo = 1 ORDER BY nome");

                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                nomes.add("Nenhum (remover entregador)");
                ids.add(0);

                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    String nome = rs.getString("nome");
                    String cel = rs.getString("celular");
                    nomes.add(nome + (cel != null && !cel.isEmpty() ? " (" + cel + ")" : ""));
                }
                rs.close();
                stmt.close();

                hideLoading();
                runOnUiThread(() -> {
                    String[] items = nomes.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Atribuir Entregador - #" + entrega.getVendaId())
                            .setItems(items, (d, w) -> {
                                int entregadorId = ids.get(w);
                                atribuirEntregador(entrega, entregadorId);
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao carregar entregadores", e);
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    /**
     * Atribui um entregador a uma entrega.
     */
    private void atribuirEntregador(Entrega entrega, int entregadorId) {
        showLoading("Atribuindo entregador...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE vendas SET entregador_id = ? WHERE id = ?");
                ps.setInt(1, entregadorId);
                ps.setInt(2, entrega.getVendaId());
                ps.executeUpdate();
                ps.close();

                hideLoading();
                runOnUiThread(() -> {
                    showToast(entregadorId > 0 ? "Entregador atribuido!" : "Entregador removido!");
                    loadData();
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao atribuir entregador", e);
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * Abre o Google Maps com a localizacao do entregador.
     */
    private void rastrearEntregador(Entrega entrega) {
        if (entrega.getLatitude() != 0 && entrega.getLongitude() != 0) {
            StringBuilder msg = new StringBuilder();
            msg.append("Entregador: ").append(FormatUtils.safeString(entrega.getEntregadorNome())).append("\n");
            msg.append("GPS: ").append(entrega.isGpsAtivo() ? "ATIVO" : "INATIVO").append("\n");
            msg.append("Ultima posicao: ").append(FormatUtils.safeString(entrega.getUltimaPosicao())).append("\n");
            msg.append("Coordenadas: ").append(String.format("%.6f, %.6f", entrega.getLatitude(), entrega.getLongitude())).append("\n\n");
            msg.append("Deseja abrir no Google Maps?");

            showConfirm("Rastrear Entregador", msg.toString(), () -> {
                try {
                    String url = "https://maps.google.com/?q=" + entrega.getLatitude() + "," + entrega.getLongitude();
                    android.content.Intent intent = new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    showToast("Erro ao abrir mapa");
                }
            });
        } else {
            showInfo("Rastreamento", "Nenhuma posicao GPS disponivel para este entregador.\n\n"
                    + "O entregador precisa estar com o Modo Entregador ativo no aplicativo.");
        }
    }

    /**
     * v6.2.0 - Notifica o cliente via WhatsApp AUTOMATICAMENTE.
     * O lojista escolhe o tipo de notificacao e o envio e feito em segundo plano.
     */
    private void notificarClienteWhatsAppAuto(Entrega entrega) {
        String[] opcoes = {
                "\uD83D\uDCE6 Pedido em preparacao",
                "\uD83D\uDE9A Pedido saiu para entrega",
                "\u2705 Pedido entregue",
                "\u23F0 Informar previsao de entrega",
                "\u270D Mensagem personalizada"
        };

        String celular = entrega.getClienteCelular();
        if (celular == null || celular.isEmpty()) {
            celular = entrega.getContatoWhatsApp();
        }
        final String destino = celular;

        if (destino == null || destino.isEmpty()) {
            showToast("Cliente sem numero de celular cadastrado");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("\u2709 Notificar Cliente (Automatico)")
                .setItems(opcoes, (d, w) -> {
                    String mensagem;
                    switch (w) {
                        case 0:
                            mensagem = "Ola! Seu pedido #" + entrega.getVendaId()
                                    + " esta sendo preparado. Em breve sairemos para entrega! \uD83C\uDF7D";
                            break;
                        case 1:
                            mensagem = "Ola! Seu pedido #" + entrega.getVendaId()
                                    + " saiu para entrega! \uD83D\uDE9A";
                            if (entrega.getEntregadorNome() != null) {
                                mensagem += "\nEntregador: " + entrega.getEntregadorNome();
                            }
                            if (entrega.getEntregadorCelular() != null && !entrega.getEntregadorCelular().isEmpty()) {
                                mensagem += "\nContato: " + entrega.getEntregadorCelular();
                            }
                            break;
                        case 2:
                            mensagem = "Ola! Seu pedido #" + entrega.getVendaId()
                                    + " foi entregue com sucesso! Obrigado pela preferencia! \uD83D\uDE0A\uD83D\uDE4F";
                            break;
                        case 3:
                            mensagem = "Ola! Seu pedido #" + entrega.getVendaId()
                                    + " tem previsao de entrega para os proximos minutos. Aguarde! \u23F0";
                            break;
                        default:
                            mensagem = "Ola! Sobre seu pedido #" + entrega.getVendaId()
                                    + " (R$ " + FormatUtils.formatMoney(entrega.getTotalLiquido()) + "): ";
                            // Para mensagem personalizada, abrir input
                            showInputDialog(entrega, destino);
                            return;
                    }

                    // ENVIO AUTOMATICO - sem abrir WhatsApp
                    WhatsAppAutoSender.enviarAutomatico(this, mensagem, destino);
                    showToast("\u2709 Notificacao enviada automaticamente!");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * v6.2.0 - Dialog para mensagem personalizada com envio automatico.
     */
    private void showInputDialog(Entrega entrega, String destino) {
        EditText input = new EditText(this);
        input.setHint("Digite a mensagem para o cliente...");
        input.setText("Ola! Sobre seu pedido #" + entrega.getVendaId()
                + " (R$ " + FormatUtils.formatMoney(entrega.getTotalLiquido()) + "): ");
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Mensagem Personalizada")
                .setView(input)
                .setPositiveButton("Enviar Automatico", (d, w) -> {
                    String msg = input.getText().toString().trim();
                    if (!msg.isEmpty()) {
                        WhatsAppAutoSender.enviarAutomatico(this, msg, destino);
                        showToast("\u2709 Mensagem enviada automaticamente!");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * v6.2.0 - Responde ao contato WhatsApp AUTOMATICAMENTE.
     */
    private void responderWhatsAppAuto(Entrega entrega) {
        String contato = entrega.getContatoWhatsApp();
        String mensagem = "Ola! Sobre seu pedido #" + entrega.getVendaId()
                + " no valor de R$ " + FormatUtils.formatMoney(entrega.getTotalLiquido()) + ":\n\n"
                + "Status: " + entrega.getStatusFormatado();

        if (entrega.getEntregadorNome() != null && !entrega.getEntregadorNome().isEmpty()) {
            mensagem += "\nEntregador: " + entrega.getEntregadorNome();
        }

        // ENVIO AUTOMATICO
        WhatsAppAutoSender.enviarAutomatico(this, mensagem, contato);
        showToast("\u2709 Resposta enviada automaticamente!");
    }

    /**
     * v6.2.0 - Envia a localizacao do entregador ao cliente AUTOMATICAMENTE.
     */
    private void enviarLocalizacaoClienteAuto(Entrega entrega) {
        String celular = entrega.getClienteCelular();
        if (celular == null || celular.isEmpty()) {
            celular = entrega.getContatoWhatsApp();
        }

        String mensagem = "Localizacao do seu entregador para o pedido #" + entrega.getVendaId() + ":\n\n"
                + "Entregador: " + FormatUtils.safeString(entrega.getEntregadorNome()) + "\n"
                + "Veja no mapa: https://maps.google.com/?q="
                + entrega.getLatitude() + "," + entrega.getLongitude();

        // ENVIO AUTOMATICO
        WhatsAppAutoSender.enviarAutomatico(this, mensagem, celular);
        showToast("\u2709 Localizacao enviada automaticamente!");
    }

    /**
     * Reimprime o cupom da venda.
     */
    private void reimprimirCupom(int vendaId) {
        new Thread(() -> {
            CupomGenerator gen = new CupomGenerator(this);
            String cupom = gen.gerarCupom(vendaId);
            PrinterManager pm = new PrinterManager(this);
            if (pm.isImpressoraConfigurada()) {
                boolean ok = pm.imprimirTexto(cupom);
                showToast(ok ? "Cupom impresso!" : "Erro na impressao");
            } else {
                showToast("Impressora nao configurada");
            }
        }).start();
    }

    /**
     * v6.2.0 - Envia o cupom via WhatsApp AUTOMATICAMENTE.
     */
    private void enviarCupomWhatsAppAuto(Entrega entrega) {
        String celular = entrega.getClienteCelular();
        if (celular == null || celular.isEmpty()) {
            celular = entrega.getContatoWhatsApp();
        }
        final String destino = celular;

        if (destino == null || destino.isEmpty()) {
            showToast("Cliente sem numero de celular cadastrado");
            return;
        }

        new Thread(() -> {
            CupomGenerator gen = new CupomGenerator(this);
            String cupom = gen.gerarCupom(entrega.getVendaId());
            runOnUiThread(() -> {
                WhatsAppAutoSender.enviarCupomAutomatico(this, cupom, destino);
                showToast("\u2709 Cupom enviado automaticamente!");
            });
        }).start();
    }

    /**
     * Cancela uma entrega com confirmacao.
     */
    private void cancelarEntrega(Entrega entrega) {
        showConfirm("Cancelar Entrega",
                "Deseja cancelar a entrega #" + entrega.getVendaId() + "?\n\n"
                        + "Cliente: " + FormatUtils.safeString(entrega.getClienteNome()) + "\n"
                        + "Valor: R$ " + FormatUtils.formatMoney(entrega.getTotalLiquido()),
                () -> atualizarStatus(entrega, "cancelada"));
    }
}
