package com.pdv.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

public class ComandaDetalheActivity extends BaseActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 3001;

    private int comandaId;
    private int comandaNumero;
    private String comandaStatus = "aberta";
    private int clienteId = 0;
    private String clienteNome = "";

    private TextView tvTituloComanda, tvStatusDetalhe, tvClienteDetalhe, tvDataDetalhe, tvTotalComandaDetalhe;
    private EditText etCodigoBarrasComanda, etObservacaoComanda;
    private RecyclerView rvItensComanda;
    private GenericAdapter<ItemComanda> itensAdapter;
    private Button btnFecharComanda, btnAddItemComanda, btnEscolherClienteComanda, btnImprimirComanda;
    private LinearLayout llBotoesComanda;

    private List<ItemComanda> itensComanda = new ArrayList<>();
    private ActivityResultLauncher<Intent> scannerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comanda_detalhe);

        comandaId = getIntent().getIntExtra("comanda_id", 0);
        if (comandaId == 0) {
            showError("Comanda nao encontrada.");
            finish();
            return;
        }

        tvTituloComanda = findViewById(R.id.tvTituloComanda);
        tvStatusDetalhe = findViewById(R.id.tvStatusDetalhe);
        tvClienteDetalhe = findViewById(R.id.tvClienteDetalhe);
        tvDataDetalhe = findViewById(R.id.tvDataDetalhe);
        tvTotalComandaDetalhe = findViewById(R.id.tvTotalComandaDetalhe);
        etCodigoBarrasComanda = findViewById(R.id.etCodigoBarrasComanda);
        etObservacaoComanda = findViewById(R.id.etObservacaoComanda);
        rvItensComanda = findViewById(R.id.rvItensComanda);
        btnFecharComanda = findViewById(R.id.btnFecharComanda);
        btnAddItemComanda = findViewById(R.id.btnAddItemComanda);
        btnEscolherClienteComanda = findViewById(R.id.btnEscolherClienteComanda);
        btnImprimirComanda = findViewById(R.id.btnImprimirComanda);
        llBotoesComanda = findViewById(R.id.llBotoesComanda);

        ImageButton btnScanBarcode = findViewById(R.id.btnScanBarcodeComanda);
        ImageButton btnBuscarProduto = findViewById(R.id.btnBuscarProdutoComanda);

        // Scanner launcher
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String barcode = result.getData().getStringExtra(
                                BarcodeScannerActivity.EXTRA_BARCODE_RESULT);
                        if (barcode != null && !barcode.isEmpty()) {
                            etCodigoBarrasComanda.setText(barcode);
                            buscarPorCodigoBarras(barcode);
                        }
                    }
                });

        // Setup RecyclerView
        rvItensComanda.setLayoutManager(new LinearLayoutManager(this));
        itensAdapter = new GenericAdapter<>(R.layout.item_comanda_produto, (holder, item, pos) -> {
            holder.setText(R.id.tvProdutoComanda, item.getDescricaoProduto());
            holder.setText(R.id.tvQtdComanda, FormatUtils.formatQuantidade(item.getQuantidade()));
            holder.setText(R.id.tvPrecoComanda, "R$ " + FormatUtils.formatMoney(item.getPrecoUnitario()));
            holder.setText(R.id.tvItemTotalComanda, "R$ " + FormatUtils.formatMoney(item.getTotal()));

            ImageView ivFoto = holder.find(R.id.ivFotoProdutoComanda);
            if (ivFoto != null && item.getFotoBase64() != null && !item.getFotoBase64().isEmpty()) {
                ivFoto.setVisibility(View.VISIBLE);
                ImageUtils.loadBase64IntoImageView(ivFoto, item.getFotoBase64());
            } else if (ivFoto != null) {
                ivFoto.setVisibility(View.GONE);
            }

            TextView tvObs = holder.find(R.id.tvObsItemComanda);
            if (tvObs != null) {
                if (item.getObservacao() != null && !item.getObservacao().isEmpty()) {
                    tvObs.setVisibility(View.VISIBLE);
                    tvObs.setText("Obs: " + item.getObservacao());
                } else {
                    tvObs.setVisibility(View.GONE);
                }
            }
        });

        itensAdapter.setOnItemLongClickListener((item, pos) -> {
            if (!"aberta".equalsIgnoreCase(comandaStatus)) return;
            showConfirm("Remover Item", "Remover " + item.getDescricaoProduto() + " da comanda?", () -> {
                removerItemComanda(item.getId(), pos);
            });
        });
        rvItensComanda.setAdapter(itensAdapter);

        // Busca por codigo de barras
        etCodigoBarrasComanda.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String texto = etCodigoBarrasComanda.getText().toString().trim();
                if (!texto.isEmpty()) {
                    buscarPorCodigoBarras(texto);
                }
                return true;
            }
            return false;
        });

        btnBuscarProduto.setOnClickListener(v -> {
            String texto = etCodigoBarrasComanda.getText().toString().trim();
            if (!texto.isEmpty()) {
                buscarPorCodigoBarras(texto);
            } else {
                showToast("Digite um codigo ou descricao");
            }
        });

        btnScanBarcode.setOnClickListener(v -> abrirScanner());
        btnAddItemComanda.setOnClickListener(v -> adicionarItemLista());
        btnEscolherClienteComanda.setOnClickListener(v -> escolherCliente());
        btnImprimirComanda.setOnClickListener(v -> imprimirComanda());
        btnFecharComanda.setOnClickListener(v -> fecharComanda());

        carregarComanda();
    }

    private void abrirScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            Intent intent = new Intent(this, BarcodeScannerActivity.class);
            scannerLauncher.launch(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(this, BarcodeScannerActivity.class);
                scannerLauncher.launch(intent);
            } else {
                showError("Permissao de camera necessaria para usar o scanner.");
            }
        }
    }

    private void carregarComanda() {
        showLoading("Carregando comanda...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Carregar dados da comanda
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT c.*, cl.nome as cliente_nome FROM comandas c "
                                + "LEFT JOIN clientes cl ON c.cliente_id = cl.id WHERE c.id = ?");
                ps.setInt(1, comandaId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    comandaNumero = rs.getInt("numero");
                    clienteId = rs.getInt("cliente_id");
                    clienteNome = rs.getString("cliente_nome");
                    if (clienteNome == null) clienteNome = "";
                    comandaStatus = rs.getString("status");
                    String dataAbertura = rs.getString("data_abertura");
                    String observacao = rs.getString("observacao");
                    double total = rs.getDouble("total_itens");

                    runOnUiThread(() -> {
                        tvTituloComanda.setText("Comanda #" + comandaNumero);
                        tvStatusDetalhe.setText(comandaStatus != null ? comandaStatus.toUpperCase() : "ABERTA");
                        tvClienteDetalhe.setText("Cliente: " + (clienteNome.isEmpty() ? "Nao informado" : clienteNome));
                        tvDataDetalhe.setText("Aberta em: " + FormatUtils.formatDate(dataAbertura));
                        tvTotalComandaDetalhe.setText("Total: R$ " + FormatUtils.formatMoney(total));
                        if (observacao != null && !observacao.isEmpty()) {
                            etObservacaoComanda.setText(observacao);
                        }

                        boolean aberta = "aberta".equalsIgnoreCase(comandaStatus);
                        btnFecharComanda.setEnabled(aberta);
                        btnAddItemComanda.setEnabled(aberta);
                        etCodigoBarrasComanda.setEnabled(aberta);
                        btnFecharComanda.setText(aberta ? "Fechar e Pagar" : "Comanda " + comandaStatus);

                        if ("aberta".equalsIgnoreCase(comandaStatus)) {
                            tvStatusDetalhe.setTextColor(getResources().getColor(R.color.colorSuccess));
                        } else if ("fechada".equalsIgnoreCase(comandaStatus)) {
                            tvStatusDetalhe.setTextColor(getResources().getColor(R.color.accent_cyan));
                        } else {
                            tvStatusDetalhe.setTextColor(getResources().getColor(R.color.colorDanger));
                        }
                    });
                }
                rs.close();
                ps.close();

                // Carregar itens da comanda
                Statement stmt = conn.createStatement();
                ResultSet rsItens = stmt.executeQuery(
                        "SELECT ic.*, p.foto_base64 FROM itens_comanda ic "
                                + "LEFT JOIN produtos p ON ic.produto_id = p.id "
                                + "WHERE ic.comanda_id = " + comandaId + " ORDER BY ic.id");
                itensComanda.clear();
                while (rsItens.next()) {
                    ItemComanda item = new ItemComanda();
                    item.setId(rsItens.getInt("id"));
                    item.setComandaId(rsItens.getInt("comanda_id"));
                    item.setProdutoId(rsItens.getInt("produto_id"));
                    item.setDescricaoProduto(rsItens.getString("descricao_produto"));
                    item.setQuantidade(rsItens.getDouble("quantidade"));
                    item.setPrecoUnitario(rsItens.getDouble("preco_unitario"));
                    item.setTotal(rsItens.getDouble("total"));
                    item.setObservacao(rsItens.getString("observacao"));
                    item.setFotoBase64(rsItens.getString("foto_base64"));
                    itensComanda.add(item);
                }
                rsItens.close();
                stmt.close();

                hideLoading();
                runOnUiThread(() -> {
                    itensAdapter.setItems(itensComanda);
                    recalcularTotal();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void recalcularTotal() {
        double total = 0;
        for (ItemComanda item : itensComanda) {
            total += item.getTotal();
        }
        tvTotalComandaDetalhe.setText("Total: R$ " + FormatUtils.formatMoney(total));
    }

    private void buscarPorCodigoBarras(String busca) {
        if (!"aberta".equalsIgnoreCase(comandaStatus)) {
            showError("Esta comanda ja foi " + comandaStatus + ".\n\nNao e possivel adicionar itens.");
            return;
        }

        showLoading("Buscando produto...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                String sql = "SELECT id, codigo, descricao, preco_venda, estoque, foto_base64, codigo_barras " +
                        "FROM produtos WHERE ativo = 1 AND (" +
                        "codigo_barras = ? OR codigo = ? OR descricao LIKE ?" +
                        ") ORDER BY " +
                        "CASE WHEN codigo_barras = ? THEN 0 " +
                        "WHEN codigo = ? THEN 1 " +
                        "ELSE 2 END, descricao";

                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, busca);
                ps.setString(2, busca);
                ps.setString(3, "%" + busca + "%");
                ps.setString(4, busca);
                ps.setString(5, busca);

                ResultSet rs = ps.executeQuery();
                List<Produto> produtos = new ArrayList<>();
                while (rs.next()) {
                    Produto p = new Produto();
                    p.setId(rs.getInt("id"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setDescricao(rs.getString("descricao"));
                    p.setPrecoVenda(rs.getDouble("preco_venda"));
                    p.setEstoque(rs.getDouble("estoque"));
                    p.setFotoBase64(rs.getString("foto_base64"));
                    try { p.setCodigoBarras(rs.getString("codigo_barras")); } catch (Exception ignored) {}
                    produtos.add(p);
                }
                rs.close();
                ps.close();
                hideLoading();

                runOnUiThread(() -> {
                    if (produtos.isEmpty()) {
                        showError("Nenhum produto encontrado:\n\"" + busca + "\"");
                        etCodigoBarrasComanda.selectAll();
                    } else if (produtos.size() == 1) {
                        showQuantidadeObsDialog(produtos.get(0));
                        etCodigoBarrasComanda.setText("");
                    } else {
                        String[] nomes = new String[produtos.size()];
                        for (int i = 0; i < produtos.size(); i++) {
                            nomes[i] = produtos.get(i).getDescricao() + " - R$ " + FormatUtils.formatMoney(produtos.get(i).getPrecoVenda());
                        }
                        new AlertDialog.Builder(this)
                                .setTitle("Produtos encontrados (" + produtos.size() + ")")
                                .setItems(nomes, (d, w) -> {
                                    showQuantidadeObsDialog(produtos.get(w));
                                    etCodigoBarrasComanda.setText("");
                                })
                                .setNegativeButton("Cancelar", null)
                                .show();
                    }
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void adicionarItemLista() {
        if (!"aberta".equalsIgnoreCase(comandaStatus)) {
            showError("Esta comanda ja foi " + comandaStatus + ".");
            return;
        }

        showLoading("Carregando produtos...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, codigo, descricao, preco_venda, estoque, foto_base64 FROM produtos WHERE ativo = 1 ORDER BY descricao");
                List<Produto> produtos = new ArrayList<>();
                while (rs.next()) {
                    Produto p = new Produto();
                    p.setId(rs.getInt("id"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setDescricao(rs.getString("descricao"));
                    p.setPrecoVenda(rs.getDouble("preco_venda"));
                    p.setEstoque(rs.getDouble("estoque"));
                    p.setFotoBase64(rs.getString("foto_base64"));
                    produtos.add(p);
                }
                rs.close();
                stmt.close();
                hideLoading();

                runOnUiThread(() -> {
                    String[] nomes = new String[produtos.size()];
                    for (int i = 0; i < produtos.size(); i++) {
                        nomes[i] = produtos.get(i).getDescricao() + " - R$ " + FormatUtils.formatMoney(produtos.get(i).getPrecoVenda());
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Escolher Produto")
                            .setItems(nomes, (d, w) -> showQuantidadeObsDialog(produtos.get(w)))
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void showQuantidadeObsDialog(Produto produto) {
        View view = getLayoutInflater().inflate(R.layout.dialog_quantidade_obs, null);
        EditText etQtd = view.findViewById(R.id.etQuantidade);
        EditText etObs = view.findViewById(R.id.etObservacaoItem);
        etQtd.setText("1");

        new AlertDialog.Builder(this)
                .setTitle(produto.getDescricao() + "\nR$ " + FormatUtils.formatMoney(produto.getPrecoVenda()))
                .setView(view)
                .setPositiveButton("Adicionar", (d, w) -> {
                    double qtd = FormatUtils.parseMoney(etQtd.getText().toString());
                    if (qtd <= 0) qtd = 1;
                    String obs = etObs.getText().toString().trim();
                    adicionarItemNoBanco(produto, qtd, obs);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void adicionarItemNoBanco(Produto produto, double quantidade, String observacao) {
        showLoading("Adicionando item...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                double total = quantidade * produto.getPrecoVenda();

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO itens_comanda (comanda_id, produto_id, descricao_produto, quantidade, preco_unitario, total, observacao, data_hora) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())");
                ps.setInt(1, comandaId);
                ps.setInt(2, produto.getId());
                ps.setString(3, produto.getDescricao());
                ps.setDouble(4, quantidade);
                ps.setDouble(5, produto.getPrecoVenda());
                ps.setDouble(6, total);
                ps.setString(7, observacao.isEmpty() ? null : observacao);
                ps.executeUpdate();
                ps.close();

                // Atualizar total da comanda
                atualizarTotalComanda(conn);

                hideLoading();
                showToast(produto.getDescricao() + " adicionado!");
                runOnUiThread(() -> carregarComanda());
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void removerItemComanda(int itemId, int pos) {
        showLoading("Removendo item...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement("DELETE FROM itens_comanda WHERE id = ?");
                ps.setInt(1, itemId);
                ps.executeUpdate();
                ps.close();

                atualizarTotalComanda(conn);

                hideLoading();
                showToast("Item removido!");
                runOnUiThread(() -> carregarComanda());
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void atualizarTotalComanda(Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE comandas SET total_itens = (SELECT COALESCE(SUM(total), 0) FROM itens_comanda WHERE comanda_id = ?) WHERE id = ?");
        ps.setInt(1, comandaId);
        ps.setInt(2, comandaId);
        ps.executeUpdate();
        ps.close();
    }

    private void escolherCliente() {
        if (!"aberta".equalsIgnoreCase(comandaStatus)) return;

        showLoading("Carregando clientes...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM clientes WHERE ativo = 1 ORDER BY nome");
                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                nomes.add("Sem cliente");
                ids.add(0);
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    nomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
                hideLoading();

                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Escolher Cliente")
                            .setItems(nomes.toArray(new String[0]), (d, w) -> {
                                clienteId = ids.get(w);
                                clienteNome = nomes.get(w);
                                tvClienteDetalhe.setText("Cliente: " + clienteNome);
                                atualizarClienteComanda(clienteId);
                            })
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void atualizarClienteComanda(int novoClienteId) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE comandas SET cliente_id = ? WHERE id = ?");
                ps.setInt(1, novoClienteId);
                ps.setInt(2, comandaId);
                ps.executeUpdate();
                ps.close();
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void imprimirComanda() {
        new Thread(() -> {
            String cupom = gerarCupomComanda();
            PrinterManager pm = new PrinterManager(this);
            if (pm.isImpressoraConfigurada()) {
                boolean ok = pm.imprimirTexto(cupom);
                showToast(ok ? "Comanda impressa!" : "Erro na impressao");
            } else {
                runOnUiThread(() -> WhatsAppHelper.enviarCupom(this, cupom, null));
            }
        }).start();
    }

    private String gerarCupomComanda() {
        try {
            PrinterManager pm = new PrinterManager(this);
            int colunas = pm.getColunasTexto();
            String line = repeat("-", colunas);

            StringBuilder sb = new StringBuilder();
            sb.append(center("COMANDA #" + comandaNumero, colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append("Cliente: ").append(clienteNome.isEmpty() ? "Nao informado" : clienteNome).append("\n");
            sb.append("Status: ").append(comandaStatus != null ? comandaStatus.toUpperCase() : "").append("\n");
            sb.append(line).append("\n");
            sb.append("ITEM  DESCRICAO          QTD    TOTAL\n");
            sb.append(line).append("\n");

            double total = 0;
            int itemNum = 1;
            for (ItemComanda item : itensComanda) {
                String desc = item.getDescricaoProduto();
                if (desc != null && desc.length() > 18) desc = desc.substring(0, 18);
                sb.append(String.format("%-5d %-18s %5s %8s\n",
                        itemNum++,
                        desc != null ? desc : "",
                        FormatUtils.formatQuantidade(item.getQuantidade()),
                        FormatUtils.formatMoney(item.getTotal())));
                if (item.getObservacao() != null && !item.getObservacao().isEmpty()) {
                    sb.append("      > ").append(item.getObservacao()).append("\n");
                }
                total += item.getTotal();
            }

            sb.append(line).append("\n");
            sb.append(rightAlign("TOTAL: R$ " + FormatUtils.formatMoney(total), colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append(center("PDV Pro v8.0.0 - Comanda", colunas)).append("\n");
            sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

            return sb.toString();
        } catch (Exception e) {
            return "Erro ao gerar cupom: " + e.getMessage();
        }
    }

    private void fecharComanda() {
        if (!"aberta".equalsIgnoreCase(comandaStatus)) {
            showError("Esta comanda ja foi " + comandaStatus + ".");
            return;
        }

        if (itensComanda.isEmpty()) {
            showError("A comanda esta vazia.\n\nAdicione pelo menos um item antes de fechar.");
            return;
        }

        // v6.0.0 - Verificar se o caixa esta aberto antes de fechar comanda (gera venda)
        showLoading("Verificando caixa...");
        new Thread(() -> {
            boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
            hideLoading();
            runOnUiThread(() -> {
                if (!caixaAberto) {
                    PermissionHelper.mostrarCaixaFechado(this);
                    return;
                }
                prosseguirFecharComanda();
            });
        }).start();
    }

    /**
     * v6.0.0 - Prossegue com o fechamento da comanda apos verificar que o caixa esta aberto.
     */
    private void prosseguirFecharComanda() {
        // Verificacoes ja feitas em fecharComanda()

        // Salvar observacao antes de fechar
        String obs = etObservacaoComanda.getText().toString().trim();

        double totalComanda = 0;
        for (ItemComanda item : itensComanda) {
            totalComanda += item.getTotal();
        }

        final double taxaServico = TaxaServicoPreferences.cobrarComandas(this)
                ? TaxaServicoPreferences.calcularTaxa(totalComanda) : 0.0;
        final double finalTotal = totalComanda;
        final String finalObs = obs;

        showConfirm("Fechar Comanda",
                "Deseja fechar a comanda #" + comandaNumero + " e ir para o pagamento?\n\n"
                        + "Subtotal: R$ " + FormatUtils.formatMoney(totalComanda)
                        + (taxaServico > 0 ? "\nTaxa de servico (10%): R$ " + FormatUtils.formatMoney(taxaServico) : "")
                        + "\nTotal: R$ " + FormatUtils.formatMoney(totalComanda + taxaServico),
                () -> {
                    // Salvar observacao
                    new Thread(() -> {
                        try {
                            DatabaseHelper db = DatabaseHelper.getInstance(this);
                            Connection conn = db.getConnection();
                            PreparedStatement psObs = conn.prepareStatement(
                                    "UPDATE comandas SET observacao = ? WHERE id = ?");
                            psObs.setString(1, finalObs.isEmpty() ? null : finalObs);
                            psObs.setInt(2, comandaId);
                            psObs.executeUpdate();
                            psObs.close();
                        } catch (Exception ignored) {}
                    }).start();

                    // Enviar para tela de pagamento como uma venda
                    enviarParaPagamento(finalTotal, taxaServico);
                });
    }

    private void enviarParaPagamento(double totalComanda, double taxaServico) {
        Intent intent = new Intent(this, PagamentoActivity.class);
        intent.putExtra("total_bruto", totalComanda);
        intent.putExtra("desconto", 0.0);
        intent.putExtra("desconto_tipo", "valor");
        intent.putExtra("desconto_input", 0.0);
        intent.putExtra("acrescimo", taxaServico);
        intent.putExtra("acrescimo_tipo", taxaServico > 0 ? "porcentagem" : "valor");
        intent.putExtra("acrescimo_input", taxaServico > 0 ? TaxaServicoPreferences.PERCENTUAL : 0.0);
        intent.putExtra("total_liquido", totalComanda + taxaServico);
        intent.putExtra("cliente_id", clienteId);
        intent.putExtra("cliente_nome", clienteNome.isEmpty() ? "Cliente nao informado" : clienteNome);
        intent.putExtra("vendedor_id", 0);
        intent.putExtra("entregador_id", 0);
        intent.putExtra("observacao", "Comanda #" + comandaNumero + (etObservacaoComanda.getText().toString().trim().isEmpty() ? "" : " - " + etObservacaoComanda.getText().toString().trim()));
        intent.putExtra("num_itens", itensComanda.size());

        // Dados da comanda para fechar apos pagamento
        intent.putExtra("comanda_id", comandaId);
        intent.putExtra("is_comanda", true);

        for (int i = 0; i < itensComanda.size(); i++) {
            ItemComanda item = itensComanda.get(i);
            intent.putExtra("item_produto_id_" + i, item.getProdutoId());
            intent.putExtra("item_descricao_" + i, item.getDescricaoProduto());
            intent.putExtra("item_qtd_" + i, item.getQuantidade());
            intent.putExtra("item_preco_" + i, item.getPrecoUnitario());
            intent.putExtra("item_total_" + i, item.getTotal());
        }

        startActivityForResult(intent, 200);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK) {
            // Pagamento concluido - fechar a comanda
            fecharComandaNoBanco();
        }
    }

    private void fecharComandaNoBanco() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE comandas SET status = 'fechada', data_fechamento = NOW() WHERE id = ?");
                ps.setInt(1, comandaId);
                ps.executeUpdate();
                ps.close();

                runOnUiThread(() -> {
                    showToast("Comanda #" + comandaNumero + " fechada com sucesso!");
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
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
