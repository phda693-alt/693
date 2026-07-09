package com.pdv.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.security.AdminAuthGuard;

/**
 * Dialogo profissional para solicitar a senha de administrador
 * antes de executar acoes sensiveis (Inserir Nova Licenca,
 * Atualizar Tabelas/Colunas do Banco, etc).
 *
 * <p>Caracteristicas:</p>
 * <ul>
 *   <li>Layout dinamico construido em codigo (sem XML adicional para
 *       manter compatibilidade total com o projeto existente).</li>
 *   <li>Tema dark/neon alinhado ao restante do PDV Pro.</li>
 *   <li>Animacao de shake + feedback haptico em caso de erro.</li>
 *   <li>Lockout progressivo apos tentativas erradas, com contador
 *       regressivo na tela.</li>
 *   <li>Trilha de auditoria via {@link AdminAuthGuard}.</li>
 *   <li>API simples e retrocompativel com o codigo legado:
 *       {@link #show(Activity, String, String, Runnable)}.</li>
 * </ul>
 */
public final class AdminPasswordDialog {

    private AdminPasswordDialog() { /* factory */ }

    /**
     * Exibe o dialogo profissional. Se a senha digitada for igual
     * a {@link AdminAuthGuard#ADMIN_PASSWORD} (4872), executa a
     * acao informada. Em caso de erro, mantem o dialogo aberto e
     * fornece feedback visual e haptico.
     *
     * @param activity activity hospedeira (nao pode ser null)
     * @param titulo   titulo do dialogo
     * @param mensagem mensagem explicativa
     * @param acao     Runnable a ser executado apos validacao OK
     */
    public static void show(final Activity activity,
                            final String titulo,
                            final String mensagem,
                            final Runnable acao) {
        if (activity == null) return;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        final Context ctx = activity;

        // ------------------------------------------------------------------
        // Container raiz (vertical) com padding generoso
        // ------------------------------------------------------------------
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        root.setPadding(pad, dp(ctx, 12), pad, dp(ctx, 4));

        // ------------------------------------------------------------------
        // Mensagem (subtitulo)
        // ------------------------------------------------------------------
        TextView tvMsg = new TextView(ctx);
        tvMsg.setText(mensagem != null ? mensagem :
                "Esta operacao requer senha de administrador.\nDigite a senha para continuar:");
        tvMsg.setTextColor(0xFFB0BEC5);
        tvMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tvMsg.setLineSpacing(0f, 1.2f);
        root.addView(tvMsg, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(ctx, 16)));

        // ------------------------------------------------------------------
        // Campo de senha (numerico oculto) com fundo arredondado
        // ------------------------------------------------------------------
        final EditText etSenha = new EditText(ctx);
        etSenha.setHint("Senha de administrador");
        etSenha.setHintTextColor(0xFF607D8B);
        etSenha.setTextColor(0xFFFFFFFF);
        etSenha.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        etSenha.setLetterSpacing(0.25f);
        etSenha.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etSenha.setGravity(Gravity.CENTER);
        etSenha.setSingleLine(true);
        etSenha.setBackground(roundedField());
        int innerPad = dp(ctx, 14);
        etSenha.setPadding(innerPad, innerPad, innerPad, innerPad);
        root.addView(etSenha, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(ctx, 8)));

        // ------------------------------------------------------------------
        // Linha de status (mensagens de erro / lockout)
        // ------------------------------------------------------------------
        final TextView tvStatus = new TextView(ctx);
        tvStatus.setText("");
        tvStatus.setTextColor(0xFFFF5252);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tvStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvStatus.setVisibility(View.GONE);
        root.addView(tvStatus, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, dp(ctx, 4), 0, dp(ctx, 4)));

        // ------------------------------------------------------------------
        // Construcao do AlertDialog
        // ------------------------------------------------------------------
        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(titulo != null ? titulo : "Confirmacao Administrativa")
                .setView(root)
                .setCancelable(true)
                .setPositiveButton("Confirmar", null) // sobrescreveremos
                .setNegativeButton("Cancelar", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            etSenha.requestFocus();

            // Bloqueia botao Confirmar enquanto estiver em lockout
            final Button btnOk = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            applyLockoutIfActive(ctx, dialog, btnOk, etSenha, tvStatus);

            btnOk.setOnClickListener(view -> {
                String digitada = etSenha.getText() != null
                        ? etSenha.getText().toString().trim() : "";
                if (TextUtils.isEmpty(digitada)) {
                    setError(tvStatus, "Digite a senha de administrador.");
                    ShakeAnimator.shake(etSenha);
                    HapticHelper.error(ctx);
                    return;
                }

                AdminAuthGuard.AuthResult result =
                        AdminAuthGuard.authenticate(ctx, digitada,
                                titulo != null ? titulo : "admin");

                if (result.ok) {
                    HapticHelper.success(ctx);
                    dialog.dismiss();
                    if (acao != null) {
                        // Roda na UI thread no proximo tick para garantir
                        // que o dialogo ja foi descartado antes da nova tela.
                        new Handler(Looper.getMainLooper()).post(acao);
                    }
                    return;
                }

                if (result.lockedOut) {
                    HapticHelper.error(ctx);
                    ShakeAnimator.shakeStrong(etSenha);
                    etSenha.setText("");
                    startLockoutCountdown(ctx, dialog, btnOk, etSenha,
                            tvStatus, result.lockoutMs);
                } else {
                    HapticHelper.error(ctx);
                    ShakeAnimator.shake(etSenha);
                    etSenha.setText("");
                    etSenha.requestFocus();
                    int restantes = Math.max(0,
                            AdminAuthGuard.MAX_ATTEMPTS_BEFORE_LOCKOUT - result.failCount);
                    setError(tvStatus,
                            "Senha incorreta. Tentativas restantes: " + restantes);
                }
            });
        });

        try {
            dialog.show();
        } catch (Throwable ignored) { /* activity nao mais valida */ }
    }

    // ----------------------------------------------------------------------
    // Helpers internos
    // ----------------------------------------------------------------------

    private static void applyLockoutIfActive(Context ctx,
                                             AlertDialog dialog,
                                             Button btnOk,
                                             EditText etSenha,
                                             TextView tvStatus) {
        long remaining = AdminAuthGuard.lockoutRemainingMs(ctx);
        if (remaining > 0L) {
            startLockoutCountdown(ctx, dialog, btnOk, etSenha, tvStatus, remaining);
        }
    }

    private static void startLockoutCountdown(final Context ctx,
                                              final AlertDialog dialog,
                                              final Button btnOk,
                                              final EditText etSenha,
                                              final TextView tvStatus,
                                              final long durationMs) {
        btnOk.setEnabled(false);
        etSenha.setEnabled(false);
        tvStatus.setTextColor(0xFFFFD740); // amarelo aviso
        tvStatus.setVisibility(View.VISIBLE);

        new CountDownTimer(durationMs, 1000L) {
            @Override
            public void onTick(long ms) {
                long s = (ms + 999L) / 1000L;
                tvStatus.setText("Bloqueado. Aguarde " + s + "s para tentar novamente.");
            }

            @Override
            public void onFinish() {
                btnOk.setEnabled(true);
                etSenha.setEnabled(true);
                tvStatus.setVisibility(View.GONE);
                tvStatus.setTextColor(0xFFFF5252);
                etSenha.requestFocus();
            }
        }.start();
    }

    private static void setError(TextView tvStatus, String msg) {
        tvStatus.setTextColor(0xFFFF5252);
        tvStatus.setText(msg);
        tvStatus.setVisibility(View.VISIBLE);
    }

    private static GradientDrawable roundedField() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2247);
        bg.setStroke(2, Color.parseColor("#00BCD4"));
        bg.setCornerRadius(24f);
        return bg;
    }

    private static int dp(Context ctx, int value) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static LinearLayout.LayoutParams lp(int w, int h,
                                                int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    @SuppressWarnings("unused")
    private static ViewGroup.LayoutParams full() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
