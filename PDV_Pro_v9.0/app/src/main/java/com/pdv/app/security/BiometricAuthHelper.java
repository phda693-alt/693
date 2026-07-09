package com.pdv.app.security;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;

import java.util.concurrent.Executor;

/**
 * Compatibilidade robusta para autenticacao biometrica sem adicionar novas
 * dependencias ao projeto.
 *
 * v8.0.23.1
 * - Android 10+ agora usa android.hardware.biometrics.BiometricManager, evitando
 *   falso "sem sensor" em aparelhos que nao expõem FingerprintManager.
 * - Android 9 usa BiometricPrompt nativo com validacao por FingerprintManager.
 * - Android 6 a 8.1 usam FingerprintManager legado.
 * - Callbacks protegidos contra retorno duplicado em cancelamentos/erros.
 */
public final class BiometricAuthHelper {
    private BiometricAuthHelper() {}

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    public static String getFingerprintAvailabilityError(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "Este aparelho precisa estar no Android 6.0 ou superior para usar biometria.";
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                BiometricManager manager =
                        (BiometricManager) context.getSystemService(Context.BIOMETRIC_SERVICE);
                if (manager == null) {
                    return "Servico de biometria indisponivel neste aparelho.";
                }

                int status;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    status = manager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    | BiometricManager.Authenticators.BIOMETRIC_WEAK);
                } else {
                    status = manager.canAuthenticate();
                }

                if (status == BiometricManager.BIOMETRIC_SUCCESS) {
                    return null;
                }
                if (status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                    return "Nenhum sensor biometrico foi encontrado neste aparelho.";
                }
                if (status == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE) {
                    return "Sensor biometrico temporariamente indisponivel. Tente novamente.";
                }
                if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                    return "Cadastre pelo menos uma biometria nas configuracoes do Android antes de usar esta funcao.";
                }
                return "Biometria indisponivel neste aparelho. Codigo: " + status;
            }

            FingerprintManager manager =
                    (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
            if (manager == null || !manager.isHardwareDetected()) {
                return "Nenhum sensor de digital foi encontrado neste aparelho.";
            }
            if (!manager.hasEnrolledFingerprints()) {
                return "Cadastre pelo menos uma digital nas configuracoes do Android antes de usar esta funcao.";
            }
            return null;
        } catch (SecurityException e) {
            return "O aplicativo nao tem permissao para usar o sensor biometrico.";
        } catch (Exception e) {
            return "Nao foi possivel verificar o sensor biometrico: " + e.getMessage();
        }
    }

    public static void authenticate(Activity activity, String title, String subtitle, Callback callback) {
        String error = getFingerprintAvailabilityError(activity);
        if (error != null) {
            callback.onError(error);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            authenticateWithBiometricPrompt(activity, title, subtitle, callback);
        } else {
            authenticateWithFingerprintManager(activity, title, subtitle, callback);
        }
    }

    private static void authenticateWithBiometricPrompt(
            Activity activity,
            String title,
            String subtitle,
            Callback callback
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            authenticateWithFingerprintManager(activity, title, subtitle, callback);
            return;
        }

        CancellationSignal signal = new CancellationSignal();
        Executor executor = activity.getMainExecutor();
        final boolean[] finished = {false};

        BiometricPrompt prompt = new BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButton("Cancelar", executor,
                        (DialogInterface dialog, int which) -> {
                            if (finished[0]) return;
                            finished[0] = true;
                            signal.cancel();
                            callback.onError("Autenticacao biometrica cancelada.");
                        })
                .build();

        prompt.authenticate(signal, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                if (finished[0]) return;
                finished[0] = true;
                callback.onSuccess();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                if (finished[0]) return;
                finished[0] = true;
                callback.onError(errString != null && errString.length() > 0
                        ? errString.toString()
                        : "Autenticacao biometrica cancelada.");
            }

            @Override
            public void onAuthenticationFailed() {
                // O prompt nativo permanece aberto e mostra feedback ao usuario.
            }
        });
    }

    @SuppressWarnings({"deprecation", "MissingPermission"})
    private static void authenticateWithFingerprintManager(
            Activity activity,
            String title,
            String subtitle,
            Callback callback
    ) {
        FingerprintManager manager =
                (FingerprintManager) activity.getSystemService(Context.FINGERPRINT_SERVICE);
        if (manager == null) {
            callback.onError("Sensor de digital indisponivel.");
            return;
        }

        CancellationSignal signal = new CancellationSignal();
        final boolean[] finished = {false};
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(subtitle + "\n\nToque no sensor de digital para confirmar.")
                .setNegativeButton("Cancelar", (d, w) -> {
                    if (finished[0]) return;
                    finished[0] = true;
                    signal.cancel();
                    callback.onError("Autenticacao biometrica cancelada.");
                })
                .create();

        dialog.setOnDismissListener(d -> {
            if (!finished[0]) {
                finished[0] = true;
                signal.cancel();
            }
        });
        dialog.show();

        manager.authenticate(null, signal, 0, new FingerprintManager.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                if (finished[0]) return;
                finished[0] = true;
                dialog.dismiss();
                callback.onSuccess();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                if (finished[0]) return;
                finished[0] = true;
                dialog.dismiss();
                callback.onError(errString != null && errString.length() > 0
                        ? errString.toString()
                        : "Falha na autenticacao biometrica.");
            }

            @Override
            public void onAuthenticationFailed() {
                if (!finished[0]) {
                    dialog.setMessage(subtitle + "\n\nDigital nao reconhecida. Tente novamente.");
                }
            }

            @Override
            public void onAuthenticationHelp(int helpCode, CharSequence helpString) {
                if (!finished[0] && helpString != null && helpString.length() > 0) {
                    dialog.setMessage(subtitle + "\n\n" + helpString);
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }
}
