package com.pdv.app.ui;

import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.CycleInterpolator;

/**
 * Animacao de "shake" (tremor horizontal) usada para indicar erro
 * em campos de senha ou login. Reutilizavel em qualquer EditText
 * ou container.
 *
 * <p>Implementacao puramente Android nativa, sem dependencias
 * externas. Compativel com Android 5.0+ ate Android 15+.</p>
 */
public final class ShakeAnimator {

    private ShakeAnimator() { /* utility */ }

    /** Shake padrao curto (ideal para erro de senha). */
    public static void shake(View view) {
        shake(view, 16f, 4, 380L);
    }

    /** Shake intenso (ideal para lockout). */
    public static void shakeStrong(View view) {
        shake(view, 24f, 5, 520L);
    }

    /**
     * @param view      view a animar
     * @param amplitude deslocamento maximo em pixels
     * @param cycles    numero de ciclos de tremor
     * @param durationMs duracao total da animacao
     */
    public static void shake(View view, float amplitude, int cycles, long durationMs) {
        if (view == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "translationX",
                0f, amplitude, -amplitude, amplitude * 0.6f, -amplitude * 0.6f, 0f);
        anim.setDuration(durationMs);
        anim.setInterpolator(new CycleInterpolator(cycles));
        anim.start();
    }
}
