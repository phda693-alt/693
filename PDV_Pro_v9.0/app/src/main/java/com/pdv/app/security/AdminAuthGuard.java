package com.pdv.app.security;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Guarda de autenticacao administrativa do PDV Pro.
 *
 * <p>Centraliza a logica de validacao de senha de administrador para
 * acoes sensiveis na tela de login (Inserir Nova Licenca, Atualizar
 * Tabelas e Colunas do Banco) e em qualquer outra tela que precise
 * pedir confirmacao administrativa.</p>
 *
 * <p>Recursos profissionais:</p>
 * <ul>
 *   <li>Senha mestre {@value #ADMIN_PASSWORD} validada em tempo
 *       constante para mitigar timing attacks.</li>
 *   <li>Lockout progressivo apos 3 tentativas erradas (10s -> 30s -> 60s).</li>
 *   <li>Trilha de auditoria persistente em SharedPreferences.</li>
 *   <li>Compatibilidade total com codigo legado: o metodo
 *       {@link #isCorrect(String)} aceita exatamente a mesma string
 *       que ja era esperada antes (4872), nao quebrando nada que
 *       dependa dessa senha.</li>
 * </ul>
 *
 * <p>Esta classe e thread-safe para o uso comum (chamadas a partir
 * da UI thread). Todas as operacoes de IO sao sincronas e rapidas
 * pois usam SharedPreferences.</p>
 */
public final class AdminAuthGuard {

    /**
     * Senha mestre administrativa (mantida identica ao valor historico
     * para preservar a integridade das funcionalidades existentes).
     */
    public static final String ADMIN_PASSWORD = "4872";

    /** Numero maximo de tentativas erradas antes do primeiro lockout. */
    public static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = 3;

    /** Duracoes progressivas (em milissegundos) de lockout: 10s, 30s, 60s. */
    private static final long[] LOCKOUT_DURATIONS_MS = {
            10_000L, 30_000L, 60_000L
    };

    private static final String PREFS = "pdv_admin_auth_guard";
    private static final String KEY_FAIL_COUNT = "fail_count";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    private static final String KEY_LOCKOUT_LEVEL = "lockout_level";
    private static final String KEY_LAST_SUCCESS = "last_success_ts";
    private static final String KEY_LAST_FAILURE = "last_failure_ts";
    private static final String KEY_AUDIT_LOG = "audit_log";

    private AdminAuthGuard() { /* utility class */ }

    /**
     * Compara a senha digitada com a senha mestre em tempo constante
     * (mesma quantidade de comparacoes independente de quao parecida
     * a entrada eh com a senha real).
     *
     * @param input senha digitada pelo usuario (pode ser null)
     * @return true se for igual a {@link #ADMIN_PASSWORD}
     */
    public static boolean isCorrect(String input) {
        if (input == null) return false;
        return constantTimeEquals(input, ADMIN_PASSWORD);
    }

    /**
     * Tempo restante (em milissegundos) ate que o usuario possa tentar
     * novamente. Retorna 0 se nao houver lockout ativo.
     */
    public static long lockoutRemainingMs(Context ctx) {
        if (ctx == null) return 0L;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L);
        long now = System.currentTimeMillis();
        return Math.max(0L, until - now);
    }

    /**
     * Indica se o guarda esta atualmente em lockout.
     */
    public static boolean isLockedOut(Context ctx) {
        return lockoutRemainingMs(ctx) > 0L;
    }

    /**
     * Tenta autenticar a senha digitada. Atualiza contadores de
     * tentativa, ativa lockout progressivo se necessario e registra
     * trilha de auditoria.
     *
     * @return resultado da autenticacao
     */
    public static AuthResult authenticate(Context ctx, String input, String acao) {
        if (ctx == null) {
            // Sem contexto, faz a verificacao stateless apenas.
            return isCorrect(input)
                    ? AuthResult.success(0)
                    : AuthResult.failure(1, 0L);
        }

        long remaining = lockoutRemainingMs(ctx);
        if (remaining > 0L) {
            return AuthResult.locked(remaining);
        }

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        long now = System.currentTimeMillis();

        if (isCorrect(input)) {
            ed.putInt(KEY_FAIL_COUNT, 0)
              .putLong(KEY_LOCKOUT_UNTIL, 0L)
              .putInt(KEY_LOCKOUT_LEVEL, 0)
              .putLong(KEY_LAST_SUCCESS, now)
              .apply();
            appendAudit(ctx, "OK   | " + safe(acao));
            return AuthResult.success(0);
        }

        int fails = prefs.getInt(KEY_FAIL_COUNT, 0) + 1;
        ed.putInt(KEY_FAIL_COUNT, fails)
          .putLong(KEY_LAST_FAILURE, now);

        long lockMs = 0L;
        if (fails >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            int level = prefs.getInt(KEY_LOCKOUT_LEVEL, 0);
            int idx = Math.min(level, LOCKOUT_DURATIONS_MS.length - 1);
            lockMs = LOCKOUT_DURATIONS_MS[idx];
            ed.putLong(KEY_LOCKOUT_UNTIL, now + lockMs)
              .putInt(KEY_LOCKOUT_LEVEL, level + 1)
              .putInt(KEY_FAIL_COUNT, 0); // reseta contador apos disparar lockout
        }
        ed.apply();
        appendAudit(ctx, "FAIL | " + safe(acao) + " | tentativas=" + fails
                + (lockMs > 0 ? " | lockout=" + (lockMs / 1000) + "s" : ""));

        return lockMs > 0L
                ? AuthResult.locked(lockMs)
                : AuthResult.failure(fails, 0L);
    }

    /**
     * Limpa estado de lockout (apenas para uso administrativo / reset
     * manual). Nao limpa a trilha de auditoria.
     */
    public static void resetLockout(Context ctx) {
        if (ctx == null) return;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_FAIL_COUNT, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .putInt(KEY_LOCKOUT_LEVEL, 0)
                .apply();
        appendAudit(ctx, "RESET| lockout limpo manualmente");
    }

    /**
     * Le os ultimos eventos de auditoria (mais recentes primeiro).
     * Limitado a {@code maxLines} linhas.
     */
    public static String readAuditLog(Context ctx, int maxLines) {
        if (ctx == null) return "";
        String full = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AUDIT_LOG, "");
        if (full.isEmpty()) return "";
        String[] lines = full.split("\n");
        int from = Math.max(0, lines.length - maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - 1; i >= from; i--) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Calcula um hash SHA-256 hex (em letras minusculas) de qualquer
     * string. Util para comparar senhas armazenadas em hash sem
     * expor o valor original em logs ou em telas administrativas.
     */
    public static String sha256Hex(String value) {
        if (value == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes();
        byte[] bb = b.getBytes();
        int diff = ab.length ^ bb.length;
        int len = Math.min(ab.length, bb.length);
        for (int i = 0; i < len; i++) {
            diff |= (ab[i] ^ bb[i]) & 0xFF;
        }
        return diff == 0;
    }

    private static void appendAudit(Context ctx, String entry) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previous = prefs.getString(KEY_AUDIT_LOG, "");
            String stamp = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.SHORT,
                    java.text.DateFormat.MEDIUM,
                    Locale.getDefault()).format(new java.util.Date());
            String line = stamp + " | " + entry;
            // Mantem ate 200 linhas para nao crescer indefinidamente
            String combined = previous.isEmpty() ? line : previous + "\n" + line;
            String[] lines = combined.split("\n");
            if (lines.length > 200) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 200; i < lines.length; i++) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(lines[i]);
                }
                combined = sb.toString();
            }
            prefs.edit().putString(KEY_AUDIT_LOG, combined).apply();
        } catch (Exception ignored) { /* auditoria nao deve quebrar fluxo */ }
    }

    private static String safe(String s) {
        if (s == null) return "?";
        return s.replace('\n', ' ').replace('|', '/').trim();
    }

    /**
     * Resultado imutavel de uma tentativa de autenticacao administrativa.
     */
    public static final class AuthResult {
        public final boolean ok;
        public final boolean lockedOut;
        public final int failCount;
        public final long lockoutMs;

        private AuthResult(boolean ok, boolean lockedOut, int failCount, long lockoutMs) {
            this.ok = ok;
            this.lockedOut = lockedOut;
            this.failCount = failCount;
            this.lockoutMs = lockoutMs;
        }

        static AuthResult success(int failCount) {
            return new AuthResult(true, false, failCount, 0L);
        }

        static AuthResult failure(int failCount, long lockoutMs) {
            return new AuthResult(false, false, failCount, lockoutMs);
        }

        static AuthResult locked(long lockoutMs) {
            return new AuthResult(false, true, 0, lockoutMs);
        }
    }
}
