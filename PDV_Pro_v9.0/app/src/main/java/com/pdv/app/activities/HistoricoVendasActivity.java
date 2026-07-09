package com.pdv.app.activities;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Venda;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

public class HistoricoVendasActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Venda> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.HISTORICO_ACESSAR)) {
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Historico de Vendas");
        Button btnReimprimir = findViewById(R.id.btnNovo);
        btnReimprimir.setText("Reimprimir");

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, "#" + item.getId() + " - " + FormatUtils.safeString(item.getClienteNome())
                    + " - R$ " + FormatUtils.formatMoney(item.getTotalLiquido()));
            holder.setText(R.id.tvLine2, FormatUtils.formatDate(item.getDataVenda()) + " | " + item.getStatus());
            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);
        });
        adapter.setOnItemClickListener((item, pos) -> showVendaOptions(item));
        recyclerView.setAdapter(adapter);
        PermissionHelper.controlarVisibilidade(this, btnReimprimir, PermissionConstants.HISTORICO_REIMPRIMIR);
        btnReimprimir.setOnClickListener(v -> showSeletorReimpressao());

        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());
        loadData();
    }

    private void showSeletorReimpressao() {
        if (!PermissionHelper.verificar(this, PermissionConstants.HISTORICO_REIMPRIMIR)) return;
        List<Venda> vendas = adapter.getItems();
        if (vendas == null || vendas.isEmpty()) {
            showToast("Nenhuma venda carregada para reimprimir.");
            return;
        }
        String[] itens = new String[vendas.size()];
        for (int i = 0; i < vendas.size(); i++) {
            Venda venda = vendas.get(i);
            itens[i] = "#" + venda.getId() + " - " + FormatUtils.formatDate(venda.getDataVenda())
                    + " - R$ " + FormatUtils.formatMoney(venda.getTotalLiquido());
        }
        new AlertDialog.Builder(this)
                .setTitle("Escolha o cupom")
                .setItems(itens, (d, pos) -> reimprimir(vendas.get(pos).getId()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void loadData() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT v.*, c.nome as cliente_nome FROM vendas v "
                                + "LEFT JOIN clientes c ON v.cliente_id = c.id ORDER BY v.id DESC LIMIT 100");
                List<Venda> list = new ArrayList<>();
                while (rs.next()) {
                    Venda v = new Venda();
                    v.setId(rs.getInt("id"));
                    v.setClienteNome(rs.getString("cliente_nome"));
                    v.setDataVenda(rs.getString("data_venda"));
                    v.setTotalLiquido(rs.getDouble("total_liquido"));
                    v.setStatus(rs.getString("status"));
                    list.add(v);
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void showVendaOptions(Venda venda) {
        // Montar opcoes dinamicamente baseado nas permissoes
        java.util.List<String> opcoes = new java.util.ArrayList<>();
        java.util.List<Runnable> acoes = new java.util.ArrayList<>();
        String status = venda.getStatus() != null ? venda.getStatus().trim().toLowerCase() : "";
        boolean vendaAtiva = !"cancelada".equals(status) && !"devolvida".equals(status);

        if (PermissionHelper.temPermissao(this, PermissionConstants.HISTORICO_REIMPRIMIR)) {
            opcoes.add("Reimprimir Cupom");
            acoes.add(() -> reimprimir(venda.getId()));
        }
        if (PermissionHelper.temPermissao(this, PermissionConstants.HISTORICO_ENVIAR_WHATSAPP)) {
            opcoes.add("Enviar via WhatsApp");
            acoes.add(() -> enviarWhatsApp(venda.getId()));
        }
        if (vendaAtiva && PermissionHelper.temPermissao(this, PermissionConstants.HISTORICO_CANCELAR_VENDA)) {
            opcoes.add("Cancelar Venda");
            acoes.add(() -> solicitarSenhaAdministrador(
                    "Autorizar Cancelamento",
                    "Para cancelar a venda #" + venda.getId() + ", digite a senha de um usuario com perfil Administrador.",
                    () -> confirmarCancelarVenda(venda.getId())
            ));

        }
        if (vendaAtiva && PermissionHelper.temPermissao(this, PermissionConstants.HISTORICO_DEVOLVER_VENDA)) {
            opcoes.add("Devolucao de Venda");
            acoes.add(() -> solicitarSenhaAdministrador(
                    "Autorizar Devolucao",
                    "Para fazer devolucao da venda #" + venda.getId() + ", digite a senha de um usuario com perfil Administrador.",
                    () -> confirmarDevolucaoVenda(venda.getId())
            ));
        }

        if (opcoes.isEmpty()) {
            showToast("Voce nao tem permissao para nenhuma acao nesta venda.");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Venda #" + venda.getId())
                .setItems(opcoes.toArray(new String[0]), (d, w) -> acoes.get(w).run())
                .show();
    }

    private void reimprimir(int vendaId) {
        UserActionLogger.log(this, "REIMPRIMIR_CUPOM", "Historico de Vendas", "Venda #" + vendaId);
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

    private void enviarWhatsApp(int vendaId) {
        new Thread(() -> {
            CupomGenerator gen = new CupomGenerator(this);
            String cupom = gen.gerarCupom(vendaId);
            runOnUiThread(() -> WhatsAppHelper.enviarCupom(this, cupom, null));
        }).start();
    }

    private void solicitarSenhaAdministrador(String titulo, String mensagem, Runnable autorizado) {
        final EditText input = new EditText(this);
        input.setHint("Senha do administrador");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(32, 18, 32, 18);

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensagem)
                .setView(input)
                .setPositiveButton("Autorizar", (dialog, which) -> {
                    String senha = input.getText().toString().trim();
                    if (senha.isEmpty()) {
                        showError("Digite a senha de um usuario administrador.");
                        return;
                    }
                    showLoading("Validando administrador...");
                    new Thread(() -> {
                        boolean ok = validarSenhaAdministrador(senha);
                        hideLoading();
                        if (ok) {
                            runOnUiThread(autorizado);
                        } else {
                            showError("Senha invalida ou usuario sem perfil Administrador. Operacao bloqueada.");
                        }
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean validarSenhaAdministrador(String senha) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            PreparedStatement ps;
            ResultSet rs;
            try {
                ps = conn.prepareStatement(
                        "SELECT u.id FROM usuarios u " +
                        "LEFT JOIN perfis p ON u.perfil_id = p.id " +
                        "WHERE u.senha = ? AND u.ativo = 1 " +
                        "AND (LOWER(u.nivel) = 'admin' OR LOWER(p.nome) = 'administrador' OR p.sistematico = 1) " +
                        "LIMIT 1");
                ps.setString(1, senha);
                rs = ps.executeQuery();
                boolean ok = rs.next();
                rs.close();
                ps.close();
                return ok;
            } catch (Exception ex) {
                ps = conn.prepareStatement(
                        "SELECT id FROM usuarios WHERE senha = ? AND ativo = 1 AND LOWER(nivel) = 'admin' LIMIT 1");
                ps.setString(1, senha);
                rs = ps.executeQuery();
                boolean ok = rs.next();
                rs.close();
                ps.close();
                return ok;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void confirmarCancelarVenda(int vendaId) {
        showConfirm("Cancelar Venda", "Deseja realmente cancelar a venda #" + vendaId + "?", () -> cancelarVenda(vendaId));
    }

    private void confirmarDevolucaoVenda(int vendaId) {
        showConfirm("Devolucao de Venda",
                "Deseja realmente fazer a devolucao da venda #" + vendaId + "?\n\n" +
                "O status da venda sera alterado para DEVOLVIDA e o estoque dos produtos sera devolvido.",
                () -> devolverVenda(vendaId));
    }

    private void cancelarVenda(int vendaId) {
        showLoading("Cancelando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE vendas SET status = 'cancelada' WHERE id = ? AND status NOT IN ('cancelada','devolvida')");
                ps.setInt(1, vendaId);
                int linhas = ps.executeUpdate();
                ps.close();
                hideLoading();
                if (linhas > 0) {
                    showToast("Venda cancelada!");
                } else {
                    showToast("Venda ja estava cancelada/devolvida ou nao foi encontrada.");
                }
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void devolverVenda(int vendaId) {
        showLoading("Processando devolucao...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                boolean oldAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);

                    PreparedStatement psStatus = conn.prepareStatement(
                            "UPDATE vendas SET status = 'devolvida' WHERE id = ? AND status NOT IN ('cancelada','devolvida')");
                    psStatus.setInt(1, vendaId);
                    int linhas = psStatus.executeUpdate();
                    psStatus.close();
                    if (linhas <= 0) {
                        conn.rollback();
                        hideLoading();
                        showToast("Venda ja estava cancelada/devolvida ou nao foi encontrada.");
                        loadData();
                        return;
                    }

                    PreparedStatement psItens = conn.prepareStatement(
                            "SELECT produto_id, quantidade FROM itens_venda WHERE venda_id = ? AND produto_id IS NOT NULL AND produto_id > 0");
                    psItens.setInt(1, vendaId);
                    ResultSet rsItens = psItens.executeQuery();
                    PreparedStatement psEstoque = conn.prepareStatement(
                            "UPDATE produtos SET estoque = estoque + ? WHERE id = ?");
                    while (rsItens.next()) {
                        int produtoId = rsItens.getInt("produto_id");
                        double qtd = rsItens.getDouble("quantidade");
                        psEstoque.setDouble(1, qtd);
                        psEstoque.setInt(2, produtoId);
                        psEstoque.executeUpdate();
                    }
                    psEstoque.close();
                    rsItens.close();
                    psItens.close();

                    try {
                        PreparedStatement psCR = conn.prepareStatement(
                                "UPDATE contas_receber SET status = 'cancelado', observacao = CONCAT(COALESCE(observacao,''), ' | Venda devolvida') WHERE venda_id = ? AND status <> 'pago'");
                        psCR.setInt(1, vendaId);
                        psCR.executeUpdate();
                        psCR.close();
                    } catch (Exception ignored) {}

                    conn.commit();
                    conn.setAutoCommit(oldAutoCommit);
                    hideLoading();
                    showToast("Devolucao realizada e estoque devolvido!");
                    loadData();
                } catch (Exception e) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                    try { conn.setAutoCommit(oldAutoCommit); } catch (Exception ignored) {}
                    throw e;
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }
}
