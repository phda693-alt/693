package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gerenciador de Multiimpressoras por categoria/tipo de produto.
 *
 * Funcao: quando ativado, permite direcionar produtos de uma venda para
 * impressoras diferentes conforme o tipo/categoria do produto.
 * Exemplo: BEBIDAS -> bar, LANCHES -> cozinha, PIZZAS -> forno.
 */
public class MultiPrinterManager {
    private static final String TAG = "MultiPrinterManager";
    private static final String PREF = "multi_impressoras_config";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RULES = "rules";

    private final Context context;
    private final SharedPreferences prefs;

    public MultiPrinterManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public List<Rule> getRules() {
        List<Rule> out = new ArrayList<>();
        String raw = prefs.getString(KEY_RULES, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            Rule r = Rule.decode(line);
            if (r != null) out.add(r);
        }
        return out;
    }

    public void saveRules(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules != null) {
            for (Rule r : rules) {
                if (r != null && r.categoriaId > 0 && r.tipo != null && !r.tipo.trim().isEmpty()) {
                    sb.append(r.encode()).append("\n");
                }
            }
        }
        prefs.edit().putString(KEY_RULES, sb.toString()).apply();
    }

    public void addOrReplaceRule(Rule nova) {
        List<Rule> rules = getRules();
        boolean replaced = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).categoriaId == nova.categoriaId) {
                rules.set(i, nova);
                replaced = true;
                break;
            }
        }
        if (!replaced) rules.add(nova);
        saveRules(rules);
    }

    public void removeRule(int categoriaId) {
        List<Rule> rules = getRules();
        List<Rule> keep = new ArrayList<>();
        for (Rule r : rules) if (r.categoriaId != categoriaId) keep.add(r);
        saveRules(keep);
    }

    public Rule findRule(int categoriaId) {
        for (Rule r : getRules()) if (r.categoriaId == categoriaId && r.ativo) return r;
        return null;
    }

    /**
     * Imprime comandas separadas por categoria configurada.
     * Retorna quantidade de impressoras/categorias atendidas.
     */
    public int imprimirVendaPorCategorias(int vendaId) {
        if (!isEnabled()) return 0;
        List<Rule> rules = getRules();
        if (rules.isEmpty()) return 0;

        Map<Integer, Rule> ruleMap = new LinkedHashMap<>();
        for (Rule r : rules) {
            if (r.ativo && r.categoriaId > 0) ruleMap.put(r.categoriaId, r);
        }
        if (ruleMap.isEmpty()) return 0;

        Map<Integer, StringBuilder> blocos = new LinkedHashMap<>();
        Map<Integer, Double> totais = new LinkedHashMap<>();

        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT i.descricao_produto, i.quantidade, i.preco_unitario, i.total, " +
                    "p.tipo_produto_id, COALESCE(tp.descricao, 'SEM CATEGORIA') AS categoria " +
                    "FROM itens_venda i " +
                    "LEFT JOIN produtos p ON p.id = i.produto_id " +
                    "LEFT JOIN tipos_produto tp ON tp.id = p.tipo_produto_id " +
                    "WHERE i.venda_id = ? ORDER BY tp.descricao, i.id");
            ps.setInt(1, vendaId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int catId = rs.getInt("tipo_produto_id");
                if (!ruleMap.containsKey(catId)) continue;

                Rule r = ruleMap.get(catId);
                StringBuilder sb = blocos.get(catId);
                if (sb == null) {
                    sb = new StringBuilder();
                    sb.append("<b><center>MULTIIMPRESSORA</center></b>\n");
                    sb.append("<center>").append(safe(r.categoriaNome)).append("</center>\n");
                    sb.append("--------------------------------\n");
                    sb.append("Venda: #").append(vendaId).append("\n");
                    sb.append("Categoria: ").append(safe(r.categoriaNome)).append("\n");
                    sb.append("--------------------------------\n");
                    blocos.put(catId, sb);
                    totais.put(catId, 0.0);
                }
                double qtd = rs.getDouble("quantidade");
                double preco = rs.getDouble("preco_unitario");
                double total = rs.getDouble("total");
                String desc = rs.getString("descricao_produto");
                sb.append(safe(desc)).append("\n");
                sb.append(String.format(Locale.US, "  %.3f x R$ %.2f = R$ %.2f", qtd, preco, total)).append("\n");
                totais.put(catId, totais.get(catId) + total);
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao montar multiimpressao", e);
            return 0;
        }

        int okCount = 0;
        for (Map.Entry<Integer, StringBuilder> e : blocos.entrySet()) {
            int catId = e.getKey();
            Rule r = ruleMap.get(catId);
            String texto = e.getValue()
                    .append("--------------------------------\n")
                    .append(String.format(Locale.US, "TOTAL CATEGORIA: R$ %.2f", totais.get(catId)))
                    .append("\n\n\n")
                    .toString();
            if (imprimirComRegra(r, texto)) okCount++;
        }
        return okCount;
    }



    /**
     * Impressao de pedidos de mesa respeitando a configuracao de Multiimpressoras.
     *
     * Regras:
     * - Se a multiimpressao estiver desativada ou sem regras ativas, retorna 0 para o chamador
     *   usar a impressora comum do sistema.
     * - Se houver regra para a categoria do produto, imprime naquela impressora.
     * - Itens sem categoria/regra ficam para a impressora comum apenas quando nenhum item foi
     *   enviado por multiimpressora. Assim o botao nao perde pedido e respeita a configuracao.
     *
     * Retorna a quantidade de blocos enviados por multiimpressora.
     */
    public int imprimirMesaPorCategorias(int mesaNumero, List<Map<String, Object>> itensMesa, boolean apenasUltimoItem) {
        if (!isEnabled()) return 0;
        if (itensMesa == null || itensMesa.isEmpty()) return 0;

        List<Rule> rules = getRules();
        if (rules.isEmpty()) return 0;

        Map<Integer, Rule> ruleMap = new LinkedHashMap<>();
        for (Rule r : rules) {
            if (r != null && r.ativo && r.categoriaId > 0) {
                ruleMap.put(r.categoriaId, r);
            }
        }
        if (ruleMap.isEmpty()) return 0;

        List<Map<String, Object>> origem = itensMesa;
        if (apenasUltimoItem) {
            origem = new ArrayList<>();
            origem.add(itensMesa.get(itensMesa.size() - 1));
        }

        Map<Integer, StringBuilder> blocos = new LinkedHashMap<>();
        Map<Integer, Double> totais = new LinkedHashMap<>();
        int itensComRegra = 0;

        for (Map<String, Object> item : origem) {
            int produtoId = getInt(item, "produto_id");
            ProdutoCategoria cat = buscarCategoriaProduto(produtoId);
            if (cat.categoriaId <= 0 || !ruleMap.containsKey(cat.categoriaId)) {
                continue;
            }

            Rule r = ruleMap.get(cat.categoriaId);
            StringBuilder sb = blocos.get(cat.categoriaId);
            if (sb == null) {
                sb = new StringBuilder();
                sb.append("<b><center>PEDIDO MESA ").append(mesaNumero).append("</center></b>\n");
                sb.append("<center>").append(apenasUltimoItem ? "ULTIMO PEDIDO" : "PEDIDO COMPLETO").append("</center>\n");
                sb.append("<center>").append(safe(r.categoriaNome).isEmpty() ? safe(cat.categoriaNome) : safe(r.categoriaNome)).append("</center>\n");
                sb.append("--------------------------------\n");
                sb.append("Mesa: ").append(mesaNumero).append("\n");
                sb.append("Categoria: ").append(safe(r.categoriaNome).isEmpty() ? safe(cat.categoriaNome) : safe(r.categoriaNome)).append("\n");
                sb.append("Data: ").append(FormatUtils.getCurrentDateTime()).append("\n");
                sb.append("--------------------------------\n");
                blocos.put(cat.categoriaId, sb);
                totais.put(cat.categoriaId, 0.0);
            }

            String desc = safe(asString(item.get("descricao_produto")));
            double qtd = getDouble(item, "quantidade");
            double preco = getDouble(item, "preco_unitario");
            double total = getDouble(item, "total") + getDouble(item, "adicionais_total");
            String adicionais = safe(asString(item.get("adicionais_descricao")));

            sb.append(desc).append("\n");
            sb.append(String.format(Locale.US, "  %.3f x R$ %.2f = R$ %.2f", qtd, preco, total)).append("\n");
            if (!adicionais.isEmpty()) {
                sb.append("  + ").append(adicionais).append("\n");
            }
            String obsCozinha = safe(asString(item.get("observacao_cozinha")));
            if (!obsCozinha.isEmpty()) {
                sb.append("  OBS COZINHA: ").append(obsCozinha).append("\n");
            }
            totais.put(cat.categoriaId, totais.get(cat.categoriaId) + total);
            itensComRegra++;
        }

        if (itensComRegra <= 0 || blocos.isEmpty()) return 0;

        int okCount = 0;
        for (Map.Entry<Integer, StringBuilder> e : blocos.entrySet()) {
            int catId = e.getKey();
            Rule r = ruleMap.get(catId);
            String texto = e.getValue()
                    .append("--------------------------------\n")
                    .append(String.format(Locale.US, "TOTAL CATEGORIA: R$ %.2f", totais.get(catId)))
                    .append("\n\n\n")
                    .toString();
            if (imprimirComRegra(r, texto)) okCount++;
        }
        return okCount;
    }

    public boolean existeRegraAtivaParaItemMesa(Map<String, Object> item) {
        if (!isEnabled() || item == null) return false;
        int produtoId = getInt(item, "produto_id");
        ProdutoCategoria cat = buscarCategoriaProduto(produtoId);
        return cat.categoriaId > 0 && findRule(cat.categoriaId) != null;
    }

    private ProdutoCategoria buscarCategoriaProduto(int produtoId) {
        ProdutoCategoria out = new ProdutoCategoria();
        if (produtoId <= 0) return out;
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.tipo_produto_id, COALESCE(tp.descricao, 'SEM CATEGORIA') AS categoria " +
                    "FROM produtos p LEFT JOIN tipos_produto tp ON tp.id = p.tipo_produto_id " +
                    "WHERE p.id = ? LIMIT 1");
            ps.setInt(1, produtoId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                out.categoriaId = rs.getInt("tipo_produto_id");
                out.categoriaNome = rs.getString("categoria");
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel identificar categoria do produto " + produtoId + ": " + e.getMessage());
        }
        return out;
    }

    private static class ProdutoCategoria {
        int categoriaId = 0;
        String categoriaNome = "";
    }

    private static int getInt(Map<String, Object> map, String key) {
        try {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v != null) return Integer.parseInt(v.toString());
        } catch (Exception ignored) {}
        return 0;
    }

    private static double getDouble(Map<String, Object> map, String key) {
        try {
            Object v = map.get(key);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v != null) return Double.parseDouble(v.toString().replace(",", "."));
        } catch (Exception ignored) {}
        return 0.0;
    }

    private static String asString(Object v) { return v == null ? "" : v.toString(); }

    public boolean testarRegra(Rule r) {
        if (r == null) return false;
        String teste = "<b><center>TESTE MULTIIMPRESSORA</center></b>\n" +
                "Categoria: " + safe(r.categoriaNome) + "\n" +
                "Impressora: " + safe(r.nomeImpressora) + "\n" +
                "Tipo: " + safe(r.tipo) + "\n" +
                "--------------------------------\n" +
                "Configuracao OK.\n\n\n";
        return imprimirComRegra(r, teste);
    }

    private boolean imprimirComRegra(Rule r, String texto) {
        if (r == null || !r.ativo) return false;
        PrinterManager pm = new PrinterManager(context);
        String oldTipo = pm.getTipoImpressora();
        String oldIp = pm.getIpImpressora();
        int oldPorta = pm.getPortaImpressora();
        String oldMac = pm.getMacBluetooth();
        int oldPapel = pm.getTamanhoPapel();
        String oldDriver = pm.getDriverId();
        String oldPrintServerIp = pm.getPrintServerIp();
        int oldPrintServerPorta = pm.getPrintServerPorta();
        String oldPrintServerImpressora = pm.getPrintServerImpressora();
        try {
            pm.saveConfig(r.tipo, safe(r.ip), r.porta <= 0 ? 9100 : r.porta, safe(r.mac), r.papel <= 0 ? 80 : r.papel);
            if (PrinterManager.TIPO_PRINT_SERVER.equals(r.tipo)) {
                pm.savePrintServerConfig(safe(r.ip), r.porta <= 0 ? 9200 : r.porta, safe(r.impressoraSistema).isEmpty() ? safe(r.nomeImpressora) : safe(r.impressoraSistema));
            }
            pm.saveDriverConfig(safe(r.driverId).isEmpty() ? ThermalPrinterDriver.DRIVER_AUTO : r.driverId);
            return pm.imprimirTexto(texto);
        } catch (Exception ex) {
            Log.e(TAG, "Falha na impressao por regra", ex);
            return false;
        } finally {
            try {
                pm.saveConfig(oldTipo, oldIp, oldPorta, oldMac, oldPapel);
                pm.saveDriverConfig(oldDriver);
                pm.savePrintServerConfig(oldPrintServerIp, oldPrintServerPorta, oldPrintServerImpressora);
            } catch (Exception ignored) {}
        }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    public static class Rule {
        public boolean ativo = true;
        public int categoriaId;
        public String categoriaNome = "";
        public String nomeImpressora = "";
        public String tipo = PrinterManager.TIPO_REDE;
        public String ip = "";
        public int porta = 9100;
        public String mac = "";
        public int papel = 80;
        public String driverId = ThermalPrinterDriver.DRIVER_AUTO;
        public String impressoraSistema = "";

        public String encode() {
            return (ativo ? "1" : "0") + "|" + categoriaId + "|" + enc(categoriaNome) + "|" + enc(nomeImpressora) + "|" +
                    enc(tipo) + "|" + enc(ip) + "|" + porta + "|" + enc(mac) + "|" + papel + "|" + enc(driverId) + "|" + enc(impressoraSistema);
        }

        public static Rule decode(String line) {
            try {
                String[] p = line.split("\\|", -1);
                if (p.length < 9) return null;
                Rule r = new Rule();
                r.ativo = "1".equals(p[0]);
                r.categoriaId = Integer.parseInt(p[1]);
                r.categoriaNome = dec(p[2]);
                r.nomeImpressora = dec(p[3]);
                r.tipo = dec(p[4]);
                r.ip = dec(p[5]);
                r.porta = Integer.parseInt(p[6]);
                r.mac = dec(p[7]);
                r.papel = Integer.parseInt(p[8]);
                r.driverId = p.length > 9 ? dec(p[9]) : ThermalPrinterDriver.DRIVER_AUTO;
                r.impressoraSistema = p.length > 10 ? dec(p[10]) : "";
                if (r.driverId == null || r.driverId.trim().isEmpty()) r.driverId = ThermalPrinterDriver.DRIVER_AUTO;
                return r;
            } catch (Exception e) { return null; }
        }

        private static String enc(String s) {
            if (s == null) return "";
            return s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A");
        }
        private static String dec(String s) {
            if (s == null) return "";
            return s.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%");
        }
    }
}
