package com.pdv.app.dashboard;

import android.content.Context;
import android.util.Log;
import com.pdv.app.database.DatabaseHelper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PDV Pro v9.0 - Gerenciador de estatísticas do dashboard em tempo real.
 *
 * Fornece dados rápidos para o painel principal:
 * - Vendas do dia
 * - Caixa aberto/fechado
 * - Mesas ocupadas
 * - Estoque baixo
 * - Contas a vencer
 * - Pedidos pendentes
 */
public class DashboardStatsManager {

    private static final String TAG = "DashboardStatsManager";

    public interface StatsCallback {
        void onStatsLoaded(DashboardStats stats);
        void onError(String message);
    }

    public static class DashboardStats {
        public double vendasHoje = 0;
        public int qtdVendasHoje = 0;
        public boolean caixaAberto = false;
        public double saldoCaixa = 0;
        public int mesasOcupadas = 0;
        public int mesasTotal = 0;
        public int produtosEstoqueBaixo = 0;
        public int contasVencer7Dias = 0;
        public int pedidosPendentes = 0;
        public int comandasAbertas = 0;
        public int entregasPendentes = 0;
        public double ticketMedioHoje = 0;
        public String dataHora = "";
        public boolean loaded = false;
    }

    public static void loadStats(Context context, StatsCallback callback) {
        new Thread(() -> {
            DashboardStats stats = new DashboardStats();
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(context);
                Connection conn = db.getConnection();
                String hoje = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                Statement stmt = conn.createStatement();

                // Vendas do dia
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as qtd, COALESCE(SUM(total_liquido),0) as total " +
                        "FROM vendas WHERE DATE(data_venda) = '" + hoje + "' AND status != 'cancelada'");
                    if (rs.next()) {
                        stats.qtdVendasHoje = rs.getInt("qtd");
                        stats.vendasHoje = rs.getDouble("total");
                        if (stats.qtdVendasHoje > 0) {
                            stats.ticketMedioHoje = stats.vendasHoje / stats.qtdVendasHoje;
                        }
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Vendas hoje: " + e.getMessage()); }

                // Caixa aberto
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT id, saldo_inicial FROM caixas WHERE status = 'aberto' LIMIT 1");
                    if (rs.next()) {
                        stats.caixaAberto = true;
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Caixa: " + e.getMessage()); }

                // Mesas
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as total, " +
                        "SUM(CASE WHEN status = 'ocupada' THEN 1 ELSE 0 END) as ocupadas " +
                        "FROM mesas WHERE ativo = 1");
                    if (rs.next()) {
                        stats.mesasTotal = rs.getInt("total");
                        stats.mesasOcupadas = rs.getInt("ocupadas");
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Mesas: " + e.getMessage()); }

                // Estoque baixo
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as qtd FROM produtos " +
                        "WHERE ativo = 1 AND estoque <= estoque_minimo AND estoque_minimo > 0");
                    if (rs.next()) {
                        stats.produtosEstoqueBaixo = rs.getInt("qtd");
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Estoque: " + e.getMessage()); }

                // Contas a vencer em 7 dias
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as qtd FROM contas_pagar " +
                        "WHERE status = 'pendente' AND data_vencimento BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)");
                    if (rs.next()) {
                        stats.contasVencer7Dias = rs.getInt("qtd");
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Contas: " + e.getMessage()); }

                // Comandas abertas
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as qtd FROM comandas WHERE status = 'aberta'");
                    if (rs.next()) {
                        stats.comandasAbertas = rs.getInt("qtd");
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Comandas: " + e.getMessage()); }

                // Entregas pendentes
                try {
                    ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) as qtd FROM entregas WHERE status = 'pendente'");
                    if (rs.next()) {
                        stats.entregasPendentes = rs.getInt("qtd");
                    }
                    rs.close();
                } catch (Exception e) { Log.d(TAG, "Entregas: " + e.getMessage()); }

                stmt.close();
                stats.dataHora = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date());
                stats.loaded = true;

                if (callback != null) callback.onStatsLoaded(stats);

            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar stats: " + e.getMessage());
                if (callback != null) callback.onError(e.getMessage());
            }
        }).start();
    }
}
