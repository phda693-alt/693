package com.pdv.app.utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import com.pdv.app.R;

/**
 * UiFx - Utilitario de efeitos visuais futuristas do PDV Pro.
 *
 * v9.1.0 - Recurso novo e 100% aditivo: nao altera nenhuma logica existente.
 *
 * Centraliza animacoes reutilizaveis (entrada escalonada de grades, fade/slide,
 * pulso de brilho) para que telas possam ganhar um acabamento animado e elegante
 * sem duplicar codigo. Todos os metodos sao defensivos: se a view for nula ou a
 * animacao indisponivel, simplesmente nao fazem nada (nunca quebram a tela).
 */
public final class UiFx {

    private UiFx() {}

    /**
     * Aplica entrada escalonada (stagger) aos filhos de um container, como as
     * grades de botoes do dashboard. Cada item surge com leve atraso e escala,
     * criando o efeito animado/futurista solicitado.
     */
    public static void staggerChildren(ViewGroup container) {
        if (container == null) return;
        try {
            LayoutAnimationController controller =
                    AnimationUtils.loadLayoutAnimation(container.getContext(), R.anim.layout_grid_stagger);
            container.setLayoutAnimation(controller);
            container.scheduleLayoutAnimation();
        } catch (Throwable ignored) {
            // Efeito puramente cosmetico - nunca deve impactar a operacao.
        }
    }

    /** Faz a view surgir com fade + deslize para cima. Seguro contra nulos. */
    public static void fadeSlideUp(View view) {
        if (view == null) return;
        try {
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.fade_slide_up));
        } catch (Throwable ignored) {
        }
    }

    /** Aplica um pulso de respiracao continuo (ideal para badges/alertas). */
    public static void breathe(View view) {
        if (view == null) return;
        try {
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.breathe));
        } catch (Throwable ignored) {
        }
    }

    /** Aplica um pulso neon continuo (ideal para destacar elementos). */
    public static void neonPulse(View view) {
        if (view == null) return;
        try {
            view.startAnimation(AnimationUtils.loadAnimation(view.getContext(), R.anim.neon_pulse));
        } catch (Throwable ignored) {
        }
    }
}
