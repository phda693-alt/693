package com.phda.phserver;

import android.content.Context;

import com.pdv.app.server.MariaDbServerManager;

/**
 * Wrapper para configuracao do servidor MariaDB integrado.
 *
 * <p>Permite ler/gravar a preference "address" (interface de bind do servidor)
 * e solicitar restart do servico MariaDB interno.</p>
 */
public final class ServerConfigHelper {

    private static final String PREFS = "embedded_mariadb_config";
    private static final String KEY_ADDRESS = "address";

    private ServerConfigHelper() { /* utility */ }

    /**
     * Le o valor atual da preference "address" do servidor interno.
     * @return valor atual ou null se nao acessivel
     */
    public static String getAddress(Context ctx) {
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ADDRESS, "all");
        } catch (Exception e) {
            // Configuracao nao acessivel
        }
        return null;
    }

    /**
     * Define o valor da preference "address" do servidor interno.
     * @param value novo valor (ex: "all" para 0.0.0.0)
     * @return numero de linhas afetadas (1 = sucesso, 0 = falha)
     */
    public static int setAddress(Context ctx, String value) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_ADDRESS, value)
                    .apply();
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Solicita que o PDV Pro mantenha o servico MariaDB interno ativo.
     */
    public static void requestRestart(Context ctx) {
        try {
            MariaDbServerManager.tryAutoStart(ctx, false);
        } catch (Exception e) {
            // Servico indisponivel
        }
    }
}
