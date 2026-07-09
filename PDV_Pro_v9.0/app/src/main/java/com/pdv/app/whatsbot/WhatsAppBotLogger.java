package com.pdv.app.whatsbot;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Sistema de logging e historico de conversas do Bot WhatsApp.
 * Registra todas as interacoes em arquivo local para auditoria.
 */
public class WhatsAppBotLogger {
    private static final String TAG = "WhatsAppBotLogger";
    private static final String LOG_DIR = "PDVPro/WhatsBot/logs";
    private static final String CONV_DIR = "PDVPro/WhatsBot/conversas";
    private static final int MAX_LOG_LINES = 5000;
    private static final int MAX_MEMORY_LOGS = 200;

    private Context context;
    private WhatsAppBotConfig config;
    private List<LogEntry> memoryLogs;

    public WhatsAppBotLogger(Context context) {
        this.context = context;
        this.config = new WhatsAppBotConfig(context);
        this.memoryLogs = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Classe para representar uma entrada de log.
     */
    public static class LogEntry {
        public String timestamp;
        public String tipo; // RECEBIDA, ENVIADA, SISTEMA, ERRO, PEDIDO
        public String contato;
        public String mensagem;
        public String detalhes;

        public LogEntry(String tipo, String contato, String mensagem, String detalhes) {
            this.timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
            this.tipo = tipo;
            this.contato = contato;
            this.mensagem = mensagem;
            this.detalhes = detalhes;
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] [" + tipo + "] " + contato + ": " + mensagem
                    + (detalhes != null && !detalhes.isEmpty() ? " (" + detalhes + ")" : "");
        }
    }

    /**
     * Registra uma mensagem recebida.
     */
    public void logMsgRecebida(String contato, String mensagem) {
        LogEntry entry = new LogEntry("RECEBIDA", contato, mensagem, null);
        addLog(entry);
        config.incrementMsgsRecebidas();
    }

    /**
     * Registra uma mensagem enviada pelo bot.
     */
    public void logMsgEnviada(String contato, String mensagem) {
        LogEntry entry = new LogEntry("ENVIADA", contato, truncate(mensagem, 100), null);
        addLog(entry);
        config.incrementMsgsEnviadas();
    }

    /**
     * Registra um evento do sistema.
     */
    public void logSistema(String mensagem) {
        LogEntry entry = new LogEntry("SISTEMA", "BOT", mensagem, null);
        addLog(entry);
    }

    /**
     * Registra um erro.
     */
    public void logErro(String mensagem, Exception e) {
        String detalhes = e != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : null;
        LogEntry entry = new LogEntry("ERRO", "BOT", mensagem, detalhes);
        addLog(entry);
    }

    /**
     * Registra um pedido recebido.
     */
    public void logPedido(String contato, int pedidoId, String total) {
        LogEntry entry = new LogEntry("PEDIDO", contato,
                "Pedido #" + pedidoId + " - R$ " + total, null);
        addLog(entry);
        config.incrementPedidos();
    }

    /**
     * Adiciona uma entrada ao log em memoria e em arquivo.
     */
    private void addLog(LogEntry entry) {
        // Adicionar em memoria
        memoryLogs.add(entry);
        if (memoryLogs.size() > MAX_MEMORY_LOGS) {
            memoryLogs.remove(0);
        }

        // Log no Android
        Log.d(TAG, entry.toString());

        // Salvar em arquivo se log ativo
        if (config.isLogAtivo()) {
            saveToFile(entry);
        }
    }

    /**
     * Salva a entrada de log em arquivo.
     */
    private void saveToFile(LogEntry entry) {
        try {
            File dir = new File(context.getFilesDir(), LOG_DIR);
            if (!dir.exists()) dir.mkdirs();

            String fileName = "bot_log_" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".txt";
            File logFile = new File(dir, fileName);

            BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true));
            writer.write(entry.toString());
            writer.newLine();
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar log: " + e.getMessage());
        }
    }

    /**
     * Salva uma conversa completa com um contato.
     */
    public void salvarConversa(String contato, List<String> mensagens) {
        try {
            File dir = new File(context.getFilesDir(), CONV_DIR);
            if (!dir.exists()) dir.mkdirs();

            String safeContato = contato.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "conv_" + safeContato + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
            File convFile = new File(dir, fileName);

            BufferedWriter writer = new BufferedWriter(new FileWriter(convFile));
            writer.write("=== Conversa com " + contato + " ===");
            writer.newLine();
            writer.write("Data: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date()));
            writer.newLine();
            writer.write("================================");
            writer.newLine();
            writer.newLine();
            for (String msg : mensagens) {
                writer.write(msg);
                writer.newLine();
            }
            writer.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar conversa: " + e.getMessage());
        }
    }

    /**
     * Retorna os logs em memoria para exibicao na tela.
     */
    public List<LogEntry> getMemoryLogs() {
        return new ArrayList<>(memoryLogs);
    }

    /**
     * Retorna os logs de um dia especifico lidos do arquivo.
     */
    public List<String> getLogsFromFile(String data) {
        List<String> lines = new ArrayList<>();
        try {
            File dir = new File(context.getFilesDir(), LOG_DIR);
            String fileName = "bot_log_" + data + ".txt";
            File logFile = new File(dir, fileName);

            if (logFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(logFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                reader.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao ler logs: " + e.getMessage());
        }
        return lines;
    }

    /**
     * Retorna os logs de hoje.
     */
    public List<String> getLogsHoje() {
        String hoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return getLogsFromFile(hoje);
    }

    /**
     * Limpa os logs em memoria.
     */
    public void limparMemoria() {
        memoryLogs.clear();
    }

    /**
     * Limpa todos os arquivos de log.
     */
    public void limparTodosLogs() {
        try {
            File dir = new File(context.getFilesDir(), LOG_DIR);
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
            File convDir = new File(context.getFilesDir(), CONV_DIR);
            if (convDir.exists()) {
                File[] files = convDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        f.delete();
                    }
                }
            }
            memoryLogs.clear();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao limpar logs: " + e.getMessage());
        }
    }

    /**
     * Retorna estatisticas formatadas do bot.
     */
    public String getEstatisticasFormatadas() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Estatisticas do Bot*\n\n");
        sb.append("📩 Mensagens recebidas: *").append(config.getTotalMsgsRecebidas()).append("*\n");
        sb.append("📤 Mensagens enviadas: *").append(config.getTotalMsgsEnviadas()).append("*\n");
        sb.append("🛒 Pedidos recebidos: *").append(config.getTotalPedidos()).append("*\n");
        sb.append("\n📅 Logs de hoje: *").append(getLogsHoje().size()).append("* entradas");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
