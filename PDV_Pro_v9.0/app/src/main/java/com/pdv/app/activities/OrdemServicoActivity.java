package com.pdv.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.OSPhotoSyncManager;
import com.pdv.app.utils.OrderPrintManager;
import com.pdv.app.utils.WhatsAppManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modulo de Ordem de Servico com integracao total: Clientes, Servicos, Produtos, Impressao e Fechamento.
 *
 * Permite cadastrar, editar, inativar, imprimir e fechar ordens de servico.
 * v8.0.13 - Correção do bug da câmera (fechamento do diálogo) e persistência do desconto.
 */
public class OrdemServicoActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;
    private List<String> currentFotosPaths;
    private GenericAdapter<String> currentFotosAdapter;
    
    // v8.0.13 - Launchers para Câmera e Galeria
    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Uri photoUri;
    private File photoFile;
    private Map<String, Object> currentEditingRecord;

    // Leitor de codigo de barras para produtos/pecas dentro da Ordem de Servico
    private ActivityResultLauncher<Intent> produtoOsScannerLauncher;
    private List<Map<String, Object>> currentOsItensProdutos;
    private GenericAdapter<Map<String, Object>> currentOsProdutosAdapter;
    private TextView currentOsTvTotalServicos, currentOsTvTotalProdutos, currentOsTvTotalGeral;
    private List<Map<String, Object>> currentOsItensServicos;
    private EditText currentOsDescontoValor, currentOsDescontoPercent;

    /**
     * Retorna o ID do usuario logado a partir da sessao.
     */
    private int getSessionUserId() {
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        return prefs.getInt("user_id", 0);
    }

    /**
     * Retorna o nome do usuario logado a partir da sessao.
     */
    private String getSessionUserNome() {
        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        return prefs.getString("user_nome", "");
    }

    /**
     * Verifica se o usuario logado e administrador.
     */
    private boolean isAdmin() {
        return PermissionManager.getInstance(this).isAdministrador();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificar(this, PermissionConstants.ORDEM_SERVICO_ACESSAR)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_cadastro_lista);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Ordens de Servico");

        etBusca = findViewById(R.id.etBusca);
        etBusca.setHint("Buscar por numero, cliente, equipamento ou status...");

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_os, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, safeStr(item.get("line1")));
            holder.setText(R.id.tvLine2, safeStr(item.get("line2")));
            
            TextView tvLine3 = holder.find(R.id.tvLine3);
            if (tvLine3 != null) {
                String infoUsuario = safeStr(item.get("line3"));
                if (!infoUsuario.isEmpty()) {
                    tvLine3.setVisibility(View.VISIBLE);
                    tvLine3.setText(infoUsuario);
                } else {
                    tvLine3.setVisibility(View.GONE);
                }
            }

            Button btnVerOS = holder.find(R.id.btnVerOS);
            if (btnVerOS != null) {
                btnVerOS.setVisibility(View.VISIBLE);
                btnVerOS.setOnClickListener(v -> {
                    Intent intent = new Intent(this, OrdemServicoDetalheActivity.class);
                    intent.putExtra("os_id", ((Number) item.get("id")).intValue());
                    startActivity(intent);
                });
            }

            Button btnImprimir = holder.find(R.id.btnImprimir);
            if (btnImprimir != null) {
                btnImprimir.setVisibility(View.VISIBLE);
                btnImprimir.setOnClickListener(v -> imprimirOSComSeguranca(item));
            }

            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.ORDEM_SERVICO_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            Button btnWhatsApp = holder.find(R.id.btnWhatsApp);
            if (btnWhatsApp != null) {
                if (WhatsAppManager.isWhatsAppInstalled(this)) {
                    btnWhatsApp.setVisibility(View.VISIBLE);
                    btnWhatsApp.setOnClickListener(v -> {
                        new AlertDialog.Builder(this)
                                .setTitle("Enviar via WhatsApp")
                                .setMessage("Escolha como deseja enviar a OS:")
                                .setPositiveButton("Sem numero (seletor)", (d, w) -> {
                                    WhatsAppManager.enviarOSWhatsAppSemNumero(this, item);
                                })
                                .setNegativeButton("Com numero", (d, w) -> {
                                    showInputDialogWhatsApp(item);
                                })
                                .setNeutralButton("Cancelar", null)
                                .show();
                    });
                } else {
                    btnWhatsApp.setVisibility(View.GONE);
                }
            }

            Button btnFechar = holder.find(R.id.btnInativar);
            if (btnFechar != null) {
                String status = safeStr(item.get("status"));
                int vendaId = (int) (item.get("venda_id") != null ? item.get("venda_id") : 0);

                if (vendaId == 0 && !"Concluida".equalsIgnoreCase(status) && !"Cancelada".equalsIgnoreCase(status)) {
                    btnFechar.setText("FECHAR");
                    btnFechar.setBackgroundResource(R.drawable.btn_primary);
                    btnFechar.setOnClickListener(v -> fecharOS(item));
                } else {
                    if (isAdmin()) {
                        btnFechar.setText("X");
                        btnFechar.setVisibility(View.VISIBLE);
                        btnFechar.setBackgroundResource(R.drawable.btn_danger);
                        btnFechar.setOnClickListener(v -> showConfirm(
                                "Inativar OS",
                                "Deseja inativar esta ordem de servico?\n\nEsta acao so pode ser realizada por administradores.",
                                () -> inativarRecord(((Number) item.get("id")).intValue())
                        ));
                    } else {
                        btnFechar.setVisibility(View.GONE);
                    }
                }
            }
        });
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnNovo).setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.ORDEM_SERVICO_CRIAR)) {
                showEditDialog(null);
            }
        });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());
        etBusca.setOnEditorActionListener((v, a, e) -> {
            loadData();
            return true;
        });

        // v8.0.13 - Inicializar Launchers
        initLaunchers();

        ensureColumnsExist();
        loadData();
    }



    /**
     * Imprime a OS em thread separada, recarregando os dados pelo ID antes de imprimir.
     * Isso evita travamento da tela e corrige falhas quando o item da lista vem incompleto.
     */
    private void imprimirOSComSeguranca(Map<String, Object> itemLista) {
        if (itemLista == null || itemLista.get("id") == null) {
            showError("Nao foi possivel identificar esta ordem de servico para impressao.");
            return;
        }
        final int osId = ((Number) itemLista.get("id")).intValue();
        showLoading("Imprimindo ordem de servico...");
        new Thread(() -> {
            try {
                Map<String, Object> osCompleta = carregarOSParaImpressao(osId);
                if (osCompleta == null) osCompleta = itemLista;

                OrderPrintManager opm = new OrderPrintManager(this);
                boolean ok = opm.imprimirOS(osCompleta);
                hideLoading();
                if (ok) {
                    showToast("Ordem de servico enviada para impressao!");
                } else {
                    showError("Falha ao imprimir a ordem de servico. Verifique a impressora configurada.");
                }
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao imprimir ordem de servico: " + e.getMessage());
            }
        }).start();
    }

    private Map<String, Object> carregarOSParaImpressao(int osId) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT os.*, c.nome as cliente_cadastrado_nome, " +
                    "(SELECT COALESCE(SUM(oi.total),0) FROM os_itens oi WHERE oi.os_id = os.id) as total_itens " +
                    "FROM ordens_servico os " +
                    "LEFT JOIN clientes c ON os.cliente_id = c.id " +
                    "WHERE os.id = ? LIMIT 1");
            ps.setInt(1, osId);
            ResultSet rs = ps.executeQuery();
            Map<String, Object> map = null;
            if (rs.next()) {
                map = new LinkedHashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("numero", rs.getInt("numero"));
                map.put("cliente_nome", rs.getString("cliente_nome"));
                map.put("cliente_cadastrado_nome", rs.getString("cliente_cadastrado_nome"));
                map.put("equipamento", rs.getString("equipamento"));
                map.put("defeito_relatado", rs.getString("defeito_relatado"));
                map.put("status", rs.getString("status"));
                map.put("valor_servico", rs.getDouble("valor_servico"));
                map.put("observacao", rs.getString("observacao"));
                map.put("servico_id", rs.getInt("servico_id"));
                map.put("cliente_id", rs.getInt("cliente_id"));
                map.put("produto_id", rs.getInt("produto_id"));
                map.put("venda_id", rs.getInt("venda_id"));
                map.put("desconto_valor", rs.getDouble("desconto_valor"));
                map.put("desconto_percentual", rs.getDouble("desconto_percentual"));
                try { map.put("usuario_abertura_nome", rs.getString("usuario_abertura_nome")); } catch (Exception ignored) {}
                try { map.put("usuario_fechamento_nome", rs.getString("usuario_fechamento_nome")); } catch (Exception ignored) {}
                try { map.put("defeitos", rs.getString("defeitos")); } catch (Exception ignored) {}
                try { map.put("solucoes", rs.getString("solucoes")); } catch (Exception ignored) {}
                try { map.put("equipamento_detalhado", rs.getString("equipamento_detalhado")); } catch (Exception ignored) {}
            }
            rs.close();
            ps.close();
            return map;
        } catch (Exception e) {
            return null;
        }
    }

    private void initLaunchers() {
        produtoOsScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String barcode = result.getData().getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT);
                        if (barcode != null && !barcode.trim().isEmpty()) {
                            adicionarProdutoOSPorCodigo(barcode.trim());
                        }
                    }
                });

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                abrirCamera();
            } else {
                showError("Permissão de câmera negada. Não é possível tirar fotos.");
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
            if (result && photoFile != null) {
                String path = photoFile.getAbsolutePath();
                if (currentFotosPaths != null) {
                    currentFotosPaths.add(path);
                    if (currentFotosAdapter != null) {
                        currentFotosAdapter.setItems(new ArrayList<>(currentFotosPaths));
                    }
                }
                // Reabrir o diálogo após a captura
                showEditDialog(currentEditingRecord);
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                    String path = saveImageToInternalStorage(bitmap);
                    if (path != null && currentFotosPaths != null) {
                        currentFotosPaths.add(path);
                        if (currentFotosAdapter != null) {
                            currentFotosAdapter.setItems(new ArrayList<>(currentFotosPaths));
                        }
                    }
                } catch (Exception e) {
                    showError("Erro ao carregar imagem da galeria.");
                }
                // Reabrir o diálogo após a seleção
                showEditDialog(currentEditingRecord);
            }
        });
    }

    // ==================== FECHAR OS ====================

    private void fecharOS(Map<String, Object> os) {
        int osId = ((Number) os.get("id")).intValue();
        showLoading("Preparando fechamento...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                List<Map<String, Object>> itens = new ArrayList<>();
                PreparedStatement psItens = conn.prepareStatement(
                        "SELECT * FROM os_itens WHERE os_id = ? ORDER BY id ASC");
                psItens.setInt(1, osId);
                ResultSet rsItens = psItens.executeQuery();
                while (rsItens.next()) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("descricao", rsItens.getString("descricao"));
                    it.put("quantidade", rsItens.getDouble("quantidade"));
                    it.put("preco_unitario", rsItens.getDouble("preco_unitario"));
                    it.put("total", rsItens.getDouble("total"));
                    it.put("ref_id", rsItens.getInt("ref_id"));
                    itens.add(it);
                }
                rsItens.close();
                psItens.close();

                hideLoading();

                if (itens.isEmpty()) {
                    double valor = os.get("valor_servico") != null ? (double) os.get("valor_servico") : 0.0;
                    Object clienteIdObj = os.get("cliente_id");
                    int clienteId = (clienteIdObj != null) ? ((Number) clienteIdObj).intValue() : 0;

                    Intent intent = new Intent(this, PagamentoActivity.class);
                    intent.putExtra("total_bruto", valor);
                    intent.putExtra("total_liquido", valor);
                    intent.putExtra("cliente_id", clienteId);
                    intent.putExtra("os_id_fechar", osId);
                    intent.putExtra("os_usuario_fechamento_id", getSessionUserId());
                    intent.putExtra("os_usuario_fechamento_nome", getSessionUserNome());
                    intent.putExtra("itens_count", 1);
                    intent.putExtra("item_produto_id_0", 0);
                    String desc = "OS #" + os.get("numero");
                    String servico = (String) os.get("servico_nome");
                    if (servico != null && !servico.isEmpty()) desc += " - " + servico;
                    intent.putExtra("item_descricao_0", desc);
                    intent.putExtra("item_qtd_0", 1.0);
                    intent.putExtra("item_preco_0", valor);
                    intent.putExtra("item_total_0", valor);
                    runOnUiThread(() -> startActivity(intent));
                } else {
                    double totalBruto = 0;
                    for (Map<String, Object> it : itens) {
                        totalBruto += ((Number) it.get("total")).doubleValue();
                    }

                    double dVal = os.get("desconto_valor") != null ? ((Number) os.get("desconto_valor")).doubleValue() : 0;
                    double dPer = os.get("desconto_percentual") != null ? ((Number) os.get("desconto_percentual")).doubleValue() : 0;
                    double descontoTotal = (dPer > 0) ? (totalBruto * dPer / 100.0) : dVal;
                    double totalLiquido = totalBruto - descontoTotal;
                    if (totalLiquido < 0) totalLiquido = 0;

                    Object clienteIdObj = os.get("cliente_id");
                    int clienteId = (clienteIdObj != null) ? ((Number) clienteIdObj).intValue() : 0;

                    Intent intent = new Intent(this, PagamentoActivity.class);
                    intent.putExtra("total_bruto", totalBruto);
                    intent.putExtra("total_liquido", totalLiquido);
                    intent.putExtra("desconto", descontoTotal);
                    intent.putExtra("cliente_id", clienteId);
                    intent.putExtra("os_id_fechar", osId);
                    intent.putExtra("os_usuario_fechamento_id", getSessionUserId());
                    intent.putExtra("os_usuario_fechamento_nome", getSessionUserNome());
                    intent.putExtra("itens_count", itens.size());
                    for (int i = 0; i < itens.size(); i++) {
                        Map<String, Object> it = itens.get(i);
                        intent.putExtra("item_produto_id_" + i, ((Number) it.get("ref_id")).intValue());
                        intent.putExtra("item_descricao_" + i, it.get("descricao").toString());
                        intent.putExtra("item_qtd_" + i, ((Number) it.get("quantidade")).doubleValue());
                        intent.putExtra("item_preco_" + i, ((Number) it.get("preco_unitario")).doubleValue());
                        intent.putExtra("item_total_" + i, ((Number) it.get("total")).doubleValue());
                    }
                    runOnUiThread(() -> startActivity(intent));
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    // ==================== GARANTIA DE COLUNAS ====================

    /**
     * Garante que todas as colunas adicionais das tabelas de OS existam.
     * As tabelas ordens_servico, os_itens e os_fotos sao criadas pelo DatabaseHelper na inicializacao.
     * Este metodo apenas adiciona colunas que podem nao existir em bancos antigos
     * e que nao estao cobertas pelo mecanismo central de migracao.
     * Executado em background para nao bloquear a UI.
     */
    private void ensureColumnsExist() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                // Colunas adicionais de ordens_servico (bancos antigos)
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN servico_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN cliente_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN produto_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN venda_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN usuario_abertura_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN usuario_abertura_nome VARCHAR(100) NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN usuario_fechamento_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN usuario_fechamento_nome VARCHAR(100) NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN defeitos TEXT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN solucoes TEXT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE ordens_servico ADD COLUMN equipamento_detalhado TEXT NULL"); } catch (Exception ignored) {}
                // Colunas adicionais de os_itens (bancos antigos)
                try { conn.createStatement().execute("ALTER TABLE os_itens ADD COLUMN usuario_id INT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE os_itens ADD COLUMN usuario_nome VARCHAR(100) NULL"); } catch (Exception ignored) {}
                // Colunas adicionais de os_fotos (bancos antigos)
                try { conn.createStatement().execute("ALTER TABLE os_fotos ADD COLUMN ftp_caminho TEXT NULL"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE os_fotos ADD COLUMN ftp_status VARCHAR(30) DEFAULT 'pendente'"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE os_fotos ADD COLUMN tamanho_bytes BIGINT DEFAULT 0"); } catch (Exception ignored) {}
                try { conn.createStatement().execute("ALTER TABLE os_fotos ADD COLUMN data_sync_ftp DATETIME NULL"); } catch (Exception ignored) {}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private String safeStr(Object o) {
        return o != null ? o.toString() : "";
    }

    // ==================== CARREGAR LISTA DE OS ====================

    private void loadData() {
        final String busca = etBusca != null ? etBusca.getText().toString().trim() : "";
        showLoading("Carregando ordens de servico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                StringBuilder sql = new StringBuilder();
                sql.append("SELECT os.*, c.nome as cliente_cadastrado_nome, ")
                        .append("(SELECT COALESCE(SUM(oi.total),0) FROM os_itens oi WHERE oi.os_id = os.id) as total_itens ")
                        .append("FROM ordens_servico os ")
                        .append("LEFT JOIN clientes c ON os.cliente_id = c.id ")
                        .append("WHERE os.ativo = 1 ");

                boolean comBusca = !busca.isEmpty();
                if (comBusca) {
                    sql.append("AND (CAST(os.numero AS CHAR) LIKE ? OR os.cliente_nome LIKE ? OR c.nome LIKE ? OR os.equipamento LIKE ? OR os.status LIKE ?) ");
                }
                sql.append("ORDER BY os.id DESC");

                PreparedStatement ps = conn.prepareStatement(sql.toString());
                if (comBusca) {
                    String like = "%" + busca + "%";
                    ps.setString(1, like);
                    ps.setString(2, like);
                    ps.setString(3, like);
                    ps.setString(4, like);
                    ps.setString(5, like);
                }

                ResultSet rs = ps.executeQuery();
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    map.put("numero", rs.getInt("numero"));
                    map.put("cliente_nome", rs.getString("cliente_nome"));
                    map.put("cliente_cadastrado_nome", rs.getString("cliente_cadastrado_nome"));
                    map.put("equipamento", rs.getString("equipamento"));
                    map.put("defeito_relatado", rs.getString("defeito_relatado"));
                    map.put("status", rs.getString("status"));
                    map.put("valor_servico", rs.getDouble("valor_servico"));
                    map.put("observacao", rs.getString("observacao"));
                    map.put("servico_id", rs.getInt("servico_id"));
                    map.put("cliente_id", rs.getInt("cliente_id"));
                    map.put("produto_id", rs.getInt("produto_id"));
                    map.put("venda_id", rs.getInt("venda_id"));
                    map.put("desconto_valor", rs.getDouble("desconto_valor"));
                    map.put("desconto_percentual", rs.getDouble("desconto_percentual"));

                    try { map.put("usuario_abertura_id", rs.getInt("usuario_abertura_id")); } catch (Exception ignored) {}
                    try { map.put("usuario_abertura_nome", rs.getString("usuario_abertura_nome")); } catch (Exception ignored) {}
                    try { map.put("usuario_fechamento_id", rs.getInt("usuario_fechamento_id")); } catch (Exception ignored) {}
                    try { map.put("usuario_fechamento_nome", rs.getString("usuario_fechamento_nome")); } catch (Exception ignored) {}
                    try { map.put("defeitos", rs.getString("defeitos")); } catch (Exception ignored) {}
                    try { map.put("solucoes", rs.getString("solucoes")); } catch (Exception ignored) {}
                    try { map.put("equipamento_detalhado", rs.getString("equipamento_detalhado")); } catch (Exception ignored) {}
                    
                    double totalBruto = rs.getDouble("total_itens");
                    if (totalBruto <= 0) totalBruto = rs.getDouble("valor_servico");

                    double dVal = rs.getDouble("desconto_valor");
                    double dPer = rs.getDouble("desconto_percentual");
                    double descontoTotal = (dPer > 0) ? (totalBruto * dPer / 100.0) : dVal;
                    double valorExibir = totalBruto - descontoTotal;
                    if (valorExibir < 0) valorExibir = 0;

                    map.put("valor_servico_final", valorExibir);

                    String nomeExibicao = rs.getString("cliente_cadastrado_nome");
                    if (nomeExibicao == null || nomeExibicao.isEmpty()) {
                        nomeExibicao = rs.getString("cliente_nome");
                    }

                    map.put("line1", String.format(Locale.US, "OS #%d - %s", rs.getInt("numero"), safeStr(nomeExibicao)));
                    map.put("line2", "Equip.: " + safeStr(rs.getString("equipamento"))
                            + " | Status: " + safeStr(rs.getString("status"))
                            + " | Total: R$ " + String.format(Locale.US, "%.2f", valorExibir));

                    StringBuilder line3 = new StringBuilder();
                    String nomeAbertura = safeStr(map.get("usuario_abertura_nome"));
                    String nomeFechamento = safeStr(map.get("usuario_fechamento_nome"));
                    if (!nomeAbertura.isEmpty()) {
                        line3.append("Aberta por: ").append(nomeAbertura);
                    }
                    if (!nomeFechamento.isEmpty()) {
                        if (line3.length() > 0) line3.append("  |  ");
                        line3.append("Fechada por: ").append(nomeFechamento);
                    }
                    map.put("line3", line3.toString());

                    list.add(map);
                }
                rs.close();
                ps.close();
                hideLoading();
                runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    // ==================== CLASSES AUXILIARES ====================

    private static class SpinnerItem {
        int id;
        String nome;
        double valor;
        SpinnerItem(int id, String nome) { this.id = id; this.nome = nome; }
        SpinnerItem(int id, String nome, double valor) { this.id = id; this.nome = nome; this.valor = valor; }
        @Override public String toString() { return nome; }
    }

    // ==================== DIALOG DE EDICAO ====================

    private void showEditDialog(Map<String, Object> record) {
        this.currentEditingRecord = record;
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_os_form, null);

        Spinner spCliente = dialogView.findViewById(R.id.spCliente);
        EditText etClienteManual = dialogView.findViewById(R.id.etClienteManual);
        EditText etEquipamento = dialogView.findViewById(R.id.etEquipamento);
        EditText etEquipamentoDetalhado = dialogView.findViewById(R.id.etEquipamentoDetalhado);
        EditText etDefeito = dialogView.findViewById(R.id.etDefeito);
        Spinner spStatus = dialogView.findViewById(R.id.spStatus);
        EditText etObs = dialogView.findViewById(R.id.etObs);

        String[] statusOptions = {"Aberta", "Em Andamento", "Aguardando Peça", "Aguardando Aprovação", "Concluída", "Entregue", "Cancelada"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStatus.setAdapter(statusAdapter);

        Spinner spServico = dialogView.findViewById(R.id.spServico);
        EditText etQtdServico = dialogView.findViewById(R.id.etQtdServico);
        EditText etPrecoServico = dialogView.findViewById(R.id.etPrecoServico);
        Button btnAddServico = dialogView.findViewById(R.id.btnAddServico);
        RecyclerView recyclerServicos = dialogView.findViewById(R.id.recyclerServicos);
        TextView tvTotalServicos = dialogView.findViewById(R.id.tvTotalServicos);

        Spinner spProduto = dialogView.findViewById(R.id.spProduto);
        EditText etQtdProduto = dialogView.findViewById(R.id.etQtdProduto);
        EditText etPrecoProduto = dialogView.findViewById(R.id.etPrecoProduto);
        Button btnAddProduto = dialogView.findViewById(R.id.btnAddProduto);
        ImageButton btnScanProdutoOS = dialogView.findViewById(R.id.btnScanProdutoOS);
        RecyclerView recyclerProdutos = dialogView.findViewById(R.id.recyclerProdutos);
        TextView tvTotalProdutos = dialogView.findViewById(R.id.tvTotalProdutos);

        EditText etDescontoValor = dialogView.findViewById(R.id.etDescontoValor);
        EditText etDescontoPercent = dialogView.findViewById(R.id.etDescontoPercent);
        TextView tvTotalGeral = dialogView.findViewById(R.id.tvTotalGeral);

        EditText etDefeitos = dialogView.findViewById(R.id.etDefeitos);
        EditText etSolucoes = dialogView.findViewById(R.id.etSolucoes);

        if (record != null) {
            etClienteManual.setText(safeStr(record.get("cliente_nome")));
            etEquipamento.setText(safeStr(record.get("equipamento")));
            try { etEquipamentoDetalhado.setText(safeStr(record.get("equipamento_detalhado"))); } catch (Exception ignored) {}
            etDefeito.setText(safeStr(record.get("defeito_relatado")));
            try { etDefeitos.setText(safeStr(record.get("defeitos"))); } catch (Exception ignored) {}
            try { etSolucoes.setText(safeStr(record.get("solucoes"))); } catch (Exception ignored) {}

            String currentStatus = safeStr(record.get("status"));
            for (int i = 0; i < statusOptions.length; i++) {
                if (statusOptions[i].equalsIgnoreCase(currentStatus)) {
                    spStatus.setSelection(i);
                    break;
                }
            }

            etObs.setText(safeStr(record.get("observacao")));
            
            // v8.0.13 - Garantir que o desconto seja carregado corretamente
            Object descValor = record.get("desconto_valor");
            if (descValor != null && ((Number) descValor).doubleValue() > 0) {
                etDescontoValor.setText(String.format(Locale.US, "%.2f", ((Number) descValor).doubleValue()));
            }
            Object descPercent = record.get("desconto_percentual");
            if (descPercent != null && ((Number) descPercent).doubleValue() > 0) {
                etDescontoPercent.setText(String.format(Locale.US, "%.2f", ((Number) descPercent).doubleValue()));
            }
        } else {
            spStatus.setSelection(0);
        }

        final List<Map<String, Object>> itensServicos = new ArrayList<>();
        final List<Map<String, Object>> itensProdutos = new ArrayList<>();
        currentOsItensServicos = itensServicos;
        currentOsItensProdutos = itensProdutos;
        currentOsTvTotalServicos = tvTotalServicos;
        currentOsTvTotalProdutos = tvTotalProdutos;
        currentOsTvTotalGeral = tvTotalGeral;
        currentOsDescontoValor = etDescontoValor;
        currentOsDescontoPercent = etDescontoPercent;
        final List<String> fotosPaths = (currentFotosPaths != null) ? currentFotosPaths : new ArrayList<>();
        this.currentFotosPaths = fotosPaths;

        @SuppressWarnings("unchecked")
        final GenericAdapter<Map<String, Object>>[] adpServHolder = new GenericAdapter[1];
        @SuppressWarnings("unchecked")
        final GenericAdapter<Map<String, Object>>[] adpProdHolder = new GenericAdapter[1];
        @SuppressWarnings("unchecked")
        final GenericAdapter<String>[] adapterFotosHolder = new GenericAdapter[1];

        adpServHolder[0] = new GenericAdapter<>(R.layout.item_os_item, (holder, item, pos) -> {
            holder.setText(R.id.tvItemNome, safeStr(item.get("descricao")));
            double qtd = ((Number) item.get("quantidade")).doubleValue();
            double preco = ((Number) item.get("preco_unitario")).doubleValue();
            double total = ((Number) item.get("total")).doubleValue();
            String usuarioNome = safeStr(item.get("usuario_nome"));
            String detalhe = String.format(Locale.US, "%.2fx R$ %.2f = R$ %.2f", qtd, preco, total);
            if (!usuarioNome.isEmpty()) {
                detalhe += "  [por: " + usuarioNome + "]";
            }
            holder.setText(R.id.tvItemDetalhe, detalhe);
            Button btnRem = holder.find(R.id.btnRemoverItem);
            if (btnRem != null) {
                if (isAdmin()) {
                    btnRem.setVisibility(View.VISIBLE);
                    btnRem.setOnClickListener(v -> {
                        int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (itemId > 0) {
                            removerOsItem(itemId, itensServicos, adpServHolder[0], tvTotalServicos, tvTotalProdutos, tvTotalGeral, itensProdutos, "servico");
                        } else {
                            itensServicos.remove(pos);
                            adpServHolder[0].setItems(new ArrayList<>(itensServicos));
                            atualizarTotais(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral);
                        }
                    });
                } else {
                    btnRem.setVisibility(View.GONE);
                }
            }
        });

        adpProdHolder[0] = new GenericAdapter<>(R.layout.item_os_item, (holder, item, pos) -> {
            holder.setText(R.id.tvItemNome, safeStr(item.get("descricao")));
            double qtd = ((Number) item.get("quantidade")).doubleValue();
            double preco = ((Number) item.get("preco_unitario")).doubleValue();
            double total = ((Number) item.get("total")).doubleValue();
            String usuarioNome = safeStr(item.get("usuario_nome"));
            String detalhe = String.format(Locale.US, "%.2fx R$ %.2f = R$ %.2f", qtd, preco, total);
            if (!usuarioNome.isEmpty()) {
                detalhe += "  [por: " + usuarioNome + "]";
            }
            holder.setText(R.id.tvItemDetalhe, detalhe);
            Button btnRem = holder.find(R.id.btnRemoverItem);
            if (btnRem != null) {
                if (isAdmin()) {
                    btnRem.setVisibility(View.VISIBLE);
                    btnRem.setOnClickListener(v -> {
                        int itemId = item.get("id") != null ? ((Number) item.get("id")).intValue() : 0;
                        if (itemId > 0) {
                            removerOsItem(itemId, itensProdutos, adpProdHolder[0], tvTotalServicos, tvTotalProdutos, tvTotalGeral, itensServicos, "produto");
                        } else {
                            itensProdutos.remove(pos);
                            adpProdHolder[0].setItems(new ArrayList<>(itensProdutos));
                            atualizarTotais(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral);
                        }
                    });
                } else {
                    btnRem.setVisibility(View.GONE);
                }
            }
        });

        recyclerServicos.setLayoutManager(new LinearLayoutManager(this));
        recyclerServicos.setAdapter(adpServHolder[0]);
        recyclerProdutos.setLayoutManager(new LinearLayoutManager(this));
        recyclerProdutos.setAdapter(adpProdHolder[0]);
        currentOsProdutosAdapter = adpProdHolder[0];

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                ResultSet rsC = conn.createStatement().executeQuery("SELECT id, nome FROM clientes WHERE ativo = 1 ORDER BY nome ASC");
                List<SpinnerItem> clientes = new ArrayList<>();
                clientes.add(new SpinnerItem(0, "-- Selecione um cliente (opcional) --"));
                int selCPos = 0;
                int targetCId = record != null ? ((Number) record.get("cliente_id")).intValue() : 0;
                while (rsC.next()) {
                    SpinnerItem si = new SpinnerItem(rsC.getInt("id"), rsC.getString("nome"));
                    clientes.add(si);
                    if (si.id == targetCId) selCPos = clientes.size() - 1;
                }
                rsC.close();

                ResultSet rsS = conn.createStatement().executeQuery("SELECT id, nome, valor FROM servicos WHERE ativo = 1 ORDER BY nome ASC");
                List<SpinnerItem> servicos = new ArrayList<>();
                servicos.add(new SpinnerItem(0, "-- Selecione um servico --", 0));
                while (rsS.next()) {
                    servicos.add(new SpinnerItem(rsS.getInt("id"), rsS.getString("nome"), rsS.getDouble("valor")));
                }
                rsS.close();

                ResultSet rsP = conn.createStatement().executeQuery("SELECT id, descricao, preco_venda FROM produtos WHERE ativo = 1 ORDER BY descricao ASC");
                List<SpinnerItem> produtos = new ArrayList<>();
                produtos.add(new SpinnerItem(0, "-- Selecione um produto --", 0));
                while (rsP.next()) {
                    produtos.add(new SpinnerItem(rsP.getInt("id"), rsP.getString("descricao"), rsP.getDouble("preco_venda")));
                }
                rsP.close();

                if (record != null) {
                    int osId = ((Number) record.get("id")).intValue();
                    PreparedStatement psIt = conn.prepareStatement(
                            "SELECT * FROM os_itens WHERE os_id = ? ORDER BY id ASC");
                    psIt.setInt(1, osId);
                    ResultSet rsIt = psIt.executeQuery();
                    while (rsIt.next()) {
                        Map<String, Object> it = new LinkedHashMap<>();
                        it.put("id", rsIt.getInt("id"));
                        it.put("tipo", rsIt.getString("tipo"));
                        it.put("ref_id", rsIt.getInt("ref_id"));
                        it.put("descricao", rsIt.getString("descricao"));
                        it.put("quantidade", rsIt.getDouble("quantidade"));
                        it.put("preco_unitario", rsIt.getDouble("preco_unitario"));
                        it.put("total", rsIt.getDouble("total"));
                        try { it.put("usuario_id", rsIt.getInt("usuario_id")); } catch (Exception ignored) {}
                        try { it.put("usuario_nome", rsIt.getString("usuario_nome")); } catch (Exception ignored) {}
                        if ("servico".equals(rsIt.getString("tipo"))) {
                            itensServicos.add(it);
                        } else {
                            itensProdutos.add(it);
                        }
                    }
                    rsIt.close();
                    psIt.close();

                    try {
                        PreparedStatement psFotos = conn.prepareStatement(
                                "SELECT id, caminho_foto, ftp_caminho, ftp_status, usuario_nome FROM os_fotos WHERE os_id = ? ORDER BY id ASC");
                        psFotos.setInt(1, osId);
                        ResultSet rsFotos = psFotos.executeQuery();
                        OSPhotoSyncManager photoSync = new OSPhotoSyncManager(this);
                        while (rsFotos.next()) {
                            String caminho = rsFotos.getString("caminho_foto");
                            String caminhoResolvido = caminho;
                            if (!photoSync.hasLocalFile(caminho)) {
                                OSPhotoSyncManager.OSPhoto foto = new OSPhotoSyncManager.OSPhoto(
                                        rsFotos.getInt("id"),
                                        osId,
                                        caminho,
                                        rsFotos.getString("ftp_caminho"),
                                        rsFotos.getString("ftp_status"),
                                        rsFotos.getString("usuario_nome"));
                                try {
                                    String baixada = photoSync.ensureLocalCopy(foto);
                                    if (baixada != null) {
                                        caminhoResolvido = baixada;
                                        try {
                                            PreparedStatement psUpdFoto = conn.prepareStatement(
                                                    "UPDATE os_fotos SET caminho_foto = ? WHERE id = ?");
                                            psUpdFoto.setString(1, baixada);
                                            psUpdFoto.setInt(2, foto.id);
                                            psUpdFoto.executeUpdate();
                                            psUpdFoto.close();
                                        } catch (Exception ignoredUpdateFoto) {}
                                    }
                                } catch (Exception ignoredDownloadFoto) {}
                            }
                            if (caminhoResolvido != null && !caminhoResolvido.isEmpty() && !fotosPaths.contains(caminhoResolvido)) {
                                fotosPaths.add(caminhoResolvido);
                            }
                        }
                        rsFotos.close();
                        psFotos.close();
                    } catch (Exception ignoredFotos) {
                        try {
                            PreparedStatement psFotosFallback = conn.prepareStatement(
                                    "SELECT caminho_foto FROM os_fotos WHERE os_id = ? ORDER BY id ASC");
                            psFotosFallback.setInt(1, osId);
                            ResultSet rsFotosFallback = psFotosFallback.executeQuery();
                            while (rsFotosFallback.next()) {
                                String caminho = rsFotosFallback.getString("caminho_foto");
                                if (caminho != null && !caminho.isEmpty() && !fotosPaths.contains(caminho)) {
                                    fotosPaths.add(caminho);
                                }
                            }
                            rsFotosFallback.close();
                            psFotosFallback.close();
                        } catch (Exception ignoredFallback) {
                            ignoredFallback.printStackTrace();
                        }
                    }
                }

                final int fSelCPos = selCPos;
                runOnUiThread(() -> {
                    ArrayAdapter<SpinnerItem> adpC = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, clientes);
                    adpC.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCliente.setAdapter(adpC);
                    spCliente.setSelection(fSelCPos);
                    spCliente.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            if (pos > 0) etClienteManual.setText(clientes.get(pos).nome);
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) {}
                    });

                    ArrayAdapter<SpinnerItem> adpS = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, servicos);
                    adpS.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spServico.setAdapter(adpS);
                    spServico.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            if (pos > 0) {
                                SpinnerItem si = servicos.get(pos);
                                if (si.valor > 0) {
                                    etPrecoServico.setText(String.format(Locale.US, "%.2f", si.valor));
                                }
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) {}
                    });

                    ArrayAdapter<SpinnerItem> adpP = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, produtos);
                    adpP.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spProduto.setAdapter(adpP);
                    spProduto.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                            if (pos > 0) {
                                SpinnerItem si = produtos.get(pos);
                                if (si.valor > 0) {
                                    etPrecoProduto.setText(String.format(Locale.US, "%.2f", si.valor));
                                }
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> p) {}
                    });

                    adpServHolder[0].setItems(new ArrayList<>(itensServicos));
                    adpProdHolder[0].setItems(new ArrayList<>(itensProdutos));
                    atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, etDescontoValor, etDescontoPercent);
                    if (!fotosPaths.isEmpty()) {
                        adapterFotosHolder[0].setItems(new ArrayList<>(fotosPaths));
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        etDescontoValor.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, etDescontoValor, etDescontoPercent);
            }
        });
        etDescontoPercent.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, etDescontoValor, etDescontoPercent);
            }
        });

        btnAddServico.setOnClickListener(v -> {
            int pos = spServico.getSelectedItemPosition();
            String nomeServico;
            int refId = 0;
            if (pos > 0 && spServico.getAdapter() != null) {
                SpinnerItem si = (SpinnerItem) spServico.getSelectedItem();
                nomeServico = si.nome;
                refId = si.id;
            } else {
                showToast("Selecione um servico");
                return;
            }
            double qtd = parseDouble(etQtdServico.getText().toString(), 1.0);
            double preco = parseDouble(etPrecoServico.getText().toString(), 0.0);
            if (qtd <= 0) qtd = 1;
            double total = qtd * preco;

            Map<String, Object> novoItem = new LinkedHashMap<>();
            novoItem.put("id", null);
            novoItem.put("tipo", "servico");
            novoItem.put("ref_id", refId);
            novoItem.put("descricao", nomeServico);
            novoItem.put("quantidade", qtd);
            novoItem.put("preco_unitario", preco);
            novoItem.put("total", total);
            novoItem.put("usuario_id", getSessionUserId());
            novoItem.put("usuario_nome", getSessionUserNome());
            itensServicos.add(novoItem);
            adpServHolder[0].setItems(new ArrayList<>(itensServicos));
            atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, etDescontoValor, etDescontoPercent);
            spServico.setSelection(0);
            etQtdServico.setText("1");
            etPrecoServico.setText("");
        });

        if (btnScanProdutoOS != null) {
            btnScanProdutoOS.setOnClickListener(v -> produtoOsScannerLauncher.launch(new Intent(this, BarcodeScannerActivity.class)));
        }

        btnAddProduto.setOnClickListener(v -> {
            int pos = spProduto.getSelectedItemPosition();
            String nomeProduto;
            int refId = 0;
            if (pos > 0 && spProduto.getAdapter() != null) {
                SpinnerItem si = (SpinnerItem) spProduto.getSelectedItem();
                nomeProduto = si.nome;
                refId = si.id;
            } else {
                showToast("Selecione um produto");
                return;
            }
            double qtd = parseDouble(etQtdProduto.getText().toString(), 1.0);
            double preco = parseDouble(etPrecoProduto.getText().toString(), 0.0);
            if (qtd <= 0) qtd = 1;
            double total = qtd * preco;

            Map<String, Object> novoItem = new LinkedHashMap<>();
            novoItem.put("id", null);
            novoItem.put("tipo", "produto");
            novoItem.put("ref_id", refId);
            novoItem.put("descricao", nomeProduto);
            novoItem.put("quantidade", qtd);
            novoItem.put("preco_unitario", preco);
            novoItem.put("total", total);
            novoItem.put("usuario_id", getSessionUserId());
            novoItem.put("usuario_nome", getSessionUserNome());
            itensProdutos.add(novoItem);
            adpProdHolder[0].setItems(new ArrayList<>(itensProdutos));
            atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, etDescontoValor, etDescontoPercent);
            spProduto.setSelection(0);
            etQtdProduto.setText("1");
            etPrecoProduto.setText("");
        });

        Button btnCapturarFoto = dialogView.findViewById(R.id.btnCapturarFoto);
        Button btnSelecionarFoto = dialogView.findViewById(R.id.btnSelecionarFoto);
        RecyclerView recyclerFotos = dialogView.findViewById(R.id.recyclerFotos);
        
        adapterFotosHolder[0] = new GenericAdapter<>(R.layout.item_foto, (holder, item, pos) -> {
            ImageView ivFoto = holder.find(R.id.ivFoto);
            Button btnRemover = holder.find(R.id.btnRemoverFoto);
            TextView tvStatusFoto = holder.find(R.id.tvFotoStatus);
            
            Bitmap bitmap = BitmapFactory.decodeFile(item);
            if (bitmap != null) {
                ivFoto.setImageBitmap(bitmap);
                if (tvStatusFoto != null) {
                    tvStatusFoto.setText("LOCAL");
                    tvStatusFoto.setTextColor(getResources().getColor(R.color.accent_cyan));
                }
            } else {
                ivFoto.setImageResource(R.drawable.ic_image_placeholder);
                if (tvStatusFoto != null) {
                    tvStatusFoto.setText("PENDENTE");
                    tvStatusFoto.setTextColor(getResources().getColor(R.color.colorWarning));
                }
            }
            
            btnRemover.setOnClickListener(v -> {
                fotosPaths.remove(pos);
                adapterFotosHolder[0].setItems(new ArrayList<>(fotosPaths));
            });
        });
        GenericAdapter<String> adapterFotos = adapterFotosHolder[0];
        this.currentFotosAdapter = adapterFotos;
        recyclerFotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerFotos.setAdapter(adapterFotos);
        
        btnCapturarFoto.setOnClickListener(v -> {
            // Fechar o diálogo antes de abrir a câmera para evitar inconsistência
            AlertDialog dialog = (AlertDialog) v.getTag();
            if (dialog != null) dialog.dismiss();
            
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                abrirCamera();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });
        
        btnSelecionarFoto.setOnClickListener(v -> {
            // Fechar o diálogo antes de abrir a galeria
            AlertDialog dialog = (AlertDialog) v.getTag();
            if (dialog != null) dialog.dismiss();
            
            galleryLauncher.launch("image/*");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(record == null ? "Nova Ordem de Servico" : "Editar Ordem de Servico")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    SpinnerItem selC = (SpinnerItem) spCliente.getSelectedItem();
                    double descValor = parseDouble(etDescontoValor.getText().toString(), 0);
                    double descPercent = parseDouble(etDescontoPercent.getText().toString(), 0);
                    saveRecord(
                            record != null ? ((Number) record.get("id")).intValue() : 0,
                            etClienteManual.getText().toString().trim(),
                            etEquipamento.getText().toString().trim(),
                            etDefeito.getText().toString().trim(),
                            spStatus.getSelectedItem().toString(),
                            etObs.getText().toString().trim(),
                            selC != null ? selC.id : 0,
                            itensServicos,
                            itensProdutos,
                            descValor,
                            descPercent,
                            etDefeitos.getText().toString().trim(),
                            etSolucoes.getText().toString().trim(),
                            etEquipamentoDetalhado.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .create();
        
        // v8.0.13 - Armazenar referência do diálogo nos botões para fechamento manual
        btnCapturarFoto.setTag(dialog);
        btnSelecionarFoto.setTag(dialog);
        
        dialog.show();
    }


    /**
     * Busca produto por codigo de barras/codigo interno e adiciona como produto/peca na OS aberta.
     */
    private void adicionarProdutoOSPorCodigo(String codigo) {
        if (currentOsItensProdutos == null || currentOsProdutosAdapter == null) {
            showError("Abra uma ordem de servico antes de usar o leitor de codigo de barras.");
            return;
        }
        showLoading("Buscando produto pelo codigo...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, descricao, preco_venda FROM produtos WHERE ativo = 1 AND (codigo_barras = ? OR codigo = ?) LIMIT 1");
                ps.setString(1, codigo);
                ps.setString(2, codigo);
                ResultSet rs = ps.executeQuery();
                Map<String, Object> produto = null;
                if (rs.next()) {
                    produto = new LinkedHashMap<>();
                    produto.put("id", rs.getInt("id"));
                    produto.put("descricao", rs.getString("descricao"));
                    produto.put("preco_venda", rs.getDouble("preco_venda"));
                }
                rs.close();
                ps.close();
                hideLoading();
                Map<String, Object> finalProduto = produto;
                runOnUiThread(() -> {
                    if (finalProduto == null) {
                        showError("Produto nao encontrado para o codigo: " + codigo);
                        return;
                    }
                    double qtd = 1.0;
                    double preco = ((Number) finalProduto.get("preco_venda")).doubleValue();
                    Map<String, Object> novoItem = new LinkedHashMap<>();
                    novoItem.put("id", null);
                    novoItem.put("tipo", "produto");
                    novoItem.put("ref_id", ((Number) finalProduto.get("id")).intValue());
                    novoItem.put("descricao", finalProduto.get("descricao").toString());
                    novoItem.put("quantidade", qtd);
                    novoItem.put("preco_unitario", preco);
                    novoItem.put("total", qtd * preco);
                    novoItem.put("usuario_id", getSessionUserId());
                    novoItem.put("usuario_nome", getSessionUserNome());
                    currentOsItensProdutos.add(novoItem);
                    currentOsProdutosAdapter.setItems(new ArrayList<>(currentOsItensProdutos));
                    atualizarTotaisComDesconto(currentOsItensServicos, currentOsItensProdutos,
                            currentOsTvTotalServicos, currentOsTvTotalProdutos, currentOsTvTotalGeral,
                            currentOsDescontoValor, currentOsDescontoPercent);
                    showToast("Produto adicionado na OS: " + finalProduto.get("descricao"));
                });
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao buscar produto: " + e.getMessage());
            }
        }).start();
    }

    private void abrirCamera() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "OS_FOTO_" + timeStamp + ".jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = new File(storageDir, imageFileName);
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(photoUri);
        } catch (Exception e) {
            showError("Erro ao preparar câmera: " + e.getMessage());
        }
    }

    // ==================== ATUALIZAR TOTAIS ====================

    private void atualizarTotais(
            List<Map<String, Object>> itensServicos,
            List<Map<String, Object>> itensProdutos,
            TextView tvTotalServicos,
            TextView tvTotalProdutos,
            TextView tvTotalGeral) {
        atualizarTotaisComDesconto(itensServicos, itensProdutos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, null, null);
    }

    private void atualizarTotaisComDesconto(
            List<Map<String, Object>> itensServicos,
            List<Map<String, Object>> itensProdutos,
            TextView tvTotalServicos,
            TextView tvTotalProdutos,
            TextView tvTotalGeral,
            EditText etDescontoValor,
            EditText etDescontoPercent) {
        double totalS = 0;
        for (Map<String, Object> it : itensServicos) {
            totalS += ((Number) it.get("total")).doubleValue();
        }
        double totalP = 0;
        for (Map<String, Object> it : itensProdutos) {
            totalP += ((Number) it.get("total")).doubleValue();
        }
        double totalG = totalS + totalP;
        double desconto = 0;
        if (etDescontoValor != null) {
            double descValor = parseDouble(etDescontoValor.getText().toString(), 0);
            double descPercent = parseDouble(etDescontoPercent.getText().toString(), 0);
            if (descPercent > 0) {
                desconto = (totalG * descPercent) / 100.0;
            } else {
                desconto = descValor;
            }
        }
        double totalFinal = totalG - desconto;
        if (totalFinal < 0) totalFinal = 0;
        final double fS = totalS, fP = totalP, fG = totalG, fDesc = desconto, fFinal = totalFinal;
        runOnUiThread(() -> {
            tvTotalServicos.setText(String.format(Locale.US, "Total Serviços: R$ %.2f", fS));
            tvTotalProdutos.setText(String.format(Locale.US, "Total Produtos: R$ %.2f", fP));
            if (fDesc > 0) {
                tvTotalGeral.setText(String.format(Locale.US, "Subtotal: R$ %.2f | Desc: -R$ %.2f | TOTAL: R$ %.2f", fG, fDesc, fFinal));
            } else {
                tvTotalGeral.setText(String.format(Locale.US, "TOTAL GERAL: R$ %.2f", fG));
            }
        });
    }

    // ==================== REMOVER ITEM DO BANCO ====================

    private void removerOsItem(int itemId,
                                List<Map<String, Object>> listaLocal,
                                GenericAdapter<Map<String, Object>> adapterLocal,
                                TextView tvTotalServicos,
                                TextView tvTotalProdutos,
                                TextView tvTotalGeral,
                                List<Map<String, Object>> outraLista,
                                String tipoLocal) {
        if (!isAdmin()) {
            showError("Apenas administradores podem remover itens de uma Ordem de Servico.");
            return;
        }
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM os_itens WHERE id = ?");
                ps.setInt(1, itemId);
                ps.executeUpdate();
                ps.close();
                listaLocal.removeIf(it -> it.get("id") != null && ((Number) it.get("id")).intValue() == itemId);
                runOnUiThread(() -> {
                    adapterLocal.setItems(new ArrayList<>(listaLocal));
                    List<Map<String, Object>> servicos = "servico".equals(tipoLocal) ? listaLocal : outraLista;
                    List<Map<String, Object>> produtos  = "produto".equals(tipoLocal) ? listaLocal : outraLista;
                    atualizarTotaisComDesconto(servicos, produtos, tvTotalServicos, tvTotalProdutos, tvTotalGeral, null, null);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ==================== SALVAR OS ====================

    private void saveRecord(int id, String cliente, String equipamento, String defeito,
                             String status, String obs, int clienteId,
                             List<Map<String, Object>> itensServicos,
                             List<Map<String, Object>> itensProdutos,
                             double descValor,
                             double descPercent,
                             String defeitos,
                             String solucoes,
                             String equipamentoDetalhado) {
        if (cliente.isEmpty()) {
            showError("Informe o nome do cliente.");
            return;
        }
        if (equipamento.isEmpty()) {
            showError("Informe o equipamento/aparelho.");
            return;
        }

        double totalServicos = 0;
        for (Map<String, Object> it : itensServicos) totalServicos += ((Number) it.get("total")).doubleValue();
        double totalProdutos = 0;
        for (Map<String, Object> it : itensProdutos) totalProdutos += ((Number) it.get("total")).doubleValue();
        double valorTotal = totalServicos + totalProdutos;

        final double fValorTotal = valorTotal;
        final int fUserId = getSessionUserId();
        final String fUserNome = getSessionUserNome();

        showLoading("Salvando ordem de servico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                int osId = id;
                if (id == 0) {
                    int proximoNumero = 1;
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(numero), 0) + 1 AS proximo FROM ordens_servico");
                    if (rs.next()) proximoNumero = rs.getInt("proximo");
                    rs.close();
                    stmt.close();

                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO ordens_servico (numero, cliente_nome, equipamento, defeito_relatado, status, valor_servico, observacao, cliente_id, desconto_valor, desconto_percentual, ativo, usuario_abertura_id, usuario_abertura_nome, defeitos, solucoes, equipamento_detalhado) VALUES (?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS);
                    ps.setInt(1, proximoNumero);
                    ps.setString(2, cliente);
                    ps.setString(3, equipamento);
                    ps.setString(4, defeito);
                    ps.setString(5, status.isEmpty() ? "Aberta" : status);
                    ps.setDouble(6, fValorTotal);
                    ps.setString(7, obs);
                    if (clienteId > 0) ps.setInt(8, clienteId); else ps.setNull(8, java.sql.Types.INTEGER);
                    ps.setDouble(9, descValor);
                    ps.setDouble(10, descPercent);
                    if (fUserId > 0) ps.setInt(11, fUserId); else ps.setNull(11, java.sql.Types.INTEGER);
                    ps.setString(12, fUserNome.isEmpty() ? null : fUserNome);
                    ps.setString(13, defeitos.isEmpty() ? null : defeitos);
                    ps.setString(14, solucoes.isEmpty() ? null : solucoes);
                    ps.setString(15, equipamentoDetalhado.isEmpty() ? null : equipamentoDetalhado);
                    ps.executeUpdate();
                    ResultSet rsKeys = ps.getGeneratedKeys();
                    if (rsKeys.next()) osId = rsKeys.getInt(1);
                    rsKeys.close();
                    ps.close();
                } else {
                    // v8.0.13 - Corrigido: Incluído desconto_percentual no UPDATE
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ordens_servico SET cliente_nome=?, equipamento=?, defeito_relatado=?, status=?, valor_servico=?, observacao=?, cliente_id=?, desconto_valor=?, desconto_percentual=?, defeitos=?, solucoes=?, equipamento_detalhado=? WHERE id=?");
                    ps.setString(1, cliente);
                    ps.setString(2, equipamento);
                    ps.setString(3, defeito);
                    ps.setString(4, status.isEmpty() ? "Aberta" : status);
                    ps.setDouble(5, fValorTotal);
                    ps.setString(6, obs);
                    if (clienteId > 0) ps.setInt(7, clienteId); else ps.setNull(7, java.sql.Types.INTEGER);
                    ps.setDouble(8, descValor);
                    ps.setDouble(9, descPercent);
                    ps.setString(10, defeitos.isEmpty() ? null : defeitos);
                    ps.setString(11, solucoes.isEmpty() ? null : solucoes);
                    ps.setString(12, equipamentoDetalhado.isEmpty() ? null : equipamentoDetalhado);
                    ps.setInt(13, id);
                    ps.executeUpdate();
                    ps.close();
                }

                final int fOsId = osId;
                List<Map<String, Object>> todosItens = new ArrayList<>();
                todosItens.addAll(itensServicos);
                todosItens.addAll(itensProdutos);
                for (Map<String, Object> it : todosItens) {
                    if (it.get("id") == null) {
                        PreparedStatement psIt = conn.prepareStatement(
                                "INSERT INTO os_itens (os_id, tipo, ref_id, descricao, quantidade, preco_unitario, total, usuario_id, usuario_nome) VALUES (?,?,?,?,?,?,?,?,?)");
                        psIt.setInt(1, fOsId);
                        psIt.setString(2, it.get("tipo").toString());
                        int refId = it.get("ref_id") != null ? ((Number) it.get("ref_id")).intValue() : 0;
                        if (refId > 0) psIt.setInt(3, refId); else psIt.setNull(3, java.sql.Types.INTEGER);
                        psIt.setString(4, it.get("descricao").toString());
                        psIt.setDouble(5, ((Number) it.get("quantidade")).doubleValue());
                        psIt.setDouble(6, ((Number) it.get("preco_unitario")).doubleValue());
                        psIt.setDouble(7, ((Number) it.get("total")).doubleValue());
                        Object itUserId = it.get("usuario_id");
                        Object itUserNome = it.get("usuario_nome");
                        int uid = (itUserId != null) ? ((Number) itUserId).intValue() : fUserId;
                        String unome = (itUserNome != null && !itUserNome.toString().isEmpty()) ? itUserNome.toString() : fUserNome;
                        if (uid > 0) psIt.setInt(8, uid); else psIt.setNull(8, java.sql.Types.INTEGER);
                        psIt.setString(9, unome.isEmpty() ? null : unome);
                        psIt.executeUpdate();
                        psIt.close();
                    }
                }

                Map<String, Integer> fotosJaSalvas = new LinkedHashMap<>();
                Map<String, String> fotosFtpSalvas = new LinkedHashMap<>();
                try {
                    PreparedStatement psFotosExist = conn.prepareStatement(
                            "SELECT id, caminho_foto, ftp_caminho FROM os_fotos WHERE os_id = ?");
                    psFotosExist.setInt(1, fOsId);
                    ResultSet rsFotosExist = psFotosExist.executeQuery();
                    while (rsFotosExist.next()) {
                        String caminho = rsFotosExist.getString("caminho_foto");
                        fotosJaSalvas.put(caminho, rsFotosExist.getInt("id"));
                        fotosFtpSalvas.put(caminho, rsFotosExist.getString("ftp_caminho"));
                    }
                    rsFotosExist.close();
                    psFotosExist.close();
                } catch (Exception ignoredFotosCheck) {}

                if (currentFotosPaths != null) {
                    OSPhotoSyncManager photoSync = new OSPhotoSyncManager(this);
                    boolean redeDisponivel = photoSync.isNetworkAvailable();
                    for (String caminhoFoto : currentFotosPaths) {
                        if (caminhoFoto == null || caminhoFoto.trim().isEmpty()) continue;

                        Integer fotoId = fotosJaSalvas.get(caminhoFoto);
                        String ftpAtual = fotosFtpSalvas.get(caminhoFoto);

                        if (fotoId == null) {
                            try {
                                PreparedStatement psFoto = conn.prepareStatement(
                                        "INSERT INTO os_fotos (os_id, caminho_foto, ftp_status, tamanho_bytes, usuario_id, usuario_nome) VALUES (?,?,?,?,?,?)",
                                        Statement.RETURN_GENERATED_KEYS);
                                psFoto.setInt(1, fOsId);
                                psFoto.setString(2, caminhoFoto);
                                psFoto.setString(3, OSPhotoSyncManager.STATUS_PENDING);
                                File arquivoFoto = new File(caminhoFoto);
                                psFoto.setLong(4, arquivoFoto.exists() ? arquivoFoto.length() : 0);
                                if (fUserId > 0) psFoto.setInt(5, fUserId); else psFoto.setNull(5, java.sql.Types.INTEGER);
                                psFoto.setString(6, fUserNome.isEmpty() ? null : fUserNome);
                                psFoto.executeUpdate();
                                ResultSet rsFotoKey = psFoto.getGeneratedKeys();
                                if (rsFotoKey.next()) {
                                    fotoId = rsFotoKey.getInt(1);
                                }
                                rsFotoKey.close();
                                psFoto.close();
                            } catch (Exception ignoredFoto) {
                                ignoredFoto.printStackTrace();
                            }
                        }

                        if (fotoId != null && (ftpAtual == null || ftpAtual.trim().isEmpty())) {
                            if (!redeDisponivel) {
                                atualizarStatusFotoFtp(conn, fotoId, OSPhotoSyncManager.STATUS_PENDING, null, 0);
                            } else {
                                try {
                                    OSPhotoSyncManager.UploadResult upload = photoSync.uploadPhoto(fOsId, caminhoFoto);
                                    atualizarStatusFotoFtp(conn, fotoId, OSPhotoSyncManager.STATUS_SYNCED, upload.remotePath, upload.bytes);
                                } catch (Exception e) {
                                    atualizarStatusFotoFtp(conn, fotoId, OSPhotoSyncManager.STATUS_ERROR, null, 0);
                                }
                            }
                        }
                    }

                    for (String caminhoSalvo : fotosJaSalvas.keySet()) {
                        if (!currentFotosPaths.contains(caminhoSalvo)) {
                            try {
                                PreparedStatement psDelFoto = conn.prepareStatement(
                                        "DELETE FROM os_fotos WHERE os_id = ? AND caminho_foto = ?");
                                psDelFoto.setInt(1, fOsId);
                                psDelFoto.setString(2, caminhoSalvo);
                                psDelFoto.executeUpdate();
                                psDelFoto.close();
                            } catch (Exception ignoredDelFoto) {
                                ignoredDelFoto.printStackTrace();
                            }
                        }
                    }
                }

                hideLoading();
                showToast("Ordem de servico salva com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void atualizarStatusFotoFtp(Connection conn, int fotoId, String status, String ftpCaminho, long tamanhoBytes) {
        try {
            PreparedStatement ps;
            if (ftpCaminho != null && !ftpCaminho.trim().isEmpty()) {
                ps = conn.prepareStatement(
                        "UPDATE os_fotos SET ftp_caminho = ?, ftp_status = ?, tamanho_bytes = ?, data_sync_ftp = NOW() WHERE id = ?");
                ps.setString(1, ftpCaminho);
                ps.setString(2, status);
                ps.setLong(3, tamanhoBytes);
                ps.setInt(4, fotoId);
            } else {
                ps = conn.prepareStatement("UPDATE os_fotos SET ftp_status = ? WHERE id = ?");
                ps.setString(1, status);
                ps.setInt(2, fotoId);
            }
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    // ==================== INATIVAR OS ====================

    private void inativarRecord(int id) {
        if (!isAdmin()) {
            showError("Apenas administradores podem inativar uma Ordem de Servico.");
            return;
        }
        showLoading("Inativando ordem de servico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE ordens_servico SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Ordem de servico inativada com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

     // ==================== WHATSAPP ====================

    private void showInputDialogWhatsApp(Map<String, Object> os) {
        EditText input = new EditText(this);
        input.setHint("Ex: 5511999999999");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF90A4AE);
        input.setBackgroundColor(0xFF2C3E50);
        input.setPadding(16, 16, 16, 16);

        new AlertDialog.Builder(this)
                .setTitle("Numero de WhatsApp")
                .setMessage("Digite o numero de WhatsApp do cliente (com codigo de pais):")
                .setView(input)
                .setPositiveButton("Enviar", (d, w) -> {
                    String numero = input.getText().toString().trim();
                    if (numero.isEmpty()) {
                        showError("Digite um numero de WhatsApp valido.");
                        return;
                    }
                    WhatsAppManager.enviarOSWhatsApp(this, os, numero);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ==================== UTILITÁRIOS ====================
    private double parseDouble(String s, double defaultVal) {
        try {
            if (s == null || s.trim().isEmpty()) return defaultVal;
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ==================== TRATAMENTO DE IMAGENS ====================

    private String saveImageToInternalStorage(Bitmap bitmap) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "OS_FOTO_" + timeStamp + ".jpg";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File imageFile = new File(storageDir, imageFileName);
            
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            
            return imageFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
