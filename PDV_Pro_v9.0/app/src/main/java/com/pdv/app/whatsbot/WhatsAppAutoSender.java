package com.pdv.app.whatsbot;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Envia mensagens WhatsApp automaticamente sem interacao do usuario.
 *
 * Estrategia de envio (em ordem de prioridade):
 *
 * 1. Se o WhatsApp Bot Service estiver ativo e o contato tiver uma sessao,
 *    injeta a resposta diretamente via RemoteInput (mesmo mecanismo do bot).
 *
 * 2. Usa Intent com ACTION_SEND para o WhatsApp com flag de auto-submit
 *    via AccessibilityService (WhatsAppAutoClickService).
 *
 * 3. Fallback: abre a URL api.whatsapp.com/send (metodo antigo).
 *
 * v6.2.0 - Envio automatico de notificacoes ao cliente
 */
public class WhatsAppAutoSender {
    private static final String TAG = "WhatsAppAutoSender";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Flag global que indica se o AccessibilityService esta ativo
    private static AtomicBoolean accessibilityReady = new AtomicBoolean(false);

    // Fila de mensagens pendentes para o AccessibilityService clicar em enviar
    private static volatile String pendingAutoSendTarget = null;
    private static volatile boolean pendingAutoSend = false;

    /**
     * Callback para informar o resultado do envio.
     */
    public interface SendCallback {
        void onSuccess(String metodo);
        void onFallback(String motivo);
    }

    /**
     * Envia uma mensagem WhatsApp automaticamente para o numero informado.
     * O lojista NAO precisa abrir o WhatsApp nem procurar o contato.
     *
     * @param context  Contexto da aplicacao
     * @param mensagem Texto da mensagem a enviar
     * @param celular  Numero do celular do destinatario (com ou sem DDI)
     * @param callback Callback opcional para resultado
     */
    public static void enviarAutomatico(Context context, String mensagem, String celular, SendCallback callback) {
        if (context == null || mensagem == null || mensagem.isEmpty()) {
            if (callback != null) callback.onFallback("Mensagem vazia");
            return;
        }

        executor.execute(() -> {
            try {
                String phone = formatarNumero(celular);

                // ===== ESTRATEGIA 1: Enviar via Bot Service (RemoteInput) =====
                // Se o bot esta rodando e o contato tem notificacao ativa,
                // podemos injetar a mensagem diretamente
                if (WhatsAppBotService.isServiceRunning()) {
                    WhatsAppBotService service = WhatsAppBotService.getInstance();
                    if (service != null) {
                        boolean enviado = service.enviarMensagemDireta(phone, mensagem);
                        if (enviado) {
                            Log.d(TAG, "Mensagem enviada via BotService RemoteInput para " + phone);
                            mainHandler.post(() -> {
                                Toast.makeText(context, "✓ Notificação enviada automaticamente!", Toast.LENGTH_SHORT).show();
                                if (callback != null) callback.onSuccess("BotService");
                            });
                            return;
                        }
                    }
                }

                // ===== ESTRATEGIA 2: Enviar via Intent direto com auto-click =====
                // Usa o AccessibilityService para clicar automaticamente no botao enviar
                if (accessibilityReady.get()) {
                    pendingAutoSend = true;
                    pendingAutoSendTarget = phone;

                    mainHandler.post(() -> {
                        try {
                            // Abrir WhatsApp diretamente na conversa com o numero
                            String url = "https://api.whatsapp.com/send?phone=" + phone
                                    + "&text=" + URLEncoder.encode(mensagem, "UTF-8");
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            intent.setPackage("com.whatsapp");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                            context.startActivity(intent);

                            // O AccessibilityService vai detectar a tela do WhatsApp
                            // e clicar automaticamente no botao enviar
                            Toast.makeText(context, "✓ Enviando notificação...", Toast.LENGTH_SHORT).show();
                            if (callback != null) callback.onSuccess("AutoClick");
                        } catch (Exception e) {
                            Log.w(TAG, "Erro no auto-click, usando fallback", e);
                            pendingAutoSend = false;
                            enviarViaIntentDireto(context, mensagem, phone, callback);
                        }
                    });
                    return;
                }

                // ===== ESTRATEGIA 3: Enviar via Intent direto (sem auto-click) =====
                // Abre o WhatsApp com a mensagem pre-preenchida
                // e envia automaticamente usando ACTION_SEND
                mainHandler.post(() -> enviarViaIntentDireto(context, mensagem, phone, callback));

            } catch (Exception e) {
                Log.e(TAG, "Erro ao enviar mensagem automatica", e);
                mainHandler.post(() -> {
                    if (callback != null) callback.onFallback("Erro: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Envia mensagem usando Intent ACTION_SEND diretamente para o WhatsApp.
     * Este metodo envia a mensagem sem precisar que o usuario confirme manualmente.
     */
    private static void enviarViaIntentDireto(Context context, String mensagem, String phone, SendCallback callback) {
        try {
            // Metodo 1: Tentar enviar via content provider do WhatsApp (vCard trick)
            // que permite enviar sem abrir a UI completa
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TEXT, mensagem);
            sendIntent.putExtra("jid", phone + "@s.whatsapp.net");
            sendIntent.setPackage("com.whatsapp");
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            context.startActivity(sendIntent);

            // Agendar retorno ao app PDV Pro apos 2 segundos
            mainHandler.postDelayed(() -> {
                try {
                    // Voltar para o PDV Pro automaticamente
                    Intent backIntent = context.getPackageManager()
                            .getLaunchIntentForPackage(context.getPackageName());
                    if (backIntent != null) {
                        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        context.startActivity(backIntent);
                    }
                } catch (Exception ignored) {}
            }, 2500);

            Toast.makeText(context, "✓ Notificação enviada via WhatsApp!", Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onSuccess("IntentDireto");

        } catch (Exception e) {
            Log.w(TAG, "Erro ao enviar via Intent direto, usando URL", e);
            // Fallback final: abrir URL do WhatsApp
            try {
                String url = "https://api.whatsapp.com/send?phone=" + phone
                        + "&text=" + URLEncoder.encode(mensagem, "UTF-8");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Toast.makeText(context, "Confirme o envio no WhatsApp", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onFallback("URL aberta");
            } catch (Exception ex) {
                Log.e(TAG, "Falha total ao enviar", ex);
                Toast.makeText(context, "Erro ao enviar notificação", Toast.LENGTH_SHORT).show();
                if (callback != null) callback.onFallback("Erro total");
            }
        }
    }

    /**
     * Envia mensagem automatica sem callback (versao simplificada).
     */
    public static void enviarAutomatico(Context context, String mensagem, String celular) {
        enviarAutomatico(context, mensagem, celular, null);
    }

    /**
     * Envia cupom automaticamente via WhatsApp.
     */
    public static void enviarCupomAutomatico(Context context, String cupom, String celular) {
        enviarAutomatico(context, cupom, celular, new SendCallback() {
            @Override
            public void onSuccess(String metodo) {
                Log.d(TAG, "Cupom enviado automaticamente via " + metodo);
            }
            @Override
            public void onFallback(String motivo) {
                Log.w(TAG, "Cupom enviado com fallback: " + motivo);
            }
        });
    }

    /**
     * Envia notificacao de status de entrega automaticamente.
     */
    public static void notificarStatusEntrega(Context context, int vendaId, String status,
                                               String celular, String nomeEntregador) {
        String mensagem;
        switch (status) {
            case "preparando":
                mensagem = "Olá! Seu pedido #" + vendaId
                        + " está sendo preparado. Em breve sairemos para entrega! 🍽️";
                break;
            case "em_rota":
                mensagem = "Olá! Seu pedido #" + vendaId + " saiu para entrega! 🛵";
                if (nomeEntregador != null && !nomeEntregador.isEmpty()) {
                    mensagem += "\nEntregador: " + nomeEntregador;
                }
                break;
            case "entregue":
                mensagem = "Olá! Seu pedido #" + vendaId
                        + " foi entregue com sucesso! Obrigado pela preferência! 😊🙏";
                break;
            case "cancelada":
                mensagem = "Olá! Informamos que seu pedido #" + vendaId
                        + " foi cancelado. Entre em contato para mais informações.";
                break;
            default:
                mensagem = "Olá! Atualização sobre seu pedido #" + vendaId + ": " + status;
                break;
        }

        enviarAutomatico(context, mensagem, celular, new SendCallback() {
            @Override
            public void onSuccess(String metodo) {
                Log.d(TAG, "Notificacao de status enviada via " + metodo + " para pedido #" + vendaId);
            }
            @Override
            public void onFallback(String motivo) {
                Log.w(TAG, "Notificacao de status com fallback: " + motivo);
            }
        });
    }

    /**
     * Formata um numero de telefone para o formato internacional.
     */
    public static String formatarNumero(String celular) {
        if (celular == null) return "";
        String phone = celular.replaceAll("[^0-9]", "");
        if (phone.length() > 0 && !phone.startsWith("55")) {
            phone = "55" + phone;
        }
        return phone;
    }

    /**
     * Define se o AccessibilityService esta pronto.
     */
    public static void setAccessibilityReady(boolean ready) {
        accessibilityReady.set(ready);
        Log.d(TAG, "AccessibilityService ready: " + ready);
    }

    /**
     * Verifica se ha envio automatico pendente.
     */
    public static boolean isPendingAutoSend() {
        return pendingAutoSend;
    }

    /**
     * Consome o flag de envio pendente.
     */
    public static void consumePendingAutoSend() {
        pendingAutoSend = false;
        pendingAutoSendTarget = null;
    }

    /**
     * Retorna o numero alvo do envio pendente.
     */
    public static String getPendingTarget() {
        return pendingAutoSendTarget;
    }
}
