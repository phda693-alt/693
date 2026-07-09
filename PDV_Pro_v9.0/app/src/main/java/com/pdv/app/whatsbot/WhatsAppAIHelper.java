package com.pdv.app.whatsbot;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.FormatUtils;

/**
 * Motor de Inteligencia Artificial para o Bot WhatsApp do PDV Pro.
 * Integra com a API OpenAI (GPT) para processar linguagem natural,
 * interpretar intencoes do cliente, gerar respostas inteligentes,
 * sugerir produtos e otimizar o atendimento automatizado.
 *
 * v5.0.0 - IA Integrada ao WhatsApp Bot
 *
 * Funcionalidades:
 * - Interpretacao de linguagem natural (NLU)
 * - Classificacao de intencao do cliente
 * - Sugestao inteligente de produtos
 * - Respostas contextuais e humanizadas
 * - Resumo inteligente de pedidos
 * - Deteccao de sentimento do cliente
 * - Cache de respostas para otimizar custos
 * - Fallback gracioso quando a API nao esta disponivel
 */
public class WhatsAppAIHelper {
    private static final String TAG = "WhatsAppAIHelper";

    // Timeout para chamadas HTTP a API
    private static final int HTTP_CONNECT_TIMEOUT = 10000; // 10s
    private static final int HTTP_READ_TIMEOUT = 15000;    // 15s

    // Cache de respostas da IA para reduzir chamadas
    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutos

    // Intencoes reconhecidas pela IA
    public static final String INTENT_GREETING = "SAUDACAO";
    public static final String INTENT_CATALOG = "CATALOGO";
    public static final String INTENT_PRICE = "PRECO";
    public static final String INTENT_STOCK = "ESTOQUE";
    public static final String INTENT_ORDER = "PEDIDO";
    public static final String INTENT_ORDER_STATUS = "CONSULTA_PEDIDO";
    public static final String INTENT_CONTACT = "CONTATO";
    public static final String INTENT_HUMAN = "ATENDENTE";
    public static final String INTENT_TRACK = "RASTREAR";
    public static final String INTENT_THANKS = "AGRADECIMENTO";
    public static final String INTENT_GOODBYE = "DESPEDIDA";
    public static final String INTENT_HELP = "AJUDA";
    public static final String INTENT_COMPLAINT = "RECLAMACAO";
    public static final String INTENT_PRODUCT_SEARCH = "BUSCA_PRODUTO";
    public static final String INTENT_UNKNOWN = "DESCONHECIDO";

    private Context context;
    private WhatsAppBotConfig config;
    private WhatsAppBotLogger logger;
    private DatabaseHelper dbHelper;
    private Gson gson;

    // Cache de respostas: chave -> {resposta, timestamp}
    private final ConcurrentHashMap<String, CachedResponse> responseCache = new ConcurrentHashMap<>();

    // Executor para chamadas assincronas
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    /**
     * Resultado da analise de intencao pela IA.
     */
    public static class AIIntentResult {
        public String intent;           // Intencao classificada
        public String productSearch;    // Produto mencionado (se houver)
        public String sentiment;        // Sentimento: positivo, neutro, negativo
        public double confidence;       // Confianca da classificacao (0-1)
        public String suggestedResponse; // Resposta sugerida pela IA
        public boolean needsHuman;      // Se precisa de atendente humano

        public AIIntentResult(String intent) {
            this.intent = intent;
            this.confidence = 1.0;
            this.sentiment = "neutro";
            this.needsHuman = false;
        }
    }

    /**
     * Resposta em cache.
     */
    private static class CachedResponse {
        String response;
        long timestamp;

        CachedResponse(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    public WhatsAppAIHelper(Context context) {
        this.context = context;
        this.config = new WhatsAppBotConfig(context);
        this.logger = new WhatsAppBotLogger(context);
        this.dbHelper = DatabaseHelper.getInstance(context);
        this.gson = new Gson();
    }

    /**
     * Verifica se a IA esta habilitada e configurada.
     */
    public boolean isIADisponivel() {
        return config.isIAEnabled()
                && config.getIAApiKey() != null
                && !config.getIAApiKey().isEmpty();
    }

    /**
     * Classifica a intencao do usuario usando IA.
     * Primeiro tenta classificacao local (regex), depois usa a API se necessario.
     */
    public AIIntentResult classificarIntencao(String mensagem, String estadoAtual) {
        if (mensagem == null || mensagem.isEmpty()) {
            return new AIIntentResult(INTENT_UNKNOWN);
        }

        String msg = mensagem.trim().toLowerCase();

        // 1. Classificacao local rapida (sem custo de API)
        AIIntentResult localResult = classificacaoLocal(msg);
        if (localResult != null && localResult.confidence >= 0.85) {
            return localResult;
        }

        // 2. Se IA nao esta disponivel, usar classificacao local mesmo com baixa confianca
        if (!isIADisponivel()) {
            return localResult != null ? localResult : new AIIntentResult(INTENT_UNKNOWN);
        }

        // 3. Usar IA para classificacao avancada
        try {
            AIIntentResult aiResult = classificarComIA(mensagem, estadoAtual);
            if (aiResult != null) {
                return aiResult;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro na classificacao com IA", e);
            logger.logErro("Erro na classificacao IA", e);
        }

        // 4. Fallback para classificacao local
        return localResult != null ? localResult : new AIIntentResult(INTENT_UNKNOWN);
    }

    /**
     * Classificacao local usando regex e heuristicas.
     * Otimizada para ser rapida e sem custo de API.
     */
    private AIIntentResult classificacaoLocal(String msg) {
        // Saudacoes
        if (msg.matches("^(oi|ola|olá|bom dia|boa tarde|boa noite|hey|hi|hello|e ai|eai|fala|opa|salve).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_GREETING);
            r.confidence = 0.95;
            r.sentiment = "positivo";
            return r;
        }

        // Agradecimento
        if (msg.matches(".*(obrigado|obrigada|valeu|thanks|brigado|brigada|agradeco|grato|grata).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_THANKS);
            r.confidence = 0.95;
            r.sentiment = "positivo";
            return r;
        }

        // Despedida
        if (msg.matches("^(tchau|bye|ate mais|ate logo|falou|flw|vlw|adeus|encerrar|sair|fim).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_GOODBYE);
            r.confidence = 0.90;
            return r;
        }

        // Preco - deteccao avancada
        if (msg.matches(".*(pre[cç]o|quanto custa|quanto [eé]|valor d[eoa]|qual o valor|quanto fica|quanto sai|quanto t[aá]).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_PRICE);
            r.confidence = 0.90;
            // Extrair produto mencionado
            r.productSearch = extrairProdutoDaMensagem(msg,
                    "pre[cç]o|quanto custa|quanto [eé]|valor d[eoa]|qual o valor|quanto fica|quanto sai|quanto t[aá]|d[eoa]|o|a|um|uma|do|da|dos|das");
            return r;
        }

        // Pedido / Compra
        if (msg.matches(".*(quero|pedir|comprar|pedido|encomend|me v[eê]|manda|preciso d|queria|gostaria).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_ORDER);
            r.confidence = 0.88;
            r.productSearch = extrairProdutoDaMensagem(msg,
                    "quero|pedir|comprar|pedido|encomend|me v[eê]|manda|preciso d[eoa]|queria|gostaria d[eoa]|um|uma|uns|umas|d[eoa]");
            return r;
        }

        // Consulta de pedido
        if (msg.matches(".*(meu pedido|status|andamento|onde est[aá]|situacao|situação|acompanhar).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_ORDER_STATUS);
            r.confidence = 0.88;
            return r;
        }

        // Rastrear entregador
        if (msg.matches(".*(rastrear|rastreio|rastreamento|entregador|entrega|localizar|localizacao|localização|gps|cadê|cade).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_TRACK);
            r.confidence = 0.88;
            return r;
        }

        // Catalogo
        if (msg.matches(".*(cat[aá]logo|card[aá]pio|produtos|lista|menu|o que (tem|voc[eê]s)|que voc[eê]s).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_CATALOG);
            r.confidence = 0.88;
            return r;
        }

        // Contato / Localizacao
        if (msg.matches(".*(endere[cç]o|localiza[cç][aã]o|onde fica|telefone|contato|hor[aá]rio|funciona).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_CONTACT);
            r.confidence = 0.85;
            return r;
        }

        // Atendente humano
        if (msg.matches(".*(atendente|humano|pessoa|falar com|gerente|responsavel|responsável|reclamar|problema).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_HUMAN);
            r.confidence = 0.88;
            r.needsHuman = true;
            return r;
        }

        // Reclamacao
        if (msg.matches(".*(reclama|insatisf|p[eé]ssimo|horrivel|horr[ií]vel|ruim|lixo|absurdo|vergonha|nunca mais).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_COMPLAINT);
            r.confidence = 0.85;
            r.sentiment = "negativo";
            r.needsHuman = true;
            return r;
        }

        // Ajuda
        if (msg.matches(".*(ajuda|help|socorro|op[cç][oõ]es|como fun|o que posso|n[aã]o sei|n[aã]o entendi).*")) {
            AIIntentResult r = new AIIntentResult(INTENT_HELP);
            r.confidence = 0.85;
            return r;
        }

        // Busca de produto especifico (quando nao se encaixa em nenhum acima)
        // Verificar se a mensagem pode ser um nome de produto
        if (msg.length() >= 3 && msg.length() <= 50 && !msg.matches(".*[0-9].*") && verificarSeProdutoExiste(msg)) {
            AIIntentResult r = new AIIntentResult(INTENT_PRODUCT_SEARCH);
            r.confidence = 0.75;
            r.productSearch = msg;
            return r;
        }

        // Nao identificado localmente
        AIIntentResult r = new AIIntentResult(INTENT_UNKNOWN);
        r.confidence = 0.3;
        return r;
    }

    /**
     * Extrai o nome do produto de uma mensagem removendo palavras-chave.
     */
    private String extrairProdutoDaMensagem(String msg, String regexRemover) {
        String produto = msg.replaceAll(regexRemover, "").trim();
        produto = produto.replaceAll("\\s+", " ").trim();
        // Remover pontuacao
        produto = produto.replaceAll("[?!.,;:]", "").trim();
        return produto.isEmpty() ? null : produto;
    }

    /**
     * Verifica no banco se existe algum produto com nome similar.
     */
    private boolean verificarSeProdutoExiste(String busca) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) as total FROM produtos WHERE ativo = 1 AND descricao LIKE ?");
            ps.setString(1, "%" + busca + "%");
            ResultSet rs = ps.executeQuery();
            boolean existe = false;
            if (rs.next()) {
                existe = rs.getInt("total") > 0;
            }
            rs.close();
            ps.close();
            return existe;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Classifica a intencao usando a API de IA (OpenAI/GPT).
     */
    private AIIntentResult classificarComIA(String mensagem, String estadoAtual) {
        // Verificar cache
        String cacheKey = "intent:" + mensagem.toLowerCase().trim();
        CachedResponse cached = responseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            try {
                return gson.fromJson(cached.response, AIIntentResult.class);
            } catch (Exception ignored) {}
        }

        String systemPrompt = "Voce e um assistente de classificacao de intencoes para um bot de atendimento de loja/comercio via WhatsApp. "
                + "Classifique a mensagem do cliente em uma das intencoes abaixo e retorne APENAS um JSON valido:\n\n"
                + "Intencoes possiveis:\n"
                + "- SAUDACAO: cumprimentos, oi, bom dia, etc\n"
                + "- CATALOGO: quer ver produtos, cardapio, lista\n"
                + "- PRECO: quer saber preco de algo\n"
                + "- PEDIDO: quer comprar, pedir algo\n"
                + "- CONSULTA_PEDIDO: quer saber status do pedido\n"
                + "- CONTATO: quer endereco, telefone, horario\n"
                + "- ATENDENTE: quer falar com pessoa humana\n"
                + "- RASTREAR: quer rastrear entrega\n"
                + "- AGRADECIMENTO: obrigado, valeu\n"
                + "- DESPEDIDA: tchau, ate mais\n"
                + "- AJUDA: precisa de ajuda, nao entende\n"
                + "- RECLAMACAO: insatisfacao, problema\n"
                + "- BUSCA_PRODUTO: menciona nome de produto especifico\n"
                + "- DESCONHECIDO: nao se encaixa em nenhuma\n\n"
                + "Estado atual da conversa: " + estadoAtual + "\n\n"
                + "Retorne SOMENTE o JSON no formato:\n"
                + "{\"intent\":\"INTENCAO\",\"productSearch\":\"produto ou null\",\"sentiment\":\"positivo/neutro/negativo\",\"confidence\":0.95,\"needsHuman\":false}";

        String aiResponse = chamarAPI(systemPrompt, mensagem);
        if (aiResponse != null) {
            try {
                // Extrair JSON da resposta
                String json = extrairJSON(aiResponse);
                AIIntentResult result = gson.fromJson(json, AIIntentResult.class);
                if (result != null && result.intent != null) {
                    // Salvar no cache
                    responseCache.put(cacheKey, new CachedResponse(gson.toJson(result)));
                    return result;
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parsear resposta da IA: " + aiResponse, e);
            }
        }

        return null;
    }

    /**
     * Gera uma resposta inteligente e humanizada usando IA.
     * Usada para mensagens que nao se encaixam no fluxo padrao.
     */
    public String gerarRespostaInteligente(String mensagem, String nomeCliente, String estadoAtual, String contexto) {
        if (!isIADisponivel()) {
            return null;
        }

        // Verificar cache
        String cacheKey = "resp:" + mensagem.toLowerCase().trim() + ":" + estadoAtual;
        CachedResponse cached = responseCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.response;
        }

        String nomeEmpresa = config.getNomeEmpresa();

        String systemPrompt = "Voce e o assistente virtual da loja *" + nomeEmpresa + "* no WhatsApp. "
                + "Responda de forma amigavel, profissional e concisa (maximo 3 linhas). "
                + "Use formatacao do WhatsApp (*negrito*, _italico_). "
                + "Sempre direcione o cliente para as opcoes do menu quando apropriado.\n\n"
                + "Opcoes do menu:\n"
                + "*1* - Catalogo | *2* - Precos | *3* - Pedido\n"
                + "*4* - Consultar Pedido | *5* - Contato | *6* - Atendente | *7* - Rastrear\n\n"
                + "Estado atual: " + estadoAtual + "\n"
                + (contexto != null ? "Contexto: " + contexto + "\n" : "")
                + (nomeCliente != null ? "Nome do cliente: " + nomeCliente + "\n" : "")
                + "\nIMPORTANTE: Nao invente informacoes sobre produtos ou precos. "
                + "Se o cliente perguntar algo especifico, direcione para a opcao correta do menu. "
                + "Termine sempre com as opcoes relevantes do menu.";

        String aiResponse = chamarAPI(systemPrompt, mensagem);
        if (aiResponse != null && !aiResponse.isEmpty()) {
            // Limpar e formatar resposta
            String resposta = aiResponse.trim();
            // Remover aspas se a IA colocou
            if (resposta.startsWith("\"") && resposta.endsWith("\"")) {
                resposta = resposta.substring(1, resposta.length() - 1);
            }
            // Salvar no cache
            responseCache.put(cacheKey, new CachedResponse(resposta));
            return resposta;
        }

        return null;
    }

    /**
     * Gera sugestoes de produtos baseadas no historico do cliente.
     */
    public String gerarSugestoesProdutos(String celularCliente) {
        if (!isIADisponivel()) {
            return null;
        }

        try {
            // Buscar historico de compras do cliente
            String historico = buscarHistoricoCliente(celularCliente);
            if (historico == null || historico.isEmpty()) {
                return null;
            }

            // Buscar produtos populares
            String populares = buscarProdutosPopulares();

            String systemPrompt = "Voce e o assistente de vendas da loja *" + config.getNomeEmpresa() + "*. "
                    + "Com base no historico de compras do cliente e nos produtos populares, "
                    + "sugira 3 produtos que o cliente pode gostar. "
                    + "Seja breve e use formatacao WhatsApp. Maximo 5 linhas.";

            String userMsg = "Historico do cliente:\n" + historico
                    + "\n\nProdutos populares:\n" + populares
                    + "\n\nSugira 3 produtos relevantes de forma amigavel.";

            return chamarAPI(systemPrompt, userMsg);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar sugestoes", e);
            return null;
        }
    }

    /**
     * Gera um resumo inteligente e humanizado do pedido.
     */
    public String gerarResumoInteligente(String resumoPedido, String nomeCliente) {
        if (!isIADisponivel()) {
            return null;
        }

        String systemPrompt = "Voce e o assistente da loja *" + config.getNomeEmpresa() + "*. "
                + "Reescreva o resumo do pedido de forma mais amigavel e organizada, "
                + "mantendo TODOS os dados (itens, precos, totais, endereco). "
                + "Use emojis e formatacao WhatsApp. Mantenha as opcoes de confirmacao no final.";

        return chamarAPI(systemPrompt, "Resumo original:\n" + resumoPedido);
    }

    /**
     * Detecta se o cliente esta frustrado ou precisa de atendimento humano.
     */
    public boolean detectarFrustracao(String mensagem) {
        if (mensagem == null) return false;
        String msg = mensagem.toLowerCase();

        // Deteccao local rapida
        String[] palavrasFrustracao = {
                "absurdo", "ridiculo", "ridículo", "pessimo", "péssimo",
                "horrivel", "horrível", "lixo", "vergonha", "nunca mais",
                "vou processar", "procon", "reclame aqui", "insatisfeito",
                "nao funciona", "nao resolve", "cansado", "irritado",
                "raiva", "indignado", "palhaçada", "palhacada"
        };

        for (String palavra : palavrasFrustracao) {
            if (msg.contains(palavra)) {
                return true;
            }
        }

        // Se IA disponivel, fazer analise mais profunda
        if (isIADisponivel() && msg.length() > 20) {
            try {
                AIIntentResult result = classificarIntencao(mensagem, "MENU");
                return "negativo".equals(result.sentiment) || result.needsHuman;
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * Gera mensagem de atendimento para cliente frustrado.
     */
    public String gerarRespostaFrustracao(String mensagem, String nomeCliente) {
        if (isIADisponivel()) {
            String systemPrompt = "Voce e o assistente da loja *" + config.getNomeEmpresa() + "*. "
                    + "O cliente esta insatisfeito/frustrado. Responda com empatia, "
                    + "peca desculpas pelo inconveniente e ofereça encaminhar para um atendente humano. "
                    + "Seja breve (3 linhas max), use formatacao WhatsApp.";

            String resposta = chamarAPI(systemPrompt, mensagem);
            if (resposta != null) {
                return resposta + "\n\n*7* - Falar com Atendente\n*0* - Menu principal";
            }
        }

        // Fallback sem IA
        return "\uD83D\uDE4F Pedimos sinceras desculpas pelo inconveniente"
                + (nomeCliente != null ? ", *" + nomeCliente + "*" : "") + ".\n\n"
                + "Sua satisfacao e muito importante para nos. "
                + "Vou encaminhar para um atendente resolver isso.\n\n"
                + "*7* - \uD83D\uDC64 Falar com Atendente\n"
                + "*0* - Menu principal";
    }

    /**
     * Busca o historico de compras de um cliente pelo celular.
     */
    private String buscarHistoricoCliente(String celular) {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT iv.descricao_produto, iv.quantidade, iv.preco_unitario "
                            + "FROM itens_venda iv "
                            + "INNER JOIN vendas v ON iv.venda_id = v.id "
                            + "WHERE v.celular_whatsapp LIKE ? "
                            + "ORDER BY v.data_venda DESC LIMIT 20");
            ps.setString(1, "%" + celular.replaceAll("[^0-9]", "") + "%");
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append("- ").append(rs.getString("descricao_produto"));
                sb.append(" (").append(FormatUtils.formatQuantidade(rs.getDouble("quantidade"))).append("x");
                sb.append(" R$").append(FormatUtils.formatMoney(rs.getDouble("preco_unitario"))).append(")\n");
            }
            rs.close();
            ps.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar historico", e);
            return null;
        }
    }

    /**
     * Busca os produtos mais vendidos.
     */
    private String buscarProdutosPopulares() {
        try {
            Connection conn = dbHelper.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT iv.descricao_produto, SUM(iv.quantidade) as total_vendido "
                            + "FROM itens_venda iv "
                            + "INNER JOIN vendas v ON iv.venda_id = v.id "
                            + "WHERE v.data_venda >= DATE_SUB(NOW(), INTERVAL 30 DAY) "
                            + "GROUP BY iv.descricao_produto "
                            + "ORDER BY total_vendido DESC LIMIT 10");

            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                sb.append("- ").append(rs.getString("descricao_produto"));
                sb.append(" (").append(FormatUtils.formatQuantidade(rs.getDouble("total_vendido"))).append(" vendidos)\n");
            }
            rs.close();
            stmt.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar populares", e);
            return null;
        }
    }

    /**
     * Chama a API OpenAI/GPT.
     */
    private String chamarAPI(String systemPrompt, String userMessage) {
        try {
            String apiKey = config.getIAApiKey();
            String apiUrl = config.getIAApiUrl();
            String model = config.getIAModel();

            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "API Key da IA nao configurada");
                return null;
            }

            // Montar request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", model);
            requestBody.addProperty("temperature", 0.7);
            requestBody.addProperty("max_tokens", 300);

            JsonArray messages = new JsonArray();

            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);

            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);

            requestBody.add("messages", messages);

            // Fazer chamada HTTP
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(HTTP_CONNECT_TIMEOUT);
            conn.setReadTimeout(HTTP_READ_TIMEOUT);
            conn.setDoOutput(true);

            // Enviar body
            OutputStream os = conn.getOutputStream();
            os.write(requestBody.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                // Parsear resposta
                JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                JsonArray choices = jsonResponse.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message != null) {
                        String content = message.get("content").getAsString();
                        Log.d(TAG, "Resposta da IA recebida: " + content.substring(0, Math.min(100, content.length())));
                        return content;
                    }
                }
            } else {
                // Ler erro
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    error.append(line);
                }
                br.close();
                Log.e(TAG, "Erro na API IA (" + responseCode + "): " + error.toString());
                logger.logErro("API IA retornou erro " + responseCode, null);
            }

            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao chamar API da IA", e);
            logger.logErro("Erro ao chamar API IA", e);
        }

        return null;
    }

    /**
     * Extrai JSON de uma string que pode conter texto adicional.
     */
    private String extrairJSON(String texto) {
        if (texto == null) return "{}";

        // Tentar encontrar JSON na resposta
        int inicio = texto.indexOf('{');
        int fim = texto.lastIndexOf('}');
        if (inicio >= 0 && fim > inicio) {
            return texto.substring(inicio, fim + 1);
        }

        return texto;
    }

    /**
     * Limpa o cache de respostas expiradas.
     */
    public void limparCacheExpirado() {
        Iterator<Map.Entry<String, CachedResponse>> it = responseCache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
            }
        }
        // Limitar tamanho do cache
        if (responseCache.size() > MAX_CACHE_SIZE) {
            responseCache.clear();
        }
    }

    /**
     * Retorna estatisticas do uso da IA.
     */
    public String getEstatisticasIA() {
        return "Cache IA: " + responseCache.size() + " entradas";
    }

    /**
     * Libera recursos.
     */
    public void destroy() {
        responseCache.clear();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
