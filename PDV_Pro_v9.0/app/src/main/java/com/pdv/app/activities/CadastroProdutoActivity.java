package com.pdv.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Produto;
import com.pdv.app.models.TipoProduto;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

public class CadastroProdutoActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Produto> adapter;
    private EditText etBusca;
    private Button btnNovo;
    private String fotoBase64Temp = null;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PRODUTOS_ACESSAR)) {
            return;
        }
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Produtos");

        etBusca = findViewById(R.id.etBusca);
        btnNovo = findViewById(R.id.btnNovo);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, item.getDescricao());
            String line2 = "Cod: " + FormatUtils.safeString(item.getCodigo())
                    + " | Venda: R$ " + FormatUtils.formatMoney(item.getPrecoVenda())
                    + " | Est: " + FormatUtils.formatQuantidade(item.getEstoque());
            String codBarras = item.getCodigoBarras();
            if (codBarras != null && !codBarras.isEmpty()) {
                line2 += "\nCod.Barras: " + codBarras;
            }
            String tipoDesc = item.getTipoProdutoDesc();
            if (tipoDesc != null && !tipoDesc.isEmpty()) {
                line2 += "\nTipo: " + tipoDesc;
            }
            holder.setText(R.id.tvLine2, line2);
            ImageView ivFoto = holder.find(R.id.ivFoto);
            if (ivFoto != null && item.getFotoBase64() != null && !item.getFotoBase64().isEmpty()) {
                ivFoto.setVisibility(View.VISIBLE);
                ImageUtils.loadBase64IntoImageView(ivFoto, item.getFotoBase64());
            } else if (ivFoto != null) {
                ivFoto.setVisibility(View.GONE);
            }

            // Botao Duplicar
            Button btnDuplicar = holder.find(R.id.btnDuplicar);
            if (btnDuplicar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.PRODUTOS_CRIAR)) {
                    btnDuplicar.setVisibility(View.VISIBLE);
                    btnDuplicar.setOnClickListener(v -> {
                        Produto duplicate = new Produto();
                        duplicate.setDescricao(item.getDescricao() + " (Cópia)");
                        duplicate.setUnidade(item.getUnidade());
                        duplicate.setPrecoCusto(item.getPrecoCusto());
                        duplicate.setPrecoVenda(item.getPrecoVenda());
                        duplicate.setEstoque(item.getEstoque());
                        duplicate.setEstoqueMinimo(item.getEstoqueMinimo());
                        duplicate.setTipoProdutoId(item.getTipoProdutoId());
                        duplicate.setTipoProdutoDesc(item.getTipoProdutoDesc());
                        duplicate.setFotoBase64(item.getFotoBase64());
                        // Nao copia ID, Codigo nem Codigo de Barras para forcar geracao de novos
                        showEditDialog(duplicate);
                    });
                } else {
                    btnDuplicar.setVisibility(View.GONE);
                }
            }

            // Botao Editar
            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.PRODUTOS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            // Botao Inativar
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.PRODUTOS_EXCLUIR)) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setOnClickListener(v -> {
                        showConfirm("Inativar", "Deseja inativar " + item.getDescricao() + "?",
                                () -> inativarProduto(item.getId()));
                    });
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }
        });
        recyclerView.setAdapter(adapter);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            fotoBase64Temp = ImageUtils.uriToBase64(this, uri);
                        }
                    }
                });

        PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.PRODUTOS_CRIAR);
        btnNovo.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.PRODUTOS_CRIAR)) {
                showEditDialog(null);
            }
        });
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });

        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String busca = etBusca.getText().toString().trim();
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String sql = "SELECT p.*, tp.descricao AS tipo_desc FROM produtos p LEFT JOIN tipos_produto tp ON p.tipo_produto_id = tp.id WHERE p.ativo = 1";
                if (!busca.isEmpty()) {
                    sql += " AND (p.descricao LIKE ? OR p.codigo LIKE ? OR p.codigo_barras LIKE ?)";
                }
                sql += " ORDER BY p.descricao";

                PreparedStatement ps = conn.prepareStatement(sql);
                if (!busca.isEmpty()) {
                    ps.setString(1, "%" + busca + "%");
                    ps.setString(2, "%" + busca + "%");
                    ps.setString(3, "%" + busca + "%");
                }

                ResultSet rs = ps.executeQuery();
                List<Produto> list = new ArrayList<>();
                while (rs.next()) {
                    Produto p = new Produto();
                    p.setId(rs.getInt("id"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setDescricao(rs.getString("descricao"));
                    p.setUnidade(rs.getString("unidade"));
                    p.setPrecoCusto(rs.getDouble("preco_custo"));
                    p.setPrecoVenda(rs.getDouble("preco_venda"));
                    p.setEstoque(rs.getDouble("estoque"));
                    p.setEstoqueMinimo(rs.getDouble("estoque_minimo"));
                    try {
                        p.setTipoProdutoId(rs.getInt("tipo_produto_id"));
                    } catch (Exception ignored) {}
                    try {
                        p.setTipoProdutoDesc(rs.getString("tipo_desc"));
                    } catch (Exception ignored) {}
                    try {
                        p.setCodigoBarras(rs.getString("codigo_barras"));
                    } catch (Exception ignored) {
                        p.setCodigoBarras("");
                    }
                    p.setFotoBase64(rs.getString("foto_base64"));
                    list.add(p);
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

    /**
     * Gera o proximo codigo sequencial para produtos.
     */
    private String gerarProximoCodigo(Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT MAX(CAST(codigo AS UNSIGNED)) AS max_codigo FROM produtos WHERE codigo REGEXP '^[0-9]+$'");
            int maxCodigo = 0;
            if (rs.next()) {
                maxCodigo = rs.getInt("max_codigo");
            }
            rs.close();
            stmt.close();

            int proximoCodigo = maxCodigo + 1;

            if (proximoCodigo < 1000) {
                return String.format("%03d", proximoCodigo);
            } else {
                return String.valueOf(proximoCodigo);
            }
        } catch (Exception e) {
            return "001";
        }
    }

    /**
     * Gera um EAN-13 unico verificando no banco de dados se ja nao existe.
     */
    private String gerarEan13Unico(Connection conn) {
        try {
            for (int tentativa = 0; tentativa < 50; tentativa++) {
                String ean = EanUtils.gerarEan13();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM produtos WHERE codigo_barras = ?");
                ps.setString(1, ean);
                ResultSet rs = ps.executeQuery();
                rs.next();
                int count = rs.getInt(1);
                rs.close();
                ps.close();
                if (count == 0) {
                    return ean;
                }
            }
        } catch (Exception e) {
        }
        return EanUtils.gerarEan13();
    }

    /**
     * Carrega os tipos de produto do banco de dados para popular o Spinner.
     */
    private void carregarTiposProduto(Spinner spinner, int tipoProdutoIdSelecionado) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, descricao FROM tipos_produto WHERE ativo = 1 ORDER BY descricao");
                ResultSet rs = ps.executeQuery();

                List<TipoProduto> tipos = new ArrayList<>();
                // Adicionar opcao vazia (nenhum tipo)
                TipoProduto semTipo = new TipoProduto();
                semTipo.setId(0);
                semTipo.setDescricao("-- Selecione o Tipo --");
                tipos.add(semTipo);

                int posicaoSelecionada = 0;
                int index = 1;
                while (rs.next()) {
                    TipoProduto tp = new TipoProduto();
                    tp.setId(rs.getInt("id"));
                    tp.setDescricao(rs.getString("descricao"));
                    tipos.add(tp);
                    if (tp.getId() == tipoProdutoIdSelecionado) {
                        posicaoSelecionada = index;
                    }
                    index++;
                }
                rs.close();
                ps.close();

                final int posFinal = posicaoSelecionada;
                runOnUiThread(() -> {
                    ArrayAdapter<TipoProduto> adapterSpinner = new ArrayAdapter<>(
                            CadastroProdutoActivity.this,
                            android.R.layout.simple_spinner_item,
                            tipos);
                    adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapterSpinner);
                    if (posFinal >= 0 && posFinal < tipos.size()) {
                        spinner.setSelection(posFinal);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    List<TipoProduto> fallback = new ArrayList<>();
                    TipoProduto semTipo = new TipoProduto();
                    semTipo.setId(0);
                    semTipo.setDescricao("-- Sem tipos cadastrados --");
                    fallback.add(semTipo);
                    ArrayAdapter<TipoProduto> adapterSpinner = new ArrayAdapter<>(
                            CadastroProdutoActivity.this,
                            android.R.layout.simple_spinner_item,
                            fallback);
                    adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinner.setAdapter(adapterSpinner);
                });
            }
        }).start();
    }

    private void showEditDialog(Produto produto) {
        fotoBase64Temp = produto != null ? produto.getFotoBase64() : null;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_produto, null);
        EditText etCodigo = dialogView.findViewById(R.id.etCodigo);
        EditText etCodigoBarras = dialogView.findViewById(R.id.etCodigoBarras);
        Button btnGerarEan = dialogView.findViewById(R.id.btnGerarEan);
        EditText etDescricao = dialogView.findViewById(R.id.etDescricao);
        Spinner spinnerTipoProduto = dialogView.findViewById(R.id.spinnerTipoProduto);
        EditText etUnidade = dialogView.findViewById(R.id.etUnidade);
        EditText etPrecoCusto = dialogView.findViewById(R.id.etPrecoCusto);
        EditText etPrecoVenda = dialogView.findViewById(R.id.etPrecoVenda);
        EditText etEstoque = dialogView.findViewById(R.id.etEstoque);
        EditText etEstoqueMin = dialogView.findViewById(R.id.etEstoqueMin);
        ImageView ivFoto = dialogView.findViewById(R.id.ivFoto);
        Button btnFoto = dialogView.findViewById(R.id.btnFoto);

        // Carregar tipos de produto no Spinner
        int tipoProdutoIdAtual = produto != null ? produto.getTipoProdutoId() : 0;
        carregarTiposProduto(spinnerTipoProduto, tipoProdutoIdAtual);

        if (produto != null) {
            etCodigo.setText(FormatUtils.safeString(produto.getCodigo()));
            etCodigoBarras.setText(FormatUtils.safeString(produto.getCodigoBarras()));
            etDescricao.setText(FormatUtils.safeString(produto.getDescricao()));
            etUnidade.setText(FormatUtils.safeString(produto.getUnidade()));
            etPrecoCusto.setText(FormatUtils.formatMoney(produto.getPrecoCusto()));
            etPrecoVenda.setText(FormatUtils.formatMoney(produto.getPrecoVenda()));
            etEstoque.setText(FormatUtils.formatQuantidade(produto.getEstoque()));
            etEstoqueMin.setText(FormatUtils.formatQuantidade(produto.getEstoqueMinimo()));
            if (produto.getFotoBase64() != null && !produto.getFotoBase64().isEmpty()) {
                ImageUtils.loadBase64IntoImageView(ivFoto, produto.getFotoBase64());
            }
        } else {
            etCodigo.setText("...");
            etCodigo.setEnabled(false);

            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    String novoCodigo = gerarProximoCodigo(conn);
                    runOnUiThread(() -> {
                        etCodigo.setText(novoCodigo);
                        etCodigo.setEnabled(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        etCodigo.setText("001");
                        etCodigo.setEnabled(true);
                    });
                }
            }).start();
        }

        // Botao para gerar EAN-13 automaticamente
        btnGerarEan.setOnClickListener(v -> {
            btnGerarEan.setEnabled(false);
            btnGerarEan.setText("...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    String ean = gerarEan13Unico(conn);
                    runOnUiThread(() -> {
                        etCodigoBarras.setText(ean);
                        btnGerarEan.setEnabled(true);
                        btnGerarEan.setText("Gerar");
                        showToast("EAN-13 gerado: " + ean);
                    });
                } catch (Exception e) {
                    String ean = EanUtils.gerarEan13();
                    runOnUiThread(() -> {
                        etCodigoBarras.setText(ean);
                        btnGerarEan.setEnabled(true);
                        btnGerarEan.setText("Gerar");
                        showToast("EAN-13 gerado: " + ean);
                    });
                }
            }).start();
        });

        btnFoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(produto == null ? "Novo Produto" : "Editar Produto")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    // Obter tipo de produto selecionado
                    int tipoProdutoId = 0;
                    Object selectedItem = spinnerTipoProduto.getSelectedItem();
                    if (selectedItem instanceof TipoProduto) {
                        tipoProdutoId = ((TipoProduto) selectedItem).getId();
                    }
                    saveProduto(
                            produto != null ? produto.getId() : 0,
                            etCodigo.getText().toString().trim(),
                            etCodigoBarras.getText().toString().trim(),
                            etDescricao.getText().toString().trim(),
                            etUnidade.getText().toString().trim(),
                            etPrecoCusto.getText().toString().trim(),
                            etPrecoVenda.getText().toString().trim(),
                            etEstoque.getText().toString().trim(),
                            etEstoqueMin.getText().toString().trim(),
                            tipoProdutoId
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveProduto(int id, String codigo, String codigoBarras, String descricao, String unidade,
                             String precoCusto, String precoVenda, String estoque, String estoqueMin,
                             int tipoProdutoId) {
        if (descricao.isEmpty()) {
            showError("Por favor, preencha o campo Descricao antes de salvar.");
            return;
        }

        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                String codigoFinal = codigo;
                if (codigoFinal.isEmpty() || codigoFinal.equals("...")) {
                    codigoFinal = gerarProximoCodigo(conn);
                }

                PreparedStatement ps;

                if (id == 0) {
                    ps = conn.prepareStatement("INSERT INTO produtos (codigo,codigo_barras,descricao,unidade,tipo_produto_id,preco_custo,preco_venda,estoque,estoque_minimo,foto_base64,ativo) VALUES (?,?,?,?,?,?,?,?,?,?,1)");
                } else {
                    ps = conn.prepareStatement("UPDATE produtos SET codigo=?,codigo_barras=?,descricao=?,unidade=?,tipo_produto_id=?,preco_custo=?,preco_venda=?,estoque=?,estoque_minimo=?,foto_base64=? WHERE id=?");
                }

                ps.setString(1, codigoFinal);
                ps.setString(2, codigoBarras.isEmpty() ? null : codigoBarras);
                ps.setString(3, descricao);
                ps.setString(4, unidade.isEmpty() ? "UN" : unidade);
                if (tipoProdutoId > 0) {
                    ps.setInt(5, tipoProdutoId);
                } else {
                    ps.setNull(5, Types.INTEGER);
                }
                ps.setDouble(6, FormatUtils.parseMoney(precoCusto));
                ps.setDouble(7, FormatUtils.parseMoney(precoVenda));
                ps.setDouble(8, FormatUtils.parseMoney(estoque));
                ps.setDouble(9, FormatUtils.parseMoney(estoqueMin));
                ps.setString(10, fotoBase64Temp);

                if (id > 0) {
                    ps.setInt(11, id);
                }

                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Produto salvo!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void inativarProduto(int id) {
        showLoading("Inativando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE produtos SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Produto inativado!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
