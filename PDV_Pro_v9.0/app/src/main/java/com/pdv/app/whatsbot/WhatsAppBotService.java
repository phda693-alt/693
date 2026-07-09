package com.pdv.app.whatsbot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.pdv.app.R;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servico de escuta de notificacoes do WhatsApp.
 * Intercepta mensagens recebidas, processa com o BotEngine
 * e responde automaticamente via RemoteInput (resposta direta na notificacao).
 *
 * v3.8.0 - Correcao de responsividade:
 * - Cooldowns drasticamente reduzidos para resposta rapida
 * - Mensagens curtas (opcoes numericas) NUNCA sao deduplicadas
 * - Deduplicacao inteligente apenas para mensagens longas
 * - Anti-loop baseado em padroes de texto do bot (mais eficiente)
 * - Removido double-check de cooldown que causava bloqueio
 */
public class WhatsAppBotService extends NotificationListenerService {
    private static final String TAG = "WhatsAppBotService";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    private static final String CHANNEL_ID = "whatsbot_channel";
    private static final int NOTIFICATION_ID = 9999;

    // ===== CONTROLES ANTI-LOOP (v3.8.0 - otimizados) =====

    /** Intervalo minimo entre respostas ao mesmo contato (ms) - REDUZIDO */
    private static final long COOLDOWN_POR_CONTATO_MS = 1500; // 1.5 segundos (era 6s)

    /** Cooldown global apos qualquer envio do bot (ms) - REDUZIDO */
    private static final long POST_SEND_COOLDOWN_MS = 1000; // 1 segundo (era 5s)

    /** Tempo maximo que uma mensagem LONGA fica no cache de deduplicacao (ms) */
    private static final long DEDUP_EXPIRY_MS = 10000; // 10 segundos (era 60s)

    /** Tamanho maximo do cache de deduplicacao antes de forcar limpeza */
    private static final int MAX_DEDUP_CACHE_SIZE = 200;

    /** Tempo maximo que um ID de notificacao fica no cache de processados (ms) */
    private static final long NOTIF_ID_EXPIRY_MS = 10000; // 10 segundos (era 30s)

    /** Tempo maximo que uma resposta do bot fica no cache de filtragem (ms) */
    private static final long BOT_RESPONSE_EXPIRY_MS = 60000; // 60 segundos (era 120s)

    /** Tamanho maximo de mensagem considerada "curta" (opcao de menu) - NUNCA deduplicada */
    private static final int MSG_CURTA_MAX_LEN = 5;

    /** Cache de mensagens ja processadas: hash(contato+texto) -> timestamp */
    private final ConcurrentHashMap<String, Long> mensagensProcessadas = new ConcurrentHashMap<>();

    /** Ultimo timestamp de resposta enviada por contato */
    private final ConcurrentHashMap<String, Long> ultimaRespostaPorContato = new ConcurrentHashMap<>();

    /** IDs de notificacoes ja processadas: notificationId -> timestamp */
    private final ConcurrentHashMap<Integer, Long> notificacoesProcessadas = new ConcurrentHashMap<>();

    /** Set de respostas enviadas pelo bot para ignorar quando voltarem como notificacao */
    private final ConcurrentHashMap<String, Long> respostasEnviadasPeloBot = new ConcurrentHashMap<>();

    /** Timestamp do ultimo envio global do bot */
    private volatile long ultimoEnvioGlobalTimestamp = 0;

    /** Prefixos conhecidos de mensagens proprias em varios idiomas */
    private static final String[] PREFIXOS_MSG_PROPRIA = {
            "Voce:", "Você:", "You:", "Tu:", "Usted:",
            "voce:", "você:", "you:", "tu:", "usted:"
    };

    /** Palavras-chave de notificacoes de sistema do WhatsApp para ignorar */
    private static final String[] NOTIFICACOES_SISTEMA = {
            "Chamada", "ligacao", "ligação", "Call", "Ringing",
            "Verificacao", "Verificação", "Verification",
            "mensagens", "messages", "novas mensagens", "new messages",
            "Backup", "backup", "Aguardando", "Waiting",
            "Conectando", "Connecting"
    };

    /**
     * Padroes de texto que indicam que a mensagem e uma resposta do bot.
     * Se a mensagem contem qualquer um desses padroes, e ignorada.
     * v3.8.0: Padroes atualizados para os novos dialogos.
     */
    private static final String[] PADROES_RESPOSTA_BOT = {
            "Digite o numero da opcao",
            "Catalogo",
            "Consulta de Preco",
            "Fazer Pedido",
            "Consultar Pedido",
            "Atendimento Humano",
            "Rastrear Entregador",
            "Bem-vindo(a) ao",
            "assistente virtual",
            "CATALOGO",
            "RESUMO DO PEDIDO",
            "Pedido recebido com sucesso",
            "CONTATO",
            "Desculpe, ocorreu um erro",
            "RASTREAMENTO - Pedido",
            "ENTREGADORES ATIVOS",
            "Precos:",
            "Resultados:",
            "DETALHES DO PRODUTO",
            "Adicionado ao carrinho",
            "Pedido cancelado",
            "fora do horario de atendimento",
            "prazer atende-lo",
            "Opcao invalida",
            "Escolha a categoria",
            "CATEGORIA",
            "Continuar comprando",
            "Finalizar pedido",
            "Cancelar pedido",
            "Confirmar pedido",
            "Editar itens",
            "Voltar ao menu",
            "Voltar as categorias",
            "Voltar aos produtos",
            "Numero invalido",
            "Digite apenas",
            "Carrinho vazio",
            "Mensagem registrada",
            "atendente respondera",
            "De nada! Estamos",
            "entregadores ativos",
            "Sem dados de localizacao",
            "Nenhum entregador",
            "Nenhum produto",
            "Nenhuma categoria",
            "Erro ao",
            "Tente novamente",
            "Dados encontrados",
            "Dados para entrega",
            "nome completo",
            "endereco completo",
            "mesmo endereco",
            "Novos dados para entrega",
            "informar novo endereco",
            "\u2501\u2501\u2501\u2501\u2501\u2501",
            "*1*-Catalogo",
            "1*-Catalogo"
    };

    private WhatsAppBotEngine engine;
    private WhatsAppBotConfig config;
    private WhatsAppBotLogger logger;
    private ExecutorService executor;
    private Handler mainHandler;

    private static WhatsAppBotService instance;
    private static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        engine = new WhatsAppBotEngine(this);
        config = new WhatsAppBotConfig(this);
        logger = new WhatsAppBotLogger(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        isRunning = true;

        createNotificationChannel();

        // Agendar limpeza periodica dos caches (a cada 2 minutos)
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                limparCachesExpirados();
                if (isRunning) {
                    mainHandler.postDelayed(this, 2 * 60 * 1000);
                }
            }
        }, 2 * 60 * 1000);

        logger.logSistema("Servico do Bot WhatsApp iniciado (v3.8.0 fast-response)");
        Log.d(TAG, "WhatsAppBotService criado v3.8.0 fast-response");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        isRunning = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        mainHandler.removeCallbacksAndMessages(null);

        // Limpar caches
        mensagensProcessadas.clear();
        ultimaRespostaPorContato.clear();
        notificacoesProcessadas.clear();
        respostasEnviadasPeloBot.clear();

        logger.logSistema("Servico do Bot WhatsApp parado");
        Log.d(TAG, "WhatsAppBotService destruido");
    }

    public static WhatsAppBotService getInstance() {
        return instance;
    }

    public static boolean isServiceRunning() {
        return isRunning && instance != null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!config.isBotAtivo()) return;

        String packageName = sbn.getPackageName();
        if (!WHATSAPP_PACKAGE.equals(packageName) && !WHATSAPP_BUSINESS.equals(packageName)) {
            return;
        }

        try {
            Notification notification = sbn.getNotification();
            if (notification == null) return;

            Bundle extras = notification.extras;
            if (extras == null) return;

            long agora = System.currentTimeMillis();

            // Extrair informacoes da notificacao
            String titulo = extras.getString(Notification.EXTRA_TITLE, "");
            CharSequence textoCs = extras.getCharSequence(Notification.EXTRA_TEXT);
            String texto = textoCs != null ? textoCs.toString() : "";

            // Ignorar notificacoes vazias
            if (titulo.isEmpty() || texto.isEmpty()) return;

            String contato = titulo.trim();
            String mensagem = texto.trim();
            boolean isMsgCurta = mensagem.length() <= MSG_CURTA_MAX_LEN;

            // ===== CONTROLE 1: Cooldown global pos-envio =====
            // Para mensagens curtas (opcoes de menu), cooldown reduzido pela metade
            long cooldownGlobal = isMsgCurta ? POST_SEND_COOLDOWN_MS / 2 : POST_SEND_COOLDOWN_MS;
            if (agora - ultimoEnvioGlobalTimestamp < cooldownGlobal) {
                Log.d(TAG, "Cooldown global ativo (" + cooldownGlobal + "ms), ignorando: " + mensagem);
                return;
            }

            // ===== CONTROLE 2: Filtro de mensagens proprias =====
            if (isMensagemPropria(texto)) {
                Log.d(TAG, "Mensagem propria detectada, ignorando");
                return;
            }

            // ===== CONTROLE 3: Filtro de notificacoes de sistema =====
            if (isNotificacaoSistema(titulo, texto)) {
                Log.d(TAG, "Notificacao de sistema ignorada: " + titulo);
                return;
            }

            // ===== CONTROLE 4: Verificar se e grupo =====
            if (!config.isResponderGrupos()) {
                CharSequence subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
                boolean isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false);
                if (isGroup || (subText != null && subText.length() > 0) || titulo.contains(" - ")) {
                    if (texto.contains("@") || titulo.contains("@")) {
                        Log.d(TAG, "Mensagem de grupo ignorada: " + titulo);
                        return;
                    }
                }
            }

            // ===== CONTROLE 5: Verificar se o texto contem padroes de resposta do bot =====
            // Este e o filtro PRINCIPAL anti-loop - baseado em padroes de texto
            if (contemPadraoRespostaBot(texto)) {
                Log.d(TAG, "Padrao de resposta do bot detectado, ignorando");
                return;
            }

            // ===== CONTROLE 6: Verificar se e resposta enviada pelo bot (cache) =====
            if (isRespostaEnviadaPeloBot(texto)) {
                Log.d(TAG, "Resposta do bot em cache, ignorando");
                return;
            }

            // ===== CONTROLE 7: Verificar ID de notificacao ja processada =====
            int notifId = sbn.getId();
            long notifPostTime = sbn.getPostTime();
            String notifKey = notifId + "_" + notifPostTime;
            int notifHash = notifKey.hashCode();
            if (notificacoesProcessadas.containsKey(notifHash)) {
                Log.d(TAG, "Notificacao ja processada (ID: " + notifId + ")");
                return;
            }

            // ===== CONTROLE 8: Deduplicacao - APENAS para mensagens longas =====
            // Mensagens curtas (1, 2, 3, 0, etc.) NUNCA sao deduplicadas
            // pois o usuario pode legitimamente enviar "1" varias vezes em sequencia
            if (!isMsgCurta) {
                String dedupKey = gerarChaveDedup(contato, mensagem);
                if (mensagensProcessadas.containsKey(dedupKey)) {
                    Long tsAnterior = mensagensProcessadas.get(dedupKey);
                    if (tsAnterior != null && (agora - tsAnterior) < DEDUP_EXPIRY_MS) {
                        Log.d(TAG, "Mensagem longa duplicada ignorada de " + contato);
                        return;
                    }
                }
                mensagensProcessadas.put(dedupKey, agora);
            }

            // ===== CONTROLE 9: Cooldown por contato =====
            // Para mensagens curtas, cooldown reduzido pela metade
            long cooldownContato = isMsgCurta ? COOLDOWN_POR_CONTATO_MS / 2 : COOLDOWN_POR_CONTATO_MS;
            Long ultimaResposta = ultimaRespostaPorContato.get(contato);
            if (ultimaResposta != null && (agora - ultimaResposta) < cooldownContato) {
                Log.d(TAG, "Cooldown contato ativo (" + cooldownContato + "ms) para " + contato);
                return;
            }

            // Extrair acao de resposta direta
            Notification.Action replyAction = getReplyAction(notification);
            if (replyAction == null) {
                Log.d(TAG, "Sem acao de resposta para: " + titulo);
                return;
            }

            // ===== Registrar notificacao como processada =====
            notificacoesProcessadas.put(notifHash, agora);

            // v6.2.0 - Cachear ReplyAction para envio direto posterior
            cacheReplyAction(contato, replyAction);

            Log.d(TAG, ">>> Processando mensagem de " + contato + ": " + mensagem);

            // Processar em background
            int delay = config.getDelayResposta() * 1000;
            executor.execute(() -> {
                try {
                    // Delay para parecer mais natural (se configurado)
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }

                    // Processar mensagem - SEM double-check de cooldown
                    // Os filtros acima ja sao suficientes
                    String resposta = engine.processarMensagem(contato, mensagem);

                    if (resposta != null && !resposta.isEmpty()) {
                        // Registrar resposta como enviada pelo bot ANTES de enviar
                        registrarRespostaBot(resposta);

                        // Registrar timestamp da resposta (por contato e global)
                        long tsEnvio = System.currentTimeMillis();
                        ultimaRespostaPorContato.put(contato, tsEnvio);
                        ultimoEnvioGlobalTimestamp = tsEnvio;

                        // Enviar resposta via RemoteInput
                        enviarResposta(replyAction, resposta);

                        // Atualizar notificacao do bot
                        atualizarNotificacaoBot(contato, resposta);

                        Log.d(TAG, ">>> Resposta enviada para " + contato + " (" + resposta.length() + " chars)");
                    } else {
                        Log.d(TAG, ">>> Engine retornou null/vazio para " + contato + " (anti-loop interno)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao processar mensagem", e);
                    logger.logErro("Erro ao processar mensagem de " + contato, e);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar notificacao", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Limpar ID da notificacao removida do cache
        if (sbn != null) {
            String notifKey = sbn.getId() + "_" + sbn.getPostTime();
            notificacoesProcessadas.remove(notifKey.hashCode());
        }
    }

    // ===== METODOS DE CONTROLE ANTI-LOOP =====

    /**
     * Verifica se o texto e uma mensagem propria (enviada pelo usuario do dispositivo).
     */
    private boolean isMensagemPropria(String texto) {
        if (texto == null) return false;
        for (String prefixo : PREFIXOS_MSG_PROPRIA) {
            if (texto.startsWith(prefixo)) return true;
        }
        String textoLower = texto.toLowerCase();
        if (textoLower.startsWith("voce:") || textoLower.startsWith("você:")
                || textoLower.startsWith("you:") || textoLower.startsWith("tu:")) {
            return true;
        }
        return false;
    }

    /**
     * Verifica se a notificacao e de sistema do WhatsApp (chamada, backup, etc).
     */
    private boolean isNotificacaoSistema(String titulo, String texto) {
        String tituloLower = titulo.toLowerCase();
        String textoLower = texto.toLowerCase();

        for (String keyword : NOTIFICACOES_SISTEMA) {
            String kw = keyword.toLowerCase();
            if (textoLower.contains(kw) || tituloLower.contains(kw)) {
                return true;
            }
        }

        if (textoLower.matches("\\d+\\s+(novas?\\s+)?mensage(m|ns).*")
                || textoLower.matches("\\d+\\s+new\\s+message.*")) {
            return true;
        }

        if (tituloLower.equals("whatsapp") || tituloLower.matches("\\d+\\s+(chat|conversa).*")) {
            return true;
        }

        return false;
    }

    /**
     * Verifica se o texto contem padroes conhecidos de respostas do bot.
     * Este e o filtro PRINCIPAL anti-loop - muito mais confiavel que timers.
     */
    private boolean contemPadraoRespostaBot(String texto) {
        if (texto == null || texto.isEmpty()) return false;
        for (String padrao : PADROES_RESPOSTA_BOT) {
            if (texto.contains(padrao)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se o texto corresponde a uma resposta recentemente enviada pelo bot.
     */
    private boolean isRespostaEnviadaPeloBot(String texto) {
        if (texto == null || texto.isEmpty()) return false;

        long agora = System.currentTimeMillis();
        String textoNorm = normalizarTexto(texto);
        String textoHash = "HASH:" + texto.trim().hashCode();

        Iterator<Map.Entry<String, Long>> it = respostasEnviadasPeloBot.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            // Limpar entradas antigas
            if (agora - entry.getValue() > BOT_RESPONSE_EXPIRY_MS) {
                it.remove();
                continue;
            }
            String chave = entry.getKey();
            // Comparacao normalizada
            if (textoNorm.equals(chave)) {
                it.remove();
                return true;
            }
            // Comparacao por hash
            if (chave.equals(textoHash)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * Registra uma resposta enviada pelo bot para posterior filtragem.
     */
    private void registrarRespostaBot(String resposta) {
        if (resposta == null || resposta.isEmpty()) return;
        long agora = System.currentTimeMillis();

        // Registrar texto normalizado
        String textoNorm = normalizarTexto(resposta);
        respostasEnviadasPeloBot.put(textoNorm, agora);

        // Registrar hash completo
        String textoHash = "HASH:" + resposta.trim().hashCode();
        respostasEnviadasPeloBot.put(textoHash, agora);
    }

    /**
     * Normaliza texto para comparacao: primeiros 80 chars, sem espacos extras.
     */
    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String norm = texto.trim().replaceAll("\\s+", " ");
        return norm.length() > 80 ? norm.substring(0, 80) : norm;
    }

    /**
     * Gera uma chave unica para deduplicacao baseada no contato e texto da mensagem.
     */
    private String gerarChaveDedup(String contato, String mensagem) {
        return contato + "|" + mensagem.trim();
    }

    /**
     * Limpa caches expirados para evitar vazamento de memoria.
     */
    private void limparCachesExpirados() {
        long agora = System.currentTimeMillis();
        int removidos = 0;

        // Limpar mensagens processadas expiradas
        Iterator<Map.Entry<String, Long>> it1 = mensagensProcessadas.entrySet().iterator();
        while (it1.hasNext()) {
            if (agora - it1.next().getValue() > DEDUP_EXPIRY_MS) {
                it1.remove();
                removidos++;
            }
        }

        // Limpar notificacoes processadas expiradas
        Iterator<Map.Entry<Integer, Long>> it2 = notificacoesProcessadas.entrySet().iterator();
        while (it2.hasNext()) {
            if (agora - it2.next().getValue() > NOTIF_ID_EXPIRY_MS) {
                it2.remove();
                removidos++;
            }
        }

        // Limpar cooldowns antigos (mais de 30 segundos)
        Iterator<Map.Entry<String, Long>> it3 = ultimaRespostaPorContato.entrySet().iterator();
        while (it3.hasNext()) {
            if (agora - it3.next().getValue() > 30000) {
                it3.remove();
                removidos++;
            }
        }

        // Limpar respostas do bot antigas
        Iterator<Map.Entry<String, Long>> it4 = respostasEnviadasPeloBot.entrySet().iterator();
        while (it4.hasNext()) {
            if (agora - it4.next().getValue() > BOT_RESPONSE_EXPIRY_MS) {
                it4.remove();
                removidos++;
            }
        }

        // Forcar limpeza se cache muito grande
        if (mensagensProcessadas.size() > MAX_DEDUP_CACHE_SIZE) {
            mensagensProcessadas.clear();
            removidos += MAX_DEDUP_CACHE_SIZE;
        }

        if (removidos > 0) {
            Log.d(TAG, "Limpeza de cache: " + removidos + " entradas removidas");
        }
    }

    // ===== METODOS DE ENVIO E NOTIFICACAO =====

    /**
     * Extrai a acao de resposta direta de uma notificacao.
     */
    private Notification.Action getReplyAction(Notification notification) {
        if (notification.actions == null) return null;

        for (Notification.Action action : notification.actions) {
            if (action.getRemoteInputs() != null && action.getRemoteInputs().length > 0) {
                return action;
            }
        }
        return null;
    }

    /**
     * Envia uma resposta usando RemoteInput (resposta direta na notificacao).
     */
    private void enviarResposta(Notification.Action action, String resposta) {
        try {
            if (action.getRemoteInputs() == null || action.getRemoteInputs().length == 0) {
                Log.w(TAG, "Sem RemoteInput disponivel");
                return;
            }

            android.app.RemoteInput[] remoteInputs = action.getRemoteInputs();
            Intent intent = new Intent();
            Bundle bundle = new Bundle();

            for (android.app.RemoteInput remoteInput : remoteInputs) {
                bundle.putCharSequence(remoteInput.getResultKey(), resposta);
            }

            android.app.RemoteInput.addResultsToIntent(remoteInputs, intent, bundle);

            action.actionIntent.send(this, 0, intent);

            Log.d(TAG, "Resposta enviada com sucesso via RemoteInput");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar resposta", e);
            logger.logErro("Erro ao enviar resposta via RemoteInput", e);
        }
    }

    /**
     * Cria o canal de notificacao para o bot.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WhatsApp Bot PDV Pro",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificacoes do Bot WhatsApp do PDV Pro");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Atualiza a notificacao persistente do bot com a ultima interacao.
     */
    private void atualizarNotificacaoBot(String contato, String ultimaResposta) {
        try {
            String resumo = ultimaResposta.length() > 80
                    ? ultimaResposta.substring(0, 80) + "..."
                    : ultimaResposta;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_whatsbot)
                    .setContentTitle("Bot WhatsApp Ativo")
                    .setContentText("Ultimo: " + contato + " - " + resumo)
                    .setOngoing(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao atualizar notificacao: " + e.getMessage());
        }
    }

    // ===== v6.2.0 - ENVIO DIRETO DE MENSAGENS =====

    /** Cache de ReplyActions por contato para envio direto */
    private final ConcurrentHashMap<String, Notification.Action> replyActionsCache = new ConcurrentHashMap<>();

    /**
     * Armazena a ReplyAction de um contato para uso posterior no envio direto.
     * Chamado internamente quando uma notificacao e processada.
     */
    private void cacheReplyAction(String contato, Notification.Action action) {
        if (contato != null && action != null) {
            replyActionsCache.put(contato, action);
            // Tambem armazenar pelo numero limpo
            String numLimpo = contato.replaceAll("[^0-9]", "");
            if (!numLimpo.isEmpty()) {
                replyActionsCache.put(numLimpo, action);
            }
        }
    }

    /**
     * Envia uma mensagem diretamente para um contato usando RemoteInput.
     * Utiliza o cache de ReplyActions das notificacoes recentes.
     *
     * @param celular  Numero do celular (com ou sem DDI)
     * @param mensagem Texto da mensagem
     * @return true se a mensagem foi enviada com sucesso
     */
    public boolean enviarMensagemDireta(String celular, String mensagem) {
        if (celular == null || mensagem == null || mensagem.isEmpty()) return false;

        try {
            String numLimpo = celular.replaceAll("[^0-9]", "");

            // Tentar encontrar ReplyAction pelo numero ou variantes
            Notification.Action action = null;

            // Buscar direto pelo numero
            action = replyActionsCache.get(numLimpo);

            // Buscar sem DDI
            if (action == null && numLimpo.startsWith("55") && numLimpo.length() > 4) {
                action = replyActionsCache.get(numLimpo.substring(2));
            }

            // Buscar com DDI
            if (action == null && !numLimpo.startsWith("55")) {
                action = replyActionsCache.get("55" + numLimpo);
            }

            // Buscar por nome do contato (percorrer todas as chaves)
            if (action == null) {
                for (Map.Entry<String, Notification.Action> entry : replyActionsCache.entrySet()) {
                    String key = entry.getKey().replaceAll("[^0-9]", "");
                    if (key.contains(numLimpo) || numLimpo.contains(key)) {
                        action = entry.getValue();
                        break;
                    }
                }
            }

            if (action != null) {
                // Registrar resposta como enviada pelo bot
                registrarRespostaBot(mensagem);

                // Enviar via RemoteInput
                enviarResposta(action, mensagem);

                // Atualizar timestamps
                long tsEnvio = System.currentTimeMillis();
                ultimoEnvioGlobalTimestamp = tsEnvio;

                Log.d(TAG, "Mensagem direta enviada para " + celular);
                logger.logMsgEnviada(celular, "[DIRETO] " + (mensagem.length() > 50 ? mensagem.substring(0, 50) + "..." : mensagem));
                return true;
            }

            Log.d(TAG, "Sem ReplyAction em cache para " + celular);
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar mensagem direta", e);
            return false;
        }
    }

    /**
     * Retorna o engine do bot para acesso externo.
     */
    public WhatsAppBotEngine getEngine() {
        return engine;
    }

    /**
     * Retorna o logger do bot.
     */
    public WhatsAppBotLogger getLogger() {
        return logger;
    }
}
