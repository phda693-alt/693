package com.pdv.app.activities;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.ItemNotaEntrada;
import com.pdv.app.models.NotaEntrada;
import com.pdv.app.models.Produto;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.FormatUtils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela de Entrada de Notas Fiscais.
 * Permite registrar notas de entrada de mercadorias para alimentar o estoque.
 * 
 * Fluxo:
 * 1. Criar nova nota (numero, fornecedor, observacao)
 * 2. Adicionar itens a nota (produto, quantidade, custo unitario)
 * 3. Confirmar nota -> atualiza estoque dos produtos e preco de custo
 * 
 * Status da nota:
 * - pendente: nota criada, aguardando confirmacao (itens podem ser adicionados/removidos)
 * - confirmada: nota confirmada, estoque atualizado (nao pode mais ser alterada)
 * - cancelada: nota cancelada (nao afeta estoque)
 */
public class EntradaNotasActivity extends BaseActivity {
    private static final String TAG = "EntradaNotasActivity";

    private RecyclerView recyclerView;
    private GenericAdapter<NotaEntrada> adapter;
    private EditText etBusca;
    private TextView tvContador, tvEmpty;
    private Button btnFiltroTodas, btnFiltroConfirmadas, btnFiltroPendentes;

    // Filtro: 0 = todas, 1 = confirmadas, 2 = pendentes
    private int filtroAtual = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrada_notas);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.ENTRADA_NOTAS_ACESSAR)) {
            return;
        }

        etBusca = findViewById(R.id.etBusca);
        tvContador = findViewById(R.id.tvContador);
        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        btnFiltroTodas = findViewById(R.id.btnFiltroTodas);
        btnFiltroConfirmadas = findViewById(R.id.btnFiltroConfirmadas);
        btnFiltroPendentes = findViewById(R.id.btnFiltroPendentes);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_nota_entrada, (holder, item, pos) -> {
            // Numero da nota
            String titulo = "Nota: " + FormatUtils.safeString(item.getNumeroNota());
            if (titulo.equals("Nota: ")) titulo = "Nota #" + item.getId();
            holder.setText(R.id.tvNumeroNota, titulo);

            // Fornecedor
            holder.setText(R.id.tvFornecedor, "Fornecedor: " + FormatUtils.safeString(item.getFornecedor()));

            // Data
            holder.setText(R.id.tvData, FormatUtils.formatDate(item.getDataEntrada()));

            // Total
            holder.setText(R.id.tvTotal, "R$ " + FormatUtils.formatMoney(item.getTotalNota()));

            // Observacao
            TextView tvObs = holder.find(R.id.tvObservacao);
            if (tvObs != null) {
                String obs = item.getObservacao();
                if (obs != null && !obs.isEmpty()) {
                    tvObs.setVisibility(View.VISIBLE);
                    tvObs.setText(obs);
                } else {
                    tvObs.setVisibility(View.GONE);
                }
            }

            // Status badge
            TextView tvStatus = holder.find(R.id.tvStatus);
            if (tvStatus != null) {
                String status = item.getStatus();
                if ("confirmada".equals(status)) {
                    tvStatus.setText("CONFIRMADA");
                    tvStatus.setTextColor(Color.parseColor("#00E676"));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(8f);
                    bg.setColor(Color.parseColor("#1B3A1B"));
                    bg.setStroke(1, Color.parseColor("#00E676"));
                    tvStatus.setBackground(bg);
                } else if ("cancelada".equals(status)) {
                    tvStatus.setText("CANCELADA");
                    tvStatus.setTextColor(Color.parseColor("#FF5252"));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(8f);
                    bg.setColor(Color.parseColor("#3A1B1B"));
                    bg.setStroke(1, Color.parseColor("#FF5252"));
                    tvStatus.setBackground(bg);
                } else {
                    tvStatus.setText("PENDENTE");
                    tvStatus.setTextColor(Color.parseColor("#FFD740"));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setShape(GradientDrawable.RECTANGLE);
                    bg.setCornerRadius(8f);
                    bg.setColor(Color.parseColor("#3A3A1B"));
                    bg.setStroke(1, Color.parseColor("#FFD740"));
                    tvStatus.setBackground(bg);
                }
            }

            // Botoes
            Button btnDetalhes = holder.find(R.id.btnDetalhes);
            Button btnConfirmar = holder.find(R.id.btnConfirmar);
            Button btnCancelar = holder.find(R.id.btnCancelarNota);

            if (btnDetalhes != null) {
                btnDetalhes.setOnClickListener(v -> showDetalhesNota(item));
            }

            if (btnConfirmar != null) {
                if ("pendente".equals(item.getStatus())) {
                    btnConfirmar.setVisibility(View.VISIBLE);
                    btnConfirmar.setOnClickListener(v -> {
                        showConfirm("Confirmar Nota",
                                "Ao confirmar esta nota, o estoque dos produtos sera atualizado com as quantidades informadas.\n\nDeseja confirmar?",
                                () -> confirmarNota(item.getId()));
                    });
                } else {
                    btnConfirmar.setVisibility(View.GONE);
                }
            }

            if (btnCancelar != null) {
                if ("pendente".equals(item.getStatus())) {
                    btnCancelar.setVisibility(View.VISIBLE);
                    btnCancelar.setOnClickListener(v -> {
                        showConfirm("Cancelar Nota",
                                "Deseja cancelar esta nota de entrada?\n\nEsta acao nao pode ser desfeita.",
                                () -> cancelarNota(item.getId()));
                    });
                } else {
                    btnCancelar.setVisibility(View.GONE);
                }
            }
        });

        recyclerView.setAdapter(adapter);

        // Botao voltar
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        // Nova nota
        findViewById(R.id.btnNovaNota).setOnClickListener(v -> showNovaNotaDialog());

        // Busca
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());

        // Filtros
        btnFiltroTodas.setOnClickListener(v -> { filtroAtual = 0; updateFilterButtons(); loadData(); });
        btnFiltroConfirmadas.setOnClickListener(v -> { filtroAtual = 1; updateFilterButtons(); loadData(); });
        btnFiltroPendentes.setOnClickListener(v -> { filtroAtual = 2; updateFilterButtons(); loadData(); });

        updateFilterButtons();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void updateFilterButtons() {
        btnFiltroTodas.setAlpha(0.5f);
        btnFiltroConfirmadas.setAlpha(0.5f);
        btnFiltroPendentes.setAlpha(0.5f);
        switch (filtroAtual) {
            case 0: btnFiltroTodas.setAlpha(1.0f); break;
            case 1: btnFiltroConfirmadas.setAlpha(1.0f); break;
            case 2: btnFiltroPendentes.setAlpha(1.0f); break;
        }
    }

    // =========================================================================
    // CARREGAR DADOS
    // =========================================================================

    private void loadData() {
        String busca = etBusca.getText().toString().trim();
        showLoading("Carregando notas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                StringBuilder sql = new StringBuilder(
                        "SELECT n.*, u.nome AS usuario_nome FROM notas_entrada n " +
                        "LEFT JOIN usuarios u ON n.usuario_id = u.id WHERE 1=1");

                switch (filtroAtual) {
                    case 1: sql.append(" AND n.status = 'confirmada'"); break;
                    case 2: sql.append(" AND n.status = 'pendente'"); break;
                }

                if (!busca.isEmpty()) {
                    sql.append(" AND (n.numero_nota LIKE ? OR n.fornecedor LIKE ?)");
                }
                sql.append(" ORDER BY n.id DESC");

                PreparedStatement ps = conn.prepareStatement(sql.toString());
                int paramIndex = 1;
                if (!busca.isEmpty()) {
                    ps.setString(paramIndex++, "%" + busca + "%");
                    ps.setString(paramIndex++, "%" + busca + "%");
                }

                ResultSet rs = ps.executeQuery();
                List<NotaEntrada> list = new ArrayList<>();
                while (rs.next()) {
                    NotaEntrada n = new NotaEntrada();
                    n.setId(rs.getInt("id"));
                    n.setNumeroNota(rs.getString("numero_nota"));
                    n.setFornecedor(rs.getString("fornecedor"));
                    n.setDataEntrada(rs.getString("data_entrada"));
                    n.setTotalNota(rs.getDouble("total_nota"));
                    n.setObservacao(rs.getString("observacao"));
                    n.setStatus(rs.getString("status"));
                    n.setUsuarioId(rs.getInt("usuario_id"));
                    try {
                        n.setUsuarioNome(rs.getString("usuario_nome"));
                    } catch (Exception ignored) {}
                    list.add(n);
                }
                rs.close();
                ps.close();

                final int total = list.size();
                hideLoading();
                runOnUiThread(() -> {
                    adapter.setItems(list);
                    tvContador.setText(total + " nota" + (total != 1 ? "s" : ""));
                    if (total == 0) {
                        recyclerView.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        recyclerView.setVisibility(View.VISIBLE);
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    // =========================================================================
    // NOVA NOTA
    // =========================================================================

    private void showNovaNotaDialog() {
        showLoading("Carregando fornecedores...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                List<Integer> fornecedorIds = new ArrayList<>();
                List<String> fornecedores = new ArrayList<>();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id,nome FROM fornecedores WHERE ativo=1 ORDER BY nome");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    fornecedorIds.add(rs.getInt("id"));
                    fornecedores.add(rs.getString("nome"));
                }
                rs.close();
                ps.close();

                String proximoNumero = "001";
                ps = conn.prepareStatement("SELECT COALESCE(MAX(CASE WHEN numero_nota REGEXP '^[0-9]+$' "
                        + "THEN CAST(numero_nota AS UNSIGNED) END),0)+1 proximo FROM notas_entrada");
                rs = ps.executeQuery();
                if (rs.next()) proximoNumero = String.format(Locale.US, "%03d", rs.getInt("proximo"));
                rs.close();
                ps.close();
                hideLoading();
                String numeroFinal = proximoNumero;
                runOnUiThread(() -> showNovaNotaDialogUI(numeroFinal, fornecedorIds, fornecedores));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    // Opções de condição de pagamento
    private static final String[] CONDICOES_PAGAMENTO = {
        "A Vista",
        "30 dias",
        "30/60",
        "30/60/90",
        "30/60/90/120",
        "30/60/90/120/150",
        "30/60/90/120/150/180",
        "30/60/90/120/150/180/210",
        "8 meses",
        "9 meses",
        "10 meses",
        "11 meses",
        "12 meses",
        "Personalizado"
    };

    private List<String> datasVencimentoSelecionadas = new ArrayList<>();
    private String condicaoSelecionada = "A Vista";

    private void showNovaNotaDialogUI(String proximoNumero, List<Integer> fornecedorIds,
                                      List<String> fornecedores) {
        if (fornecedores.isEmpty()) {
            showError("Nenhum fornecedor ativo cadastrado. Cadastre um fornecedor antes de criar a nota de entrada.");
            return;
        }
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_nova_nota_entrada, null);
        EditText etNumero = dialogView.findViewById(R.id.etNumeroNota);
        AutoCompleteTextView etFornecedor = dialogView.findViewById(R.id.etFornecedor);
        EditText etObs = dialogView.findViewById(R.id.etObservacao);
        Spinner spCondicao = dialogView.findViewById(R.id.spCondicaoPagamento);
        Spinner spCaixa = dialogView.findViewById(R.id.spCaixaNota);
        LinearLayout llParcelasContainer = dialogView.findViewById(R.id.llParcelasContainer);
        LinearLayout llParcelas = dialogView.findViewById(R.id.llParcelas);
        Button btnEditarParcelas = dialogView.findViewById(R.id.btnEditarParcelas);

        etNumero.setText(proximoNumero);
        ArrayAdapter<String> fornecedorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, fornecedores);
        etFornecedor.setAdapter(fornecedorAdapter);
        etFornecedor.setThreshold(0);
        etFornecedor.setOnClickListener(v -> etFornecedor.showDropDown());
        etFornecedor.setOnFocusChangeListener((v, focused) -> {
            if (focused) etFornecedor.showDropDown();
        });

        // Spinner de condição de pagamento
        ArrayAdapter<String> condicaoAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CONDICOES_PAGAMENTO);
        condicaoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCondicao.setAdapter(condicaoAdapter);

        // Spinner de caixas
        final List<Integer> caixaIds = new ArrayList<>();
        final List<String> caixaNomes = new ArrayList<>();
        caixaIds.add(0);
        caixaNomes.add("-- Sem caixa --");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    caixaIds.add(rs.getInt("id"));
                    caixaNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
                runOnUiThread(() -> {
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                });
            }
        }).start();

        // Listener de seleção de condição
        spCondicao.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                condicaoSelecionada = CONDICOES_PAGAMENTO[position];
                datasVencimentoSelecionadas = gerarDatasVencimento(condicaoSelecionada);
                if ("A Vista".equals(condicaoSelecionada)) {
                    llParcelasContainer.setVisibility(View.GONE);
                } else {
                    llParcelasContainer.setVisibility(View.VISIBLE);
                    atualizarListaParcelas(llParcelas, datasVencimentoSelecionadas);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnEditarParcelas.setOnClickListener(v -> {
            showEditarParcelasDialog(datasVencimentoSelecionadas, updated -> {
                datasVencimentoSelecionadas = updated;
                atualizarListaParcelas(llParcelas, datasVencimentoSelecionadas);
            });
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nova Nota de Entrada")
                .setView(dialogView)
                .setPositiveButton("Criar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String numero = etNumero.getText().toString().trim();
                    String fornecedor = etFornecedor.getText().toString().trim();
                    String obs = etObs.getText().toString().trim();

                    if (fornecedor.isEmpty()) {
                        etFornecedor.setError("Selecione um fornecedor");
                        return;
                    }
                    int fornecedorPos = -1;
                    for (int i = 0; i < fornecedores.size(); i++) {
                        if (fornecedores.get(i).equalsIgnoreCase(fornecedor)) {
                            fornecedorPos = i;
                            break;
                        }
                    }
                    if (fornecedorPos < 0) {
                        etFornecedor.setError("Escolha um fornecedor cadastrado na lista");
                        etFornecedor.showDropDown();
                        return;
                    }
                    int caixaPos = spCaixa.getSelectedItemPosition();
                    int caixaId = caixaPos >= 0 && caixaPos < caixaIds.size() ? caixaIds.get(caixaPos) : 0;
                    dialog.dismiss();
                    criarNota(numero, fornecedorIds.get(fornecedorPos), fornecedores.get(fornecedorPos),
                            obs, condicaoSelecionada, datasVencimentoSelecionadas, caixaId);
        }));
        dialog.show();
    }

    /**
     * Gera datas de vencimento baseadas na condição de pagamento selecionada.
     */
    private List<String> gerarDatasVencimento(String condicao) {
        List<String> datas = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat sdfDisplay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        if ("A Vista".equals(condicao)) {
            return datas; // sem parcelas
        } else if ("Personalizado".equals(condicao)) {
            // Uma parcela padrão para o usuário editar
            cal.add(Calendar.DAY_OF_MONTH, 30);
            datas.add(sdfDisplay.format(cal.getTime()));
            return datas;
        }

        // Parsear condição numérica (ex: "30", "30/60", "8 meses")
        if (condicao.contains("meses")) {
            int meses = Integer.parseInt(condicao.replace(" meses", "").trim());
            for (int i = 1; i <= meses; i++) {
                Calendar c = Calendar.getInstance();
                c.add(Calendar.MONTH, i);
                datas.add(sdfDisplay.format(c.getTime()));
            }
        } else {
            String[] partes = condicao.replace(" dias", "").split("/");
            for (String parte : partes) {
                try {
                    int dias = Integer.parseInt(parte.trim());
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DAY_OF_MONTH, dias);
                    datas.add(sdfDisplay.format(c.getTime()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return datas;
    }

    private void atualizarListaParcelas(LinearLayout llParcelas, List<String> datas) {
        llParcelas.removeAllViews();
        for (int i = 0; i < datas.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 4, 0, 4);

            TextView tvNum = new TextView(this);
            tvNum.setText("Parcela " + (i + 1) + ": ");
            tvNum.setTextColor(0xFF90A4AE);
            tvNum.setTextSize(12);
            tvNum.setMinWidth(80);
            row.addView(tvNum);

            TextView tvData = new TextView(this);
            tvData.setText(datas.get(i));
            tvData.setTextColor(0xFFFFD740);
            tvData.setTextSize(12);
            tvData.setPadding(8, 0, 0, 0);
            row.addView(tvData);

            llParcelas.addView(row);
        }
    }

    interface OnParcelasUpdated {
        void onUpdated(List<String> datas);
    }

    private void showEditarParcelasDialog(List<String> datasAtuais, OnParcelasUpdated callback) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        List<EditText> campos = new ArrayList<>();
        for (int i = 0; i < datasAtuais.size(); i++) {
            TextView tvLabel = new TextView(this);
            tvLabel.setText("Parcela " + (i + 1) + ":");
            tvLabel.setTextColor(0xFF90A4AE);
            tvLabel.setTextSize(12);
            layout.addView(tvLabel);

            EditText et = new EditText(this);
            et.setText(datasAtuais.get(i));
            et.setHint("dd/MM/yyyy");
            et.setTextColor(0xFFFFFFFF);
            et.setHintTextColor(0xFF607D8B);
            et.setBackgroundResource(R.drawable.input_bg);
            et.setPadding(24, 16, 24, 16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 0, 8);
            et.setLayoutParams(lp);
            layout.addView(et);
            campos.add(et);
        }

        // Botão para adicionar parcela
        Button btnAdd = new Button(this);
        btnAdd.setText("+ Adicionar Parcela");
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setBackgroundResource(R.drawable.btn_secondary);
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBtn.setMargins(0, 8, 0, 0);
        btnAdd.setLayoutParams(lpBtn);
        layout.addView(btnAdd);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Editar Parcelas")
                .setView(sv)
                .setPositiveButton("Confirmar", (d, w) -> {
                    List<String> novasDatas = new ArrayList<>();
                    for (EditText et : campos) {
                        String val = et.getText().toString().trim();
                        if (!val.isEmpty()) novasDatas.add(val);
                    }
                    callback.onUpdated(novasDatas);
                })
                .setNegativeButton("Cancelar", null)
                .create();

        btnAdd.setOnClickListener(v -> {
            // Adicionar novo campo de data
            int novaParcela = campos.size() + 1;
            TextView tvLabel = new TextView(this);
            tvLabel.setText("Parcela " + novaParcela + ":");
            tvLabel.setTextColor(0xFF90A4AE);
            tvLabel.setTextSize(12);
            layout.addView(tvLabel, layout.indexOfChild(btnAdd));

            EditText et = new EditText(this);
            et.setHint("dd/MM/yyyy");
            et.setTextColor(0xFFFFFFFF);
            et.setHintTextColor(0xFF607D8B);
            et.setBackgroundResource(R.drawable.input_bg);
            et.setPadding(24, 16, 24, 16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 4, 0, 8);
            et.setLayoutParams(lp);
            layout.addView(et, layout.indexOfChild(btnAdd));
            campos.add(et);
        });

        dialog.show();
    }

    private void criarNota(String numero, int fornecedorId, String fornecedor, String obs) {
        criarNota(numero, fornecedorId, fornecedor, obs, "A Vista", new ArrayList<>(), 0);
    }

    private void criarNota(String numero, int fornecedorId, String fornecedor, String obs,
                           String condicaoPagamento, List<String> datasVencimento, int caixaId) {
        showLoading("Criando nota...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                int userId = prefs.getInt("user_id", 0);

                // Serializar datas de vencimento
                String datasStr = datasVencimento.isEmpty() ? null : String.join(",", datasVencimento);

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO notas_entrada (numero_nota, fornecedor_id, fornecedor, observacao, usuario_id, status, total_nota, condicao_pagamento, datas_vencimento, caixa_nominal_id) " +
                        "VALUES (?, ?, ?, ?, ?, 'pendente', 0, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, numero);
                ps.setInt(2, fornecedorId);
                ps.setString(3, fornecedor);
                ps.setString(4, obs.isEmpty() ? null : obs);
                ps.setInt(5, userId);
                ps.setString(6, condicaoPagamento);
                ps.setString(7, datasStr);
                if (caixaId > 0) ps.setInt(8, caixaId); else ps.setNull(8, Types.INTEGER);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                int notaId = 0;
                if (keys.next()) notaId = keys.getInt(1);
                keys.close();
                ps.close();

                hideLoading();
                showToast("Nota criada! Adicione os itens.");
                loadData();

                // Abrir detalhes da nota recem-criada para adicionar itens
                final int finalNotaId = notaId;
                runOnUiThread(() -> {
                    // Carregar a nota e abrir detalhes
                    loadNotaAndShowDetalhes(finalNotaId);
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void loadNotaAndShowDetalhes(int notaId) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT n.*, u.nome AS usuario_nome FROM notas_entrada n " +
                        "LEFT JOIN usuarios u ON n.usuario_id = u.id WHERE n.id = ?");
                ps.setInt(1, notaId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    NotaEntrada nota = new NotaEntrada();
                    nota.setId(rs.getInt("id"));
                    nota.setNumeroNota(rs.getString("numero_nota"));
                    nota.setFornecedor(rs.getString("fornecedor"));
                    nota.setDataEntrada(rs.getString("data_entrada"));
                    nota.setTotalNota(rs.getDouble("total_nota"));
                    nota.setObservacao(rs.getString("observacao"));
                    nota.setStatus(rs.getString("status"));
                    nota.setUsuarioId(rs.getInt("usuario_id"));
                    try { nota.setUsuarioNome(rs.getString("usuario_nome")); } catch (Exception ignored) {}

                    runOnUiThread(() -> showDetalhesNota(nota));
                }
                rs.close();
                ps.close();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar nota: " + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    // DETALHES DA NOTA
    // =========================================================================

    private void showDetalhesNota(NotaEntrada nota) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_detalhes_nota, null);
        TextView tvInfo = dialogView.findViewById(R.id.tvInfoNota);
        LinearLayout llItens = dialogView.findViewById(R.id.llItensNota);
        TextView tvTotal = dialogView.findViewById(R.id.tvTotalNota);
        Button btnAddItem = dialogView.findViewById(R.id.btnAddItem);

        // Info da nota
        StringBuilder info = new StringBuilder();
        info.append("Nota: ").append(FormatUtils.safeString(nota.getNumeroNota())).append("\n");
        info.append("Fornecedor: ").append(FormatUtils.safeString(nota.getFornecedor())).append("\n");
        info.append("Data: ").append(FormatUtils.formatDate(nota.getDataEntrada())).append("\n");
        info.append("Status: ").append(nota.getStatus().toUpperCase()).append("\n");
        if (nota.getUsuarioNome() != null && !nota.getUsuarioNome().isEmpty()) {
            info.append("Usuario: ").append(nota.getUsuarioNome());
        }
        if (nota.getObservacao() != null && !nota.getObservacao().isEmpty()) {
            info.append("\nObs: ").append(nota.getObservacao());
        }
        tvInfo.setText(info.toString());

        // Botao adicionar item - somente para notas pendentes
        if ("pendente".equals(nota.getStatus())) {
            btnAddItem.setVisibility(View.VISIBLE);
        } else {
            btnAddItem.setVisibility(View.GONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Detalhes da Nota #" + nota.getId())
                .setView(dialogView)
                .setPositiveButton("Fechar", null)
                .create();

        // Carregar itens da nota
        loadItensNota(nota.getId(), llItens, tvTotal, nota.getStatus(), dialog);

        btnAddItem.setOnClickListener(v -> {
            dialog.dismiss();
            showAddItemDialog(nota);
        });

        dialog.show();
    }

    private void loadItensNota(int notaId, LinearLayout llItens, TextView tvTotal, String status, AlertDialog parentDialog) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM itens_nota_entrada WHERE nota_entrada_id = ? ORDER BY id ASC");
                ps.setInt(1, notaId);
                ResultSet rs = ps.executeQuery();

                List<ItemNotaEntrada> itens = new ArrayList<>();
                double totalGeral = 0;
                while (rs.next()) {
                    ItemNotaEntrada item = new ItemNotaEntrada();
                    item.setId(rs.getInt("id"));
                    item.setNotaEntradaId(rs.getInt("nota_entrada_id"));
                    item.setProdutoId(rs.getInt("produto_id"));
                    item.setDescricaoProduto(rs.getString("descricao_produto"));
                    item.setQuantidade(rs.getDouble("quantidade"));
                    item.setCustoUnitario(rs.getDouble("custo_unitario"));
                    item.setTotal(rs.getDouble("total"));
                    itens.add(item);
                    totalGeral += item.getTotal();
                }
                rs.close();
                ps.close();

                final double finalTotal = totalGeral;
                final List<ItemNotaEntrada> finalItens = itens;

                runOnUiThread(() -> {
                    llItens.removeAllViews();

                    if (finalItens.isEmpty()) {
                        TextView tvVazio = new TextView(this);
                        tvVazio.setText("Nenhum item adicionado ainda.");
                        tvVazio.setTextColor(Color.parseColor("#607D8B"));
                        tvVazio.setTextSize(13);
                        llItens.addView(tvVazio);
                    } else {
                        for (ItemNotaEntrada item : finalItens) {
                            LinearLayout itemLayout = new LinearLayout(this);
                            itemLayout.setOrientation(LinearLayout.VERTICAL);
                            itemLayout.setPadding(0, 4, 0, 8);

                            // Linha 1: Produto + Remover
                            LinearLayout linha1 = new LinearLayout(this);
                            linha1.setOrientation(LinearLayout.HORIZONTAL);

                            TextView tvProd = new TextView(this);
                            tvProd.setText(item.getDescricaoProduto());
                            tvProd.setTextColor(Color.WHITE);
                            tvProd.setTextSize(13);
                            tvProd.setTypeface(null, Typeface.BOLD);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                            tvProd.setLayoutParams(lp);
                            linha1.addView(tvProd);

                            // Botao remover (somente pendente)
                            if ("pendente".equals(status)) {
                                TextView tvRemover = new TextView(this);
                                tvRemover.setText("X");
                                tvRemover.setTextColor(Color.parseColor("#FF5252"));
                                tvRemover.setTextSize(14);
                                tvRemover.setTypeface(null, Typeface.BOLD);
                                tvRemover.setPadding(16, 0, 4, 0);
                                tvRemover.setOnClickListener(v -> {
                                    showConfirm("Remover Item",
                                            "Deseja remover \"" + item.getDescricaoProduto() + "\" da nota?",
                                            () -> {
                                                if (parentDialog != null) parentDialog.dismiss();
                                                removerItemNota(item.getId(), item.getNotaEntradaId());
                                            });
                                });
                                linha1.addView(tvRemover);
                            }

                            itemLayout.addView(linha1);

                            // Linha 2: Qtd x Custo = Total
                            TextView tvDetalhe = new TextView(this);
                            tvDetalhe.setText(FormatUtils.formatQuantidade(item.getQuantidade())
                                    + " x R$ " + FormatUtils.formatMoney(item.getCustoUnitario())
                                    + " = R$ " + FormatUtils.formatMoney(item.getTotal()));
                            tvDetalhe.setTextColor(Color.parseColor("#B0BEC5"));
                            tvDetalhe.setTextSize(12);
                            itemLayout.addView(tvDetalhe);

                            // Divider
                            View divider = new View(this);
                            divider.setBackgroundColor(Color.parseColor("#2A2F5A"));
                            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
                            divLp.topMargin = 4;
                            divider.setLayoutParams(divLp);
                            itemLayout.addView(divider);

                            llItens.addView(itemLayout);
                        }
                    }

                    tvTotal.setText("Total: R$ " + FormatUtils.formatMoney(finalTotal));
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar itens da nota: " + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    // ADICIONAR ITEM
    // =========================================================================

    private void showAddItemDialog(NotaEntrada nota) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_item_nota, null);
        Spinner spProduto = dialogView.findViewById(R.id.spProduto);
        EditText etQuantidade = dialogView.findViewById(R.id.etQuantidade);
        EditText etCusto = dialogView.findViewById(R.id.etCustoUnitario);
        TextView tvEstoqueAtual = dialogView.findViewById(R.id.tvEstoqueAtual);

        // Carregar produtos
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, descricao, preco_custo, estoque FROM produtos WHERE ativo = 1 ORDER BY descricao ASC");
                ResultSet rs = ps.executeQuery();

                List<Produto> produtos = new ArrayList<>();
                while (rs.next()) {
                    Produto p = new Produto();
                    p.setId(rs.getInt("id"));
                    p.setDescricao(rs.getString("descricao"));
                    p.setPrecoCusto(rs.getDouble("preco_custo"));
                    p.setEstoque(rs.getDouble("estoque"));
                    produtos.add(p);
                }
                rs.close();
                ps.close();

                runOnUiThread(() -> {
                    ArrayAdapter<Produto> spAdapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, produtos);
                    spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spProduto.setAdapter(spAdapter);

                    spProduto.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            Produto sel = produtos.get(position);
                            etCusto.setText(FormatUtils.formatMoney(sel.getPrecoCusto()));
                            tvEstoqueAtual.setText("Estoque atual: " + FormatUtils.formatQuantidade(sel.getEstoque()));
                        }
                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Adicionar Item a Nota")
                .setView(dialogView)
                .setPositiveButton("Adicionar", (d, w) -> {
                    Produto prodSel = (Produto) spProduto.getSelectedItem();
                    if (prodSel == null) {
                        showError("Selecione um produto.");
                        return;
                    }

                    String qtdStr = etQuantidade.getText().toString().trim();
                    String custoStr = etCusto.getText().toString().trim();

                    if (qtdStr.isEmpty()) {
                        showError("Informe a quantidade.");
                        return;
                    }

                    double qtd = FormatUtils.parseMoney(qtdStr);
                    double custo = FormatUtils.parseMoney(custoStr);

                    if (qtd <= 0) {
                        showError("A quantidade deve ser maior que zero.");
                        return;
                    }

                    adicionarItemNota(nota.getId(), prodSel.getId(), prodSel.getDescricao(), qtd, custo);
                })
                .setNegativeButton("Cancelar", (d, w) -> {
                    // Reabrir detalhes da nota
                    loadNotaAndShowDetalhes(nota.getId());
                })
                .setOnCancelListener(d -> loadNotaAndShowDetalhes(nota.getId()))
                .show();
    }

    private void adicionarItemNota(int notaId, int produtoId, String descricao, double quantidade, double custoUnitario) {
        showLoading("Adicionando item...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                double total = quantidade * custoUnitario;

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO itens_nota_entrada (nota_entrada_id, produto_id, descricao_produto, quantidade, custo_unitario, total) " +
                        "VALUES (?, ?, ?, ?, ?, ?)");
                ps.setInt(1, notaId);
                ps.setInt(2, produtoId);
                ps.setString(3, descricao);
                ps.setDouble(4, quantidade);
                ps.setDouble(5, custoUnitario);
                ps.setDouble(6, total);
                ps.executeUpdate();
                ps.close();

                // Atualizar total da nota
                atualizarTotalNota(conn, notaId);

                hideLoading();
                showToast("Item adicionado!");
                loadData();

                // Reabrir detalhes da nota
                runOnUiThread(() -> loadNotaAndShowDetalhes(notaId));

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void removerItemNota(int itemId, int notaId) {
        showLoading("Removendo item...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_nota_entrada WHERE id = ?");
                ps.setInt(1, itemId);
                ps.executeUpdate();
                ps.close();

                // Atualizar total da nota
                atualizarTotalNota(conn, notaId);

                hideLoading();
                showToast("Item removido!");
                loadData();

                // Reabrir detalhes da nota
                runOnUiThread(() -> loadNotaAndShowDetalhes(notaId));

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    private void atualizarTotalNota(Connection conn, int notaId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE notas_entrada SET total_nota = (SELECT COALESCE(SUM(total), 0) FROM itens_nota_entrada WHERE nota_entrada_id = ?) WHERE id = ?");
        ps.setInt(1, notaId);
        ps.setInt(2, notaId);
        ps.executeUpdate();
        ps.close();
    }

    // =========================================================================
    // CONFIRMAR NOTA (ATUALIZA ESTOQUE)
    // =========================================================================

    private void confirmarNota(int notaId) {
        showLoading("Confirmando nota e atualizando estoque...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Verificar se a nota tem itens
                PreparedStatement psCount = conn.prepareStatement(
                        "SELECT COUNT(*) FROM itens_nota_entrada WHERE nota_entrada_id = ?");
                psCount.setInt(1, notaId);
                ResultSet rsCount = psCount.executeQuery();
                rsCount.next();
                int totalItens = rsCount.getInt(1);
                rsCount.close();
                psCount.close();

                if (totalItens == 0) {
                    hideLoading();
                    showError("A nota nao possui itens. Adicione pelo menos um item antes de confirmar.");
                    return;
                }

                // Carregar itens da nota
                PreparedStatement psItens = conn.prepareStatement(
                        "SELECT produto_id, quantidade, custo_unitario FROM itens_nota_entrada WHERE nota_entrada_id = ?");
                psItens.setInt(1, notaId);
                ResultSet rsItens = psItens.executeQuery();

                // Atualizar estoque e preco de custo de cada produto
                PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE produtos SET estoque = estoque + ?, preco_custo = ? WHERE id = ?");

                int itensProcessados = 0;
                while (rsItens.next()) {
                    int produtoId = rsItens.getInt("produto_id");
                    double quantidade = rsItens.getDouble("quantidade");
                    double custoUnitario = rsItens.getDouble("custo_unitario");

                    psUpdate.setDouble(1, quantidade);
                    psUpdate.setDouble(2, custoUnitario);
                    psUpdate.setInt(3, produtoId);
                    psUpdate.addBatch();
                    itensProcessados++;
                }
                rsItens.close();
                psItens.close();

                psUpdate.executeBatch();
                psUpdate.close();

                // Atualizar status da nota para confirmada
                PreparedStatement psStatus = conn.prepareStatement(
                        "UPDATE notas_entrada SET status = 'confirmada' WHERE id = ?");
                psStatus.setInt(1, notaId);
                psStatus.executeUpdate();
                psStatus.close();

                // Buscar dados da nota para contas a pagar
                PreparedStatement psNota = conn.prepareStatement(
                        "SELECT * FROM notas_entrada WHERE id = ?");
                psNota.setInt(1, notaId);
                ResultSet rsNota = psNota.executeQuery();
                String condicaoPag = null;
                String datasVenc = null;
                double totalNota = 0;
                int fornecedorIdNota = 0;
                String fornecedorNome = "";
                int caixaNominalId = 0;
                if (rsNota.next()) {
                    condicaoPag = rsNota.getString("condicao_pagamento");
                    datasVenc = rsNota.getString("datas_vencimento");
                    totalNota = rsNota.getDouble("total_nota");
                    fornecedorIdNota = rsNota.getInt("fornecedor_id");
                    fornecedorNome = rsNota.getString("fornecedor");
                    try { caixaNominalId = rsNota.getInt("caixa_nominal_id"); } catch (Exception ignored) {}
                }
                rsNota.close();
                psNota.close();

                hideLoading();
                final int finalItens = itensProcessados;
                final String fCondicao = condicaoPag;
                final String fDatasVenc = datasVenc;
                final double fTotal = totalNota;
                final int fFornecedorId = fornecedorIdNota;
                final String fFornecedorNome = fornecedorNome;
                final int fCaixaId = caixaNominalId;

                runOnUiThread(() -> {
                    boolean temParcelamento = fCondicao != null && !"A Vista".equals(fCondicao)
                            && fDatasVenc != null && !fDatasVenc.trim().isEmpty();
                    List<String> datas = new ArrayList<>();
                    if (temParcelamento) {
                        for (String data : fDatasVenc.split(",")) {
                            if (data != null && !data.trim().isEmpty()) datas.add(data.trim());
                        }
                    }

                    // A partir desta versao, a nota confirmada gera o Contas a Pagar automaticamente.
                    // Se for a vista ou vier sem vencimentos, o modulo cria uma parcela unica com vencimento hoje.
                    ContasPagarActivity.criarContasPorParcelamento(
                            this, notaId, fFornecedorNome, fFornecedorId,
                            fTotal, datas, fCaixaId);

                    String msg = "Nota confirmada com sucesso!\n\n" +
                            finalItens + " produto(s) tiveram o estoque atualizado.";
                    if (temParcelamento) {
                        msg += "\nCondicao de pagamento: " + fCondicao;
                        msg += "\nContas a pagar: " + datas.size() + " parcela(s) enviada(s) automaticamente.";
                    } else {
                        msg += "\nContas a pagar: 1 parcela enviada automaticamente.";
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Nota Confirmada")
                            .setMessage(msg)
                            .setPositiveButton("OK", (d, w) -> loadData())
                            .show();
                });

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    // =========================================================================
    // CANCELAR NOTA
    // =========================================================================

    private void cancelarNota(int notaId) {
        showLoading("Cancelando nota...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE notas_entrada SET status = 'cancelada' WHERE id = ? AND status = 'pendente'");
                ps.setInt(1, notaId);
                int rows = ps.executeUpdate();
                ps.close();

                hideLoading();
                if (rows > 0) {
                    showToast("Nota cancelada!");
                } else {
                    showError("Nao foi possivel cancelar a nota. Ela pode ja ter sido confirmada.");
                }
                loadData();

            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }
}
