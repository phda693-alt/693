package com.pdv.app.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Feedback haptico (vibracao) curto e profissional para reacoes a
 * cliques e validacoes. Compativel com Android 5.0+ (API 21) ate
 * Android 15+, usando o {@link VibratorManager} em API 31+ e o
 * {@link Vibrator} legado nas versoes anteriores.
 *
 * <p>Todas as chamadas sao silenciosas em caso de erro: se o
 * dispositivo nao tiver vibrador ou se a permissao nao estiver
 * concedida, simplesmente nao acontece nada. Nenhum throw e
 * propagado para a UI.</p>
 */
public final class HapticHelper {

    private HapticHelper() { /* utility */ }

    /** Pulso curto e leve (ideal para clique de confirmacao). */
    public static void light(Context ctx) {
        vibrateOnce(ctx, 18L, defaultAmplitude(80));
    }

    /** Pulso medio (ideal para abrir dialogo importante). */
    public static void medium(Context ctx) {
        vibrateOnce(ctx, 35L, defaultAmplitude(150));
    }

    /** Pulso forte (ideal para indicar erro de validacao). */
    public static void error(Context ctx) {
        try {
            Vibrator v = vibrator(ctx);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = {0L, 60L, 40L, 60L};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int[] amplitudes = {0, 200, 0, 200};
                v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1));
            } else {
                //noinspection deprecation
                v.vibrate(pattern, -1);
            }
        } catch (Throwable ignored) { /* sem haptico, sem problema */ }
    }

    /** Pulso duplo (ideal para confirmacao bem sucedida de admin). */
    public static void success(Context ctx) {
        try {
            Vibrator v = vibrator(ctx);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = {0L, 25L, 70L, 25L};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int[] amplitudes = {0, 120, 0, 120};
                v.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1));
            } else {
                //noinspection deprecation
                v.vibrate(pattern, -1);
            }
        } catch (Throwable ignored) { /* sem haptico, sem problema */ }
    }

    private static void vibrateOnce(Context ctx, long ms, int amplitude) {
        try {
            Vibrator v = vibrator(ctx);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude));
            } else {
                //noinspection deprecation
                v.vibrate(ms);
            }
        } catch (Throwable ignored) { /* sem haptico, sem problema */ }
    }

    private static Vibrator vibrator(Context ctx) {
        if (ctx == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx
                        .getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            } else {
                return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int defaultAmplitude(int fallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return VibrationEffect.DEFAULT_AMPLITUDE;
        }
        return fallback;
    }
}
