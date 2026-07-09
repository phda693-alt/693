package com.pdv.app.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper de banco de dados MySQL com otimizacoes de performance.
 *
 * v8.0.23.0 - Melhorias:
 * - Verificacao obrigatoria de TODAS as tabelas e colunas na inicializacao
 * - schema_migrations_pdv: detecta bancos antigos e migra automaticamente
 * - Batch de verificacoes de colunas via INFORMATION_SCHEMA (1 query por tabela)
 * - Conexao com cachePrepStmts, rewriteBatchedStatements e maintainTimeStats=false
 * - Timeouts de conexao reduzidos (8s connect, 15s socket)
 * - Retry com backoff mais curto (500ms base)
 * - Validacao de conexao com timeout menor (3s)
 * - Flag markAsInitialized() para pular verificacoes redundantes
 */
public class DatabaseHelper {
    private static final String TAG = "DatabaseHelper";
    private static final int DB_VERSION = 2;
    /** Versao atual do schema. Incrementar a cada nova tabela ou coluna adicionada. */
    private static final int SCHEMA_VERSION = 26;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 500; // Reduzido de 1000ms para 500ms
    private static final int VALIDATION_TIMEOUT = 3; // Reduzido de 5s para 3s
    private static DatabaseHelper instance;
    private Context context;
    private Connection connection;
    private long lastConnectionValidationAt = 0L;
    private String host, database, username, password;
    private int port;

    // Flag para evitar inicializacao duplicada na mesma sessao
    private boolean databaseInitialized = false;

    private DatabaseHelper(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context);
        }
        return instance;
    }

    public void reloadConfig() {
        loadConfig();
        closeConnection();
        databaseInitialized = false;
    }

    private void loadConfig() {
        SharedPreferences prefs = context.getSharedPreferences("db_config", Context.MODE_PRIVATE);
        host = prefs.getString("host", "127.0.0.1");
        database = prefs.getString("database", "banco");
        username = prefs.getString("username", "usuario");
        password = prefs.getString("password", "senha");
        port = prefs.getInt("port", 3306);
    }

    public void saveConfig(String host, String database, String username, String password, int port) {
        SharedPreferences prefs = context.getSharedPreferences("db_config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("host", host);
        editor.putString("database", database);
        editor.putString("username", username);
        editor.putString("password", password);
        editor.putInt("port", port);
        editor.apply();
        this.host = host;
        this.database = database;
        this.username = username;
        this.password = password;
        this.port = port;
        closeConnection();
        databaseInitialized = false;

        // Invalidar cache de inicializacao da splash
        try {
            com.pdv.app.activities.SplashActivity.invalidateInitCache(context);
        } catch (Exception e) {
            Log.w(TAG, "Aviso ao invalidar cache de splash: " + e.getMessage());
        }
    }

    /**
     * Verifica se a conexao atual ainda esta valida e utilizavel.
     * Usa isValid() com timeout reduzido e fallback para query simples.
     */
    private boolean isConnectionValid() {
        try {
            if (connection == null || connection.isClosed()) {
                return false;
            }
            // Evita um round-trip JDBC em cada consulta. Dentro desta janela a
            // conexao ja validada pode ser reutilizada com seguranca.
            if (System.currentTimeMillis() - lastConnectionValidationAt < 5000L) {
                return true;
            }
            // Tenta isValid() primeiro (metodo padrao JDBC 4)
            try {
                if (!connection.isValid(VALIDATION_TIMEOUT)) {
                    return false;
                }
                lastConnectionValidationAt = System.currentTimeMillis();
                return true;
            } catch (AbstractMethodError e) {
                // Driver antigo sem suporte a isValid - fallback para query
            }
            // Fallback: executa query simples para testar
            Statement stmt = null;
            ResultSet rs = null;
            try {
                stmt = connection.createStatement();
                stmt.setQueryTimeout(VALIDATION_TIMEOUT);
                rs = stmt.executeQuery("SELECT 1");
                lastConnectionValidationAt = System.currentTimeMillis();
                return true;
            } finally {
                if (rs != null) try { rs.close(); } catch (Exception ignored) {}
                if (stmt != null) try { stmt.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "Conexao invalida: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cria uma nova conexao MySQL com as configuracoes atuais.
     * v6.9.2 - Timeouts otimizados para inicializacao mais rapida.
     */
    private Connection createNewConnection() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL nao encontrado: " + e.getMessage());
        }

        // v8.0.16 - Timeouts curtos + parametros de estabilidade/performance JDBC.
        // v9.1.0 - Adicionados parametros que reduzem round-trips de rede (resposta
        //          mais rapida e estavel, sem perda de pacotes/dados):
        //          useLocalSessionState, useLocalTransactionState,
        //          cacheServerConfiguration, useServerPrepStmts e elideSetAutoCommits.
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&connectTimeout=8000"
                + "&socketTimeout=15000&characterEncoding=UTF-8"
                + "&useUnicode=true&allowPublicKeyRetrieval=true"
                + "&tcpKeepAlive=true&interactiveClient=true"
                + "&serverTimezone=America/Sao_Paulo"
                + "&zeroDateTimeBehavior=convertToNull"
                + "&useLegacyDatetimeCode=false"
                + "&cachePrepStmts=true&prepStmtCacheSize=250"
                + "&prepStmtCacheSqlLimit=2048&rewriteBatchedStatements=true"
                + "&useServerPrepStmts=true&useLocalSessionState=true"
                + "&useLocalTransactionState=true&cacheServerConfiguration=true"
                + "&elideSetAutoCommits=true&maintainTimeStats=false";

        Connection conn = DriverManager.getConnection(url, username, password);

        // Configurar charset utf8mb4 para suporte Unicode completo
        Statement stmt = conn.createStatement();
        try {
            stmt.execute("SET NAMES utf8mb4");
            stmt.execute("SET CHARACTER SET utf8mb4");
            stmt.execute("SET character_set_connection=utf8mb4");
        } catch (SQLException e) {
            // Se utf8mb4 nao for suportado, ignora silenciosamente
            Log.w(TAG, "Aviso ao configurar utf8mb4: " + e.getMessage());
        } finally {
            stmt.close();
        }

        Log.d(TAG, "Nova conexao MySQL estabelecida com sucesso");
        return conn;
    }

    /**
     * Cria uma conexao ao servidor MySQL SEM especificar o banco de dados.
     * Usada para verificar/criar o banco de dados caso nao exista.
     * v6.9.2 - Timeouts otimizados.
     */
    private Connection createServerConnection() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver MySQL nao encontrado: " + e.getMessage());
        }

        String url = "jdbc:mysql://" + host + ":" + port
                + "?useSSL=false&autoReconnect=true&connectTimeout=8000"
                + "&socketTimeout=15000&characterEncoding=UTF-8"
                + "&useUnicode=true&allowPublicKeyRetrieval=true"
                + "&serverTimezone=America/Sao_Paulo"
                + "&zeroDateTimeBehavior=convertToNull"
                + "&useLegacyDatetimeCode=false"
                + "&maintainTimeStats=false";

        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Obtem uma conexao valida com retry automatico.
     * v6.9.2 - Retry com backoff mais curto (500ms base).
     */
    public synchronized Connection getConnection() throws SQLException {
        // Se ja tem conexao valida, reutiliza
        if (isConnectionValid()) {
            aplicarContextoSessao(connection);
            return connection;
        }

        // Conexao invalida - fechar e criar nova
        closeConnectionQuietly();

        SQLException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                connection = createNewConnection();
                lastConnectionValidationAt = System.currentTimeMillis();
                aplicarContextoSessao(connection);
                return connection;
            } catch (SQLException e) {
                lastException = e;
                Log.w(TAG, "Tentativa " + attempt + "/" + MAX_RETRIES + " falhou: " + e.getMessage());
                closeConnectionQuietly();

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Conexao interrompida", ie);
                    }
                }
            }
        }

        throw new SQLException("Falha ao conectar apos " + MAX_RETRIES + " tentativas. "
                + "Verifique sua conexao de internet e as configuracoes do banco. "
                + (lastException != null ? "Ultimo erro: " + lastException.getMessage() : ""));
    }

    /**
     * Fecha a conexao silenciosamente, sem lancar excecoes.
     */
    private void closeConnectionQuietly() {
        try {
            if (connection != null) {
                if (!connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao fechar conexao: " + e.getMessage());
        }
        connection = null;
        lastConnectionValidationAt = 0L;
    }

    private void aplicarContextoSessao(Connection conn) {
        if (conn == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE);
            int userId = prefs.getInt("user_id", 0);
            String userName = prefs.getString("user_nome", "Sistema");
            String ip = com.pdv.app.utils.NetworkUtils.getLocalIpv4();
            PreparedStatement ps = conn.prepareStatement(
                    "SET @pdv_usuario_id=?, @pdv_usuario_nome=?, @pdv_ip=?");
            ps.setInt(1, userId);
            ps.setString(2, userName != null ? userName : "Sistema");
            ps.setString(3, ip != null ? ip : "");
            ps.execute();
            ps.close();
        } catch (Exception e) {
            Log.d(TAG, "Contexto de auditoria ainda indisponivel: " + e.getMessage());
        }
    }

    public void closeConnection() {
        closeConnectionQuietly();
    }

    public boolean testConnection() {
        Connection testConn = null;
        try {
            // Fecha conexao existente para forcar teste real
            closeConnectionQuietly();
            testConn = createNewConnection();
            Statement stmt = testConn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT 1");
            rs.close();
            stmt.close();
            // Guarda a conexao testada como conexao ativa
            connection = testConn;
            testConn = null; // Evita fechar no finally
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Teste de conexao falhou", e);
            return false;
        } finally {
            if (testConn != null) {
                try { testConn.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Test connection with custom parameters - also uses UTF-8 encoding fix
     */
    public boolean testConnection(String h, String db, String user, String pass, int p) {
        Connection testConn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            String url = "jdbc:mysql://" + h + ":" + p + "/" + db
                    + "?useSSL=false&connectTimeout=8000&useUnicode=true"
                    + "&characterEncoding=UTF-8&allowPublicKeyRetrieval=true"
                    + "&tcpKeepAlive=true&serverTimezone=America/Sao_Paulo"
                    + "&zeroDateTimeBehavior=convertToNull&useLegacyDatetimeCode=false"
                    + "&maintainTimeStats=false";
            testConn = DriverManager.getConnection(url, user, pass);
            if (testConn != null && !testConn.isClosed()) {
                Statement stmt = testConn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1");
                rs.close();
                stmt.close();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Teste de conexao personalizada falhou: " + e.getMessage(), e);
        } finally {
            if (testConn != null) {
                try { testConn.close(); } catch (Exception ignored) {}
            }
        }
        return false;
    }

    /**
     * Executa uma operacao de banco de dados com retry automatico.
     */
    public <T> T executeWithRetry(DatabaseOperation<T> operation) throws SQLException {
        SQLException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Connection conn = getConnection();
                return operation.execute(conn);
            } catch (SQLException e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                boolean isConnectionError = msg.contains("communications link failure")
                        || msg.contains("connection reset")
                        || msg.contains("broken pipe")
                        || msg.contains("socket")
                        || msg.contains("timeout")
                        || msg.contains("no operations allowed after connection closed")
                        || msg.contains("connection is not available")
                        || msg.contains("could not create connection");

                if (isConnectionError && attempt < MAX_RETRIES) {
                    Log.w(TAG, "Erro de conexao na tentativa " + attempt + ", reconectando: " + e.getMessage());
                    closeConnectionQuietly();
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Operacao interrompida", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw lastException != null ? lastException : new SQLException("Falha apos retries");
    }

    /**
     * Interface funcional para operacoes de banco de dados com retry.
     */
    public interface DatabaseOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Versao void do executeWithRetry para operacoes sem retorno.
     */
    public void executeWithRetryVoid(VoidDatabaseOperation operation) throws SQLException {
        executeWithRetry(conn -> {
            operation.execute(conn);
            return null;
        });
    }

    /**
     * Interface funcional para operacoes void de banco de dados.
     */
    public interface VoidDatabaseOperation {
        void execute(Connection conn) throws SQLException;
    }

    /**
     * Marca o banco como inicializado externamente (usado pelo cache da splash).
     * Evita que createTables() refaca a verificacao quando ja foi feita.
     */
    public void markAsInitialized() {
        databaseInitialized = true;
    }

    // =========================================================================
    // SISTEMA DE AUTO-CRIACAO E AUTO-REPARO DO BANCO DE DADOS
    // Garante que o banco, tabelas e colunas existam na inicializacao
    // =========================================================================

    /**
     * Classe auxiliar para definir uma coluna do banco de dados.
     */
    private static class ColumnDef {
        String name;
        String type;
        String defaultValue; // null = sem default, "NULL" = DEFAULT NULL

        ColumnDef(String name, String type, String defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        String toCreateSQL() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" ").append(type);
            if (defaultValue != null) {
                sb.append(" DEFAULT ").append(defaultValue);
            }
            return sb.toString();
        }

        String toAlterSQL() {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append(" ").append(type);
            if (defaultValue != null) {
                sb.append(" DEFAULT ").append(defaultValue);
            }
            return sb.toString();
        }
    }

    /**
     * Classe auxiliar para definir uma tabela do banco de dados.
     */
    private static class TableDef {
        String name;
        List<ColumnDef> columns;

        TableDef(String name) {
            this.name = name;
            this.columns = new ArrayList<>();
        }

        TableDef col(String name, String type, String defaultValue) {
            columns.add(new ColumnDef(name, type, defaultValue));
            return this;
        }

        TableDef col(String name, String type) {
            columns.add(new ColumnDef(name, type, null));
            return this;
        }
    }

    /**
     * Retorna a definicao completa de TODAS as tabelas do sistema.
     * Esta e a UNICA fonte de verdade para a estrutura do banco.
     * Inclui tabelas de dados E tabelas do sistema de permissoes.
     */
    private List<TableDef> getTableDefinitions() {
        List<TableDef> tables = new ArrayList<>();

        // Tabela: empresa
        tables.add(new TableDef("empresa")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("razao_social", "VARCHAR(200) NOT NULL")
                .col("nome_fantasia", "VARCHAR(200)", "NULL")
                .col("cnpj", "VARCHAR(20)", "NULL")
                .col("ie", "VARCHAR(20)", "NULL")
                .col("endereco", "VARCHAR(300)", "NULL")
                .col("numero", "VARCHAR(20)", "NULL")
                .col("bairro", "VARCHAR(100)", "NULL")
                .col("cidade", "VARCHAR(100)", "NULL")
                .col("uf", "VARCHAR(2)", "NULL")
                .col("cep", "VARCHAR(10)", "NULL")
                .col("telefone", "VARCHAR(20)", "NULL")
                .col("email", "VARCHAR(100)", "NULL")
        );

        // Tabela: perfis (DEVE ser criada ANTES de usuarios por causa da FK)
        tables.add(new TableDef("perfis")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("descricao", "VARCHAR(255)", "NULL")
                .col("sistematico", "TINYINT(1)", "0")
                .col("personalizavel", "TINYINT(1)", "0")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("atualizado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: usuarios (com coluna perfil_id para sistema de permissoes)
        tables.add(new TableDef("usuarios")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("login", "VARCHAR(50) NOT NULL")
                .col("senha", "VARCHAR(100) NOT NULL")
                .col("nivel", "VARCHAR(20)", "'operador'")
                .col("ativo", "TINYINT(1)", "1")
                .col("perfil_id", "INT", "NULL")
        );

        // Tabela: usuarios_biometria (vinculos ilimitados de digital por usuario)
        tables.add(new TableDef("usuarios_biometria")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("usuario_id", "INT NOT NULL")
                .col("descricao", "VARCHAR(100) NOT NULL")
                .col("login", "VARCHAR(50) NOT NULL")
                .col("senha", "VARCHAR(100) NOT NULL")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("ultimo_uso", "DATETIME", "NULL")
        );

        // Tabela: permissoes
        tables.add(new TableDef("permissoes")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("modulo", "VARCHAR(100) NOT NULL")
                .col("acao", "VARCHAR(100) NOT NULL")
                .col("chave", "VARCHAR(100) NOT NULL")
                .col("descricao", "VARCHAR(255)", "NULL")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: perfil_permissoes (vinculacao N:N entre perfis e permissoes)
        tables.add(new TableDef("perfil_permissoes")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("perfil_id", "INT NOT NULL")
                .col("permissao_id", "INT NOT NULL")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: usuario_permissoes (excecoes de permissao por usuario)
        tables.add(new TableDef("usuario_permissoes")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("usuario_id", "INT NOT NULL")
                .col("permissao_id", "INT NOT NULL")
                .col("tipo", "VARCHAR(20) NOT NULL", "'adicionar'")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("criado_por", "INT", "NULL")
        );

        // Tabela: tipos_produto
        tables.add(new TableDef("tipos_produto")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("descricao", "VARCHAR(100) NOT NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: produtos
        tables.add(new TableDef("produtos")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("codigo", "VARCHAR(50)", "NULL")
                .col("descricao", "VARCHAR(200) NOT NULL")
                .col("unidade", "VARCHAR(10)", "'UN'")
                .col("tipo_produto_id", "INT", "NULL")
                .col("preco_custo", "DECIMAL(10,2)", "0")
                .col("preco_venda", "DECIMAL(10,2)", "0")
                .col("estoque", "DECIMAL(10,3)", "0")
                .col("estoque_minimo", "DECIMAL(10,3)", "0")
                .col("codigo_barras", "VARCHAR(100)", "NULL")
                .col("foto_base64", "LONGTEXT", "NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: clientes
        tables.add(new TableDef("clientes")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(200) NOT NULL")
                .col("cpf_cnpj", "VARCHAR(20)", "NULL")
                .col("celular", "VARCHAR(20)", "NULL")
                .col("endereco", "VARCHAR(300)", "NULL")
                .col("numero", "VARCHAR(20)", "NULL")
                .col("bairro", "VARCHAR(100)", "NULL")
                .col("cidade", "VARCHAR(100)", "NULL")
                .col("uf", "VARCHAR(2)", "NULL")
                .col("cep", "VARCHAR(10)", "NULL")
                .col("email", "VARCHAR(100)", "NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: vendedores
        tables.add(new TableDef("vendedores")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("celular", "VARCHAR(20)", "NULL")
                .col("comissao", "DECIMAL(5,2)", "0")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: entregadores
        tables.add(new TableDef("entregadores")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("celular", "VARCHAR(20)", "NULL")
                .col("veiculo", "VARCHAR(100)", "NULL")
                .col("modelo", "VARCHAR(100)", "NULL")
                .col("placa", "VARCHAR(20)", "NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: formas_pagamento
        tables.add(new TableDef("formas_pagamento")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("descricao", "VARCHAR(100) NOT NULL")
                .col("tipo", "VARCHAR(20)", "'dinheiro'")
                .col("permite_parcelamento", "TINYINT(1)", "0")
                .col("exige_cliente", "TINYINT(1)", "0")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: observacoes_cupom
        tables.add(new TableDef("observacoes_cupom")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("texto", "TEXT", "NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: caixa
        tables.add(new TableDef("caixa")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("usuario_id", "INT", "NULL")
                .col("caixa_nominal_id", "INT", "NULL")
                .col("data_abertura", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_fechamento", "DATETIME", "NULL")
                .col("valor_abertura", "DECIMAL(10,2)", "0")
                .col("valor_fechamento", "DECIMAL(10,2)", "0")
                .col("status", "VARCHAR(20)", "'aberto'")
                .col("observacao", "TEXT")
                .col("relatorio_fechamento", "LONGTEXT", "NULL")
        );

        // Tabela: vendas
        tables.add(new TableDef("vendas")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("cliente_id", "INT", "NULL")
                .col("vendedor_id", "INT", "NULL")
                .col("entregador_id", "INT", "NULL")
                .col("caixa_id", "INT", "NULL")
                .col("data_venda", "DATETIME", "CURRENT_TIMESTAMP")
                .col("total_bruto", "DECIMAL(10,2)", "0")
                .col("desconto_tipo", "VARCHAR(20)", "NULL")
                .col("desconto_valor", "DECIMAL(10,2)", "0")
                .col("acrescimo_tipo", "VARCHAR(20)", "NULL")
                .col("acrescimo_valor", "DECIMAL(10,2)", "0")
                .col("total_liquido", "DECIMAL(10,2)", "0")
                .col("valor_recebido", "DECIMAL(10,2)", "0")
                .col("troco", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT")
                .col("status", "VARCHAR(20)", "'finalizada'")
                .col("celular_whatsapp", "VARCHAR(30)", "NULL")
                .col("para_entrega", "TINYINT(1)", "0")
                .col("taxa_entrega", "DECIMAL(10,2)", "0")
                .col("bairro_entrega", "VARCHAR(150)", "NULL")
                .col("endereco_entrega", "VARCHAR(500)", "NULL")
                .col("uso_armario_id", "INT", "0")
                .col("garcom_id", "INT", "NULL")
                .col("comissao_percentual", "DECIMAL(7,4)", "NULL")
                .col("comissao_valor", "DECIMAL(10,2)", "NULL")
                .col("garcom_percentual", "DECIMAL(7,4)", "NULL")
                .col("garcom_valor", "DECIMAL(10,2)", "NULL")
        );

        // Tabela: itens_venda
        tables.add(new TableDef("itens_venda")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("venda_id", "INT", "NULL")
                .col("produto_id", "INT", "NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("desconto", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
        );

        // Tabela: pagamentos_venda
        tables.add(new TableDef("pagamentos_venda")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("venda_id", "INT", "NULL")
                .col("forma_pagamento_id", "INT", "NULL")
                .col("valor", "DECIMAL(10,2)", "0")
                .col("parcelas", "INT", "1")
                .col("bandeira", "VARCHAR(50)", "NULL")
        );

        // Tabela: centros_custo
        tables.add(new TableDef("centros_custo")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(120) NOT NULL")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: vales_debito
        tables.add(new TableDef("vales_debito")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("caixa_id", "INT", "NULL")
                .col("centro_custo_id", "INT", "NULL")
                .col("usuario_id", "INT", "NULL")
                .col("descricao", "VARCHAR(200)", "NULL")
                .col("valor", "DECIMAL(10,2)", "0")
                .col("data", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: comandas
        tables.add(new TableDef("comandas")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero", "INT NOT NULL")
                .col("cliente_id", "INT", "0")
                .col("vendedor_id", "INT", "0")
                .col("caixa_id", "INT", "0")
                .col("data_abertura", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_fechamento", "DATETIME", "NULL")
                .col("total_itens", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT")
                .col("status", "VARCHAR(20)", "'aberta'")
        );

        // Tabela: itens_comanda
        tables.add(new TableDef("itens_comanda")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("comanda_id", "INT", "NULL")
                .col("produto_id", "INT", "NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT")
                .col("data_hora", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: licenca
        tables.add(new TableDef("licenca")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("contra_chave", "VARCHAR(100)", "NULL")
                .col("chave_ativacao", "VARCHAR(200)", "NULL")
                .col("data_ativacao", "DATETIME", "NULL")
                .col("data_expiracao", "DATETIME", "NULL")
        );

        // Tabela: rastreamento_entregador
        tables.add(new TableDef("rastreamento_entregador")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("entregador_id", "INT NOT NULL")
                .col("latitude", "DOUBLE", "0")
                .col("longitude", "DOUBLE", "0")
                .col("velocidade", "FLOAT", "0")
                .col("precisao", "FLOAT", "0")
                .col("data_hora", "DATETIME", "CURRENT_TIMESTAMP")
                .col("ativo", "TINYINT(1)", "0")
        );

        // Tabela: historico_localizacao
        tables.add(new TableDef("historico_localizacao")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("entregador_id", "INT NOT NULL")
                .col("latitude", "DOUBLE", "0")
                .col("longitude", "DOUBLE", "0")
                .col("velocidade", "FLOAT", "0")
                .col("precisao", "FLOAT", "0")
                .col("data_hora", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: contas_receber
        tables.add(new TableDef("contas_receber")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("cliente_id", "INT NOT NULL")
                .col("venda_id", "INT", "NULL")
                .col("valor_original", "DECIMAL(10,2)", "0")
                .col("valor_pago", "DECIMAL(10,2)", "0")
                .col("valor_pendente", "DECIMAL(10,2)", "0")
                .col("data_venda", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_vencimento", "DATETIME", "NULL")
                .col("data_pagamento", "DATETIME", "NULL")
                .col("status", "VARCHAR(20)", "'pendente'")
                .col("observacao", "TEXT")
        );

        // Tabela: recebimentos_conta
        tables.add(new TableDef("recebimentos_conta")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("conta_receber_id", "INT NOT NULL")
                .col("valor", "DECIMAL(10,2)", "0")
                .col("data_recebimento", "DATETIME", "CURRENT_TIMESTAMP")
                .col("forma_pagamento", "VARCHAR(50)", "NULL")
                .col("observacao", "TEXT")
        );

        // Tabela: fornecedores
        tables.add(new TableDef("fornecedores")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(200) NOT NULL")
                .col("razao_social", "VARCHAR(200)", "NULL")
                .col("documento", "VARCHAR(30)", "NULL")
                .col("contato", "VARCHAR(150)", "NULL")
                .col("telefone", "VARCHAR(30)", "NULL")
                .col("email", "VARCHAR(200)", "NULL")
                .col("endereco", "VARCHAR(300)", "NULL")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("atualizado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: notas_entrada
        tables.add(new TableDef("notas_entrada")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero_nota", "VARCHAR(100)", "NULL")
                .col("fornecedor_id", "INT", "NULL")
                .col("fornecedor", "VARCHAR(200) NOT NULL")
                .col("data_entrada", "DATETIME", "CURRENT_TIMESTAMP")
                .col("total_nota", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT")
                .col("status", "VARCHAR(20)", "'pendente'")
                .col("usuario_id", "INT", "NULL")
                .col("condicao_pagamento", "VARCHAR(100)", "NULL")
                .col("datas_vencimento", "TEXT", "NULL")
                .col("caixa_nominal_id", "INT", "NULL")
        );

        // Tabela: itens_nota_entrada
        tables.add(new TableDef("itens_nota_entrada")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nota_entrada_id", "INT NOT NULL")
                .col("produto_id", "INT NOT NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "0")
                .col("custo_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
        );

        // Tabela: adicionais
        tables.add(new TableDef("adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("descricao", "VARCHAR(200) NOT NULL")
                .col("preco", "DECIMAL(10,2)", "0")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: tipo_produto_adicionais
        tables.add(new TableDef("tipo_produto_adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("tipo_produto_id", "INT NOT NULL")
                .col("adicional_id", "INT NOT NULL")
        );

        // Tabela: itens_venda_adicionais
        tables.add(new TableDef("itens_venda_adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("item_venda_id", "INT NOT NULL")
                .col("adicional_id", "INT NOT NULL")
                .col("descricao_adicional", "VARCHAR(200)", "NULL")
                .col("preco", "DECIMAL(10,2)", "0")
        );

        // Tabela: taxa_entrega_bairro
        tables.add(new TableDef("taxa_entrega_bairro")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("bairro", "VARCHAR(150) NOT NULL")
                .col("taxa", "DECIMAL(10,2)", "0")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: cliente_bairro_whatsapp
        tables.add(new TableDef("cliente_bairro_whatsapp")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("celular_whatsapp", "VARCHAR(30) NOT NULL")
                .col("bairro_id", "INT NOT NULL")
                .col("data_atualizacao", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: garcons
        tables.add(new TableDef("garcons")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(150) NOT NULL")
                .col("celular", "VARCHAR(30)", "NULL")
                .col("porcentagem", "DECIMAL(7,4)", "0")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Agenda de servicos com alerta de proximidade.
        tables.add(new TableDef("agenda_servicos")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("titulo", "VARCHAR(200) NOT NULL")
                .col("cliente_id", "INT", "NULL")
                .col("servico_id", "INT", "NULL")
                .col("data_hora", "DATETIME NOT NULL")
                .col("duracao_minutos", "INT", "60")
                .col("alerta_minutos", "INT", "30")
                .col("observacao", "TEXT", "NULL")
                .col("status", "VARCHAR(20)", "'agendado'")
                .col("alertado", "TINYINT(1)", "0")
                .col("usuario_id", "INT", "NULL")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Auditoria central: os triggers registram INSERT/UPDATE/DELETE e o
        // BaseActivity registra navegacao entre telas.
        tables.add(new TableDef("auditoria_acoes")
                .col("id", "BIGINT AUTO_INCREMENT PRIMARY KEY")
                .col("usuario_id", "INT", "NULL")
                .col("usuario_nome", "VARCHAR(150)", "NULL")
                .col("acao", "VARCHAR(30) NOT NULL")
                .col("modulo", "VARCHAR(100)", "NULL")
                .col("tabela", "VARCHAR(100)", "NULL")
                .col("registro_id", "BIGINT", "NULL")
                .col("detalhes", "TEXT", "NULL")
                .col("ip", "VARCHAR(45)", "NULL")
                .col("data_acao", "DATETIME", "CURRENT_TIMESTAMP")
        );

        tables.add(new TableDef("migracoes_sistema")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("chave", "VARCHAR(100) NOT NULL")
                .col("executada_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("detalhes", "VARCHAR(500)", "NULL")
        );

        // Tabela: mesas
        tables.add(new TableDef("mesas")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero", "INT NOT NULL")
                .col("descricao", "VARCHAR(200)", "NULL")
                .col("capacidade", "INT", "4")
                .col("ativa", "TINYINT(1)", "1")
        );

        // Tabela: ocupacao_mesa
        tables.add(new TableDef("ocupacao_mesa")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("mesa_id", "INT NOT NULL")
                .col("garcom_id", "INT", "0")
                .col("qtd_pessoas", "INT", "0")
                .col("status", "VARCHAR(20)", "'livre'")
                .col("data_abertura", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_fechamento", "DATETIME", "NULL")
                .col("reservado_por_usuario_id", "INT", "0")
                .col("reservado_por_usuario_nome", "VARCHAR(150)", "NULL")
        );

        // Tabela: itens_mesa
        tables.add(new TableDef("itens_mesa")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("ocupacao_id", "INT NOT NULL")
                .col("produto_id", "INT NOT NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
                .col("adicionais_descricao", "VARCHAR(500)", "NULL")
                .col("adicionais_total", "DECIMAL(10,2)", "0")
                .col("observacao_cozinha", "VARCHAR(255)", "NULL")
                .col("impresso", "TINYINT(1)", "0")
        );

        // Tabela: itens_mesa_adicionais
        tables.add(new TableDef("itens_mesa_adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("item_mesa_id", "INT NOT NULL")
                .col("adicional_id", "INT NOT NULL")
                .col("descricao_adicional", "VARCHAR(200)", "NULL")
                .col("preco", "DECIMAL(10,2)", "0")
        );

        // =====================================================================
        // v6.9.5 - MODULO DE ARMARIOS PARA SAUNA
        // =====================================================================

        // Tabela: armarios_sauna (cadastro de armarios fisicos)
        tables.add(new TableDef("armarios_sauna")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero", "INT NOT NULL")
                .col("descricao", "VARCHAR(200)", "NULL")
                .col("localizacao", "VARCHAR(100)", "NULL")
                .col("ativo", "TINYINT(1)", "1")
        );

        // Tabela: uso_armario_sauna (controle de uso/ocupacao dos armarios)
        tables.add(new TableDef("uso_armario_sauna")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("armario_id", "INT NOT NULL")
                .col("cliente_nome", "VARCHAR(200)", "NULL")
                .col("observacao", "VARCHAR(500)", "NULL")
                .col("status", "VARCHAR(20)", "'ocupado'")
                .col("data_entrada", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_saida", "DATETIME", "NULL")
        );

        // Tabela: itens_armario_sauna (consumo vinculado ao uso do armario)
        tables.add(new TableDef("itens_armario_sauna")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("uso_armario_id", "INT NOT NULL")
                .col("produto_id", "INT NOT NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
                .col("adicionais_descricao", "VARCHAR(500)", "NULL")
                .col("adicionais_total", "DECIMAL(10,2)", "0")
                .col("observacao_cozinha", "VARCHAR(255)", "NULL")
                .col("impresso", "TINYINT(1)", "0")
        );

        // Tabela: itens_armario_sauna_adicionais (adicionais dos itens de consumo do armario)
        tables.add(new TableDef("itens_armario_sauna_adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("item_armario_id", "INT NOT NULL")
                .col("adicional_id", "INT NOT NULL")
                .col("descricao_adicional", "VARCHAR(200)", "NULL")
                .col("preco", "DECIMAL(10,2)", "0")
        );


        // Tabela: estacionamento - controle profissional de entrada, saida, vaga e cobranca
        tables.add(new TableDef("estacionamento")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("ticket", "VARCHAR(40) NOT NULL")
                .col("placa", "VARCHAR(20) NOT NULL")
                .col("veiculo", "VARCHAR(120)", "NULL")
                .col("condutor", "VARCHAR(120)", "NULL")
                .col("telefone", "VARCHAR(30)", "NULL")
                .col("vaga", "VARCHAR(30)", "NULL")
                .col("tipo", "VARCHAR(40)", "'CARRO'")
                .col("entrada", "DATETIME NOT NULL")
                .col("saida", "DATETIME", "NULL")
                .col("status", "VARCHAR(20)", "'ABERTO'")
                .col("valor_hora", "DECIMAL(10,2)", "0")
                .col("valor_total", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
                .col("atualizado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: ordens_servico
        tables.add(new TableDef("ordens_servico")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero", "INT NOT NULL")
                .col("cliente_nome", "VARCHAR(200) NOT NULL")
                .col("equipamento", "VARCHAR(200) NOT NULL")
                .col("defeito_relatado", "VARCHAR(500)", "NULL")
                .col("status", "VARCHAR(50)", "'Aberta'")
                .col("valor_servico", "DECIMAL(10,2)", "0")
                .col("observacao", "VARCHAR(1000)", "NULL")
                .col("servico_id", "INT", "NULL")
                .col("cliente_id", "INT", "NULL")
                .col("produto_id", "INT", "NULL")
                .col("venda_id", "INT", "NULL")
                .col("desconto_valor", "DECIMAL(10,2)", "0")
                .col("desconto_percentual", "DECIMAL(10,2)", "0")
                .col("data_abertura", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_atualizacao", "DATETIME", "CURRENT_TIMESTAMP")
                .col("ativo", "TINYINT(1)", "1")
                // v8.0.8 - Rastreamento de usuarios responsaveis
                .col("usuario_abertura_id", "INT", "NULL")
                .col("usuario_abertura_nome", "VARCHAR(100)", "NULL")
                .col("usuario_fechamento_id", "INT", "NULL")
                .col("usuario_fechamento_nome", "VARCHAR(100)", "NULL")
                // v8.0.9 - Campos de defeitos e solucoes
                .col("defeitos", "TEXT", "NULL")
                .col("solucoes", "TEXT", "NULL")
                // v8.0.12 - Campo detalhado do equipamento/produto recebido
                .col("equipamento_detalhado", "TEXT", "NULL")
        );

        // Tabela: servicos (Cadastro de Servicos - v8.0.0)
        tables.add(new TableDef("servicos")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(200) NOT NULL")
                .col("descricao", "VARCHAR(500)", "NULL")
                .col("valor", "DECIMAL(10,2)", "0")
                .col("ativo", "TINYINT(1)", "1")
                .col("data_cadastro", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_atualizacao", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: os_itens - Itens (servicos e produtos) vinculados a uma OS (v8.0.6)
        tables.add(new TableDef("os_itens")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("os_id", "INT NOT NULL")
                .col("tipo", "VARCHAR(20) NOT NULL")
                .col("ref_id", "INT", "NULL")
                .col("descricao", "VARCHAR(300) NOT NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
                .col("data_cadastro", "DATETIME", "CURRENT_TIMESTAMP")
                // v8.0.8 - Rastreamento do usuario que adicionou o item
                .col("usuario_id", "INT", "NULL")
                .col("usuario_nome", "VARCHAR(100)", "NULL")
        );

        // v8.0.10 - Tabela de fotos das Ordens de Servico
        tables.add(new TableDef("os_fotos")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("os_id", "INT NOT NULL")
                .col("caminho_foto", "TEXT NOT NULL")
                .col("ftp_caminho", "TEXT", "NULL")
                .col("ftp_status", "VARCHAR(30)", "'pendente'")
                .col("tamanho_bytes", "BIGINT", "0")
                .col("data_sync_ftp", "DATETIME", "NULL")
                .col("descricao", "VARCHAR(500)", "NULL")
                .col("data_adicao", "DATETIME", "CURRENT_TIMESTAMP")
                .col("usuario_id", "INT", "NULL")
                .col("usuario_nome", "VARCHAR(100)", "NULL")
        );

        // =====================================================================
        // v8.0.12.1 - Tabelas web/chamados garantidas na migracao inicial
        // Antes estas tabelas eram criadas somente quando o servidor web abria.
        // Agora entram no preflight para bancos antigos iniciarem completos.
        // =====================================================================
        tables.add(new TableDef("chamados")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero_chamado", "INT NOT NULL")
                .col("cliente_nome", "VARCHAR(200) NOT NULL")
                .col("comanda_numero", "INT", "0")
                .col("tipo", "VARCHAR(50)", "'comanda'")
                .col("descricao", "TEXT", "NULL")
                .col("status", "VARCHAR(30)", "'aguardando'")
                .col("prioridade", "VARCHAR(20)", "'normal'")
                .col("data_criacao", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_chamada", "DATETIME", "NULL")
                .col("data_atendido", "DATETIME", "NULL")
                .col("vezes_chamado", "INT", "0")
                .col("observacao", "TEXT", "NULL")
                .col("atendente", "VARCHAR(100)", "''")
        );

        tables.add(new TableDef("pedidos_web")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("numero_pedido", "INT NOT NULL")
                .col("mesa_numero", "INT NOT NULL")
                .col("cliente_nome", "VARCHAR(200) NOT NULL")
                .col("observacao", "TEXT", "NULL")
                .col("total", "DECIMAL(10,2)", "0")
                .col("status", "VARCHAR(30)", "'pendente'")
                .col("data_criacao", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_preparo", "DATETIME", "NULL")
                .col("data_pronto", "DATETIME", "NULL")
                .col("data_entregue", "DATETIME", "NULL")
        );

        tables.add(new TableDef("itens_pedido_web")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("pedido_web_id", "INT NOT NULL")
                .col("produto_id", "INT NOT NULL")
                .col("descricao_produto", "VARCHAR(200)", "NULL")
                .col("quantidade", "DECIMAL(10,3)", "1")
                .col("preco_unitario", "DECIMAL(10,2)", "0")
                .col("total", "DECIMAL(10,2)", "0")
                .col("observacao", "TEXT", "NULL")
        );

        tables.add(new TableDef("itens_pedido_web_adicionais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("item_pedido_web_id", "INT NOT NULL")
                .col("adicional_id", "INT NOT NULL")
                .col("descricao_adicional", "VARCHAR(200)", "NULL")
                .col("preco", "DECIMAL(10,2)", "0")
        );

        // Tabela: caixas_nominais (caixas criados pelo usuario)
        tables.add(new TableDef("caixas_nominais")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("descricao", "TEXT", "NULL")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: turnos
        tables.add(new TableDef("turnos")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("nome", "VARCHAR(100) NOT NULL")
                .col("hora_inicio", "VARCHAR(10)", "NULL")
                .col("hora_fim", "VARCHAR(10)", "NULL")
                .col("descricao", "TEXT", "NULL")
                .col("ativo", "TINYINT(1)", "1")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: usuario_caixa_turno (vinculo usuario-caixa-turno)
        tables.add(new TableDef("usuario_caixa_turno")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("usuario_id", "INT NOT NULL")
                .col("caixa_nominal_id", "INT", "NULL")
                .col("turno_id", "INT", "NULL")
                .col("criado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        // Tabela: contas_pagar
        tables.add(new TableDef("contas_pagar")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("descricao", "VARCHAR(255) NOT NULL")
                .col("fornecedor_id", "INT", "NULL")
                .col("fornecedor_nome", "VARCHAR(200)", "NULL")
                .col("nota_entrada_id", "INT", "NULL")
                .col("valor_total", "DECIMAL(10,2)", "0")
                .col("valor_pago", "DECIMAL(10,2)", "0")
                .col("valor_pendente", "DECIMAL(10,2)", "0")
                .col("data_emissao", "DATETIME", "CURRENT_TIMESTAMP")
                .col("data_vencimento", "DATETIME", "NULL")
                .col("data_pagamento", "DATETIME", "NULL")
                .col("status", "VARCHAR(20)", "'pendente'")
                .col("forma_pagamento", "VARCHAR(100)", "NULL")
                .col("caixa_nominal_id", "INT", "NULL")
                .col("parcela_numero", "INT", "1")
                .col("total_parcelas", "INT", "1")
                .col("observacao", "TEXT", "NULL")
                .col("usuario_id", "INT", "NULL")
        );

        // Tabela: pagamentos_conta_pagar
        tables.add(new TableDef("pagamentos_conta_pagar")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("conta_pagar_id", "INT NOT NULL")
                .col("valor", "DECIMAL(10,2)", "0")
                .col("forma_pagamento", "VARCHAR(100)", "NULL")
                .col("caixa_nominal_id", "INT", "NULL")
                .col("data_pagamento", "DATETIME", "CURRENT_TIMESTAMP")
                .col("observacao", "TEXT", "NULL")
                .col("usuario_id", "INT", "NULL")
        );

        // Controle interno de migracoes executadas.
        tables.add(new TableDef("schema_migrations_pdv")
                .col("id", "INT AUTO_INCREMENT PRIMARY KEY")
                .col("versao", "VARCHAR(50) NOT NULL")
                .col("descricao", "VARCHAR(255)", "NULL")
                .col("executado_em", "DATETIME", "CURRENT_TIMESTAMP")
        );

        return tables;
    }

    /**
     * Verifica se uma tabela existe no banco de dados.
     */
    private boolean tableExists(Connection conn, String tableName) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(database, null, tableName, new String[]{"TABLE"});
            boolean exists = rs.next();
            rs.close();
            return exists;
        } catch (Exception e) {
            // Fallback: tenta via INFORMATION_SCHEMA
            try {
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?");
                ps.setString(1, database);
                ps.setString(2, tableName);
                ResultSet rs = ps.executeQuery();
                boolean exists = false;
                if (rs.next()) exists = rs.getInt(1) > 0;
                rs.close();
                ps.close();
                return exists;
            } catch (Exception e2) {
                Log.e(TAG, "Erro ao verificar existencia da tabela " + tableName, e2);
                return false;
            }
        }
    }

    /**
     * Verifica se uma coluna existe em uma tabela.
     */
    private boolean columnExists(Connection conn, String tableName, String columnName) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?");
            ps.setString(1, database);
            ps.setString(2, tableName);
            ps.setString(3, columnName);
            ResultSet rs = ps.executeQuery();
            boolean exists = false;
            if (rs.next()) exists = rs.getInt(1) > 0;
            rs.close();
            ps.close();
            return exists;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar coluna " + columnName + " na tabela " + tableName, e);
            return false;
        }
    }

    /**
     * Obtem a lista de colunas existentes em uma tabela.
     */
    private List<String> getExistingColumns(Connection conn, String tableName) {
        List<String> columns = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION");
            ps.setString(1, database);
            ps.setString(2, tableName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter colunas da tabela " + tableName, e);
        }
        return columns;
    }

    /**
     * Cria uma tabela completa a partir da definicao.
     */
    private void createTable(Statement stmt, TableDef table) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(table.name).append(" (");
        for (int i = 0; i < table.columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(table.columns.get(i).toCreateSQL());
        }
        sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        stmt.executeUpdate(sql.toString());
        Log.d(TAG, "Tabela criada: " + table.name);
    }

    /**
     * Adiciona uma coluna faltante a uma tabela existente.
     */
    private void addMissingColumn(Statement stmt, String tableName, ColumnDef column) {
        try {
            // Nao tenta adicionar PRIMARY KEY como coluna separada
            if (column.type.toUpperCase().contains("PRIMARY KEY")) {
                return;
            }
            String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + column.toAlterSQL();
            stmt.executeUpdate(sql);
            Log.d(TAG, "Coluna adicionada: " + tableName + "." + column.name);
        } catch (SQLException e) {
            // Coluna ja pode existir com nome diferente ou tipo incompativel
            Log.w(TAG, "Aviso ao adicionar coluna " + tableName + "." + column.name + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // METODO PRINCIPAL: INICIALIZACAO COMPLETA DO BANCO DE DADOS
    // =========================================================================

    /**
     * Callback para reportar progresso da inicializacao do banco.
     */
    public interface InitProgressCallback {
        void onProgress(String message);
    }

    /**
     * METODO PRINCIPAL DE INICIALIZACAO DO BANCO DE DADOS.
     *
     * v8.0.23.0 - Otimizado com:
     * - Verificacao de schema_version para detectar bancos antigos
     * - Carregamento em batch de tabelas e colunas (1 query por tipo)
     * - Registro de versao do schema apos migracao bem-sucedida
     * - Relatorio detalhado de todas as acoes realizadas
     *
     * @param callback Callback opcional para reportar progresso (pode ser null)
     * @return Relatorio detalhado das acoes realizadas
     */
    public synchronized String initializeDatabase(InitProgressCallback callback) {
        StringBuilder report = new StringBuilder();
        int tablesCreated = 0;
        int columnsAdded = 0;

        try {
            // PASSO 1: Verificar/criar o banco de dados (DATABASE)
            if (callback != null) callback.onProgress("Verificando banco de dados...");
            report.append("=== Inicializacao do Banco de Dados PDV Pro v8.0.23.2 ===\n");
            report.append("Servidor: ").append(host).append(":").append(port).append("\n");
            report.append("Banco: ").append(database).append("\n\n");

            boolean dbCreated = ensureDatabaseExists();
            if (dbCreated) {
                report.append("[CRIADO] Banco de dados '").append(database).append("' criado\n");
            } else {
                report.append("[OK] Banco de dados '").append(database).append("' ja existe\n");
            }

            // PASSO 2: Conectar ao banco
            if (callback != null) callback.onProgress("Conectando ao banco...");
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();

            // PASSO 3: Verificar se precisa de migracao (banco antigo)
            boolean bancoAntigo = precisaMigracao(conn);
            if (bancoAntigo) {
                report.append("[INFO] Banco antigo detectado - executando migracao completa\n");
                if (callback != null) callback.onProgress("Banco antigo detectado, atualizando estrutura...");
            } else {
                report.append("[OK] Schema version " + SCHEMA_VERSION + " - banco atualizado\n");
            }

            // PASSO 4: Carregar tabelas e colunas existentes em BATCH (1 query cada)
            if (callback != null) callback.onProgress("Verificando estrutura do banco...");
            java.util.Set<String> tabelasExistentes = carregarTabelasExistentes(conn);
            Map<String, List<String>> colunasExistentes = carregarColunasExistentes(conn);

            List<TableDef> tables = getTableDefinitions();

            for (TableDef table : tables) {
                String tableNameLower = table.name.toLowerCase();
                if (!tabelasExistentes.contains(tableNameLower)) {
                    // Tabela nao existe - criar completa
                    if (callback != null) callback.onProgress("Criando tabela: " + table.name + "...");
                    createTable(stmt, table);
                    tablesCreated++;
                    report.append("[CRIADO] Tabela: ").append(table.name).append("\n");
                    // Adicionar ao mapa para evitar verificacao dupla
                    tabelasExistentes.add(tableNameLower);
                    colunasExistentes.put(tableNameLower, new ArrayList<>());
                } else {
                    // Tabela existe - verificar colunas faltantes usando cache
                    List<String> existingCols = colunasExistentes.getOrDefault(tableNameLower, new ArrayList<>());
                    boolean tableOk = true;
                    for (ColumnDef col : table.columns) {
                        if (col.name.equals("id")) continue;
                        if (!existingCols.contains(col.name.toLowerCase())) {
                            if (callback != null) callback.onProgress("Adicionando coluna: " + table.name + "." + col.name + "...");
                            addMissingColumn(stmt, table.name, col);
                            columnsAdded++;
                            report.append("[ADICIONADO] Coluna: ").append(table.name).append(".").append(col.name).append("\n");
                            existingCols.add(col.name.toLowerCase());
                            tableOk = false;
                        }
                    }
                    if (tableOk) {
                        report.append("[OK] Tabela: ").append(table.name).append("\n");
                    }
                }
            }

            // PASSO 5: Criar indices unicos necessarios
            if (callback != null) callback.onProgress("Verificando indices...");
            ensureIndexes(stmt, report);

            if (callback != null) callback.onProgress("Atualizando charset e triggers de auditoria...");
            ensureUtf8mb4(conn, stmt, report);
            ensureAuditTriggers(conn, report);

            // PASSO 6: Inserir dados padrao
            if (callback != null) callback.onProgress("Verificando dados padrao...");
            insertDefaultData(stmt);
            report.append("\n[OK] Dados padrao verificados\n");

            // PASSO 7: Registrar versao do schema
            registrarVersaoSchema(conn);
            report.append("[OK] Schema version " + SCHEMA_VERSION + " registrado\n");

            stmt.close();

            report.append("\n=== Resultado ===\n");
            report.append("Tabelas criadas: ").append(tablesCreated).append("\n");
            report.append("Colunas adicionadas: ").append(columnsAdded).append("\n");
            report.append("Banco antigo migrado: ").append(bancoAntigo ? "SIM" : "NAO").append("\n");
            report.append("Inicializacao concluida com sucesso!\n");

            databaseInitialized = true;
            Log.d(TAG, "Inicializacao do banco concluida: " + tablesCreated + " tabelas criadas, "
                    + columnsAdded + " colunas adicionadas, banco_antigo=" + bancoAntigo);

        } catch (Exception e) {
            String errorMsg = "Erro na inicializacao do banco: " + e.getMessage();
            report.append("\n[ERRO] ").append(errorMsg).append("\n");
            Log.e(TAG, errorMsg, e);
        }

        return report.toString();
    }

    /**
     * Migracao inicial obrigatoria.
     *
     * v8.0.23.0 - Agora verifica schema_version para detectar bancos antigos
     * e migrar automaticamente ANTES de qualquer operacao ou tela.
     * Executa a verificacao completa do banco real antes do sistema liberar
     * qualquer outra tela ou operacao. Nao respeita cache de inicializacao:
     * sempre confere banco, tabelas, colunas, indices e dados padrao.
     */
    public synchronized String executarMigracaoInicialObrigatoria(InitProgressCallback callback) {
        databaseInitialized = false;
        return initializeDatabase(callback);
    }

    /**
     * Verifica se o banco precisa de migracao comparando a versao do schema.
     * Retorna true se o banco esta desatualizado ou nao tem controle de versao.
     */
    private boolean precisaMigracao(Connection conn) {
        try {
            if (!tableExists(conn, "schema_migrations_pdv")) {
                Log.d(TAG, "Tabela schema_migrations_pdv ausente - banco antigo, migracao necessaria");
                return true;
            }
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT MAX(CAST(versao AS UNSIGNED)) FROM schema_migrations_pdv");
            int versaoAtual = 0;
            if (rs.next()) versaoAtual = rs.getInt(1);
            rs.close();
            stmt.close();
            boolean precisa = versaoAtual < SCHEMA_VERSION;
            Log.d(TAG, "Schema: atual=" + versaoAtual + ", esperado=" + SCHEMA_VERSION
                    + ", precisa_migracao=" + precisa);
            return precisa;
        } catch (Exception e) {
            Log.w(TAG, "Erro ao verificar versao do schema: " + e.getMessage());
            return true;
        }
    }

    /**
     * Registra a versao atual do schema apos migracao bem-sucedida.
     */
    private void registrarVersaoSchema(Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO schema_migrations_pdv (versao, descricao) VALUES (?, ?)"
                    + " ON DUPLICATE KEY UPDATE descricao = VALUES(descricao), executado_em = NOW()");
            ps.setString(1, String.valueOf(SCHEMA_VERSION));
            ps.setString(2, "PDV Pro v9.1.0 - Revalidacao completa de tabelas/colunas, re-sincronizacao de permissoes e visual futurista aprimorado");
            ps.executeUpdate();
            ps.close();
            Log.d(TAG, "Versao do schema registrada: " + SCHEMA_VERSION);
        } catch (Exception e) {
            Log.w(TAG, "Erro ao registrar versao do schema: " + e.getMessage());
        }
    }

    /**
     * Carrega em memoria as colunas existentes de TODAS as tabelas de uma vez
     * usando uma unica query ao INFORMATION_SCHEMA.
     * Reduz drasticamente o numero de round-trips ao banco durante a migracao.
     */
    private Map<String, List<String>> carregarColunasExistentes(Connection conn) {
        Map<String, List<String>> mapa = new LinkedHashMap<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                    + " WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION");
            ps.setString(1, database);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String tabela = rs.getString(1).toLowerCase();
                String coluna = rs.getString(2).toLowerCase();
                mapa.computeIfAbsent(tabela, k -> new ArrayList<>()).add(coluna);
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao carregar colunas existentes em batch: " + e.getMessage());
        }
        return mapa;
    }

    /**
     * Carrega em memoria os nomes de TODAS as tabelas existentes no banco
     * usando uma unica query ao INFORMATION_SCHEMA.
     */
    private java.util.Set<String> carregarTabelasExistentes(Connection conn) {
        java.util.Set<String> tabelas = new java.util.HashSet<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                    + " WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'");
            ps.setString(1, database);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                tabelas.add(rs.getString(1).toLowerCase());
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao carregar tabelas existentes em batch: " + e.getMessage());
        }
        return tabelas;
    }

    /**
     * Verifica se o banco de dados (DATABASE) existe no servidor MySQL.
     * Se nao existir, tenta cria-lo.
     */
    private boolean ensureDatabaseExists() throws SQLException {
        Connection serverConn = null;
        try {
            // Tentativa 1: Conectar com as credenciais atuais ao servidor (sem banco)
            try {
                serverConn = createServerConnection();
            } catch (SQLException e) {
                // Se falhar, pode ser que o usuario nao exista. 
                // Tentamos conectar como 'root' sem senha (padrao em muitos servidores locais)
                // para tentar criar o usuario e o banco.
                try {
                    String urlRoot = "jdbc:mysql://" + host + ":" + port + "?useSSL=false&allowPublicKeyRetrieval=true";
                    serverConn = DriverManager.getConnection(urlRoot, "root", "");
                    Log.d(TAG, "Conectado como root para provisionamento");
                } catch (SQLException e2) {
                    // Se falhar como root, nao temos o que fazer, repassa o erro original
                    throw e;
                }
            }

            Statement stmt = serverConn.createStatement();

            // 1. Garantir que o USUARIO existe e tem permissoes
            try {
                Log.d(TAG, "Garantindo existencia do usuario: " + username);
                // No MySQL 5.7+ / 8.0+, CREATE USER IF NOT EXISTS e GRANT sao recomendados
                stmt.executeUpdate("CREATE USER IF NOT EXISTS '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
                stmt.executeUpdate("ALTER USER '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
                stmt.executeUpdate("GRANT ALL PRIVILEGES ON *.* TO '" + username + "'@'%' WITH GRANT OPTION");
                stmt.executeUpdate("FLUSH PRIVILEGES");
            } catch (SQLException e) {
                Log.w(TAG, "Aviso ao criar/provisionar usuario: " + e.getMessage());
            }

            // 2. Verificar se o banco existe
            ResultSet rs = stmt.executeQuery(
                    "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = '" 
                    + database.replace("'", "\\'") + "'");
            boolean exists = rs.next();
            rs.close();

            if (!exists) {
                // Banco nao existe - tentar criar
                Log.d(TAG, "Banco de dados '" + database + "' nao encontrado, criando...");
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database 
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                Log.d(TAG, "Banco de dados '" + database + "' criado com sucesso");
            }

            // Garantir permissoes (sempre executa para garantir que o usuario tenha acesso)
            try {
                stmt.executeUpdate("GRANT ALL PRIVILEGES ON `" + database + "`.* TO '" + username + "'@'%'");
                stmt.executeUpdate("FLUSH PRIVILEGES");
                Log.d(TAG, "Privilegios concedidos ao usuario '" + username + "' no banco '" + database + "'");
            } catch (SQLException e) {
                Log.w(TAG, "Aviso ao conceder privilegios no banco: " + e.getMessage());
            }

            return !exists;

        } finally {
            if (serverConn != null) {
                try { serverConn.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Cria indices unicos necessarios para integridade dos dados.
     */
    private void ensureIndexes(Statement stmt, StringBuilder report) {
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_permissoes_chave ON permissoes(chave)", 
                "idx_permissoes_chave", report);
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_perfil_permissao ON perfil_permissoes(perfil_id, permissao_id)", 
                "idx_perfil_permissao", report);
        try {
            stmt.executeUpdate("DELETE up1 FROM usuario_permissoes up1 "
                    + "INNER JOIN usuario_permissoes up2 ON up1.usuario_id = up2.usuario_id "
                    + "AND up1.permissao_id = up2.permissao_id AND up1.id < up2.id");
        } catch (SQLException ignored) {}
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_usuario_permissao ON usuario_permissoes(usuario_id, permissao_id)",
                "idx_usuario_permissao", report);
        createIndexSafe(stmt, "CREATE INDEX idx_usuarios_biometria_usuario ON usuarios_biometria(usuario_id, ativo)",
                "idx_usuarios_biometria_usuario", report);
        createIndexSafe(stmt, "CREATE INDEX idx_usuarios_biometria_ativo ON usuarios_biometria(ativo, ultimo_uso)",
                "idx_usuarios_biometria_ativo", report);
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_perfis_nome ON perfis(nome)", 
                "idx_perfis_nome", report);
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_centros_custo_nome ON centros_custo(nome)",
                "idx_centros_custo_nome", report);
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_migracoes_chave ON migracoes_sistema(chave)",
                "idx_migracoes_chave", report);
        createIndexSafe(stmt, "CREATE UNIQUE INDEX idx_schema_migrations_pdv_versao ON schema_migrations_pdv(versao)",
                "idx_schema_migrations_pdv_versao", report);
        createIndexSafe(stmt, "CREATE INDEX idx_caixa_status_data ON caixa(status, data_abertura)",
                "idx_caixa_status_data", report);
        createIndexSafe(stmt, "CREATE INDEX idx_caixa_nominal_status ON caixa(caixa_nominal_id, status, data_abertura)",
                "idx_caixa_nominal_status", report);
        createIndexSafe(stmt, "CREATE INDEX idx_vendas_data_status ON vendas(data_venda, status)",
                "idx_vendas_data_status", report);
        createIndexSafe(stmt, "CREATE INDEX idx_vendas_vendedor_data ON vendas(vendedor_id, data_venda)",
                "idx_vendas_vendedor_data", report);
        createIndexSafe(stmt, "CREATE INDEX idx_vendas_entregador_data ON vendas(entregador_id, data_venda)",
                "idx_vendas_entregador_data", report);
        createIndexSafe(stmt, "CREATE INDEX idx_vendas_garcom_data ON vendas(garcom_id, data_venda)",
                "idx_vendas_garcom_data", report);
        createIndexSafe(stmt, "CREATE INDEX idx_vales_filtros ON vales_debito(data, centro_custo_id, usuario_id)",
                "idx_vales_filtros", report);
        createIndexSafe(stmt, "CREATE INDEX idx_agenda_alertas ON agenda_servicos(status, data_hora, alertado)",
                "idx_agenda_alertas", report);
        createIndexSafe(stmt, "CREATE INDEX idx_auditoria_data_usuario ON auditoria_acoes(data_acao, usuario_id)",
                "idx_auditoria_data_usuario", report);
        createIndexSafe(stmt, "CREATE INDEX idx_fornecedores_nome_ativo ON fornecedores(nome, ativo)",
                "idx_fornecedores_nome_ativo", report);
        createIndexSafe(stmt, "CREATE INDEX idx_notas_fornecedor ON notas_entrada(fornecedor_id, data_entrada)",
                "idx_notas_fornecedor", report);

        // v6.9.3 - UNIQUE KEY no entregador_id para ON DUPLICATE KEY UPDATE funcionar
        // Primeiro limpar duplicatas existentes
        try {
            stmt.executeUpdate(
                "DELETE r1 FROM rastreamento_entregador r1 "
                + "INNER JOIN rastreamento_entregador r2 "
                + "WHERE r1.entregador_id = r2.entregador_id AND r1.id < r2.id");
        } catch (SQLException e) {
            // Ignorar se nao houver duplicatas ou tabela nao existir
        }
        createIndexSafe(stmt, "CREATE UNIQUE INDEX uk_rastreamento_entregador ON rastreamento_entregador(entregador_id)", 
                "uk_rastreamento_entregador", report);

        // Indices de modulos adicionados em versoes recentes
        createIndexSafe(stmt, "CREATE INDEX idx_chamados_status ON chamados(status)", "idx_chamados_status", report);
        createIndexSafe(stmt, "CREATE INDEX idx_chamados_numero ON chamados(numero_chamado)", "idx_chamados_numero", report);
        createIndexSafe(stmt, "CREATE INDEX idx_pedidos_web_status ON pedidos_web(status)", "idx_pedidos_web_status", report);
        createIndexSafe(stmt, "CREATE INDEX idx_pedidos_web_mesa ON pedidos_web(mesa_numero)", "idx_pedidos_web_mesa", report);
        createIndexSafe(stmt, "CREATE INDEX idx_itens_pedido_web_pedido ON itens_pedido_web(pedido_web_id)", "idx_itens_pedido_web_pedido", report);
        createIndexSafe(stmt, "CREATE INDEX idx_itens_pedido_web_adic_item ON itens_pedido_web_adicionais(item_pedido_web_id)", "idx_itens_pedido_web_adic_item", report);
        createIndexSafe(stmt, "CREATE INDEX idx_estacionamento_status ON estacionamento(status)", "idx_estacionamento_status", report);
        createIndexSafe(stmt, "CREATE INDEX idx_estacionamento_placa ON estacionamento(placa)", "idx_estacionamento_placa", report);
        createIndexSafe(stmt, "CREATE INDEX idx_os_itens_os ON os_itens(os_id)", "idx_os_itens_os", report);
        createIndexSafe(stmt, "CREATE INDEX idx_os_fotos_os ON os_fotos(os_id)", "idx_os_fotos_os", report);
    }

    /** Converte bancos antigos uma unica vez para suporte integral a acentos e emoji. */
    private void ensureUtf8mb4(Connection conn, Statement stmt, StringBuilder report) {
        try {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM migracoes_sistema WHERE chave='utf8mb4_v1'");
            rs.next();
            boolean concluida = rs.getInt(1) > 0;
            rs.close();
            if (concluida) return;

            int convertidas = 0;
            for (TableDef table : getTableDefinitions()) {
                if ("migracoes_sistema".equals(table.name)) continue;
                try {
                    stmt.executeUpdate("ALTER TABLE `" + table.name
                            + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    convertidas++;
                } catch (SQLException e) {
                    Log.w(TAG, "Conversao utf8mb4 ignorada em " + table.name + ": " + e.getMessage());
                }
            }
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO migracoes_sistema (chave, detalhes) VALUES ('utf8mb4_v1', ?)");
            ps.setString(1, convertidas + " tabelas convertidas para utf8mb4");
            ps.executeUpdate();
            ps.close();
            report.append("[OK] Suporte utf8mb4 verificado em ").append(convertidas).append(" tabelas\n");
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel concluir migracao utf8mb4: " + e.getMessage());
            report.append("[AVISO] Migracao utf8mb4: ").append(e.getMessage()).append("\n");
        }
    }

    /** Registra automaticamente toda alteracao feita nas tabelas do sistema. */
    private void ensureAuditTriggers(Connection conn, StringBuilder report) {
        int criados = 0;
        for (TableDef table : getTableDefinitions()) {
            String tabela = table.name;
            if ("auditoria_acoes".equals(tabela) || "migracoes_sistema".equals(tabela)) continue;
            criados += ensureAuditTrigger(conn, tabela, "ai", "INSERT", "NEW.id");
            criados += ensureAuditTrigger(conn, tabela, "au", "UPDATE", "NEW.id");
            criados += ensureAuditTrigger(conn, tabela, "ad", "DELETE", "OLD.id");
        }
        report.append("[OK] Auditoria automatica: ").append(criados).append(" trigger(s) novo(s)\n");
    }

    private int ensureAuditTrigger(Connection conn, String tabela, String sufixo,
                                   String evento, String idExpr) {
        String nome = "aud_" + tabela + "_" + sufixo;
        try {
            PreparedStatement check = conn.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                            + "WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=?");
            check.setString(1, nome);
            ResultSet rs = check.executeQuery();
            rs.next();
            boolean existe = rs.getInt(1) > 0;
            rs.close();
            check.close();
            if (existe) return 0;

            Statement create = conn.createStatement();
            create.executeUpdate("CREATE TRIGGER `" + nome + "` AFTER " + evento
                    + " ON `" + tabela + "` FOR EACH ROW INSERT INTO auditoria_acoes "
                    + "(usuario_id,usuario_nome,acao,modulo,tabela,registro_id,detalhes,ip,data_acao) VALUES ("
                    + "NULLIF(@pdv_usuario_id,0),COALESCE(@pdv_usuario_nome,'Sistema'),'" + evento
                    + "','Banco','" + tabela + "'," + idExpr + ",'Alteracao automatica',"
                    + "COALESCE(@pdv_ip,''),NOW())");
            create.close();
            return 1;
        } catch (Exception e) {
            Log.w(TAG, "Trigger de auditoria " + nome + " nao criado: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Tenta criar um indice, ignorando erro se ja existir.
     */
    private void createIndexSafe(Statement stmt, String sql, String indexName, StringBuilder report) {
        try {
            stmt.executeUpdate(sql);
            report.append("[CRIADO] Indice: ").append(indexName).append("\n");
            Log.d(TAG, "Indice criado: " + indexName);
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Duplicate") 
                    || e.getMessage().contains("already exists")
                    || e.getMessage().contains("duplicate"))) {
                // Normal - indice ja existe
            } else {
                Log.w(TAG, "Aviso ao criar indice " + indexName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Metodo de compatibilidade para criacao/migracao de tabelas.
     * v8.0.23.0 - Otimizado com carregamento em batch de tabelas e colunas.
     */
    public void createTables() {
        if (databaseInitialized) {
            Log.d(TAG, "Banco ja inicializado nesta sessao, pulando createTables()");
            return;
        }
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            List<TableDef> tables = getTableDefinitions();
            int tablesCreated = 0;
            int columnsAdded = 0;

            // Carrega tabelas e colunas em batch (2 queries ao inves de N)
            java.util.Set<String> tabelasExistentes = carregarTabelasExistentes(conn);
            Map<String, List<String>> colunasExistentes = carregarColunasExistentes(conn);

            for (TableDef table : tables) {
                String tableNameLower = table.name.toLowerCase();
                if (!tabelasExistentes.contains(tableNameLower)) {
                    createTable(stmt, table);
                    tablesCreated++;
                    tabelasExistentes.add(tableNameLower);
                    colunasExistentes.put(tableNameLower, new ArrayList<>());
                } else {
                    List<String> existingCols = colunasExistentes.getOrDefault(tableNameLower, new ArrayList<>());
                    for (ColumnDef col : table.columns) {
                        if (col.name.equals("id")) continue;
                        if (!existingCols.contains(col.name.toLowerCase())) {
                            addMissingColumn(stmt, table.name, col);
                            columnsAdded++;
                            existingCols.add(col.name.toLowerCase());
                        }
                    }
                }
            }

            StringBuilder migrationReport = new StringBuilder();
            ensureIndexes(stmt, migrationReport);
            ensureUtf8mb4(conn, stmt, migrationReport);
            ensureAuditTriggers(conn, migrationReport);
            insertDefaultData(stmt);
            registrarVersaoSchema(conn);

            stmt.close();
            databaseInitialized = true;
            Log.d(TAG, "createTables concluido: " + tablesCreated + " tabelas criadas, "
                    + columnsAdded + " colunas adicionadas");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao criar/migrar tabelas", e);
        }
    }

    /**
     * Insere dados padrao necessarios para o funcionamento do sistema.
     */
    private void insertDefaultData(Statement stmt) {
        try {
            // Garantir usuario admin padrao
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuarios");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO usuarios (nome,login,senha,nivel,ativo) VALUES ('Administrador','admin','admin','admin',1)");
                Log.d(TAG, "Usuario admin padrao criado");
            }
            rs.close();

            // Garantir ao menos um centro de custos para lancamentos de caixa.
            rs = stmt.executeQuery("SELECT COUNT(*) FROM centros_custo WHERE ativo = 1");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO centros_custo (nome, ativo) VALUES ('Geral', 1)");
                Log.d(TAG, "Centro de custos padrao criado");
            }
            rs.close();
            // Garantir ao menos um caixa nominal para o dropdown do modulo Caixa.
            rs = stmt.executeQuery("SELECT COUNT(*) FROM caixas_nominais WHERE ativo = 1");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO caixas_nominais (nome, descricao, ativo) VALUES ('Caixa Principal', 'Caixa criado automaticamente para bancos antigos', 1)");
                Log.d(TAG, "Caixa nominal padrao criado");
            }
            rs.close();


            // Preencher o snapshot de comissao das vendas anteriores a esta versao.
            stmt.executeUpdate("UPDATE vendas v LEFT JOIN vendedores vd ON v.vendedor_id = vd.id "
                    + "SET v.comissao_percentual = COALESCE(vd.comissao, 0), "
                    + "v.comissao_valor = ROUND(v.total_liquido * COALESCE(vd.comissao, 0) / 100, 2) "
                    + "WHERE v.comissao_percentual IS NULL OR v.comissao_valor IS NULL");

            stmt.executeUpdate("UPDATE vendas v LEFT JOIN garcons g ON v.garcom_id = g.id "
                    + "SET v.garcom_percentual = COALESCE(g.porcentagem, 0), "
                    + "v.garcom_valor = ROUND(v.total_liquido * COALESCE(g.porcentagem, 0) / 100, 2) "
                    + "WHERE v.garcom_percentual IS NULL OR v.garcom_valor IS NULL");

            // Garantir cliente padrao
            rs = stmt.executeQuery("SELECT COUNT(*) FROM clientes WHERE nome='Cliente nao informado'");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO clientes (nome,ativo) VALUES ('Cliente nao informado',1)");
                Log.d(TAG, "Cliente padrao criado");
            }
            rs.close();

            // Garantir forma de pagamento padrao (Dinheiro)
            rs = stmt.executeQuery("SELECT COUNT(*) FROM formas_pagamento");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('Dinheiro','dinheiro',0,1)");
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('Cartao de Credito','credito',1,1)");
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('Cartao de Debito','debito',0,1)");
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('PIX','pix',0,1)");
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('Contas a Receber','conta_receber',0,1)");
                Log.d(TAG, "Formas de pagamento padrao criadas");
            }
            rs.close();

            // Garantir forma de pagamento Contas a Receber (para bancos existentes)
            rs = stmt.executeQuery("SELECT COUNT(*) FROM formas_pagamento WHERE tipo = 'conta_receber'");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.executeUpdate("INSERT INTO formas_pagamento (descricao,tipo,permite_parcelamento,ativo) VALUES ('Contas a Receber','conta_receber',0,1)");
                Log.d(TAG, "Forma de pagamento Contas a Receber criada");
            }
            rs.close();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inserir dados padrao", e);
        }
    }

    /**
     * Verifica se o banco ja foi inicializado nesta sessao.
     */
    public boolean isDatabaseInitialized() {
        return databaseInitialized;
    }

    /**
     * Metodo utilitario para verificar e corrigir a estrutura do banco.
     */
    public String verificarEstrutura() {
        StringBuilder report = new StringBuilder();
        try {
            Connection conn = getConnection();
            List<TableDef> tables = getTableDefinitions();

            report.append("=== Verificacao de Estrutura do Banco ===\n\n");

            for (TableDef table : tables) {
                if (!tableExists(conn, table.name)) {
                    report.append("[FALTANDO] Tabela: ").append(table.name).append("\n");
                } else {
                    List<String> existingCols = getExistingColumns(conn, table.name);
                    boolean allOk = true;
                    for (ColumnDef col : table.columns) {
                        if (col.name.equals("id")) continue;
                        if (!existingCols.contains(col.name.toLowerCase())) {
                            report.append("[FALTANDO] Coluna: ").append(table.name)
                                    .append(".").append(col.name).append("\n");
                            allOk = false;
                        }
                    }
                    if (allOk) {
                        report.append("[OK] Tabela: ").append(table.name).append("\n");
                    }
                }
            }

            report.append("\nVerificacao concluida.");
        } catch (Exception e) {
            report.append("Erro na verificacao: ").append(e.getMessage());
        }
        return report.toString();
    }

    /**
     * Garante que todas as tabelas existam no banco de dados.
     * Diferente de createTables(), este metodo NAO verifica a flag databaseInitialized,
     * portanto sempre executa a verificacao e criacao de tabelas faltantes.
     * Ideal para ser chamado quando uma tela especifica precisa de tabelas
     * que podem nao ter sido criadas na inicializacao.
     * v6.9.5 - Adicionado para resolver problema de tabelas de armarios nao criadas.
     */
    public void ensureTablesExist() {
        try {
            Connection conn = getConnection();
            Statement stmt = conn.createStatement();
            List<TableDef> tables = getTableDefinitions();
            int tablesCreated = 0;
            int columnsAdded = 0;

            // Carrega tabelas e colunas em batch (2 queries ao inves de N)
            java.util.Set<String> tabelasExistentes = carregarTabelasExistentes(conn);
            Map<String, List<String>> colunasExistentes = carregarColunasExistentes(conn);

            for (TableDef table : tables) {
                String tableNameLower = table.name.toLowerCase();
                if (!tabelasExistentes.contains(tableNameLower)) {
                    createTable(stmt, table);
                    tablesCreated++;
                    tabelasExistentes.add(tableNameLower);
                    colunasExistentes.put(tableNameLower, new ArrayList<>());
                } else {
                    List<String> existingCols = colunasExistentes.getOrDefault(tableNameLower, new ArrayList<>());
                    for (ColumnDef col : table.columns) {
                        if (col.name.equals("id")) continue;
                        if (!existingCols.contains(col.name.toLowerCase())) {
                            addMissingColumn(stmt, table.name, col);
                            columnsAdded++;
                            existingCols.add(col.name.toLowerCase());
                        }
                    }
                }
            }

            if (tablesCreated > 0 || columnsAdded > 0) {
                StringBuilder migrationReport = new StringBuilder();
                ensureIndexes(stmt, migrationReport);
                ensureUtf8mb4(conn, stmt, migrationReport);
                ensureAuditTriggers(conn, migrationReport);
                insertDefaultData(stmt);
                registrarVersaoSchema(conn);
                Log.d(TAG, "ensureTablesExist: " + tablesCreated + " tabelas criadas, "
                        + columnsAdded + " colunas adicionadas");
            }

            stmt.close();
            databaseInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Erro em ensureTablesExist", e);
        }
    }

    public String getHost() { return host; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getPort() { return port; }
}
