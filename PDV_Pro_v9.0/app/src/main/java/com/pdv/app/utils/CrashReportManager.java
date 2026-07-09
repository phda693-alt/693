package com.pdv.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.pdv.app.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro local de falhas fatais para suporte e diagnostico.
 * Mantem apenas o ultimo crash em armazenamento interno do app.
 */
public final class CrashReportManager {
    private static final String TAG = "CrashReportManager";
    private static final String DIR_NAME = "crash_reports";
    private static final String LAST_CRASH_FILE = "last_crash.txt";

    private CrashReportManager() {
    }

    public static void record(Context context, Thread thread, Throwable throwable) {
        if (context == null || throwable == null) return;

        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Nao foi possivel criar diretorio de crash reports");
            return;
        }

        File file = new File(dir, LAST_CRASH_FILE);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            writer.println("PDV Pro - Crash Report");
            writer.println("Data: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            writer.println("Versao: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
            writer.println("Android SDK: " + Build.VERSION.SDK_INT);
            writer.println("Dispositivo: " + Build.MANUFACTURER + " " + Build.MODEL);
            writer.println("Thread: " + (thread != null ? thread.getName() : "desconhecida"));
            writer.println("Erro: " + throwable.getClass().getName() + ": " + safeMessage(throwable));
            writer.println();
            writer.println(stackTraceToString(throwable));
        } catch (Exception e) {
            Log.w(TAG, "Falha ao salvar crash report", e);
        }
    }

    public static String getLastCrashSummary(Context context) {
        if (context == null) return "Nenhuma falha fatal registrada.";

        File file = new File(new File(context.getFilesDir(), DIR_NAME), LAST_CRASH_FILE);
        if (!file.exists() || file.length() == 0) {
            return "Nenhuma falha fatal registrada.";
        }

        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(file.lastModified()));
        return "Ultima falha registrada em " + date + "\nArquivo interno: " + file.getName()
                + "\nTamanho: " + formatBytes(file.length());
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    private static String stackTraceToString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
