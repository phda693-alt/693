package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;

import java.security.MessageDigest;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LicencaManager {
    private static final String TAG = "LicencaManager";
    private static final String SECRET_KEY = "PDV_PRO_V3_2024_SECRET";
    private static final String PREFS_NAME = "licenca_cache";

    // Keys for SharedPreferences cache
    private static final String KEY_CONTRA_CHAVE = "contra_chave";
    private static final String KEY_CHAVE_ATIVACAO = "chave_ativacao";
    private static final String KEY_DATA_EXPIRACAO = "data_expiracao";
    private static final String KEY_LICENCA_VALIDA = "licenca_valida";
    private static final String KEY_LAST_CHECK = "last_check_time";

    public static String gerarContraChave(Context context) {
        try {
            String androidId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId == null || androidId.isEmpty()) {
                androidId = "DEFAULT_DEVICE";
            }
            String raw = androidId + "_PDV";
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar contra-chave", e);
            return "0000000000000000";
        }
    }

    public static String gerarChaveAtivacao(String contraChave, String dataExpiracao) {
        try {
            String raw = contraChave + SECRET_KEY + dataExpiracao;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02X", digest[i]));
            }
            // Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
            String hex = sb.toString();
            return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-"
                    + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                    + hex.substring(16, 20) + "-" + hex.substring(20, 24) + "-"
                    + hex.substring(24, 28) + "-" + hex.substring(28, 32);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar chave", e);
            return "";
        }
    }

    public static boolean validarChave(String contraChave, String chaveAtivacao, String dataExpiracao) {
        String chaveEsperada = gerarChaveAtivacao(contraChave, dataExpiracao);
        return chaveEsperada.equalsIgnoreCase(chaveAtivacao);
    }

    /**
     * Salva os dados da licenca no cache local (SharedPreferences).
     * Isso garante que a licenca persista mesmo sem conexao ao banco.
     */
    private static void salvarCacheLocal(Context context, String contraChave, String chaveAtivacao, String dataExpiracao, boolean valida) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_CONTRA_CHAVE, contraChave)
                .putString(KEY_CHAVE_ATIVACAO, chaveAtivacao)
                .putString(KEY_DATA_EXPIRACAO, dataExpiracao)
                .putBoolean(KEY_LICENCA_VALIDA, valida)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .commit(); // Usando commit() para garantir gravacao sincrona
    }

    /**
     * Limpa o cache local da licenca.
     */
    private static void limparCacheLocal(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    /**
     * Verifica a licenca usando cache local primeiro, depois banco remoto.
     * Estrategia:
     * 1. Tenta verificar no cache local (SharedPreferences)
     * 2. Se o cache local indica licenca valida e nao expirada, retorna true imediatamente
     * 3. Se nao ha cache ou esta expirado, tenta verificar no banco remoto
     * 4. Se o banco remoto confirma, atualiza o cache local
     * 5. Se o banco remoto falha mas o cache local indica valida, confia no cache
     */
    public static boolean verificarLicenca(Context context) {
        // PASSO 1: Verificar cache local primeiro
        boolean cacheValido = verificarCacheLocal(context);

        // PASSO 2: Tentar verificar no banco remoto
        boolean bancoVerificado = false;
        boolean bancoResultado = false;
        try {
            bancoResultado = verificarLicencaBanco(context);
            bancoVerificado = true;
        } catch (Exception e) {
            Log.w(TAG, "Falha ao verificar licenca no banco remoto: " + e.getMessage());
        }

        // PASSO 3: Decidir resultado
        if (bancoVerificado) {
            // Banco respondeu - usar resultado do banco e atualizar cache
            if (bancoResultado) {
                // Licenca valida no banco - sincronizar cache
                sincronizarCacheDoBanco(context);
            }
            return bancoResultado;
        } else {
            // Banco nao respondeu - confiar no cache local
            if (cacheValido) {
                Log.d(TAG, "Usando cache local da licenca (banco indisponivel)");
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica a licenca diretamente no cache local (SharedPreferences).
     */
    private static boolean verificarCacheLocal(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean valida = prefs.getBoolean(KEY_LICENCA_VALIDA, false);
            if (!valida) return false;

            String contraChave = prefs.getString(KEY_CONTRA_CHAVE, null);
            String chaveAtivacao = prefs.getString(KEY_CHAVE_ATIVACAO, null);
            String dataExp = prefs.getString(KEY_DATA_EXPIRACAO, null);

            if (contraChave == null || chaveAtivacao == null || dataExp == null) {
                return false;
            }

            // Validar a chave localmente
            if (!validarChave(contraChave, chaveAtivacao, dataExp)) {
                return false;
            }

            // Verificar se nao expirou
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date expDate = sdf.parse(dataExp);
            Date now = new Date();
            return now.before(expDate) || sdf.format(now).equals(sdf.format(expDate));
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar cache local", e);
            return false;
        }
    }

    /**
     * Verifica a licenca diretamente no banco de dados MySQL.
     */
    private static boolean verificarLicencaBanco(Context context) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT contra_chave, chave_ativacao, DATE_FORMAT(data_expiracao, '%Y-%m-%d') AS data_exp FROM licenca ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                String contraChave = rs.getString("contra_chave");
                String chaveAtivacao = rs.getString("chave_ativacao");
                String dataExp = rs.getString("data_exp");
                rs.close();
                stmt.close();

                if (contraChave == null || chaveAtivacao == null || dataExp == null) {
                    return false;
                }

                // Validate key
                if (!validarChave(contraChave, chaveAtivacao, dataExp)) {
                    return false;
                }

                // Check expiration
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expDate = sdf.parse(dataExp);
                Date now = new Date();
                boolean valida = now.before(expDate) || sdf.format(now).equals(sdf.format(expDate));

                // Atualizar cache local com dados do banco
                if (valida) {
                    salvarCacheLocal(context, contraChave, chaveAtivacao, dataExp, true);
                } else {
                    limparCacheLocal(context);
                }

                return valida;
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar licenca no banco", e);
            throw new RuntimeException(e);
        }
        return false;
    }

    /**
     * Sincroniza o cache local com os dados do banco remoto.
     */
    private static void sincronizarCacheDoBanco(Context context) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT contra_chave, chave_ativacao, DATE_FORMAT(data_expiracao, '%Y-%m-%d') AS data_exp FROM licenca ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                String contraChave = rs.getString("contra_chave");
                String chaveAtivacao = rs.getString("chave_ativacao");
                String dataExp = rs.getString("data_exp");
                if (contraChave != null && chaveAtivacao != null && dataExp != null) {
                    salvarCacheLocal(context, contraChave, chaveAtivacao, dataExp, true);
                }
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao sincronizar cache do banco", e);
        }
    }

    /**
     * Verifica se existe uma licenca cadastrada mas que esta VENCIDA (expirada).
     * Diferente de verificarLicenca() que retorna false tanto para "sem licenca" quanto "vencida",
     * este metodo retorna true SOMENTE quando existe uma licenca mas a data de expiracao ja passou.
     */
    public static boolean isLicencaExpirada(Context context) {
        // Verificar no cache local primeiro
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String dataExp = prefs.getString(KEY_DATA_EXPIRACAO, null);
            String chaveAtivacao = prefs.getString(KEY_CHAVE_ATIVACAO, null);

            if (dataExp != null && chaveAtivacao != null && !chaveAtivacao.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date expDate = sdf.parse(dataExp);
                Date now = new Date();
                String todayStr = sdf.format(now);
                Date today = sdf.parse(todayStr);
                if (expDate != null && expDate.before(today)) {
                    return true; // Licenca existe mas esta vencida
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar expiracao no cache local", e);
        }

        // Verificar no banco remoto
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT DATE_FORMAT(data_expiracao, '%Y-%m-%d') AS data_exp FROM licenca ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                String dataExp = rs.getString("data_exp");
                rs.close();
                stmt.close();
                if (dataExp != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date expDate = sdf.parse(dataExp);
                    Date now = new Date();
                    String todayStr = sdf.format(now);
                    Date today = sdf.parse(todayStr);
                    if (expDate != null && expDate.before(today)) {
                        return true; // Licenca existe no banco mas esta vencida
                    }
                }
            } else {
                rs.close();
                stmt.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao verificar expiracao no banco", e);
        }

        return false;
    }

    /**
     * Retorna a data de expiracao da licenca atual (do cache local ou banco).
     * Retorna null se nao houver licenca cadastrada.
     */
    public static String getDataExpiracao(Context context) {
        // Tentar do cache local primeiro
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String dataExp = prefs.getString(KEY_DATA_EXPIRACAO, null);
            if (dataExp != null && !dataExp.isEmpty()) {
                return dataExp;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter data de expiracao do cache", e);
        }

        // Tentar do banco remoto
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT DATE_FORMAT(data_expiracao, '%Y-%m-%d') AS data_exp FROM licenca ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                String dataExp = rs.getString("data_exp");
                rs.close();
                stmt.close();
                return dataExp;
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao obter data de expiracao do banco", e);
        }

        return null;
    }

    /**
     * Verifica se a data de expiracao nao e retroativa (anterior a data atual).
     * Retorna true se a data for valida (igual ou posterior a hoje).
     */
    public static boolean validarDataNaoRetroativa(String dataExpiracao) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            Date expDate = sdf.parse(dataExpiracao);
            Date now = new Date();
            String todayStr = sdf.format(now);
            Date today = sdf.parse(todayStr);
            return expDate != null && !expDate.before(today);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao validar data de expiracao", e);
            return false;
        }
    }

    /**
     * Limpa completamente a tabela licenca no banco de dados e o cache local.
     * Remove todos os registros de licenca.
     */
    public static boolean limparTabelaLicenca(Context context) {
        // Limpar cache local primeiro
        limparCacheLocal(context);
        Log.d(TAG, "Cache local da licenca limpo com sucesso");

        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("DELETE FROM licenca");
            stmt.close();
            Log.d(TAG, "Tabela licenca limpa com sucesso no banco de dados");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao limpar tabela licenca no banco", e);
            // Cache local ja foi limpo, retorna true parcial
            return true;
        }
    }

    /**
     * Retorna a quantidade de dias restantes ate o vencimento da licenca.
     * Retorna -1 se nao houver licenca cadastrada ou se houver erro.
     * Retorna 0 se vence hoje.
     * Retorna valores negativos se ja estiver vencida.
     */
    public static int getDiasParaVencimento(Context context) {
        String dataExp = getDataExpiracao(context);
        if (dataExp == null || dataExp.isEmpty()) {
            return -1;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date expDate = sdf.parse(dataExp);
            Date now = new Date();
            String todayStr = sdf.format(now);
            Date today = sdf.parse(todayStr);
            if (expDate == null || today == null) return -1;
            long diffMs = expDate.getTime() - today.getTime();
            int dias = (int) (diffMs / (1000 * 60 * 60 * 24));
            return dias;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular dias para vencimento", e);
            return -1;
        }
    }

    public static boolean ativarLicenca(Context context, String contraChave, String chaveAtivacao, String dataExpiracao) {
        // Validar se a data de expiracao nao e retroativa
        if (!validarDataNaoRetroativa(dataExpiracao)) {
            Log.w(TAG, "Tentativa de ativar licenca com data retroativa: " + dataExpiracao);
            return false;
        }

        if (!validarChave(contraChave, chaveAtivacao, dataExpiracao)) {
            return false;
        }

        // IMPORTANTE: Salvar no cache local PRIMEIRO para garantir persistencia
        salvarCacheLocal(context, contraChave, chaveAtivacao, dataExpiracao, true);
        Log.d(TAG, "Licenca salva no cache local com sucesso");

        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            // Remove old licenses
            Statement delStmt = conn.createStatement();
            delStmt.executeUpdate("DELETE FROM licenca");
            delStmt.close();

            // Insert new license - usar STR_TO_DATE para garantir formato correto
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO licenca (contra_chave, chave_ativacao, data_ativacao, data_expiracao) VALUES (?, ?, NOW(), STR_TO_DATE(?, '%Y-%m-%d'))");
            ps.setString(1, contraChave);
            ps.setString(2, chaveAtivacao);
            ps.setString(3, dataExpiracao);
            ps.executeUpdate();
            ps.close();

            Log.d(TAG, "Licenca ativada e salva no banco com sucesso");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao ativar licenca no banco (cache local ja salvo)", e);
            // Licenca ja foi salva no cache local, entao retorna true
            return true;
        }
    }
}
