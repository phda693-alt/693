package com.pdv.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * PDV Pro v9.0 - Utilitários de animação avançados.
 *
 * Animações fluidas, modernas e de alta performance para toda a interface.
 * Todas as animações são otimizadas para não bloquear a UI thread.
 */
public class AnimUtils {

    // === FADE ===

    public static void fadeIn(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(1f)
                .setDuration(280)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    public static void fadeOut(View view, long delay) {
        if (view == null) return;
        view.animate()
                .alpha(0f)
                .setDuration(220)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    public static void fadeInFast(View view) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(150).start();
    }

    // === SLIDE ===

    public static void slideUp(View view, long delay) {
        if (view == null) return;
        view.setTranslationY(60f);
        view.setAlpha(0f);
        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(320)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    public static void slideDown(View view, long delay) {
        if (view == null) return;
        view.setTranslationY(-60f);
        view.setAlpha(0f);
        view.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(320)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    public static void slideInFromRight(View view, long delay) {
        if (view == null) return;
        view.setTranslationX(120f);
        view.setAlpha(0f);
        view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    public static void slideInFromLeft(View view, long delay) {
        if (view == null) return;
        view.setTranslationX(-120f);
        view.setAlpha(0f);
        view.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    // === SCALE ===

    public static void scaleIn(View view, long delay) {
        if (view == null) return;
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(380)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(1.8f))
                .start();
    }

    public static void scaleInFast(View view, long delay) {
        if (view == null) return;
        view.setScaleX(0.7f);
        view.setScaleY(0.7f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(250)
                .setStartDelay(delay)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
    }

    public static void bounceIn(View view, long delay) {
        if (view == null) return;
        view.setScaleX(0.2f);
        view.setScaleY(0.2f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(450)
                .setStartDelay(delay)
                .setInterpolator(new BounceInterpolator())
                .start();
    }

    // === PULSE ===

    public static void pulseAnimation(View view) {
        if (view == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.08f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(500);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    public static void pulseRepeat(View view, int repeatCount) {
        if (view == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f);
        scaleX.setRepeatCount(repeatCount);
        scaleY.setRepeatCount(repeatCount);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(600);
        set.start();
    }

    public static void breatheAnimation(View view) {
        if (view == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "alpha", 0.5f, 1f);
        anim.setDuration(1200);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    // === SHAKE (para erros) ===

    public static void shakeAnimation(View view) {
        if (view == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "translationX",
                0f, -16f, 16f, -12f, 12f, -8f, 8f, -4f, 4f, 0f);
        anim.setDuration(500);
        anim.setInterpolator(new LinearInterpolator());
        anim.start();
    }

    // === EXPAND / COLLAPSE ===

    public static void expandView(View view) {
        if (view == null) return;
        view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int targetHeight = view.getMeasuredHeight();
        view.getLayoutParams().height = 0;
        view.setVisibility(View.VISIBLE);
        ValueAnimator anim = ValueAnimator.ofInt(0, targetHeight);
        anim.addUpdateListener(animation -> {
            view.getLayoutParams().height = (int) animation.getAnimatedValue();
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        });
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    public static void collapseView(View view) {
        if (view == null) return;
        int initialHeight = view.getMeasuredHeight();
        ValueAnimator anim = ValueAnimator.ofInt(initialHeight, 0);
        anim.addUpdateListener(animation -> {
            view.getLayoutParams().height = (int) animation.getAnimatedValue();
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        });
        anim.setDuration(250);
        anim.setInterpolator(new AccelerateInterpolator());
        anim.start();
    }

    // === COLOR TRANSITION ===

    public static void colorTransition(View view, int fromColor, int toColor, long duration) {
        if (view == null) return;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), fromColor, toColor);
        anim.setDuration(duration);
        anim.addUpdateListener(animator -> view.setBackgroundColor((int) animator.getAnimatedValue()));
        anim.start();
    }

    // === MULTIPLE ITEMS ===

    public static void animateItems(View... views) {
        for (int i = 0; i < views.length; i++) {
            slideUp(views[i], i * 55L);
        }
    }

    public static void animateItemsFromRight(View... views) {
        for (int i = 0; i < views.length; i++) {
            slideInFromRight(views[i], i * 50L);
        }
    }

    public static void animateItemsFade(View... views) {
        for (int i = 0; i < views.length; i++) {
            fadeIn(views[i], i * 60L);
        }
    }

    // === ROTATE ===

    public static void rotateAnimation(View view, float degrees, long duration) {
        if (view == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "rotation", 0f, degrees);
        anim.setDuration(duration);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    public static void rotateInfinite(View view) {
        if (view == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        anim.setDuration(1000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.start();
    }

    // === UTILITY ===

    public static void cancelAnimations(View view) {
        if (view != null) {
            view.animate().cancel();
            view.clearAnimation();
        }
    }

    public static void resetView(View view) {
        if (view == null) return;
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setRotation(0f);
    }
}
