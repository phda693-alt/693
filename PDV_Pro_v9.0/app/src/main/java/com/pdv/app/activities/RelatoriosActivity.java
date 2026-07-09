package com.pdv.app.activities;

import android.os.Bundle;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.*;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

public class RelatoriosActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_relatorios);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.RELATORIOS_ACESSAR)) {
            return;
        }

        // Controlar visibilidade de cada tipo de relatorio
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelVendas), PermissionConstants.RELATORIOS_VENDAS);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelLucratividade), PermissionConstants.RELATORIOS_LUCRATIVIDADE);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelVendedor), PermissionConstants.RELATORIOS_VENDEDOR);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelComissoes), PermissionConstants.RELATORIOS_VENDEDOR);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelGarcom), PermissionConstants.RELATORIOS_GARCOM);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelTaxasEntregador), PermissionConstants.RELATORIOS_TAXAS_ENTREGADOR);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelValesCaixa), PermissionConstants.RELATORIOS_VALES_CAIXA);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelAuditoria), PermissionConstants.RELATORIOS_AUDITORIA);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelEntregador), PermissionConstants.RELATORIOS_ENTREGADOR);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelCliente), PermissionConstants.RELATORIOS_CLIENTE);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelProdutos), PermissionConstants.RELATORIOS_PRODUTOS);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelCaixa), PermissionConstants.RELATORIOS_CAIXA);
        PermissionHelper.controlarVisibilidade(this, findViewById(R.id.btnRelContasReceber), PermissionConstants.CONTAS_RECEBER_RELATORIO);

        findViewById(R.id.btnRelVendas).setOnClickListener(v -> gerarRelatorio("vendas"));
        findViewById(R.id.btnRelLucratividade).setOnClickListener(v -> gerarRelatorio("lucratividade"));
        findViewById(R.id.btnRelVendedor).setOnClickListener(v -> gerarRelatorio("vendedor"));
        findViewById(R.id.btnRelComissoes).setOnClickListener(v -> abrirFiltroComissoes());
        findViewById(R.id.btnRelGarcom).setOnClickListener(v -> abrirFiltroAvancado("garcom"));
        findViewById(R.id.btnRelTaxasEntregador).setOnClickListener(v -> abrirFiltroAvancado("entregador"));
        findViewById(R.id.btnRelValesCaixa).setOnClickListener(v -> abrirFiltroAvancado("vales"));
        findViewById(R.id.btnRelAuditoria).setOnClickListener(v -> abrirFiltroAuditoria());
        findViewById(R.id.btnRelEntregador).setOnClickListener(v -> gerarRelatorio("entregador"));
        findViewById(R.id.btnRelCliente).setOnClickListener(v -> gerarRelatorio("cliente"));
        findViewById(R.id.btnRelProdutos).setOnClickListener(v -> gerarRelatorio("produtos"));
        findViewById(R.id.btnRelCaixa).setOnClickListener(v -> gerarRelatorio("caixa"));
        findViewById(R.id.btnRelContasReceber).setOnClickListener(v -> gerarRelatorio("contas_receber"));
    }

    /** Filtro dedicado de auditoria por usuario e periodo (data inicial/final). */
    private void abrirFiltroAuditoria() {
        showLoading("Carregando usuarios...");
        new Thread(() -> {
            try {
                List<Integer> ids = new ArrayList<>();
                List<String> nomes = new ArrayList<>();
                ids.add(0);
                nomes.add("Todos os usuarios");
                Statement stmt = DatabaseHelper.getInstance(this).getConnection().createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id,nome FROM usuarios ORDER BY ativo DESC,nome");
                while (rs.next()) {
                    ids.add(rs.getInt("id"));
                    nomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> mostrarFiltroAuditoria(ids, nomes));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    private void mostrarFiltroAuditoria(List<Integer> ids, List<String> nomes) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView usuarioLabel = new TextView(this);
        usuarioLabel.setText("Usuario");
        usuarioLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(usuarioLabel);
        Spinner usuarios = new Spinner(this);
        ArrayAdapter<String> usuariosAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, nomes);
        usuariosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        usuarios.setAdapter(usuariosAdapter);
        layout.addView(usuarios);

        TextView periodoLabel = new TextView(this);
        periodoLabel.setText("Periodo");
        periodoLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        periodoLabel.setPadding(0, pad, 0, 4);
        layout.addView(periodoLabel);

        Button inicioButton = new Button(this);
        inicioButton.setText("Data inicial: todas");
        Button fimButton = new Button(this);
        fimButton.setText("Data final: todas");
        Button limparButton = new Button(this);
        limparButton.setText("Limpar datas");
        layout.addView(inicioButton);
        layout.addView(fimButton);
        layout.addView(limparButton);

        final String[] inicioSql = {null};
        final String[] fimSql = {null};
        final String[] inicioLabel = {"Todas"};
        final String[] fimLabel = {"Todas"};
        inicioButton.setOnClickListener(v -> escolherData(inicioButton, "Data inicial", inicioSql, inicioLabel));
        fimButton.setOnClickListener(v -> escolherData(fimButton, "Data final", fimSql, fimLabel));
        limparButton.setOnClickListener(v -> {
            inicioSql[0] = null; fimSql[0] = null;
            inicioLabel[0] = "Todas"; fimLabel[0] = "Todas";
            inicioButton.setText("Data inicial: todas");
            fimButton.setText("Data final: todas");
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Acoes dos Usuarios")
                .setView(layout)
                .setPositiveButton("Gerar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (inicioSql[0] != null && fimSql[0] != null && inicioSql[0].compareTo(fimSql[0]) > 0) {
                showError("A data inicial nao pode ser posterior a data final.");
                return;
            }
            int pos = usuarios.getSelectedItemPosition();
            dialog.dismiss();
            gerarRelatorioAuditoria(ids.get(pos), nomes.get(pos), inicioSql[0], fimSql[0],
                    inicioLabel[0] + " ate " + fimLabel[0]);
        }));
        dialog.show();
    }

    private void escolherData(Button botao, String prefixo, String[] dataSql, String[] dataLabel) {
        Calendar cal = Calendar.getInstance();
        new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            Calendar escolhida = Calendar.getInstance();
            escolhida.set(year, month, day);
            dataSql[0] = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(escolhida.getTime());
            dataLabel[0] = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(escolhida.getTime());
            botao.setText(prefixo + ": " + dataLabel[0]);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void abrirFiltroAvancado(String tipo) {
        showLoading("Carregando filtros...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                List<Integer> ids = new ArrayList<>();
                List<String> nomes = new ArrayList<>();
                ids.add(0);
                nomes.add("Todos");
                String sql;
                if ("garcom".equals(tipo)) sql = "SELECT id,nome FROM garcons WHERE ativo=1 ORDER BY nome";
                else if ("entregador".equals(tipo)) sql = "SELECT id,nome FROM entregadores WHERE ativo=1 ORDER BY nome";
                else sql = "SELECT id,nome FROM usuarios WHERE ativo=1 ORDER BY nome";
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) { ids.add(rs.getInt(1)); nomes.add(rs.getString(2)); }
                rs.close();
                stmt.close();

                List<Integer> centroIds = new ArrayList<>();
                List<String> centroNomes = new ArrayList<>();
                centroIds.add(0);
                centroNomes.add("Todos os centros");
                if ("vales".equals(tipo)) {
                    stmt = conn.createStatement();
                    rs = stmt.executeQuery("SELECT id,nome FROM centros_custo WHERE ativo=1 ORDER BY nome");
                    while (rs.next()) { centroIds.add(rs.getInt(1)); centroNomes.add(rs.getString(2)); }
                    rs.close();
                    stmt.close();
                }
                hideLoading();
                runOnUiThread(() -> mostrarFiltroAvancado(tipo, ids, nomes, centroIds, centroNomes));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    private void mostrarFiltroAvancado(String tipo, List<Integer> ids, List<String> nomes,
                                       List<Integer> centroIds, List<String> centroNomes) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView label = new TextView(this);
        label.setText("garcom".equals(tipo) ? "Garcom" : "entregador".equals(tipo)
                ? "Entregador" : "Usuario");
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(label);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, nomes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        layout.addView(spinner);

        Spinner spinnerCentro = null;
        if ("vales".equals(tipo)) {
            TextView centroLabel = new TextView(this);
            centroLabel.setText("Centro de custos");
            centroLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            centroLabel.setPadding(0, pad, 0, 0);
            layout.addView(centroLabel);
            spinnerCentro = new Spinner(this);
            ArrayAdapter<String> centroAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, centroNomes);
            centroAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerCentro.setAdapter(centroAdapter);
            layout.addView(spinnerCentro);
        }

        TextView dataLabelView = new TextView(this);
        dataLabelView.setText("Data");
        dataLabelView.setTypeface(null, android.graphics.Typeface.BOLD);
        dataLabelView.setPadding(0, pad, 0, 0);
        layout.addView(dataLabelView);
        LinearLayout dataRow = new LinearLayout(this);
        dataRow.setOrientation(LinearLayout.HORIZONTAL);
        Button dateButton = new Button(this);
        dateButton.setText("Todas as datas");
        dateButton.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button clearButton = new Button(this);
        clearButton.setText("Todas");
        dataRow.addView(dateButton);
        dataRow.addView(clearButton);
        layout.addView(dataRow);
        final String[] dateSql = {null};
        final String[] dateLabel = {"Todas as datas"};
        dateButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day);
                dateSql[0] = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.getTime());
                dateLabel[0] = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(chosen.getTime());
                dateButton.setText(dateLabel[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        clearButton.setOnClickListener(v -> {
            dateSql[0] = null;
            dateLabel[0] = "Todas as datas";
            dateButton.setText(dateLabel[0]);
        });

        String title = "garcom".equals(tipo) ? "Porcentagens do Garcom"
                : "entregador".equals(tipo) ? "Taxas por Entregador"
                : "vales".equals(tipo) ? "Vales do Caixa" : "Acoes dos Usuarios";
        final Spinner finalSpinnerCentro = spinnerCentro;
        new AlertDialog.Builder(this).setTitle(title).setView(layout)
                .setPositiveButton("Gerar", (d, w) -> {
                    int entityPos = spinner.getSelectedItemPosition();
                    int entityId = ids.get(entityPos);
                    int centroId = finalSpinnerCentro != null
                            ? centroIds.get(finalSpinnerCentro.getSelectedItemPosition()) : 0;
                    if ("garcom".equals(tipo)) gerarRelatorioGarcom(entityId, nomes.get(entityPos), dateSql[0], dateLabel[0]);
                    else if ("entregador".equals(tipo)) gerarRelatorioTaxas(entityId, nomes.get(entityPos), dateSql[0], dateLabel[0]);
                    else if ("vales".equals(tipo)) gerarRelatorioVales(entityId, nomes.get(entityPos), centroId,
                            finalSpinnerCentro != null ? centroNomes.get(finalSpinnerCentro.getSelectedItemPosition()) : "Todos", dateSql[0], dateLabel[0]);
                    else gerarRelatorioAuditoria(entityId, nomes.get(entityPos), dateSql[0], dateSql[0], dateLabel[0]);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void gerarRelatorioVales(int userId, String userName, int centroId, String centroName,
                                     String date, String dateLabel) {
        showLoading("Gerando relatorio de vales...");
        new Thread(() -> {
            try {
                StringBuilder sql = new StringBuilder(
                        "SELECT vd.id,vd.data,vd.descricao,vd.valor,vd.caixa_id,"
                                + "COALESCE(cc.nome,'Sem centro') centro,COALESCE(u.nome,'Usuario antigo') usuario "
                                + "FROM vales_debito vd LEFT JOIN centros_custo cc ON vd.centro_custo_id=cc.id "
                                + "LEFT JOIN usuarios u ON vd.usuario_id=u.id WHERE 1=1");
                if (userId > 0) sql.append(" AND vd.usuario_id=?");
                if (centroId > 0) sql.append(" AND vd.centro_custo_id=?");
                if (date != null) sql.append(" AND DATE(vd.data)=?");
                sql.append(" ORDER BY vd.data DESC,vd.id DESC");
                PreparedStatement ps = DatabaseHelper.getInstance(this).getConnection().prepareStatement(sql.toString());
                int p = 1;
                if (userId > 0) ps.setInt(p++, userId);
                if (centroId > 0) ps.setInt(p++, centroId);
                if (date != null) ps.setString(p, date);
                ResultSet rs = ps.executeQuery();
                StringBuilder out = new StringBuilder("=== VALES DO CAIXA ===\n\nUsuario: ")
                        .append(userName).append("\nCentro: ").append(centroName)
                        .append("\nData: ").append(dateLabel).append("\n\n");
                Map<String, Double> byCenter = new LinkedHashMap<>();
                Map<String, Double> byUser = new LinkedHashMap<>();
                double total = 0;
                int count = 0;
                while (rs.next()) {
                    double value = rs.getDouble("valor");
                    String center = rs.getString("centro");
                    String user = rs.getString("usuario");
                    out.append(FormatUtils.formatDate(rs.getString("data"))).append(" | Caixa #")
                            .append(rs.getInt("caixa_id")).append("\n  ").append(rs.getString("descricao"))
                            .append("\n  ").append(center).append(" | ").append(user)
                            .append("\n  R$ ").append(FormatUtils.formatMoney(value)).append("\n\n");
                    byCenter.put(center, byCenter.getOrDefault(center, 0.0) + value);
                    byUser.put(user, byUser.getOrDefault(user, 0.0) + value);
                    total += value;
                    count++;
                }
                rs.close(); ps.close();
                out.append("--- TOTAL POR CENTRO ---\n");
                for (Map.Entry<String, Double> e : byCenter.entrySet()) out.append(e.getKey()).append(": R$ ").append(FormatUtils.formatMoney(e.getValue())).append('\n');
                out.append("\n--- TOTAL POR USUARIO ---\n");
                for (Map.Entry<String, Double> e : byUser.entrySet()) out.append(e.getKey()).append(": R$ ").append(FormatUtils.formatMoney(e.getValue())).append('\n');
                out.append("\nTOTAL (" + count + "): R$ ").append(FormatUtils.formatMoney(total));
                showReport("Vales do Caixa", out.toString());
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_RELATORIO); }
        }).start();
    }

    private void gerarRelatorioGarcom(int id, String name, String date, String dateLabel) {
        gerarRelatorioPercentualGenerico("garcom", id, name, date, dateLabel);
    }

    private void gerarRelatorioPercentualGenerico(String type, int id, String name, String date, String dateLabel) {
        showLoading("Gerando porcentagens do garcom...");
        new Thread(() -> {
            try {
                String pct = "COALESCE(v.garcom_percentual,g.porcentagem,0)";
                String val = "COALESCE(v.garcom_valor,ROUND(v.total_liquido*" + pct + "/100,2))";
                StringBuilder sql = new StringBuilder("SELECT DATE(v.data_venda) data_ref,g.nome," + pct
                        + " percentual,COUNT(*) qtd,SUM(v.total_liquido) base,SUM(" + val + ") ganho "
                        + "FROM vendas v JOIN garcons g ON v.garcom_id=g.id WHERE v.status='finalizada'");
                if (id > 0) sql.append(" AND v.garcom_id=?");
                if (date != null) sql.append(" AND DATE(v.data_venda)=?");
                sql.append(" GROUP BY DATE(v.data_venda),g.id,g.nome,").append(pct).append(" ORDER BY data_ref DESC,g.nome");
                PreparedStatement ps = DatabaseHelper.getInstance(this).getConnection().prepareStatement(sql.toString());
                int p = 1; if (id > 0) ps.setInt(p++, id); if (date != null) ps.setString(p, date);
                ResultSet rs = ps.executeQuery();
                StringBuilder out = new StringBuilder("=== PORCENTAGENS DO GARCOM ===\n\nGarcom: ")
                        .append(name).append("\nData: ").append(dateLabel).append("\n\n");
                double baseTotal = 0, gainTotal = 0; int sales = 0;
                while (rs.next()) {
                    out.append(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(rs.getDate("data_ref")))
                            .append(" | ").append(rs.getString("nome")).append(" | ")
                            .append(String.format(Locale.getDefault(), "%.2f%%", rs.getDouble("percentual")))
                            .append("\n  Vendas: ").append(rs.getInt("qtd")).append(" | Base: R$ ")
                            .append(FormatUtils.formatMoney(rs.getDouble("base"))).append("\n  Ganho: R$ ")
                            .append(FormatUtils.formatMoney(rs.getDouble("ganho"))).append("\n\n");
                    sales += rs.getInt("qtd"); baseTotal += rs.getDouble("base"); gainTotal += rs.getDouble("ganho");
                }
                rs.close(); ps.close();
                out.append("TOTAL: ").append(sales).append(" vendas | Base R$ ")
                        .append(FormatUtils.formatMoney(baseTotal)).append(" | Ganho R$ ")
                        .append(FormatUtils.formatMoney(gainTotal));
                showReport("Porcentagens do Garcom", out.toString());
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_RELATORIO); }
        }).start();
    }

    private void gerarRelatorioTaxas(int id, String name, String date, String dateLabel) {
        showLoading("Gerando taxas de entrega...");
        new Thread(() -> {
            try {
                StringBuilder sql = new StringBuilder("SELECT DATE(v.data_venda) data_ref,e.nome,COUNT(*) qtd,"
                        + "SUM(v.taxa_entrega) total FROM vendas v JOIN entregadores e ON v.entregador_id=e.id "
                        + "WHERE v.status='finalizada' AND v.para_entrega=1 AND v.taxa_entrega>0");
                if (id > 0) sql.append(" AND v.entregador_id=?");
                if (date != null) sql.append(" AND DATE(v.data_venda)=?");
                sql.append(" GROUP BY DATE(v.data_venda),e.id,e.nome ORDER BY data_ref DESC,e.nome");
                PreparedStatement ps = DatabaseHelper.getInstance(this).getConnection().prepareStatement(sql.toString());
                int p = 1; if (id > 0) ps.setInt(p++, id); if (date != null) ps.setString(p, date);
                ResultSet rs = ps.executeQuery();
                StringBuilder out = new StringBuilder("=== TAXAS POR ENTREGADOR ===\n\nEntregador: ")
                        .append(name).append("\nData: ").append(dateLabel).append("\n\n");
                double total = 0; int count = 0;
                while (rs.next()) {
                    out.append(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(rs.getDate("data_ref")))
                            .append(" | ").append(rs.getString("nome")).append("\n  Entregas: ")
                            .append(rs.getInt("qtd")).append(" | Taxas: R$ ")
                            .append(FormatUtils.formatMoney(rs.getDouble("total"))).append("\n\n");
                    count += rs.getInt("qtd"); total += rs.getDouble("total");
                }
                rs.close(); ps.close();
                out.append("TOTAL: ").append(count).append(" entregas | R$ ").append(FormatUtils.formatMoney(total));
                showReport("Taxas por Entregador", out.toString());
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_RELATORIO); }
        }).start();
    }

    private void gerarRelatorioAuditoria(int id, String name, String dataInicio, String dataFim, String dateLabel) {
        showLoading("Carregando acoes dos usuarios...");
        new Thread(() -> {
            try {
                StringBuilder sql = new StringBuilder("SELECT * FROM auditoria_acoes WHERE 1=1");
                if (id > 0) sql.append(" AND usuario_id=?");
                if (dataInicio != null) sql.append(" AND data_acao>=CONCAT(?,' 00:00:00')");
                if (dataFim != null) sql.append(" AND data_acao<DATE_ADD(CONCAT(?,' 00:00:00'),INTERVAL 1 DAY)");
                sql.append(" ORDER BY id DESC LIMIT 500");
                PreparedStatement ps = DatabaseHelper.getInstance(this).getConnection().prepareStatement(sql.toString());
                int p = 1;
                if (id > 0) ps.setInt(p++, id);
                if (dataInicio != null) ps.setString(p++, dataInicio);
                if (dataFim != null) ps.setString(p, dataFim);
                ResultSet rs = ps.executeQuery();
                StringBuilder out = new StringBuilder("=== ACOES DOS USUARIOS ===\n\nUsuario: ")
                        .append(name).append("\nData: ").append(dateLabel).append("\n\n");
                int count = 0;
                while (rs.next()) {
                    out.append(FormatUtils.formatDate(rs.getString("data_acao"))).append(" | ")
                            .append(FormatUtils.safeString(rs.getString("usuario_nome"))).append("\n  ")
                            .append(rs.getString("acao")).append(" | ")
                            .append(FormatUtils.safeString(rs.getString("modulo")));
                    String table = rs.getString("tabela");
                    if (table != null && !table.isEmpty()) out.append(" | ").append(table).append(" #").append(rs.getLong("registro_id"));
                    out.append("\n  IP: ").append(FormatUtils.safeString(rs.getString("ip"))).append("\n\n");
                    count++;
                }
                rs.close(); ps.close();
                out.append("Acoes exibidas: ").append(count).append(" (limite 500)");
                showReport("Acoes dos Usuarios", out.toString());
            } catch (Exception e) { hideLoading(); showErrorFromException(e, ErrorHandler.CTX_RELATORIO); }
        }).start();
    }

    private void showReport(String title, String report) {
        UserActionLogger.log(this, "GERAR_RELATORIO", "Relatorios", title);
        hideLoading();
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(report)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("WhatsApp", (d, w) -> WhatsAppHelper.enviarCupom(this, report, null))
                .show());
    }

    private void abrirFiltroComissoes() {
        showLoading("Carregando vendedores...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                List<Integer> vendedorIds = new ArrayList<>();
                List<String> vendedorNomes = new ArrayList<>();
                vendedorIds.add(0);
                vendedorNomes.add("Todos os vendedores");

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT id, nome FROM vendedores WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    vendedorIds.add(rs.getInt("id"));
                    vendedorNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> mostrarFiltroComissoes(vendedorIds, vendedorNomes));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    private void mostrarFiltroComissoes(List<Integer> vendedorIds, List<String> vendedorNomes) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        TextView tvVendedor = new TextView(this);
        tvVendedor.setText("Vendedor");
        tvVendedor.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvVendedor);

        Spinner spinnerVendedor = new Spinner(this);
        ArrayAdapter<String> vendedorAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, vendedorNomes);
        vendedorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVendedor.setAdapter(vendedorAdapter);
        layout.addView(spinnerVendedor);

        TextView tvData = new TextView(this);
        tvData.setText("Data");
        tvData.setTypeface(null, android.graphics.Typeface.BOLD);
        tvData.setPadding(0, pad, 0, 0);
        layout.addView(tvData);

        LinearLayout linhaData = new LinearLayout(this);
        linhaData.setOrientation(LinearLayout.HORIZONTAL);
        Button btnData = new Button(this);
        btnData.setText("Todas as datas");
        btnData.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button btnLimpar = new Button(this);
        btnLimpar.setText("Todas");
        linhaData.addView(btnData);
        linhaData.addView(btnLimpar);
        layout.addView(linhaData);

        final String[] dataSql = {null};
        final String[] dataLabel = {"Todas as datas"};
        btnData.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new android.app.DatePickerDialog(this, (view, year, month, day) -> {
                Calendar escolhida = Calendar.getInstance();
                escolhida.set(year, month, day);
                dataSql[0] = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(escolhida.getTime());
                dataLabel[0] = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        .format(escolhida.getTime());
                btnData.setText(dataLabel[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        btnLimpar.setOnClickListener(v -> {
            dataSql[0] = null;
            dataLabel[0] = "Todas as datas";
            btnData.setText(dataLabel[0]);
        });

        new AlertDialog.Builder(this)
                .setTitle("Comissoes por Vendedor")
                .setView(layout)
                .setPositiveButton("Gerar", (d, w) -> {
                    int pos = spinnerVendedor.getSelectedItemPosition();
                    gerarRelatorioComissoes(vendedorIds.get(pos), vendedorNomes.get(pos),
                            dataSql[0], dataLabel[0]);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void gerarRelatorioComissoes(int vendedorId, String vendedorNome,
                                           String dataSql, String dataLabel) {
        showLoading("Gerando relatorio de comissoes...");
        new Thread(() -> {
            try {
                Connection conn = DatabaseHelper.getInstance(this).getConnection();
                String percentualExpr = "COALESCE(v.comissao_percentual, vd.comissao, 0)";
                String valorExpr = "COALESCE(v.comissao_valor, "
                        + "ROUND(v.total_liquido * " + percentualExpr + " / 100, 2))";
                StringBuilder sql = new StringBuilder(
                        "SELECT DATE(v.data_venda) AS data_ref, vd.id AS vendedor_id, vd.nome, "
                                + percentualExpr + " AS percentual, COUNT(v.id) AS qtd, "
                                + "COALESCE(SUM(v.total_liquido),0) AS base_calculo, "
                                + "COALESCE(SUM(" + valorExpr + "),0) AS valor_comissao "
                                + "FROM vendas v JOIN vendedores vd ON v.vendedor_id = vd.id "
                                + "WHERE v.status = 'finalizada'");
                if (vendedorId > 0) sql.append(" AND v.vendedor_id = ?");
                if (dataSql != null) sql.append(" AND DATE(v.data_venda) = ?");
                sql.append(" GROUP BY DATE(v.data_venda), vd.id, vd.nome, ")
                        .append(percentualExpr)
                        .append(" ORDER BY data_ref DESC, vd.nome, percentual");

                PreparedStatement ps = conn.prepareStatement(sql.toString());
                int param = 1;
                if (vendedorId > 0) ps.setInt(param++, vendedorId);
                if (dataSql != null) ps.setString(param, dataSql);
                ResultSet rs = ps.executeQuery();

                StringBuilder sb = new StringBuilder();
                sb.append("=== COMISSOES POR VENDEDOR ===\n\n")
                        .append("Vendedor: ").append(vendedorNome).append("\n")
                        .append("Periodo: ").append(dataLabel).append("\n\n");

                Map<Integer, double[]> totais = new LinkedHashMap<>();
                Map<Integer, String> nomes = new LinkedHashMap<>();
                double totalGeralBase = 0;
                double totalGeralComissao = 0;
                int totalGeralVendas = 0;
                boolean encontrou = false;
                while (rs.next()) {
                    encontrou = true;
                    int id = rs.getInt("vendedor_id");
                    String nome = rs.getString("nome");
                    double percentual = rs.getDouble("percentual");
                    int qtd = rs.getInt("qtd");
                    double base = rs.getDouble("base_calculo");
                    double comissao = rs.getDouble("valor_comissao");
                    java.sql.Date data = rs.getDate("data_ref");
                    String dataFormatada = data != null
                            ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(data)
                            : "Sem data";

                    sb.append(dataFormatada).append(" | ").append(nome).append("\n")
                            .append("  Percentual: ")
                            .append(String.format(Locale.getDefault(), "%.2f%%", percentual)).append("\n")
                            .append("  Vendas: ").append(qtd)
                            .append(" | Base: R$ ").append(FormatUtils.formatMoney(base)).append("\n")
                            .append("  Valor ganho: R$ ").append(FormatUtils.formatMoney(comissao))
                            .append("\n\n");

                    double[] total = totais.get(id);
                    if (total == null) {
                        total = new double[3];
                        totais.put(id, total);
                        nomes.put(id, nome);
                    }
                    total[0] += qtd;
                    total[1] += base;
                    total[2] += comissao;
                    totalGeralVendas += qtd;
                    totalGeralBase += base;
                    totalGeralComissao += comissao;
                }
                rs.close();
                ps.close();

                if (!encontrou) {
                    sb.append("Nenhuma venda com vendedor encontrada para o filtro informado.\n");
                } else {
                    sb.append("--- TOTAL POR VENDEDOR ---\n\n");
                    for (Map.Entry<Integer, double[]> entry : totais.entrySet()) {
                        double[] t = entry.getValue();
                        sb.append(nomes.get(entry.getKey())).append("\n")
                                .append("  Vendas: ").append((int) t[0])
                                .append(" | Base: R$ ").append(FormatUtils.formatMoney(t[1])).append("\n")
                                .append("  Total ganho: R$ ").append(FormatUtils.formatMoney(t[2]))
                                .append("\n\n");
                    }
                    sb.append("=== TOTAL GERAL ===\n")
                            .append("Vendas: ").append(totalGeralVendas).append("\n")
                            .append("Base: R$ ").append(FormatUtils.formatMoney(totalGeralBase)).append("\n")
                            .append("Comissoes: R$ ").append(FormatUtils.formatMoney(totalGeralComissao));
                }

                hideLoading();
                String relatorio = sb.toString();
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Comissoes por Vendedor")
                        .setMessage(relatorio)
                        .setPositiveButton("Fechar", null)
                        .setNeutralButton("WhatsApp", (d, w) ->
                                WhatsAppHelper.enviarCupom(this, relatorio, null))
                        .show());
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }

    private void gerarRelatorio(String tipo) {
        showLoading("Gerando relatorio...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                StringBuilder sb = new StringBuilder();

                switch (tipo) {
                    case "vendas":
                        sb.append("=== RELATORIO DE VENDAS ===\n\n");
                        ResultSet rs = stmt.executeQuery(
                                "SELECT v.id, v.data_venda, v.total_liquido, v.status, c.nome as cliente "
                                        + "FROM vendas v LEFT JOIN clientes c ON v.cliente_id = c.id "
                                        + "ORDER BY v.id DESC LIMIT 50");
                        while (rs.next()) {
                            sb.append("#" + rs.getInt("id") + " | "
                                    + FormatUtils.formatDate(rs.getString("data_venda")) + " | "
                                    + FormatUtils.safeString(rs.getString("cliente")) + " | R$ "
                                    + FormatUtils.formatMoney(rs.getDouble("total_liquido")) + " | "
                                    + rs.getString("status") + "\n");
                        }
                        rs.close();
                        break;

                    case "lucratividade":
                        sb.append("=== RELATORIO DE LUCRATIVIDADE ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT p.descricao, SUM(iv.quantidade) as qtd_vendida, "
                                        + "SUM(iv.total) as total_vendido, "
                                        + "SUM(iv.quantidade * p.preco_custo) as total_custo "
                                        + "FROM itens_venda iv "
                                        + "JOIN produtos p ON iv.produto_id = p.id "
                                        + "JOIN vendas v ON iv.venda_id = v.id "
                                        + "WHERE v.status = 'finalizada' "
                                        + "GROUP BY p.id ORDER BY total_vendido DESC");
                        while (rs.next()) {
                            double vendido = rs.getDouble("total_vendido");
                            double custo = rs.getDouble("total_custo");
                            double lucro = vendido - custo;
                            sb.append(rs.getString("descricao") + " | Qtd: "
                                    + FormatUtils.formatQuantidade(rs.getDouble("qtd_vendida"))
                                    + " | Vendido: R$ " + FormatUtils.formatMoney(vendido)
                                    + " | Lucro: R$ " + FormatUtils.formatMoney(lucro) + "\n");
                        }
                        rs.close();
                        break;

                    case "vendedor":
                        sb.append("=== VENDAS POR VENDEDOR ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT vd.nome, COUNT(v.id) as qtd, SUM(v.total_liquido) as total "
                                        + "FROM vendas v JOIN vendedores vd ON v.vendedor_id = vd.id "
                                        + "WHERE v.status = 'finalizada' GROUP BY vd.id ORDER BY total DESC");
                        while (rs.next()) {
                            sb.append(rs.getString("nome") + " | Vendas: " + rs.getInt("qtd")
                                    + " | Total: R$ " + FormatUtils.formatMoney(rs.getDouble("total")) + "\n");
                        }
                        rs.close();
                        break;

                    case "entregador":
                        sb.append("=== ENTREGAS POR ENTREGADOR ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT e.nome, COUNT(v.id) as qtd, SUM(v.total_liquido) as total "
                                        + "FROM vendas v JOIN entregadores e ON v.entregador_id = e.id "
                                        + "WHERE v.status = 'finalizada' GROUP BY e.id ORDER BY total DESC");
                        while (rs.next()) {
                            sb.append(rs.getString("nome") + " | Entregas: " + rs.getInt("qtd")
                                    + " | Total: R$ " + FormatUtils.formatMoney(rs.getDouble("total")) + "\n");
                        }
                        rs.close();
                        break;

                    case "cliente":
                        sb.append("=== VENDAS POR CLIENTE ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT c.nome, COUNT(v.id) as qtd, SUM(v.total_liquido) as total "
                                        + "FROM vendas v JOIN clientes c ON v.cliente_id = c.id "
                                        + "WHERE v.status = 'finalizada' GROUP BY c.id ORDER BY total DESC");
                        while (rs.next()) {
                            sb.append(rs.getString("nome") + " | Compras: " + rs.getInt("qtd")
                                    + " | Total: R$ " + FormatUtils.formatMoney(rs.getDouble("total")) + "\n");
                        }
                        rs.close();
                        break;

                    case "produtos":
                        sb.append("=== PRODUTOS MAIS VENDIDOS ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT p.descricao, SUM(iv.quantidade) as qtd, SUM(iv.total) as total "
                                        + "FROM itens_venda iv JOIN produtos p ON iv.produto_id = p.id "
                                        + "JOIN vendas v ON iv.venda_id = v.id "
                                        + "WHERE v.status = 'finalizada' GROUP BY p.id ORDER BY qtd DESC");
                        while (rs.next()) {
                            sb.append(rs.getString("descricao") + " | Qtd: "
                                    + FormatUtils.formatQuantidade(rs.getDouble("qtd"))
                                    + " | Total: R$ " + FormatUtils.formatMoney(rs.getDouble("total")) + "\n");
                        }
                        rs.close();
                        break;

                    case "caixa":
                        sb.append("=== FECHAMENTO DE CAIXA ===\n\n");
                        rs = stmt.executeQuery(
                                "SELECT c.*, u.nome as usuario FROM caixa c "
                                        + "LEFT JOIN usuarios u ON c.usuario_id = u.id ORDER BY c.id DESC LIMIT 20");
                        while (rs.next()) {
                            sb.append("Caixa #" + rs.getInt("id") + " | "
                                    + FormatUtils.safeString(rs.getString("usuario")) + "\n"
                                    + "  Abertura: " + FormatUtils.formatDate(rs.getString("data_abertura"))
                                    + " | R$ " + FormatUtils.formatMoney(rs.getDouble("valor_abertura")) + "\n"
                                    + "  Fechamento: " + FormatUtils.formatDate(rs.getString("data_fechamento"))
                                    + " | R$ " + FormatUtils.formatMoney(rs.getDouble("valor_fechamento")) + "\n"
                                    + "  Status: " + rs.getString("status") + "\n\n");
                        }
                        rs.close();
                        break;

                    case "contas_receber":
                        sb.append("=== RELATORIO DE CONTAS A RECEBER ===\n\n");

                        // Resumo geral
                        rs = stmt.executeQuery(
                                "SELECT "
                                + "SUM(CASE WHEN status IN ('pendente','pago_parcial') THEN valor_pendente ELSE 0 END) as total_pendente, "
                                + "SUM(valor_pago) as total_recebido, "
                                + "SUM(valor_original) as total_original, "
                                + "COUNT(CASE WHEN status = 'pendente' THEN 1 END) as qtd_pendente, "
                                + "COUNT(CASE WHEN status = 'pago_parcial' THEN 1 END) as qtd_parcial, "
                                + "COUNT(CASE WHEN status = 'pago' THEN 1 END) as qtd_pago "
                                + "FROM contas_receber WHERE status != 'cancelado'");
                        if (rs.next()) {
                            sb.append("Total Original: R$ " + FormatUtils.formatMoney(rs.getDouble("total_original")) + "\n");
                            sb.append("Total Recebido: R$ " + FormatUtils.formatMoney(rs.getDouble("total_recebido")) + "\n");
                            sb.append("Total Pendente: R$ " + FormatUtils.formatMoney(rs.getDouble("total_pendente")) + "\n");
                            sb.append("Contas Pendentes: " + rs.getInt("qtd_pendente") + "\n");
                            sb.append("Contas Parciais: " + rs.getInt("qtd_parcial") + "\n");
                            sb.append("Contas Pagas: " + rs.getInt("qtd_pago") + "\n");
                        }
                        rs.close();

                        sb.append("\n--- POR CLIENTE ---\n\n");

                        // Detalhamento por cliente
                        rs = stmt.executeQuery(
                                "SELECT c.nome, "
                                + "SUM(cr.valor_original) as total_original, "
                                + "SUM(cr.valor_pago) as total_pago, "
                                + "SUM(cr.valor_pendente) as total_pendente, "
                                + "COUNT(cr.id) as qtd_contas "
                                + "FROM contas_receber cr "
                                + "JOIN clientes c ON cr.cliente_id = c.id "
                                + "WHERE cr.status IN ('pendente', 'pago_parcial') "
                                + "GROUP BY c.id ORDER BY total_pendente DESC");
                        while (rs.next()) {
                            sb.append(rs.getString("nome") + "\n");
                            sb.append("  Contas: " + rs.getInt("qtd_contas")
                                    + " | Original: R$ " + FormatUtils.formatMoney(rs.getDouble("total_original"))
                                    + " | Pago: R$ " + FormatUtils.formatMoney(rs.getDouble("total_pago"))
                                    + " | Pendente: R$ " + FormatUtils.formatMoney(rs.getDouble("total_pendente")) + "\n\n");
                        }
                        rs.close();
                        break;
                }

                stmt.close();
                String relatorio = sb.toString();
                if (relatorio.isEmpty()) relatorio = "Nenhum dado encontrado.";

                hideLoading();
                final String finalRel = relatorio;
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Relatorio")
                            .setMessage(finalRel)
                            .setPositiveButton("Fechar", null)
                            .setNeutralButton("WhatsApp", (d, w) -> {
                                WhatsAppHelper.enviarCupom(this, finalRel, null);
                            })
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_RELATORIO);
            }
        }).start();
    }
}
