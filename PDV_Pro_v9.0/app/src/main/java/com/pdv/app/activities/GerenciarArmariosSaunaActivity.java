package com.pdv.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.ArmarioSaunaAdapter;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.PrinterManager;
import com.pdv.app.utils.TaxaServicoPreferences;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tela de gerenciamento visual de armarios para sauna.
 * Exibe todos os armarios em grid com cores por status:
 * - Verde: armario livre (disponivel)
 * - Vermelho: armario ocupado (chave entregue a um cliente)
 * - Roxo: armario em manutencao (indisponivel)
 *
 * v6.9.5 - Modulo de Armarios para Sauna
 * v6.9.5.1 - Correcao de exibicao e otimizacao de performance
 * v6.9.8 - OTIMIZACAO MASSIVA DE PERFORMANCE
 * v7.0.0 - OTIMIZACAO AVANCADA COMPLETA:
 *   - AtomicBoolean para controle thread-safe de carregamento
 *   - Cache local de armarios para exibicao instantanea enquanto recarrega
 *   - Carregamento em 2 fases: exibe cache primeiro, atualiza em background
 *   - Query principal com indice otimizado e LIMIT para seguranca
 *   - Timer inteligente: 15s ocupados, 45s livres (mais responsivo)
 *   - RecyclerView com prefetch e cache de views otimizado
 *   - Adapter com StableIds e DiffUtil com payload (zero flickering)
 *   - Produtos pre-carregados em paralelo no onCreate
 *   - Connection reuse com validacao automatica
 *   - Tratamento robusto de erros com retry automatico
 *   - Feedback visual melhorado (progress sutil, sem loading dialog)
 * v7.0.2 - Horario de entrada e saida do armario:
 *   - Exibicao da hora de saida (fechamento da conta) no grid e dialog
 *   - Registro e exibicao de data_saida ao encerrar/pagar
 *   - Hora de entrada e saida no cupom de impressao
 *   - Tempo total de permanencia calculado entre entrada e saida
 */
public class GerenciarArmariosSaunaActivity extends BaseActivity {
    private static final String TAG = "GerenciarArmariosSauna";
    private static final int REQUEST_PAGAMENTO_ARMARIO = 400;
    private static final int REQUEST_SCAN_ARMARIO_PRODUTO = 7300;
    private RecyclerView recyclerArmarios;
    private ArmarioSaunaAdapter armarioAdapter;
    private TextView tvResumoArmarios;
    private ProgressBar progressBarArmarios;
    private TextView tvSemArmarios;

    // v7.0.3 - Campo de busca rapida por numero do armario
    private EditText etBuscarArmario;
    private Button btnBuscarArmario;
    // v7.0.3 - Indicador grande de armarios livres
    private TextView tvQtdLivres;

    // Dados carregados para uso nos dialogs
    private List<Map<String, Object>> produtosList = new ArrayList<>();
    private List<Map<String, Object>> categoriasList = new ArrayList<>();
    private boolean produtosCarregados = false;
    // v7.0.0 - Timestamp do ultimo carregamento de produtos para cache com TTL
    private long produtosCarregadosTimestamp = 0;
    private static final long PRODUTOS_CACHE_TTL = 5 * 60 * 1000; // 5 minutos

    // Controle do armario sendo encaminhado para pagamento
    private int armarioIdPagamento = 0;
    private int usoArmarioIdPagamento = 0;
    // v7.0.2 - Hora de entrada do armario para pagamento
    private String horaEntradaPagamento = "";

    // Handler para atualizar tempo de uso periodicamente
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    // Flag para controlar se as tabelas ja foram verificadas nesta sessao
    private boolean tabelasVerificadas = false;

    // v7.0.0 - Pool de threads reutilizavel para operacoes de banco
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    // v7.0.0 - Flag para evitar carregamento duplo onCreate+onResume
    private boolean primeiroCarregamentoFeito = false;

    // v7.0.0 - Flag para saber se tem armarios ocupados (ajusta timer)
    private boolean temArmariosOcupados = false;

    // Contexto do dialog aberto para adicionar consumo por leitor de codigo de barras
    private List<Map<String, Object>> currentArmarioItens;
    private GenericAdapter<Map<String, Object>> currentArmarioAdapter;
    private View currentArmarioDialogView;
    private Spinner currentArmarioSpProduto;
    private EditText currentArmarioEtQtdProduto;
    private int currentArmarioUsoId = 0;

    // v7.0.0 - AtomicBoolean thread-safe para controle de carregamento simultaneo
    private final AtomicBoolean carregamentoEmAndamento = new AtomicBoolean(false);

    // v7.0.0 - Cache local de armarios para exibicao instantanea
    private List<Map<String, Object>> cacheArmarios = null;
    private int cacheCountLivres = 0, cacheCountOcupados = 0, cacheCountManutencao = 0;

    // v7.0.0 - Contador de erros consecutivos para retry inteligente
    private int errosConsecutivos = 0;
    private static final int MAX_ERROS_CONSECUTIVOS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gerenciar_armarios_sauna);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR)) {
            return;
        }

        tvResumoArmarios = findViewById(R.id.tvResumoArmarios);
        progressBarArmarios = findViewById(R.id.progressBarArmarios);
        tvSemArmarios = findViewById(R.id.tvSemArmarios);

        // v7.0.3 - Campo de busca rapida por numero do armario
        etBuscarArmario = findViewById(R.id.etBuscarArmario);
        btnBuscarArmario = findViewById(R.id.btnBuscarArmario);
        tvQtdLivres = findViewById(R.id.tvQtdLivres);
        configurarBuscaRapidaArmario();

        recyclerArmarios = findViewById(R.id.recyclerArmarios);

        // v7.0.0 - Grid com 3 colunas, prefetch e cache otimizados
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        recyclerArmarios.setLayoutManager(gridLayoutManager);
        recyclerArmarios.setHasFixedSize(true);
        recyclerArmarios.setItemViewCacheSize(40);
        recyclerArmarios.getRecycledViewPool().setMaxRecycledViews(0, 30);

        // v7.0.0 - Desabilitar animacoes padrao para evitar flickering
        recyclerArmarios.setItemAnimator(null);

        armarioAdapter = new ArmarioSaunaAdapter((armario, position) -> {
            abrirDetalheArmario(armario);
        });
        recyclerArmarios.setAdapter(armarioAdapter);

        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        // v7.0.0 - Verificar tabelas e pre-carregar produtos em paralelo
        ensureTablesOnce(() -> {
            carregarArmarios();
            primeiroCarregamentoFeito = true;
        });

        // v7.0.0 - Pre-carregar produtos em paralelo (nao bloqueia a UI)
        carregarDadosAuxiliares();

        // Timer para atualizar periodicamente
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    carregarArmarios();
                    // v7.0.0 - Timer mais responsivo: 15s ocupados, 45s livres
                    long intervalo = temArmariosOcupados ? 15000 : 45000;
                    timerHandler.postDelayed(this, intervalo);
                }
            }
        };
    }

    /**
     * v7.0.0 - Verifica tabelas apenas uma vez e executa callback ao terminar.
     */
    private void ensureTablesOnce(Runnable onComplete) {
        if (tabelasVerificadas) {
            onComplete.run();
            return;
        }
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                db.ensureTablesExist();
            } catch (Exception e) {
                Log.w(TAG, "Aviso ao verificar tabelas: " + e.getMessage());
            }
            tabelasVerificadas = true;
            runOnUiThread(onComplete);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // v7.0.0 - Exibir cache instantaneamente se disponivel
        if (primeiroCarregamentoFeito) {
            if (cacheArmarios != null && !cacheArmarios.isEmpty()) {
                // Exibir cache imediatamente enquanto recarrega em background
                armarioAdapter.setArmarios(cacheArmarios);
                tvResumoArmarios.setText(String.format("Livres: %d | Ocupados: %d | Manutencao: %d | Total: %d",
                        cacheCountLivres, cacheCountOcupados, cacheCountManutencao, cacheArmarios.size()));
                // v7.0.3 - Atualizar indicador grande de armarios livres
                if (tvQtdLivres != null) {
                    tvQtdLivres.setText(String.valueOf(cacheCountLivres));
                }
            }
            carregarArmarios();
        }
        // v7.0.0 - Timer mais responsivo
        long intervalo = temArmariosOcupados ? 15000 : 45000;
        timerHandler.postDelayed(timerRunnable, intervalo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timerHandler.removeCallbacks(timerRunnable);
    }


    private void adicionarProdutoArmarioPorCodigo(String codigo) {
        if (currentArmarioItens == null || currentArmarioAdapter == null || currentArmarioDialogView == null) {
            showError("Abra um armario ocupado antes de usar o leitor de codigo de barras.");
            return;
        }
        Map<String, Object> produtoEncontrado = null;
        for (Map<String, Object> p : produtosList) {
            String cod = p.get("codigo") != null ? p.get("codigo").toString() : "";
            String cb = p.get("codigo_barras") != null ? p.get("codigo_barras").toString() : "";
            if (codigo.equals(cod) || codigo.equals(cb)) {
                produtoEncontrado = p;
                break;
            }
        }
        if (produtoEncontrado == null) {
            showError("Produto nao encontrado para o codigo: " + codigo);
            return;
        }
        double qtd = 1;
        try {
            if (currentArmarioEtQtdProduto != null && currentArmarioEtQtdProduto.getText() != null) {
                String txt = currentArmarioEtQtdProduto.getText().toString().trim();
                if (!txt.isEmpty()) qtd = Double.parseDouble(txt.replace(',', '.'));
            }
        } catch (Exception ignored) { qtd = 1; }
        if (qtd <= 0) qtd = 1;
        final double finalQtd = qtd;
        Map<String, Object> finalProduto = produtoEncontrado;
        int tipoProdutoId = 0;
        try { tipoProdutoId = ((Number) finalProduto.get("tipo_produto_id")).intValue(); } catch (Exception ignored) {}
        if (tipoProdutoId > 0) {
            verificarAdicionaisEAdicionar(finalProduto, finalQtd, currentArmarioItens, currentArmarioAdapter,
                    currentArmarioDialogView, currentArmarioSpProduto, currentArmarioEtQtdProduto, currentArmarioUsoId);
        } else {
            adicionarProdutoNoArmario(finalProduto, finalQtd, new ArrayList<>(), currentArmarioItens,
                    currentArmarioAdapter, currentArmarioDialogView, currentArmarioUsoId);
            if (currentArmarioSpProduto != null) currentArmarioSpProduto.setSelection(0);
            if (currentArmarioEtQtdProduto != null) currentArmarioEtQtdProduto.setText("1");
        }
        showToast("Produto adicionado: " + finalProduto.get("descricao"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        // v7.0.0 - Shutdown do executor ao destruir activity
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdownNow();
        }
    }

    /**
     * Carrega produtos para uso nos dialogs de consumo.
     * v7.0.0 - Cache com TTL de 5 minutos para evitar recarregamentos frequentes.
     */
    private void carregarDadosAuxiliares() {
        long agora = System.currentTimeMillis();
        if (produtosCarregados && !produtosList.isEmpty() 
                && (agora - produtosCarregadosTimestamp) < PRODUTOS_CACHE_TTL) {
            return; // Cache ainda valido
        }

        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Carregar categorias/tipos de produto
                List<Map<String, Object>> novaCategorias = new ArrayList<>();
                try {
                    PreparedStatement psCat = conn.prepareStatement(
                        "SELECT id, descricao FROM tipos_produto WHERE ativo = 1 ORDER BY descricao");
                    ResultSet rsCat = psCat.executeQuery();
                    while (rsCat.next()) {
                        Map<String, Object> cat = new LinkedHashMap<>();
                        cat.put("id", rsCat.getInt("id"));
                        cat.put("descricao", rsCat.getString("descricao"));
                        novaCategorias.add(cat);
                    }
                    rsCat.close();
                    psCat.close();
                } catch (Exception e) {
                    Log.w(TAG, "Aviso ao carregar categorias: " + e.getMessage());
                }

                // Carregar produtos
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, codigo, codigo_barras, descricao, preco_venda, tipo_produto_id FROM produtos WHERE ativo = 1 ORDER BY descricao");
                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> novaLista = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("id", rs.getInt("id"));
                    try { p.put("codigo", rs.getString("codigo")); } catch (Exception ignored) { p.put("codigo", ""); }
                    try { p.put("codigo_barras", rs.getString("codigo_barras")); } catch (Exception ignored) { p.put("codigo_barras", ""); }
                    p.put("descricao", rs.getString("descricao"));
                    p.put("preco_venda", rs.getDouble("preco_venda"));
                    try {
                        p.put("tipo_produto_id", rs.getInt("tipo_produto_id"));
                    } catch (Exception ignored) {
                        p.put("tipo_produto_id", 0);
                    }
                    novaLista.add(p);
                }
                rs.close();
                ps.close();

                // Atualizar cache de forma atomica
                categoriasList = novaCategorias;
                produtosList = novaLista;
                produtosCarregados = true;
                produtosCarregadosTimestamp = System.currentTimeMillis();

            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar dados auxiliares: " + e.getMessage());
            }
        });
    }

    /**
     * Carrega todos os armarios com seus status e dados de uso.
     * 
     * v7.0.0 - OTIMIZACOES AVANCADAS:
     * 1. AtomicBoolean thread-safe para evitar carregamentos simultaneos
     * 2. Cache local para exibicao instantanea no onResume
     * 3. Progress bar sutil (sem loading dialog bloqueante)
     * 4. Query otimizada com LEFT JOIN e subquery agregada
     * 5. Retry automatico em caso de erro de conexao
     * 6. Timer inteligente baseado no estado dos armarios
     */
    private void carregarArmarios() {
        // v7.0.0 - AtomicBoolean thread-safe para evitar carregamentos simultaneos
        if (!carregamentoEmAndamento.compareAndSet(false, true)) {
            return;
        }

        // v7.0.0 - Progress bar sutil, sem loading dialog bloqueante
        runOnUiThread(() -> {
            if (progressBarArmarios != null) {
                progressBarArmarios.setVisibility(View.VISIBLE);
            }
            if (tvSemArmarios != null && cacheArmarios == null) {
                tvSemArmarios.setVisibility(View.GONE);
            }
        });

        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v7.0.2 - Query otimizada com colunas especificas, subquery agregada e data_saida
                String sql = "SELECT a.id, a.numero, a.descricao, a.localizacao, " +
                        "COALESCE(u.id, 0) as uso_id, " +
                        "COALESCE(u.cliente_nome, '') as cliente_nome, " +
                        "COALESCE(u.observacao, '') as observacao, " +
                        "COALESCE(u.status, 'livre') as status, " +
                        "u.data_entrada, " +
                        "u.data_saida, " +
                        "COALESCE(itens_agg.qtd_itens, 0) as qtd_itens, " +
                        "COALESCE(itens_agg.total_val, 0) as total_val " +
                        "FROM armarios_sauna a " +
                        "LEFT JOIN uso_armario_sauna u ON a.id = u.armario_id AND u.status IN ('ocupado', 'manutencao') " +
                        "LEFT JOIN (" +
                        "  SELECT uso_armario_id, " +
                        "    COUNT(*) as qtd_itens, " +
                        "    SUM(total + COALESCE(adicionais_total, 0)) as total_val " +
                        "  FROM itens_armario_sauna " +
                        "  GROUP BY uso_armario_id" +
                        ") itens_agg ON u.id = itens_agg.uso_armario_id " +
                        "WHERE a.ativo = 1 " +
                        "ORDER BY a.numero ASC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> armariosList = new ArrayList<>();

                int livres = 0, ocupados = 0, manutencao = 0;

                while (rs.next()) {
                    Map<String, Object> armario = new LinkedHashMap<>();
                    int armarioId = rs.getInt("id");
                    armario.put("id", armarioId);
                    armario.put("numero", rs.getInt("numero"));
                    armario.put("descricao", rs.getString("descricao"));
                    armario.put("localizacao", rs.getString("localizacao"));
                    armario.put("uso_id", rs.getInt("uso_id"));
                    armario.put("cliente_nome", rs.getString("cliente_nome"));
                    armario.put("observacao", rs.getString("observacao"));

                    String status = rs.getString("status");
                    if (status == null || status.isEmpty()) status = "livre";
                    armario.put("status", status);

                    // Data de entrada e tempo de uso
                    Timestamp dataEntrada = null;
                    try {
                        dataEntrada = rs.getTimestamp("data_entrada");
                    } catch (Exception ignored) {}

                    // v7.0.2 - Ler data_saida
                    Timestamp dataSaida = null;
                    try {
                        dataSaida = rs.getTimestamp("data_saida");
                    } catch (Exception ignored) {}

                    if (dataEntrada != null && "ocupado".equals(status)) {
                        armario.put("hora_entrada", FormatUtils.formatTime(dataEntrada));
                        armario.put("data_entrada_ts", dataEntrada.getTime());

                        long diffMs = System.currentTimeMillis() - dataEntrada.getTime();
                        long horas = TimeUnit.MILLISECONDS.toHours(diffMs);
                        long minutos = TimeUnit.MILLISECONDS.toMinutes(diffMs) % 60;
                        armario.put("tempo_uso", String.format("%02dh%02dmin", horas, minutos));
                    } else {
                        armario.put("hora_entrada", "");
                        armario.put("tempo_uso", "");
                    }

                    // v7.0.2 - Hora de saida (fechamento da conta)
                    if (dataSaida != null) {
                        armario.put("hora_saida", FormatUtils.formatTime(dataSaida));
                        armario.put("data_saida_ts", dataSaida.getTime());
                    } else {
                        armario.put("hora_saida", "");
                    }

                    int usoId = rs.getInt("uso_id");
                    if (usoId > 0 && "ocupado".equals(status)) {
                        armario.put("qtd_itens", rs.getInt("qtd_itens"));
                        armario.put("total", rs.getDouble("total_val"));
                    } else {
                        armario.put("qtd_itens", 0);
                        armario.put("total", 0.0);
                    }

                    switch (status) {
                        case "ocupado": ocupados++; break;
                        case "manutencao": manutencao++; break;
                        default: livres++; break;
                    }

                    armariosList.add(armario);
                }
                rs.close();
                stmt.close();

                final int fLivres = livres, fOcupados = ocupados, fManutencao = manutencao;
                final List<Map<String, Object>> finalList = armariosList;

                // v7.0.0 - Atualizar cache local
                cacheArmarios = new ArrayList<>(finalList);
                cacheCountLivres = fLivres;
                cacheCountOcupados = fOcupados;
                cacheCountManutencao = fManutencao;

                // v7.0.0 - Atualizar flag de armarios ocupados para timer inteligente
                temArmariosOcupados = ocupados > 0;

                // v7.0.0 - Reset contador de erros apos sucesso
                errosConsecutivos = 0;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (progressBarArmarios != null) {
                        progressBarArmarios.setVisibility(View.GONE);
                    }

                    armarioAdapter.setArmarios(finalList);
                    tvResumoArmarios.setText(String.format("Livres: %d | Ocupados: %d | Manutencao: %d | Total: %d",
                            fLivres, fOcupados, fManutencao, finalList.size()));
                    // v7.0.3 - Atualizar indicador grande de armarios livres
                    if (tvQtdLivres != null) {
                        tvQtdLivres.setText(String.valueOf(fLivres));
                    }

                    if (finalList.isEmpty()) {
                        if (tvSemArmarios != null) {
                            tvSemArmarios.setVisibility(View.VISIBLE);
                            tvSemArmarios.setText("Nenhum armario cadastrado.\n\nCadastre armarios no menu:\nCadastros > Armarios Sauna");
                        }
                        recyclerArmarios.setVisibility(View.GONE);
                    } else {
                        if (tvSemArmarios != null) {
                            tvSemArmarios.setVisibility(View.GONE);
                        }
                        recyclerArmarios.setVisibility(View.VISIBLE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar armarios: " + e.getMessage(), e);
                errosConsecutivos++;

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (progressBarArmarios != null) {
                        progressBarArmarios.setVisibility(View.GONE);
                    }

                    // v7.0.0 - Se tem cache, mostra cache com aviso; senao mostra erro
                    if (cacheArmarios != null && !cacheArmarios.isEmpty()) {
                        // Manter cache visivel, apenas mostrar toast sutil
                        if (errosConsecutivos <= 1) {
                            showToast("Atualizando armarios...");
                        }
                    } else {
                        if (tvSemArmarios != null) {
                            tvSemArmarios.setVisibility(View.VISIBLE);
                            tvSemArmarios.setText("Erro ao carregar armarios.\nVerifique a conexao com o banco de dados.\n\nToque em Voltar e tente novamente.");
                        }
                    }
                });

                // v7.0.0 - Retry automatico se poucos erros consecutivos
                if (errosConsecutivos < MAX_ERROS_CONSECUTIVOS) {
                    // Agendar retry em 3 segundos
                    timerHandler.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            carregarArmarios();
                        }
                    }, 3000);
                } else {
                    showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
                }
            } finally {
                carregamentoEmAndamento.set(false);
            }
        });
    }

    /**
     * Abre o dialog de detalhe do armario para gerenciar chave, consumo, etc.
     */
    private void abrirDetalheArmario(Map<String, Object> armario) {
        // v7.0.0 - Carregar produtos de forma lazy quando o dialog for aberto
        if (!produtosCarregados) {
            carregarDadosAuxiliares();
        }

        final int armarioId = ((Number) armario.get("id")).intValue();
        final int armarioNumero = ((Number) armario.get("numero")).intValue();
        final int usoId = ((Number) armario.get("uso_id")).intValue();
        final String statusAtual = armario.get("status") != null ? armario.get("status").toString() : "livre";
        final String clienteNome = armario.get("cliente_nome") != null ? armario.get("cliente_nome").toString() : "";
        final String observacao = armario.get("observacao") != null ? armario.get("observacao").toString() : "";

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_armario_detalhe, null);

        // Status info
        TextView tvStatusArmario = dialogView.findViewById(R.id.tvStatusArmario);
        TextView tvInfoCliente = dialogView.findViewById(R.id.tvInfoCliente);
        TextView tvInfoHorario = dialogView.findViewById(R.id.tvInfoHorario);
        // v7.0.2 - Hora de saida (fechamento da conta)
        TextView tvInfoHoraSaida = dialogView.findViewById(R.id.tvInfoHoraSaida);
        TextView tvInfoTempo = dialogView.findViewById(R.id.tvInfoTempo);

        // Campos de entrada
        EditText etNomeCliente = dialogView.findViewById(R.id.etNomeCliente);
        EditText etObservacao = dialogView.findViewById(R.id.etObservacao);
        TextView tvLabelCliente = dialogView.findViewById(R.id.tvLabelCliente);
        TextView tvLabelObs = dialogView.findViewById(R.id.tvLabelObs);

        // Produto/consumo
        TextView tvLabelProduto = dialogView.findViewById(R.id.tvLabelProduto);
        Spinner spCategoria = dialogView.findViewById(R.id.spCategoria);
        Spinner spProduto = dialogView.findViewById(R.id.spProduto);
        LinearLayout llProdutoArmarioScanner = dialogView.findViewById(R.id.llProdutoArmarioScanner);
        ImageButton btnScanProdutoArmario = dialogView.findViewById(R.id.btnScanProdutoArmario);
        LinearLayout llAddProduto = dialogView.findViewById(R.id.llAddProduto);
        EditText etQtdProduto = dialogView.findViewById(R.id.etQtdProduto);
        Button btnAddProduto = dialogView.findViewById(R.id.btnAddProduto);
        TextView tvLabelItens = dialogView.findViewById(R.id.tvLabelItens);
        RecyclerView recyclerItens = dialogView.findViewById(R.id.recyclerItensArmario);
        TextView tvTotalArmario = dialogView.findViewById(R.id.tvTotalArmario);

        // v6.9.7 - Botoes de Impressao
        LinearLayout llBotoesImpressaoArmario = dialogView.findViewById(R.id.llBotoesImpressaoArmario);
        Button btnImprimirPedidoCompletoArmario = dialogView.findViewById(R.id.btnImprimirPedidoCompletoArmario);
        Button btnImprimirUltimoItemArmario = dialogView.findViewById(R.id.btnImprimirUltimoItemArmario);

        // Botoes
        Button btnEntregarChave = dialogView.findViewById(R.id.btnEntregarChave);
        Button btnEncaminharPagamento = dialogView.findViewById(R.id.btnEncaminharPagamento);
        Button btnDevolverChave = dialogView.findViewById(R.id.btnDevolverChave);
        Button btnManutencao = dialogView.findViewById(R.id.btnManutencao);
        Button btnLiberarManutencao = dialogView.findViewById(R.id.btnLiberarManutencao);
        Button btnSalvarArmario = dialogView.findViewById(R.id.btnSalvarArmario);

        // Lista de itens do armario
        final List<Map<String, Object>> itensArmario = new ArrayList<>();
        recyclerItens.setLayoutManager(new LinearLayoutManager(this));
        recyclerItens.setHasFixedSize(false);
        recyclerItens.setItemViewCacheSize(10);

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

            TextView tvAdicionais = holder.find(R.id.tvAdicionaisInfo);
            String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
            if (tvAdicionais != null) {
                if (!adDesc.isEmpty()) {
                    tvAdicionais.setVisibility(View.VISIBLE);
                    tvAdicionais.setText("  + " + adDesc);
                } else {
                    tvAdicionais.setVisibility(View.GONE);
                }
            }

            Button btnRemover = holder.find(R.id.btnRemoverProduto);
            if (btnRemover != null) {
                btnRemover.setVisibility(View.VISIBLE);
                btnRemover.setOnClickListener(v -> {
                    int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                    if (itemId > 0) {
                        removerItemArmario(itemId, itensArmario, adapterHolder[0], dialogView);
                    } else {
                        itensArmario.remove(pos);
                        adapterHolder[0].setItems(new ArrayList<>(itensArmario));
                        atualizarTotalDialog(dialogView, itensArmario);
                    }
                });
            }
        });
        final GenericAdapter<Map<String, Object>> itensAdapter = adapterHolder[0];
        recyclerItens.setAdapter(itensAdapter);

        // Lista de produtos filtrados pela categoria selecionada
        final List<Map<String, Object>> produtosFiltrados = new ArrayList<>();

        // Configurar Spinner de Categorias
        List<String> categoriaNomes = new ArrayList<>();
        categoriaNomes.add("-- Selecione a Categoria --");
        for (Map<String, Object> cat : categoriasList) {
            categoriaNomes.add(cat.get("descricao").toString());
        }
        // Adicionar opcao "Sem Categoria" para produtos sem tipo
        categoriaNomes.add("Sem Categoria");
        ArrayAdapter<String> categoriaAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categoriaNomes);
        categoriaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoria.setAdapter(categoriaAdapter);

        // Spinner de Produto - inicialmente vazio (sera preenchido ao selecionar categoria)
        List<String> produtoNomesInicial = new ArrayList<>();
        produtoNomesInicial.add("-- Selecione a Categoria primeiro --");
        ArrayAdapter<String> produtoAdapterInicial = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, produtoNomesInicial);
        produtoAdapterInicial.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spProduto.setAdapter(produtoAdapterInicial);

        // Listener do Spinner de Categorias - filtra produtos ao selecionar categoria
        spCategoria.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                produtosFiltrados.clear();
                List<String> produtoNomes = new ArrayList<>();

                if (position == 0) {
                    // Nenhuma categoria selecionada
                    produtoNomes.add("-- Selecione a Categoria primeiro --");
                } else if (position <= categoriasList.size()) {
                    // Categoria especifica selecionada
                    int categoriaId = ((Number) categoriasList.get(position - 1).get("id")).intValue();
                    produtoNomes.add("-- Selecione Produto --");
                    for (Map<String, Object> p : produtosList) {
                        int tipoProdId = 0;
                        try {
                            tipoProdId = ((Number) p.get("tipo_produto_id")).intValue();
                        } catch (Exception ignored) {}
                        if (tipoProdId == categoriaId) {
                            produtosFiltrados.add(p);
                            produtoNomes.add(p.get("descricao").toString() + " - R$ " + String.format("%.2f", ((Number) p.get("preco_venda")).doubleValue()));
                        }
                    }
                    if (produtosFiltrados.isEmpty()) {
                        produtoNomes.clear();
                        produtoNomes.add("-- Nenhum produto nesta categoria --");
                    }
                } else {
                    // "Sem Categoria" selecionada - produtos sem tipo
                    produtoNomes.add("-- Selecione Produto --");
                    for (Map<String, Object> p : produtosList) {
                        int tipoProdId = 0;
                        try {
                            tipoProdId = ((Number) p.get("tipo_produto_id")).intValue();
                        } catch (Exception ignored) {}
                        if (tipoProdId == 0) {
                            produtosFiltrados.add(p);
                            produtoNomes.add(p.get("descricao").toString() + " - R$ " + String.format("%.2f", ((Number) p.get("preco_venda")).doubleValue()));
                        }
                    }
                    if (produtosFiltrados.isEmpty()) {
                        produtoNomes.clear();
                        produtoNomes.add("-- Nenhum produto sem categoria --");
                    }
                }

                ArrayAdapter<String> produtoAdapter = new ArrayAdapter<>(GerenciarArmariosSaunaActivity.this,
                        android.R.layout.simple_spinner_item, produtoNomes);
                produtoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spProduto.setAdapter(produtoAdapter);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Nao faz nada
            }
        });

        // Configurar visibilidade baseada no status
        switch (statusAtual) {
            case "livre":
                tvStatusArmario.setText("LIVRE");
                tvStatusArmario.setTextColor(0xFF4CAF50);
                tvLabelCliente.setVisibility(View.VISIBLE);
                etNomeCliente.setVisibility(View.VISIBLE);
                tvLabelObs.setVisibility(View.VISIBLE);
                etObservacao.setVisibility(View.VISIBLE);
                btnEntregarChave.setVisibility(View.VISIBLE);
                btnManutencao.setVisibility(View.VISIBLE);
                tvLabelProduto.setVisibility(View.GONE);
                spCategoria.setVisibility(View.GONE);
                spProduto.setVisibility(View.GONE); if (llProdutoArmarioScanner != null) llProdutoArmarioScanner.setVisibility(View.GONE);
                llAddProduto.setVisibility(View.GONE);
                tvLabelItens.setVisibility(View.GONE);
                recyclerItens.setVisibility(View.GONE);
                tvTotalArmario.setVisibility(View.GONE);
                btnEncaminharPagamento.setVisibility(View.GONE);
                btnDevolverChave.setVisibility(View.GONE);
                btnLiberarManutencao.setVisibility(View.GONE);
                btnSalvarArmario.setVisibility(View.GONE);
                llBotoesImpressaoArmario.setVisibility(View.GONE);
                break;

            case "ocupado":
                tvStatusArmario.setText("OCUPADO");
                tvStatusArmario.setTextColor(0xFFFF5722);
                tvInfoCliente.setText("Cliente: " + clienteNome);
                tvInfoCliente.setVisibility(View.VISIBLE);

                String horaEntrada = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
                if (!horaEntrada.isEmpty()) {
                    tvInfoHorario.setText("Entrada: " + horaEntrada);
                    tvInfoHorario.setVisibility(View.VISIBLE);
                }

                // v7.0.2 - Exibir hora de saida se existir
                String horaSaida = armario.get("hora_saida") != null ? armario.get("hora_saida").toString() : "";
                if (!horaSaida.isEmpty()) {
                    tvInfoHoraSaida.setText("Saida: " + horaSaida);
                    tvInfoHoraSaida.setVisibility(View.VISIBLE);
                }

                String tempoUso = armario.get("tempo_uso") != null ? armario.get("tempo_uso").toString() : "";
                if (!tempoUso.isEmpty()) {
                    tvInfoTempo.setText("Tempo: " + tempoUso);
                    tvInfoTempo.setVisibility(View.VISIBLE);
                }

                tvLabelCliente.setVisibility(View.GONE);
                etNomeCliente.setVisibility(View.GONE);
                tvLabelObs.setVisibility(View.GONE);
                etObservacao.setVisibility(View.GONE);
                btnEntregarChave.setVisibility(View.GONE);
                btnManutencao.setVisibility(View.GONE);
                btnLiberarManutencao.setVisibility(View.GONE);

                tvLabelProduto.setVisibility(View.VISIBLE);
                spCategoria.setVisibility(View.VISIBLE);
                spProduto.setVisibility(View.VISIBLE); if (llProdutoArmarioScanner != null) llProdutoArmarioScanner.setVisibility(View.VISIBLE);
                llAddProduto.setVisibility(View.VISIBLE);
                tvLabelItens.setVisibility(View.VISIBLE);
                recyclerItens.setVisibility(View.VISIBLE);
                tvTotalArmario.setVisibility(View.VISIBLE);
                btnEncaminharPagamento.setVisibility(View.VISIBLE);
                btnDevolverChave.setVisibility(View.VISIBLE);
                btnSalvarArmario.setVisibility(View.VISIBLE);

                if (usoId > 0) {
                    llBotoesImpressaoArmario.setVisibility(View.VISIBLE);
                } else {
                    llBotoesImpressaoArmario.setVisibility(View.GONE);
                }

                // Carregar itens existentes
                if (usoId > 0) {
                    carregarItensArmario(usoId, itensArmario, itensAdapter, dialogView);
                }
                break;

            case "manutencao":
                tvStatusArmario.setText("EM MANUTENCAO");
                tvStatusArmario.setTextColor(0xFFCE93D8);
                tvLabelCliente.setVisibility(View.GONE);
                etNomeCliente.setVisibility(View.GONE);
                tvLabelObs.setVisibility(View.GONE);
                etObservacao.setVisibility(View.GONE);
                tvLabelProduto.setVisibility(View.GONE);
                spCategoria.setVisibility(View.GONE);
                spProduto.setVisibility(View.GONE); if (llProdutoArmarioScanner != null) llProdutoArmarioScanner.setVisibility(View.GONE);
                llAddProduto.setVisibility(View.GONE);
                tvLabelItens.setVisibility(View.GONE);
                recyclerItens.setVisibility(View.GONE);
                tvTotalArmario.setVisibility(View.GONE);
                btnEntregarChave.setVisibility(View.GONE);
                btnEncaminharPagamento.setVisibility(View.GONE);
                btnDevolverChave.setVisibility(View.GONE);
                btnManutencao.setVisibility(View.GONE);
                btnSalvarArmario.setVisibility(View.GONE);
                llBotoesImpressaoArmario.setVisibility(View.GONE);
                btnLiberarManutencao.setVisibility(View.VISIBLE);
                break;
        }

        // Criar o dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Armario " + armarioNumero)
                .setView(dialogView)
                .setNegativeButton("Fechar", null)
                .create();

        // ===== Botao ENTREGAR CHAVE =====
        btnEntregarChave.setOnClickListener(v -> {
            String nome = etNomeCliente.getText().toString().trim();
            if (nome.isEmpty()) {
                showError("Por favor, informe o nome do cliente para entregar a chave.");
                return;
            }
            String obs = etObservacao.getText().toString().trim();
            entregarChave(armarioId, nome, obs, dialog);
        });

        // ===== Botao LEITOR CODIGO DE BARRAS DO PRODUTO =====
        if (btnScanProdutoArmario != null) {
            btnScanProdutoArmario.setOnClickListener(v -> {
                currentArmarioItens = itensArmario;
                currentArmarioAdapter = itensAdapter;
                currentArmarioDialogView = dialogView;
                currentArmarioSpProduto = spProduto;
                currentArmarioEtQtdProduto = etQtdProduto;
                currentArmarioUsoId = usoId;
                startActivityForResult(new Intent(this, BarcodeScannerActivity.class), REQUEST_SCAN_ARMARIO_PRODUTO);
            });
        }

        // ===== Botao ADICIONAR PRODUTO =====
        btnAddProduto.setOnClickListener(v -> {
            int selectedCatPos = spCategoria.getSelectedItemPosition();
            if (selectedCatPos <= 0) {
                showToast("Selecione uma categoria primeiro");
                return;
            }
            int selectedPos = spProduto.getSelectedItemPosition();
            if (selectedPos <= 0 || produtosFiltrados.isEmpty()) {
                showToast("Selecione um produto");
                return;
            }
            if (selectedPos - 1 >= produtosFiltrados.size()) {
                showToast("Produto invalido. Selecione novamente.");
                return;
            }
            Map<String, Object> produtoSelecionado = produtosFiltrados.get(selectedPos - 1);
            String qtdStr = etQtdProduto.getText().toString().trim();
            double qtd = 1;
            try {
                if (!qtdStr.isEmpty()) qtd = Double.parseDouble(qtdStr);
            } catch (NumberFormatException e) {
                qtd = 1;
            }
            if (qtd <= 0) qtd = 1;

            final double finalQtd = qtd;
            int tipoProdutoId = 0;
            try {
                tipoProdutoId = ((Number) produtoSelecionado.get("tipo_produto_id")).intValue();
            } catch (Exception ignored) {}

            if (tipoProdutoId > 0) {
                // v7.0.3 - AUTOSAVE: passa usoId para salvar automaticamente
                verificarAdicionaisEAdicionar(produtoSelecionado, finalQtd, itensArmario, itensAdapter, dialogView, spProduto, etQtdProduto, usoId);
            } else {
                // v7.0.3 - AUTOSAVE: passa usoId para salvar automaticamente
                adicionarProdutoNoArmario(produtoSelecionado, finalQtd, new ArrayList<>(), itensArmario, itensAdapter, dialogView, usoId);
                spProduto.setSelection(0);
                etQtdProduto.setText("1");
            }
        });

        // ===== Botao ENCAMINHAR PARA PAGAMENTO =====
        btnEncaminharPagamento.setOnClickListener(v -> {
            if (itensArmario.isEmpty()) {
                showToast("O armario nao possui itens para pagamento");
                return;
            }

            boolean temItensNaoSalvos = false;
            for (Map<String, Object> item : itensArmario) {
                int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                if (itemId == 0) {
                    temItensNaoSalvos = true;
                    break;
                }
            }

            if (temItensNaoSalvos) {
                showError("Existem itens nao salvos no armario.\n\nSalve antes de encaminhar para pagamento.");
                return;
            }

            // v7.0.2 - Incluir horarios na confirmacao
            String horaEntradaConf = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
            String msgConfirmacao = "Deseja encaminhar o Armario " + armarioNumero + " para pagamento?\n\n"
                    + "Cliente: " + clienteNome + "\n";
            if (!horaEntradaConf.isEmpty()) {
                msgConfirmacao += "Entrada: " + horaEntradaConf + "\n";
            }
            msgConfirmacao += "Total: R$ " + String.format("%.2f", calcularTotalItens(itensArmario));

            showConfirm("Encaminhar para Pagamento", msgConfirmacao,
                    () -> {
                        showLoading("Verificando caixa...");
                        dbExecutor.execute(() -> {
                            boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
                            hideLoading();
                            runOnUiThread(() -> {
                                if (!caixaAberto) {
                                    PermissionHelper.mostrarCaixaFechado(this);
                                    return;
                                }
                                armarioIdPagamento = armarioId;
                                usoArmarioIdPagamento = usoId;
                                // v7.0.2 - Guardar hora de entrada para o pagamento
                                horaEntradaPagamento = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
                                enviarArmarioParaPagamento(armarioNumero, clienteNome, itensArmario);
                                dialog.dismiss();
                            });
                        });
                    });
        });

        // ===== Botao DEVOLVER CHAVE / LIBERAR =====
        btnDevolverChave.setOnClickListener(v -> {
            // v7.0.2 - Incluir hora de entrada na confirmacao
            String horaEntradaDev = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
            String msgDevolucao = "Deseja devolver a chave e liberar o Armario " + armarioNumero + "?\n\n"
                    + "Cliente: " + clienteNome + "\n";
            if (!horaEntradaDev.isEmpty()) {
                msgDevolucao += "Entrada: " + horaEntradaDev + "\n";
            }
            msgDevolucao += "\nATENCAO: Se houver consumo nao pago, ele sera perdido!";
            showConfirm("Devolver Chave", msgDevolucao,
                    () -> devolverChave(armarioId, usoId, dialog));
        });

        // ===== Botao MANUTENCAO =====
        btnManutencao.setOnClickListener(v -> {
            showConfirm("Manutencao",
                    "Deseja colocar o Armario " + armarioNumero + " em manutencao?\n\n"
                            + "O armario ficara indisponivel para uso.",
                    () -> colocarEmManutencao(armarioId, dialog));
        });

        // ===== Botao LIBERAR MANUTENCAO =====
        btnLiberarManutencao.setOnClickListener(v -> {
            showConfirm("Liberar Manutencao",
                    "Deseja liberar o Armario " + armarioNumero + " da manutencao?\n\n"
                            + "O armario ficara disponivel para uso novamente.",
                    () -> liberarManutencao(armarioId, usoId, dialog));
        });

        // v7.0.3 - AUTOSAVE: Botao Salvar oculto pois itens sao salvos automaticamente
        btnSalvarArmario.setVisibility(View.GONE);
        btnSalvarArmario.setOnClickListener(v -> {
            salvarItensArmario(usoId, itensArmario, dialog);
        });

        // v6.9.7 - Configurar botao Imprimir Pedido Completo do Armario
        btnImprimirPedidoCompletoArmario.setOnClickListener(v -> {
            if (itensArmario.isEmpty()) {
                showToast("O armario nao possui itens para imprimir");
                return;
            }

            boolean temItensNaoSalvos = false;
            for (Map<String, Object> item : itensArmario) {
                int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                if (itemId == 0) {
                    temItensNaoSalvos = true;
                    break;
                }
            }

            if (temItensNaoSalvos) {
                showError("Existem itens nao salvos no armario.\n\nSalve antes de imprimir.");
                return;
            }

            // v7.0.2 - Passar hora de entrada para o cupom
            String horaEntradaImp = armario.get("hora_entrada") != null ? armario.get("hora_entrada").toString() : "";
            imprimirPedidoCompletoArmario(armarioNumero, itensArmario, horaEntradaImp);
        });

        // v6.9.7 - Configurar botao Imprimir Ultimo Item do Armario
        btnImprimirUltimoItemArmario.setOnClickListener(v -> {
            if (itensArmario.isEmpty()) {
                showToast("O armario nao possui itens para imprimir");
                return;
            }

            boolean temItensNaoSalvos = false;
            for (Map<String, Object> item : itensArmario) {
                int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                if (itemId == 0) {
                    temItensNaoSalvos = true;
                    break;
                }
            }

            if (temItensNaoSalvos) {
                showError("Existem itens nao salvos no armario.\n\nSalve antes de imprimir.");
                return;
            }

            imprimirUltimoItemArmario(armarioNumero, usoId, itensArmario, btnImprimirUltimoItemArmario);
        });

        dialog.show();
    }

    // ==================== OPERACOES DE CHAVE ====================

    /**
     * Entrega a chave do armario para um cliente.
     * v7.0.0 - Usa dbExecutor com tratamento robusto de erros.
     */
    private void entregarChave(int armarioId, String clienteNome, String observacao, AlertDialog dialog) {
        showLoading("Entregando chave...");
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                ps = conn.prepareStatement(
                        "INSERT INTO uso_armario_sauna (armario_id, cliente_nome, observacao, status, data_entrada) VALUES (?,?,?,'ocupado',NOW())");
                ps.setInt(1, armarioId);
                ps.setString(2, clienteNome);
                ps.setString(3, observacao.isEmpty() ? null : observacao);
                ps.executeUpdate();
                ps.close();
                ps = null;

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Chave entregue para " + clienteNome + "!");
                    dialog.dismiss();
                    carregarArmarios();
                });

            } catch (Exception e) {
                if (ps != null) { try { ps.close(); } catch (Exception ignored) {} }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        });
    }

    /**
     * Devolve a chave e libera o armario.
     * v7.0.0 - Usa dbExecutor com tratamento robusto.
     */
    private void devolverChave(int armarioId, int usoId, AlertDialog dialog) {
        showLoading("Devolvendo chave...");
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (usoId > 0) {
                    // Remover adicionais dos itens
                    try {
                        PreparedStatement psAd = conn.prepareStatement(
                                "DELETE FROM itens_armario_sauna_adicionais WHERE item_armario_id IN (SELECT id FROM itens_armario_sauna WHERE uso_armario_id = ?)");
                        psAd.setInt(1, usoId);
                        psAd.executeUpdate();
                        psAd.close();
                    } catch (Exception ignored) {}

                    PreparedStatement psItens = conn.prepareStatement("DELETE FROM itens_armario_sauna WHERE uso_armario_id = ?");
                    psItens.setInt(1, usoId);
                    psItens.executeUpdate();
                    psItens.close();

                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE uso_armario_sauna SET status = 'encerrado', data_saida = NOW() WHERE id = ?");
                    ps.setInt(1, usoId);
                    ps.executeUpdate();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Chave devolvida! Armario liberado.");
                    dialog.dismiss();
                    carregarArmarios();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        });
    }

    // ==================== OPERACOES DE MANUTENCAO ====================

    /**
     * Coloca o armario em manutencao.
     * v7.0.0 - Usa dbExecutor.
     */
    private void colocarEmManutencao(int armarioId, AlertDialog dialog) {
        showLoading("Colocando em manutencao...");
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                ps = conn.prepareStatement(
                        "INSERT INTO uso_armario_sauna (armario_id, cliente_nome, observacao, status, data_entrada) VALUES (?,?,?,'manutencao',NOW())");
                ps.setInt(1, armarioId);
                ps.setString(2, "MANUTENCAO");
                ps.setString(3, "Armario em manutencao");
                ps.executeUpdate();
                ps.close();
                ps = null;

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Armario colocado em manutencao!");
                    dialog.dismiss();
                    carregarArmarios();
                });

            } catch (Exception e) {
                if (ps != null) { try { ps.close(); } catch (Exception ignored) {} }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        });
    }

    /**
     * Libera o armario da manutencao.
     * v7.0.0 - Usa dbExecutor.
     */
    private void liberarManutencao(int armarioId, int usoId, AlertDialog dialog) {
        showLoading("Liberando manutencao...");
        dbExecutor.execute(() -> {
            PreparedStatement ps = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (usoId > 0) {
                    ps = conn.prepareStatement(
                            "UPDATE uso_armario_sauna SET status = 'encerrado', data_saida = NOW() WHERE id = ?");
                    ps.setInt(1, usoId);
                    ps.executeUpdate();
                    ps.close();
                    ps = null;
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Armario liberado da manutencao!");
                    dialog.dismiss();
                    carregarArmarios();
                });

            } catch (Exception e) {
                if (ps != null) { try { ps.close(); } catch (Exception ignored) {} }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        });
    }

    // ==================== OPERACOES DE CONSUMO ====================

    /**
     * Adiciona um produto na lista de itens do armario.
     * v7.0.3 - AUTOSAVE: Sobrecarga sem usoId para compatibilidade.
     */
    private void adicionarProdutoNoArmario(Map<String, Object> produtoSelecionado, double quantidade,
                                            List<Map<String, Object>> adicionaisSelecionados,
                                            List<Map<String, Object>> itensArmario,
                                            GenericAdapter<Map<String, Object>> itensAdapter,
                                            View dialogView) {
        adicionarProdutoNoArmario(produtoSelecionado, quantidade, adicionaisSelecionados,
                itensArmario, itensAdapter, dialogView, 0);
    }

    /**
     * v7.0.3 - AUTOSAVE: Adiciona produto no armario e salva automaticamente no banco.
     * Se usoId > 0, persiste o item imediatamente no banco de dados.
     */
    private void adicionarProdutoNoArmario(Map<String, Object> produtoSelecionado, double quantidade,
                                            List<Map<String, Object>> adicionaisSelecionados,
                                            List<Map<String, Object>> itensArmario,
                                            GenericAdapter<Map<String, Object>> itensAdapter,
                                            View dialogView, int usoId) {
        Map<String, Object> novoItem = new LinkedHashMap<>();
        novoItem.put("id", 0);
        novoItem.put("produto_id", ((Number) produtoSelecionado.get("id")).intValue());
        novoItem.put("descricao_produto", produtoSelecionado.get("descricao").toString());
        novoItem.put("quantidade", quantidade);
        double preco = ((Number) produtoSelecionado.get("preco_venda")).doubleValue();
        novoItem.put("preco_unitario", preco);
        novoItem.put("total", quantidade * preco);

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

        itensArmario.add(novoItem);
        itensAdapter.setItems(new ArrayList<>(itensArmario));
        atualizarTotalDialog(dialogView, itensArmario);

        // v7.0.3 - AUTOSAVE: Salvar automaticamente no banco de dados
        if (usoId > 0) {
            autoSalvarItemArmario(usoId, novoItem, adicionaisSelecionados, itensArmario, itensAdapter);
        }
    }

    /**
     * v7.0.3 - Salva automaticamente um item do armario no banco de dados.
     * Insere o item e seus adicionais, e atualiza o ID na lista local.
     */
    private void autoSalvarItemArmario(int usoId, Map<String, Object> novoItem,
                                        List<Map<String, Object>> adicionaisSelecionados,
                                        List<Map<String, Object>> itensArmario,
                                        GenericAdapter<Map<String, Object>> itensAdapter) {
        dbExecutor.execute(() -> {
            PreparedStatement psItem = null;
            PreparedStatement psAd = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                String adDesc = novoItem.get("adicionais_descricao") != null ? novoItem.get("adicionais_descricao").toString() : "";
                double adTotal = novoItem.get("adicionais_total") != null ? ((Number) novoItem.get("adicionais_total")).doubleValue() : 0;

                psItem = conn.prepareStatement(
                        "INSERT INTO itens_armario_sauna (uso_armario_id, produto_id, descricao_produto, quantidade, preco_unitario, total, adicionais_descricao, adicionais_total) VALUES (?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                psItem.setInt(1, usoId);
                psItem.setInt(2, ((Number) novoItem.get("produto_id")).intValue());
                psItem.setString(3, novoItem.get("descricao_produto").toString());
                psItem.setDouble(4, ((Number) novoItem.get("quantidade")).doubleValue());
                psItem.setDouble(5, ((Number) novoItem.get("preco_unitario")).doubleValue());
                psItem.setDouble(6, ((Number) novoItem.get("total")).doubleValue());
                psItem.setString(7, adDesc);
                psItem.setDouble(8, adTotal);
                psItem.executeUpdate();

                ResultSet keysItem = psItem.getGeneratedKeys();
                int novoItemId = 0;
                if (keysItem.next()) {
                    novoItemId = keysItem.getInt(1);
                }
                keysItem.close();
                psItem.close();
                psItem = null;

                // Atualizar o ID na lista local
                if (novoItemId > 0) {
                    novoItem.put("id", novoItemId);
                }

                // Salvar adicionais individuais
                if (novoItemId > 0 && adicionaisSelecionados != null && !adicionaisSelecionados.isEmpty()) {
                    psAd = conn.prepareStatement(
                            "INSERT INTO itens_armario_sauna_adicionais (item_armario_id, adicional_id, descricao_adicional, preco) VALUES (?,?,?,?)");
                    for (Map<String, Object> ad : adicionaisSelecionados) {
                        psAd.setInt(1, novoItemId);
                        psAd.setInt(2, ((Number) ad.get("id")).intValue());
                        psAd.setString(3, ad.get("descricao") != null ? ad.get("descricao").toString() : "");
                        psAd.setDouble(4, ((Number) ad.get("preco")).doubleValue());
                        psAd.addBatch();
                    }
                    psAd.executeBatch();
                    psAd.close();
                    psAd = null;
                }

                runOnUiThread(() -> {
                    showToast(novoItem.get("descricao_produto") + " salvo automaticamente!");
                    itensAdapter.setItems(new ArrayList<>(itensArmario));
                });

            } catch (Exception e) {
                if (psItem != null) { try { psItem.close(); } catch (Exception ignored) {} }
                if (psAd != null) { try { psAd.close(); } catch (Exception ignored) {} }
                Log.e(TAG, "Erro ao auto-salvar item do armario: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao salvar item automaticamente: " + e.getMessage()));
            }
        });
    }

    /**
     * Verifica se o produto possui adicionais vinculados ao tipo.
     * v7.0.0 - Usa dbExecutor.
     * v7.0.3 - AUTOSAVE: Recebe usoId para salvar automaticamente.
     */
    private void verificarAdicionaisEAdicionar(Map<String, Object> produtoSelecionado, double quantidade,
                                                List<Map<String, Object>> itensArmario,
                                                GenericAdapter<Map<String, Object>> itensAdapter,
                                                View dialogView, Spinner spProduto, EditText etQtdProduto,
                                                int usoId) {
        int tipoProdutoId = 0;
        try {
            tipoProdutoId = ((Number) produtoSelecionado.get("tipo_produto_id")).intValue();
        } catch (Exception ignored) {}

        if (tipoProdutoId <= 0) {
            adicionarProdutoNoArmario(produtoSelecionado, quantidade, new ArrayList<>(), itensArmario, itensAdapter, dialogView, usoId);
            spProduto.setSelection(0);
            etQtdProduto.setText("1");
            return;
        }

        final int finalTipoProdutoId = tipoProdutoId;
        showLoading("Verificando adicionais...");
        dbExecutor.execute(() -> {
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
                        // v7.0.3 - AUTOSAVE
                        adicionarProdutoNoArmario(produtoSelecionado, quantidade, new ArrayList<>(), itensArmario, itensAdapter, dialogView, usoId);
                        spProduto.setSelection(0);
                        etQtdProduto.setText("1");
                    } else {
                        // v7.0.3 - AUTOSAVE: passa usoId
                        mostrarDialogoAdicionais(produtoSelecionado, quantidade, adicionaisDisponiveis,
                                itensArmario, itensAdapter, dialogView, spProduto, etQtdProduto, usoId);
                    }
                });
            } catch (Exception e) {
                hideLoading();
                // v7.0.3 - AUTOSAVE
                runOnUiThread(() -> {
                    adicionarProdutoNoArmario(produtoSelecionado, quantidade, new ArrayList<>(), itensArmario, itensAdapter, dialogView, usoId);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                });
            }
        });
    }

    /**
     * Exibe dialogo com checkboxes para escolher adicionais.
     * v7.0.3 - AUTOSAVE: Recebe usoId para salvar automaticamente.
     */
    private void mostrarDialogoAdicionais(Map<String, Object> produtoSelecionado, double quantidade,
                                           List<Map<String, Object>> adicionaisDisponiveis,
                                           List<Map<String, Object>> itensArmario,
                                           GenericAdapter<Map<String, Object>> itensAdapter,
                                           View armarioDialogView, Spinner spProduto, EditText etQtdProduto,
                                           int usoId) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        String descProduto = produtoSelecionado.get("descricao") != null ? produtoSelecionado.get("descricao").toString() : "Produto";
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Selecione os adicionais para:\n" + descProduto);
        tvInfo.setTextColor(0xFF00BCD4);
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, 0, 0, 16);
        container.addView(tvInfo);

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

        TextView tvTotalAd = new TextView(this);
        tvTotalAd.setText("Total adicionais: R$ 0,00");
        tvTotalAd.setTextColor(0xFFFFD700);
        tvTotalAd.setTextSize(13);
        tvTotalAd.setPadding(0, 16, 0, 0);
        container.addView(tvTotalAd);

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
                    adicionarProdutoNoArmario(produtoSelecionado, quantidade, selecionados, itensArmario, itensAdapter, armarioDialogView, usoId);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                })
                .setNegativeButton("Sem adicionais", (d, w) -> {
                    // v7.0.3 - AUTOSAVE
                    adicionarProdutoNoArmario(produtoSelecionado, quantidade, new ArrayList<>(), itensArmario, itensAdapter, armarioDialogView, usoId);
                    spProduto.setSelection(0);
                    etQtdProduto.setText("1");
                })
                .show();
    }

    /**
     * Carrega os itens de consumo de um uso de armario.
     * v7.0.0 - OTIMIZADO: Query UNICA com LEFT JOIN para adicionais (elimina N+1).
     *          Usa dbExecutor em vez de new Thread().
     */
    private void carregarItensArmario(int usoId, List<Map<String, Object>> itensArmario,
                                       GenericAdapter<Map<String, Object>> adapter, View dialogView) {
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v7.0.0 - Query UNICA que traz itens E adicionais com LEFT JOIN
                String sql = "SELECT i.id, i.produto_id, i.descricao_produto, i.quantidade, " +
                        "i.preco_unitario, i.total, i.adicionais_descricao, i.adicionais_total, " +
                        "a.adicional_id as ad_id, a.descricao_adicional as ad_desc, a.preco as ad_preco " +
                        "FROM itens_armario_sauna i " +
                        "LEFT JOIN itens_armario_sauna_adicionais a ON i.id = a.item_armario_id " +
                        "WHERE i.uso_armario_id = ? " +
                        "ORDER BY i.id ASC, a.id ASC";

                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, usoId);
                ResultSet rs = ps.executeQuery();

                itensArmario.clear();
                Map<Integer, Map<String, Object>> itensMap = new LinkedHashMap<>();

                while (rs.next()) {
                    int itemId = rs.getInt("id");

                    if (!itensMap.containsKey(itemId)) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", itemId);
                        item.put("produto_id", rs.getInt("produto_id"));
                        item.put("descricao_produto", rs.getString("descricao_produto"));
                        item.put("quantidade", rs.getDouble("quantidade"));
                        item.put("preco_unitario", rs.getDouble("preco_unitario"));
                        item.put("total", rs.getDouble("total"));

                        String adDesc = "";
                        double adTotal = 0;
                        try { adDesc = rs.getString("adicionais_descricao"); if (adDesc == null) adDesc = ""; } catch (Exception ignored) {}
                        try { adTotal = rs.getDouble("adicionais_total"); } catch (Exception ignored) {}
                        item.put("adicionais_descricao", adDesc);
                        item.put("adicionais_total", adTotal);
                        item.put("adicionais_lista", new ArrayList<Map<String, Object>>());

                        itensMap.put(itemId, item);
                    }

                    // Adicionar adicional se existir
                    int adId = rs.getInt("ad_id");
                    if (adId > 0) {
                        Map<String, Object> ad = new LinkedHashMap<>();
                        ad.put("id", adId);
                        ad.put("descricao", rs.getString("ad_desc"));
                        ad.put("preco", rs.getDouble("ad_preco"));
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> adicionaisItem = (List<Map<String, Object>>) itensMap.get(itemId).get("adicionais_lista");
                        adicionaisItem.add(ad);
                    }
                }
                rs.close();
                ps.close();

                itensArmario.addAll(itensMap.values());

                runOnUiThread(() -> {
                    adapter.setItems(new ArrayList<>(itensArmario));
                    atualizarTotalDialog(dialogView, itensArmario);
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar itens do armario: " + e.getMessage());
            }
        });
    }

    /**
     * Remove um item do armario do banco de dados.
     * v7.0.0 - Usa dbExecutor com tratamento robusto.
     */
    private void removerItemArmario(int itemId, List<Map<String, Object>> itensArmario,
                                     GenericAdapter<Map<String, Object>> adapter, View dialogView) {
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Remover adicionais do item primeiro
                try {
                    PreparedStatement psAd = conn.prepareStatement("DELETE FROM itens_armario_sauna_adicionais WHERE item_armario_id = ?");
                    psAd.setInt(1, itemId);
                    psAd.executeUpdate();
                    psAd.close();
                } catch (Exception ignored) {}

                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_armario_sauna WHERE id = ?");
                ps.setInt(1, itemId);
                ps.executeUpdate();
                ps.close();

                runOnUiThread(() -> {
                    for (int i = itensArmario.size() - 1; i >= 0; i--) {
                        if (itensArmario.get(i).get("id") != null &&
                                ((Number) itensArmario.get(i).get("id")).intValue() == itemId) {
                            itensArmario.remove(i);
                            break;
                        }
                    }
                    adapter.setItems(new ArrayList<>(itensArmario));
                    atualizarTotalDialog(dialogView, itensArmario);
                    showToast("Item removido");
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        });
    }

    /**
     * Salva os itens novos do armario no banco de dados.
     * v7.0.0 - OTIMIZADO: Usa batch insert com PreparedStatement reutilizado.
     *          Usa dbExecutor em vez de new Thread().
     */
    private void salvarItensArmario(int usoId, List<Map<String, Object>> itensArmario, AlertDialog dialog) {
        if (usoId <= 0) {
            showError("Erro: uso do armario nao encontrado.");
            return;
        }

        // v7.0.0 - Verificar se ha itens novos para salvar
        boolean temNovos = false;
        for (Map<String, Object> item : itensArmario) {
            int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
            if (itemId == 0) {
                temNovos = true;
                break;
            }
        }

        if (!temNovos) {
            showToast("Nenhum item novo para salvar.");
            dialog.dismiss();
            carregarArmarios();
            return;
        }

        showLoading("Salvando...");
        dbExecutor.execute(() -> {
            PreparedStatement psItem = null;
            PreparedStatement psAd = null;
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v7.0.0 - PreparedStatement reutilizado para todos os itens novos
                psItem = conn.prepareStatement(
                        "INSERT INTO itens_armario_sauna (uso_armario_id, produto_id, descricao_produto, quantidade, preco_unitario, total, adicionais_descricao, adicionais_total) VALUES (?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);

                // v7.0.0 - PreparedStatement reutilizado para adicionais
                psAd = conn.prepareStatement(
                        "INSERT INTO itens_armario_sauna_adicionais (item_armario_id, adicional_id, descricao_adicional, preco) VALUES (?,?,?,?)");

                for (Map<String, Object> item : itensArmario) {
                    int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                    if (itemId == 0) {
                        String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
                        double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0;

                        psItem.setInt(1, usoId);
                        psItem.setInt(2, ((Number) item.get("produto_id")).intValue());
                        psItem.setString(3, item.get("descricao_produto").toString());
                        psItem.setDouble(4, ((Number) item.get("quantidade")).doubleValue());
                        psItem.setDouble(5, ((Number) item.get("preco_unitario")).doubleValue());
                        psItem.setDouble(6, ((Number) item.get("total")).doubleValue());
                        psItem.setString(7, adDesc);
                        psItem.setDouble(8, adTotal);
                        psItem.executeUpdate();

                        ResultSet keysItem = psItem.getGeneratedKeys();
                        int novoItemId = 0;
                        if (keysItem.next()) {
                            novoItemId = keysItem.getInt(1);
                        }
                        keysItem.close();

                        if (novoItemId > 0) {
                            item.put("id", novoItemId);
                        }

                        // Salvar adicionais individuais com PreparedStatement reutilizado
                        if (novoItemId > 0 && item.get("adicionais_lista") != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> adicionaisLista = (List<Map<String, Object>>) item.get("adicionais_lista");
                            if (adicionaisLista != null && !adicionaisLista.isEmpty()) {
                                for (Map<String, Object> ad : adicionaisLista) {
                                    psAd.setInt(1, novoItemId);
                                    psAd.setInt(2, ((Number) ad.get("id")).intValue());
                                    psAd.setString(3, ad.get("descricao") != null ? ad.get("descricao").toString() : "");
                                    psAd.setDouble(4, ((Number) ad.get("preco")).doubleValue());
                                    psAd.addBatch();
                                }
                                psAd.executeBatch();
                            }
                        }
                    }
                }

                psItem.close();
                psItem = null;
                psAd.close();
                psAd = null;

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Armario salvo com sucesso!");
                    dialog.dismiss();
                    carregarArmarios();
                });

            } catch (Exception e) {
                if (psItem != null) { try { psItem.close(); } catch (Exception ignored) {} }
                if (psAd != null) { try { psAd.close(); } catch (Exception ignored) {} }
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        });
    }

    // ==================== PAGAMENTO ====================

    /**
     * Calcula o total dos itens do armario incluindo adicionais.
     */
    private double calcularTotalItens(List<Map<String, Object>> itensArmario) {
        double total = 0;
        for (Map<String, Object> item : itensArmario) {
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
     * Envia os itens do armario para a tela de pagamento.
     */
    private void enviarArmarioParaPagamento(int armarioNumero, String clienteNome, List<Map<String, Object>> itensArmario) {
        double totalArmario = calcularTotalItens(itensArmario);
        double taxaServico = TaxaServicoPreferences.cobrarArmarios(this)
                ? TaxaServicoPreferences.calcularTaxa(totalArmario) : 0.0;

        Intent intent = new Intent(this, PagamentoActivity.class);
        intent.putExtra("total_bruto", totalArmario);
        intent.putExtra("desconto", 0.0);
        intent.putExtra("desconto_tipo", "valor");
        intent.putExtra("desconto_input", 0.0);
        intent.putExtra("acrescimo", taxaServico);
        intent.putExtra("acrescimo_tipo", taxaServico > 0 ? "porcentagem" : "valor");
        intent.putExtra("acrescimo_input", taxaServico > 0 ? TaxaServicoPreferences.PERCENTUAL : 0.0);
        intent.putExtra("total_liquido", totalArmario + taxaServico);
        intent.putExtra("cliente_id", 0);
        intent.putExtra("cliente_nome", clienteNome != null && !clienteNome.isEmpty() ? clienteNome : "Cliente nao informado");
        intent.putExtra("vendedor_id", 0);
        intent.putExtra("entregador_id", 0);
        intent.putExtra("observacao", "Armario Sauna " + armarioNumero
                + (taxaServico > 0 ? " - Taxa de servico 10%" : ""));
        intent.putExtra("num_itens", itensArmario.size());

        intent.putExtra("armario_sauna_id", armarioIdPagamento);
        intent.putExtra("uso_armario_id", usoArmarioIdPagamento);
        intent.putExtra("armario_numero", armarioNumero);
        intent.putExtra("is_armario_sauna", true);
        // v7.0.2 - Enviar hora de entrada para exibicao no pagamento
        intent.putExtra("armario_hora_entrada", horaEntradaPagamento);

        for (int i = 0; i < itensArmario.size(); i++) {
            Map<String, Object> item = itensArmario.get(i);
            intent.putExtra("item_produto_id_" + i,
                    item.get("produto_id") != null ? ((Number) item.get("produto_id")).intValue() : 0);
            intent.putExtra("item_descricao_" + i,
                    item.get("descricao_produto") != null ? item.get("descricao_produto").toString() : "");
            intent.putExtra("item_qtd_" + i,
                    item.get("quantidade") != null ? ((Number) item.get("quantidade")).doubleValue() : 0.0);
            intent.putExtra("item_preco_" + i,
                    item.get("preco_unitario") != null ? ((Number) item.get("preco_unitario")).doubleValue() : 0.0);

            double totalItem = item.get("total") != null ? ((Number) item.get("total")).doubleValue() : 0.0;
            double adTotal = item.get("adicionais_total") != null ? ((Number) item.get("adicionais_total")).doubleValue() : 0.0;
            intent.putExtra("item_total_" + i, totalItem + adTotal);

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

        startActivityForResult(intent, REQUEST_PAGAMENTO_ARMARIO);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /**
     * Trata o resultado do pagamento do armario.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN_ARMARIO_PRODUTO && resultCode == RESULT_OK && data != null) {
            String barcode = data.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT);
            if (barcode != null && !barcode.trim().isEmpty()) {
                adicionarProdutoArmarioPorCodigo(barcode.trim());
            }
            return;
        }
        if (requestCode == REQUEST_PAGAMENTO_ARMARIO && resultCode == RESULT_OK) {
            encerrarArmarioAposPagamento();
        }
    }

    /**
     * Encerra o uso do armario e limpa os itens apos o pagamento ser concluido.
     * v7.0.0 - Usa dbExecutor.
     */
    private void encerrarArmarioAposPagamento() {
        if (usoArmarioIdPagamento <= 0) return;

        showLoading("Encerrando armario...");
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Remover adicionais dos itens
                try {
                    PreparedStatement psAd = conn.prepareStatement(
                            "DELETE FROM itens_armario_sauna_adicionais WHERE item_armario_id IN (SELECT id FROM itens_armario_sauna WHERE uso_armario_id = ?)");
                    psAd.setInt(1, usoArmarioIdPagamento);
                    psAd.executeUpdate();
                    psAd.close();
                } catch (Exception ignored) {}

                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_armario_sauna WHERE uso_armario_id = ?");
                ps.setInt(1, usoArmarioIdPagamento);
                ps.executeUpdate();
                ps.close();

                ps = conn.prepareStatement("UPDATE uso_armario_sauna SET status = 'encerrado', data_saida = NOW() WHERE id = ?");
                ps.setInt(1, usoArmarioIdPagamento);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Armario encerrado com sucesso! Chave devolvida.");
                    armarioIdPagamento = 0;
                    usoArmarioIdPagamento = 0;
                    horaEntradaPagamento = ""; // v7.0.2
                    carregarArmarios();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        });
    }

    // ==================== IMPRESSAO v6.9.7 ====================

    /**
     * v6.9.7 - Gera o cupom de pedido completo do armario para impressao.
     */
    private String gerarCupomPedidoArmario(int armarioNumero, List<Map<String, Object>> itensArmario) {
        return gerarCupomPedidoArmario(armarioNumero, itensArmario, "", "");
    }

    /**
     * v7.0.2 - Gera o cupom de pedido completo do armario com horarios de entrada e saida.
     */
    private String gerarCupomPedidoArmario(int armarioNumero, List<Map<String, Object>> itensArmario,
                                            String horaEntrada, String horaSaida) {
        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        StringBuilder sb = new StringBuilder();
        String line = repeat("-", colunas);

        sb.append(center("*** PEDIDO ARMARIO " + armarioNumero + " ***", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append("Data: " + FormatUtils.getCurrentDateTime()).append("\n");
        // v7.0.2 - Horarios de entrada e saida no cupom
        if (horaEntrada != null && !horaEntrada.isEmpty()) {
            sb.append("Entrada: " + horaEntrada).append("\n");
        }
        if (horaSaida != null && !horaSaida.isEmpty()) {
            sb.append("Saida: " + horaSaida).append("\n");
        }
        sb.append(line).append("\n");

        sb.append("ITEM  DESCRICAO          QTD    TOTAL\n");
        sb.append(line).append("\n");

        int itemNum = 1;
        double totalGeral = 0;
        for (Map<String, Object> item : itensArmario) {
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

            String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
            if (!adDesc.isEmpty()) {
                sb.append("      + ").append(adDesc).append("\n");
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
     * v6.9.7 - Gera o cupom de um unico item do armario para impressao.
     */
    private String gerarCupomItemArmario(int armarioNumero, Map<String, Object> item) {
        PrinterManager pm = new PrinterManager(this);
        int colunas = pm.getColunasTexto();
        StringBuilder sb = new StringBuilder();
        String line = repeat("-", colunas);

        sb.append(center("*** ULTIMO ITEM - ARMARIO " + armarioNumero + " ***", colunas)).append("\n");
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

        String adDesc = item.get("adicionais_descricao") != null ? item.get("adicionais_descricao").toString() : "";
        if (!adDesc.isEmpty()) {
            sb.append("Adicionais: " + adDesc).append("\n");
            if (adTotal > 0) {
                sb.append("Valor Adicionais: R$ " + FormatUtils.formatMoney(adTotal)).append("\n");
            }
        }

        sb.append(line).append("\n");
        sb.append(rightAlign("TOTAL ITEM: R$ " + FormatUtils.formatMoney(totalComAd), colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append(center("PDV Pro v8.0.0", colunas)).append("\n");
        sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

        return sb.toString();
    }

    /**
     * v6.9.7 - Imprime o pedido completo do armario.
     * v7.0.0 - Usa dbExecutor.
     */
    private void imprimirPedidoCompletoArmario(int armarioNumero, List<Map<String, Object>> itensArmario) {
        imprimirPedidoCompletoArmario(armarioNumero, itensArmario, "");
    }

    /**
     * v7.0.2 - Imprime o pedido completo do armario com hora de entrada.
     */
    private void imprimirPedidoCompletoArmario(int armarioNumero, List<Map<String, Object>> itensArmario, String horaEntrada) {
        showLoading("Imprimindo pedido completo...");
        dbExecutor.execute(() -> {
            try {
                String cupom = gerarCupomPedidoArmario(armarioNumero, itensArmario, horaEntrada, "");
                PrinterManager pm = new PrinterManager(this);
                String tipoImpressora = pm.getTipoImpressora();

                if (PrinterManager.TIPO_NENHUMA.equals(tipoImpressora)) {
                    hideLoading();
                    runOnUiThread(() -> showError("Nenhuma impressora configurada.\n\nConfigure uma impressora em Configuracoes > Impressora."));
                    return;
                }

                boolean sucesso = pm.imprimirTexto(cupom);
                hideLoading();

                runOnUiThread(() -> {
                    if (sucesso) {
                        showToast("Pedido completo do Armario " + armarioNumero + " impresso com sucesso!");
                    } else {
                        showError("Erro ao imprimir o pedido.\n\nVerifique se a impressora esta ligada e conectada.");
                    }
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao imprimir pedido completo armario: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao imprimir: " + e.getMessage()));
            }
        });
    }

    /**
     * v6.9.7 - Imprime o ultimo item adicionado ao pedido do armario.
     * v7.0.0 - Usa dbExecutor.
     */
    private void imprimirUltimoItemArmario(int armarioNumero, int usoId,
                                            List<Map<String, Object>> itensArmario,
                                            Button btnImprimirUltimoItem) {
        if (itensArmario.isEmpty()) {
            showToast("Nenhum item no armario para imprimir");
            return;
        }

        Map<String, Object> ultimoItem = itensArmario.get(itensArmario.size() - 1);
        int ultimoItemId = ultimoItem.get("id") != null ? ((Number) ultimoItem.get("id")).intValue() : 0;

        if (ultimoItemId <= 0) {
            showError("O ultimo item ainda nao foi salvo.\n\nSalve o armario antes de imprimir.");
            return;
        }

        showLoading("Verificando impressao...");
        dbExecutor.execute(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement psCheck = conn.prepareStatement(
                        "SELECT id, impresso FROM itens_armario_sauna WHERE uso_armario_id = ? ORDER BY id DESC LIMIT 1");
                psCheck.setInt(1, usoId);
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
                    runOnUiThread(() -> showError("Nenhum item encontrado no armario."));
                    return;
                }

                if (jaImpresso) {
                    hideLoading();
                    runOnUiThread(() -> showError("O ultimo item ja foi impresso anteriormente.\n\n"
                            + "Cada item so pode ser impresso uma unica vez.\n\n"
                            + "Adicione um novo item para poder imprimir novamente."));
                    return;
                }

                String cupom = gerarCupomItemArmario(armarioNumero, ultimoItem);
                PrinterManager pm = new PrinterManager(this);
                String tipoImpressora = pm.getTipoImpressora();

                if (PrinterManager.TIPO_NENHUMA.equals(tipoImpressora)) {
                    hideLoading();
                    runOnUiThread(() -> showError("Nenhuma impressora configurada.\n\nConfigure uma impressora em Configuracoes > Impressora."));
                    return;
                }

                boolean sucesso = pm.imprimirTexto(cupom);

                if (sucesso) {
                    PreparedStatement psUpdate = conn.prepareStatement(
                            "UPDATE itens_armario_sauna SET impresso = 1 WHERE id = ?");
                    psUpdate.setInt(1, itemIdParaImprimir);
                    psUpdate.executeUpdate();
                    psUpdate.close();

                    for (Map<String, Object> item : itensArmario) {
                        int id = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (id == itemIdParaImprimir) {
                            item.put("impresso", 1);
                            break;
                        }
                    }
                }

                hideLoading();
                final int fArmarioNumero = armarioNumero;
                final boolean fSucesso = sucesso;
                runOnUiThread(() -> {
                    if (fSucesso) {
                        showToast("Ultimo item do Armario " + fArmarioNumero + " impresso com sucesso!");
                        btnImprimirUltimoItem.setAlpha(0.5f);
                    } else {
                        showError("Erro ao imprimir o item.\n\nVerifique se a impressora esta ligada e conectada.");
                    }
                });

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao imprimir ultimo item armario: " + e.getMessage());
                runOnUiThread(() -> showError("Erro ao imprimir: " + e.getMessage()));
            }
        });
    }

    // ==================== v7.0.3 - BUSCA RAPIDA POR NUMERO ====================

    /**
     * v7.0.3 - Configura o campo de busca rapida por numero do armario.
     * Ao digitar o numero e pressionar o botao "IR" ou a tecla Enter/Done,
     * o sistema busca o armario no cache e abre automaticamente o dialog de detalhe.
     */
    private void configurarBuscaRapidaArmario() {
        if (btnBuscarArmario == null || etBuscarArmario == null) return;

        // Listener do botao IR
        btnBuscarArmario.setOnClickListener(v -> buscarArmarioPorNumero());

        // Listener do teclado (Enter/Done)
        etBuscarArmario.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                buscarArmarioPorNumero();
                return true;
            }
            return false;
        });
    }

    /**
     * v7.0.3 - Busca o armario pelo numero digitado no campo de busca.
     * Primeiro tenta encontrar no cache local (rapido). Se nao encontrar no cache,
     * mostra mensagem de erro. Ao encontrar, abre o dialog de detalhe automaticamente.
     */
    private void buscarArmarioPorNumero() {
        if (etBuscarArmario == null) return;

        String texto = etBuscarArmario.getText().toString().trim();
        if (texto.isEmpty()) {
            showToast("Digite o numero do armario");
            etBuscarArmario.requestFocus();
            return;
        }

        int numeroBuscado;
        try {
            numeroBuscado = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            showToast("Numero invalido");
            etBuscarArmario.requestFocus();
            return;
        }

        // Esconder o teclado
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && etBuscarArmario.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(etBuscarArmario.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}

        // Buscar no cache local primeiro (mais rapido)
        if (cacheArmarios != null && !cacheArmarios.isEmpty()) {
            for (Map<String, Object> armario : cacheArmarios) {
                int numero = armario.get("numero") != null ? ((Number) armario.get("numero")).intValue() : 0;
                if (numero == numeroBuscado) {
                    // Encontrou! Limpar campo e abrir detalhe
                    etBuscarArmario.setText("");
                    abrirDetalheArmario(armario);
                    return;
                }
            }
        }

        // Nao encontrou no cache - mostrar mensagem
        showToast("Armario " + numeroBuscado + " nao encontrado");
        etBuscarArmario.selectAll();
        etBuscarArmario.requestFocus();
    }

    // ==================== UTILITARIOS ====================

    private void atualizarTotalDialog(View dialogView, List<Map<String, Object>> itensArmario) {
        double total = calcularTotalItens(itensArmario);
        TextView tvTotal = dialogView.findViewById(R.id.tvTotalArmario);
        if (tvTotal != null) {
            tvTotal.setText(String.format("Total: R$ %.2f", total));
        }
    }

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
