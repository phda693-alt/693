package com.pdv.app.utils;
import android.content.Context;
import com.pdv.app.database.DatabaseHelper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
/**
 * Utilitário para formatar e imprimir Ordens de Serviço.
 * v8.0.4 - Implementação inicial de impressão de OS.
 * v8.0.6 - Impressão dos itens (serviços e produtos) da tabela os_itens.
 */
public class OrderPrintManager {
    private Context context;
    private PrinterManager printerManager;
    public OrderPrintManager(Context context) {
        this.context = context;
        this.printerManager = new PrinterManager(context);
    }
    public boolean imprimirOS(Map<String, Object> os) {
        if (os == null) return false;
        StringBuilder sb = new StringBuilder();
        int width = printerManager.getTamanhoPapel() == 80 ? 48 : 32;
        String line = width == 48 ? "------------------------------------------------" : "--------------------------------";
        // Cabeçalho da Empresa
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM empresa LIMIT 1");
            if (rs.next()) {
                sb.append("<b>").append(center(rs.getString("nome_fantasia"), width)).append("</b>\n");
                String cnpj = rs.getString("cnpj");
                if (cnpj != null && !cnpj.isEmpty()) sb.append(center("CNPJ: " + cnpj, width)).append("\n");
                String tel = rs.getString("telefone");
                if (tel != null && !tel.isEmpty()) sb.append(center("Tel: " + tel, width)).append("\n");
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            sb.append("<b>").append(center("ORDEM DE SERVICO", width)).append("</b>\n");
        }
        sb.append(line).append("\n");
        sb.append("<b>").append(center("ORDEM DE SERVICO #" + os.get("numero"), width)).append("</b>\n");
        sb.append(line).append("\n");
        // Dados da OS
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        sb.append("Data: ").append(sdf.format(new Date())).append("\n");
        String cliente = (String) os.get("cliente_cadastrado_nome");
        if (cliente == null || cliente.isEmpty()) cliente = (String) os.get("cliente_nome");
        sb.append("Cliente: ").append(safeStr(cliente)).append("\n");
        sb.append("Equip.: ").append(safeStr(os.get("equipamento"))).append("\n");
        // v8.0.12 - Equipamento detalhado na impressao
        String equipDetalhado = safeStr(os.get("equipamento_detalhado"));
        if (!equipDetalhado.isEmpty()) {
            sb.append("Descricao: ").append(equipDetalhado).append("\n");
        }
        sb.append("Status: ").append(safeStr(os.get("status"))).append("\n");
        // v8.0.8 - Exibir usuario de abertura e fechamento na impressao
        String nomeAbertura = safeStr(os.get("usuario_abertura_nome"));
        if (!nomeAbertura.isEmpty()) {
            sb.append("Aberta por: ").append(nomeAbertura).append("\n");
        }
        String nomeFechamento = safeStr(os.get("usuario_fechamento_nome"));
        if (!nomeFechamento.isEmpty()) {
            sb.append("Fechada por: ").append(nomeFechamento).append("\n");
        }
        sb.append(line).append("\n");
        // Defeito
        sb.append("Defeito Relatado:\n").append(safeStr(os.get("defeito_relatado"))).append("\n");
        
        // v8.0.9 - Defeitos detalhados e solucoes na impressao
        String defeitosDet = safeStr(os.get("defeitos"));
        if (!defeitosDet.isEmpty()) {
            sb.append("Defeitos Detalhados:\n").append(defeitosDet).append("\n");
        }
        
        String solucoes = safeStr(os.get("solucoes"));
        if (!solucoes.isEmpty()) {
            sb.append("Solucoes Aplicadas:\n").append(solucoes).append("\n");
        }
        
        sb.append("\n").append(line).append("\n");
        // Itens da OS (v8.0.6)
        double totalGeral = 0;
        boolean temItens = false;
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            int osId = ((Number) os.get("id")).intValue();
            // Serviços
            PreparedStatement psS = conn.prepareStatement(
                "SELECT * FROM os_itens WHERE os_id = ? AND tipo = 'servico' ORDER BY id ASC");
            psS.setInt(1, osId);
            ResultSet rsS = psS.executeQuery();
            boolean temServicos = false;
            StringBuilder sbServicos = new StringBuilder();
            while (rsS.next()) {
                if (!temServicos) {
                    sbServicos.append("SERVICOS:\n");
                    temServicos = true;
                    temItens = true;
                }
                double qtd = rsS.getDouble("quantidade");
                double preco = rsS.getDouble("preco_unitario");
                double total = rsS.getDouble("total");
                totalGeral += total;
                sbServicos.append("  ").append(rsS.getString("descricao")).append("\n");
                sbServicos.append("  ").append(String.format(Locale.US, "%.2fx R$%.2f = R$%.2f", qtd, preco, total)).append("\n");
            }
            rsS.close();
            psS.close();
            if (temServicos) sb.append(sbServicos);
            // Produtos
            PreparedStatement psP = conn.prepareStatement(
                "SELECT * FROM os_itens WHERE os_id = ? AND tipo = 'produto' ORDER BY id ASC");
            psP.setInt(1, osId);
            ResultSet rsP = psP.executeQuery();
            boolean temProdutos = false;
            StringBuilder sbProdutos = new StringBuilder();
            while (rsP.next()) {
                if (!temProdutos) {
                    sbProdutos.append("PECAS/MATERIAIS:\n");
                    temProdutos = true;
                    temItens = true;
                }
                double qtd = rsP.getDouble("quantidade");
                double preco = rsP.getDouble("preco_unitario");
                double total = rsP.getDouble("total");
                totalGeral += total;
                sbProdutos.append("  ").append(rsP.getString("descricao")).append("\n");
                sbProdutos.append("  ").append(String.format(Locale.US, "%.2fx R$%.2f = R$%.2f", qtd, preco, total)).append("\n");
            }
            rsP.close();
            psP.close();
            if (temProdutos) sb.append(sbProdutos);
        } catch (Exception e) {
            // fallback legado
        }
        // Fallback legado se nao houver itens na tabela os_itens
        if (!temItens) {
            String servico = (String) os.get("servico_nome");
            if (servico != null && !servico.isEmpty()) {
                sb.append("Servico: ").append(servico).append("\n");
            }
            String produto = (String) os.get("produto_nome");
            if (produto != null && !produto.isEmpty()) {
                sb.append("Produto/Peca: ").append(produto).append("\n");
            }
            totalGeral = os.get("valor_servico") != null ? ((Number) os.get("valor_servico")).doubleValue() : 0;
        }
        sb.append(line).append("\n");
        
        double dVal = os.get("desconto_valor") != null ? ((Number) os.get("desconto_valor")).doubleValue() : 0;
        double dPer = os.get("desconto_percentual") != null ? ((Number) os.get("desconto_percentual")).doubleValue() : 0;
        double descontoTotal = (dPer > 0) ? (totalGeral * dPer / 100.0) : dVal;
        
        if (descontoTotal > 0) {
            sb.append(rightJustify("SUBTOTAL: R$ " + String.format(Locale.US, "%.2f", totalGeral), width)).append("\n");
            sb.append(rightJustify("DESCONTO: -R$ " + String.format(Locale.US, "%.2f", descontoTotal), width)).append("\n");
        }
        
        double totalLiquido = totalGeral - descontoTotal;
        if (totalLiquido < 0) totalLiquido = 0;
        
        sb.append("<b>").append(rightJustify("TOTAL GERAL: R$ " + String.format(Locale.US, "%.2f", totalLiquido), width)).append("</b>\n");
        sb.append(line).append("\n");
        String obs = (String) os.get("observacao");
        if (obs != null && !obs.isEmpty()) {
            sb.append("Obs: ").append(obs).append("\n");
            sb.append(line).append("\n");
        }
        sb.append("\n\n").append(center("__________________________", width)).append("\n");
        sb.append(center("Assinatura do Cliente", width)).append("\n\n\n\n");
        return printerManager.imprimirTexto(sb.toString());
    }
    private String safeStr(Object o) {
        return o != null ? o.toString() : "";
    }
    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = (width - text.length()) / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pad; i++) sb.append(" ");
        sb.append(text);
        return sb.toString();
    }
    private String rightJustify(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text;
        int pad = width - text.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pad; i++) sb.append(" ");
        sb.append(text);
        return sb.toString();
    }
}
