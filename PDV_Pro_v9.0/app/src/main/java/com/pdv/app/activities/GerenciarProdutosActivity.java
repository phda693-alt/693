package com.pdv.app.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

/**
 * Tela de Gerenciamento de Produtos.
 * Exibe TODOS os produtos cadastrados (ativos e inativos) com opcoes de:
 * - Visualizar detalhes completos
 * - Filtrar por status (Todos / Ativos / Inativos)
 * - Buscar por descricao, codigo ou codigo de barras
 * - Editar qualquer campo do produto
 * - Inativar / Reativar produtos
 */
public class GerenciarProdutosActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Produto> adapter;
    private EditText etBusca;
    private TextView tvContador, tvEmpty;
    private Button btnFiltroTodos, btnFiltroAtivos, btnFiltroInativos;
    private String fotoBase64Temp = null;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    // Filtro: 0 = todos, 1 = ativos, 2 = inativos
    private int filtroAtual = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gerenciar_produtos);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR)) {
            return;
        }
        etBusca = findViewById(R.id.etBusca);
        tvContador = findViewById(R.id.tvContador);
        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        btnFiltroTodos = findViewById(R.id.btnFiltroTodos);
        btnFiltroAtivos = findViewById(R.id.btnFiltroAtivos);
        btnFiltroInativos = findViewById(R.id.btnFiltroInativos);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_produto_gerenciar, (holder, item, pos) -> {
            // Descricao
            holder.setText(R.id.tvDescricao, item.getDescricao());

            // Status badge
            TextView tvStatus = holder.find(R.id.tvStatus);
            if (tvStatus != null) {
                if (item.isAtivo()) {
                    tvStatus.setText("ATIVO");
                    tvStatus.setTextColor(Color.parseColor("#00E676"));
                    GradientDrawable bgAtivo = new GradientDrawable();
                    bgAtivo.setShape(GradientDrawable.RECTANGLE);
                    bgAtivo.setCornerRadius(8f);
                    bgAtivo.setColor(Color.parseColor("#1B3A1B"));
                    bgAtivo.setStroke(1, Color.parseColor("#00E676"));
                    tvStatus.setBackground(bgAtivo);
                } else {
                    tvStatus.setText("INATIVO");
                    tvStatus.setTextColor(Color.parseColor("#FF5252"));
                    GradientDrawable bgInativo = new GradientDrawable();
                    bgInativo.setShape(GradientDrawable.RECTANGLE);
                    bgInativo.setCornerRadius(8f);
                    bgInativo.setColor(Color.parseColor("#3A1B1B"));
                    bgInativo.setStroke(1, Color.parseColor("#FF5252"));
                    tvStatus.setBackground(bgInativo);
                }
            }

            // Codigo
            String codInfo = "Cod: " + FormatUtils.safeString(item.getCodigo());
            if (item.getUnidade() != null && !item.getUnidade().isEmpty()) {
                codInfo += " | Unid: " + item.getUnidade();
            }
            holder.setText(R.id.tvCodigo, codInfo);

            // Precos
            String precos = "Custo: R$ " + FormatUtils.formatMoney(item.getPrecoCusto())
                    + "  |  Venda: R$ " + FormatUtils.formatMoney(item.getPrecoVenda());
            holder.setText(R.id.tvPrecos, precos);

            // Estoque
            String estoque = "Estoque: " + FormatUtils.formatQuantidade(item.getEstoque())
                    + " | Min: " + FormatUtils.formatQuantidade(item.getEstoqueMinimo());
            TextView tvEstoque = holder.find(R.id.tvEstoque);
            if (tvEstoque != null) {
                tvEstoque.setText(estoque);
                // Destacar estoque baixo
                if (item.getEstoque() <= item.getEstoqueMinimo() && item.getEstoque() > 0) {
                    tvEstoque.setTextColor(Color.parseColor("#FFD740")); // Amarelo
                } else if (item.getEstoque() <= 0) {
                    tvEstoque.setTextColor(Color.parseColor("#FF5252")); // Vermelho
                } else {
                    tvEstoque.setTextColor(Color.parseColor("#B0BEC5")); // Normal
                }
            }

            // Codigo de barras
            TextView tvCodBarras = holder.find(R.id.tvCodigoBarras);
            if (tvCodBarras != null) {
                String codBarras = item.getCodigoBarras();
                if (codBarras != null && !codBarras.isEmpty()) {
                    tvCodBarras.setVisibility(View.VISIBLE);
                    tvCodBarras.setText("Cod.Barras: " + codBarras);
                } else {
                    tvCodBarras.setVisibility(View.GONE);
                }
            }

            // Foto
            ImageView ivFoto = holder.find(R.id.ivFoto);
            if (ivFoto != null && item.getFotoBase64() != null && !item.getFotoBase64().isEmpty()) {
                ivFoto.setVisibility(View.VISIBLE);
                ImageUtils.loadBase64IntoImageView(ivFoto, item.getFotoBase64());
            } else if (ivFoto != null) {
                ivFoto.setVisibility(View.GONE);
            }

            // Botao Editar
            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                btnEditar.setOnClickListener(v -> showEditDialog(item));
            }

            // Botao Inativar/Reativar
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (item.isAtivo()) {
                    btnInativar.setText("INATIVAR");
                    btnInativar.setBackground(getResources().getDrawable(R.drawable.btn_danger));
                    btnInativar.setTextColor(Color.WHITE);
                } else {
                    btnInativar.setText("REATIVAR");
                    btnInativar.setBackground(getResources().getDrawable(R.drawable.btn_success));
                    btnInativar.setTextColor(Color.parseColor("#0A0E27"));
                }
                btnInativar.setOnClickListener(v -> {
                    if (item.isAtivo()) {
                        showConfirm("Inativar Produto",
                                "Deseja inativar o produto \"" + item.getDescricao() + "\"?\n\nO produto nao aparecera mais nas vendas, mas podera ser reativado depois.",
                                () -> toggleAtivo(item.getId(), false));
                    } else {
                        showConfirm("Reativar Produto",
                                "Deseja reativar o produto \"" + item.getDescricao() + "\"?\n\nO produto voltara a aparecer nas vendas.",
                                () -> toggleAtivo(item.getId(), true));
                    }
                });
            }

            // Se inativo, aplicar opacidade reduzida no texto
            float alpha = item.isAtivo() ? 1.0f : 0.6f;
            View tvDescricao = holder.find(R.id.tvDescricao);
            if (tvDescricao != null) tvDescricao.setAlpha(alpha);
            View tvPrecos2 = holder.find(R.id.tvPrecos);
            if (tvPrecos2 != null) tvPrecos2.setAlpha(alpha);
        });

        recyclerView.setAdapter(adapter);

        // Image picker
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

        // Botao voltar
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());

        // Busca
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });
        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());

        // Filtros
        btnFiltroTodos.setOnClickListener(v -> { filtroAtual = 0; updateFilterButtons(); loadData(); });
        btnFiltroAtivos.setOnClickListener(v -> { filtroAtual = 1; updateFilterButtons(); loadData(); });
        btnFiltroInativos.setOnClickListener(v -> { filtroAtual = 2; updateFilterButtons(); loadData(); });

        updateFilterButtons();
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void updateFilterButtons() {
        // Reset all
        btnFiltroTodos.setAlpha(0.5f);
        btnFiltroAtivos.setAlpha(0.5f);
        btnFiltroInativos.setAlpha(0.5f);

        // Highlight selected
        switch (filtroAtual) {
            case 0: btnFiltroTodos.setAlpha(1.0f); break;
            case 1: btnFiltroAtivos.setAlpha(1.0f); break;
            case 2: btnFiltroInativos.setAlpha(1.0f); break;
        }
    }

    private void loadData() {
        String busca = etBusca.getText().toString().trim();
        showLoading("Carregando produtos...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                StringBuilder sql = new StringBuilder("SELECT * FROM produtos WHERE 1=1");

                // Filtro de status
                switch (filtroAtual) {
                    case 1: sql.append(" AND ativo = 1"); break;
                    case 2: sql.append(" AND ativo = 0"); break;
                    // case 0: sem filtro, mostra todos
                }

                // Filtro de busca
                if (!busca.isEmpty()) {
                    sql.append(" AND (descricao LIKE ? OR codigo LIKE ? OR codigo_barras LIKE ?)");
                }
                sql.append(" ORDER BY ativo DESC, descricao ASC");

                PreparedStatement ps = conn.prepareStatement(sql.toString());
                int paramIndex = 1;
                if (!busca.isEmpty()) {
                    ps.setString(paramIndex++, "%" + busca + "%");
                    ps.setString(paramIndex++, "%" + busca + "%");
                    ps.setString(paramIndex++, "%" + busca + "%");
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
                    p.setAtivo(rs.getInt("ativo") == 1);
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

                final int total = list.size();
                hideLoading();
                runOnUiThread(() -> {
                    adapter.setItems(list);
                    tvContador.setText(total + " produto" + (total != 1 ? "s" : ""));
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
     * Gera um EAN-13 unico verificando no banco de dados.
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
            // fallback
        }
        return EanUtils.gerarEan13();
    }

    /**
     * Exibe dialog para editar um produto existente.
     */
    private void showEditDialog(Produto produto) {
        fotoBase64Temp = produto.getFotoBase64();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_produto, null);
        EditText etCodigo = dialogView.findViewById(R.id.etCodigo);
        EditText etCodigoBarras = dialogView.findViewById(R.id.etCodigoBarras);
        Button btnGerarEan = dialogView.findViewById(R.id.btnGerarEan);
        EditText etDescricao = dialogView.findViewById(R.id.etDescricao);
        EditText etUnidade = dialogView.findViewById(R.id.etUnidade);
        EditText etPrecoCusto = dialogView.findViewById(R.id.etPrecoCusto);
        EditText etPrecoVenda = dialogView.findViewById(R.id.etPrecoVenda);
        EditText etEstoque = dialogView.findViewById(R.id.etEstoque);
        EditText etEstoqueMin = dialogView.findViewById(R.id.etEstoqueMin);
        ImageView ivFoto = dialogView.findViewById(R.id.ivFoto);
        Button btnFoto = dialogView.findViewById(R.id.btnFoto);

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

        // Botao para gerar EAN-13
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

        String statusText = produto.isAtivo() ? " (Ativo)" : " (Inativo)";
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Editar Produto" + statusText)
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    saveProduto(
                            produto.getId(),
                            etCodigo.getText().toString().trim(),
                            etCodigoBarras.getText().toString().trim(),
                            etDescricao.getText().toString().trim(),
                            etUnidade.getText().toString().trim(),
                            etPrecoCusto.getText().toString().trim(),
                            etPrecoVenda.getText().toString().trim(),
                            etEstoque.getText().toString().trim(),
                            etEstoqueMin.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveProduto(int id, String codigo, String codigoBarras, String descricao, String unidade,
                             String precoCusto, String precoVenda, String estoque, String estoqueMin) {
        if (descricao.isEmpty()) {
            showError("Por favor, preencha o campo Descricao antes de salvar.");
            return;
        }

        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE produtos SET codigo=?,codigo_barras=?,descricao=?,unidade=?,preco_custo=?,preco_venda=?,estoque=?,estoque_minimo=?,foto_base64=? WHERE id=?");

                ps.setString(1, codigo);
                ps.setString(2, codigoBarras.isEmpty() ? null : codigoBarras);
                ps.setString(3, descricao);
                ps.setString(4, unidade.isEmpty() ? "UN" : unidade);
                ps.setDouble(5, FormatUtils.parseMoney(precoCusto));
                ps.setDouble(6, FormatUtils.parseMoney(precoVenda));
                ps.setDouble(7, FormatUtils.parseMoney(estoque));
                ps.setDouble(8, FormatUtils.parseMoney(estoqueMin));
                ps.setString(9, fotoBase64Temp);
                ps.setInt(10, id);

                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Produto atualizado!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    /**
     * Alterna o status ativo/inativo de um produto.
     */
    private void toggleAtivo(int id, boolean ativar) {
        String acao = ativar ? "Reativando..." : "Inativando...";
        showLoading(acao);
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE produtos SET ativo = ? WHERE id = ?");
                ps.setInt(1, ativar ? 1 : 0);
                ps.setInt(2, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast(ativar ? "Produto reativado!" : "Produto inativado!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }
}
