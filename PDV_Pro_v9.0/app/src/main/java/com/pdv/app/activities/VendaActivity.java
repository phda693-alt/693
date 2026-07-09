package com.pdv.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.util.Map;
import java.util.LinkedHashMap;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.ErrorHandler;

public class VendaActivity extends BaseActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 2001;

    private TextView tvCliente, tvTotal, tvDesconto, tvAcrescimo, tvSubtotal;
    private RecyclerView rvCarrinho;
    private GenericAdapter<ItemVenda> carrinhoAdapter;
    private Spinner spDescontoTipo, spAcrescimoTipo;
    private EditText etDescontoValor, etAcrescimoValor, etObservacao, etCodigoBarras;
    private Button btnAddItem, btnFinalizar;
    private ImageButton btnScanBarcode, btnBuscarProduto;

    private List<ItemVenda> carrinho = new ArrayList<>();
    private int clienteId = 0;
    private String clienteNome = "Cliente nao informado";
    private int vendedorId = 0;
    private int entregadorId = 0;

    private ActivityResultLauncher<Intent> scannerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_venda);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.VENDAS_ACESSAR)) {
            return;
        }

        tvCliente = findViewById(R.id.tvCliente);
        tvTotal = findViewById(R.id.tvTotal);
        tvDesconto = findViewById(R.id.tvDesconto);
        tvAcrescimo = findViewById(R.id.tvAcrescimo);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        rvCarrinho = findViewById(R.id.rvCarrinho);
        spDescontoTipo = findViewById(R.id.spDescontoTipo);
        spAcrescimoTipo = findViewById(R.id.spAcrescimoTipo);
        etDescontoValor = findViewById(R.id.etDescontoValor);
        etAcrescimoValor = findViewById(R.id.etAcrescimoValor);
        etObservacao = findViewById(R.id.etObservacao);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnFinalizar = findViewById(R.id.btnFinalizar);
        etCodigoBarras = findViewById(R.id.etCodigoBarras);
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnBuscarProduto = findViewById(R.id.btnBuscarProduto);

        // Registrar launcher para resultado do scanner
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String barcode = result.getData().getStringExtra(
                                BarcodeScannerActivity.EXTRA_BARCODE_RESULT);
                        String format = result.getData().getStringExtra(
                                BarcodeScannerActivity.EXTRA_BARCODE_FORMAT);
                        if (barcode != null && !barcode.isEmpty()) {
                            etCodigoBarras.setText(barcode);
                            buscarPorCodigoBarras(barcode);
                        }
                    }
                });

        // Spinners for discount/surcharge type
        String[] tipos = {"Valor (R$)", "Porcentagem (%)"};
        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tipos);
        tipoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDescontoTipo.setAdapter(tipoAdapter);
        spAcrescimoTipo.setAdapter(tipoAdapter);

        rvCarrinho.setLayoutManager(new LinearLayoutManager(this));
        carrinhoAdapter = new GenericAdapter<>(R.layout.item_carrinho, (holder, item, pos) -> {
            // v6.7.5 - Exibir descricao do produto com adicionais
            String descExibir = item.getDescricaoProduto();
            String adDesc = item.getAdicionaisDescricao();
            if (!adDesc.isEmpty()) {
                descExibir += "\n  + " + adDesc;
            }
            holder.setText(R.id.tvProduto, descExibir);
            holder.setText(R.id.tvQtd, FormatUtils.formatQuantidade(item.getQuantidade()));
            holder.setText(R.id.tvPreco, "R$ " + FormatUtils.formatMoney(item.getPrecoUnitario()));
            // v6.7.5 - Total inclui adicionais
            holder.setText(R.id.tvItemTotal, "R$ " + FormatUtils.formatMoney(item.getTotalComAdicionais()));
            ImageView ivFoto = holder.find(R.id.ivFotoProduto);
            if (ivFoto != null && item.getFotoBase64() != null && !item.getFotoBase64().isEmpty()) {
                ivFoto.setVisibility(View.VISIBLE);
                ImageUtils.loadBase64IntoImageView(ivFoto, item.getFotoBase64());
            } else if (ivFoto != null) {
                ivFoto.setVisibility(View.GONE);
            }
        });
        carrinhoAdapter.setOnItemLongClickListener((item, pos) -> {
            showConfirm("Remover", "Remover " + item.getDescricaoProduto() + " do carrinho?", () -> {
                // Auditoria: o carrinho vive apenas em memoria, entao remover um item aqui
                // NAO dispara os triggers de banco. Registramos explicitamente para que a
                // retirada de itens antes de finalizar a venda nao fique sem rastro.
                registrarRemocaoItemCarrinho(item);

                carrinho.remove(pos);
                carrinhoAdapter.setItems(carrinho);
                recalcular();
            });
        });
        rvCarrinho.setAdapter(carrinhoAdapter);

        // Real-time recalculation
        TextWatcher recalcWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) { recalcular(); }
        };
        etDescontoValor.addTextChangedListener(recalcWatcher);
        etAcrescimoValor.addTextChangedListener(recalcWatcher);
        spDescontoTipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { recalcular(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        spAcrescimoTipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { recalcular(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });

        // Busca por codigo de barras ao pressionar Enter/Search no teclado
        etCodigoBarras.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String texto = etCodigoBarras.getText().toString().trim();
                if (!texto.isEmpty()) {
                    buscarPorCodigoBarras(texto);
                }
                return true;
            }
            return false;
        });

        // Botao de buscar produto por texto/codigo
        btnBuscarProduto.setOnClickListener(v -> {
            String texto = etCodigoBarras.getText().toString().trim();
            if (!texto.isEmpty()) {
                buscarPorCodigoBarras(texto);
            } else {
                showToast("Digite um codigo de barras ou descricao");
            }
        });

        // Botao de scanner de codigo de barras
        btnScanBarcode.setOnClickListener(v -> abrirScanner());

        // Controlar visibilidade/habilitacao baseado em permissoes
        View btnCliente = findViewById(R.id.btnEscolherCliente);
        View btnVendedor = findViewById(R.id.btnEscolherVendedor);
        View btnEntregador = findViewById(R.id.btnEscolherEntregador);

        PermissionHelper.controlarVisibilidade(this, btnCliente, PermissionConstants.VENDAS_ESCOLHER_CLIENTE);
        PermissionHelper.controlarVisibilidade(this, btnVendedor, PermissionConstants.VENDAS_ESCOLHER_VENDEDOR);
        PermissionHelper.controlarVisibilidade(this, btnEntregador, PermissionConstants.VENDAS_ESCOLHER_ENTREGADOR);
        PermissionHelper.controlarHabilitacao(this, etDescontoValor, PermissionConstants.VENDAS_APLICAR_DESCONTO);
        PermissionHelper.controlarHabilitacao(this, spDescontoTipo, PermissionConstants.VENDAS_APLICAR_DESCONTO);
        PermissionHelper.controlarHabilitacao(this, etAcrescimoValor, PermissionConstants.VENDAS_APLICAR_ACRESCIMO);
        PermissionHelper.controlarHabilitacao(this, spAcrescimoTipo, PermissionConstants.VENDAS_APLICAR_ACRESCIMO);

        btnCliente.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.VENDAS_ESCOLHER_CLIENTE)) escolherCliente();
        });
        btnVendedor.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.VENDAS_ESCOLHER_VENDEDOR)) escolherVendedor();
        });
        btnEntregador.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.VENDAS_ESCOLHER_ENTREGADOR)) escolherEntregador();
        });
        btnAddItem.setOnClickListener(v -> adicionarItem());
        btnFinalizar.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.VENDAS_CRIAR)) finalizarVenda();
        });

        tvCliente.setText("Cliente: " + clienteNome);
    }

    /**
     * Abre o scanner de codigo de barras usando a camera.
     * Verifica permissao de camera antes de abrir.
     */
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
                showError("Permissao de camera necessaria para usar o scanner de codigo de barras.\n\n"
                        + "Voce pode digitar o codigo manualmente no campo de busca.");
            }
        }
    }

    /**
     * Busca produto por codigo de barras, codigo interno ou descricao.
     * Se encontrar exatamente 1 produto, adiciona direto ao carrinho.
     * Se encontrar varios, mostra lista para o usuario escolher.
     * Se nao encontrar, mostra mensagem de erro.
     */
    private void buscarPorCodigoBarras(String busca) {
        showLoading("Buscando produto...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // v6.7.5 - Incluir tipo_produto_id na busca para adicionais
                String sql = "SELECT id, codigo, descricao, preco_venda, estoque, foto_base64, codigo_barras, tipo_produto_id " +
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
                    try {
                        p.setCodigoBarras(rs.getString("codigo_barras"));
                    } catch (Exception ignored) {
                        p.setCodigoBarras("");
                    }
                    try {
                        p.setTipoProdutoId(rs.getInt("tipo_produto_id"));
                    } catch (Exception ignored) {}
                    produtos.add(p);
                }
                rs.close();
                ps.close();
                hideLoading();

                runOnUiThread(() -> {
                    if (produtos.isEmpty()) {
                        showError("Nenhum produto encontrado com o codigo ou descricao:\n\n\"" + busca + "\"\n\n"
                                + "Verifique se o codigo de barras esta cadastrado no produto.");
                        etCodigoBarras.selectAll();
                        etCodigoBarras.requestFocus();
                    } else if (produtos.size() == 1) {
                        // Produto unico encontrado - adicionar direto ao carrinho
                        Produto p = produtos.get(0);
                        showToast("Produto encontrado: " + p.getDescricao());
                        showQuantidadeDialog(p);
                        etCodigoBarras.setText("");
                    } else {
                        // Multiplos produtos encontrados - mostrar lista
                        String[] nomes = new String[produtos.size()];
                        for (int i = 0; i < produtos.size(); i++) {
                            Produto p = produtos.get(i);
                            String codBarras = p.getCodigoBarras();
                            nomes[i] = p.getDescricao() + " - R$ " + FormatUtils.formatMoney(p.getPrecoVenda());
                            if (codBarras != null && !codBarras.isEmpty()) {
                                nomes[i] += "\n  CB: " + codBarras;
                            }
                        }

                        new AlertDialog.Builder(this)
                                .setTitle("Produtos encontrados (" + produtos.size() + ")")
                                .setItems(nomes, (d, w) -> {
                                    Produto p = produtos.get(w);
                                    showQuantidadeDialog(p);
                                    etCodigoBarras.setText("");
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

    /**
     * Registra na trilha de auditoria a remocao de um item do carrinho de venda.
     *
     * <p>Diferente das vendas ja persistidas (que sao auditadas pelos triggers de
     * banco), o carrinho e mantido apenas em memoria ate a finalizacao. Sem este
     * registro, um operador poderia adicionar um item, remove-lo e finalizar a
     * venda sem ele, sem deixar qualquer rastro. Aqui gravamos produto,
     * quantidade e valores para permitir a fiscalizacao dessa acao.</p>
     */
    private void registrarRemocaoItemCarrinho(ItemVenda item) {
        if (item == null) return;
        StringBuilder detalhes = new StringBuilder();
        detalhes.append("Item removido do carrinho: ").append(item.getDescricaoProduto());
        String adicionais = item.getAdicionaisDescricao();
        if (adicionais != null && !adicionais.isEmpty()) {
            detalhes.append(" (adicionais: ").append(adicionais).append(")");
        }
        detalhes.append(" | Qtd: ").append(FormatUtils.formatQuantidade(item.getQuantidade()))
                .append(" | Unit.: R$ ").append(FormatUtils.formatMoney(item.getPrecoUnitario()))
                .append(" | Total: R$ ").append(FormatUtils.formatMoney(item.getTotalComAdicionais()));
        if (clienteId > 0) {
            detalhes.append(" | Cliente: ").append(clienteNome).append(" (#").append(clienteId).append(")");
        }
        UserActionLogger.log(this, "REMOVER_ITEM_CARRINHO", "Venda", detalhes.toString());
    }

    private void recalcular() {
        double totalBruto = 0;
        for (ItemVenda item : carrinho) {
            // v6.7.5 - Total inclui adicionais
            totalBruto += item.getTotalComAdicionais();
        }

        double descontoInput = FormatUtils.parseMoney(etDescontoValor.getText().toString());
        double acrescimoInput = FormatUtils.parseMoney(etAcrescimoValor.getText().toString());

        double desconto = 0, acrescimo = 0;
        if (spDescontoTipo.getSelectedItemPosition() == 1) { // Percentage
            desconto = totalBruto * descontoInput / 100.0;
        } else {
            desconto = descontoInput;
        }
        if (spAcrescimoTipo.getSelectedItemPosition() == 1) {
            acrescimo = totalBruto * acrescimoInput / 100.0;
        } else {
            acrescimo = acrescimoInput;
        }

        double subtotal = totalBruto - desconto + acrescimo;
        if (subtotal < 0) subtotal = 0;

        tvTotal.setText("Total: R$ " + FormatUtils.formatMoney(totalBruto));
        tvDesconto.setText("Desconto: -R$ " + FormatUtils.formatMoney(desconto));
        tvAcrescimo.setText("Acrescimo: +R$ " + FormatUtils.formatMoney(acrescimo));
        tvSubtotal.setText("Subtotal: R$ " + FormatUtils.formatMoney(subtotal));
    }

    private void escolherCliente() {
        showLoading("Carregando clientes...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM clientes WHERE ativo = 1 ORDER BY nome");
                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                nomes.add("Cliente nao informado");
                ids.add(0);
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    nomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
                hideLoading();

                runOnUiThread(() -> {
                    String[] items = nomes.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Escolher Cliente")
                            .setItems(items, (d, w) -> {
                                clienteId = ids.get(w);
                                clienteNome = nomes.get(w);
                                tvCliente.setText("Cliente: " + clienteNome);
                            })
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void escolherVendedor() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM vendedores WHERE ativo = 1 ORDER BY nome");
                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                nomes.add("Sem vendedor");
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
                            .setTitle("Escolher Vendedor")
                            .setItems(nomes.toArray(new String[0]), (d, w) -> {
                                vendedorId = ids.get(w);
                                showToast("Vendedor: " + nomes.get(w));
                            })
                            .show();
                });
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_VENDA); }
        }).start();
    }

    private void escolherEntregador() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM entregadores WHERE ativo = 1 ORDER BY nome");
                List<String> nomes = new ArrayList<>();
                List<Integer> ids = new ArrayList<>();
                nomes.add("Sem entregador");
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
                            .setTitle("Escolher Entregador")
                            .setItems(nomes.toArray(new String[0]), (d, w) -> {
                                entregadorId = ids.get(w);
                                showToast("Entregador: " + nomes.get(w));
                            })
                            .show();
                });
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_VENDA); }
        }).start();
    }

    private void adicionarItem() {
        showLoading("Carregando produtos...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                // v6.7.5 - Incluir tipo_produto_id na consulta
                ResultSet rs = stmt.executeQuery("SELECT id, codigo, descricao, preco_venda, estoque, foto_base64, tipo_produto_id FROM produtos WHERE ativo = 1 ORDER BY descricao");
                List<Produto> produtos = new ArrayList<>();
                while (rs.next()) {
                    Produto p = new Produto();
                    p.setId(rs.getInt("id"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setDescricao(rs.getString("descricao"));
                    p.setPrecoVenda(rs.getDouble("preco_venda"));
                    p.setEstoque(rs.getDouble("estoque"));
                    p.setFotoBase64(rs.getString("foto_base64"));
                    try {
                        p.setTipoProdutoId(rs.getInt("tipo_produto_id"));
                    } catch (Exception ignored) {}
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
                            .setItems(nomes, (d, w) -> {
                                Produto p = produtos.get(w);
                                showQuantidadeDialog(p);
                            })
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void showQuantidadeDialog(Produto produto) {
        View view = getLayoutInflater().inflate(R.layout.dialog_quantidade, null);
        EditText etQtd = view.findViewById(R.id.etQuantidade);
        etQtd.setText("1");

        new AlertDialog.Builder(this)
                .setTitle("Quantidade - " + produto.getDescricao())
                .setView(view)
                .setPositiveButton("Adicionar", (d, w) -> {
                    double qtd = FormatUtils.parseMoney(etQtd.getText().toString());
                    if (qtd <= 0) qtd = 1;

                    final double finalQtd = qtd;
                    // v6.7.5 - Verificar se o produto tem adicionais vinculados ao tipo
                    verificarAdicionaisEAdicionar(produto, finalQtd);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * v6.7.5 - Verifica se o produto possui tipo com adicionais vinculados.
     * Se sim, exibe dialogo para o usuario escolher os adicionais.
     * Se nao, adiciona o produto diretamente ao carrinho.
     */
    private void verificarAdicionaisEAdicionar(Produto produto, double quantidade) {
        int tipoProdutoId = produto.getTipoProdutoId();
        if (tipoProdutoId <= 0) {
            // Produto sem tipo - adicionar direto ao carrinho
            adicionarAoCarrinho(produto, quantidade, new ArrayList<>());
            return;
        }

        // Buscar adicionais vinculados ao tipo do produto
        showLoading("Verificando adicionais...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.id, a.descricao, a.preco FROM tipo_produto_adicionais tpa " +
                    "INNER JOIN adicionais a ON tpa.adicional_id = a.id " +
                    "WHERE tpa.tipo_produto_id = ? AND a.ativo = 1 ORDER BY a.descricao");
                ps.setInt(1, tipoProdutoId);
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
                        adicionarAoCarrinho(produto, quantidade, new ArrayList<>());
                    } else {
                        // Mostrar dialogo para escolher adicionais
                        mostrarDialogoAdicionais(produto, quantidade, adicionaisDisponiveis);
                    }
                });
            } catch (Exception e) {
                hideLoading();
                // Em caso de erro, adicionar sem adicionais
                runOnUiThread(() -> adicionarAoCarrinho(produto, quantidade, new ArrayList<>()));
            }
        }).start();
    }

    /**
     * v6.7.5 - Exibe dialogo com checkboxes para o usuario escolher os adicionais.
     */
    private void mostrarDialogoAdicionais(Produto produto, double quantidade, List<Map<String, Object>> adicionaisDisponiveis) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        // Titulo informativo
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Selecione os adicionais para:\n" + produto.getDescricao());
        tvInfo.setTextColor(0xFF00BCD4);
        tvInfo.setTextSize(14);
        tvInfo.setPadding(0, 0, 0, 16);
        container.addView(tvInfo);

        // Criar checkboxes para cada adicional
        List<CheckBox> checkboxes = new ArrayList<>();
        for (Map<String, Object> ad : adicionaisDisponiveis) {
            String descricao = (String) ad.get("descricao");
            double preco = (Double) ad.get("preco");

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
                        Map<String, Object> adData = (Map<String, Object>) c.getTag();
                        totalAd += (Double) adData.get("preco");
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
                    List<ItemVenda.AdicionalSelecionado> selecionados = new ArrayList<>();
                    for (CheckBox cb : checkboxes) {
                        if (cb.isChecked()) {
                            Map<String, Object> adData = (Map<String, Object>) cb.getTag();
                            int adId = ((Number) adData.get("id")).intValue();
                            String adDesc = (String) adData.get("descricao");
                            double adPreco = (Double) adData.get("preco");
                            selecionados.add(new ItemVenda.AdicionalSelecionado(adId, adDesc, adPreco));
                        }
                    }
                    adicionarAoCarrinho(produto, quantidade, selecionados);
                })
                .setNegativeButton("Sem adicionais", (d, w) -> {
                    // Adicionar sem adicionais
                    adicionarAoCarrinho(produto, quantidade, new ArrayList<>());
                })
                .show();
    }

    /**
     * v6.7.5 - Adiciona o produto ao carrinho com os adicionais selecionados.
     */
    private void adicionarAoCarrinho(Produto produto, double quantidade, List<ItemVenda.AdicionalSelecionado> adicionais) {
        ItemVenda item = new ItemVenda();
        item.setProdutoId(produto.getId());
        item.setDescricaoProduto(produto.getDescricao());
        item.setPrecoUnitario(produto.getPrecoVenda());
        item.setQuantidade(quantidade);
        item.setTotal(quantidade * produto.getPrecoVenda());
        item.setFotoBase64(produto.getFotoBase64());
        item.setAdicionais(adicionais);

        carrinho.add(item);
        carrinhoAdapter.setItems(carrinho);
        recalcular();
    }

    private void finalizarVenda() {
        if (carrinho.isEmpty()) {
            showError("O carrinho esta vazio.\n\nAdicione pelo menos um produto antes de prosseguir.");
            return;
        }

        // v6.0.0 - Verificar se o caixa esta aberto antes de finalizar venda
        showLoading("Verificando caixa...");
        new Thread(() -> {
            boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
            hideLoading();
            runOnUiThread(() -> {
                if (!caixaAberto) {
                    PermissionHelper.mostrarCaixaFechado(this);
                    return;
                }
                // Caixa aberto - prosseguir com a venda
                prosseguirFinalizarVenda();
            });
        }).start();
    }

    /**
     * v6.0.0 - Prossegue com a finalizacao da venda apos verificar que o caixa esta aberto.
     * v6.7.5 - Inclui adicionais no total e passa dados de adicionais para PagamentoActivity.
     */
    private void prosseguirFinalizarVenda() {

        double totalBruto = 0;
        for (ItemVenda item : carrinho) {
            // v6.7.5 - Total inclui adicionais
            totalBruto += item.getTotalComAdicionais();
        }

        double descontoInput = FormatUtils.parseMoney(etDescontoValor.getText().toString());
        double acrescimoInput = FormatUtils.parseMoney(etAcrescimoValor.getText().toString());
        String descontoTipo = spDescontoTipo.getSelectedItemPosition() == 1 ? "porcentagem" : "valor";
        String acrescimoTipo = spAcrescimoTipo.getSelectedItemPosition() == 1 ? "porcentagem" : "valor";

        double desconto = descontoTipo.equals("porcentagem") ? totalBruto * descontoInput / 100.0 : descontoInput;
        double acrescimo = acrescimoTipo.equals("porcentagem") ? totalBruto * acrescimoInput / 100.0 : acrescimoInput;
        double totalLiquido = totalBruto - desconto + acrescimo;

        Intent intent = new Intent(this, PagamentoActivity.class);
        intent.putExtra("total_bruto", totalBruto);
        intent.putExtra("desconto", desconto);
        intent.putExtra("desconto_tipo", descontoTipo);
        intent.putExtra("desconto_input", descontoInput);
        intent.putExtra("acrescimo", acrescimo);
        intent.putExtra("acrescimo_tipo", acrescimoTipo);
        intent.putExtra("acrescimo_input", acrescimoInput);
        intent.putExtra("total_liquido", totalLiquido);
        intent.putExtra("cliente_id", clienteId);
        intent.putExtra("cliente_nome", clienteNome);
        intent.putExtra("vendedor_id", vendedorId);
        intent.putExtra("entregador_id", entregadorId);
        intent.putExtra("observacao", etObservacao.getText().toString().trim());
        intent.putExtra("num_itens", carrinho.size());

        for (int i = 0; i < carrinho.size(); i++) {
            ItemVenda item = carrinho.get(i);
            intent.putExtra("item_produto_id_" + i, item.getProdutoId());
            intent.putExtra("item_descricao_" + i, item.getDescricaoProduto());
            intent.putExtra("item_qtd_" + i, item.getQuantidade());
            intent.putExtra("item_preco_" + i, item.getPrecoUnitario());
            // v6.7.5 - Total do item inclui adicionais
            intent.putExtra("item_total_" + i, item.getTotalComAdicionais());

            // v6.7.5 - Passar dados dos adicionais selecionados
            List<ItemVenda.AdicionalSelecionado> adicionais = item.getAdicionais();
            intent.putExtra("item_num_adicionais_" + i, adicionais != null ? adicionais.size() : 0);
            if (adicionais != null) {
                for (int j = 0; j < adicionais.size(); j++) {
                    ItemVenda.AdicionalSelecionado ad = adicionais.get(j);
                    intent.putExtra("item_" + i + "_ad_id_" + j, ad.getAdicionalId());
                    intent.putExtra("item_" + i + "_ad_desc_" + j, ad.getDescricao());
                    intent.putExtra("item_" + i + "_ad_preco_" + j, ad.getPreco());
                }
            }
        }

        startActivityForResult(intent, 100);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Venda finalizada, limpar carrinho
            carrinho.clear();
            carrinhoAdapter.setItems(carrinho);
            clienteId = 0;
            clienteNome = "Cliente nao informado";
            vendedorId = 0;
            entregadorId = 0;
            tvCliente.setText("Cliente: " + clienteNome);
            etDescontoValor.setText("");
            etAcrescimoValor.setText("");
            etObservacao.setText("");
            etCodigoBarras.setText("");
            recalcular();
        }
    }
}
