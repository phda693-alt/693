package com.pdv.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Comanda;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

public class ComandasActivity extends BaseActivity {
    private RecyclerView rvComandas;
    private GenericAdapter<Comanda> adapter;
    private TextView tvEmpty;
    private boolean filtroAbertas = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comandas);


        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.COMANDAS_ACESSAR)) {
            return;
        }
        rvComandas = findViewById(R.id.rvComandas);
        tvEmpty = findViewById(R.id.tvEmpty);
        Button btnNovaComanda = findViewById(R.id.btnNovaComanda);
        Button btnAssistenteComandas = findViewById(R.id.btnAssistenteComandas);
        Button btnFiltroAbertas = findViewById(R.id.btnFiltroAbertas);
        Button btnFiltroTodas = findViewById(R.id.btnFiltroTodas);

        rvComandas.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter<>(R.layout.item_comanda, (holder, item, pos) -> {
            holder.setText(R.id.tvNumeroComanda, "#" + item.getNumero());
            String clienteNome = item.getClienteNome();
            if (clienteNome == null || clienteNome.isEmpty()) {
                clienteNome = "Sem cliente";
            }
            holder.setText(R.id.tvClienteComanda, clienteNome);
            String info = FormatUtils.formatDate(item.getDataAbertura());
            if (item.getObservacao() != null && !item.getObservacao().isEmpty()) {
                info += " | " + item.getObservacao();
            }
            holder.setText(R.id.tvInfoComanda, info);
            holder.setText(R.id.tvTotalComanda, "R$ " + FormatUtils.formatMoney(item.getTotalItens()));

            TextView tvStatus = holder.find(R.id.tvStatusComanda);
            if (tvStatus != null) {
                String status = item.getStatus();
                tvStatus.setText(status != null ? status.toUpperCase() : "ABERTA");
                if ("aberta".equalsIgnoreCase(status)) {
                    tvStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
                } else if ("fechada".equalsIgnoreCase(status)) {
                    tvStatus.setTextColor(getResources().getColor(R.color.accent_cyan));
                } else {
                    tvStatus.setTextColor(getResources().getColor(R.color.colorDanger));
                }
            }
        });

        adapter.setOnItemClickListener((item, pos) -> {
            if ("aberta".equalsIgnoreCase(item.getStatus())) {
                abrirDetalheComanda(item.getId());
            } else {
                showComandaFechadaOptions(item);
            }
        });

        adapter.setOnItemLongClickListener((item, pos) -> {
            showComandaOptions(item);
        });

        rvComandas.setAdapter(adapter);

        boolean podeCriar = PermissionHelper.verificarSilencioso(this, PermissionConstants.COMANDAS_CRIAR);
        btnNovaComanda.setVisibility(podeCriar ? View.VISIBLE : View.GONE);
        btnAssistenteComandas.setVisibility(podeCriar ? View.VISIBLE : View.GONE);
        btnNovaComanda.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.COMANDAS_CRIAR)) showNovaComandaDialog();
        });
        btnAssistenteComandas.setOnClickListener(v -> showAssistenteComandas());
        btnFiltroAbertas.setOnClickListener(v -> {
            filtroAbertas = true;
            carregarComandas();
        });
        btnFiltroTodas.setOnClickListener(v -> {
            filtroAbertas = false;
            carregarComandas();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarComandas();
    }

    private void carregarComandas() {
        showLoading("Carregando comandas...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String sql = "SELECT c.*, cl.nome as cliente_nome FROM comandas c "
                        + "LEFT JOIN clientes cl ON c.cliente_id = cl.id ";
                if (filtroAbertas) {
                    sql += "WHERE c.status = 'aberta' ";
                }
                sql += "ORDER BY c.status = 'aberta' DESC, c.id DESC LIMIT 200";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Comanda> list = new ArrayList<>();
                while (rs.next()) {
                    Comanda cmd = new Comanda();
                    cmd.setId(rs.getInt("id"));
                    cmd.setNumero(rs.getInt("numero"));
                    cmd.setClienteId(rs.getInt("cliente_id"));
                    cmd.setClienteNome(rs.getString("cliente_nome"));
                    cmd.setDataAbertura(rs.getString("data_abertura"));
                    cmd.setTotalItens(rs.getDouble("total_itens"));
                    cmd.setObservacao(rs.getString("observacao"));
                    cmd.setStatus(rs.getString("status"));
                    list.add(cmd);
                }
                rs.close();
                stmt.close();
                hideLoading();

                runOnUiThread(() -> {
                    adapter.setItems(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    rvComandas.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void showNovaComandaDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_nova_comanda, null);
        EditText etNumero = view.findViewById(R.id.etNumeroComanda);
        EditText etCliente = view.findViewById(R.id.etClienteComandaDialog);
        EditText etObs = view.findViewById(R.id.etObsComandaDialog);

        // Sugerir proximo numero
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(numero), 0) + 1 as prox FROM comandas WHERE status = 'aberta'");
                int prox = 1;
                if (rs.next()) prox = rs.getInt("prox");
                rs.close();
                stmt.close();
                final int proxNum = prox;
                runOnUiThread(() -> etNumero.setText(String.valueOf(proxNum)));
            } catch (Exception ignored) {}
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Nova Comanda")
                .setView(view)
                .setPositiveButton("Abrir Comanda", (d, w) -> {
                    String numStr = etNumero.getText().toString().trim();
                    if (numStr.isEmpty()) {
                        showError("Informe o numero da comanda.");
                        return;
                    }
                    int numero;
                    try {
                        numero = Integer.parseInt(numStr);
                    } catch (NumberFormatException e) {
                        showError("Numero invalido.");
                        return;
                    }
                    String clienteNome = etCliente.getText().toString().trim();
                    String obs = etObs.getText().toString().trim();
                    criarComanda(numero, clienteNome, obs);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText campoAssistente(String hint, String valor) {
        EditText campo = new EditText(this);
        campo.setHint(hint);
        campo.setText(valor);
        campo.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        campo.setTextColor(0xFFFFFFFF);
        campo.setHintTextColor(0xFF90A4AE);
        campo.setBackgroundResource(R.drawable.input_bg);
        campo.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 8, 0, 8);
        campo.setLayoutParams(lp);
        return campo;
    }

    private void showAssistenteComandas() {
        if (!PermissionHelper.verificar(this, PermissionConstants.COMANDAS_CRIAR)) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = view.findViewById(R.id.formContainer);
        TextView info = new TextView(this);
        info.setText("Abra varias comandas numeradas em sequencia. Numeros que ja estiverem abertos serao ignorados.");
        info.setTextColor(0xFFB0BEC5);
        info.setPadding(8, 8, 8, 16);
        container.addView(info);
        Spinner porte = new Spinner(this);
        String[] opcoes = {"Pequena - 10 comandas", "Media - 30 comandas", "Grande - 100 comandas", "Personalizada"};
        porte.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, opcoes));
        container.addView(porte);
        EditText inicio = campoAssistente("Numero inicial", "1");
        EditText quantidade = campoAssistente("Quantidade", "10");
        container.addView(inicio);
        container.addView(quantidade);
        porte.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == 0) quantidade.setText("10");
                else if (pos == 1) quantidade.setText("30");
                else if (pos == 2) quantidade.setText("100");
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        new AlertDialog.Builder(this).setTitle("Assistente de Criacao de Comandas")
                .setView(view).setPositiveButton("Criar", (d, w) -> {
                    try {
                        int primeiro = Integer.parseInt(inicio.getText().toString().trim());
                        int qtd = Integer.parseInt(quantidade.getText().toString().trim());
                        if (primeiro <= 0 || qtd <= 0 || qtd > 500) {
                            showError("Use numeros positivos. O limite e de 500 comandas por vez.");
                            return;
                        }
                        criarComandasEmLote(primeiro, qtd);
                    } catch (NumberFormatException e) {
                        showError("Preencha o numero inicial e a quantidade corretamente.");
                    }
                }).setNegativeButton("Cancelar", null).show();
    }


    private int buscarCaixaAbertoPreferencial(Connection conn) throws SQLException {
        int caixaNominalPreferido = getSharedPreferences("caixa_config", MODE_PRIVATE)
                .getInt("caixa_nominal_id_selecionado", 0);
        PreparedStatement psCaixa;
        if (caixaNominalPreferido > 0) {
            psCaixa = conn.prepareStatement(
                    "SELECT id FROM caixa WHERE status='aberto' AND caixa_nominal_id=? ORDER BY id DESC LIMIT 1");
            psCaixa.setInt(1, caixaNominalPreferido);
        } else {
            psCaixa = conn.prepareStatement(
                    "SELECT id FROM caixa WHERE status='aberto' ORDER BY id DESC LIMIT 1");
        }
        ResultSet rsCaixa = psCaixa.executeQuery();
        int caixaId = 0;
        if (rsCaixa.next()) caixaId = rsCaixa.getInt(1);
        rsCaixa.close();
        psCaixa.close();
        return caixaId;
    }

    private void criarComandasEmLote(int inicio, int quantidade) {
        showLoading("Criando comandas...");
        new Thread(() -> {
            int criadas = 0, ignoradas = 0;
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                int caixaId = buscarCaixaAbertoPreferencial(conn);
                if (caixaId <= 0) {
                    hideLoading();
                    showError("Nao existe caixa aberto para o caixa selecionado. Abra o caixa no modulo Caixa ou escolha outro no dropdown.");
                    return;
                }
                PreparedStatement check = conn.prepareStatement(
                        "SELECT COUNT(*) FROM comandas WHERE numero=? AND status='aberta'");
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO comandas (numero,cliente_id,caixa_id,data_abertura,total_itens,observacao,status) " +
                                "VALUES (?,0,?,NOW(),0,NULL,'aberta')");
                for (int i = 0; i < quantidade; i++) {
                    int numero = inicio + i;
                    check.setInt(1, numero);
                    ResultSet rs = check.executeQuery();
                    boolean existe = rs.next() && rs.getInt(1) > 0;
                    rs.close();
                    if (existe) { ignoradas++; continue; }
                    insert.setInt(1, numero);
                    insert.setInt(2, caixaId);
                    insert.addBatch();
                    criadas++;
                }
                insert.executeBatch();
                check.close(); insert.close();
                final int ok = criadas, dup = ignoradas;
                hideLoading();
                runOnUiThread(() -> {
                    showSuccess(ok + " comandas criadas" + (dup > 0 ? " e " + dup + " ja abertas ignoradas." : "."));
                    carregarComandas();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void criarComanda(int numero, String clienteNome, String observacao) {
        showLoading("Criando comanda...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                // Verificar se ja existe comanda aberta com esse numero
                PreparedStatement psCheck = conn.prepareStatement(
                        "SELECT COUNT(*) FROM comandas WHERE numero = ? AND status = 'aberta'");
                psCheck.setInt(1, numero);
                ResultSet rsCheck = psCheck.executeQuery();
                rsCheck.next();
                if (rsCheck.getInt(1) > 0) {
                    rsCheck.close();
                    psCheck.close();
                    hideLoading();
                    showError("Ja existe uma comanda aberta com o numero " + numero + ".\n\nEscolha outro numero ou feche a comanda existente.");
                    return;
                }
                rsCheck.close();
                psCheck.close();

                // Buscar cliente pelo nome se informado
                int clienteId = 0;
                if (!clienteNome.isEmpty()) {
                    PreparedStatement psCli = conn.prepareStatement(
                            "SELECT id FROM clientes WHERE nome LIKE ? AND ativo = 1 LIMIT 1");
                    psCli.setString(1, "%" + clienteNome + "%");
                    ResultSet rsCli = psCli.executeQuery();
                    if (rsCli.next()) {
                        clienteId = rsCli.getInt("id");
                    }
                    rsCli.close();
                    psCli.close();
                }

                // Buscar caixa aberto respeitando o dropdown selecionado no modulo Caixa
                int caixaId = buscarCaixaAbertoPreferencial(conn);
                if (caixaId <= 0) {
                    hideLoading();
                    showError("Nao existe caixa aberto para o caixa selecionado. Abra o caixa no modulo Caixa ou escolha outro no dropdown.");
                    return;
                }

                // Inserir comanda
                PreparedStatement psInsert = conn.prepareStatement(
                        "INSERT INTO comandas (numero, cliente_id, caixa_id, data_abertura, total_itens, observacao, status) "
                                + "VALUES (?, ?, ?, NOW(), 0, ?, 'aberta')",
                        Statement.RETURN_GENERATED_KEYS);
                psInsert.setInt(1, numero);
                psInsert.setInt(2, clienteId);
                psInsert.setInt(3, caixaId);
                psInsert.setString(4, observacao.isEmpty() ? null : observacao);
                psInsert.executeUpdate();

                ResultSet keys = psInsert.getGeneratedKeys();
                int comandaId = 0;
                if (keys.next()) comandaId = keys.getInt(1);
                keys.close();
                psInsert.close();

                hideLoading();
                final int finalComandaId = comandaId;
                runOnUiThread(() -> {
                    showToast("Comanda #" + numero + " criada!");
                    abrirDetalheComanda(finalComandaId);
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_VENDA);
            }
        }).start();
    }

    private void abrirDetalheComanda(int comandaId) {
        Intent intent = new Intent(this, ComandaDetalheActivity.class);
        intent.putExtra("comanda_id", comandaId);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void showComandaOptions(Comanda comanda) {
        List<String> options = new ArrayList<>();
        options.add("Abrir Detalhes");
        if ("aberta".equalsIgnoreCase(comanda.getStatus())) {
            if (PermissionHelper.temPermissao(this, PermissionConstants.COMANDAS_FECHAR)) options.add("Fechar e Pagar");
            if (PermissionHelper.temPermissao(this, PermissionConstants.COMANDAS_CANCELAR)) options.add("Cancelar Comanda");
        }
        if (PermissionHelper.temPermissao(this, PermissionConstants.COMANDAS_IMPRIMIR)) options.add("Reimprimir");

        new AlertDialog.Builder(this)
                .setTitle("Comanda #" + comanda.getNumero())
                .setItems(options.toArray(new String[0]), (d, w) -> {
                    String selected = options.get(w);
                    if ("Abrir Detalhes".equals(selected)) {
                        abrirDetalheComanda(comanda.getId());
                    } else if ("Fechar e Pagar".equals(selected)) {
                        abrirDetalheComanda(comanda.getId());
                    } else if ("Cancelar Comanda".equals(selected)) {
                        cancelarComanda(comanda);
                    } else if ("Reimprimir".equals(selected)) {
                        reimprimirComanda(comanda.getId());
                    }
                })
                .show();
    }

    private void showComandaFechadaOptions(Comanda comanda) {
        boolean podeImprimir = PermissionHelper.temPermissao(this, PermissionConstants.COMANDAS_IMPRIMIR);
        String[] options = podeImprimir ? new String[]{"Ver Detalhes", "Reimprimir"} : new String[]{"Ver Detalhes"};
        new AlertDialog.Builder(this)
                .setTitle("Comanda #" + comanda.getNumero() + " (" + comanda.getStatus() + ")")
                .setItems(options, (d, w) -> {
                    if (w == 0) abrirDetalheComanda(comanda.getId());
                    else if (podeImprimir) reimprimirComanda(comanda.getId());
                })
                .show();
    }

    private void cancelarComanda(Comanda comanda) {
        showConfirm("Cancelar Comanda", "Deseja cancelar a comanda #" + comanda.getNumero() + "?\n\nEsta acao nao pode ser desfeita.", () -> {
            showLoading("Cancelando...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE comandas SET status = 'cancelada', data_fechamento = NOW() WHERE id = ?");
                    ps.setInt(1, comanda.getId());
                    ps.executeUpdate();
                    ps.close();
                    hideLoading();
                    showToast("Comanda cancelada!");
                    runOnUiThread(() -> carregarComandas());
                } catch (Exception e) {
                    hideLoading();
                    showErrorFromException(e, ErrorHandler.CTX_VENDA);
                }
            }).start();
        });
    }

    private void reimprimirComanda(int comandaId) {
        new Thread(() -> {
            String cupom = gerarCupomComanda(comandaId);
            PrinterManager pm = new PrinterManager(this);
            if (pm.isImpressoraConfigurada()) {
                boolean ok = pm.imprimirTexto(cupom);
                showToast(ok ? "Comanda impressa!" : "Erro na impressao");
            } else {
                runOnUiThread(() -> WhatsAppHelper.enviarCupom(this, cupom, null));
            }
        }).start();
    }

    private String gerarCupomComanda(int comandaId) {
        try {
            PrinterManager pm = new PrinterManager(this);
            int colunas = pm.getColunasTexto();
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();

            // Dados da comanda
            ResultSet rs = stmt.executeQuery("SELECT c.*, cl.nome as cliente_nome FROM comandas c "
                    + "LEFT JOIN clientes cl ON c.cliente_id = cl.id WHERE c.id = " + comandaId);
            int numero = 0;
            String clienteNome = "", dataAbertura = "", obs = "", status = "";
            double total = 0;
            if (rs.next()) {
                numero = rs.getInt("numero");
                clienteNome = rs.getString("cliente_nome");
                if (clienteNome == null) clienteNome = "Sem cliente";
                dataAbertura = rs.getString("data_abertura");
                obs = rs.getString("observacao");
                total = rs.getDouble("total_itens");
                status = rs.getString("status");
            }
            rs.close();

            // Itens
            rs = stmt.executeQuery("SELECT * FROM itens_comanda WHERE comanda_id = " + comandaId + " ORDER BY id");
            StringBuilder sb = new StringBuilder();
            String line = repeat("-", colunas);

            sb.append(center("COMANDA #" + numero, colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append("Cliente: ").append(clienteNome).append("\n");
            sb.append("Data: ").append(FormatUtils.formatDate(dataAbertura)).append("\n");
            sb.append("Status: ").append(status != null ? status.toUpperCase() : "").append("\n");
            if (obs != null && !obs.isEmpty()) sb.append("Obs: ").append(obs).append("\n");
            sb.append(line).append("\n");
            sb.append("ITEM  DESCRICAO          QTD    TOTAL\n");
            sb.append(line).append("\n");

            int itemNum = 1;
            while (rs.next()) {
                String desc = rs.getString("descricao_produto");
                if (desc != null && desc.length() > 18) desc = desc.substring(0, 18);
                sb.append(String.format("%-5d %-18s %5s %8s\n",
                        itemNum++,
                        desc != null ? desc : "",
                        FormatUtils.formatQuantidade(rs.getDouble("quantidade")),
                        FormatUtils.formatMoney(rs.getDouble("total"))));
                String obsItem = rs.getString("observacao");
                if (obsItem != null && !obsItem.isEmpty()) {
                    sb.append("      > ").append(obsItem).append("\n");
                }
            }
            rs.close();
            stmt.close();

            sb.append(line).append("\n");
            sb.append(rightAlign("TOTAL: R$ " + FormatUtils.formatMoney(total), colunas)).append("\n");
            sb.append(line).append("\n");
            sb.append(center("PDV Pro v8.0.0 - Comanda", colunas)).append("\n");
            sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

            return sb.toString();
        } catch (Exception e) {
            return "Erro ao gerar cupom da comanda: " + e.getMessage();
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
