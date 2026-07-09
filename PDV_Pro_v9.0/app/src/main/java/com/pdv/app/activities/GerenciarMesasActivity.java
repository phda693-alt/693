package com.pdv.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.MesaAdapter;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.MultiPrinterManager;
import com.pdv.app.utils.PrinterManager;
import com.pdv.app.utils.TaxaServicoPreferences;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tela de gerenciamento visual de mesas.
 * Exibe todas as mesas em grid com cores por status:
 * - Verde: mesa livre (sem produtos)
 * - Vermelho: mesa ocupada (com produtos)
 * - Laranja: mesa reservada
 * - Azul: mesa pronta para cobranca (v6.8.0)
 * 
 * Ao clicar em uma mesa, abre dialog para:
 * - Escolher garcom
 * - Definir quantidade de pessoas
 * - Adicionar/remover produtos (com adicionais)
 * - Limpar mesa
 * - Reservar mesa (v6.7.8)
 * - Marcar como pronta para cobranca (v6.8.0)
 * - Encaminhar para pagamento (v6.7.7)
 * - Imprimir pedido completo (v6.7.9)
 * - Imprimir ultimo item pedido - impressao unica por item (v6.7.9)
 *
 * v6.7.6 - Adicionado suporte a adicionais nos itens de mesa
 * v6.7.7 - Adicionado botao "Encaminhar p/ Pagamento" para mesas ocupadas
 * v6.7.8 - Adicionado botao "Reservar Mesa" com controle de acesso por usuario logado.
 *          Apenas o usuario que reservou pode adicionar/alterar itens na mesa reservada.
 *          Outros usuarios veem quem reservou mas nao podem alterar.
 * v6.7.9 - Adicionado botoes "Imprimir Pedido Completo" e "Imprimir Ultimo Item Pedido".
 *          O botao "Imprimir Ultimo Item Pedido" so pode ser impresso uma unica vez por item.
 *          Controle via coluna 'impresso' na tabela itens_mesa.
 * v6.8.0 - Adicionado botao "Pronta p/ Cobranca" para mesas ocupadas ou reservadas.
 *          Marca a mesa com status 'pronta' (cor azul) indicando que esta pronta para
 *          ser enviada para a tela de venda para cobranca e fechamento.
 * v6.9.9 - Corrigido botao "Imprimir Ultimo Item Pedido" para permitir imprimir
 *          o ultimo item adicionado ANTES de salvar a mesa. Se o item ainda nao foi
 *          salvo (id == 0), imprime usando os dados em memoria e marca como impresso
 *          na lista local. Ao salvar a mesa, o flag impresso = 1 e preservado no banco.
 */
public class GerenciarMesasActivity extends BaseActivity {
    private static final String TAG = "GerenciarMesas";
    private static final int REQUEST_PAGAMENTO_MESA = 300;
    private static final int REQUEST_SCAN_MESA_PRODUTO = 301;
    private RecyclerView recyclerMesas;
    private MesaAdapter mesaAdapter;

    // Dados carregados para uso nos dialogs
    private List<Map<String, Object>> garconsList = new ArrayList<>();
    private List<Map<String, Object>> produtosList = new ArrayList<>();

    // v6.7.7 - Controle da mesa sendo encaminhada para pagamento
    private int mesaIdPagamento = 0;
    private int ocupacaoIdPagamento = 0;
    private AlertDialog dialogMesaAtual = null;

    // Leitor de codigo de barras dentro da mesa aberta
    private List<Map<String, Object>> scannerMesaItens = null;
    private GenericAdapter<Map<String, Object>> scannerMesaAdapter = null;
    private View scannerMesaDialogView = null;
    private Spinner scannerMesaSpProduto = null;
    private EditText scannerMesaEtQtdProduto = null;
    private EditText scannerMesaEtObservacaoCozinha = null;
    private int scannerMesaId = 0;
    private int[] scannerMesaOcupacaoIdHolder = null;
    private boolean scannerMesaPodeEditar = true;
    private String scannerMesaReservadoPorNome = "";

    // v6.7.8 - Dados do usuario logado
    private int usuarioLogadoId = 0;
    private String usuarioLogadoNome = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gerenciar_mesas);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.GERENCIAR_MESAS_ACESSAR)) {
            return;
        }

        // v6.7.8 - Obter dados do usuario logado
        SharedPreferences sessionPrefs = getSharedPreferences("session", MODE_PRIVATE);
        usuarioLogadoId = sessionPrefs.getInt("user_id", 0);
        usuarioLogadoNome = sessionPrefs.getString("user_nome", "");

        recyclerMesas = findViewById(R.id.recyclerMesas);
        recyclerMesas.setLayoutManager(new GridLayoutManager(this, 3));

        mesaAdapter = new MesaAdapter((mesa, position) -> {
            abrirDetalheMesa(mesa);
        });
        recyclerMesas.setAdapter(mesaAdapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        View btnAssistenteMesas = findViewById(R.id.btnAssistenteMesas);
        if (btnAssistenteMesas != null) {
            if (PermissionHelper.verificarSilencioso(this, PermissionConstants.MESAS_CRIAR)) {
                btnAssistenteMesas.setVisibility(View.VISIBLE);
                btnAssistenteMesas.setOnClickListener(v -> abrirAssistenteCriacaoMesas());
            } else {
                btnAssistenteMesas.setVisibility(View.GONE);
            }
        }

        carregarDadosAuxiliares();
        carregarMesas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarMesas();
    }


    private String normalizarObservacaoCozinha(String texto) {
        if (texto == null) return "";
        String out = texto.trim().replace("\r", " ").replace("\n", " ").replaceAll("[ \t]+", " ");
        if (out.length() > 120) out = out.substring(0, 120).trim();
        return out;
    }

    private void abrirAssistenteCriacaoMesas() {
        if (!PermissionHelper.verificar(this, PermissionConstants.MESAS_CRIAR)) return;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        TextView info = new TextView(this);
        info.setText("Assistente para criar varias mesas de uma vez. Mesas existentes nao serao duplicadas.");
        info.setTextColor(0xFFB0BEC5);
        info.setTextSize(13);
        info.setPadding(0, 0, 0, 12);
        container.addView(info);

        EditText etInicio = criarCampoAssistente("Numero inicial", "1", android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etFim = criarCampoAssistente("Numero final", "20", android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etCapacidade = criarCampoAssistente("Capacidade padrao por mesa", "4", android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etDescricao = criarCampoAssistente("Descricao/prefixo opcional", "Mesa", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView(etInicio);
        container.addView(etFim);
        container.addView(etCapacidade);
        container.addView(etDescricao);

        CheckBox cbSobrescrever = new CheckBox(this);
        cbSobrescrever.setText("Atualizar capacidade/descricao das mesas que ja existem");
        cbSobrescrever.setTextColor(0xFFFFFFFF);
        cbSobrescrever.setTextSize(13);
        cbSobrescrever.setPadding(0, 12, 0, 4);
        container.addView(cbSobrescrever);

        new AlertDialog.Builder(this)
                .setTitle("Assistente de Criacao de Mesas")
                .setView(dialogView)
                .setPositiveButton("Criar Mesas", (d, w) -> executarAssistenteCriacaoMesas(
                        etInicio.getText().toString(), etFim.getText().toString(),
                        etCapacidade.getText().toString(), etDescricao.getText().toString(),
                        cbSobrescrever.isChecked()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText criarCampoAssistente(String hint, String valor, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(valor);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF90A4AE);
        et.setBackgroundResource(R.drawable.input_bg);
        et.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        et.setLayoutParams(lp);
        return et;
    }

    private void executarAssistenteCriacaoMesas(String inicioStr, String fimStr, String capacidadeStr,
                                                 String descricaoPrefixo, boolean sobrescreverExistentes) {
        int inicio, fim, capacidade;
        try { inicio = Integer.parseInt(inicioStr.trim()); } catch (Exception e) { showError("Numero inicial invalido."); return; }
        try { fim = Integer.parseInt(fimStr.trim()); } catch (Exception e) { showError("Numero final invalido."); return; }
        try { capacidade = Integer.parseInt(capacidadeStr.trim()); } catch (Exception e) { capacidade = 4; }
        if (inicio <= 0 || fim <= 0 || fim < inicio) { showError("Intervalo de mesas invalido."); return; }
        if ((fim - inicio) > 300) { showError("Por seguranca, crie no maximo 300 mesas por vez."); return; }
        if (capacidade <= 0) capacidade = 4;
        final int fInicio = inicio, fFim = fim, fCapacidade = capacidade;
        final String fDescricao = descricaoPrefixo == null ? "" : descricaoPrefixo.trim();

        showLoading("Criando mesas...");
        new Thread(() -> {
            int criadas = 0, atualizadas = 0, ignoradas = 0;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                for (int n = fInicio; n <= fFim; n++) {
                    PreparedStatement check = conn.prepareStatement("SELECT id FROM mesas WHERE numero = ? LIMIT 1");
                    check.setInt(1, n);
                    ResultSet rs = check.executeQuery();
                    int idExistente = 0;
                    if (rs.next()) idExistente = rs.getInt("id");
                    rs.close();
                    check.close();
                    String desc = fDescricao.isEmpty() ? "Mesa " + n : fDescricao + " " + n;
                    if (idExistente > 0) {
                        if (sobrescreverExistentes) {
                            PreparedStatement ps = conn.prepareStatement("UPDATE mesas SET descricao=?, capacidade=?, ativa=1 WHERE id=?");
                            ps.setString(1, desc);
                            ps.setInt(2, fCapacidade);
                            ps.setInt(3, idExistente);
                            ps.executeUpdate();
                            ps.close();
                            atualizadas++;
                        } else {
                            ignoradas++;
                        }
                    } else {
                        PreparedStatement ps = conn.prepareStatement("INSERT INTO mesas (numero, descricao, capacidade, ativa) VALUES (?,?,?,1)");
                        ps.setInt(1, n);
                        ps.setString(2, desc);
                        ps.setInt(3, fCapacidade);
                        ps.executeUpdate();
                        ps.close();
                        criadas++;
                    }
                }
                hideLoading();
                final int fc = criadas, fa = atualizadas, fi = ignoradas;
                runOnUiThread(() -> {
                    showToast("Assistente concluido: " + fc + " criadas, " + fa + " atualizadas, " + fi + " ignoradas.");
                    carregarMesas();
                });
            } catch (Exception e) {
                hideLoading();
                runOnUiThread(() -> showError("Erro no assistente de mesas: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Carrega garcons, produtos e adicionais para uso nos dialogs.
     */
    private void carregarDadosAuxiliares() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Carregar garcons
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM garcons WHERE ativo = 1 ORDER BY nome");
                garconsList.clear();
                while (rs.next()) {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("id", rs.getInt("id"));
                    g.put("nome", rs.getString("nome"));
                    garconsList.add(g);
                }
                rs.close();
                stmt.close();

                // Carregar produtos (incluindo tipo_produto_id para adicionais)
                stmt = conn.createStatement();
                rs = stmt.executeQuery("SELECT id, descricao, preco_venda, tipo_produto_id FROM produtos WHERE ativo = 1 ORDER BY descricao");
                produtosList.clear();
                while (rs.next()) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", rs.getInt("id"));
                    p.put("descricao", rs.getString("descricao"));
                    p.put("preco_venda", rs.getDouble("preco_venda"));
                    try {
                        p.put("tipo_produto_id", rs.getInt("tipo_produto_id"));
                    } catch (Exception ignored) {
                        p.put("tipo_produto_id", 0);
                    }
                    produtosList.add(p);
                }
                rs.close();
                stmt.close();

            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar dados auxiliares: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Carrega todas as mesas com seus status e dados.
     * v6.7.8 - Agora carrega tambem dados de reserva (usuario que reservou).
     */
    private void carregarMesas() {
        showLoading("Carregando mesas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                String sql = "SELECT m.id, m.numero, m.descricao, m.capacidade, " +
                        "COALESCE(om.garcom_id, 0) as garcom_id, " +
                        "COALESCE(g.nome, '') as garcom_nome, " +
                        "COALESCE(om.qtd_pessoas, 0) as qtd_pessoas, " +
                        "COALESCE(om.status, 'livre') as status, " +
                        "COALESCE(om.id, 0) as ocupacao_id, " +
                        "COALESCE(om.reservado_por_usuario_id, 0) as reservado_por_usuario_id, " +
                        "COALESCE(om.reservado_por_usuario_nome, '') as reservado_por_usuario_nome " +
                        "FROM mesas m " +
                        "LEFT JOIN ocupacao_mesa om ON m.id = om.mesa_id AND om.status != 'encerrada' " +
                        "LEFT JOIN garcons g ON om.garcom_id = g.id " +
                        "WHERE m.ativa = 1 " +
                        "ORDER BY m.numero ASC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> mesasList = new ArrayList<>();

                while (rs.next()) {
                    Map<String, Object> mesa = new LinkedHashMap<>();
                    int mesaId = rs.getInt("id");
                    mesa.put("id", mesaId);
                    mesa.put("numero", rs.getInt("numero"));
                    mesa.put("descricao", rs.getString("descricao"));
                    mesa.put("capacidade", rs.getInt("capacidade"));
                    mesa.put("garcom_id", rs.getInt("garcom_id"));
                    mesa.put("garcom_nome", rs.getString("garcom_nome"));
                    mesa.put("qtd_pessoas", rs.getInt("qtd_pessoas"));
                    mesa.put("status", rs.getString("status"));
                    mesa.put("ocupacao_id", rs.getInt("ocupacao_id"));

                    // v6.7.8 - Dados de reserva
                    int reservadoPorId = 0;
                    String reservadoPorNome = "";
                    try {
                        reservadoPorId = rs.getInt("reservado_por_usuario_id");
                    } catch (Exception ignored) {}
                    try {
                        reservadoPorNome = rs.getString("reservado_por_usuario_nome");
                        if (reservadoPorNome == null) reservadoPorNome = "";
                    } catch (Exception ignored) {}
                    mesa.put("reservado_por_usuario_id", reservadoPorId);
                    mesa.put("reservado_por_usuario_nome", reservadoPorNome);

                    // Contar itens e total (incluindo adicionais)
                    int ocupacaoId = rs.getInt("ocupacao_id");
                    if (ocupacaoId > 0) {
                        PreparedStatement psItens = conn.prepareStatement(
                                "SELECT COUNT(*) as qtd, COALESCE(SUM(total + COALESCE(adicionais_total, 0)), 0) as total_val FROM itens_mesa WHERE ocupacao_id = ?");
                        psItens.setInt(1, ocupacaoId);
                        ResultSet rsItens = psItens.executeQuery();
                        if (rsItens.next()) {
                            mesa.put("qtd_itens", rsItens.getInt("qtd"));
                            mesa.put("total", rsItens.getDouble("total_val"));
                        }
                        rsItens.close();
                        psItens.close();
                    } else {
                        mesa.put("qtd_itens", 0);
                        mesa.put("total", 0.0);
                    }

                    mesasList.add(mesa);
                }
                rs.close();
                stmt.close();

                hideLoading();
                runOnUiThread(() -> mesaAdapter.setMesas(mesasList));

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    /**
     * Abre o dialog de detalhe da mesa para gerenciar produtos, garcom e pessoas.
     * v6.7.6 - Agora suporta adicionais nos produtos.
     * v6.7.7 - Agora inclui botao "Encaminhar p/ Pagamento" para mesas ocupadas.
     * v6.7.8 - Agora inclui botao "Reservar Mesa" e controle de acesso por usuario.
     *          Se a mesa esta reservada por outro usuario, bloqueia edicao.
     * v6.7.9 - Agora inclui botoes "Imprimir Pedido Completo" e "Imprimir Ultimo Item Pedido".
     * v6.8.0 - Agora inclui botao "Pronta p/ Cobranca" para marcar mesa como pronta (azul).
     */
    private void abrirDetalheMesa(Map<String, Object> mesa) {
        final int mesaId = ((Number) mesa.get("id")).intValue();
        final int mesaNumero = ((Number) mesa.get("numero")).intValue();
        final int ocupacaoId = ((Number) mesa.get("ocupacao_id")).intValue();
        // v7.0.3 - AUTOSAVE: ocupacaoIdHolder mutavel para permitir criacao de nova ocupacao
        final int[] ocupacaoIdHolder = new int[]{ ocupacaoId };
        final String statusAtual = mesa.get("status") != null ? mesa.get("status").toString() : "livre";

        // v6.7.8 - Dados de reserva
        final int reservadoPorId = mesa.get("reservado_por_usuario_id") != null
                ? ((Number) mesa.get("reservado_por_usuario_id")).intValue() : 0;
        final String reservadoPorNome = mesa.get("reservado_por_usuario_nome") != null
                ? mesa.get("reservado_por_usuario_nome").toString() : "";

        // v6.7.8 - Verificar se o usuario logado e o dono da reserva
        final boolean mesaReservada = "reservada".equals(statusAtual) && reservadoPorId > 0;
        final boolean usuarioEhDono = (reservadoPorId == usuarioLogadoId);
        final boolean podeEditar = !mesaReservada || usuarioEhDono;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_mesa_detalhe, null);

        // v6.7.8 - Exibir info de reserva
        LinearLayout llReservaInfo = dialogView.findViewById(R.id.llReservaInfo);
        TextView tvReservaStatus = dialogView.findViewById(R.id.tvReservaStatus);
        TextView tvReservadoPorInfo = dialogView.findViewById(R.id.tvReservadoPorInfo);

        if (mesaReservada) {
            llReservaInfo.setVisibility(View.VISIBLE);
            tvReservaStatus.setText("MESA RESERVADA");
            tvReservadoPorInfo.setText("Reservado por: " + reservadoPorNome);

            if (!podeEditar) {
                // Mostrar aviso de bloqueio
                tvReservaStatus.setText("MESA RESERVADA (BLOQUEADA)");
                tvReservaStatus.setTextColor(0xFFFF5252);
            }
        } else if ("pronta".equals(statusAtual)) {
            // v6.8.0 - Exibir info de pronta para cobranca
            llReservaInfo.setVisibility(View.VISIBLE);
            tvReservaStatus.setText("PRONTA PARA COBRAN\u00c7A");
            tvReservaStatus.setTextColor(0xFF42A5F5);
            tvReservadoPorInfo.setText("Esta mesa esta pronta para ser cobrada e fechada.");
            tvReservadoPorInfo.setTextColor(0xFF90CAF9);
        } else {
            llReservaInfo.setVisibility(View.GONE);
        }

        // Spinner Garcom
        Spinner spGarcom = dialogView.findViewById(R.id.spGarcom);
        List<String> garcomNomes = new ArrayList<>();
        garcomNomes.add("-- Selecione Garcom --");
        for (Map<String, Object> g : garconsList) {
            garcomNomes.add(g.get("nome").toString());
        }
        ArrayAdapter<String> garcomAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, garcomNomes);
        garcomAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spGarcom.setAdapter(garcomAdapter);

        // Selecionar garcom atual
        int garcomIdAtual = mesa.get("garcom_id") != null ? ((Number) mesa.get("garcom_id")).intValue() : 0;
        if (garcomIdAtual > 0) {
            for (int i = 0; i < garconsList.size(); i++) {
                if (((Number) garconsList.get(i).get("id")).intValue() == garcomIdAtual) {
                    spGarcom.setSelection(i + 1);
                    break;
                }
            }
        }

        // Pessoas
        EditText etPessoas = dialogView.findViewById(R.id.etPessoas);
        int pessoasAtual = mesa.get("qtd_pessoas") != null ? ((Number) mesa.get("qtd_pessoas")).intValue() : 0;
        if (pessoasAtual > 0) etPessoas.setText(String.valueOf(pessoasAtual));

        // Spinner Produto
        Spinner spProduto = dialogView.findViewById(R.id.spProduto);
        List<String> produtoNomes = new ArrayList<>();
        produtoNomes.add("-- Selecione Produto --");
        for (Map<String, Object> p : produtosList) {
            produtoNomes.add(p.get("descricao").toString() + " - R$ " + String.format("%.2f", ((Number) p.get("preco_venda")).doubleValue()));
        }
        ArrayAdapter<String> produtoAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, produtoNomes);
        produtoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProduto.setAdapter(produtoAdapter);

        EditText etQtdProduto = dialogView.findViewById(R.id.etQtdProduto);
        EditText etObservacaoCozinhaItem = dialogView.findViewById(R.id.etObservacaoCozinhaItem);
        // RecyclerView de produtos na mesa
        RecyclerView recyclerProdutos = dialogView.findViewById(R.id.recyclerProdutosMesa);
        recyclerProdutos.setLayoutManager(new LinearLayoutManager(this));

        // Lista de itens da mesa (carregada do banco)
        final List<Map<String, Object>> itensMesa = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final GenericAdapter<Map<String, Object>>[] adapterHolder = new GenericAdapter[1];
        adapterHolder[0] = new GenericAdapter<>(
                R.layout.item_produto_mesa, (holder, item, pos) -> {
            holder.setText(R.id.tvProdutoNome, item.get("descricao_produto") != null ? item.get("descricao_produto").toString() : "");
            double qtd = item.get("quantidade") != null ? ((Number) item.get("quantidade")).doubleValue() : 0;
            double preco = item.get("preco_unitario") != null ? ((Number) item.get("preco_unitario")).doubleValue() : 0;
            double totalItem = item.get("total") != null ? ((Number) item.get("total")).doubleValue() : 0;
            double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0;
            double totalComAd = totalItem + adTotal;
            holder.setText(R.id.tvProdutoDetalhe, String.format("%.0f x R$ %.2f = R$ %.2f", qtd, preco, totalComAd));

            // v6.7.6 - Exibir adicionais se houver
            TextView tvAdicionais = holder.find(R.id.tvAdicionaisInfo);
            String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
            String obsCozinha = item.get("observacao_cozinha") != null ? item.get("observacao_cozinha").toString() : "";
            if (tvAdicionais != null) {
                StringBuilder detalhesExtras = new StringBuilder();
                if (!adDesc.isEmpty()) {
                    detalhesExtras.append("  + ").append(adDesc);
                }
                if (!obsCozinha.isEmpty()) {
                    if (detalhesExtras.length() > 0) detalhesExtras.append("\n");
                    detalhesExtras.append("  Obs cozinha: ").append(obsCozinha);
                }
                if (detalhesExtras.length() > 0) {
                    tvAdicionais.setVisibility(View.VISIBLE);
                    tvAdicionais.setText(detalhesExtras.toString());
                } else {
                    tvAdicionais.setVisibility(View.GONE);
                }
            }

            Button btnRemover = holder.find(R.id.btnRemoverProduto);
            if (btnRemover != null) {
                // v6.7.8 - Desabilitar remocao se mesa reservada por outro usuario
                if (!podeEditar) {
                    btnRemover.setVisibility(View.GONE);
                } else {
                    btnRemover.setVisibility(View.VISIBLE);
                    btnRemover.setOnClickListener(v -> {
                        int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (itemId > 0) {
                            removerItemMesa(itemId, itensMesa, adapterHolder[0], dialogView);
                        } else {
                            itensMesa.remove(pos);
                            adapterHolder[0].setItems(new ArrayList<>(itensMesa));
                            atualizarTotalDialog(dialogView, itensMesa);
                        }
                    });
                }
            }
        });
        final GenericAdapter<Map<String, Object>> itensAdapter = adapterHolder[0];
        recyclerProdutos.setAdapter(itensAdapter);

        ImageButton btnScanProdutoMesa = dialogView.findViewById(R.id.btnScanProdutoMesa);
        if (btnScanProdutoMesa != null) {
            btnScanProdutoMesa.setOnClickListener(v -> {
                if (!podeEditar) {
                    showError("Esta mesa esta reservada por " + reservadoPorNome + ".\n\n"
                            + "Apenas o usuario que reservou pode adicionar itens.");
                    return;
                }
                scannerMesaItens = itensMesa;
                scannerMesaAdapter = itensAdapter;
                scannerMesaDialogView = dialogView;
                scannerMesaSpProduto = spProduto;
                scannerMesaEtQtdProduto = etQtdProduto;
                scannerMesaEtObservacaoCozinha = etObservacaoCozinhaItem;
                scannerMesaId = mesaId;
                scannerMesaOcupacaoIdHolder = ocupacaoIdHolder;
                scannerMesaPodeEditar = podeEditar;
                scannerMesaReservadoPorNome = reservadoPorNome;
                startActivityForResult(new Intent(this, BarcodeScannerActivity.class), REQUEST_SCAN_MESA_PRODUTO);
            });
        }

        // Carregar itens existentes
        if (ocupacaoId > 0) {
            carregarItensMesa(ocupacaoId, itensMesa, itensAdapter, dialogView);
        }

        // v6.7.8 - Bloquear controles de edicao se mesa reservada por outro usuario
        if (!podeEditar) {
            spGarcom.setEnabled(false);
            etPessoas.setEnabled(false);
            spProduto.setEnabled(false);
            etQtdProduto.setEnabled(false);
            if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setEnabled(false);
            if (btnScanProdutoMesa != null) { btnScanProdutoMesa.setEnabled(false); btnScanProdutoMesa.setAlpha(0.4f); }
        }

        // Botao adicionar produto - v6.7.6: agora verifica adicionais antes de adicionar
        Button btnAddProduto = dialogView.findViewById(R.id.btnAddProduto);
        if (!podeEditar) {
            btnAddProduto.setEnabled(false);
            btnAddProduto.setAlpha(0.4f);
        }
        btnAddProduto.setOnClickListener(v -> {
            // v6.7.8 - Verificar permissao de edicao
            if (!podeEditar) {
                showError("Esta mesa esta reservada por " + reservadoPorNome + ".\n\n"
                        + "Apenas o usuario que reservou pode adicionar itens.");
                return;
            }

            int selectedPos = spProduto.getSelectedItemPosition();
            if (selectedPos <= 0) {
                showToast("Selecione um produto");
                return;
            }
            Map<String, Object> produtoSelecionado = produtosList.get(selectedPos - 1);
            String qtdStr = etQtdProduto.getText().toString().trim();
            double qtd = 1;
            try {
                if (!qtdStr.isEmpty()) qtd = Double.parseDouble(qtdStr);
            } catch (NumberFormatException e) {
                qtd = 1;
            }
            if (qtd <= 0) qtd = 1;

            final double finalQtd = qtd;
            final String observacaoCozinha = normalizarObservacaoCozinha(etObservacaoCozinhaItem != null ? etObservacaoCozinhaItem.getText().toString() : "");
            // v6.7.6 - Verificar se o produto tem adicionais vinculados ao tipo
            int tipoProdutoId = 0;
            try {
                tipoProdutoId = ((Number) produtoSelecionado.get("tipo_produto_id")).intValue();
            } catch (Exception ignored) {}

            if (tipoProdutoId > 0) {
                // v7.0.3 - AUTOSAVE: passa mesaId e ocupacaoIdHolder para salvar automaticamente
                verificarAdicionaisEAdicionar(produtoSelecionado, finalQtd, observacaoCozinha, itensMesa, itensAdapter, dialogView, spProduto, etQtdProduto, etObservacaoCozinhaItem, mesaId, ocupacaoIdHolder);
            } else {
                // Sem tipo - adicionar direto sem adicionais
                // v7.0.3 - AUTOSAVE: passa mesaId e ocupacaoIdHolder
                adicionarProdutoNaMesa(produtoSelecionado, finalQtd, observacaoCozinha, new ArrayList<>(), itensMesa, itensAdapter, dialogView, mesaId, ocupacaoIdHolder);
                spProduto.setSelection(0);
                etQtdProduto.setText("1");
                if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
            }
        });

        // Total
        TextView tvTotalMesa = dialogView.findViewById(R.id.tvTotalMesa);
        atualizarTotalDialog(dialogView, itensMesa);

        // v6.7.9 - Botoes de Impressao
        LinearLayout llBotoesImpressao = dialogView.findViewById(R.id.llBotoesImpressao);
        Button btnImprimirPedidoCompleto = dialogView.findViewById(R.id.btnImprimirPedidoCompleto);
        Button btnImprimirUltimoItem = dialogView.findViewById(R.id.btnImprimirUltimoItem);

        // v6.9.9 - Mostrar botoes de impressao sempre (serao habilitados/desabilitados conforme necessidade)
        // Antes so mostrava para mesas ocupadas/reservadas/prontas com ocupacaoId > 0
        // Agora mostra sempre para permitir imprimir ultimo item antes de salvar
        llBotoesImpressao.setVisibility(View.VISIBLE);

        // v6.7.8 - Botao Reservar Mesa
        Button btnReservarMesa = dialogView.findViewById(R.id.btnReservarMesa);
        // Mostrar botao de reservar quando mesa esta livre ou quando o dono quer cancelar reserva
        if ("livre".equals(statusAtual)) {
            btnReservarMesa.setVisibility(View.VISIBLE);
            btnReservarMesa.setText("RESERVAR MESA");
        } else if ("reservada".equals(statusAtual) && usuarioEhDono) {
            btnReservarMesa.setVisibility(View.VISIBLE);
            btnReservarMesa.setText("CANCELAR RESERVA");
        } else {
            btnReservarMesa.setVisibility(View.GONE);
        }

        // v6.8.0 - Botao Pronta para Cobranca
        Button btnProntaCobranca = dialogView.findViewById(R.id.btnProntaCobranca);
        // Mostrar botao quando a mesa esta ocupada ou reservada (com itens salvos)
        // Quando ja esta pronta, mostrar botao para cancelar (voltar para ocupada)
        if ("pronta".equals(statusAtual) && ocupacaoId > 0 && podeEditar) {
            btnProntaCobranca.setVisibility(View.VISIBLE);
            btnProntaCobranca.setText("CANCELAR PRONTA P/ COBRAN\u00c7A");
        } else if (("ocupada".equals(statusAtual) || "reservada".equals(statusAtual)) && ocupacaoId > 0 && podeEditar) {
            btnProntaCobranca.setVisibility(View.VISIBLE);
            btnProntaCobranca.setText("PRONTA P/ COBRAN\u00c7A");
        } else {
            btnProntaCobranca.setVisibility(View.GONE);
        }

        // v6.7.7 - Botao Encaminhar para Pagamento
        Button btnEncaminharPagamento = dialogView.findViewById(R.id.btnEncaminharPagamento);
        // Mostrar botao somente se a mesa esta ocupada ou pronta (tem itens salvos no banco)
        if (("ocupada".equals(statusAtual) || "pronta".equals(statusAtual)) && ocupacaoId > 0 && podeEditar) {
            btnEncaminharPagamento.setVisibility(View.VISIBLE);
        } else {
            btnEncaminharPagamento.setVisibility(View.GONE);
        }

        // Botao Limpar Mesa
        Button btnLimpar = dialogView.findViewById(R.id.btnLimparMesa);
        if (!podeEditar) {
            btnLimpar.setEnabled(false);
            btnLimpar.setAlpha(0.4f);
        }
        btnLimpar.setOnClickListener(v -> {
            if (!podeEditar) {
                showError("Esta mesa esta reservada por " + reservadoPorNome + ".\n\n"
                        + "Apenas o usuario que reservou pode limpar a mesa.");
                return;
            }
            showConfirm("Limpar Mesa", "Deseja remover todos os produtos e liberar a mesa " + mesaNumero + "?", () -> {
                limparMesa(mesaId, ocupacaoId);
            });
        });

        // v7.0.3 - AUTOSAVE: Botao Salvar oculto pois itens sao salvos automaticamente
        // Mantido apenas para salvar garcom/pessoas se necessario
        Button btnSalvar = dialogView.findViewById(R.id.btnSalvarMesa);
        btnSalvar.setVisibility(View.GONE);
        if (!podeEditar) {
            btnSalvar.setEnabled(false);
            btnSalvar.setAlpha(0.4f);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Mesa " + mesaNumero)
                .setView(dialogView)
                .setNegativeButton("Fechar", null)
                .create();

        // v6.7.8 - Configurar botao de reservar mesa
        btnReservarMesa.setOnClickListener(v -> {
            if ("livre".equals(statusAtual)) {
                // Reservar a mesa para o usuario logado
                showConfirm("Reservar Mesa",
                        "Deseja reservar a Mesa " + mesaNumero + " para voce (" + usuarioLogadoNome + ")?\n\n"
                                + "Apenas voce podera adicionar itens nesta mesa.",
                        () -> {
                            reservarMesa(mesaId, ocupacaoId, dialog);
                        });
            } else if ("reservada".equals(statusAtual) && usuarioEhDono) {
                // Cancelar reserva
                showConfirm("Cancelar Reserva",
                        "Deseja cancelar a reserva da Mesa " + mesaNumero + "?\n\n"
                                + "A mesa ficara livre para outros usuarios.",
                        () -> {
                            cancelarReservaMesa(mesaId, ocupacaoId, dialog);
                        });
            }
        });

        // v6.8.0 - Configurar botao de pronta para cobranca
        btnProntaCobranca.setOnClickListener(v -> {
            if (itensMesa.isEmpty()) {
                showToast("A mesa nao possui itens");
                return;
            }

            // Verificar se existem itens nao salvos (id == 0)
            boolean temItensNaoSalvosPronta = false;
            for (Map<String, Object> item : itensMesa) {
                int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                if (itemId == 0) {
                    temItensNaoSalvosPronta = true;
                    break;
                }
            }

            if (temItensNaoSalvosPronta) {
                showError("Existem itens nao salvos na mesa.\n\nSalve a mesa antes de marcar como pronta.");
                return;
            }

            if ("pronta".equals(statusAtual)) {
                // Cancelar pronta - voltar para ocupada
                showConfirm("Cancelar Pronta p/ Cobran\u00e7a",
                        "Deseja cancelar o status de pronta da Mesa " + mesaNumero + "?\n\n"
                                + "A mesa voltara para o status ocupada.",
                        () -> {
                            marcarMesaPronta(mesaId, ocupacaoId, false, dialog);
                        });
            } else {
                // Marcar como pronta para cobranca
                showConfirm("Pronta p/ Cobran\u00e7a",
                        "Deseja marcar a Mesa " + mesaNumero + " como pronta para cobran\u00e7a?\n\n"
                                + "Total: R$ " + String.format("%.2f", calcularTotalItens(itensMesa)) + "\n\n"
                                + "A mesa ficara azul indicando que esta pronta para ser cobrada e fechada.",
                        () -> {
                            marcarMesaPronta(mesaId, ocupacaoId, true, dialog);
                        });
            }
        });

        // v6.7.7 - Configurar botao de encaminhar para pagamento
        btnEncaminharPagamento.setOnClickListener(v -> {
            if (itensMesa.isEmpty()) {
                showToast("A mesa nao possui itens para pagamento");
                return;
            }

            // Verificar se existem itens nao salvos (id == 0)
            boolean temItensNaoSalvos = false;
            for (Map<String, Object> item : itensMesa) {
                int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                if (itemId == 0) {
                    temItensNaoSalvos = true;
                    break;
                }
            }

            if (temItensNaoSalvos) {
                showError("Existem itens nao salvos na mesa.\n\nSalve a mesa antes de encaminhar para pagamento.");
                return;
            }

            showConfirm("Encaminhar para Pagamento",
                    "Deseja encaminhar a Mesa " + mesaNumero + " para pagamento?\n\n"
                            + "Total: R$ " + String.format("%.2f", calcularTotalItens(itensMesa)),
                    () -> {
                        // Verificar caixa aberto antes de encaminhar
                        showLoading("Verificando caixa...");
                        new Thread(() -> {
                            boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
                            hideLoading();
                            runOnUiThread(() -> {
                                if (!caixaAberto) {
                                    PermissionHelper.mostrarCaixaFechado(this);
                                    return;
                                }
                                // Caixa aberto - encaminhar para pagamento
                                dialogMesaAtual = dialog;
                                mesaIdPagamento = mesaId;
                                ocupacaoIdPagamento = ocupacaoId;
                                enviarMesaParaPagamento(mesaNumero, itensMesa);
                                dialog.dismiss();
                            });
                        }).start();
                    });
        });

        // v6.7.9 - Configurar botao Imprimir Pedido Completo
        // v6.9.9 - Removida restricao de itens nao salvos. Agora imprime usando dados em memoria.
        btnImprimirPedidoCompleto.setOnClickListener(v -> {
            if (itensMesa.isEmpty()) {
                showToast("A mesa nao possui itens para imprimir");
                return;
            }

            imprimirPedidoCompleto(mesaNumero, itensMesa);
        });

        // v6.7.9 - Configurar botao Imprimir Ultimo Item Pedido
        // v6.9.9 - Agora permite imprimir o ultimo item mesmo antes de salvar a mesa.
        //          Se o ultimo item ainda nao foi salvo (id == 0), imprime usando os dados em memoria.
        btnImprimirUltimoItem.setOnClickListener(v -> {
            if (itensMesa.isEmpty()) {
                showToast("A mesa nao possui itens para imprimir");
                return;
            }

            imprimirUltimoItemPedido(mesaNumero, ocupacaoId, itensMesa, btnImprimirUltimoItem);
        });

        btnSalvar.setOnClickListener(v -> {
            // v6.7.8 - Verificar permissao de edicao
            if (!podeEditar) {
                showError("Esta mesa esta reservada por " + reservadoPorNome + ".\n\n"
                        + "Apenas o usuario que reservou pode salvar alteracoes.");
                return;
            }

            int garcomPos = spGarcom.getSelectedItemPosition();
            int garcomId = garcomPos > 0 ? ((Number) garconsList.get(garcomPos - 1).get("id")).intValue() : 0;
            String pessoasStr = etPessoas.getText().toString().trim();
            int qtdPessoas = 0;
            try {
                if (!pessoasStr.isEmpty()) qtdPessoas = Integer.parseInt(pessoasStr);
            } catch (NumberFormatException e) {
                // ignora
            }
            // v7.0.3 - Usa ocupacaoIdHolder que pode ter sido atualizado pelo autosave
            salvarMesa(mesaId, ocupacaoIdHolder[0], garcomId, qtdPessoas, itensMesa, dialog);
        });

        dialog.show();
    }

    /**
     * v6.7.9 - Gera o cupom de pedido completo da mesa para impressao.
     * Inclui todos os itens da mesa com adicionais.
     */
    private String gerarCupomPedidoMesa(int mesaNumero, List<Map<String, Object>> itensMesa) {
        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        StringBuilder sb = new StringBuilder();
        String line = repeat("-", colunas);

        sb.append(center("*** PEDIDO MESA " + mesaNumero + " ***", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append("Data: " + FormatUtils.getCurrentDateTime()).append("\n");
        sb.append(line).append("\n");

        // Header itens
        sb.append("ITEM  DESCRICAO          QTD    TOTAL\n");
        sb.append(line).append("\n");

        int itemNum = 1;
        double totalGeral = 0;
        for (Map<String, Object> item : itensMesa) {
            String desc = item.get("descricao_produto") != null ? item.get("descricao_produto").toString() : "";
            if (desc.length() > 18) desc = desc.substring(0, 18);
            double qtd = item.get("quantidade") != null ? ((Number) item.get("quantidade")).doubleValue() : 0;
            double totalItem = item.get("total") != null ? ((Number) item.get("total")).doubleValue() : 0;
            double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0;
            double totalComAd = totalItem + adTotal;
            totalGeral += totalComAd;

            sb.append(String.format("%-5d %-18s %5s %8s\n",
                    itemNum++,
                    desc,
                    FormatUtils.formatQuantidade(qtd),
                    FormatUtils.formatMoney(totalComAd)));

            // Exibir adicionais
            String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
            if (!adDesc.isEmpty()) {
                sb.append("      + ").append(adDesc).append("\n");
            }
            String obsCozinha = item.get("observacao_cozinha") != null ? item.get("observacao_cozinha").toString() : "";
            if (!obsCozinha.isEmpty()) {
                sb.append("      OBS COZINHA: ").append(obsCozinha).append("\n");
            }
        }

        sb.append(line).append("\n");
        sb.append(rightAlign("TOTAL: R$ " + FormatUtils.formatMoney(totalGeral), colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append(center("PDV Pro v8.0.0", colunas)).append("\n");
        sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

        return sb.toString();
    }

    /**
     * v6.7.9 - Gera o cupom de um unico item da mesa para impressao.
     */
    private String gerarCupomItemMesa(int mesaNumero, Map<String, Object> item) {
        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        StringBuilder sb = new StringBuilder();
        String line = repeat("-", colunas);

        sb.append(center("*** ULTIMO ITEM - MESA " + mesaNumero + " ***", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append("Data: " + FormatUtils.getCurrentDateTime()).append("\n");
        sb.append(line).append("\n");

        String desc = item.get("descricao_produto") != null ? item.get("descricao_produto").toString() : "";
        double qtd = item.get("quantidade") != null ? ((Number) item.get("quantidade")).doubleValue() : 0;
        double preco = item.get("preco_unitario") != null ? ((Number) item.get("preco_unitario")).doubleValue() : 0;
        double totalItem = item.get("total") != null ? ((Number) item.get("total")).doubleValue() : 0;
        double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0;
        double totalComAd = totalItem + adTotal;

        sb.append("Produto: " + desc).append("\n");
        sb.append("Qtd: " + FormatUtils.formatQuantidade(qtd)).append("\n");
        sb.append("Preco Unit.: R$ " + FormatUtils.formatMoney(preco)).append("\n");

        // Exibir adicionais
        String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
        if (!adDesc.isEmpty()) {
            sb.append("Adicionais: " + adDesc).append("\n");
            if (adTotal > 0) {
                sb.append("Valor Adicionais: R$ " + FormatUtils.formatMoney(adTotal)).append("\n");
            }
        }
        String obsCozinha = item.get("observacao_cozinha") != null ? item.get("observacao_cozinha").toString() : "";
        if (!obsCozinha.isEmpty()) {
            sb.append("OBS COZINHA: ").append(obsCozinha).append("\n");
        }

        sb.append(line).append("\n");
        sb.append(rightAlign("TOTAL ITEM: R$ " + FormatUtils.formatMoney(totalComAd), colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append(center("PDV Pro v8.0.0", colunas)).append("\n");
        sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

        return sb.toString();
    }

    /**
     * v6.7.9 - Imprime o pedido completo da mesa.
     * Gera um cupom com todos os itens e envia para a impressora configurada.
     */
    private void imprimirPedidoCompleto(int mesaNumero, List<Map<String, Object>> itensMesa) {
        showLoading("Imprimindo pedido completo...");
        new Thread(() -> {
            try {
                MultiPrinterManager multi = new MultiPrinterManager(this);
                int enviadosMulti = multi.imprimirMesaPorCategorias(mesaNumero, itensMesa, false);

                boolean sucesso;
                if (enviadosMulti > 0) {
                    sucesso = true;
                } else {
                    String cupom = gerarCupomPedidoMesa(mesaNumero, itensMesa);
                    PrinterManager pm = new PrinterManager(this);
                    String tipoImpressora = pm.getTipoImpressora();

                    if (PrinterManager.TIPO_NENHUMA.equals(tipoImpressora)) {
                        hideLoading();
                        runOnUiThread(() -> showError("Nenhuma impressora configurada.\n\nConfigure uma impressora em Configuracoes > Impressora."));
                        return;
                    }

                    sucesso = pm.imprimirTexto(cupom);
                }
                hideLoading();

                final int finalEnviadosMulti = enviadosMulti;
                runOnUiThread(() -> {
                    if (sucesso) {
                        showToast(finalEnviadosMulti > 0
                                ? "Pedido completo da Mesa " + mesaNumero + " enviado para multiimpressoras por categoria!"
                                : "Pedido completo da Mesa " + mesaNumero + " impresso com sucesso!");
                    } else {
                        showError("Erro ao imprimir o pedido.\n\nVerifique se a impressora esta ligada e conectada.");
                    }
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao imprimir pedido completo: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao imprimir: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * v6.7.9 - Imprime o ultimo item adicionado ao pedido da mesa.
     * Cada item so pode ser impresso UMA UNICA VEZ.
     * Controle feito pela coluna 'impresso' na tabela itens_mesa.
     *
     * v6.9.9 - Agora permite imprimir o ultimo item mesmo antes de salvar a mesa.
     *          Se o ultimo item ainda nao foi salvo (id == 0), imprime usando os dados em memoria
     *          e marca o item como impresso na lista local. Quando a mesa for salva posteriormente,
     *          o item sera salvo no banco ja com o flag impresso = 1.
     */
    private void imprimirUltimoItemPedido(int mesaNumero, int ocupacaoId,
                                           List<Map<String, Object>> itensMesa,
                                           Button btnImprimirUltimoItem) {
        if (itensMesa.isEmpty()) {
            showToast("Nenhum item na mesa para imprimir");
            return;
        }

        // Pegar o ultimo item da lista (ultimo adicionado)
        Map<String, Object> ultimoItem = itensMesa.get(itensMesa.size() - 1);
        int ultimoItemId = ultimoItem.get("id") != null ? ((Number) ultimoItem.get("id")).intValue() : 0;

        // Verificar se o item ja foi impresso (flag local)
        int impressoFlag = ultimoItem.get("impresso") != null ? ((Number) ultimoItem.get("impresso")).intValue() : 0;
        if (impressoFlag == 1) {
            showError("O ultimo item ja foi impresso anteriormente.\n\n"
                    + "Cada item so pode ser impresso uma unica vez.\n\n"
                    + "Adicione um novo item para poder imprimir novamente.");
            return;
        }

        // v6.9.9 - Se o item ainda nao foi salvo (id == 0), imprimir usando dados em memoria
        if (ultimoItemId <= 0) {
            // Item nao salvo no banco - imprimir direto da memoria
            showLoading("Imprimindo ultimo item...");
            new Thread(() -> {
                try {
                    MultiPrinterManager multi = new MultiPrinterManager(this);
                    int enviadosMulti = multi.imprimirMesaPorCategorias(mesaNumero, itensMesa, true);

                    boolean sucesso;
                    if (enviadosMulti > 0) {
                        sucesso = true;
                    } else {
                        String cupom = gerarCupomItemMesa(mesaNumero, ultimoItem);
                        PrinterManager pm = new PrinterManager(this);
                        String tipoImpressora = pm.getTipoImpressora();

                        if (PrinterManager.TIPO_NENHUMA.equals(tipoImpressora)) {
                            hideLoading();
                            runOnUiThread(() -> showError("Nenhuma impressora configurada.\n\nConfigure uma impressora em Configuracoes > Impressora."));
                            return;
                        }

                        sucesso = pm.imprimirTexto(cupom);
                    }

                    if (sucesso) {
                        // Marcar o item como impresso na lista local
                        // Quando a mesa for salva, o item sera salvo com impresso = 1
                        ultimoItem.put("impresso", 1);
                    }

                    hideLoading();
                    runOnUiThread(() -> {
                        if (sucesso) {
                            showToast("Ultimo item da Mesa " + mesaNumero + " impresso com sucesso!");
                            btnImprimirUltimoItem.setAlpha(0.5f);
                        } else {
                            showError("Erro ao imprimir o item.\n\nVerifique se a impressora esta ligada e conectada.");
                        }
                    });
                } catch (Exception e) {
                    hideLoading();
                    Log.e(TAG, "Erro ao imprimir ultimo item (nao salvo): " + e.getMessage());
                    runOnUiThread(() -> showError("Erro ao imprimir: " + e.getMessage()));
                }
            }).start();
            return;
        }

        // Item ja salvo no banco - fluxo original com verificacao no banco
        showLoading("Verificando impressao...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Buscar o ultimo item nao impresso da mesa
                PreparedStatement psCheck = conn.prepareStatement(
                        "SELECT id, impresso FROM itens_mesa WHERE ocupacao_id = ? ORDER BY id DESC LIMIT 1");
                psCheck.setInt(1, ocupacaoId);
                ResultSet rsCheck = psCheck.executeQuery();

                int itemIdParaImprimir = 0;
                boolean jaImpresso = false;

                if (rsCheck.next()) {
                    itemIdParaImprimir = rsCheck.getInt("id");
                    try {
                        jaImpresso = rsCheck.getInt("impresso") == 1;
                    } catch (Exception ignored) {
                        jaImpresso = false;
                    }
                }
                rsCheck.close();
                psCheck.close();

                if (itemIdParaImprimir <= 0) {
                    hideLoading();
                    runOnUiThread(() -> showError("Nenhum item encontrado na mesa."));
                    return;
                }

                if (jaImpresso) {
                    hideLoading();
                    runOnUiThread(() -> showError("O ultimo item ja foi impresso anteriormente.\n\n"
                            + "Cada item so pode ser impresso uma unica vez.\n\n"
                            + "Adicione um novo item para poder imprimir novamente."));
                    return;
                }

                // Gerar cupom do ultimo item
                MultiPrinterManager multi = new MultiPrinterManager(this);
                    int enviadosMulti = multi.imprimirMesaPorCategorias(mesaNumero, itensMesa, true);

                    boolean sucesso;
                    if (enviadosMulti > 0) {
                        sucesso = true;
                    } else {
                        String cupom = gerarCupomItemMesa(mesaNumero, ultimoItem);
                        PrinterManager pm = new PrinterManager(this);
                        String tipoImpressora = pm.getTipoImpressora();

                        if (PrinterManager.TIPO_NENHUMA.equals(tipoImpressora)) {
                            hideLoading();
                            runOnUiThread(() -> showError("Nenhuma impressora configurada.\n\nConfigure uma impressora em Configuracoes > Impressora."));
                            return;
                        }

                        sucesso = pm.imprimirTexto(cupom);
                    }

                if (sucesso) {
                    // Marcar o item como impresso no banco de dados
                    PreparedStatement psUpdate = conn.prepareStatement(
                            "UPDATE itens_mesa SET impresso = 1 WHERE id = ?");
                    psUpdate.setInt(1, itemIdParaImprimir);
                    psUpdate.executeUpdate();
                    psUpdate.close();

                    // Atualizar o flag na lista local tambem
                    for (Map<String, Object> item : itensMesa) {
                        int id = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (id == itemIdParaImprimir) {
                            item.put("impresso", 1);
                            break;
                        }
                    }
                }

                hideLoading();
                runOnUiThread(() -> {
                    if (sucesso) {
                        showToast("Ultimo item da Mesa " + mesaNumero + " impresso com sucesso!");
                        // Desabilitar visualmente o botao para indicar que ja foi impresso
                        btnImprimirUltimoItem.setAlpha(0.5f);
                    } else {
                        showError("Erro ao imprimir o item.\n\nVerifique se a impressora esta ligada e conectada.");
                    }
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao imprimir ultimo item: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao imprimir: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * v6.7.8 - Reserva a mesa para o usuario logado.
     * Cria uma ocupacao com status 'reservada' e registra o usuario.
     */
    private void reservarMesa(int mesaId, int ocupacaoIdAtual, AlertDialog dialog) {
        showLoading("Reservando mesa...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (ocupacaoIdAtual == 0) {
                    // Criar nova ocupacao com status reservada
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ocupacao_mesa (mesa_id, garcom_id, qtd_pessoas, status, data_abertura, reservado_por_usuario_id, reservado_por_usuario_nome) "
                                    + "VALUES (?, 0, 0, 'reservada', NOW(), ?, ?)");
                    ps.setInt(1, mesaId);
                    ps.setInt(2, usuarioLogadoId);
                    ps.setString(3, usuarioLogadoNome);
                    ps.executeUpdate();
                    ps.close();
                } else {
                    // Atualizar ocupacao existente para reservada
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ocupacao_mesa SET status = 'reservada', reservado_por_usuario_id = ?, reservado_por_usuario_nome = ? WHERE id = ?");
                    ps.setInt(1, usuarioLogadoId);
                    ps.setString(2, usuarioLogadoNome);
                    ps.setInt(3, ocupacaoIdAtual);
                    ps.executeUpdate();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Mesa reservada por " + usuarioLogadoNome + "!");
                    dialog.dismiss();
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * v6.7.8 - Cancela a reserva da mesa, voltando para status 'livre'.
     * Apenas o usuario que reservou pode cancelar.
     */
    private void cancelarReservaMesa(int mesaId, int ocupacaoId, AlertDialog dialog) {
        showLoading("Cancelando reserva...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (ocupacaoId > 0) {
                    // Verificar se tem itens na mesa
                    PreparedStatement psCount = conn.prepareStatement(
                            "SELECT COUNT(*) as qtd FROM itens_mesa WHERE ocupacao_id = ?");
                    psCount.setInt(1, ocupacaoId);
                    ResultSet rsCount = psCount.executeQuery();
                    int qtdItens = 0;
                    if (rsCount.next()) {
                        qtdItens = rsCount.getInt("qtd");
                    }
                    rsCount.close();
                    psCount.close();

                    if (qtdItens > 0) {
                        // Tem itens - manter como ocupada mas remover reserva
                        PreparedStatement ps = conn.prepareStatement(
                                "UPDATE ocupacao_mesa SET status = 'ocupada', reservado_por_usuario_id = 0, reservado_por_usuario_nome = NULL WHERE id = ?");
                        ps.setInt(1, ocupacaoId);
                        ps.executeUpdate();
                        ps.close();
                    } else {
                        // Sem itens - encerrar ocupacao (mesa fica livre)
                        PreparedStatement ps = conn.prepareStatement(
                                "UPDATE ocupacao_mesa SET status = 'encerrada', data_fechamento = NOW(), reservado_por_usuario_id = 0, reservado_por_usuario_nome = NULL WHERE id = ?");
                        ps.setInt(1, ocupacaoId);
                        ps.executeUpdate();
                        ps.close();
                    }
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Reserva cancelada com sucesso!");
                    dialog.dismiss();
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    /**
     * v6.8.0 - Marca ou desmarca a mesa como pronta para cobranca.
     * Quando marcada como pronta, o status muda para 'pronta' (cor azul no grid).
     * Quando desmarcada, o status volta para 'ocupada'.
     *
     * @param mesaId ID da mesa
     * @param ocupacaoId ID da ocupacao atual
     * @param marcarPronta true para marcar como pronta, false para cancelar
     * @param dialog dialog atual para fechar apos a operacao
     */
    private void marcarMesaPronta(int mesaId, int ocupacaoId, boolean marcarPronta, AlertDialog dialog) {
        showLoading(marcarPronta ? "Marcando mesa como pronta..." : "Cancelando status pronta...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (ocupacaoId > 0) {
                    String novoStatus = marcarPronta ? "pronta" : "ocupada";
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ocupacao_mesa SET status = ? WHERE id = ?");
                    ps.setString(1, novoStatus);
                    ps.setInt(2, ocupacaoId);
                    ps.executeUpdate();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> {
                    if (marcarPronta) {
                        showToast("Mesa marcada como pronta para cobran\u00e7a!");
                    } else {
                        showToast("Status pronta cancelado. Mesa voltou para ocupada.");
                    }
                    dialog.dismiss();
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * v6.7.7 - Calcula o total dos itens da mesa incluindo adicionais.
     */
    private double calcularTotalItens(List<Map<String, Object>> itensMesa) {
        double total = 0;
        for (Map<String, Object> item : itensMesa) {
            if (item.get("total") != null) {
                total += ((Number) item.get("total")).doubleValue();
            }
            if (item.get("adicionais_total") != null) {
                total += ((Number) item.get("adicionais_total")).doubleValue();
            }
        }
        return total;
    }

    /**
     * v6.7.7 - Envia os itens da mesa para a tela de pagamento.
     * Similar ao fluxo de fechar comanda, mas para mesas.
     */
    private void enviarMesaParaPagamento(int mesaNumero, List<Map<String, Object>> itensMesa) {
        double totalMesa = calcularTotalItens(itensMesa);
        double taxaServico = TaxaServicoPreferences.cobrarMesas(this)
                ? TaxaServicoPreferences.calcularTaxa(totalMesa) : 0.0;

        Intent intent = new Intent(this, PagamentoActivity.class);
        intent.putExtra("total_bruto", totalMesa);
        intent.putExtra("desconto", 0.0);
        intent.putExtra("desconto_tipo", "valor");
        intent.putExtra("desconto_input", 0.0);
        intent.putExtra("acrescimo", taxaServico);
        intent.putExtra("acrescimo_tipo", taxaServico > 0 ? "porcentagem" : "valor");
        intent.putExtra("acrescimo_input", taxaServico > 0 ? TaxaServicoPreferences.PERCENTUAL : 0.0);
        intent.putExtra("total_liquido", totalMesa + taxaServico);
        intent.putExtra("cliente_id", 0);
        intent.putExtra("cliente_nome", "Cliente nao informado");
        intent.putExtra("vendedor_id", 0);
        intent.putExtra("entregador_id", 0);
        intent.putExtra("observacao", "Mesa " + mesaNumero
                + (taxaServico > 0 ? " - Taxa de servico 10%" : ""));
        intent.putExtra("num_itens", itensMesa.size());

        // Dados da mesa para encerrar apos pagamento
        intent.putExtra("mesa_id", mesaIdPagamento);
        intent.putExtra("ocupacao_id", ocupacaoIdPagamento);
        intent.putExtra("mesa_numero", mesaNumero);
        intent.putExtra("is_mesa", true);

        for (int i = 0; i < itensMesa.size(); i++) {
            Map<String, Object> item = itensMesa.get(i);
            intent.putExtra("item_produto_id_" + i,
                    item.get("produto_id") != null ? ((Number) item.get("produto_id")).intValue() : 0);
            intent.putExtra("item_descricao_" + i,
                    item.get("descricao_produto") != null ? item.get("descricao_produto").toString() : "");
            intent.putExtra("item_qtd_" + i,
                    item.get("quantidade") != null ? ((Number) item.get("quantidade")).doubleValue() : 0.0);
            intent.putExtra("item_preco_" + i,
                    item.get("preco_unitario") != null ? ((Number) item.get("preco_unitario")).doubleValue() : 0.0);

            // Total do item inclui adicionais
            double totalItem = item.get("total") != null ? ((Number) item.get("total")).doubleValue() : 0.0;
            double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0.0;
            intent.putExtra("item_total_" + i, totalItem + adTotal);

            // Passar dados dos adicionais
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> adicionaisLista = (List<Map<String, Object>>) item.get("adicionais_lista");
            int numAdicionais = (adicionaisLista != null) ? adicionaisLista.size() : 0;
            intent.putExtra("item_num_adicionais_" + i, numAdicionais);
            if (adicionaisLista != null) {
                for (int j = 0; j < adicionaisLista.size(); j++) {
                    Map<String, Object> ad = adicionaisLista.get(j);
                    intent.putExtra("item_" + i + "_ad_id_" + j,
                            ad.get("id") != null ? ((Number) ad.get("id")).intValue() : 0);
                    intent.putExtra("item_" + i + "_ad_desc_" + j,
                            ad.get("descricao") != null ? ad.get("descricao").toString() : "");
                    intent.putExtra("item_" + i + "_ad_preco_" + j,
                            ad.get("preco") != null ? ((Number) ad.get("preco")).doubleValue() : 0.0);
                }
            }
        }

        startActivityForResult(intent, REQUEST_PAGAMENTO_MESA);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /**
     * v6.7.7 - Trata o resultado do pagamento da mesa.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PAGAMENTO_MESA && resultCode == RESULT_OK) {
            // Pagamento concluido - encerrar a mesa
            encerrarMesaAposPagamento();
        } else if (requestCode == REQUEST_SCAN_MESA_PRODUTO && resultCode == RESULT_OK && data != null) {
            String barcode = data.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT);
            if (barcode != null && !barcode.trim().isEmpty()) {
                adicionarProdutoMesaPorCodigo(barcode.trim());
            }
        }
    }

    /**
     * Adiciona produto na mesa aberta usando codigo de barras ou codigo interno.
     */
    private void adicionarProdutoMesaPorCodigo(String codigo) {
        if (!scannerMesaPodeEditar) {
            showError("Esta mesa esta reservada por " + scannerMesaReservadoPorNome + ".");
            return;
        }
        if (scannerMesaDialogView == null || scannerMesaItens == null || scannerMesaAdapter == null) {
            showError("Abra uma mesa antes de usar o leitor de codigo de barras.");
            return;
        }

        Map<String, Object> produto = localizarProdutoPorCodigo(codigo);
        if (produto == null) {
            showError("Produto nao encontrado para o codigo: " + codigo);
            return;
        }

        double qtd = 1;
        try {
            if (scannerMesaEtQtdProduto != null) {
                String qtdStr = scannerMesaEtQtdProduto.getText().toString().trim();
                if (!qtdStr.isEmpty()) qtd = Double.parseDouble(qtdStr.replace(",", "."));
            }
        } catch (Exception ignored) { qtd = 1; }
        if (qtd <= 0) qtd = 1;

        String obsScanner = normalizarObservacaoCozinha(scannerMesaEtObservacaoCozinha != null ? scannerMesaEtObservacaoCozinha.getText().toString() : "");
        int tipoProdutoId = 0;
        try { tipoProdutoId = ((Number) produto.get("tipo_produto_id")).intValue(); } catch (Exception ignored) {}
        if (tipoProdutoId > 0) {
            verificarAdicionaisEAdicionar(produto, qtd, obsScanner, scannerMesaItens, scannerMesaAdapter, scannerMesaDialogView,
                    scannerMesaSpProduto, scannerMesaEtQtdProduto, scannerMesaEtObservacaoCozinha, scannerMesaId, scannerMesaOcupacaoIdHolder);
        } else {
            adicionarProdutoNaMesa(produto, qtd, obsScanner, new ArrayList<>(), scannerMesaItens, scannerMesaAdapter,
                    scannerMesaDialogView, scannerMesaId, scannerMesaOcupacaoIdHolder);
            if (scannerMesaSpProduto != null) scannerMesaSpProduto.setSelection(0);
            if (scannerMesaEtQtdProduto != null) scannerMesaEtQtdProduto.setText("1");
            if (scannerMesaEtObservacaoCozinha != null) scannerMesaEtObservacaoCozinha.setText("");
        }
    }

    private Map<String, Object> localizarProdutoPorCodigo(String codigo) {
        if (codigo == null) return null;
        String c = codigo.trim();
        for (Map<String, Object> p : produtosList) {
            if (p == null) continue;
            Object id = p.get("id");
            if (id != null && c.equals(String.valueOf(id))) return p;
            Object barras = p.get("codigo_barras");
            if (barras != null && c.equalsIgnoreCase(String.valueOf(barras).trim())) return p;
            Object codigoInterno = p.get("codigo");
            if (codigoInterno != null && c.equalsIgnoreCase(String.valueOf(codigoInterno).trim())) return p;
        }
        // fallback no banco para campos comuns de codigo de barras
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            String[] campos = {"codigo_barras", "codigo", "ean", "gtin"};
            for (String campo : campos) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, descricao, preco_venda, tipo_produto_id FROM produtos WHERE ativo = 1 AND " + campo + " = ? LIMIT 1")) {
                    ps.setString(1, c);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Map<String, Object> p = new LinkedHashMap<>();
                            p.put("id", rs.getInt("id"));
                            p.put("descricao", rs.getString("descricao"));
                            p.put("preco_venda", rs.getDouble("preco_venda"));
                            try { p.put("tipo_produto_id", rs.getInt("tipo_produto_id")); } catch (Exception ignored) { p.put("tipo_produto_id", 0); }
                            return p;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * v6.7.7 - Encerra a ocupacao da mesa e limpa os itens apos o pagamento ser concluido.
     */
    private void encerrarMesaAposPagamento() {
        if (ocupacaoIdPagamento <= 0) return;

        showLoading("Encerrando mesa...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Remover adicionais dos itens
                try {
                    PreparedStatement psAd = conn.prepareStatement(
                            "DELETE FROM itens_mesa_adicionais WHERE item_mesa_id IN (SELECT id FROM itens_mesa WHERE ocupacao_id = ?)");
                    psAd.setInt(1, ocupacaoIdPagamento);
                    psAd.executeUpdate();
                    psAd.close();
                } catch (Exception ignored) {}

                // Remover itens da mesa
                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_mesa WHERE ocupacao_id = ?");
                ps.setInt(1, ocupacaoIdPagamento);
                ps.executeUpdate();
                ps.close();

                // Encerrar ocupacao
                ps = conn.prepareStatement("UPDATE ocupacao_mesa SET status = 'encerrada', data_fechamento = NOW(), reservado_por_usuario_id = 0, reservado_por_usuario_nome = NULL WHERE id = ?");
                ps.setInt(1, ocupacaoIdPagamento);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Mesa encerrada com sucesso!");
                    // Limpar controles
                    mesaIdPagamento = 0;
                    ocupacaoIdPagamento = 0;
                    dialogMesaAtual = null;
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    /**
     * v6.7.6 - Verifica se o produto possui tipo com adicionais vinculados.
     * Se sim, exibe dialogo para o usuario escolher os adicionais.
     * Se nao, adiciona o produto diretamente a mesa.
     * v7.0.3 - AUTOSAVE: Recebe mesaId e ocupacaoIdHolder para autosave.
     */
    private void verificarAdicionaisEAdicionar(Map<String, Object> produtoSelecionado, double quantidade, String observacaoCozinha,
                                                List<Map<String, Object>> itensMesa,
                                                GenericAdapter<Map<String, Object>> itensAdapter,
                                                View dialogView, Spinner spProduto, EditText etQtdProduto, EditText etObservacaoCozinhaItem,
                                                int mesaId, int[] ocupacaoIdHolder) {
        int tipoProdutoId = 0;
        try {
            tipoProdutoId = ((Number) produtoSelecionado.get("tipo_produto_id")).intValue();
        } catch (Exception ignored) {}

        if (tipoProdutoId <= 0) {
            adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, new ArrayList<>(), itensMesa, itensAdapter, dialogView, mesaId, ocupacaoIdHolder);
            spProduto.setSelection(0);
            etQtdProduto.setText("1");
            if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
            return;
        }

        final int finalTipoProdutoId = tipoProdutoId;
        showLoading("Verificando adicionais...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.id, a.descricao, a.preco FROM tipo_produto_adicionais tpa " +
                    "INNER JOIN adicionais a ON tpa.adicional_id = a.id " +
                    "WHERE tpa.tipo_produto_id = ? AND a.ativo = 1 ORDER BY a.descricao");
                ps.setInt(1, finalTipoProdutoId);
                ResultSet rs = ps.executeQuery();

                List<Map<String, Object>> adicionaisDisponiveis = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> ad = new LinkedHashMap<>();
                    ad.put("id", rs.getInt("id"));
                    ad.put("descricao", rs.getString("descricao"));
                    ad.put("preco", rs.getDouble("preco"));
                    adicionaisDisponiveis.add(ad);
                }
                rs.close();
                ps.close();
                hideLoading();

                runOnUiThread(() -> {
                    if (adicionaisDisponiveis.isEmpty()) {
                        // Tipo sem adicionais vinculados - adicionar direto
                        // v7.0.3 - AUTOSAVE
                        adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, new ArrayList<>(), itensMesa, itensAdapter, dialogView, mesaId, ocupacaoIdHolder);
                        spProduto.setSelection(0);
                        etQtdProduto.setText("1");
                        if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
                    } else {
                        // Mostrar dialogo para escolher adicionais
                        // v7.0.3 - AUTOSAVE: passa mesaId e ocupacaoIdHolder
                        mostrarDialogoAdicionais(produtoSelecionado, quantidade, observacaoCozinha, adicionaisDisponiveis,
                                itensMesa, itensAdapter, dialogView, spProduto, etQtdProduto, etObservacaoCozinhaItem, mesaId, ocupacaoIdHolder);
                    }
                });
            } catch (Exception e) {
                hideLoading();
                // Em caso de erro, adicionar sem adicionais
                // v7.0.3 - AUTOSAVE
                runOnUiThread(() -> {
                    adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, new ArrayList<>(), itensMesa, itensAdapter, dialogView, mesaId, ocupacaoIdHolder);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                    if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
                });
            }
        }).start();
    }

    /**
     * v6.7.6 - Exibe dialogo com checkboxes para o usuario escolher os adicionais
     * ao adicionar um produto na mesa.
     * v7.0.3 - AUTOSAVE: Recebe mesaId e ocupacaoIdHolder para salvar automaticamente.
     */
    private void mostrarDialogoAdicionais(Map<String, Object> produtoSelecionado, double quantidade, String observacaoCozinha,
                                           List<Map<String, Object>> adicionaisDisponiveis,
                                           List<Map<String, Object>> itensMesa,
                                           GenericAdapter<Map<String, Object>> itensAdapter,
                                           View mesaDialogView, Spinner spProduto, EditText etQtdProduto, EditText etObservacaoCozinhaItem,
                                           int mesaId, int[] ocupacaoIdHolder) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        // Titulo informativo
        String descProduto = produtoSelecionado.get("descricao") != null ? produtoSelecionado.get("descricao").toString() : "Produto";
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Selecione os adicionais para:\n" + descProduto);
        tvInfo.setTextColor(0xFF00BCD4);
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, 0, 0, 16);
        container.addView(tvInfo);

        // Criar checkboxes para cada adicional
        List<CheckBox> checkboxes = new ArrayList<>();
        for (Map<String, Object> ad : adicionaisDisponiveis) {
            String descricao = (String) ad.get("descricao");
            double preco = ((Number) ad.get("preco")).doubleValue();

            CheckBox cb = new CheckBox(this);
            String textoAd = descricao;
            if (preco > 0) {
                textoAd += " (+R$ " + FormatUtils.formatMoney(preco) + ")";
            }
            cb.setText(textoAd);
            cb.setTextColor(0xFFFFFFFF);
            cb.setTextSize(14);
            cb.setPadding(8, 12, 8, 12);
            cb.setTag(ad);
            checkboxes.add(cb);
            container.addView(cb);
        }

        // Texto de total dos adicionais
        TextView tvTotalAd = new TextView(this);
        tvTotalAd.setText("Total adicionais: R$ 0,00");
        tvTotalAd.setTextColor(0xFFFFD700);
        tvTotalAd.setTextSize(13);
        tvTotalAd.setPadding(0, 16, 0, 0);
        container.addView(tvTotalAd);

        // Atualizar total quando checkboxes mudam
        for (CheckBox cb : checkboxes) {
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                double totalAd = 0;
                for (CheckBox c : checkboxes) {
                    if (c.isChecked()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> adData = (Map<String, Object>) c.getTag();
                        totalAd += ((Number) adData.get("preco")).doubleValue();
                    }
                }
                tvTotalAd.setText("Total adicionais: R$ " + FormatUtils.formatMoney(totalAd));
            });
        }

        new AlertDialog.Builder(this)
                .setTitle("Adicionais")
                .setView(dialogView)
                .setPositiveButton("Confirmar", (d, w) -> {
                    // Coletar adicionais selecionados
                    List<Map<String, Object>> selecionados = new ArrayList<>();
                    for (CheckBox cb : checkboxes) {
                        if (cb.isChecked()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> adData = (Map<String, Object>) cb.getTag();
                            Map<String, Object> adSel = new LinkedHashMap<>();
                            adSel.put("id", ((Number) adData.get("id")).intValue());
                            adSel.put("descricao", adData.get("descricao"));
                            adSel.put("preco", ((Number) adData.get("preco")).doubleValue());
                            selecionados.add(adSel);
                        }
                    }
                    // v7.0.3 - AUTOSAVE
                    adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, selecionados, itensMesa, itensAdapter, mesaDialogView, mesaId, ocupacaoIdHolder);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                    if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
                })
                .setNegativeButton("Sem adicionais", (d, w) -> {
                    // Adicionar sem adicionais
                    // v7.0.3 - AUTOSAVE
                    adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, new ArrayList<>(), itensMesa, itensAdapter, mesaDialogView, mesaId, ocupacaoIdHolder);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                    if (etObservacaoCozinhaItem != null) etObservacaoCozinhaItem.setText("");
                })
                .show();
    }

    /**
     * v6.7.6 - Adiciona um produto na lista de itens da mesa, incluindo adicionais selecionados.
     * v7.0.3 - AUTOSAVE: Agora salva automaticamente no banco ao adicionar o item.
     *          Recebe mesaId e ocupacaoIdHolder para criar/reutilizar a ocupacao.
     */
    private void adicionarProdutoNaMesa(Map<String, Object> produtoSelecionado, double quantidade,
                                         List<Map<String, Object>> adicionaisSelecionados,
                                         List<Map<String, Object>> itensMesa,
                                         GenericAdapter<Map<String, Object>> itensAdapter,
                                         View dialogView) {
        adicionarProdutoNaMesa(produtoSelecionado, quantidade, "", adicionaisSelecionados,
                itensMesa, itensAdapter, dialogView, 0, null);
    }

    private void adicionarProdutoNaMesa(Map<String, Object> produtoSelecionado, double quantidade,
                                         String observacaoCozinha,
                                         List<Map<String, Object>> adicionaisSelecionados,
                                         List<Map<String, Object>> itensMesa,
                                         GenericAdapter<Map<String, Object>> itensAdapter,
                                         View dialogView) {
        adicionarProdutoNaMesa(produtoSelecionado, quantidade, observacaoCozinha, adicionaisSelecionados,
                itensMesa, itensAdapter, dialogView, 0, null);
    }

    /**
     * v7.0.3 - AUTOSAVE: Adiciona produto na mesa e salva automaticamente no banco.
     * Se mesaId > 0 e ocupacaoIdHolder != null, persiste o item imediatamente.
     * Cria uma nova ocupacao se necessario (ocupacaoIdHolder[0] == 0).
     */
    private void adicionarProdutoNaMesa(Map<String, Object> produtoSelecionado, double quantidade,
                                         String observacaoCozinha,
                                         List<Map<String, Object>> adicionaisSelecionados,
                                         List<Map<String, Object>> itensMesa,
                                         GenericAdapter<Map<String, Object>> itensAdapter,
                                         View dialogView, int mesaId, int[] ocupacaoIdHolder) {
        Map<String, Object> novoItem = new LinkedHashMap<>();
        novoItem.put("id", 0); // novo, sera atualizado apos salvar
        novoItem.put("produto_id", ((Number) produtoSelecionado.get("id")).intValue());
        novoItem.put("descricao_produto", produtoSelecionado.get("descricao").toString());
        novoItem.put("quantidade", quantidade);
        double preco = ((Number) produtoSelecionado.get("preco_venda")).doubleValue();
        novoItem.put("preco_unitario", preco);
        novoItem.put("total", quantidade * preco);
        novoItem.put("impresso", 0); // v6.7.9 - Novo item nao impresso
        novoItem.put("observacao_cozinha", normalizarObservacaoCozinha(observacaoCozinha));

        // v6.7.6 - Processar adicionais
        double totalAdicionais = 0;
        StringBuilder adDescBuilder = new StringBuilder();
        if (adicionaisSelecionados != null && !adicionaisSelecionados.isEmpty()) {
            for (Map<String, Object> ad : adicionaisSelecionados) {
                double adPreco = ((Number) ad.get("preco")).doubleValue();
                totalAdicionais += adPreco;
                if (adDescBuilder.length() > 0) adDescBuilder.append(", ");
                adDescBuilder.append(ad.get("descricao"));
                if (adPreco > 0) {
                    adDescBuilder.append(" (+R$ ").append(String.format("%.2f", adPreco)).append(")");
                }
            }
        }
        novoItem.put("adicionais_total", totalAdicionais * quantidade);
        novoItem.put("adicionais_descricao", adDescBuilder.toString());
        novoItem.put("adicionais_lista", adicionaisSelecionados);

        itensMesa.add(novoItem);
        itensAdapter.setItems(new ArrayList<>(itensMesa));
        atualizarTotalDialog(dialogView, itensMesa);

        // v7.0.3 - AUTOSAVE: Salvar automaticamente no banco de dados
        if (mesaId > 0 && ocupacaoIdHolder != null) {
            autoSalvarItemMesa(mesaId, ocupacaoIdHolder, novoItem, adicionaisSelecionados, itensMesa, itensAdapter, dialogView);
        }
    }

    /**
     * v7.0.3 - Salva automaticamente um item da mesa no banco de dados.
     * Cria a ocupacao se necessario e insere o item com seus adicionais.
     */
    private void autoSalvarItemMesa(int mesaId, int[] ocupacaoIdHolder, Map<String, Object> novoItem,
                                     List<Map<String, Object>> adicionaisSelecionados,
                                     List<Map<String, Object>> itensMesa,
                                     GenericAdapter<Map<String, Object>> itensAdapter,
                                     View dialogView) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                int ocupacaoId = ocupacaoIdHolder[0];

                // Se nao existe ocupacao, criar uma nova
                if (ocupacaoId == 0) {
                    PreparedStatement psOcup = conn.prepareStatement(
                            "INSERT INTO ocupacao_mesa (mesa_id, garcom_id, qtd_pessoas, status, data_abertura) VALUES (?,0,0,'ocupada',NOW())",
                            Statement.RETURN_GENERATED_KEYS);
                    psOcup.setInt(1, mesaId);
                    psOcup.executeUpdate();
                    ResultSet keysOcup = psOcup.getGeneratedKeys();
                    if (keysOcup.next()) {
                        ocupacaoId = keysOcup.getInt(1);
                        ocupacaoIdHolder[0] = ocupacaoId;
                    }
                    keysOcup.close();
                    psOcup.close();
                } else {
                    // Garantir que o status seja 'ocupada' se estava 'livre'
                    PreparedStatement psStatus = conn.prepareStatement(
                            "UPDATE ocupacao_mesa SET status = CASE WHEN status = 'livre' THEN 'ocupada' ELSE status END WHERE id = ?");
                    psStatus.setInt(1, ocupacaoId);
                    psStatus.executeUpdate();
                    psStatus.close();
                }

                if (ocupacaoId > 0) {
                    String adDesc = novoItem.get("adicionais_descricao") != null ? novoItem.get("adicionais_descricao").toString() : "";
                    double adTotal = novoItem.get("adicionais_total") != null ? ((Number) novoItem.get("adicionais_total")).doubleValue() : 0;

                    PreparedStatement psItem = conn.prepareStatement(
                            "INSERT INTO itens_mesa (ocupacao_id, produto_id, descricao_produto, quantidade, preco_unitario, total, adicionais_descricao, adicionais_total, observacao_cozinha, impresso) VALUES (?,?,?,?,?,?,?,?,?,0)",
                            Statement.RETURN_GENERATED_KEYS);
                    psItem.setInt(1, ocupacaoId);
                    psItem.setInt(2, ((Number) novoItem.get("produto_id")).intValue());
                    psItem.setString(3, novoItem.get("descricao_produto").toString());
                    psItem.setDouble(4, ((Number) novoItem.get("quantidade")).doubleValue());
                    psItem.setDouble(5, ((Number) novoItem.get("preco_unitario")).doubleValue());
                    psItem.setDouble(6, ((Number) novoItem.get("total")).doubleValue());
                    psItem.setString(7, adDesc);
                    psItem.setDouble(8, adTotal);
                    psItem.setString(9, novoItem.get("observacao_cozinha") != null ? novoItem.get("observacao_cozinha").toString() : "");
                    psItem.executeUpdate();

                    ResultSet keysItem = psItem.getGeneratedKeys();
                    int novoItemId = 0;
                    if (keysItem.next()) {
                        novoItemId = keysItem.getInt(1);
                    }
                    keysItem.close();
                    psItem.close();

                    // Atualizar o ID na lista local
                    if (novoItemId > 0) {
                        novoItem.put("id", novoItemId);
                    }

                    // Salvar adicionais individuais
                    if (novoItemId > 0 && adicionaisSelecionados != null && !adicionaisSelecionados.isEmpty()) {
                        for (Map<String, Object> ad : adicionaisSelecionados) {
                            PreparedStatement psAd = conn.prepareStatement(
                                    "INSERT INTO itens_mesa_adicionais (item_mesa_id, adicional_id, descricao_adicional, preco) VALUES (?,?,?,?)");
                            psAd.setInt(1, novoItemId);
                            psAd.setInt(2, ((Number) ad.get("id")).intValue());
                            psAd.setString(3, ad.get("descricao") != null ? ad.get("descricao").toString() : "");
                            psAd.setDouble(4, ((Number) ad.get("preco")).doubleValue());
                            psAd.executeUpdate();
                            psAd.close();
                        }
                    }
                }

                runOnUiThread(() -> {
                    showToast(novoItem.get("descricao_produto") + " salvo automaticamente!");
                    itensAdapter.setItems(new ArrayList<>(itensMesa));
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao auto-salvar item da mesa: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao salvar item automaticamente: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Carrega os itens de uma ocupacao de mesa, incluindo adicionais.
     * v6.7.9 - Agora carrega tambem o campo 'impresso'.
     */
    private void carregarItensMesa(int ocupacaoId, List<Map<String, Object>> itensMesa,
                                    GenericAdapter<Map<String, Object>> adapter, View dialogView) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM itens_mesa WHERE ocupacao_id = ? ORDER BY id ASC");
                ps.setInt(1, ocupacaoId);
                ResultSet rs = ps.executeQuery();

                itensMesa.clear();
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id"));
                    item.put("produto_id", rs.getInt("produto_id"));
                    item.put("descricao_produto", rs.getString("descricao_produto"));
                    item.put("quantidade", rs.getDouble("quantidade"));
                    item.put("preco_unitario", rs.getDouble("preco_unitario"));
                    item.put("total", rs.getDouble("total"));

                    // v6.7.6 - Carregar dados de adicionais
                    String adDesc = "";
                    double adTotal = 0;
                    try {
                        adDesc = rs.getString("adicionais_descricao");
                        if (adDesc == null) adDesc = "";
                    } catch (Exception ignored) {}
                    try {
                        adTotal = rs.getDouble("adicionais_total");
                    } catch (Exception ignored) {}
                    item.put("adicionais_descricao", adDesc);
                    item.put("adicionais_total", adTotal);
                    String obsCozinha = "";
                    try {
                        obsCozinha = rs.getString("observacao_cozinha");
                        if (obsCozinha == null) obsCozinha = "";
                    } catch (Exception ignored) {}
                    item.put("observacao_cozinha", obsCozinha);

                    // v6.7.9 - Carregar flag de impressao
                    int impresso = 0;
                    try {
                        impresso = rs.getInt("impresso");
                    } catch (Exception ignored) {}
                    item.put("impresso", impresso);

                    // Carregar adicionais individuais do banco
                    int itemMesaId = rs.getInt("id");
                    List<Map<String, Object>> adicionaisItem = new ArrayList<>();
                    try {
                        PreparedStatement psAd = conn.prepareStatement(
                                "SELECT * FROM itens_mesa_adicionais WHERE item_mesa_id = ? ORDER BY id ASC");
                        psAd.setInt(1, itemMesaId);
                        ResultSet rsAd = psAd.executeQuery();
                        while (rsAd.next()) {
                            Map<String, Object> ad = new LinkedHashMap<>();
                            ad.put("id", rsAd.getInt("adicional_id"));
                            ad.put("descricao", rsAd.getString("descricao_adicional"));
                            ad.put("preco", rsAd.getDouble("preco"));
                            adicionaisItem.add(ad);
                        }
                        rsAd.close();
                        psAd.close();
                    } catch (Exception ignored) {}
                    item.put("adicionais_lista", adicionaisItem);

                    itensMesa.add(item);
                }
                rs.close();
                ps.close();

                runOnUiThread(() -> {
                    adapter.setItems(new ArrayList<>(itensMesa));
                    atualizarTotalDialog(dialogView, itensMesa);
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar itens da mesa: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Remove um item da mesa do banco de dados, incluindo seus adicionais.
     */
    private void removerItemMesa(int itemId, List<Map<String, Object>> itensMesa,
                                  GenericAdapter<Map<String, Object>> adapter, View dialogView) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v6.7.6 - Remover adicionais do item primeiro
                try {
                    PreparedStatement psAd = conn.prepareStatement("DELETE FROM itens_mesa_adicionais WHERE item_mesa_id = ?");
                    psAd.setInt(1, itemId);
                    psAd.executeUpdate();
                    psAd.close();
                } catch (Exception ignored) {}

                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_mesa WHERE id = ?");
                ps.setInt(1, itemId);
                ps.executeUpdate();
                ps.close();

                // Remover da lista local
                runOnUiThread(() -> {
                    for (int i = itensMesa.size() - 1; i >= 0; i--) {
                        if (itensMesa.get(i).get("id") != null &&
                                ((Number) itensMesa.get(i).get("id")).intValue() == itemId) {
                            itensMesa.remove(i);
                            break;
                        }
                    }
                    adapter.setItems(new ArrayList<>(itensMesa));
                    atualizarTotalDialog(dialogView, itensMesa);
                    showToast("Item removido");
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    /**
     * Atualiza o total exibido no dialog, incluindo adicionais.
     */
    private void atualizarTotalDialog(View dialogView, List<Map<String, Object>> itensMesa) {
        double total = calcularTotalItens(itensMesa);
        TextView tvTotal = dialogView.findViewById(R.id.tvTotalMesa);
        tvTotal.setText(String.format("Total: R$ %.2f", total));
    }

    /**
     * Salva os dados da mesa (garcom, pessoas, itens com adicionais).
     * v6.7.6 - Agora salva tambem os adicionais de cada item.
     * v6.7.8 - Preserva dados de reserva ao salvar.
     * v6.7.9 - Novos itens sao salvos com impresso = 0.
     */
    private void salvarMesa(int mesaId, int ocupacaoIdAtual, int garcomId, int qtdPessoas,
                             List<Map<String, Object>> itensMesa, AlertDialog dialog) {
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                int ocupacaoId = ocupacaoIdAtual;

                // Determinar status: se tem itens, ocupada; senao, verificar se esta reservada
                // v6.8.0 - Preservar status 'pronta' ao salvar
                String novoStatus;
                if (!itensMesa.isEmpty()) {
                    // v6.8.0 - Verificar se a mesa esta marcada como pronta para manter o status
                    if (ocupacaoId > 0) {
                        PreparedStatement psCheckPronta = conn.prepareStatement(
                                "SELECT status FROM ocupacao_mesa WHERE id = ?");
                        psCheckPronta.setInt(1, ocupacaoId);
                        ResultSet rsCheckPronta = psCheckPronta.executeQuery();
                        String statusAtualSalvar = "ocupada";
                        if (rsCheckPronta.next()) {
                            statusAtualSalvar = rsCheckPronta.getString("status");
                        }
                        rsCheckPronta.close();
                        psCheckPronta.close();

                        if ("pronta".equals(statusAtualSalvar)) {
                            novoStatus = "pronta"; // Manter status pronta
                        } else {
                            novoStatus = "ocupada";
                        }
                    } else {
                        novoStatus = "ocupada";
                    }
                } else {
                    // v6.7.8 - Verificar se a mesa esta reservada para manter o status
                    if (ocupacaoId > 0) {
                        PreparedStatement psCheck = conn.prepareStatement(
                                "SELECT status, reservado_por_usuario_id FROM ocupacao_mesa WHERE id = ?");
                        psCheck.setInt(1, ocupacaoId);
                        ResultSet rsCheck = psCheck.executeQuery();
                        String statusAtual = "livre";
                        int reservadoPorId = 0;
                        if (rsCheck.next()) {
                            statusAtual = rsCheck.getString("status");
                            reservadoPorId = rsCheck.getInt("reservado_por_usuario_id");
                        }
                        rsCheck.close();
                        psCheck.close();

                        if ("reservada".equals(statusAtual) && reservadoPorId > 0) {
                            novoStatus = "reservada"; // Manter reserva
                        } else {
                            novoStatus = "livre";
                        }
                    } else {
                        novoStatus = "livre";
                    }
                }

                if (ocupacaoId == 0) {
                    // Criar nova ocupacao
                    if (!itensMesa.isEmpty() || garcomId > 0 || qtdPessoas > 0) {
                        PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO ocupacao_mesa (mesa_id, garcom_id, qtd_pessoas, status, data_abertura) VALUES (?,?,?,?,NOW())",
                                Statement.RETURN_GENERATED_KEYS);
                        ps.setInt(1, mesaId);
                        ps.setInt(2, garcomId);
                        ps.setInt(3, qtdPessoas);
                        ps.setString(4, novoStatus);
                        ps.executeUpdate();
                        ResultSet keys = ps.getGeneratedKeys();
                        if (keys.next()) {
                            ocupacaoId = keys.getInt(1);
                        }
                        keys.close();
                        ps.close();
                    }
                } else {
                    // Atualizar ocupacao existente (preservando dados de reserva)
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ocupacao_mesa SET garcom_id=?, qtd_pessoas=?, status=? WHERE id=?");
                    ps.setInt(1, garcomId);
                    ps.setInt(2, qtdPessoas);
                    ps.setString(3, novoStatus);
                    ps.setInt(4, ocupacaoId);
                    ps.executeUpdate();
                    ps.close();
                }

                // Salvar itens novos (id == 0) com adicionais
                if (ocupacaoId > 0) {
                    for (Map<String, Object> item : itensMesa) {
                        int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (itemId == 0) {
                            // v6.7.6 - Incluir adicionais_descricao e adicionais_total no INSERT
                            // v6.7.9 - Incluir impresso = 0 para novos itens
                            String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
                            double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0;

                            // v6.9.9 - Respeitar o flag impresso da lista local
                            // Se o item ja foi impresso antes de salvar (via impressao em memoria),
                            // salvar com impresso = 1 para manter a consistencia
                            int impressoLocal = item.get("impresso") != null ? ((Number) item.get("impresso")).intValue() : 0;

                            PreparedStatement psItem = conn.prepareStatement(
                                    "INSERT INTO itens_mesa (ocupacao_id, produto_id, descricao_produto, quantidade, preco_unitario, total, adicionais_descricao, adicionais_total, observacao_cozinha, impresso) VALUES (?,?,?,?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS);
                            psItem.setInt(1, ocupacaoId);
                            psItem.setInt(2, ((Number) item.get("produto_id")).intValue());
                            psItem.setString(3, item.get("descricao_produto").toString());
                            psItem.setDouble(4, ((Number) item.get("quantidade")).doubleValue());
                            psItem.setDouble(5, ((Number) item.get("preco_unitario")).doubleValue());
                            psItem.setDouble(6, ((Number) item.get("total")).doubleValue());
                            psItem.setString(7, adDesc);
                            psItem.setDouble(8, adTotal);
                            psItem.setString(9, item.get("observacao_cozinha") != null ? item.get("observacao_cozinha").toString() : "");
                            psItem.setInt(10, impressoLocal);
                            psItem.executeUpdate();

                            // Obter o ID do item inserido para salvar os adicionais individuais
                            ResultSet keysItem = psItem.getGeneratedKeys();
                            int novoItemId = 0;
                            if (keysItem.next()) {
                                novoItemId = keysItem.getInt(1);
                            }
                            keysItem.close();
                            psItem.close();

                            // Atualizar o ID na lista local
                            if (novoItemId > 0) {
                                item.put("id", novoItemId);
                            }

                            // v6.7.6 - Salvar adicionais individuais
                            if (novoItemId > 0 && item.get("adicionais_lista") != null) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> adicionaisLista = (List<Map<String, Object>>) item.get("adicionais_lista");
                                if (adicionaisLista != null && !adicionaisLista.isEmpty()) {
                                    for (Map<String, Object> ad : adicionaisLista) {
                                        PreparedStatement psAd = conn.prepareStatement(
                                                "INSERT INTO itens_mesa_adicionais (item_mesa_id, adicional_id, descricao_adicional, preco) VALUES (?,?,?,?)");
                                        psAd.setInt(1, novoItemId);
                                        psAd.setInt(2, ((Number) ad.get("id")).intValue());
                                        psAd.setString(3, ad.get("descricao") != null ? ad.get("descricao").toString() : "");
                                        psAd.setDouble(4, ((Number) ad.get("preco")).doubleValue());
                                        psAd.executeUpdate();
                                        psAd.close();
                                    }
                                }
                            }
                        }
                    }
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Mesa salva com sucesso!");
                    dialog.dismiss();
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * Limpa todos os itens e encerra a ocupacao da mesa.
     * v6.7.6 - Tambem limpa adicionais dos itens.
     * v6.7.8 - Tambem limpa dados de reserva.
     */
    private void limparMesa(int mesaId, int ocupacaoId) {
        showLoading("Limpando mesa...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (ocupacaoId > 0) {
                    // v6.7.6 - Remover adicionais dos itens primeiro
                    try {
                        PreparedStatement psAd = conn.prepareStatement(
                                "DELETE FROM itens_mesa_adicionais WHERE item_mesa_id IN (SELECT id FROM itens_mesa WHERE ocupacao_id = ?)");
                        psAd.setInt(1, ocupacaoId);
                        psAd.executeUpdate();
                        psAd.close();
                    } catch (Exception ignored) {}

                    // Remover itens
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_mesa WHERE ocupacao_id = ?");
                    ps.setInt(1, ocupacaoId);
                    ps.executeUpdate();
                    ps.close();

                    // Encerrar ocupacao e limpar reserva
                    ps = conn.prepareStatement("UPDATE ocupacao_mesa SET status = 'encerrada', data_fechamento = NOW(), reservado_por_usuario_id = 0, reservado_por_usuario_nome = NULL WHERE id = ?");
                    ps.setInt(1, ocupacaoId);
                    ps.executeUpdate();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Mesa limpa com sucesso!");
                    carregarMesas();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    // ==================== Metodos utilitarios para cupom ====================

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
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
