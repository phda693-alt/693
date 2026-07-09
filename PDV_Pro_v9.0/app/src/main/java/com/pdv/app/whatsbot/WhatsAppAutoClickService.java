package com.pdv.app.whatsbot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * AccessibilityService que detecta a tela do WhatsApp e clica automaticamente
 * no botao de enviar quando ha uma mensagem pendente do PDV Pro.
 *
 * Isso permite que o lojista envie notificacoes ao cliente sem precisar
 * interagir manualmente com o WhatsApp.
 *
 * v6.2.0 - Envio automatico de notificacoes
 */
public class WhatsAppAutoClickService extends AccessibilityService {
    private static final String TAG = "WhatsAutoClick";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean jaClicou = false;
    private int tentativas = 0;
    private static final int MAX_TENTATIVAS = 10;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "WhatsAppAutoClickService conectado");
        WhatsAppAutoSender.setAccessibilityReady(true);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 100;
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // So processar se ha envio pendente
        if (!WhatsAppAutoSender.isPendingAutoSend()) {
            jaClicou = false;
            tentativas = 0;
            return;
        }

        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // Verificar se estamos no WhatsApp
        if (!WHATSAPP_PACKAGE.equals(packageName) && !WHATSAPP_BUSINESS.equals(packageName)) {
            return;
        }

        if (jaClicou) return;

        // Tentar encontrar e clicar no botao de enviar
        handler.postDelayed(() -> {
            if (jaClicou || !WhatsAppAutoSender.isPendingAutoSend()) return;
            tentativas++;

            if (tentativas > MAX_TENTATIVAS) {
                Log.w(TAG, "Max tentativas atingido, desistindo do auto-click");
                WhatsAppAutoSender.consumePendingAutoSend();
                jaClicou = false;
                tentativas = 0;
                voltarParaPDV();
                return;
            }

            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return;

            boolean clicou = tentarClicarEnviar(rootNode);
            rootNode.recycle();

            if (clicou) {
                jaClicou = true;
                tentativas = 0;
                WhatsAppAutoSender.consumePendingAutoSend();
                Log.d(TAG, "Botao enviar clicado com sucesso!");

                // Voltar para o PDV Pro apos 1.5 segundos
                handler.postDelayed(this::voltarParaPDV, 1500);
            }
        }, 800);
    }

    /**
     * Tenta encontrar e clicar no botao de enviar do WhatsApp.
     * Procura por varios identificadores possiveis do botao.
     */
    private boolean tentarClicarEnviar(AccessibilityNodeInfo root) {
        // Tentar pelo ID do botao de enviar (WhatsApp usa esses IDs)
        String[] sendButtonIds = {
                "com.whatsapp:id/send",
                "com.whatsapp:id/btn_send",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp.w4b:id/btn_send"
        };

        for (String id : sendButtonIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable() && node.isEnabled()) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clicou no botao enviar via ID: " + id);
                        return true;
                    }
                }
            }
        }

        // Tentar por content description
        String[] descriptions = {"Send", "Enviar", "send", "enviar"};
        for (String desc : descriptions) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(desc);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isClickable() && node.getContentDescription() != null
                            && node.getContentDescription().toString().toLowerCase().contains("send")) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clicou no botao enviar via description: " + desc);
                        return true;
                    }
                    if (node.isClickable() && node.getContentDescription() != null
                            && node.getContentDescription().toString().toLowerCase().contains("enviar")) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clicou no botao enviar via description: " + desc);
                        return true;
                    }
                }
            }
        }

        // Tentar buscar recursivamente um ImageButton ou ImageView clicavel
        // que esteja no canto inferior direito (tipicamente o botao enviar)
        return buscarBotaoEnviarRecursivo(root);
    }

    /**
     * Busca recursivamente por um botao de enviar na arvore de acessibilidade.
     */
    private boolean buscarBotaoEnviarRecursivo(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Verificar se este no e o botao de enviar
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String descStr = desc.toString().toLowerCase();
            if ((descStr.contains("send") || descStr.contains("enviar"))
                    && node.isClickable() && node.isEnabled()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Clicou no botao enviar via busca recursiva");
                return true;
            }
        }

        // Verificar filhos
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (buscarBotaoEnviarRecursivo(child)) {
                    child.recycle();
                    return true;
                }
                child.recycle();
            }
        }

        return false;
    }

    /**
     * Volta para o aplicativo PDV Pro.
     */
    private void voltarParaPDV() {
        try {
            android.content.Intent intent = getPackageManager()
                    .getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao voltar para PDV Pro: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "WhatsAppAutoClickService interrompido");
        WhatsAppAutoSender.setAccessibilityReady(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        WhatsAppAutoSender.setAccessibilityReady(false);
        Log.d(TAG, "WhatsAppAutoClickService destruido");
    }
}
