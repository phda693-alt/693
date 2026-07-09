package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Armazena localmente as senhas geradas pelo canhoto de senha.
 *
 * Usa SharedPreferences para funcionar mesmo sem criar tabela nova no banco,
 * mantendo compatibilidade com instalacoes existentes e evitando travamentos
 * na finalizacao da venda.
 */
public final class SenhaChamadoStore {
    private static final String PREFS = "pdv_senhas_chamado_prefs";
    private static final String KEY_LISTA = "lista_senhas";
    private static final String KEY_PROXIMA_SENHA = "proxima_senha_atendimento";
    private static final String KEY_MAPA_VENDA_SENHA = "mapa_venda_senha";
    private static final int LIMITE_REGISTROS = 200;

    private SenhaChamadoStore() {}

    public static class SenhaItem {
        public int senha;
        public int vendaId;
        public String cliente;
        public double total;
        public long criadoEm;
        public long chamadoEm;

        public boolean foiChamado() {
            return chamadoEm > 0;
        }
    }

    public static synchronized void adicionarSenha(Context context, int vendaId, String cliente, double total) {
        if (context == null || vendaId <= 0) return;

        List<SenhaItem> itens = listar(context);

        // Evita duplicar a mesma venda caso o usuario toque duas vezes ou reabra a tela.
        for (SenhaItem item : itens) {
            if (item.vendaId == vendaId) return;
        }

        SenhaItem item = new SenhaItem();
        item.senha = obterOuCriarSenhaDaVenda(context, vendaId);
        item.vendaId = vendaId;
        item.cliente = limpar(cliente);
        if (item.cliente.length() == 0) item.cliente = "Cliente nao informado";
        item.total = total;
        item.criadoEm = System.currentTimeMillis();
        item.chamadoEm = 0;

        itens.add(0, item);
        while (itens.size() > LIMITE_REGISTROS) itens.remove(itens.size() - 1);
        salvar(context, itens);
    }

    /**
     * Retorna a senha de atendimento vinculada a uma venda.
     * Se a venda ainda nao tiver senha, cria uma senha sequencial independente do ID da venda.
     * Isso permite zerar as senhas do canhoto/painel sem interferir no contador de vendas.
     */
    public static synchronized int obterOuCriarSenhaDaVenda(Context context, int vendaId) {
        if (context == null || vendaId <= 0) return vendaId;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mapa = prefs.getString(KEY_MAPA_VENDA_SENHA, "");
        String prefix = vendaId + ":";
        if (mapa != null && !mapa.trim().isEmpty()) {
            String[] partes = mapa.split("\\n");
            for (String linha : partes) {
                if (linha != null && linha.startsWith(prefix)) {
                    int senhaExistente = parseInt(linha.substring(prefix.length()));
                    if (senhaExistente > 0) return senhaExistente;
                }
            }
        }

        int proxima = prefs.getInt(KEY_PROXIMA_SENHA, 1);
        if (proxima <= 0) proxima = 1;
        int senha = proxima;
        int novaProxima = senha + 1;
        if (novaProxima > 999) novaProxima = 1;

        StringBuilder novoMapa = new StringBuilder();
        if (mapa != null && !mapa.trim().isEmpty()) {
            novoMapa.append(mapa.trim()).append('\n');
        }
        novoMapa.append(vendaId).append(':').append(senha);

        prefs.edit()
                .putInt(KEY_PROXIMA_SENHA, novaProxima)
                .putString(KEY_MAPA_VENDA_SENHA, novoMapa.toString())
                .apply();
        return senha;
    }

    public static String formatarSenhaNumero(int senha) {
        if (senha <= 0) senha = 1;
        String s = String.valueOf(senha);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    public static synchronized void zerarAtendimento(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_LISTA)
                .remove(KEY_MAPA_VENDA_SENHA)
                .putInt(KEY_PROXIMA_SENHA, 1)
                .apply();
    }

    public static synchronized List<SenhaItem> listar(Context context) {
        if (context == null) return new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_LISTA, "");
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();

        List<SenhaItem> itens = new ArrayList<>();
        String[] linhas = raw.split("\\n");
        for (String linha : linhas) {
            try {
                if (linha == null || linha.trim().isEmpty()) continue;
                String[] p = linha.split("\\|", -1);
                if (p.length < 6) continue;
                SenhaItem item = new SenhaItem();
                item.senha = parseInt(p[0]);
                item.vendaId = parseInt(p[1]);
                item.cliente = dec(p[2]);
                item.total = parseDouble(p[3]);
                item.criadoEm = parseLong(p[4]);
                item.chamadoEm = parseLong(p[5]);
                if (item.senha > 0) itens.add(item);
            } catch (Exception ignored) {}
        }
        return itens;
    }

    public static synchronized void marcarChamado(Context context, int vendaId) {
        if (context == null || vendaId <= 0) return;
        List<SenhaItem> itens = listar(context);
        long agora = System.currentTimeMillis();
        SenhaItem selecionado = null;
        for (SenhaItem item : itens) {
            if (item.vendaId == vendaId) {
                item.chamadoEm = agora;
                selecionado = item;
                break;
            }
        }
        if (selecionado != null) {
            itens.remove(selecionado);
            itens.add(0, selecionado);
            salvar(context, itens);
        }
    }

    public static synchronized void remover(Context context, int vendaId) {
        if (context == null || vendaId <= 0) return;
        List<SenhaItem> itens = listar(context);
        for (int i = itens.size() - 1; i >= 0; i--) {
            if (itens.get(i).vendaId == vendaId) itens.remove(i);
        }
        salvar(context, itens);
    }

    public static synchronized void limparTudo(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LISTA).apply();
    }

    public static String formatarHora(long millis) {
        if (millis <= 0) return "--:--";
        return new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(new Date(millis));
    }

    private static void salvar(Context context, List<SenhaItem> itens) {
        if (itens == null) itens = Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        for (SenhaItem item : itens) {
            if (item == null || item.senha <= 0) continue;
            sb.append(item.senha).append('|')
                    .append(item.vendaId).append('|')
                    .append(enc(item.cliente)).append('|')
                    .append(item.total).append('|')
                    .append(item.criadoEm).append('|')
                    .append(item.chamadoEm).append('\n');
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LISTA, sb.toString()).apply();
    }

    private static String limpar(String s) {
        if (s == null) return "";
        return s.replace("\n", " ").replace("\r", " ").replace("|", "-").trim();
    }

    private static String enc(String s) {
        return limpar(s).replace("%", "%25").replace("\n", "%0A").replace("|", "%7C");
    }

    private static String dec(String s) {
        if (s == null) return "";
        return s.replace("%7C", "|").replace("%0A", "\n").replace("%25", "%");
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }
}
