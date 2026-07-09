package com.pdv.app.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.pdv.app.utils.AnimUtils;

/**
 * PDV Pro v9.0 - Helper de notificações in-app modernas.
 *
 * Exibe banners de notificação no topo da tela com animação suave.
 * Substitui Toasts simples por notificações visuais mais profissionais.
 */
public class NotificationHelper {

    public enum Type {
        SUCCESS, ERROR, WARNING, INFO
    }

    private static final int DURATION_SHORT = 2500;
    private static final int DURATION_LONG = 4000;

    /**
     * Exibe uma notificação in-app no topo da Activity.
     */
    public static void show(Activity activity, String message, Type type) {
        show(activity, message, type, DURATION_SHORT);
    }

    public static void show(Activity activity, String message, Type type, int durationMs) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Criar view de notificação
                FrameLayout container = new FrameLayout(activity);
                container.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView tv = new TextView(activity);
                int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
                tv.setPadding(padding, padding, padding, padding);
                tv.setTextSize(14f);
                tv.setTextColor(Color.WHITE);
                tv.setGravity(Gravity.CENTER_VERTICAL);
                tv.setText(getIcon(type) + "  " + message);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);

                // Cor de fundo baseada no tipo
                int bgColor;
                switch (type) {
                    case SUCCESS: bgColor = Color.parseColor("#1A00D68F"); break;
                    case ERROR:   bgColor = Color.parseColor("#1AFF4D6A"); break;
                    case WARNING: bgColor = Color.parseColor("#1AFFB300"); break;
                    default:      bgColor = Color.parseColor("#1A00B0FF"); break;
                }

                int strokeColor;
                switch (type) {
                    case SUCCESS: strokeColor = Color.parseColor("#00D68F"); break;
                    case ERROR:   strokeColor = Color.parseColor("#FF4D6A"); break;
                    case WARNING: strokeColor = Color.parseColor("#FFB300"); break;
                    default:      strokeColor = Color.parseColor("#00B0FF"); break;
                }

                tv.setBackgroundColor(bgColor);
                tv.setTextColor(strokeColor);

                FrameLayout.LayoutParams tvParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                int margin = (int) (8 * activity.getResources().getDisplayMetrics().density);
                tvParams.setMargins(margin, margin, margin, 0);
                container.addView(tv, tvParams);

                // Adicionar ao root
                ViewGroup rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
                rootView.addView(container);

                // Animação de entrada
                AnimUtils.slideDown(tv, 0);

                // Remover após duração
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        AnimUtils.fadeOut(container, 0);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                rootView.removeView(container);
                            } catch (Exception ignored) {}
                        }, 300);
                    } catch (Exception ignored) {}
                }, durationMs);

            } catch (Exception e) {
                // Fallback silencioso
            }
        });
    }

    private static String getIcon(Type type) {
        switch (type) {
            case SUCCESS: return "✓";
            case ERROR:   return "✕";
            case WARNING: return "⚠";
            default:      return "ℹ";
        }
    }
}
