package com.pdv.app.whatsbot;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gerenciamento de configuracoes do Bot WhatsApp.
 * Armazena todas as preferencias do bot em SharedPreferences.
 */
public class WhatsAppBotConfig {
    private static final String PREFS_NAME = "whatsbot_config";

    // Chaves de configuracao
    private static final String KEY_BOT_ATIVO = "bot_ativo";
    private static final String KEY_NOME_EMPRESA = "nome_empresa";
    private static final String KEY_MSG_BOAS_VINDAS = "msg_boas_vindas";
    private static final String KEY_MSG_AUSENCIA = "msg_ausencia";
    private static final String KEY_MSG_ENCERRAMENTO = "msg_encerramento";
    private static final String KEY_HORARIO_INICIO = "horario_inicio";
    private static final String KEY_HORARIO_FIM = "horario_fim";
    private static final String KEY_ATENDER_FORA_HORARIO = "atender_fora_horario";
    private static final String KEY_ENVIAR_CATALOGO = "enviar_catalogo";
    private static final String KEY_CONSULTAR_PRECO = "consultar_preco";
    private static final String KEY_CONSULTAR_ESTOQUE = "consultar_estoque";
    private static final String KEY_ENVIAR_CUPOM_AUTO = "enviar_cupom_auto";
    private static final String KEY_NOTIFICAR_VENDA = "notificar_venda";
    private static final String KEY_NUMERO_ADMIN = "numero_admin";
    private static final String KEY_DELAY_RESPOSTA = "delay_resposta";
    private static final String KEY_MAX_PRODUTOS_CATALOGO = "max_produtos_catalogo";
    private static final String KEY_RESPONDER_GRUPOS = "responder_grupos";
    private static final String KEY_RESPONDER_APENAS_CONTATOS = "responder_apenas_contatos";
    private static final String KEY_MSG_MENU_PRINCIPAL = "msg_menu_principal";
    private static final String KEY_MSG_PEDIDO_RECEBIDO = "msg_pedido_recebido";
    private static final String KEY_ACEITAR_PEDIDOS = "aceitar_pedidos";
    private static final String KEY_NUMERO_NOTIFICACAO = "numero_notificacao";
    private static final String KEY_LOG_ATIVO = "log_ativo";
    private static final String KEY_IMPRESSAO_AUTO_WHATSAPP = "impressao_auto_whatsapp";
    private static final String KEY_TOTAL_MSGS_RECEBIDAS = "total_msgs_recebidas";
    private static final String KEY_TOTAL_MSGS_ENVIADAS = "total_msgs_enviadas";
    private static final String KEY_TOTAL_PEDIDOS = "total_pedidos";

    // v5.0.0 - Configuracoes de IA
    private static final String KEY_IA_ENABLED = "ia_enabled";
    private static final String KEY_IA_API_KEY = "ia_api_key";
    private static final String KEY_IA_API_URL = "ia_api_url";
    private static final String KEY_IA_MODEL = "ia_model";
    private static final String KEY_IA_INTERPRETAR_INTENCAO = "ia_interpretar_intencao";
    private static final String KEY_IA_RESPOSTAS_INTELIGENTES = "ia_respostas_inteligentes";
    private static final String KEY_IA_SUGESTOES_PRODUTOS = "ia_sugestoes_produtos";
    private static final String KEY_IA_DETECTAR_FRUSTRACAO = "ia_detectar_frustracao";

    private SharedPreferences prefs;

    public WhatsAppBotConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ===================== GETTERS =====================

    public boolean isBotAtivo() {
        return prefs.getBoolean(KEY_BOT_ATIVO, false);
    }

    public String getNomeEmpresa() {
        return prefs.getString(KEY_NOME_EMPRESA, "PDV Pro");
    }

    public String getMsgBoasVindas() {
        return prefs.getString(KEY_MSG_BOAS_VINDAS,
                "Ola! Bem-vindo(a) ao *{empresa}*! \uD83D\uDC4B\n\n"
                        + "Sou o assistente virtual e estou aqui para ajuda-lo(a).\n\n"
                        + "Digite o numero da opcao desejada:\n\n"
                        + "*0* - \u274C *Encerrar Atendimento*\n"
                        + "*2* - \uD83D\uDCB0 Consultar Preco\n"
                        + "*3* - \uD83D\uDED2 Fazer Pedido\n"
                        + "*4* - \uD83D\uDCDD Consultar Pedido\n"
                        + "*5* - \uD83D\uDCCD Localizacao/Contato\n"
                        + "*6* - \uD83D\uDC64 Falar com Atendente\n"
                        + "*7* - \uD83D\uDCCD Rastrear Entregador\n"
                        + "*1* - \uD83D\uDCCB Catalogo de Produtos\n\n"
                        + "_Escolha uma opcao para continuar..._");
    }

    public String getMsgAusencia() {
        return prefs.getString(KEY_MSG_AUSENCIA,
                "Olá! No momento estamos *fora do horario de atendimento*. ⏰\n\n"
                        + "Nosso horario de funcionamento e:\n"
                        + "*{horario_inicio}h as {horario_fim}h*\n\n"
                        + "Deixe sua mensagem que responderemos assim que possivel!\n"
                        + "Obrigado pela compreensao. 🙏");
    }

    public String getMsgEncerramento() {
        return prefs.getString(KEY_MSG_ENCERRAMENTO,
                "Obrigado por entrar em contato com *{empresa}*! \uD83D\uDE4F\n\n"
                        + "Foi um prazer atende-lo(a). Se precisar de algo mais, e so chamar!\n\n"
                        + "Avalie nosso atendimento:\n"
                        + "⭐⭐⭐⭐⭐\n\n"
                        + "_Ate a proxima!_ \uD83D\uDC4B");
    }

    public String getHorarioInicio() {
        return prefs.getString(KEY_HORARIO_INICIO, "08:00");
    }

    public String getHorarioFim() {
        return prefs.getString(KEY_HORARIO_FIM, "18:00");
    }

    public boolean isAtenderForaHorario() {
        return prefs.getBoolean(KEY_ATENDER_FORA_HORARIO, true);
    }

    public boolean isEnviarCatalogo() {
        return prefs.getBoolean(KEY_ENVIAR_CATALOGO, true);
    }

    public boolean isConsultarPreco() {
        return prefs.getBoolean(KEY_CONSULTAR_PRECO, true);
    }

    public boolean isConsultarEstoque() {
        return prefs.getBoolean(KEY_CONSULTAR_ESTOQUE, true);
    }

    public boolean isEnviarCupomAuto() {
        return prefs.getBoolean(KEY_ENVIAR_CUPOM_AUTO, true);
    }

    public boolean isNotificarVenda() {
        return prefs.getBoolean(KEY_NOTIFICAR_VENDA, true);
    }

    public String getNumeroAdmin() {
        return prefs.getString(KEY_NUMERO_ADMIN, "");
    }

    public int getDelayResposta() {
        return prefs.getInt(KEY_DELAY_RESPOSTA, 2);
    }

    public int getMaxProdutosCatalogo() {
        return prefs.getInt(KEY_MAX_PRODUTOS_CATALOGO, 20);
    }

    public boolean isResponderGrupos() {
        return prefs.getBoolean(KEY_RESPONDER_GRUPOS, false);
    }

    public boolean isResponderApenasContatos() {
        return prefs.getBoolean(KEY_RESPONDER_APENAS_CONTATOS, false);
    }

    public String getMsgMenuPrincipal() {
        return prefs.getString(KEY_MSG_MENU_PRINCIPAL, "");
    }

    public String getMsgPedidoRecebido() {
        return prefs.getString(KEY_MSG_PEDIDO_RECEBIDO,
                "✅ *Pedido recebido com sucesso!*\n\n"
                        + "Numero do pedido: *#{pedido_id}*\n"
                        + "Total: *R$ {total}*\n\n"
                        + "Aguarde a confirmacao do estabelecimento.\n"
                        + "Voce sera notificado sobre o andamento. 📦");
    }

    public boolean isAceitarPedidos() {
        return prefs.getBoolean(KEY_ACEITAR_PEDIDOS, true);
    }

    public String getNumeroNotificacao() {
        return prefs.getString(KEY_NUMERO_NOTIFICACAO, "");
    }

    public boolean isLogAtivo() {
        return prefs.getBoolean(KEY_LOG_ATIVO, true);
    }

    /**
     * v4.6.0 - Verifica se a impressao automatica dos pedidos do WhatsApp esta habilitada.
     */
    public boolean isImpressaoAutoWhatsApp() {
        return prefs.getBoolean(KEY_IMPRESSAO_AUTO_WHATSAPP, true);
    }

    public long getTotalMsgsRecebidas() {
        return prefs.getLong(KEY_TOTAL_MSGS_RECEBIDAS, 0);
    }

    public long getTotalMsgsEnviadas() {
        return prefs.getLong(KEY_TOTAL_MSGS_ENVIADAS, 0);
    }

    public long getTotalPedidos() {
        return prefs.getLong(KEY_TOTAL_PEDIDOS, 0);
    }

    // ===================== v5.0.0 - GETTERS IA =====================

    public boolean isIAEnabled() {
        return prefs.getBoolean(KEY_IA_ENABLED, false);
    }

    public String getIAApiKey() {
        return prefs.getString(KEY_IA_API_KEY, "");
    }

    public String getIAApiUrl() {
        return prefs.getString(KEY_IA_API_URL, "https://api.openai.com/v1/chat/completions");
    }

    public String getIAModel() {
        return prefs.getString(KEY_IA_MODEL, "gpt-4.1-nano");
    }

    public boolean isIAInterpretarIntencao() {
        return prefs.getBoolean(KEY_IA_INTERPRETAR_INTENCAO, true);
    }

    public boolean isIARespostasInteligentes() {
        return prefs.getBoolean(KEY_IA_RESPOSTAS_INTELIGENTES, true);
    }

    public boolean isIASugestoesProdutos() {
        return prefs.getBoolean(KEY_IA_SUGESTOES_PRODUTOS, true);
    }

    public boolean isIADetectarFrustracao() {
        return prefs.getBoolean(KEY_IA_DETECTAR_FRUSTRACAO, true);
    }

    // ===================== SETTERS =====================

    public void setBotAtivo(boolean ativo) {
        prefs.edit().putBoolean(KEY_BOT_ATIVO, ativo).apply();
    }

    public void setNomeEmpresa(String nome) {
        prefs.edit().putString(KEY_NOME_EMPRESA, nome).apply();
    }

    public void setMsgBoasVindas(String msg) {
        prefs.edit().putString(KEY_MSG_BOAS_VINDAS, msg).apply();
    }

    public void setMsgAusencia(String msg) {
        prefs.edit().putString(KEY_MSG_AUSENCIA, msg).apply();
    }

    public void setMsgEncerramento(String msg) {
        prefs.edit().putString(KEY_MSG_ENCERRAMENTO, msg).apply();
    }

    public void setHorarioInicio(String horario) {
        prefs.edit().putString(KEY_HORARIO_INICIO, horario).apply();
    }

    public void setHorarioFim(String horario) {
        prefs.edit().putString(KEY_HORARIO_FIM, horario).apply();
    }

    public void setAtenderForaHorario(boolean atender) {
        prefs.edit().putBoolean(KEY_ATENDER_FORA_HORARIO, atender).apply();
    }

    public void setEnviarCatalogo(boolean enviar) {
        prefs.edit().putBoolean(KEY_ENVIAR_CATALOGO, enviar).apply();
    }

    public void setConsultarPreco(boolean consultar) {
        prefs.edit().putBoolean(KEY_CONSULTAR_PRECO, consultar).apply();
    }

    public void setConsultarEstoque(boolean consultar) {
        prefs.edit().putBoolean(KEY_CONSULTAR_ESTOQUE, consultar).apply();
    }

    public void setEnviarCupomAuto(boolean enviar) {
        prefs.edit().putBoolean(KEY_ENVIAR_CUPOM_AUTO, enviar).apply();
    }

    public void setNotificarVenda(boolean notificar) {
        prefs.edit().putBoolean(KEY_NOTIFICAR_VENDA, notificar).apply();
    }

    public void setNumeroAdmin(String numero) {
        prefs.edit().putString(KEY_NUMERO_ADMIN, numero).apply();
    }

    public void setDelayResposta(int delay) {
        prefs.edit().putInt(KEY_DELAY_RESPOSTA, delay).apply();
    }

    public void setMaxProdutosCatalogo(int max) {
        prefs.edit().putInt(KEY_MAX_PRODUTOS_CATALOGO, max).apply();
    }

    public void setResponderGrupos(boolean responder) {
        prefs.edit().putBoolean(KEY_RESPONDER_GRUPOS, responder).apply();
    }

    public void setResponderApenasContatos(boolean responder) {
        prefs.edit().putBoolean(KEY_RESPONDER_APENAS_CONTATOS, responder).apply();
    }

    public void setMsgMenuPrincipal(String msg) {
        prefs.edit().putString(KEY_MSG_MENU_PRINCIPAL, msg).apply();
    }

    public void setMsgPedidoRecebido(String msg) {
        prefs.edit().putString(KEY_MSG_PEDIDO_RECEBIDO, msg).apply();
    }

    public void setAceitarPedidos(boolean aceitar) {
        prefs.edit().putBoolean(KEY_ACEITAR_PEDIDOS, aceitar).apply();
    }

    public void setNumeroNotificacao(String numero) {
        prefs.edit().putString(KEY_NUMERO_NOTIFICACAO, numero).apply();
    }

    public void setLogAtivo(boolean ativo) {
        prefs.edit().putBoolean(KEY_LOG_ATIVO, ativo).apply();
    }

    /**
     * v4.6.0 - Define se a impressao automatica dos pedidos do WhatsApp esta habilitada.
     */
    public void setImpressaoAutoWhatsApp(boolean imprimir) {
        prefs.edit().putBoolean(KEY_IMPRESSAO_AUTO_WHATSAPP, imprimir).apply();
    }

    public void incrementMsgsRecebidas() {
        prefs.edit().putLong(KEY_TOTAL_MSGS_RECEBIDAS, getTotalMsgsRecebidas() + 1).apply();
    }

    public void incrementMsgsEnviadas() {
        prefs.edit().putLong(KEY_TOTAL_MSGS_ENVIADAS, getTotalMsgsEnviadas() + 1).apply();
    }

    public void incrementPedidos() {
        prefs.edit().putLong(KEY_TOTAL_PEDIDOS, getTotalPedidos() + 1).apply();
    }

    // ===================== v5.0.0 - SETTERS IA =====================

    public void setIAEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_IA_ENABLED, enabled).apply();
    }

    public void setIAApiKey(String key) {
        prefs.edit().putString(KEY_IA_API_KEY, key).apply();
    }

    public void setIAApiUrl(String url) {
        prefs.edit().putString(KEY_IA_API_URL, url).apply();
    }

    public void setIAModel(String model) {
        prefs.edit().putString(KEY_IA_MODEL, model).apply();
    }

    public void setIAInterpretarIntencao(boolean enabled) {
        prefs.edit().putBoolean(KEY_IA_INTERPRETAR_INTENCAO, enabled).apply();
    }

    public void setIARespostasInteligentes(boolean enabled) {
        prefs.edit().putBoolean(KEY_IA_RESPOSTAS_INTELIGENTES, enabled).apply();
    }

    public void setIASugestoesProdutos(boolean enabled) {
        prefs.edit().putBoolean(KEY_IA_SUGESTOES_PRODUTOS, enabled).apply();
    }

    public void setIADetectarFrustracao(boolean enabled) {
        prefs.edit().putBoolean(KEY_IA_DETECTAR_FRUSTRACAO, enabled).apply();
    }

    public void resetEstatisticas() {
        prefs.edit()
                .putLong(KEY_TOTAL_MSGS_RECEBIDAS, 0)
                .putLong(KEY_TOTAL_MSGS_ENVIADAS, 0)
                .putLong(KEY_TOTAL_PEDIDOS, 0)
                .apply();
    }

    /**
     * Substitui variaveis dinamicas em uma mensagem template.
     */
    public String processarTemplate(String template) {
        if (template == null) return "";
        return template
                .replace("{empresa}", getNomeEmpresa())
                .replace("{horario_inicio}", getHorarioInicio())
                .replace("{horario_fim}", getHorarioFim());
    }

    /**
     * Substitui variaveis dinamicas incluindo dados de pedido.
     */
    public String processarTemplatePedido(String template, int pedidoId, String total) {
        if (template == null) return "";
        return processarTemplate(template)
                .replace("{pedido_id}", String.valueOf(pedidoId))
                .replace("{total}", total != null ? total : "0.00");
    }
}
