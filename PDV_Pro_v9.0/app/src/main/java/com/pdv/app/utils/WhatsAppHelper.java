package com.pdv.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.pdv.app.whatsbot.WhatsAppAutoSender;

/**
 * Helper para integracao com WhatsApp.
 * Suporta envio de cupons, mensagens de texto e notificacoes.
 *
 * v6.2.0 - ENVIO AUTOMATICO:
 * Agora utiliza WhatsAppAutoSender para enviar mensagens automaticamente
 * sem que o lojista precise abrir o WhatsApp ou procurar o contato.
 * O metodo antigo (abrir URL) e mantido como fallback.
 */
public class WhatsAppHelper {
    private static final String TAG = "WhatsAppHelper";

    /**
     * Envia um cupom via WhatsApp para um numero especifico.
     * v6.2.0: Usa envio automatico quando o celular e informado.
     */
    public static void enviarCupom(Context context, String texto, String celular) {
        if (celular != null && !celular.isEmpty() && celular.replaceAll("[^0-9]", "").length() >= 8) {
            // v6.2.0 - Envio automatico (sem abrir WhatsApp)
            WhatsAppAutoSender.enviarCupomAutomatico(context, texto, celular);
        } else {
            // Sem celular informado: usar metodo antigo (seletor)
            enviarCupomManual(context, texto, celular);
        }
    }

    /**
     * Metodo original de envio via Intent/URL (fallback).
     */
    public static void enviarCupomManual(Context context, String texto, String celular) {
        try {
            String phone = formatarNumero(celular);
            String encodedText = Uri.encode(texto);
            String url;

            if (phone.length() >= 12) {
                url = "https://api.whatsapp.com/send?phone=" + phone + "&text=" + encodedText;
            } else {
                url = "https://api.whatsapp.com/send?text=" + encodedText;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Tentar abrir no WhatsApp primeiro
            if (isWhatsAppInstalled(context)) {
                intent.setPackage("com.whatsapp");
            }

            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Erro ao enviar via WhatsApp: " + e.getMessage());
            // Fallback: share via any app
            compartilharTexto(context, texto, "Enviar cupom via");
        }
    }

    /**
     * Envia uma mensagem simples via WhatsApp.
     * v6.2.0: Usa envio automatico quando o celular e informado.
     */
    public static void enviarMensagem(Context context, String mensagem, String celular) {
        if (celular != null && !celular.isEmpty() && celular.replaceAll("[^0-9]", "").length() >= 8) {
            // v6.2.0 - Envio automatico
            WhatsAppAutoSender.enviarAutomatico(context, mensagem, celular);
        } else {
            enviarCupomManual(context, mensagem, celular);
        }
    }

    /**
     * Envia notificacao de venda para o admin via WhatsApp.
     * v6.2.0: Usa envio automatico.
     */
    public static void notificarVenda(Context context, int vendaId, double total, String celularAdmin) {
        String msg = "\uD83D\uDD14 *NOVA VENDA REGISTRADA*\n\n"
                + "\uD83D\uDCCB Venda: #" + vendaId + "\n"
                + "\uD83D\uDCB0 Total: R$ " + FormatUtils.formatMoney(total) + "\n"
                + "\uD83D\uDCC5 Data: " + FormatUtils.getCurrentDateTime() + "\n\n"
                + "_Notificacao automatica do PDV Pro_";

        if (celularAdmin != null && !celularAdmin.isEmpty()) {
            WhatsAppAutoSender.enviarAutomatico(context, msg, celularAdmin);
        } else {
            enviarCupomManual(context, msg, celularAdmin);
        }
    }

    /**
     * Envia catalogo de produtos via WhatsApp.
     */
    public static void enviarCatalogo(Context context, String catalogo, String celular) {
        enviarCupom(context, catalogo, celular);
    }

    /**
     * Compartilha texto via qualquer app.
     */
    public static void compartilharTexto(Context context, String texto, String titulo) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, texto);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(shareIntent, titulo));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar: " + e.getMessage());
        }
    }

    /**
     * Verifica se o WhatsApp esta instalado.
     */
    public static boolean isWhatsAppInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Verifica se o WhatsApp Business esta instalado.
     */
    public static boolean isWhatsAppBusinessInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.whatsapp.w4b", PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
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
     * Abre uma conversa do WhatsApp com um numero especifico.
     */
    public static void abrirConversa(Context context, String celular) {
        try {
            String phone = formatarNumero(celular);
            String url = "https://api.whatsapp.com/send?phone=" + phone;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir conversa: " + e.getMessage());
        }
    }
}
