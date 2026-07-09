package com.pdv.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pdv.app.database.DatabaseHelper;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.*;
import java.sql.*;
import java.util.*;

public class BackupManager {
    private static final String TAG = "BackupManager";
    private Context context;

    /**
     * LISTA COMPLETA de todas as tabelas do sistema, na ordem correta
     * para exportacao (tabelas-pai primeiro, tabelas-filha depois).
     * Esta lista DEVE ser mantida sincronizada com DatabaseHelper.getTableDefinitions().
     */
    private static final String[] ALL_TABLES_EXPORT_ORDER = {
        "empresa",
        "perfis",
        "usuarios",
        "permissoes",
        "perfil_permissoes",
        "tipos_produto",
        "produtos",
        "adicionais",
        "tipo_produto_adicionais",
        "clientes",
        "vendedores",
        "entregadores",
        "garcons",
        "mesas",
        "formas_pagamento",
        "observacoes_cupom",
        "caixa",
        "vendas",
        "itens_venda",
        "itens_venda_adicionais",
        "pagamentos_venda",
        "vales_debito",
        "comandas",
        "itens_comanda",
        "contas_receber",
        "recebimentos_conta",
        "notas_entrada",
        "itens_nota_entrada",
        "rastreamento_entregador",
        "historico_localizacao",
        "taxa_entrega_bairro",
        "cliente_bairro_whatsapp",
        "ocupacao_mesa",
        "itens_mesa",
        "itens_mesa_adicionais",
        "armarios_sauna",
        "uso_armario_sauna",
        "itens_armario_sauna",
        "itens_armario_sauna_adicionais",
        "ordens_servico",
        "servicos",
        "os_itens",
        "os_fotos",
        "licenca"
    };

    /**
     * Ordem reversa para DELETE (tabelas-filha primeiro, tabelas-pai depois).
     * Respeita foreign keys para evitar erros de constraint.
     */
    private static final String[] ALL_TABLES_DELETE_ORDER = {
        "os_fotos",
        "os_itens",
        "servicos",
        "ordens_servico",
        "itens_armario_sauna_adicionais",
        "itens_armario_sauna",
        "uso_armario_sauna",
        "armarios_sauna",
        "itens_mesa_adicionais",
        "itens_mesa",
        "ocupacao_mesa",
        "cliente_bairro_whatsapp",
        "taxa_entrega_bairro",
        "historico_localizacao",
        "rastreamento_entregador",
        "itens_nota_entrada",
        "notas_entrada",
        "recebimentos_conta",
        "contas_receber",
        "itens_comanda",
        "comandas",
        "vales_debito",
        "pagamentos_venda",
        "itens_venda_adicionais",
        "itens_venda",
        "vendas",
        "caixa",
        "observacoes_cupom",
        "formas_pagamento",
        "mesas",
        "garcons",
        "entregadores",
        "vendedores",
        "clientes",
        "tipo_produto_adicionais",
        "adicionais",
        "produtos",
        "tipos_produto",
        "perfil_permissoes",
        "permissoes",
        "usuarios",
        "perfis",
        "empresa",
        "licenca"
    };

    public BackupManager(Context context) {
        this.context = context;
    }

    // =========================================================================
    // GETTERS DE CONFIGURACAO FTP
    // =========================================================================

    public String getFtpHost() {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        return prefs.getString("ftp_host", "");
    }

    public String getFtpUser() {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        return prefs.getString("ftp_user", "");
    }

    public String getFtpPassword() {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        return prefs.getString("ftp_password", "");
    }

    public boolean isAutoBackupEnabled() {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        return prefs.getBoolean("auto_backup", false);
    }

    /**
     * Retorna true se o formato de backup SQL estiver habilitado.
     * Quando true, gera e envia um arquivo .sql ALEM do .json existente.
     */
    public boolean isSqlBackupEnabled() {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        return prefs.getBoolean("sql_backup_enabled", true);
    }

    public void saveConfig(String host, String user, String pass, boolean autoBackup, boolean sqlBackupEnabled) {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("ftp_host", host)
                .putString("ftp_user", user)
                .putString("ftp_password", pass)
                .putBoolean("auto_backup", autoBackup)
                .putBoolean("sql_backup_enabled", sqlBackupEnabled)
                .apply();
    }

    /**
     * Compatibilidade retroativa: salva config sem alterar a opcao SQL.
     */
    public void saveConfig(String host, String user, String pass, boolean autoBackup) {
        SharedPreferences prefs = context.getSharedPreferences("backup_config", Context.MODE_PRIVATE);
        boolean sqlEnabled = prefs.getBoolean("sql_backup_enabled", true);
        saveConfig(host, user, pass, autoBackup, sqlEnabled);
    }

    // =========================================================================
    // REALIZAR BACKUP (JSON + SQL opcional)
    // =========================================================================

    /**
     * Realiza o backup completo: envia .json ao FTP e, se habilitado,
     * tambem envia um dump .sql completo (CREATE TABLE + INSERT INTO).
     *
     * @return true se pelo menos o backup JSON foi bem-sucedido.
     */
    public boolean realizarBackup() {
        boolean jsonOk = realizarBackupJson();
        if (isSqlBackupEnabled()) {
            boolean sqlOk = realizarBackupSql();
            Log.d(TAG, "Backup SQL: " + (sqlOk ? "sucesso" : "falha"));
        }
        return jsonOk;
    }

    // =========================================================================
    // BACKUP JSON (formato original)
    // =========================================================================

    public boolean realizarBackupJson() {
        FTPClient ftp = new FTPClient();
        try {
            String jsonData = exportDatabaseToJson();
            if (jsonData == null || jsonData.isEmpty()) {
                Log.e(TAG, "Dados vazios para backup JSON");
                return false;
            }
            if (jsonData.length() < 50) {
                Log.e(TAG, "JSON de backup muito pequeno: " + jsonData.length() + " bytes");
                return false;
            }

            String fileName = "backup_" + FormatUtils.getCurrentDateTimeForFile() + ".json";

            ftp.setConnectTimeout(15000);
            ftp.setDataTimeout(30000);
            ftp.connect(getFtpHost(), 21);
            if (!ftp.login(getFtpUser(), getFtpPassword())) {
                Log.e(TAG, "Login FTP recusado no backup JSON");
                return false;
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            ByteArrayInputStream bais = new ByteArrayInputStream(jsonData.getBytes("UTF-8"));
            boolean success = ftp.storeFile(fileName, bais);
            bais.close();

            if (success) {
                FTPFile[] files = ftp.listFiles(fileName);
                if (files != null && files.length > 0) {
                    long remoteSize = files[0].getSize();
                    long localSize = jsonData.getBytes("UTF-8").length;
                    if (remoteSize < localSize * 0.9) {
                        Log.e(TAG, "Arquivo JSON incompleto no FTP: remoto=" + remoteSize + " local=" + localSize);
                        success = false;
                    }
                }
            }

            ftp.logout();
            ftp.disconnect();
            Log.d(TAG, "Backup JSON: " + fileName + " - " + success + " (" + jsonData.length() + " bytes)");
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Erro no backup JSON", e);
            return false;
        } finally {
            try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // BACKUP SQL - NOVO
    // Gera dump SQL completo (DROP TABLE IF EXISTS + CREATE TABLE + INSERT INTO)
    // e envia ao FTP como arquivo .sql
    // =========================================================================

    /**
     * Gera um dump SQL completo do banco de dados e envia ao FTP configurado.
     * O arquivo gerado e compativel com MySQL/MariaDB e pode ser importado
     * diretamente via phpMyAdmin, MySQL Workbench ou linha de comando.
     *
     * @return true se o arquivo .sql foi enviado com sucesso ao FTP.
     */
    public boolean realizarBackupSql() {
        FTPClient ftp = new FTPClient();
        try {
            String sqlDump = exportDatabaseToSql();
            if (sqlDump == null || sqlDump.isEmpty()) {
                Log.e(TAG, "Dump SQL vazio");
                return false;
            }

            String fileName = "backup_" + FormatUtils.getCurrentDateTimeForFile() + ".sql";

            ftp.setConnectTimeout(15000);
            ftp.setDataTimeout(60000);
            ftp.connect(getFtpHost(), 21);
            if (!ftp.login(getFtpUser(), getFtpPassword())) {
                Log.e(TAG, "Login FTP recusado no backup SQL");
                return false;
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            byte[] sqlBytes = sqlDump.getBytes("UTF-8");
            ByteArrayInputStream bais = new ByteArrayInputStream(sqlBytes);
            boolean success = ftp.storeFile(fileName, bais);
            bais.close();

            if (success) {
                FTPFile[] files = ftp.listFiles(fileName);
                if (files != null && files.length > 0) {
                    long remoteSize = files[0].getSize();
                    long localSize = sqlBytes.length;
                    if (remoteSize < localSize * 0.9) {
                        Log.e(TAG, "Arquivo SQL incompleto no FTP: remoto=" + remoteSize + " local=" + localSize);
                        success = false;
                    }
                }
            }

            ftp.logout();
            ftp.disconnect();
            Log.d(TAG, "Backup SQL: " + fileName + " - " + success + " (" + sqlBytes.length + " bytes)");
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Erro no backup SQL", e);
            return false;
        } finally {
            try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
        }
    }

    /**
     * Exporta o banco de dados completo para um dump SQL no formato MySQL.
     * Inclui cabecalho, DROP TABLE IF EXISTS, CREATE TABLE e INSERT INTO
     * para todas as tabelas do sistema.
     */
    private String exportDatabaseToSql() {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            StringBuilder sql = new StringBuilder();

            // Cabecalho do dump
            sql.append("-- ============================================================\n");
            sql.append("-- PDV Pro - Dump SQL Completo\n");
            sql.append("-- Versao: 8.0.12\n");
            sql.append("-- Data: ").append(FormatUtils.getCurrentDateTime()).append("\n");
            sql.append("-- Gerado automaticamente pelo PDV Pro\n");
            sql.append("-- ============================================================\n\n");
            sql.append("SET FOREIGN_KEY_CHECKS = 0;\n");
            sql.append("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';\n");
            sql.append("SET NAMES utf8mb4;\n\n");

            int totalTabelas = 0;
            int totalRegistros = 0;

            for (String tableName : ALL_TABLES_EXPORT_ORDER) {
                try {
                    // Verificar se a tabela existe
                    ResultSet rsCheck = conn.createStatement().executeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables " +
                            "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'");
                    rsCheck.next();
                    int tableExists = rsCheck.getInt(1);
                    rsCheck.close();

                    if (tableExists == 0) {
                        Log.w(TAG, "Tabela nao encontrada, ignorando: " + tableName);
                        continue;
                    }

                    sql.append("-- ----------------------------------------------------------\n");
                    sql.append("-- Tabela: ").append(tableName).append("\n");
                    sql.append("-- ----------------------------------------------------------\n\n");

                    // DROP TABLE IF EXISTS
                    sql.append("DROP TABLE IF EXISTS `").append(tableName).append("`;\n\n");

                    // CREATE TABLE - obter DDL via SHOW CREATE TABLE
                    Statement stmtCreate = conn.createStatement();
                    ResultSet rsCreate = stmtCreate.executeQuery("SHOW CREATE TABLE `" + tableName + "`");
                    if (rsCreate.next()) {
                        String createDdl = rsCreate.getString(2);
                        // Garantir que usa IF NOT EXISTS para compatibilidade
                        createDdl = createDdl.replace(
                                "CREATE TABLE `" + tableName + "`",
                                "CREATE TABLE IF NOT EXISTS `" + tableName + "`");
                        sql.append(createDdl).append(";\n\n");
                    }
                    rsCreate.close();
                    stmtCreate.close();

                    // INSERT INTO - exportar todos os dados
                    Statement stmtData = conn.createStatement();
                    ResultSet rs = stmtData.executeQuery("SELECT * FROM `" + tableName + "`");
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    // Montar lista de colunas
                    StringBuilder colNames = new StringBuilder();
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) colNames.append(", ");
                        colNames.append("`").append(meta.getColumnName(i)).append("`");
                    }

                    int rowCount = 0;
                    while (rs.next()) {
                        StringBuilder insertSql = new StringBuilder();
                        insertSql.append("INSERT INTO `").append(tableName).append("` (")
                                .append(colNames).append(") VALUES (");

                        for (int i = 1; i <= colCount; i++) {
                            if (i > 1) insertSql.append(", ");

                            int colType = meta.getColumnType(i);
                            Object value = null;

                            switch (colType) {
                                case Types.INTEGER:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                case Types.BIGINT:
                                    long longVal = rs.getLong(i);
                                    value = rs.wasNull() ? null : longVal;
                                    break;
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                case Types.FLOAT:
                                case Types.DOUBLE:
                                case Types.REAL:
                                    String decStr = rs.getString(i);
                                    value = rs.wasNull() ? null : decStr;
                                    break;
                                case Types.BIT:
                                case Types.BOOLEAN:
                                    boolean boolVal = rs.getBoolean(i);
                                    value = rs.wasNull() ? null : (boolVal ? 1L : 0L);
                                    break;
                                default:
                                    value = rs.getString(i);
                                    if (rs.wasNull()) value = null;
                                    break;
                            }

                            if (value == null) {
                                insertSql.append("NULL");
                            } else if (value instanceof Number) {
                                insertSql.append(value);
                            } else {
                                // Escapar strings para SQL
                                String strVal = value.toString();
                                strVal = strVal.replace("\\", "\\\\");
                                strVal = strVal.replace("'", "\\'");
                                strVal = strVal.replace("\r\n", "\\n");
                                strVal = strVal.replace("\n", "\\n");
                                strVal = strVal.replace("\r", "\\n");
                                insertSql.append("'").append(strVal).append("'");
                            }
                        }

                        insertSql.append(");\n");
                        sql.append(insertSql);
                        rowCount++;
                        totalRegistros++;
                    }

                    rs.close();
                    stmtData.close();

                    if (rowCount > 0) {
                        sql.append("\n");
                    }

                    sql.append("-- ").append(rowCount).append(" registro(s) exportado(s) de ").append(tableName).append("\n\n");
                    totalTabelas++;
                    Log.d(TAG, "SQL exportado " + tableName + ": " + rowCount + " registros");

                } catch (Exception e) {
                    Log.w(TAG, "Erro ao exportar tabela " + tableName + " para SQL: " + e.getMessage());
                    sql.append("-- ERRO ao exportar tabela ").append(tableName).append(": ").append(e.getMessage()).append("\n\n");
                }
            }

            // Rodape do dump
            sql.append("\n");
            sql.append("SET FOREIGN_KEY_CHECKS = 1;\n\n");
            sql.append("-- ============================================================\n");
            sql.append("-- Fim do dump SQL\n");
            sql.append("-- Total: ").append(totalTabelas).append(" tabela(s), ")
               .append(totalRegistros).append(" registro(s)\n");
            sql.append("-- ============================================================\n");

            Log.d(TAG, "Dump SQL gerado: " + totalTabelas + " tabelas, " + totalRegistros + " registros, " + sql.length() + " bytes");
            return sql.toString();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao gerar dump SQL", e);
            return null;
        }
    }

    // =========================================================================
    // LISTAR BACKUPS (JSON e SQL)
    // =========================================================================

    public List<String> listarBackups() {
        List<String> backups = new ArrayList<>();
        FTPClient ftp = new FTPClient();
        try {
            ftp.setConnectTimeout(15000);
            ftp.setDataTimeout(30000);
            ftp.connect(getFtpHost(), 21);
            if (!ftp.login(getFtpUser(), getFtpPassword())) {
                Log.e(TAG, "Login FTP recusado ao listar backups");
                return backups;
            }
            ftp.enterLocalPassiveMode();

            FTPFile[] files = ftp.listFiles();
            if (files != null) {
                for (FTPFile file : files) {
                    String name = file.getName();
                    if (name.startsWith("backup_") && (name.endsWith(".json") || name.endsWith(".sql"))) {
                        backups.add(name);
                    }
                }
            }
            Collections.sort(backups, Collections.reverseOrder());

            ftp.logout();
            ftp.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar backups", e);
        } finally {
            try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
        }
        return backups;
    }

    // =========================================================================
    // RESTAURAR BACKUP - suporta apenas .json (restauracao de .sql nao aplicavel via app)
    // =========================================================================

    public boolean restaurarBackup(String fileName) {
        if (fileName.endsWith(".sql")) {
            Log.w(TAG, "Restauracao de arquivos .sql nao e suportada pelo app. Use um cliente MySQL externo.");
            return false;
        }
        FTPClient ftp = new FTPClient();
        try {
            ftp.setConnectTimeout(15000);
            ftp.setDataTimeout(60000);
            ftp.connect(getFtpHost(), 21);
            if (!ftp.login(getFtpUser(), getFtpPassword())) {
                Log.e(TAG, "Login FTP recusado ao restaurar backup");
                return false;
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean downloaded = ftp.retrieveFile(fileName, baos);
            ftp.logout();
            ftp.disconnect();

            if (!downloaded) {
                Log.e(TAG, "Falha ao baixar arquivo de backup: " + fileName);
                return false;
            }

            String jsonData = baos.toString("UTF-8");
            if (jsonData == null || jsonData.trim().isEmpty()) {
                Log.e(TAG, "Arquivo de backup vazio: " + fileName);
                return false;
            }

            Log.d(TAG, "Backup baixado com sucesso: " + fileName + " (" + jsonData.length() + " bytes)");
            return importDatabaseFromJson(jsonData);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao restaurar backup", e);
            return false;
        } finally {
            try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // EXPORTAR BANCO PARA JSON - CORRIGIDO
    // Agora exporta TODAS as tabelas do sistema
    // =========================================================================

    private String exportDatabaseToJson() {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();

            Map<String, List<Map<String, Object>>> allData = new LinkedHashMap<>();
            int totalRegistros = 0;

            for (String table : ALL_TABLES_EXPORT_ORDER) {
                try {
                    ResultSet rs = stmt.executeQuery("SELECT * FROM `" + table + "`");
                    ResultSetMetaData meta = rs.getMetaData();
                    List<Map<String, Object>> rows = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String colName = meta.getColumnName(i);
                            int colType = meta.getColumnType(i);
                            Object value = null;

                            switch (colType) {
                                case Types.INTEGER:
                                case Types.SMALLINT:
                                case Types.TINYINT:
                                case Types.BIGINT:
                                    long longVal = rs.getLong(i);
                                    if (!rs.wasNull()) {
                                        value = longVal;
                                    }
                                    break;
                                case Types.DECIMAL:
                                case Types.NUMERIC:
                                case Types.FLOAT:
                                case Types.DOUBLE:
                                case Types.REAL:
                                    String decStr = rs.getString(i);
                                    if (!rs.wasNull() && decStr != null) {
                                        value = decStr;
                                    }
                                    break;
                                case Types.BIT:
                                case Types.BOOLEAN:
                                    boolean boolVal = rs.getBoolean(i);
                                    if (!rs.wasNull()) {
                                        value = boolVal ? 1L : 0L;
                                    }
                                    break;
                                default:
                                    value = rs.getString(i);
                                    break;
                            }

                            row.put(colName, value);
                        }
                        rows.add(row);
                    }
                    allData.put(table, rows);
                    totalRegistros += rows.size();
                    rs.close();
                    Log.d(TAG, "Exportado " + table + ": " + rows.size() + " registros");
                } catch (Exception e) {
                    Log.w(TAG, "Tabela " + table + " nao encontrada para backup: " + e.getMessage());
                    allData.put(table, new ArrayList<>());
                }
            }
            stmt.close();

            Log.d(TAG, "Total de registros exportados: " + totalRegistros + " em " + allData.size() + " tabelas");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("versao_backup", "8.0.12");
            metadata.put("data_backup", FormatUtils.getCurrentDateTime());
            metadata.put("total_tabelas", allData.size());
            metadata.put("total_registros", totalRegistros);

            Map<String, Object> backupRoot = new LinkedHashMap<>();
            backupRoot.put("_metadata", metadata);
            backupRoot.put("dados", allData);

            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeNulls()
                    .disableHtmlEscaping()
                    .create();
            return gson.toJson(backupRoot);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao exportar dados", e);
            return null;
        }
    }

    // =========================================================================
    // IMPORTAR JSON PARA BANCO - CORRIGIDO
    // =========================================================================

    @SuppressWarnings("unchecked")
    private boolean importDatabaseFromJson(String jsonData) {
        Connection conn = null;
        boolean autoCommitOriginal = true;
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            conn = db.getConnection();

            Gson gson = new Gson();
            Map<String, Object> backupRoot = gson.fromJson(jsonData, Map.class);
            if (backupRoot == null) {
                Log.e(TAG, "JSON de backup invalido (null)");
                return false;
            }

            Map<String, List<Map<String, Object>>> allData;
            if (backupRoot.containsKey("dados")) {
                allData = (Map<String, List<Map<String, Object>>>) backupRoot.get("dados");
            } else {
                allData = (Map<String, List<Map<String, Object>>>) (Map) backupRoot;
            }

            if (allData == null || allData.isEmpty()) {
                Log.e(TAG, "Nenhum dado encontrado no backup");
                return false;
            }

            Map<String, Map<String, Integer>> tableColumnTypes = new LinkedHashMap<>();
            Statement metaStmt = conn.createStatement();
            for (String table : ALL_TABLES_EXPORT_ORDER) {
                try {
                    ResultSet rs = metaStmt.executeQuery("SELECT * FROM `" + table + "` LIMIT 0");
                    ResultSetMetaData meta = rs.getMetaData();
                    Map<String, Integer> colTypes = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        colTypes.put(meta.getColumnName(i).toLowerCase(), meta.getColumnType(i));
                    }
                    tableColumnTypes.put(table, colTypes);
                    rs.close();
                } catch (Exception e) {
                    Log.w(TAG, "Nao foi possivel obter metadados da tabela " + table);
                }
            }
            metaStmt.close();

            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            Statement stmt = conn.createStatement();
            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");

            for (String table : ALL_TABLES_DELETE_ORDER) {
                try {
                    stmt.executeUpdate("DELETE FROM `" + table + "`");
                    Log.d(TAG, "Tabela limpa: " + table);
                } catch (Exception e) {
                    Log.w(TAG, "Aviso ao limpar " + table + ": " + e.getMessage());
                }
            }

            int totalInseridos = 0;
            int totalErros = 0;

            for (String table : ALL_TABLES_EXPORT_ORDER) {
                List<Map<String, Object>> rows = allData.get(table);
                if (rows == null || rows.isEmpty()) {
                    Log.d(TAG, "Tabela " + table + ": sem dados no backup");
                    continue;
                }

                Map<String, Integer> colTypes = tableColumnTypes.get(table);
                int inseridosTabela = 0;

                for (Map<String, Object> row : rows) {
                    try {
                        if (row == null || row.isEmpty()) continue;

                        StringBuilder cols = new StringBuilder();
                        StringBuilder vals = new StringBuilder();
                        List<Object> params = new ArrayList<>();
                        List<Integer> paramTypes = new ArrayList<>();

                        for (Map.Entry<String, Object> entry : row.entrySet()) {
                            String colName = entry.getKey();
                            if (colName.startsWith("_")) continue;

                            if (cols.length() > 0) { cols.append(","); vals.append(","); }
                            cols.append("`").append(colName).append("`");
                            vals.append("?");

                            Object val = entry.getValue();
                            int sqlType = Types.VARCHAR;

                            if (colTypes != null && colTypes.containsKey(colName.toLowerCase())) {
                                sqlType = colTypes.get(colName.toLowerCase());
                            }

                            params.add(val);
                            paramTypes.add(sqlType);
                        }

                        if (cols.length() == 0) continue;

                        String sql = "INSERT INTO `" + table + "` (" + cols + ") VALUES (" + vals + ")";
                        PreparedStatement ps = conn.prepareStatement(sql);

                        for (int i = 0; i < params.size(); i++) {
                            Object val = params.get(i);
                            int sqlType = paramTypes.get(i);
                            setParameterWithCorrectType(ps, i + 1, val, sqlType);
                        }

                        ps.executeUpdate();
                        ps.close();
                        inseridosTabela++;
                        totalInseridos++;
                    } catch (Exception e) {
                        totalErros++;
                        Log.w(TAG, "Erro ao inserir registro em " + table + ": " + e.getMessage());
                    }
                }
                Log.d(TAG, "Importado " + table + ": " + inseridosTabela + "/" + rows.size() + " registros");
            }

            stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
            stmt.close();

            conn.commit();
            conn.setAutoCommit(autoCommitOriginal);

            Log.d(TAG, "Importacao concluida: " + totalInseridos + " registros inseridos, " + totalErros + " erros");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao importar dados", e);
            if (conn != null) {
                try {
                    conn.rollback();
                    conn.setAutoCommit(autoCommitOriginal);
                    Log.d(TAG, "Rollback executado com sucesso");
                } catch (Exception rollbackEx) {
                    Log.e(TAG, "Erro no rollback", rollbackEx);
                }
            }
            return false;
        }
    }

    /**
     * Define o parametro no PreparedStatement com o tipo correto,
     * convertendo valores do Gson (que transforma tudo em Double) para
     * o tipo adequado da coluna no banco de dados.
     */
    private void setParameterWithCorrectType(PreparedStatement ps, int index, Object val, int sqlType) throws SQLException {
        if (val == null) {
            ps.setNull(index, Types.VARCHAR);
            return;
        }

        switch (sqlType) {
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                if (val instanceof Number) {
                    ps.setInt(index, ((Number) val).intValue());
                } else {
                    try {
                        ps.setInt(index, (int) Double.parseDouble(val.toString()));
                    } catch (NumberFormatException e) {
                        ps.setNull(index, Types.INTEGER);
                    }
                }
                break;

            case Types.BIGINT:
                if (val instanceof Number) {
                    ps.setLong(index, ((Number) val).longValue());
                } else {
                    try {
                        ps.setLong(index, (long) Double.parseDouble(val.toString()));
                    } catch (NumberFormatException e) {
                        ps.setNull(index, Types.BIGINT);
                    }
                }
                break;

            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.REAL:
                if (val instanceof Number) {
                    ps.setDouble(index, ((Number) val).doubleValue());
                } else {
                    try {
                        ps.setDouble(index, Double.parseDouble(val.toString()));
                    } catch (NumberFormatException e) {
                        ps.setNull(index, sqlType);
                    }
                }
                break;

            case Types.BIT:
            case Types.BOOLEAN:
                if (val instanceof Boolean) {
                    ps.setInt(index, ((Boolean) val) ? 1 : 0);
                } else if (val instanceof Number) {
                    ps.setInt(index, ((Number) val).intValue());
                } else {
                    String s = val.toString().trim().toLowerCase();
                    ps.setInt(index, ("true".equals(s) || "1".equals(s) || "1.0".equals(s)) ? 1 : 0);
                }
                break;

            default:
                ps.setString(index, val.toString());
                break;
        }
    }
}
