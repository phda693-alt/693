package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferencias persistentes das taxas de servico aplicadas no fechamento. */
public final class TaxaServicoPreferences {
    private static final String PREFS = "configuracoes_taxa_servico";
    private static final String TAXA_MESAS = "cobrar_taxa_mesas";
    private static final String TAXA_COMANDAS = "cobrar_taxa_comandas";
    private static final String TAXA_ARMARIOS = "cobrar_taxa_armarios";
    public static final double PERCENTUAL = 10.0;

    private TaxaServicoPreferences() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean cobrarMesas(Context context) {
        return prefs(context).getBoolean(TAXA_MESAS, false);
    }

    public static boolean cobrarComandas(Context context) {
        return prefs(context).getBoolean(TAXA_COMANDAS, false);
    }

    public static boolean cobrarArmarios(Context context) {
        return prefs(context).getBoolean(TAXA_ARMARIOS, false);
    }

    public static void salvar(Context context, boolean mesas, boolean comandas, boolean armarios) {
        prefs(context).edit()
                .putBoolean(TAXA_MESAS, mesas)
                .putBoolean(TAXA_COMANDAS, comandas)
                .putBoolean(TAXA_ARMARIOS, armarios)
                .apply();
    }

    public static double calcularTaxa(double subtotal) {
        return Math.round(subtotal * PERCENTUAL) / 100.0;
    }
}
