package com.pdv.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * v8.0.11 - Classe utilitária para enviar Ordens de Serviço via WhatsApp
 * Formata a mensagem e integra com o aplicativo WhatsApp
 */
public class WhatsAppManager {
    private static final String TAG = "WhatsAppManager";

    /**
     * Formata a Ordem de Serviço em uma mensagem de texto para WhatsApp
     */
    public static String formatarOSParaWhatsApp(Map<String, Object> os) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("*ORDEM DE SERVIÇO #").append(os.get("numero")).append("*\n");
        sb.append("═══════════════════════════════════════\n\n");
        
        // Cliente
        String cliente = (String) os.get("cliente_cadastrado_nome");
        if (cliente == null || cliente.isEmpty()) {
            cliente = (String) os.get("cliente_nome");
        }
        sb.append("*Cliente:* ").append(cliente != null ? cliente : "N/A").append("\n");
        
        // Equipamento
        String equipamento = (String) os.get("equipamento");
        sb.append("*Equipamento:* ").append(equipamento != null ? equipamento : "N/A").append("\n");
        
        // v8.0.12 - Equipamento Detalhado
        String equipamentoDetalhado = (String) os.get("equipamento_detalhado");
        if (equipamentoDetalhado != null && !equipamentoDetalhado.isEmpty()) {
            sb.append("*Descricao:* ").append(equipamentoDetalhado).append("\n");
        }
        
        // Status
        String status = (String) os.get("status");
        sb.append("*Status:* ").append(status != null ? status : "N/A").append("\n\n");
        
        // Defeito Relatado
        String defeito = (String) os.get("defeito_relatado");
        if (defeito != null && !defeito.isEmpty()) {
            sb.append("*Defeito Relatado:*\n").append(defeito).append("\n\n");
        }
        
        // Defeitos Detalhados (v8.0.9)
        String defeitos = (String) os.get("defeitos");
        if (defeitos != null && !defeitos.isEmpty()) {
            sb.append("*Defeitos Detalhados:*\n").append(defeitos).append("\n\n");
        }
        
        // Soluções (v8.0.9)
        String solucoes = (String) os.get("solucoes");
        if (solucoes != null && !solucoes.isEmpty()) {
            sb.append("*Soluções Aplicadas:*\n").append(solucoes).append("\n\n");
        }
        
        // Valores
        double valorServico = os.get("valor_servico") != null ? 
            ((Number) os.get("valor_servico")).doubleValue() : 0;
        double desconto = os.get("desconto_valor") != null ? 
            ((Number) os.get("desconto_valor")).doubleValue() : 0;
        double descontoPercent = os.get("desconto_percentual") != null ? 
            ((Number) os.get("desconto_percentual")).doubleValue() : 0;
        
        double descontoTotal = (descontoPercent > 0) ? (valorServico * descontoPercent / 100.0) : desconto;
        double valorFinal = valorServico - descontoTotal;
        if (valorFinal < 0) valorFinal = 0;
        
        sb.append("*Valores:*\n");
        sb.append("Subtotal: R$ ").append(String.format(Locale.US, "%.2f", valorServico)).append("\n");
        if (descontoTotal > 0) {
            sb.append("Desconto: -R$ ").append(String.format(Locale.US, "%.2f", descontoTotal)).append("\n");
        }
        sb.append("*Total: R$ ").append(String.format(Locale.US, "%.2f", valorFinal)).append("*\n\n");
        
        // Observações
        String obs = (String) os.get("observacao");
        if (obs != null && !obs.isEmpty()) {
            sb.append("*Observações:*\n").append(obs).append("\n\n");
        }
        
        // Usuários responsáveis (v8.0.8)
        String usuarioAbertura = (String) os.get("usuario_abertura_nome");
        if (usuarioAbertura != null && !usuarioAbertura.isEmpty()) {
            sb.append("Aberta por: ").append(usuarioAbertura).append("\n");
        }
        
        String usuarioFechamento = (String) os.get("usuario_fechamento_nome");
        if (usuarioFechamento != null && !usuarioFechamento.isEmpty()) {
            sb.append("Fechada por: ").append(usuarioFechamento).append("\n");
        }
        
        // Data
        sb.append("Data: ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date())).append("\n");
        
        sb.append("\n═══════════════════════════════════════");
        
        return sb.toString();
    }

    /**
     * Envia a Ordem de Serviço via WhatsApp
     * @param context Contexto da aplicação
     * @param os Dados da Ordem de Serviço
     * @param numeroWhatsApp Número do WhatsApp do cliente (com código de país, ex: 5511999999999)
     */
    public static void enviarOSWhatsApp(Context context, Map<String, Object> os, String numeroWhatsApp) {
        try {
            // Formatar mensagem
            String mensagem = formatarOSParaWhatsApp(os);
            
            // Preparar URL para WhatsApp
            String url = "https://wa.me/" + numeroWhatsApp + "?text=" + Uri.encode(mensagem);
            
            // Criar intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            
            // Verificar se WhatsApp está instalado
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Log.d(TAG, "Abrindo WhatsApp com OS #" + os.get("numero"));
            } else {
                Log.w(TAG, "WhatsApp não está instalado");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar OS via WhatsApp", e);
        }
    }

    /**
     * Envia a Ordem de Serviço via WhatsApp sem número (abre seletor de contatos)
     * @param context Contexto da aplicação
     * @param os Dados da Ordem de Serviço
     */
    public static void enviarOSWhatsAppSemNumero(Context context, Map<String, Object> os) {
        try {
            // Formatar mensagem
            String mensagem = formatarOSParaWhatsApp(os);
            
            // Criar intent para compartilhar via WhatsApp
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, mensagem);
            intent.setType("text/plain");
            intent.setPackage("com.whatsapp");
            
            // Verificar se WhatsApp está instalado
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(Intent.createChooser(intent, "Enviar via WhatsApp"));
                Log.d(TAG, "Compartilhando OS #" + os.get("numero") + " via WhatsApp");
            } else {
                Log.w(TAG, "WhatsApp não está instalado");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao compartilhar OS via WhatsApp", e);
        }
    }

    /**
     * Verifica se WhatsApp está instalado no dispositivo
     */
    public static boolean isWhatsAppInstalled(Context context) {
        try {
            Intent intent = new Intent();
            intent.setPackage("com.whatsapp");
            return context.getPackageManager().resolveActivity(intent, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
