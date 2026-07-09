package com.pdv.app.permissions;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Classe utilitaria avancada para controle de permissoes nas Activities.
 * 
 * v6.0.0 - Sistema de Permissoes Avancado:
 *   - Controle de visibilidade (VISIBLE/GONE)
 *   - Controle de habilitacao (enabled/disabled com visual feedback)
 *   - Controle combinado (visibilidade + habilitacao)
 *   - Verificacao de caixa aberto para vendas
 *   - Feedback visual profissional para componentes desabilitados
 *   - Suporte a multiplas permissoes (AND/OR)
 */
public class PermissionHelper {
    private static final String TAG = "PermissionHelper";

    // Cores para feedback visual
    private static final float ALPHA_DESABILITADO = 0.35f;
    private static final float ALPHA_HABILITADO = 1.0f;

    // =========================================================================
    // VERIFICACAO BASICA DE PERMISSOES
    // =========================================================================

    /**
     * Verifica se o usuario tem permissao e exibe mensagem de erro se nao tiver.
     */
    public static boolean verificar(Context context, String chave) {
        boolean tem = PermissionManager.getInstance(context).temPermissao(chave);
        if (!tem) {
            mostrarAcessoNegado(context, chave);
        }
        return tem;
    }

    /**
     * Verifica se o usuario tem permissao sem exibir mensagem.
     */
    public static boolean verificarSilencioso(Context context, String chave) {
        return PermissionManager.getInstance(context).temPermissao(chave);
    }

    /**
     * Verifica permissao de acesso a uma tela.
     * Se nao tiver, exibe mensagem e fecha a Activity.
     */
    public static boolean verificarAcesso(Activity activity, String chave) {
        boolean tem = PermissionManager.getInstance(activity).temPermissao(chave);
        if (!tem) {
            mostrarAcessoNegadoEFechar(activity, chave);
        }
        return tem;
    }

    /**
     * Alias para verificarSilencioso.
     */
    public static boolean temPermissao(Context context, String chave) {
        return PermissionManager.getInstance(context).temPermissao(chave);
    }

    /**
     * Verifica se tem TODAS as permissoes especificadas.
     */
    public static boolean temTodasPermissoes(Context context, String... chaves) {
        return PermissionManager.getInstance(context).temTodasPermissoes(chaves);
    }

    /**
     * Verifica se tem ALGUMA das permissoes especificadas.
     */
    public static boolean temAlgumaPermissao(Context context, String... chaves) {
        return PermissionManager.getInstance(context).temAlgumaPermissao(chaves);
    }

    // =========================================================================
    // CONTROLE DE VISIBILIDADE
    // =========================================================================

    /**
     * Controla a visibilidade de um componente baseado na permissao.
     * Se nao tiver permissao, o componente fica GONE (invisivel).
     */
    public static void controlarVisibilidade(Context context, View view, String chave) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temPermissao(chave);
        view.setVisibility(tem ? View.VISIBLE : View.GONE);
    }

    /**
     * Controla a visibilidade baseado em QUALQUER uma das permissoes.
     * Visivel se tiver pelo menos uma das permissoes.
     */
    public static void controlarVisibilidadeOr(Context context, View view, String... chaves) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temAlgumaPermissao(chaves);
        view.setVisibility(tem ? View.VISIBLE : View.GONE);
    }

    /**
     * Controla a visibilidade baseado em TODAS as permissoes.
     * Visivel somente se tiver todas as permissoes.
     */
    public static void controlarVisibilidadeAnd(Context context, View view, String... chaves) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temTodasPermissoes(chaves);
        view.setVisibility(tem ? View.VISIBLE : View.GONE);
    }

    // =========================================================================
    // CONTROLE DE HABILITACAO
    // =========================================================================

    /**
     * Controla a habilitacao de um componente baseado na permissao.
     * Se nao tiver permissao, o componente fica desabilitado com visual feedback.
     */
    public static void controlarHabilitacao(Context context, View view, String chave) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temPermissao(chave);
        aplicarEstadoHabilitacao(view, tem);
    }

    /**
     * Controla a habilitacao baseado em QUALQUER uma das permissoes.
     */
    public static void controlarHabilitacaoOr(Context context, View view, String... chaves) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temAlgumaPermissao(chaves);
        aplicarEstadoHabilitacao(view, tem);
    }

    /**
     * Controla a habilitacao baseado em TODAS as permissoes.
     */
    public static void controlarHabilitacaoAnd(Context context, View view, String... chaves) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temTodasPermissoes(chaves);
        aplicarEstadoHabilitacao(view, tem);
    }

    // =========================================================================
    // CONTROLE COMBINADO (VISIBILIDADE + HABILITACAO)
    // =========================================================================

    /**
     * Controla visibilidade pelo primeiro parametro e habilitacao pelo segundo.
     * Util para botoes que devem aparecer mas ficar desabilitados.
     * 
     * @param context Contexto
     * @param view View a controlar
     * @param chaveVisibilidade Permissao para visibilidade
     * @param chaveHabilitacao Permissao para habilitacao
     */
    public static void controlarVisibilidadeEHabilitacao(Context context, View view,
                                                          String chaveVisibilidade, String chaveHabilitacao) {
        if (view == null) return;
        PermissionManager pm = PermissionManager.getInstance(context);
        boolean visivel = pm.temPermissao(chaveVisibilidade);
        boolean habilitado = pm.temPermissao(chaveHabilitacao);

        view.setVisibility(visivel ? View.VISIBLE : View.GONE);
        if (visivel) {
            aplicarEstadoHabilitacao(view, habilitado);
        }
    }

    // =========================================================================
    // CONTROLE DE BOTAO COM CLICK PROTEGIDO
    // =========================================================================

    /**
     * Configura um botao com verificacao de permissao no click.
     * Se nao tiver permissao, exibe mensagem de acesso negado ao clicar.
     * O botao fica visivel mas desabilitado visualmente.
     * 
     * @param context Contexto
     * @param view Botao a configurar
     * @param chave Permissao necessaria
     * @param acao Acao a executar se tiver permissao
     */
    public static void configurarBotaoProtegido(Context context, View view, String chave, Runnable acao) {
        if (view == null) return;
        boolean tem = PermissionManager.getInstance(context).temPermissao(chave);
        aplicarEstadoHabilitacao(view, tem);

        view.setOnClickListener(v -> {
            if (PermissionManager.getInstance(context).temPermissao(chave)) {
                acao.run();
            } else {
                mostrarAcessoNegado(context, chave);
            }
        });
    }

    // =========================================================================
    // VERIFICACAO DE CAIXA ABERTO
    // =========================================================================

    /**
     * Exibe mensagem de caixa fechado e impede a acao.
     * Deve ser chamado na UI thread.
     */
    public static void mostrarCaixaFechado(Context context) {
        if (context instanceof Activity && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed())) {
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle("\uD83D\uDCB0 Caixa Fechado")
                    .setMessage("Nao e possivel realizar vendas com o caixa fechado.\n\n"
                            + "Para efetuar vendas, e necessario abrir o caixa primeiro.\n\n"
                            + "Acesse o menu Caixa e realize a abertura do caixa.")
                    .setPositiveButton("Entendi", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao exibir dialog de caixa fechado: " + e.getMessage());
            try {
                Toast.makeText(context, "Caixa fechado! Abra o caixa para realizar vendas.", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // APLICACAO DE ESTADO VISUAL
    // =========================================================================

    /**
     * Aplica o estado de habilitacao/desabilitacao com feedback visual profissional.
     * Componentes desabilitados ficam com alpha reduzido e nao-clicaveis.
     */
    public static void aplicarEstadoHabilitacao(View view, boolean habilitado) {
        if (view == null) return;

        view.setEnabled(habilitado);
        view.setAlpha(habilitado ? ALPHA_HABILITADO : ALPHA_DESABILITADO);

        // Para ViewGroups (LinearLayout, etc.), desabilitar filhos tambem
        if (!habilitado && view instanceof ViewGroup) {
            desabilitarFilhosRecursivo((ViewGroup) view);
        } else if (habilitado && view instanceof ViewGroup) {
            habilitarFilhosRecursivo((ViewGroup) view);
        }
    }

    /**
     * Desabilita todos os filhos de um ViewGroup recursivamente.
     */
    private static void desabilitarFilhosRecursivo(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(false);
            if (child instanceof ViewGroup) {
                desabilitarFilhosRecursivo((ViewGroup) child);
            }
        }
    }

    /**
     * Habilita todos os filhos de um ViewGroup recursivamente.
     */
    private static void habilitarFilhosRecursivo(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setEnabled(true);
            if (child instanceof ViewGroup) {
                habilitarFilhosRecursivo((ViewGroup) child);
            }
        }
    }

    // =========================================================================
    // DIALOGS DE FEEDBACK
    // =========================================================================

    /**
     * Exibe dialog de acesso negado com informacoes sobre a permissao.
     */
    public static void mostrarAcessoNegado(Context context, String chave) {
        if (context instanceof Activity && (((Activity) context).isFinishing() || ((Activity) context).isDestroyed())) {
            return;
        }

        String perfilNome = PermissionManager.getInstance(context).getPerfilNome();
        String mensagem = "Voce nao tem permissao para realizar esta acao.\n\n"
                + "Permissao necessaria: " + formatarChave(chave) + "\n"
                + "Seu perfil: " + perfilNome + "\n\n"
                + "Solicite ao administrador do sistema que conceda esta permissao ao seu perfil.";

        try {
            new AlertDialog.Builder(context)
                    .setTitle("\uD83D\uDEAB Acesso Negado")
                    .setMessage(mensagem)
                    .setPositiveButton("Entendi", null)
                    .setCancelable(true)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao exibir dialog de acesso negado: " + e.getMessage());
            try {
                Toast.makeText(context, "Acesso negado: " + formatarChave(chave), Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Exibe dialog de acesso negado e fecha a Activity ao confirmar.
     */
    public static void mostrarAcessoNegadoEFechar(Activity activity, String chave) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        String perfilNome = PermissionManager.getInstance(activity).getPerfilNome();
        String mensagem = "Voce nao tem permissao para acessar esta funcionalidade.\n\n"
                + "Permissao necessaria: " + formatarChave(chave) + "\n"
                + "Seu perfil: " + perfilNome + "\n\n"
                + "Solicite ao administrador do sistema que conceda esta permissao ao seu perfil.";

        try {
            new AlertDialog.Builder(activity)
                    .setTitle("\uD83D\uDEAB Acesso Negado")
                    .setMessage(mensagem)
                    .setPositiveButton("Voltar", (d, w) -> activity.finish())
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao exibir dialog: " + e.getMessage());
            Toast.makeText(activity, "Acesso negado", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    // =========================================================================
    // UTILITARIOS
    // =========================================================================

    /**
     * Formata a chave da permissao para exibicao amigavel.
     * Ex: "vendas.criar" -> "Vendas > Criar"
     */
    public static String formatarChave(String chave) {
        if (chave == null) return "";
        String[] partes = chave.split("\\.");
        if (partes.length == 2) {
            return capitalize(partes[0].replace("_", " ")) + " > " + capitalize(partes[1].replace("_", " "));
        }
        return chave;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
