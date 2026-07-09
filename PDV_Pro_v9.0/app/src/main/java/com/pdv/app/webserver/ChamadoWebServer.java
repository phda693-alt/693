package com.pdv.app.webserver;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.SenhaChamadoStore;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.Locale;

/**
 * Servidor Web de Chamados do PDV Pro.
 * 
 * Funcionalidades:
 * - API REST para gerenciamento de chamados
 * - Servir páginas HTML/CSS/JS do painel de chamados
 * - Servir páginas do gerenciador de chamados (painel de chamada)
 * - Integração com banco de dados MySQL via DatabaseHelper
 * - Suporte a TTS (Text-to-Speech) e sinais sonoros
 */
public class ChamadoWebServer extends NanoHTTPD {
    private static final String TAG = "ChamadoWebServer";
    private final Context context;
    private final AssetManager assetManager;
    private static ChamadoWebServer instance;
    private boolean tabelaCriada = false;

    public ChamadoWebServer(Context context, int port) {
        super(port);
        this.context = context.getApplicationContext();
        this.assetManager = context.getAssets();
    }

    public static synchronized ChamadoWebServer getInstance(Context context, int port) {
        if (instance == null) {
            instance = new ChamadoWebServer(context, port);
        }
        return instance;
    }

    public static synchronized ChamadoWebServer getInstance() {
        return instance;
    }

    /**
     * v6.9.1 - Tornar inicializarTabela síncrona para garantir que as tabelas
     * existam antes do servidor aceitar requisições de pedidos.
     * Anteriormente era assíncrona (new Thread), causando race condition.
     */
    /**
     * Marca as tabelas como prontas para uso.
     * As tabelas chamados, pedidos_web, itens_pedido_web e itens_pedido_web_adicionais
     * sao criadas/verificadas pelo DatabaseHelper na inicializacao do sistema (SplashActivity).
     * Nao e necessario criar as tabelas aqui.
     */
    public void inicializarTabela() {
        tabelaCriada = true;
        Log.d(TAG, "Tabelas web marcadas como prontas (criadas pelo DatabaseHelper na inicializacao)");
    }

    /**
     * Garante que as tabelas de pedidos web estejam prontas.
     * As tabelas sao criadas pelo DatabaseHelper na inicializacao do sistema.
     * Este metodo apenas marca a flag para evitar verificacoes redundantes.
     */
    private void garantirTabelasPedidosWeb() {
        tabelaCriada = true;
    }

    @Override
    public Response serve(String method, String uri, Map<String, String> params, Map<String, String> headers, String body) {
        Log.d(TAG, method + " " + uri);

        // CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return newResponse(Status.OK, "text/plain", "");
        }

        // API Routes
        if (uri.startsWith("/api/")) {
            return handleApi(method, uri, params, body);
        }

        // Serve static files from assets/web/
        return serveAsset(uri);
    }

    /**
     * Retorna o IP local do dispositivo na rede WiFi.
     */
    public String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    private Response handleApi(String method, String uri, Map<String, String> params, String body) {
        try {
            switch (uri) {
                case "/api/chamados":
                    if ("GET".equalsIgnoreCase(method)) return getChamados(params);
                    if ("POST".equalsIgnoreCase(method)) return criarChamado(params, body);
                    break;
                case "/api/chamados/chamar":
                    if ("POST".equalsIgnoreCase(method)) return chamarChamado(params, body);
                    break;
                case "/api/chamados/atender":
                    if ("POST".equalsIgnoreCase(method)) return atenderChamado(params, body);
                    break;
                case "/api/chamados/cancelar":
                    if ("POST".equalsIgnoreCase(method)) return cancelarChamado(params, body);
                    break;
                case "/api/chamados/rechamar":
                    if ("POST".equalsIgnoreCase(method)) return rechamarChamado(params, body);
                    break;
                case "/api/chamados/stats":
                    if ("GET".equalsIgnoreCase(method)) return getStats();
                    break;
                case "/api/chamados/historico":
                    if ("GET".equalsIgnoreCase(method)) return getHistorico(params);
                    break;
                case "/api/chamados/limpar":
                    if ("POST".equalsIgnoreCase(method)) return limparAtendidos();
                    break;
                case "/api/chamados/ultimo-chamado":
                    if ("GET".equalsIgnoreCase(method)) return getUltimoChamado();
                    break;
                case "/api/chamados/fila":
                    if ("GET".equalsIgnoreCase(method)) return getFilaChamados();
                    break;
                case "/api/ping":
                    return newResponse(Status.OK, "application/json", "{\"status\":\"ok\",\"server\":\"PDV Pro Chamados v1.0\"}");

                // === API do Painel Web de Senhas ===
                case "/api/senhas":
                    if ("GET".equalsIgnoreCase(method)) return getSenhasPainel();
                    break;
                case "/api/senhas/chamar":
                    if ("POST".equalsIgnoreCase(method)) return chamarSenhaPainel(params);
                    break;
                case "/api/senhas/limpar":
                    if ("POST".equalsIgnoreCase(method)) return limparSenhasPainel();
                    break;

                // === API do Cardápio Digital (Pedidos via QR Code) ===
                case "/api/cardapio/empresa":
                    if ("GET".equalsIgnoreCase(method)) return getEmpresaCardapio();
                    break;
                case "/api/cardapio/produtos":
                    if ("GET".equalsIgnoreCase(method)) return getProdutosCardapio();
                    break;
                case "/api/cardapio/pedido":
                    if ("POST".equalsIgnoreCase(method)) return criarPedidoWeb(params, body);
                    break;
                case "/api/cardapio/pedido/status":
                    if ("GET".equalsIgnoreCase(method)) return getStatusPedidoWeb(params);
                    break;
                case "/api/cardapio/pedidos":
                    if ("GET".equalsIgnoreCase(method)) return getPedidosWeb(params);
                    break;
                case "/api/cardapio/pedido/atualizar-status":
                    if ("POST".equalsIgnoreCase(method)) return atualizarStatusPedidoWeb(params, body);
                    break;

                // === API do Painel da Cozinha ===
                case "/api/cozinha/pedidos":
                    if ("GET".equalsIgnoreCase(method)) return getPedidosCozinha(params);
                    break;
            }
            return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Endpoint nao encontrado\"}");
        } catch (Exception e) {
            Log.e(TAG, "Erro na API: " + e.getMessage(), e);
            return newResponse(Status.INTERNAL_ERROR, "application/json", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private Response getChamados(Map<String, String> params) throws Exception {
        String status = params.get("status");
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        String sql = "SELECT * FROM chamados";
        if (status != null && !status.isEmpty() && !"todos".equals(status)) {
            sql += " WHERE status = ?";
        }
        sql += " ORDER BY FIELD(prioridade, 'urgente', 'alta', 'normal', 'baixa'), data_criacao ASC";

        PreparedStatement ps = conn.prepareStatement(sql);
        if (status != null && !status.isEmpty() && !"todos".equals(status)) {
            ps.setString(1, status);
        }
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append(chamadoToJson(rs));
            first = false;
        }
        json.append("]");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json.toString());
    }

    private Response criarChamado(Map<String, String> params, String body) throws Exception {
        String clienteNome = getParam(params, body, "cliente_nome");
        String comandaStr = getParam(params, body, "comanda_numero");
        String tipo = getParam(params, body, "tipo");
        String descricao = getParam(params, body, "descricao");
        String prioridade = getParam(params, body, "prioridade");

        if (clienteNome == null || clienteNome.isEmpty()) {
            return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"Nome do cliente e obrigatorio\"}");
        }

        int comandaNum = 0;
        try { if (comandaStr != null) comandaNum = Integer.parseInt(comandaStr); } catch (Exception ignored) {}
        if (tipo == null || tipo.isEmpty()) tipo = "comanda";
        if (prioridade == null || prioridade.isEmpty()) prioridade = "normal";

        Connection conn = DatabaseHelper.getInstance(context).getConnection();

        // Gerar próximo número de chamado do dia
        String hoje = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        PreparedStatement psMax = conn.prepareStatement(
            "SELECT COALESCE(MAX(numero_chamado), 0) + 1 FROM chamados WHERE DATE(data_criacao) = ?"
        );
        psMax.setString(1, hoje);
        ResultSet rsMax = psMax.executeQuery();
        int numeroChamado = 1;
        if (rsMax.next()) numeroChamado = rsMax.getInt(1);
        rsMax.close();
        psMax.close();

        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO chamados (numero_chamado, cliente_nome, comanda_numero, tipo, descricao, prioridade, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'aguardando')",
            Statement.RETURN_GENERATED_KEYS
        );
        ps.setInt(1, numeroChamado);
        ps.setString(2, clienteNome);
        ps.setInt(3, comandaNum);
        ps.setString(4, tipo);
        ps.setString(5, descricao != null ? descricao : "");
        ps.setString(6, prioridade);
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        int id = 0;
        if (keys.next()) id = keys.getInt(1);
        keys.close();
        ps.close();

        return newResponse(Status.CREATED, "application/json",
            "{\"success\":true,\"id\":" + id + ",\"numero_chamado\":" + numeroChamado + ",\"message\":\"Chamado #" + numeroChamado + " criado com sucesso\"}");
    }

    private Response chamarChamado(Map<String, String> params, String body) throws Exception {
        int id = getIntParam(params, body, "id");
        if (id <= 0) return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID invalido\"}");

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE chamados SET status = 'chamando', data_chamada = NOW(), vezes_chamado = vezes_chamado + 1 WHERE id = ?"
        );
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        ps.close();

        if (rows == 0) return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Chamado nao encontrado\"}");

        // Buscar dados do chamado para retorno
        PreparedStatement psGet = conn.prepareStatement("SELECT * FROM chamados WHERE id = ?");
        psGet.setInt(1, id);
        ResultSet rs = psGet.executeQuery();
        String json = "{}";
        if (rs.next()) json = chamadoToJson(rs);
        rs.close();
        psGet.close();

        return newResponse(Status.OK, "application/json", "{\"success\":true,\"chamado\":" + json + "}");
    }

    private Response rechamarChamado(Map<String, String> params, String body) throws Exception {
        int id = getIntParam(params, body, "id");
        if (id <= 0) return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID invalido\"}");

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE chamados SET status = 'chamando', data_chamada = NOW(), vezes_chamado = vezes_chamado + 1 WHERE id = ?"
        );
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        ps.close();

        if (rows == 0) return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Chamado nao encontrado\"}");

        PreparedStatement psGet = conn.prepareStatement("SELECT * FROM chamados WHERE id = ?");
        psGet.setInt(1, id);
        ResultSet rs = psGet.executeQuery();
        String json = "{}";
        if (rs.next()) json = chamadoToJson(rs);
        rs.close();
        psGet.close();

        return newResponse(Status.OK, "application/json", "{\"success\":true,\"chamado\":" + json + "}");
    }

    private Response atenderChamado(Map<String, String> params, String body) throws Exception {
        int id = getIntParam(params, body, "id");
        String atendente = getParam(params, body, "atendente");
        if (id <= 0) return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID invalido\"}");

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE chamados SET status = 'atendido', data_atendido = NOW(), atendente = ? WHERE id = ?"
        );
        ps.setString(1, atendente != null ? atendente : "");
        ps.setInt(2, id);
        int rows = ps.executeUpdate();
        ps.close();

        if (rows == 0) return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Chamado nao encontrado\"}");
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"message\":\"Chamado atendido com sucesso\"}");
    }

    private Response cancelarChamado(Map<String, String> params, String body) throws Exception {
        int id = getIntParam(params, body, "id");
        if (id <= 0) return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID invalido\"}");

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE chamados SET status = 'cancelado' WHERE id = ?"
        );
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        ps.close();

        if (rows == 0) return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Chamado nao encontrado\"}");
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"message\":\"Chamado cancelado\"}");
    }

    private Response getStats() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        String hoje = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        PreparedStatement ps = conn.prepareStatement(
            "SELECT " +
            "  SUM(CASE WHEN status = 'aguardando' THEN 1 ELSE 0 END) as aguardando," +
            "  SUM(CASE WHEN status = 'chamando' THEN 1 ELSE 0 END) as chamando," +
            "  SUM(CASE WHEN status = 'atendido' AND DATE(data_atendido) = ? THEN 1 ELSE 0 END) as atendidos_hoje," +
            "  SUM(CASE WHEN status = 'cancelado' AND DATE(data_criacao) = ? THEN 1 ELSE 0 END) as cancelados_hoje," +
            "  COUNT(*) as total " +
            "FROM chamados WHERE DATE(data_criacao) = ? OR status IN ('aguardando','chamando')"
        );
        ps.setString(1, hoje);
        ps.setString(2, hoje);
        ps.setString(3, hoje);
        ResultSet rs = ps.executeQuery();

        String json = "{}";
        if (rs.next()) {
            json = "{\"aguardando\":" + rs.getInt("aguardando") +
                   ",\"chamando\":" + rs.getInt("chamando") +
                   ",\"atendidos_hoje\":" + rs.getInt("atendidos_hoje") +
                   ",\"cancelados_hoje\":" + rs.getInt("cancelados_hoje") +
                   ",\"total\":" + rs.getInt("total") + "}";
        }
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json);
    }

    private Response getHistorico(Map<String, String> params) throws Exception {
        String data = params.get("data");
        if (data == null || data.isEmpty()) {
            data = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        }
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM chamados WHERE DATE(data_criacao) = ? ORDER BY data_criacao DESC LIMIT 200"
        );
        ps.setString(1, data);
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append(chamadoToJson(rs));
            first = false;
        }
        json.append("]");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json.toString());
    }

    private Response limparAtendidos() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "DELETE FROM chamados WHERE status IN ('atendido', 'cancelado')"
        );
        int rows = ps.executeUpdate();
        ps.close();
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"removidos\":" + rows + "}");
    }

    private Response getUltimoChamado() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM chamados WHERE status = 'chamando' ORDER BY data_chamada DESC LIMIT 1"
        );
        ResultSet rs = ps.executeQuery();
        String json = "null";
        if (rs.next()) json = chamadoToJson(rs);
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", "{\"chamado\":" + json + "}");
    }

    private Response getFilaChamados() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM chamados WHERE status IN ('aguardando','chamando') " +
            "ORDER BY FIELD(status, 'chamando', 'aguardando'), " +
            "FIELD(prioridade, 'urgente', 'alta', 'normal', 'baixa'), data_criacao ASC"
        );
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append(chamadoToJson(rs));
            first = false;
        }
        json.append("]");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json.toString());
    }

    // === Helpers ===

    private String chamadoToJson(ResultSet rs) throws SQLException {
        return "{" +
            "\"id\":" + rs.getInt("id") + "," +
            "\"numero_chamado\":" + rs.getInt("numero_chamado") + "," +
            "\"cliente_nome\":\"" + escapeJson(rs.getString("cliente_nome")) + "\"," +
            "\"comanda_numero\":" + rs.getInt("comanda_numero") + "," +
            "\"tipo\":\"" + escapeJson(rs.getString("tipo")) + "\"," +
            "\"descricao\":\"" + escapeJson(rs.getString("descricao") != null ? rs.getString("descricao") : "") + "\"," +
            "\"status\":\"" + escapeJson(rs.getString("status")) + "\"," +
            "\"prioridade\":\"" + escapeJson(rs.getString("prioridade")) + "\"," +
            "\"data_criacao\":\"" + formatTimestampBR(rs.getTimestamp("data_criacao")) + "\"," +
            "\"data_chamada\":\"" + formatTimestampBR(rs.getTimestamp("data_chamada")) + "\"," +
            "\"data_atendido\":\"" + formatTimestampBR(rs.getTimestamp("data_atendido")) + "\"," +
            "\"vezes_chamado\":" + rs.getInt("vezes_chamado") + "," +
            "\"observacao\":\"" + escapeJson(rs.getString("observacao") != null ? rs.getString("observacao") : "") + "\"," +
            "\"atendente\":\"" + escapeJson(rs.getString("atendente") != null ? rs.getString("atendente") : "") + "\"" +
            "}";
    }

    /**
     * Formata um Timestamp para o formato dd/MM/yyyy HH:mm:ss.
     */
    private static String formatTimestampBR(Timestamp ts) {
        if (ts == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("pt", "BR"));
            return sdf.format(ts);
        } catch (Exception e) {
            return ts.toString();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String getParam(Map<String, String> params, String body, String key) {
        if (params.containsKey(key)) return params.get(key);
        // Try JSON body
        String jsonBody = params.get("__json_body__");
        if (jsonBody == null) jsonBody = body;
        if (jsonBody != null && jsonBody.contains("\"" + key + "\"")) {
            return extractJsonValue(jsonBody, key);
        }
        return null;
    }

    private int getIntParam(Map<String, String> params, String body, String key) {
        String val = getParam(params, body, key);
        if (val == null) return 0;
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
    }

    private String extractJsonValue(String json, String key) {
        try {
            int keyIdx = json.indexOf("\"" + key + "\"");
            if (keyIdx < 0) return null;
            int colonIdx = json.indexOf(':', keyIdx);
            if (colonIdx < 0) return null;
            int start = colonIdx + 1;
            while (start < json.length() && json.charAt(start) == ' ') start++;
            if (start >= json.length()) return null;

            if (json.charAt(start) == '"') {
                int end = json.indexOf('"', start + 1);
                if (end < 0) return null;
                return json.substring(start + 1, end);
            } else {
                int end = start;
                while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ' ') end++;
                return json.substring(start, end).trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Response serveAsset(String uri) {
        if (uri.equals("/") || uri.isEmpty()) uri = "/index.html";
        if (uri.equals("/gerenciador") || uri.equals("/gerenciador/")) uri = "/gerenciador.html";
        if (uri.equals("/painel") || uri.equals("/painel/")) uri = "/painel.html";
        if (uri.equals("/senhas") || uri.equals("/senhas/")) uri = "/senhas.html";
        if (uri.startsWith("/cardapio") && !uri.contains(".")) uri = "/cardapio.html" + (uri.contains("?") ? uri.substring(uri.indexOf('?')) : "");
        if (uri.equals("/pedidos-web") || uri.equals("/pedidos-web/")) uri = "/pedidos-web.html";
        if (uri.equals("/cozinha") || uri.equals("/cozinha/")) uri = "/cozinha.html";

        String assetPath = "web" + uri;
        String mimeType = getMimeType(uri);

        try {
            InputStream is = assetManager.open(assetPath);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            is.close();
            return newBinaryResponse(Status.OK, mimeType, bos.toByteArray());
        } catch (IOException e) {
            return newResponse(Status.NOT_FOUND, "text/html",
                "<!DOCTYPE html><html><body style='background:#0A0E27;color:#fff;font-family:sans-serif;text-align:center;padding:50px'>" +
                "<h1 style='color:#00E5FF'>404</h1><p>Pagina nao encontrada</p></body></html>");
        }
    }


    // ==========================================
    // API DO PAINEL WEB DE SENHAS
    // ==========================================

    private Response getSenhasPainel() {
        List<SenhaChamadoStore.SenhaItem> itens = SenhaChamadoStore.listar(context);
        int aguardando = 0;
        int chamadas = 0;
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,");
        json.append("\"atualizado_em\":\"").append(escapeJson(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", new Locale("pt", "BR")).format(new Date()))).append("\",");
        json.append("\"senhas\":[");
        boolean first = true;
        for (SenhaChamadoStore.SenhaItem item : itens) {
            if (item == null) continue;
            if (item.foiChamado()) chamadas++; else aguardando++;
            if (!first) json.append(',');
            first = false;
            json.append('{');
            json.append("\"senha\":\"").append(SenhaChamadoStore.formatarSenhaNumero(item.senha)).append("\",");
            json.append("\"venda_id\":").append(item.vendaId).append(',');
            json.append("\"cliente\":\"").append(escapeJson(item.cliente)).append("\",");
            json.append("\"total\":").append(String.format(Locale.US, "%.2f", item.total)).append(',');
            json.append("\"status\":\"").append(item.foiChamado() ? "CHAMADA" : "AGUARDANDO").append("\",");
            json.append("\"criado_em\":\"").append(escapeJson(SenhaChamadoStore.formatarHora(item.criadoEm))).append("\",");
            json.append("\"chamado_em\":\"").append(escapeJson(item.foiChamado() ? SenhaChamadoStore.formatarHora(item.chamadoEm) : "")).append("\"");
            json.append('}');
        }
        json.append("],");
        json.append("\"aguardando\":").append(aguardando).append(',');
        json.append("\"chamadas\":").append(chamadas).append(',');
        json.append("\"total\":").append(itens.size());
        json.append('}');
        return newResponse(Status.OK, "application/json; charset=utf-8", json.toString());
    }

    private Response chamarSenhaPainel(Map<String, String> params) {
        int vendaId = getIntParam(params != null ? params : new HashMap<String, String>(), "", "vendaId");
        if (vendaId <= 0) vendaId = getIntParam(params != null ? params : new HashMap<String, String>(), "", "venda_id");

        List<SenhaChamadoStore.SenhaItem> itens = SenhaChamadoStore.listar(context);
        SenhaChamadoStore.SenhaItem alvo = null;

        if (vendaId > 0) {
            for (SenhaChamadoStore.SenhaItem item : itens) {
                if (item != null && item.vendaId == vendaId) {
                    alvo = item;
                    break;
                }
            }
        } else {
            for (SenhaChamadoStore.SenhaItem item : itens) {
                if (item != null && !item.foiChamado()) {
                    alvo = item;
                    break;
                }
            }
        }

        if (alvo == null) {
            return newResponse(Status.NOT_FOUND, "application/json", "{\"success\":false,\"error\":\"Nenhuma senha aguardando chamada\"}");
        }

        SenhaChamadoStore.marcarChamado(context, alvo.vendaId);
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"senha\":\"" + SenhaChamadoStore.formatarSenhaNumero(alvo.senha) + "\",\"venda_id\":" + alvo.vendaId + ",\"cliente\":\"" + escapeJson(alvo.cliente) + "\"}");
    }

    private Response limparSenhasPainel() {
        SenhaChamadoStore.zerarAtendimento(context);
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"message\":\"Senhas de atendimento zeradas. Proxima senha: 001\"}");
    }

    // ==========================================
    // API DO CARDÁPIO DIGITAL (Pedidos via QR Code)
    // ==========================================

    private Response getEmpresaCardapio() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT nome_fantasia, razao_social FROM empresa LIMIT 1");
        String nome = "Cardapio Digital";
        if (rs.next()) {
            String fantasia = rs.getString("nome_fantasia");
            String razao = rs.getString("razao_social");
            nome = (fantasia != null && !fantasia.isEmpty()) ? fantasia : (razao != null ? razao : "Cardapio Digital");
        }
        rs.close();
        stmt.close();
        return newResponse(Status.OK, "application/json", "{\"nome\":\"" + escapeJson(nome) + "\"}");
    }

    private Response getProdutosCardapio() throws Exception {
        Connection conn = DatabaseHelper.getInstance(context).getConnection();

        // Buscar categorias (tipos de produto)
        Statement stmtCat = conn.createStatement();
        ResultSet rsCat = stmtCat.executeQuery("SELECT id, descricao FROM tipos_produto WHERE ativo = 1 ORDER BY descricao");
        StringBuilder catJson = new StringBuilder("[");
        boolean firstCat = true;
        while (rsCat.next()) {
            if (!firstCat) catJson.append(",");
            catJson.append("{\"id\":").append(rsCat.getInt("id"))
                   .append(",\"descricao\":\"")
                   .append(escapeJson(rsCat.getString("descricao")))
                   .append("\"}");
            firstCat = false;
        }
        catJson.append("]");
        rsCat.close();
        stmtCat.close();

        // Buscar produtos ativos
        Statement stmtProd = conn.createStatement();
        ResultSet rsProd = stmtProd.executeQuery(
            "SELECT p.id, p.descricao, p.preco_venda, p.estoque, p.tipo_produto_id, " +
            "p.foto_base64, COALESCE(tp.descricao, '') as tipo_desc " +
            "FROM produtos p LEFT JOIN tipos_produto tp ON p.tipo_produto_id = tp.id " +
            "WHERE p.ativo = 1 AND p.preco_venda > 0 ORDER BY tp.descricao, p.descricao"
        );
        StringBuilder prodJson = new StringBuilder("[");
        boolean firstProd = true;
        while (rsProd.next()) {
            if (!firstProd) prodJson.append(",");
            String fotoBase64 = rsProd.getString("foto_base64");
            boolean temFoto = fotoBase64 != null && !fotoBase64.isEmpty() && fotoBase64.length() > 10;
            prodJson.append("{\"id\":").append(rsProd.getInt("id"))
                    .append(",\"descricao\":\"")
                    .append(escapeJson(rsProd.getString("descricao")))
                    .append("\",\"preco_venda\":")
                    .append(rsProd.getDouble("preco_venda"))
                    .append(",\"estoque\":")
                    .append(rsProd.getDouble("estoque"))
                    .append(",\"tipo_produto_id\":")
                    .append(rsProd.getInt("tipo_produto_id"))
                    .append(",\"tipo_produto_desc\":\"")
                    .append(escapeJson(rsProd.getString("tipo_desc")))
                    .append("\",\"controla_estoque\":false");
            if (temFoto) {
                prodJson.append(",\"foto_base64\":\"")
                        .append(fotoBase64)
                        .append("\"");
            } else {
                prodJson.append(",\"foto_base64\":null");
            }
            prodJson.append("}");
            firstProd = false;
        }
        prodJson.append("]");
        rsProd.close();
        stmtProd.close();

        // Buscar adicionais por tipo de produto
        Statement stmtAd = conn.createStatement();
        ResultSet rsAd = stmtAd.executeQuery(
            "SELECT tpa.tipo_produto_id, a.id, a.descricao, a.preco " +
            "FROM tipo_produto_adicionais tpa " +
            "JOIN adicionais a ON tpa.adicional_id = a.id " +
            "WHERE a.ativo = 1 ORDER BY a.descricao"
        );
        // Agrupar por tipo_produto_id
        Map<Integer, StringBuilder> adicionaisMap = new LinkedHashMap<>();
        Map<Integer, Boolean> adicionaisFirst = new LinkedHashMap<>();
        while (rsAd.next()) {
            int tipoId = rsAd.getInt("tipo_produto_id");
            if (!adicionaisMap.containsKey(tipoId)) {
                adicionaisMap.put(tipoId, new StringBuilder("["));
                adicionaisFirst.put(tipoId, true);
            }
            StringBuilder sb = adicionaisMap.get(tipoId);
            if (!adicionaisFirst.get(tipoId)) sb.append(",");
            sb.append("{\"id\":").append(rsAd.getInt("id"))
              .append(",\"descricao\":\"")
              .append(escapeJson(rsAd.getString("descricao")))
              .append("\",\"preco\":")
              .append(rsAd.getDouble("preco"))
              .append("}");
            adicionaisFirst.put(tipoId, false);
        }
        rsAd.close();
        stmtAd.close();

        StringBuilder adJson = new StringBuilder("{");
        boolean firstAd = true;
        for (Map.Entry<Integer, StringBuilder> entry : adicionaisMap.entrySet()) {
            if (!firstAd) adJson.append(",");
            adJson.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue()).append("]");
            firstAd = false;
        }
        adJson.append("}");

        String result = "{\"produtos\":" + prodJson + ",\"categorias\":" + catJson + ",\"adicionais_por_tipo\":" + adJson + "}";
        return newResponse(Status.OK, "application/json", result);
    }

    /**
     * v6.9.1 - Corrigido: adicionado garantirTabelasPedidosWeb() antes de inserir,
     * try-catch robusto para não propagar erros de integração com mesas,
     * e calcula total a partir dos itens quando necessário.
     */
    private Response criarPedidoWeb(Map<String, String> params, String body) throws Exception {
        // v6.9.1 - Garantir que as tabelas existam antes de inserir
        garantirTabelasPedidosWeb();

        // Obter o body JSON
        String jsonBody = params.get("__json_body__");
        if (jsonBody == null) jsonBody = body;

        String clienteNome = getParam(params, body, "cliente_nome");
        String mesaStr = getParam(params, body, "mesa_numero");
        String observacao = getParam(params, body, "observacao");

        if (clienteNome == null || clienteNome.isEmpty()) {
            return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"Nome do cliente e obrigatorio\"}");
        }
        int mesaNumero = 0;
        try { if (mesaStr != null) mesaNumero = Integer.parseInt(mesaStr.trim()); } catch (Exception ignored) {}
        if (mesaNumero <= 0) {
            return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"Numero da mesa invalido\"}");
        }

        // v6.9.1 - Calcular total a partir do JSON de forma mais robusta
        double totalPedido = 0;
        try {
            // Tentar extrair o total do nível raiz do JSON (após o array de itens)
            if (jsonBody != null) {
                // Buscar o último "total" no JSON (que é o total do pedido, não dos itens)
                int lastTotalIdx = jsonBody.lastIndexOf("\"total\"");
                if (lastTotalIdx >= 0) {
                    int colonIdx = jsonBody.indexOf(':', lastTotalIdx);
                    if (colonIdx >= 0) {
                        int start = colonIdx + 1;
                        while (start < jsonBody.length() && jsonBody.charAt(start) == ' ') start++;
                        int end = start;
                        while (end < jsonBody.length() && jsonBody.charAt(end) != ',' && jsonBody.charAt(end) != '}' && jsonBody.charAt(end) != ' ') end++;
                        String totalStr = jsonBody.substring(start, end).trim();
                        totalPedido = Double.parseDouble(totalStr);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao extrair total do pedido, sera calculado dos itens: " + e.getMessage());
        }

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        if (conn == null || conn.isClosed()) {
            return newResponse(Status.INTERNAL_ERROR, "application/json", "{\"error\":\"Erro de conexao com o banco de dados\"}");
        }

        // Gerar próximo número de pedido do dia
        String hoje = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        PreparedStatement psMax = conn.prepareStatement(
            "SELECT COALESCE(MAX(numero_pedido), 0) + 1 FROM pedidos_web WHERE DATE(data_criacao) = ?"
        );
        psMax.setString(1, hoje);
        ResultSet rsMax = psMax.executeQuery();
        int numeroPedido = 1;
        if (rsMax.next()) numeroPedido = rsMax.getInt(1);
        rsMax.close();
        psMax.close();

        // Inserir pedido
        PreparedStatement psPedido = conn.prepareStatement(
            "INSERT INTO pedidos_web (numero_pedido, mesa_numero, cliente_nome, observacao, total, status) " +
            "VALUES (?, ?, ?, ?, ?, 'pendente')",
            Statement.RETURN_GENERATED_KEYS
        );
        psPedido.setInt(1, numeroPedido);
        psPedido.setInt(2, mesaNumero);
        psPedido.setString(3, clienteNome);
        psPedido.setString(4, observacao != null ? observacao : "");
        psPedido.setDouble(5, totalPedido);
        psPedido.executeUpdate();

        ResultSet keys = psPedido.getGeneratedKeys();
        int pedidoId = 0;
        if (keys.next()) pedidoId = keys.getInt(1);
        keys.close();
        psPedido.close();

        // Inserir itens do pedido (parse do JSON body)
        if (jsonBody != null && jsonBody.contains("\"itens\"")) {
            parseAndInsertItens(conn, pedidoId, jsonBody);
        }

        // v6.9.1 - Integração com mesas em try-catch separado
        // Se falhar, o pedido já foi criado com sucesso e o cliente não verá erro
        try {
            inserirItensMesaFromPedido(conn, mesaNumero, pedidoId);
        } catch (Exception e) {
            Log.e(TAG, "Erro na integracao com mesas (pedido #" + pedidoId + " ja foi criado): " + e.getMessage(), e);
        }

        return newResponse(Status.CREATED, "application/json",
            "{\"success\":true,\"pedido_id\":" + pedidoId + ",\"numero_pedido\":" + numeroPedido +
            ",\"message\":\"Pedido #" + numeroPedido + " criado com sucesso\"}");
    }

    /**
     * Parse dos itens do pedido a partir do JSON e inserção no banco.
     */
    private void parseAndInsertItens(Connection conn, int pedidoId, String json) {
        try {
            // Encontrar o array "itens" no JSON
            int itensIdx = json.indexOf("\"itens\"");
            if (itensIdx < 0) return;
            int arrStart = json.indexOf('[', itensIdx);
            if (arrStart < 0) return;

            // Encontrar o fim do array
            int depth = 0;
            int arrEnd = arrStart;
            for (int i = arrStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) { arrEnd = i; break; }
                }
            }

            String itensStr = json.substring(arrStart, arrEnd + 1);

            // Parse simples de cada item do array
            int pos = 0;
            while (pos < itensStr.length()) {
                int objStart = itensStr.indexOf('{', pos);
                if (objStart < 0) break;

                // Encontrar fim do objeto (respeitando objetos aninhados)
                int objDepth = 0;
                int objEnd = objStart;
                for (int i = objStart; i < itensStr.length(); i++) {
                    char c = itensStr.charAt(i);
                    if (c == '{') objDepth++;
                    else if (c == '}') {
                        objDepth--;
                        if (objDepth == 0) { objEnd = i; break; }
                    }
                }

                String itemJson = itensStr.substring(objStart, objEnd + 1);

                int produtoId = 0;
                try { produtoId = Integer.parseInt(extractJsonValue(itemJson, "produto_id")); } catch (Exception ignored) {}
                String descricao = extractJsonValue(itemJson, "descricao");
                double quantidade = 1;
                try { quantidade = Double.parseDouble(extractJsonValue(itemJson, "quantidade")); } catch (Exception ignored) {}
                double precoUnit = 0;
                try { precoUnit = Double.parseDouble(extractJsonValue(itemJson, "preco_unitario")); } catch (Exception ignored) {}
                double totalItem = 0;
                try { totalItem = Double.parseDouble(extractJsonValue(itemJson, "total")); } catch (Exception ignored) {}
                String obsItem = extractJsonValue(itemJson, "observacao");

                PreparedStatement psItem = conn.prepareStatement(
                    "INSERT INTO itens_pedido_web (pedido_web_id, produto_id, descricao_produto, quantidade, preco_unitario, total, observacao) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                psItem.setInt(1, pedidoId);
                psItem.setInt(2, produtoId);
                psItem.setString(3, descricao != null ? descricao : "");
                psItem.setDouble(4, quantidade);
                psItem.setDouble(5, precoUnit);
                psItem.setDouble(6, totalItem);
                psItem.setString(7, obsItem != null ? obsItem : "");
                psItem.executeUpdate();

                ResultSet itemKeys = psItem.getGeneratedKeys();
                int itemId = 0;
                if (itemKeys.next()) itemId = itemKeys.getInt(1);
                itemKeys.close();
                psItem.close();

                // Parse adicionais do item
                int adStart = itemJson.indexOf("\"adicionais\"");
                if (adStart >= 0) {
                    int adArrStart = itemJson.indexOf('[', adStart);
                    if (adArrStart >= 0) {
                        int adDepth2 = 0;
                        int adArrEnd = adArrStart;
                        for (int i = adArrStart; i < itemJson.length(); i++) {
                            char c = itemJson.charAt(i);
                            if (c == '[') adDepth2++;
                            else if (c == ']') {
                                adDepth2--;
                                if (adDepth2 == 0) { adArrEnd = i; break; }
                            }
                        }
                        String adStr = itemJson.substring(adArrStart, adArrEnd + 1);
                        int adPos = 0;
                        while (adPos < adStr.length()) {
                            int adObjStart = adStr.indexOf('{', adPos);
                            if (adObjStart < 0) break;
                            int adObjEnd = adStr.indexOf('}', adObjStart);
                            if (adObjEnd < 0) break;
                            String adJson = adStr.substring(adObjStart, adObjEnd + 1);

                            int adId = 0;
                            try { adId = Integer.parseInt(extractJsonValue(adJson, "id")); } catch (Exception ignored) {}
                            String adDesc = extractJsonValue(adJson, "descricao");
                            double adPreco = 0;
                            try { adPreco = Double.parseDouble(extractJsonValue(adJson, "preco")); } catch (Exception ignored) {}

                            if (adId > 0 && itemId > 0) {
                                PreparedStatement psAd = conn.prepareStatement(
                                    "INSERT INTO itens_pedido_web_adicionais (item_pedido_web_id, adicional_id, descricao_adicional, preco) " +
                                    "VALUES (?, ?, ?, ?)"
                                );
                                psAd.setInt(1, itemId);
                                psAd.setInt(2, adId);
                                psAd.setString(3, adDesc != null ? adDesc : "");
                                psAd.setDouble(4, adPreco);
                                psAd.executeUpdate();
                                psAd.close();
                            }

                            adPos = adObjEnd + 1;
                        }
                    }
                }

                pos = objEnd + 1;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parsear itens do pedido: " + e.getMessage(), e);
        }
    }

    /**
     * v6.9.1 - Corrigido: Insere os itens do pedido web na mesa correspondente.
     * Inclui todas as colunas da tabela itens_mesa (adicionais_descricao, adicionais_total, impresso).
     * Também insere os adicionais na tabela itens_mesa_adicionais.
     * Atualiza o status da mesa para 'ocupada' se necessário.
     */
    private void inserirItensMesaFromPedido(Connection conn, int mesaNumero, int pedidoId) {
        try {
            // Buscar mesa pelo número
            PreparedStatement psMesa = conn.prepareStatement("SELECT id FROM mesas WHERE numero = ? AND ativa = 1 LIMIT 1");
            psMesa.setInt(1, mesaNumero);
            ResultSet rsMesa = psMesa.executeQuery();
            if (!rsMesa.next()) {
                rsMesa.close();
                psMesa.close();
                Log.w(TAG, "Mesa " + mesaNumero + " nao encontrada ou inativa, pulando integracao com mesas");
                return;
            }
            int mesaId = rsMesa.getInt("id");
            rsMesa.close();
            psMesa.close();

            // Verificar/criar ocupação da mesa
            PreparedStatement psOcup = conn.prepareStatement(
                "SELECT id FROM ocupacao_mesa WHERE mesa_id = ? AND status != 'encerrada' ORDER BY id DESC LIMIT 1"
            );
            psOcup.setInt(1, mesaId);
            ResultSet rsOcup = psOcup.executeQuery();
            int ocupacaoId;
            if (rsOcup.next()) {
                ocupacaoId = rsOcup.getInt("id");
            } else {
                // Criar nova ocupação com todas as colunas necessárias
                PreparedStatement psNewOcup = conn.prepareStatement(
                    "INSERT INTO ocupacao_mesa (mesa_id, garcom_id, qtd_pessoas, status, data_abertura) VALUES (?, 0, 0, 'ocupada', NOW())",
                    Statement.RETURN_GENERATED_KEYS
                );
                psNewOcup.setInt(1, mesaId);
                psNewOcup.executeUpdate();
                ResultSet newKeys = psNewOcup.getGeneratedKeys();
                ocupacaoId = newKeys.next() ? newKeys.getInt(1) : 0;
                newKeys.close();
                psNewOcup.close();

                // Atualizar status da mesa para 'ocupada'
                try {
                    PreparedStatement psUpdateMesa = conn.prepareStatement(
                        "UPDATE mesas SET status = 'ocupada' WHERE id = ?"
                    );
                    psUpdateMesa.setInt(1, mesaId);
                    psUpdateMesa.executeUpdate();
                    psUpdateMesa.close();
                } catch (Exception e) {
                    Log.w(TAG, "Nao foi possivel atualizar status da mesa: " + e.getMessage());
                }
            }
            rsOcup.close();
            psOcup.close();

            if (ocupacaoId <= 0) return;

            // Buscar itens do pedido web e inserir na mesa
            PreparedStatement psItens = conn.prepareStatement(
                "SELECT ipw.id, ipw.produto_id, ipw.descricao_produto, ipw.quantidade, ipw.preco_unitario, ipw.total, ipw.observacao " +
                "FROM itens_pedido_web ipw WHERE ipw.pedido_web_id = ?"
            );
            psItens.setInt(1, pedidoId);
            ResultSet rsItens = psItens.executeQuery();
            while (rsItens.next()) {
                int itemPedidoWebId = rsItens.getInt("id");

                // Buscar adicionais deste item
                StringBuilder adicionaisDesc = new StringBuilder();
                double adicionaisTotal = 0;
                try {
                    PreparedStatement psAd = conn.prepareStatement(
                        "SELECT descricao_adicional, preco FROM itens_pedido_web_adicionais WHERE item_pedido_web_id = ?"
                    );
                    psAd.setInt(1, itemPedidoWebId);
                    ResultSet rsAd = psAd.executeQuery();
                    boolean firstAd = true;
                    while (rsAd.next()) {
                        if (!firstAd) adicionaisDesc.append(", ");
                        adicionaisDesc.append(rsAd.getString("descricao_adicional"));
                        adicionaisTotal += rsAd.getDouble("preco");
                        firstAd = false;
                    }
                    rsAd.close();
                    psAd.close();
                } catch (Exception e) {
                    Log.w(TAG, "Erro ao buscar adicionais do item: " + e.getMessage());
                }

                // v6.9.1 - INSERT com TODAS as colunas da tabela itens_mesa
                PreparedStatement psInsert = conn.prepareStatement(
                    "INSERT INTO itens_mesa (ocupacao_id, produto_id, descricao_produto, quantidade, preco_unitario, total, adicionais_descricao, adicionais_total, impresso) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    Statement.RETURN_GENERATED_KEYS
                );
                psInsert.setInt(1, ocupacaoId);
                psInsert.setInt(2, rsItens.getInt("produto_id"));
                psInsert.setString(3, rsItens.getString("descricao_produto"));
                psInsert.setDouble(4, rsItens.getDouble("quantidade"));
                psInsert.setDouble(5, rsItens.getDouble("preco_unitario"));
                psInsert.setDouble(6, rsItens.getDouble("total"));
                psInsert.setString(7, adicionaisDesc.length() > 0 ? adicionaisDesc.toString() : null);
                psInsert.setDouble(8, adicionaisTotal);
                psInsert.executeUpdate();

                // Inserir adicionais na tabela itens_mesa_adicionais
                ResultSet insertKeys = psInsert.getGeneratedKeys();
                int itemMesaId = 0;
                if (insertKeys.next()) itemMesaId = insertKeys.getInt(1);
                insertKeys.close();
                psInsert.close();

                if (itemMesaId > 0) {
                    try {
                        PreparedStatement psAdCopy = conn.prepareStatement(
                            "INSERT INTO itens_mesa_adicionais (item_mesa_id, adicional_id, descricao, preco) " +
                            "SELECT ?, adicional_id, descricao_adicional, preco FROM itens_pedido_web_adicionais WHERE item_pedido_web_id = ?"
                        );
                        psAdCopy.setInt(1, itemMesaId);
                        psAdCopy.setInt(2, itemPedidoWebId);
                        psAdCopy.executeUpdate();
                        psAdCopy.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao copiar adicionais para itens_mesa_adicionais: " + e.getMessage());
                    }
                }
            }
            rsItens.close();
            psItens.close();

            Log.d(TAG, "Itens do pedido web #" + pedidoId + " inseridos na mesa " + mesaNumero + " (ocupacao " + ocupacaoId + ")");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inserir itens na mesa: " + e.getMessage(), e);
        }
    }

    private Response getStatusPedidoWeb(Map<String, String> params) throws Exception {
        String idStr = params.get("id");
        if (idStr == null || idStr.isEmpty()) {
            return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID do pedido e obrigatorio\"}");
        }
        int id = 0;
        try { id = Integer.parseInt(idStr); } catch (Exception ignored) {}

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT status FROM pedidos_web WHERE id = ?");
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        String status = "pendente";
        if (rs.next()) status = rs.getString("status");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", "{\"status\":\"" + escapeJson(status) + "\"}");
    }

    private Response getPedidosWeb(Map<String, String> params) throws Exception {
        String statusFilter = params.get("status");
        Connection conn = DatabaseHelper.getInstance(context).getConnection();

        String sql = "SELECT * FROM pedidos_web";
        if (statusFilter != null && !statusFilter.isEmpty() && !"todos".equals(statusFilter)) {
            sql += " WHERE status = ?";
        }
        sql += " ORDER BY data_criacao DESC LIMIT 100";

        PreparedStatement ps = conn.prepareStatement(sql);
        if (statusFilter != null && !statusFilter.isEmpty() && !"todos".equals(statusFilter)) {
            ps.setString(1, statusFilter);
        }
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append(pedidoWebToJson(rs));
            first = false;
        }
        json.append("]");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json.toString());
    }

    private Response atualizarStatusPedidoWeb(Map<String, String> params, String body) throws Exception {
        int id = getIntParam(params, body, "id");
        String novoStatus = getParam(params, body, "status");
        if (id <= 0 || novoStatus == null || novoStatus.isEmpty()) {
            return newResponse(Status.BAD_REQUEST, "application/json", "{\"error\":\"ID e status sao obrigatorios\"}");
        }

        String campoData = "";
        switch (novoStatus) {
            case "preparando": campoData = ", data_preparo = NOW()"; break;
            case "pronto": campoData = ", data_pronto = NOW()"; break;
            case "entregue": campoData = ", data_entregue = NOW()"; break;
        }

        Connection conn = DatabaseHelper.getInstance(context).getConnection();
        PreparedStatement ps = conn.prepareStatement(
            "UPDATE pedidos_web SET status = ?" + campoData + " WHERE id = ?"
        );
        ps.setString(1, novoStatus);
        ps.setInt(2, id);
        int rows = ps.executeUpdate();
        ps.close();

        if (rows == 0) return newResponse(Status.NOT_FOUND, "application/json", "{\"error\":\"Pedido nao encontrado\"}");
        return newResponse(Status.OK, "application/json", "{\"success\":true,\"message\":\"Status atualizado para " + escapeJson(novoStatus) + "\"}");
    }

    private String pedidoWebToJson(ResultSet rs) throws SQLException {
        return "{" +
            "\"id\":" + rs.getInt("id") + "," +
            "\"numero_pedido\":" + rs.getInt("numero_pedido") + "," +
            "\"mesa_numero\":" + rs.getInt("mesa_numero") + "," +
            "\"cliente_nome\":\"" + escapeJson(rs.getString("cliente_nome")) + "\"," +
            "\"observacao\":\"" + escapeJson(rs.getString("observacao") != null ? rs.getString("observacao") : "") + "\"," +
            "\"total\":" + rs.getDouble("total") + "," +
            "\"status\":\"" + escapeJson(rs.getString("status")) + "\"," +
            "\"data_criacao\":\"" + formatTimestampBR(rs.getTimestamp("data_criacao")) + "\"" +
            "}";
    }

    // ==========================================
    // API DO PAINEL DA COZINHA
    // Retorna pedidos com itens detalhados para a cozinha
    // ==========================================

    /**
     * v6.9.4 - API do Painel da Cozinha.
     * Retorna pedidos web com seus itens detalhados (incluindo adicionais e observações)
     * para que a cozinha possa acompanhar o que precisa ser preparado.
     */
    private Response getPedidosCozinha(Map<String, String> params) throws Exception {
        String statusFilter = params.get("status");
        Connection conn = DatabaseHelper.getInstance(context).getConnection();

        String sql = "SELECT * FROM pedidos_web";
        if (statusFilter != null && !statusFilter.isEmpty() && !"todos".equals(statusFilter)) {
            sql += " WHERE status = ?";
        }
        sql += " ORDER BY FIELD(status, 'pendente', 'preparando', 'pronto', 'entregue'), data_criacao ASC LIMIT 100";

        PreparedStatement ps = conn.prepareStatement(sql);
        if (statusFilter != null && !statusFilter.isEmpty() && !"todos".equals(statusFilter)) {
            ps.setString(1, statusFilter);
        }
        ResultSet rs = ps.executeQuery();
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            int pedidoId = rs.getInt("id");
            json.append("{");
            json.append("\"id\":").append(pedidoId).append(",");
            json.append("\"numero_pedido\":").append(rs.getInt("numero_pedido")).append(",");
            json.append("\"mesa_numero\":").append(rs.getInt("mesa_numero")).append(",");
            json.append("\"cliente_nome\":\"").append(escapeJson(rs.getString("cliente_nome"))).append("\",");
            json.append("\"observacao\":\"").append(escapeJson(rs.getString("observacao") != null ? rs.getString("observacao") : "")).append("\",");
            json.append("\"total\":").append(rs.getDouble("total")).append(",");
            json.append("\"status\":\"").append(escapeJson(rs.getString("status"))).append("\",");
            json.append("\"data_criacao\":\"").append(formatTimestampBR(rs.getTimestamp("data_criacao"))).append("\",");

            // Buscar itens do pedido com adicionais
            json.append("\"itens\":");
            json.append(getItensPedidoCozinhaJson(conn, pedidoId));

            json.append("}");
            first = false;
        }
        json.append("]");
        rs.close();
        ps.close();
        return newResponse(Status.OK, "application/json", json.toString());
    }

    /**
     * Retorna JSON array dos itens de um pedido web, incluindo adicionais concatenados.
     */
    private String getItensPedidoCozinhaJson(Connection conn, int pedidoId) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        PreparedStatement ps = conn.prepareStatement(
            "SELECT ipw.id, ipw.descricao_produto, ipw.quantidade, ipw.preco_unitario, ipw.total, ipw.observacao " +
            "FROM itens_pedido_web ipw WHERE ipw.pedido_web_id = ? ORDER BY ipw.id"
        );
        ps.setInt(1, pedidoId);
        ResultSet rs = ps.executeQuery();
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            int itemId = rs.getInt("id");

            // Buscar adicionais deste item
            String adicionaisDesc = "";
            try {
                PreparedStatement psAd = conn.prepareStatement(
                    "SELECT descricao_adicional FROM itens_pedido_web_adicionais WHERE item_pedido_web_id = ?"
                );
                psAd.setInt(1, itemId);
                ResultSet rsAd = psAd.executeQuery();
                StringBuilder adSb = new StringBuilder();
                boolean firstAd = true;
                while (rsAd.next()) {
                    if (!firstAd) adSb.append(", ");
                    adSb.append(rsAd.getString("descricao_adicional"));
                    firstAd = false;
                }
                rsAd.close();
                psAd.close();
                adicionaisDesc = adSb.toString();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao buscar adicionais do item cozinha: " + e.getMessage());
            }

            json.append("{");
            json.append("\"descricao_produto\":\"").append(escapeJson(rs.getString("descricao_produto"))).append("\",");
            json.append("\"quantidade\":").append(rs.getDouble("quantidade")).append(",");
            json.append("\"preco_unitario\":").append(rs.getDouble("preco_unitario")).append(",");
            json.append("\"total\":").append(rs.getDouble("total")).append(",");
            json.append("\"observacao\":\"").append(escapeJson(rs.getString("observacao") != null ? rs.getString("observacao") : "")).append("\",");
            json.append("\"adicionais_desc\":\"").append(escapeJson(adicionaisDesc)).append("\"");
            json.append("}");
            first = false;
        }
        rs.close();
        ps.close();
        json.append("]");
        return json.toString();
    }

    private String getMimeType(String uri) {
        if (uri.endsWith(".html")) return "text/html; charset=utf-8";
        if (uri.endsWith(".css")) return "text/css; charset=utf-8";
        if (uri.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (uri.endsWith(".json")) return "application/json";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        if (uri.endsWith(".gif")) return "image/gif";
        if (uri.endsWith(".svg")) return "image/svg+xml";
        if (uri.endsWith(".ico")) return "image/x-icon";
        if (uri.endsWith(".mp3")) return "audio/mpeg";
        if (uri.endsWith(".wav")) return "audio/wav";
        if (uri.endsWith(".ogg")) return "audio/ogg";
        if (uri.endsWith(".woff2")) return "font/woff2";
        if (uri.endsWith(".woff")) return "font/woff";
        if (uri.endsWith(".ttf")) return "font/ttf";
        return "application/octet-stream";
    }
}
