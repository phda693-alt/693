package com.pdv.app.permissions;

import android.content.Context;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Permissao;

import java.sql.*;
import java.util.List;

/**
 * Classe responsavel por criar e atualizar as tabelas do sistema de permissoes.
 * 
 * v8.0.12.1 - Sistema de Permissoes Revisado e Sincronizado:
 * 
 * Perfis do sistema:
 * 1. Administrador (sistematico) - Acesso total, abre/fecha tudo, poder total
 * 2. Gerente - Acesso amplo (vendas, caixa, relatorios, cadastros)
 * 3. Operacional - Operacoes basicas do dia-a-dia (vendas, comandas, caixa, historico)
 * 4. Caixa - Focado em operacoes de caixa e vendas
 * 5. Atendente - Atendimento ao cliente, comandas, vendas basicas, chamados
 * 6. Garcom - Comandas, mesas, painel cozinha
 * 7. Balcao - Vendas no balcao, produtos, clientes, caixa
 * 8. Vendedor - Vendas e consultas de produtos
 * 9. Estoquista - Produtos, entrada de notas, estoque
 * 10. Entregador - Modo entregador e entregas
 * 11. Personalizavel - Admin escolhe quais botoes do dashboard esse perfil pode usar
 * 
 * Tabelas:
 * - perfis: Perfis de acesso
 * - permissoes: Todas as permissoes do sistema
 * - perfil_permissoes: Vinculacao N:N entre perfis e permissoes
 * - usuario_permissoes: Overrides individuais por usuario
 * - Coluna perfil_id na tabela usuarios
 * - Coluna personalizavel na tabela perfis
 */
public class PermissionDatabaseSetup {
    private static final String TAG = "PermDBSetup";

    /**
     * Executa a criacao/atualizacao completa do sistema de permissoes.
     * Seguro para chamar multiplas vezes (idempotente).
     */
    public static void setup(Context context) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            Log.d(TAG, "Iniciando setup do sistema de permissoes v8.0.12.1...");

            // 1. Criar tabelas base
            criarTabelas(conn);

            // 2. Criar tabela de overrides por usuario
            criarTabelaOverridesUsuario(conn);

            // 2.1. Corrigir bancos antigos, que usavam a coluna "permitido"
            migrarEstruturaOverridesUsuario(conn);

            // 3. Adicionar coluna personalizavel na tabela perfis
            adicionarColunaPersonalizavel(conn);

            // 4. Sincronizar permissoes
            sincronizarPermissoes(conn);

            // 5. Criar perfis padrao (incluindo novos)
            criarPerfisDefault(conn);

            // 6. Adicionar coluna perfil_id na tabela usuarios
            adicionarColunaPerfil(conn);

            // 7. Migrar usuarios existentes
            migrarUsuariosExistentes(conn);

            // 8. Revisar e completar permissoes criadas apos a criacao dos perfis
            // Cada revisao usa INSERT IGNORE - preserva personalizacoes existentes
            aplicarRevisaoPermissoesV80121(conn);
            aplicarRevisaoPermissoesV80210(conn);
            aplicarRevisaoPermissoesV80230(conn);

            // 9. Auditoria final: garante catalogo completo e pares botao/acao coerentes
            validarIntegridadePermissoes(conn);

            Log.d(TAG, "Setup do sistema de permissoes v8.0.23.1 concluido com sucesso!");

        } catch (Exception e) {
            Log.e(TAG, "Erro no setup de permissoes: " + e.getMessage(), e);
        }
    }

    /**
     * Cria as tabelas base do sistema de permissoes.
     */
    private static void criarTabelas(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        // Tabela de perfis
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS perfis (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  nome VARCHAR(100) NOT NULL UNIQUE," +
                "  descricao VARCHAR(255)," +
                "  sistematico TINYINT(1) DEFAULT 0," +
                "  ativo TINYINT(1) DEFAULT 1," +
                "  personalizavel TINYINT(1) DEFAULT 0," +
                "  criado_em DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  atualizado_em DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Tabela de permissoes
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS permissoes (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  modulo VARCHAR(100) NOT NULL," +
                "  acao VARCHAR(100) NOT NULL," +
                "  chave VARCHAR(100) NOT NULL UNIQUE," +
                "  descricao VARCHAR(255)," +
                "  criado_em DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // Tabela de vinculacao perfil <-> permissoes
        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS perfil_permissoes (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  perfil_id INT NOT NULL," +
                "  permissao_id INT NOT NULL," +
                "  criado_em DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  UNIQUE KEY uk_perfil_permissao (perfil_id, permissao_id)," +
                "  FOREIGN KEY (perfil_id) REFERENCES perfis(id) ON DELETE CASCADE," +
                "  FOREIGN KEY (permissao_id) REFERENCES permissoes(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.close();
        Log.d(TAG, "Tabelas base de permissoes criadas/verificadas");
    }

    /**
     * Cria a tabela de overrides individuais por usuario.
     */
    private static void criarTabelaOverridesUsuario(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();

        stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS usuario_permissoes (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  usuario_id INT NOT NULL," +
                "  permissao_id INT NOT NULL," +
                "  tipo ENUM('adicionar', 'remover') NOT NULL DEFAULT 'adicionar'," +
                "  criado_em DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  criado_por INT DEFAULT NULL," +
                "  UNIQUE KEY uk_usuario_permissao (usuario_id, permissao_id)," +
                "  FOREIGN KEY (permissao_id) REFERENCES permissoes(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        stmt.close();
        Log.d(TAG, "Tabela usuario_permissoes criada/verificada");
    }

    /**
     * Migra a primeira versao de usuario_permissoes (coluna permitido) para o
     * formato atual (tipo = adicionar/remover). CREATE TABLE IF NOT EXISTS nao
     * altera uma tabela ja existente, por isso esta etapa precisa ser explicita.
     */
    private static void migrarEstruturaOverridesUsuario(Connection conn) throws SQLException {
        boolean temTipo = colunaExiste(conn, "usuario_permissoes", "tipo");
        boolean temPermitido = colunaExiste(conn, "usuario_permissoes", "permitido");
        boolean temCriadoPor = colunaExiste(conn, "usuario_permissoes", "criado_por");

        Statement stmt = conn.createStatement();
        if (!temTipo) {
            stmt.executeUpdate("ALTER TABLE usuario_permissoes "
                    + "ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'adicionar'");
        }
        if (!temCriadoPor) {
            stmt.executeUpdate("ALTER TABLE usuario_permissoes ADD COLUMN criado_por INT DEFAULT NULL");
        }
        if (temPermitido) {
            stmt.executeUpdate("UPDATE usuario_permissoes SET tipo = "
                    + "CASE WHEN permitido = 0 THEN 'remover' ELSE 'adicionar' END");
        }
        stmt.executeUpdate("UPDATE usuario_permissoes SET tipo = 'adicionar' "
                + "WHERE tipo IS NULL OR tipo NOT IN ('adicionar','remover')");
        try {
            stmt.executeUpdate("DELETE up1 FROM usuario_permissoes up1 "
                    + "INNER JOIN usuario_permissoes up2 ON up1.usuario_id = up2.usuario_id "
                    + "AND up1.permissao_id = up2.permissao_id AND up1.id < up2.id");
            stmt.executeUpdate("CREATE UNIQUE INDEX idx_usuario_permissao "
                    + "ON usuario_permissoes(usuario_id, permissao_id)");
        } catch (SQLException e) {
            Log.d(TAG, "Indice de usuario_permissoes ja existe: " + e.getMessage());
        }
        stmt.close();
        Log.d(TAG, "Estrutura de usuario_permissoes migrada/verificada");
    }

    private static boolean colunaExiste(Connection conn, String tabela, String coluna) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?");
        ps.setString(1, tabela);
        ps.setString(2, coluna);
        ResultSet rs = ps.executeQuery();
        boolean existe = rs.next() && rs.getInt(1) > 0;
        rs.close();
        ps.close();
        return existe;
    }

    /**
     * Adiciona a coluna personalizavel na tabela perfis se nao existir.
     */
    private static void adicionarColunaPersonalizavel(Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'perfis' AND COLUMN_NAME = 'personalizavel'");
            rs.next();
            boolean existe = rs.getInt(1) > 0;
            rs.close();
            stmt.close();

            if (!existe) {
                Statement stmtAlter = conn.createStatement();
                stmtAlter.executeUpdate(
                        "ALTER TABLE perfis ADD COLUMN personalizavel TINYINT(1) DEFAULT 0");
                stmtAlter.close();
                Log.d(TAG, "Coluna personalizavel adicionada a tabela perfis");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar/adicionar coluna personalizavel: " + e.getMessage());
        }
    }

    /**
     * Sincroniza as permissoes definidas em PermissionConstants com o banco.
     *
     * Otimizacao v8.0.23.0:
     * - Usa INSERT ... ON DUPLICATE KEY UPDATE para fazer upsert em batch unico
     * - Executa tudo em uma unica transacao atomica
     * - Elimina o loop de SELECT+INSERT/UPDATE por permissao (N queries -> 1 query)
     */
    private static void sincronizarPermissoes(Connection conn) throws SQLException {
        List<Permissao> todasPermissoes = PermissionConstants.getTodasPermissoes();
        if (todasPermissoes.isEmpty()) return;

        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // Upsert em batch: insere novas e atualiza existentes em uma unica operacao
            PreparedStatement psUpsert = conn.prepareStatement(
                    "INSERT INTO permissoes (modulo, acao, chave, descricao) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE modulo = VALUES(modulo), acao = VALUES(acao), descricao = VALUES(descricao)");

            for (Permissao p : todasPermissoes) {
                psUpsert.setString(1, p.getModulo());
                psUpsert.setString(2, p.getAcao());
                psUpsert.setString(3, p.getChave());
                psUpsert.setString(4, p.getDescricao());
                psUpsert.addBatch();
            }
            int[] results = psUpsert.executeBatch();
            conn.commit();
            psUpsert.close();

            // Contar insercoes (1) vs atualizacoes (2 = ON DUPLICATE KEY)
            int novas = 0, atualizadas = 0;
            for (int r : results) {
                if (r == 1) novas++;
                else if (r >= 2) atualizadas++;
            }
            Log.d(TAG, "Permissoes sincronizadas em batch: " + novas + " novas, " + atualizadas
                    + " atualizadas de " + todasPermissoes.size() + " total");
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) {}
        }
    }

    /**
     * Cria os perfis padrao do sistema se nao existirem.
     * v7.0.1 - Expandido com novos perfis: Operacional, Caixa, Atendente, Garcom,
     *          Balcao, Vendedor, Estoquista, Personalizavel
     */
    private static void criarPerfisDefault(Connection conn) throws SQLException {
        // Sempre garantir que o Administrador existe e tem todas as permissoes
        garantirAdminSistematico(conn);

        // Criar perfis que nao existem ainda
        criarPerfilSeNaoExiste(conn, "Gerente",
                "Acesso amplo: vendas, caixa, relatorios, cadastros. Sem config. criticas.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Operacional",
                "Operacoes basicas do dia-a-dia: vendas, comandas, caixa, historico, clientes.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Caixa",
                "Focado em operacoes de caixa: abertura/fechamento, vendas, comandas, vale/debito.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Atendente",
                "Atendimento ao cliente: vendas, comandas, clientes, chamados.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Garcom",
                "Servico de mesa: comandas, gerenciar mesas, painel cozinha.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Balcao",
                "Vendas no balcao: vendas, comandas, produtos, clientes, caixa.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Vendedor",
                "Vendas e consultas: vendas, produtos, clientes, historico.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Estoquista",
                "Controle de estoque: produtos, gerenciar produtos, entrada de notas.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Entregador",
                "Apenas modo entregador e entregas.",
                false, false);

        criarPerfilSeNaoExiste(conn, "Personalizavel",
                "Perfil personalizavel: o administrador escolhe quais botoes do dashboard este perfil pode usar.",
                false, true);

        // Agora conceder permissoes para cada perfil
        concederPermissoesParaPerfil(conn, "Gerente");
        concederPermissoesParaPerfil(conn, "Operacional");
        concederPermissoesParaPerfil(conn, "Caixa");
        concederPermissoesParaPerfil(conn, "Atendente");
        concederPermissoesParaPerfil(conn, "Garcom");
        concederPermissoesParaPerfil(conn, "Balcao");
        concederPermissoesParaPerfil(conn, "Vendedor");
        concederPermissoesParaPerfil(conn, "Estoquista");
        concederPermissoesParaPerfil(conn, "Entregador");
        // Personalizavel comeca sem permissoes - admin configura manualmente

        // Remover perfil antigo "Operador de Caixa" se existir (migrado para "Operacional" e "Caixa")
        migrarPerfilAntigoOperadorDeCaixa(conn);

        Log.d(TAG, "Perfis padrao criados/atualizados com sucesso");
    }

    private static void criarPerfilSeNaoExiste(Connection conn, String nome, String descricao,
                                                boolean sistematico, boolean personalizavel) throws SQLException {
        PreparedStatement psCheck = conn.prepareStatement(
                "SELECT COUNT(*) FROM perfis WHERE nome = ?");
        psCheck.setString(1, nome);
        ResultSet rs = psCheck.executeQuery();
        rs.next();
        boolean existe = rs.getInt(1) > 0;
        rs.close();
        psCheck.close();

        if (!existe) {
            PreparedStatement psInsert = conn.prepareStatement(
                    "INSERT INTO perfis (nome, descricao, sistematico, ativo, personalizavel) VALUES (?, ?, ?, 1, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            psInsert.setString(1, nome);
            psInsert.setString(2, descricao);
            psInsert.setInt(3, sistematico ? 1 : 0);
            psInsert.setInt(4, personalizavel ? 1 : 0);
            psInsert.executeUpdate();
            ResultSet keys = psInsert.getGeneratedKeys();
            int id = 0;
            if (keys.next()) id = keys.getInt(1);
            keys.close();
            psInsert.close();
            Log.d(TAG, "Perfil '" + nome + "' criado com ID: " + id);
        } else {
            // Atualizar descricao e flag personalizavel
            PreparedStatement psUpdate = conn.prepareStatement(
                    "UPDATE perfis SET descricao = ?, personalizavel = ? WHERE nome = ? AND sistematico = 0");
            psUpdate.setString(1, descricao);
            psUpdate.setInt(2, personalizavel ? 1 : 0);
            psUpdate.setString(3, nome);
            psUpdate.executeUpdate();
            psUpdate.close();
        }
    }

    /**
     * Migra usuarios do perfil antigo "Operador de Caixa" para "Operacional"
     * e desativa o perfil antigo.
     */
    private static void migrarPerfilAntigoOperadorDeCaixa(Connection conn) {
        try {
            int operadorCaixaId = getPerfilId(conn, "Operador de Caixa");
            if (operadorCaixaId <= 0) return; // Nao existe, nada a fazer

            int operacionalId = getPerfilId(conn, "Operacional");
            if (operacionalId <= 0) return;

            // Migrar usuarios do perfil antigo para o novo
            PreparedStatement psMigrar = conn.prepareStatement(
                    "UPDATE usuarios SET perfil_id = ? WHERE perfil_id = ?");
            psMigrar.setInt(1, operacionalId);
            psMigrar.setInt(2, operadorCaixaId);
            int migrados = psMigrar.executeUpdate();
            psMigrar.close();

            if (migrados > 0) {
                Log.d(TAG, "Migrados " + migrados + " usuarios de 'Operador de Caixa' para 'Operacional'");
            }

            // Desativar o perfil antigo
            PreparedStatement psDesativar = conn.prepareStatement(
                    "UPDATE perfis SET ativo = 0 WHERE id = ? AND sistematico = 0");
            psDesativar.setInt(1, operadorCaixaId);
            psDesativar.executeUpdate();
            psDesativar.close();

            Log.d(TAG, "Perfil 'Operador de Caixa' desativado e usuarios migrados para 'Operacional'");
        } catch (Exception e) {
            Log.w(TAG, "Erro ao migrar perfil Operador de Caixa: " + e.getMessage());
        }
    }

    private static void concederPermissoesParaPerfil(Connection conn, String nomePerfil) throws SQLException {
        int perfilId = getPerfilId(conn, nomePerfil);
        if (perfilId <= 0) return;

        // Verificar se o perfil ja tem permissoes
        PreparedStatement psCount = conn.prepareStatement(
                "SELECT COUNT(*) FROM perfil_permissoes WHERE perfil_id = ?");
        psCount.setInt(1, perfilId);
        ResultSet rsCount = psCount.executeQuery();
        rsCount.next();
        int count = rsCount.getInt(1);
        rsCount.close();
        psCount.close();

        // Se ja tem permissoes, nao sobrescrever (admin pode ter customizado)
        if (count > 0) {
            Log.d(TAG, "Perfil '" + nomePerfil + "' ja tem " + count + " permissoes, pulando");
            return;
        }

        switch (nomePerfil) {
            case "Gerente":
                concederPermissoesGerente(conn, perfilId);
                break;
            case "Operacional":
                concederPermissoesOperacional(conn, perfilId);
                break;
            case "Caixa":
                concederPermissoesCaixa(conn, perfilId);
                break;
            case "Atendente":
                concederPermissoesAtendente(conn, perfilId);
                break;
            case "Garcom":
                concederPermissoesGarcom(conn, perfilId);
                break;
            case "Balcao":
                concederPermissoesBalcao(conn, perfilId);
                break;
            case "Vendedor":
                concederPermissoesVendedor(conn, perfilId);
                break;
            case "Estoquista":
                concederPermissoesEstoquista(conn, perfilId);
                break;
            case "Entregador":
                concederPermissoesEntregador(conn, perfilId);
                break;
        }
    }

    private static void garantirAdminSistematico(Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM perfis WHERE nome = 'Administrador' AND sistematico = 1");
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            rs.close();
            ps.close();
            int adminId = inserirPerfil(conn, "Administrador",
                    "Acesso total ao sistema. Todas as permissoes. Abre e fecha tudo.", true, false);
            concederTodasPermissoes(conn, adminId);
        } else {
            int adminId = rs.getInt("id");
            rs.close();
            ps.close();
            concederTodasPermissoes(conn, adminId);
        }
    }

    private static int inserirPerfil(Connection conn, String nome, String descricao,
                                      boolean sistematico, boolean personalizavel) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO perfis (nome, descricao, sistematico, ativo, personalizavel) VALUES (?, ?, ?, 1, ?)",
                Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, nome);
        ps.setString(2, descricao);
        ps.setInt(3, sistematico ? 1 : 0);
        ps.setInt(4, personalizavel ? 1 : 0);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        int id = 0;
        if (keys.next()) id = keys.getInt(1);
        keys.close();
        ps.close();
        return id;
    }

    private static void concederTodasPermissoes(Connection conn, int perfilId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO perfil_permissoes (perfil_id, permissao_id) " +
                "SELECT ?, id FROM permissoes");
        ps.setInt(1, perfilId);
        ps.executeUpdate();
        ps.close();
    }

    private static void concederPermissoes(Connection conn, int perfilId,
                                            String[] chaves) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO perfil_permissoes (perfil_id, permissao_id) " +
                "SELECT ?, id FROM permissoes WHERE chave = ?");
        for (String chave : chaves) {
            ps.setInt(1, perfilId);
            ps.setString(2, chave);
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
    }

    // =========================================================================
    // PERMISSOES POR PERFIL
    // =========================================================================

    private static void concederPermissoesGerente(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard - botoes visiveis para gerente
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_CAIXA,
                PermissionConstants.DASHBOARD_BTN_RELATORIOS, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_EMPRESA, PermissionConstants.DASHBOARD_BTN_VENDEDORES,
                PermissionConstants.DASHBOARD_BTN_ENTREGADORES, PermissionConstants.DASHBOARD_BTN_FORMAS_PAGAMENTO,
                PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO, PermissionConstants.DASHBOARD_BTN_OBSERVACOES,
                PermissionConstants.DASHBOARD_BTN_IMPRESSORA, PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA,
                PermissionConstants.DASHBOARD_BTN_GERENCIAR_ENTREGAS, PermissionConstants.DASHBOARD_BTN_CONTAS_RECEBER,
                PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS, PermissionConstants.DASHBOARD_BTN_TAXA_ENTREGA,
                PermissionConstants.DASHBOARD_BTN_SOBRE,
                PermissionConstants.DASHBOARD_BTN_ADICIONAIS,
                PermissionConstants.DASHBOARD_BTN_GARCONS,
                PermissionConstants.DASHBOARD_BTN_CADASTRO_MESAS,
                PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS,
                PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA,
                PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS,
                PermissionConstants.DASHBOARD_BTN_GERENCIADOR_CHAMADOS,
                PermissionConstants.DASHBOARD_BTN_FORNECEDORES,
                PermissionConstants.DASHBOARD_BTN_CONFIGURACOES,
                // Vendas completo
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_APLICAR_DESCONTO, PermissionConstants.VENDAS_APLICAR_ACRESCIMO,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE, PermissionConstants.VENDAS_ESCOLHER_VENDEDOR,
                PermissionConstants.VENDAS_ESCOLHER_ENTREGADOR,
                // Historico completo
                PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_CANCELAR_VENDA,
                PermissionConstants.HISTORICO_REIMPRIMIR, PermissionConstants.HISTORICO_ENVIAR_WHATSAPP,
                // Comandas completo
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                PermissionConstants.COMANDAS_CANCELAR, PermissionConstants.COMANDAS_IMPRIMIR,
                // Caixa completo
                PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_VALE_DEBITO,
                // Produtos completo
                PermissionConstants.PRODUTOS_ACESSAR, PermissionConstants.PRODUTOS_CRIAR,
                PermissionConstants.PRODUTOS_EDITAR, PermissionConstants.PRODUTOS_EXCLUIR,
                // Gerenciar Produtos
                PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR, PermissionConstants.GERENCIAR_PRODUTOS_EDITAR,
                PermissionConstants.GERENCIAR_PRODUTOS_INATIVAR,
                // Clientes completo
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                PermissionConstants.CLIENTES_EDITAR, PermissionConstants.CLIENTES_EXCLUIR,
                // Vendedores completo
                PermissionConstants.VENDEDORES_ACESSAR, PermissionConstants.VENDEDORES_CRIAR,
                PermissionConstants.VENDEDORES_EDITAR, PermissionConstants.VENDEDORES_EXCLUIR,
                // Entregadores completo
                PermissionConstants.ENTREGADORES_ACESSAR, PermissionConstants.ENTREGADORES_CRIAR,
                PermissionConstants.ENTREGADORES_EDITAR, PermissionConstants.ENTREGADORES_EXCLUIR,
                // Empresa
                PermissionConstants.EMPRESA_ACESSAR, PermissionConstants.EMPRESA_EDITAR,
                // Formas Pagamento
                PermissionConstants.FORMAS_PAGAMENTO_ACESSAR, PermissionConstants.FORMAS_PAGAMENTO_CRIAR,
                PermissionConstants.FORMAS_PAGAMENTO_EDITAR, PermissionConstants.FORMAS_PAGAMENTO_EXCLUIR,
                // Tipos Produto
                PermissionConstants.TIPOS_PRODUTO_ACESSAR, PermissionConstants.TIPOS_PRODUTO_CRIAR,
                PermissionConstants.TIPOS_PRODUTO_EDITAR, PermissionConstants.TIPOS_PRODUTO_EXCLUIR,
                // Adicionais
                PermissionConstants.ADICIONAIS_ACESSAR, PermissionConstants.ADICIONAIS_CRIAR,
                PermissionConstants.ADICIONAIS_EDITAR, PermissionConstants.ADICIONAIS_EXCLUIR,
                // Observacoes
                PermissionConstants.OBSERVACOES_ACESSAR, PermissionConstants.OBSERVACOES_CRIAR,
                PermissionConstants.OBSERVACOES_EDITAR, PermissionConstants.OBSERVACOES_EXCLUIR,
                // Relatorios completo
                PermissionConstants.RELATORIOS_ACESSAR, PermissionConstants.RELATORIOS_VENDAS,
                PermissionConstants.RELATORIOS_LUCRATIVIDADE, PermissionConstants.RELATORIOS_VENDEDOR,
                PermissionConstants.RELATORIOS_ENTREGADOR, PermissionConstants.RELATORIOS_CLIENTE,
                PermissionConstants.RELATORIOS_PRODUTOS, PermissionConstants.RELATORIOS_CAIXA,
                // Impressora
                PermissionConstants.CONFIG_IMPRESSORA_ACESSAR, PermissionConstants.CONFIG_IMPRESSORA_EDITAR,
                // Entregas
                PermissionConstants.ENTREGAS_ACESSAR, PermissionConstants.ENTREGAS_GERENCIAR,
                // Entrada de Notas completo
                PermissionConstants.ENTRADA_NOTAS_ACESSAR, PermissionConstants.ENTRADA_NOTAS_CRIAR,
                PermissionConstants.ENTRADA_NOTAS_CONFIRMAR, PermissionConstants.ENTRADA_NOTAS_CANCELAR,
                // Fornecedores e configuracoes gerais
                PermissionConstants.FORNECEDORES_ACESSAR, PermissionConstants.FORNECEDORES_CRIAR,
                PermissionConstants.FORNECEDORES_EDITAR, PermissionConstants.FORNECEDORES_EXCLUIR,
                PermissionConstants.CONFIG_GERAL_ACESSAR,
                // Contas a Receber
                PermissionConstants.CONTAS_RECEBER_ACESSAR, PermissionConstants.CONTAS_RECEBER_RECEBER,
                PermissionConstants.CONTAS_RECEBER_CANCELAR, PermissionConstants.CONTAS_RECEBER_RELATORIO,
                // Taxa Entrega
                PermissionConstants.TAXA_ENTREGA_ACESSAR, PermissionConstants.TAXA_ENTREGA_CRIAR,
                PermissionConstants.TAXA_ENTREGA_EDITAR, PermissionConstants.TAXA_ENTREGA_EXCLUIR,
                // Garcons
                PermissionConstants.GARCONS_ACESSAR, PermissionConstants.GARCONS_CRIAR,
                PermissionConstants.GARCONS_EDITAR, PermissionConstants.GARCONS_EXCLUIR,
                // Mesas
                PermissionConstants.MESAS_ACESSAR, PermissionConstants.MESAS_CRIAR,
                PermissionConstants.MESAS_EDITAR, PermissionConstants.MESAS_EXCLUIR,
                // Gerenciar Mesas
                PermissionConstants.GERENCIAR_MESAS_ACESSAR,
                // Painel Cozinha
                PermissionConstants.PAINEL_COZINHA_ACESSAR,
                // Painel Chamados
                PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                // Gerenciador Chamados
                PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR,
                // Ordem de Servico
                PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO,
                PermissionConstants.ORDEM_SERVICO_ACESSAR,
                PermissionConstants.ORDEM_SERVICO_CRIAR,
                PermissionConstants.ORDEM_SERVICO_EDITAR,
                PermissionConstants.ORDEM_SERVICO_INATIVAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA,
                // Contas a Pagar
                PermissionConstants.CONTAS_PAGAR_ACESSAR, PermissionConstants.CONTAS_PAGAR_CRIAR,
                PermissionConstants.CONTAS_PAGAR_EDITAR, PermissionConstants.CONTAS_PAGAR_PAGAR,
                PermissionConstants.CONTAS_PAGAR_CANCELAR, PermissionConstants.CONTAS_PAGAR_RELATORIO,
                PermissionConstants.DASHBOARD_BTN_CONTAS_PAGAR,
                // Caixas Nominais
                PermissionConstants.CAIXAS_NOMINAIS_ACESSAR, PermissionConstants.CAIXAS_NOMINAIS_CRIAR,
                PermissionConstants.CAIXAS_NOMINAIS_EDITAR, PermissionConstants.CAIXAS_NOMINAIS_EXCLUIR,
                PermissionConstants.DASHBOARD_BTN_CAIXAS_NOMINAIS,
                // Turnos
                PermissionConstants.TURNOS_ACESSAR, PermissionConstants.TURNOS_CRIAR,
                PermissionConstants.TURNOS_EDITAR, PermissionConstants.TURNOS_EXCLUIR,
                PermissionConstants.DASHBOARD_BTN_TURNOS,
                // Vinculos
                PermissionConstants.VINCULOS_ACESSAR, PermissionConstants.VINCULOS_CRIAR,
                PermissionConstants.VINCULOS_EXCLUIR, PermissionConstants.VINCULOS_RELATORIO,
                PermissionConstants.DASHBOARD_BTN_VINCULOS
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * OPERACIONAL - Operacoes basicas do dia-a-dia
     * Vendas, Comandas, Caixa, Historico, Clientes, Produtos (consulta), Trocar Senha
     */
    private static void concederPermissoesOperacional(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard - botoes visiveis
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Vendas
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_APLICAR_DESCONTO,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                // Historico (sem cancelar)
                PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                // Comandas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                PermissionConstants.COMANDAS_IMPRIMIR,
                // Caixa
                PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                PermissionConstants.CAIXA_FECHAR,
                // Produtos (somente consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Clientes (consulta e criar)
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                // Ordem de Servico
                PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO,
                PermissionConstants.ORDEM_SERVICO_ACESSAR,
                PermissionConstants.ORDEM_SERVICO_CRIAR,
                PermissionConstants.ORDEM_SERVICO_EDITAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * CAIXA - Focado em operacoes de caixa
     * Caixa (completo), Vendas, Comandas, Historico, Clientes, Trocar Senha
     */
    private static void concederPermissoesCaixa(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Vendas
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_APLICAR_DESCONTO,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                PermissionConstants.VENDAS_ESCOLHER_VENDEDOR,
                // Caixa completo
                PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_VALE_DEBITO,
                // Comandas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                PermissionConstants.COMANDAS_IMPRIMIR,
                // Historico
                PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                // Clientes
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                // Produtos (consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * ATENDENTE - Atendimento ao cliente
     * Vendas basicas, Comandas, Clientes, Produtos (consulta), Chamados, Trocar Senha
     */
    private static void concederPermissoesAtendente(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Vendas basicas
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                // Comandas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR,
                // Clientes
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                PermissionConstants.CLIENTES_EDITAR,
                // Produtos (consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Historico (consulta)
                PermissionConstants.HISTORICO_ACESSAR,
                // Painel Chamados
                PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * GARCOM - Servico de mesa
     * Comandas, Gerenciar Mesas, Painel Cozinha, Produtos (consulta), Trocar Senha
     */
    private static void concederPermissoesGarcom(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS,
                PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA,
                PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Comandas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_IMPRIMIR,
                // Gerenciar Mesas
                PermissionConstants.GERENCIAR_MESAS_ACESSAR,
                // Painel Cozinha
                PermissionConstants.PAINEL_COZINHA_ACESSAR,
                // Produtos (consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * BALCAO - Vendas no balcao
     * Vendas, Comandas, Produtos, Clientes, Caixa, Historico, Trocar Senha
     */
    private static void concederPermissoesBalcao(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Vendas
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_APLICAR_DESCONTO,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                PermissionConstants.VENDAS_ESCOLHER_VENDEDOR,
                // Comandas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                PermissionConstants.COMANDAS_IMPRIMIR,
                // Caixa
                PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                PermissionConstants.CAIXA_FECHAR,
                // Produtos (consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Clientes
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                // Historico
                PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * VENDEDOR - Vendas e consultas
     * Vendas, Produtos (consulta), Clientes, Comandas basicas, Historico, Trocar Senha
     */
    private static void concederPermissoesVendedor(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                PermissionConstants.DASHBOARD_BTN_HISTORICO,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Vendas
                PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                // Comandas basicas
                PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                PermissionConstants.COMANDAS_EDITAR,
                // Produtos (consulta)
                PermissionConstants.PRODUTOS_ACESSAR,
                // Clientes
                PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                // Historico (consulta)
                PermissionConstants.HISTORICO_ACESSAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * ESTOQUISTA - Controle de estoque
     * Produtos (completo), Gerenciar Produtos, Entrada de Notas, Tipos Produto, Trocar Senha
     */
    private static void concederPermissoesEstoquista(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS,
                PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS,
                PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO,
                PermissionConstants.DASHBOARD_BTN_ADICIONAIS,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Produtos completo
                PermissionConstants.PRODUTOS_ACESSAR, PermissionConstants.PRODUTOS_CRIAR,
                PermissionConstants.PRODUTOS_EDITAR, PermissionConstants.PRODUTOS_EXCLUIR,
                // Gerenciar Produtos
                PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR, PermissionConstants.GERENCIAR_PRODUTOS_EDITAR,
                PermissionConstants.GERENCIAR_PRODUTOS_INATIVAR,
                // Entrada de Notas
                PermissionConstants.ENTRADA_NOTAS_ACESSAR, PermissionConstants.ENTRADA_NOTAS_CRIAR,
                PermissionConstants.ENTRADA_NOTAS_CONFIRMAR,
                // Tipos Produto (consulta)
                PermissionConstants.TIPOS_PRODUTO_ACESSAR,
                // Adicionais
                PermissionConstants.ADICIONAIS_ACESSAR, PermissionConstants.ADICIONAIS_CRIAR,
                PermissionConstants.ADICIONAIS_EDITAR,
                // Trocar senha
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * ENTREGADOR - Modo entregador e entregas
     */
    private static void concederPermissoesEntregador(Connection conn, int perfilId) throws SQLException {
        String[] chaves = {
                // Dashboard
                PermissionConstants.DASHBOARD_BTN_MODO_ENTREGADOR,
                PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA,
                PermissionConstants.DASHBOARD_BTN_SOBRE,
                // Funcionalidades
                PermissionConstants.MODO_ENTREGADOR_ACESSAR,
                PermissionConstants.ENTREGAS_ACESSAR,
                PermissionConstants.TROCAR_SENHA
        };
        concederPermissoes(conn, perfilId, chaves);
    }

    /**
     * v8.0.12.1 - Revisao minuciosa das permissoes adicionadas depois da criacao
     * dos perfis originais.
     *
     * Importante:
     * - Nao apaga personalizacoes do administrador.
     * - Usa INSERT IGNORE para acrescentar somente permissoes faltantes.
     * - Permissoes criticas de cancelar/devolver/configurar ficam restritas aos perfis
     *   Administrador/Gerente, mantendo seguranca operacional.
     */
    private static void aplicarRevisaoPermissoesV80121(Connection conn) {
        try {
            Log.d(TAG, "Aplicando revisao criteriosa de permissoes v8.0.12.1...");

            int gerente = getPerfilId(conn, "Gerente");
            int operacional = getPerfilId(conn, "Operacional");
            int caixa = getPerfilId(conn, "Caixa");
            int atendente = getPerfilId(conn, "Atendente");
            int garcom = getPerfilId(conn, "Garcom");
            int balcao = getPerfilId(conn, "Balcao");
            int vendedor = getPerfilId(conn, "Vendedor");
            int estoquista = getPerfilId(conn, "Estoquista");

            if (gerente > 0) concederPermissoes(conn, gerente, new String[]{
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS,
                    PermissionConstants.DASHBOARD_BTN_WEB_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.DASHBOARD_BTN_MULTIIMPRESSORAS,
                    PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE,
                    PermissionConstants.PAINEL_COZINHA_WEB,
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_LIMPAR, PermissionConstants.PAINEL_SENHAS_TESTAR_SOM,
                    PermissionConstants.PAINEL_SENHAS_WEB,
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_CANCELAR,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA, PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    PermissionConstants.ESTACIONAMENTO_CONFIGURAR_VALOR,
                    PermissionConstants.MULTIIMPRESSORAS_ACESSAR, PermissionConstants.MULTIIMPRESSORAS_ATIVAR,
                    PermissionConstants.MULTIIMPRESSORAS_CRIAR_REGRA, PermissionConstants.MULTIIMPRESSORAS_EDITAR_REGRA,
                    PermissionConstants.MULTIIMPRESSORAS_EXCLUIR_REGRA, PermissionConstants.MULTIIMPRESSORAS_TESTAR_IMPRESSORA,
                    PermissionConstants.MULTIIMPRESSORAS_CONFIGURAR_DRIVER,
                    PermissionConstants.CONFIG_IMPRESSORA_DRIVER,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    PermissionConstants.HISTORICO_DEVOLVER_VENDA,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.ORDEM_SERVICO_VER, PermissionConstants.ORDEM_SERVICO_IMPRIMIR,
                    PermissionConstants.ORDEM_SERVICO_FOTOS, PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.ARMARIOS_SAUNA_ACESSAR, PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR,
                    PermissionConstants.DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA,
                    PermissionConstants.DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA,
                    PermissionConstants.DASHBOARD_BTN_CADASTRO_SERVICO,
                    PermissionConstants.SERVICOS_ACESSAR, PermissionConstants.SERVICOS_CRIAR,
                    PermissionConstants.SERVICOS_EDITAR, PermissionConstants.SERVICOS_EXCLUIR
            });

            if (operacional > 0) concederPermissoes(conn, operacional, new String[]{
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS,
                    PermissionConstants.DASHBOARD_BTN_WEB_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE,
                    PermissionConstants.PAINEL_COZINHA_WEB,
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_HISTORICO,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.ORDEM_SERVICO_VER, PermissionConstants.ORDEM_SERVICO_IMPRIMIR,
                    PermissionConstants.ORDEM_SERVICO_FOTOS, PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS
            });

            if (caixa > 0) concederPermissoes(conn, caixa, new String[]{
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM
            });

            if (atendente > 0) concederPermissoes(conn, atendente, new String[]{
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO,
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.ORDEM_SERVICO_ACESSAR, PermissionConstants.ORDEM_SERVICO_CRIAR,
                    PermissionConstants.ORDEM_SERVICO_VER, PermissionConstants.ORDEM_SERVICO_IMPRIMIR,
                    PermissionConstants.ORDEM_SERVICO_FOTOS, PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS
            });

            if (garcom > 0) concederPermissoes(conn, garcom, new String[]{
                    PermissionConstants.DASHBOARD_BTN_WEB_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE,
                    PermissionConstants.PAINEL_COZINHA_WEB,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS
            });

            if (balcao > 0) concederPermissoes(conn, balcao, new String[]{
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_HISTORICO,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS
            });

            if (vendedor > 0) concederPermissoes(conn, vendedor, new String[]{
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS
            });

            if (estoquista > 0) concederPermissoes(conn, estoquista, new String[]{
                    PermissionConstants.CONFIG_IMPRESSORA_DRIVER
            });

            Log.d(TAG, "Revisao de permissoes v8.0.12.1 aplicada.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao aplicar revisao de permissoes v8.0.12.1: " + e.getMessage(), e);
        }
    }

    /**
     * v8.0.21.0 - Acrescenta somente as novas permissoes coerentes com cada
     * perfil. As personalizacoes existentes continuam preservadas e os
     * recursos de infraestrutura MySQL permanecem exclusivos do Administrador.
     */
    private static void aplicarRevisaoPermissoesV80210(Connection conn) {
        try {
            int gerente = getPerfilId(conn, "Gerente");
            int operacional = getPerfilId(conn, "Operacional");
            int caixa = getPerfilId(conn, "Caixa");
            int atendente = getPerfilId(conn, "Atendente");
            int garcom = getPerfilId(conn, "Garcom");
            int balcao = getPerfilId(conn, "Balcao");
            int vendedor = getPerfilId(conn, "Vendedor");

            if (gerente > 0) concederPermissoes(conn, gerente, new String[]{
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    PermissionConstants.RELATORIOS_GARCOM,
                    PermissionConstants.RELATORIOS_TAXAS_ENTREGADOR,
                    PermissionConstants.RELATORIOS_VALES_CAIXA,
                    PermissionConstants.RELATORIOS_AUDITORIA,
                    PermissionConstants.DASHBOARD_BTN_DIAGNOSTICO,
                    PermissionConstants.DIAGNOSTICO_ACESSAR,
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR, PermissionConstants.AGENDA_CANCELAR,
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR
            });
            if (operacional > 0) concederPermissoes(conn, operacional, new String[]{
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR,
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR
            });
            if (caixa > 0) concederPermissoes(conn, caixa, new String[]{
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO
            });
            if (atendente > 0) concederPermissoes(conn, atendente, new String[]{
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR
            });
            if (garcom > 0) concederPermissoes(conn, garcom, new String[]{
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR
            });
            if (balcao > 0) concederPermissoes(conn, balcao, new String[]{
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR
            });
            if (vendedor > 0) concederPermissoes(conn, vendedor, new String[]{
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR
            });
            Log.d(TAG, "Revisao de permissoes v8.0.21.0 aplicada.");
        } catch (Exception e) {
            Log.e(TAG, "Erro na revisao de permissoes v8.0.21.0", e);
        }
    }

    /**
     * v8.0.23.0 - Revisao COMPLETA e CRITERIOSA de permissoes.
     *
     * Cobre TODOS os modulos adicionados apos a criacao dos perfis originais:
     * - Contas a Pagar, Caixas Nominais, Turnos, Vinculos
     * - Agenda, Cardapio QR Code, Diagnostico
     * - Estacionamento, Painel de Senhas, Multiimpressoras
     * - Armarios Sauna, Ordem de Servico, Servicos
     * - Relatorios avancados (Garcom, Taxas Entregador, Vales Caixa, Auditoria)
     * - Caixa Reimprimir Fechamento
     * - Historico Devolver Venda
     * - Leitor de Codigo de Barras em todos os modulos aplicaveis
     * - Impressao de duas vias, canhoto, senha no cupom
     * - Driver de impressora
     * - Fornecedores
     * - Configuracoes Gerais
     * - Painel de Chamados e Gerenciador de Chamados
     * - Garcons, Mesas, Gerenciar Mesas
     * - Web Cozinha
     * - Contas a Receber
     * - WhatsApp Bot
     * - Entrada de Notas
     * - Taxa de Entrega
     * - Backup, Licenca, Trocar Senha
     *
     * Usa INSERT IGNORE para preservar personalizacoes existentes.
     * Criterios de seguranca:
     * - Cancelar/Devolver/Excluir: somente Gerente e acima
     * - Configuracoes criticas: somente Gerente e acima
     * - Relatorios financeiros completos: somente Gerente e acima
     * - Auditoria: somente Gerente e acima
     */
    private static void aplicarRevisaoPermissoesV80230(Connection conn) {
        try {
            Log.d(TAG, "Aplicando revisao completa de permissoes v8.0.23.0...");

            int gerente    = getPerfilId(conn, "Gerente");
            int operacional = getPerfilId(conn, "Operacional");
            int caixa      = getPerfilId(conn, "Caixa");
            int atendente  = getPerfilId(conn, "Atendente");
            int garcom     = getPerfilId(conn, "Garcom");
            int balcao     = getPerfilId(conn, "Balcao");
            int vendedor   = getPerfilId(conn, "Vendedor");
            int estoquista = getPerfilId(conn, "Estoquista");
            int entregador = getPerfilId(conn, "Entregador");

            // ===================================================================
            // GERENTE - Acesso amplo sem configuracoes criticas de sistema
            // ===================================================================
            if (gerente > 0) concederPermissoes(conn, gerente, new String[]{
                    // Dashboard - todos os botoes operacionais e de gestao
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS,
                    PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_CAIXA,
                    PermissionConstants.DASHBOARD_BTN_RELATORIOS, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                    PermissionConstants.DASHBOARD_BTN_EMPRESA, PermissionConstants.DASHBOARD_BTN_VENDEDORES,
                    PermissionConstants.DASHBOARD_BTN_ENTREGADORES, PermissionConstants.DASHBOARD_BTN_FORMAS_PAGAMENTO,
                    PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO, PermissionConstants.DASHBOARD_BTN_OBSERVACOES,
                    PermissionConstants.DASHBOARD_BTN_IMPRESSORA, PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA,
                    PermissionConstants.DASHBOARD_BTN_GERENCIAR_ENTREGAS, PermissionConstants.DASHBOARD_BTN_CONTAS_RECEBER,
                    PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS, PermissionConstants.DASHBOARD_BTN_TAXA_ENTREGA,
                    PermissionConstants.DASHBOARD_BTN_SOBRE, PermissionConstants.DASHBOARD_BTN_ADICIONAIS,
                    PermissionConstants.DASHBOARD_BTN_GARCONS, PermissionConstants.DASHBOARD_BTN_CADASTRO_MESAS,
                    PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS, PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS, PermissionConstants.DASHBOARD_BTN_GERENCIADOR_CHAMADOS,
                    PermissionConstants.DASHBOARD_BTN_FORNECEDORES, PermissionConstants.DASHBOARD_BTN_CONFIGURACOES,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.DASHBOARD_BTN_WEB_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO, PermissionConstants.DASHBOARD_BTN_MULTIIMPRESSORAS,
                    PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE, PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO,
                    PermissionConstants.DASHBOARD_BTN_CADASTRO_SERVICO, PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.DASHBOARD_BTN_DIAGNOSTICO, PermissionConstants.DASHBOARD_BTN_CONTAS_PAGAR,
                    PermissionConstants.DASHBOARD_BTN_CAIXAS_NOMINAIS, PermissionConstants.DASHBOARD_BTN_TURNOS,
                    PermissionConstants.DASHBOARD_BTN_VINCULOS,
                    PermissionConstants.DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA,
                    PermissionConstants.DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA,
                    PermissionConstants.DASHBOARD_BTN_WHATSBOT,
                    // Vendas completo
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_APLICAR_DESCONTO, PermissionConstants.VENDAS_APLICAR_ACRESCIMO,
                    PermissionConstants.VENDAS_ESCOLHER_CLIENTE, PermissionConstants.VENDAS_ESCOLHER_VENDEDOR,
                    PermissionConstants.VENDAS_ESCOLHER_ENTREGADOR, PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA, PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS,
                    PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    // Historico completo
                    PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_CANCELAR_VENDA,
                    PermissionConstants.HISTORICO_DEVOLVER_VENDA, PermissionConstants.HISTORICO_REIMPRIMIR,
                    PermissionConstants.HISTORICO_ENVIAR_WHATSAPP,
                    // Comandas completo
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                    PermissionConstants.COMANDAS_CANCELAR, PermissionConstants.COMANDAS_IMPRIMIR,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Caixa completo
                    PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                    PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_VALE_DEBITO,
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    // Produtos completo
                    PermissionConstants.PRODUTOS_ACESSAR, PermissionConstants.PRODUTOS_CRIAR,
                    PermissionConstants.PRODUTOS_EDITAR, PermissionConstants.PRODUTOS_EXCLUIR,
                    // Gerenciar Produtos
                    PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR, PermissionConstants.GERENCIAR_PRODUTOS_EDITAR,
                    PermissionConstants.GERENCIAR_PRODUTOS_INATIVAR,
                    // Clientes completo
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    PermissionConstants.CLIENTES_EDITAR, PermissionConstants.CLIENTES_EXCLUIR,
                    // Vendedores completo
                    PermissionConstants.VENDEDORES_ACESSAR, PermissionConstants.VENDEDORES_CRIAR,
                    PermissionConstants.VENDEDORES_EDITAR, PermissionConstants.VENDEDORES_EXCLUIR,
                    // Entregadores completo
                    PermissionConstants.ENTREGADORES_ACESSAR, PermissionConstants.ENTREGADORES_CRIAR,
                    PermissionConstants.ENTREGADORES_EDITAR, PermissionConstants.ENTREGADORES_EXCLUIR,
                    // Empresa
                    PermissionConstants.EMPRESA_ACESSAR, PermissionConstants.EMPRESA_EDITAR,
                    // Formas Pagamento
                    PermissionConstants.FORMAS_PAGAMENTO_ACESSAR, PermissionConstants.FORMAS_PAGAMENTO_CRIAR,
                    PermissionConstants.FORMAS_PAGAMENTO_EDITAR, PermissionConstants.FORMAS_PAGAMENTO_EXCLUIR,
                    // Tipos Produto
                    PermissionConstants.TIPOS_PRODUTO_ACESSAR, PermissionConstants.TIPOS_PRODUTO_CRIAR,
                    PermissionConstants.TIPOS_PRODUTO_EDITAR, PermissionConstants.TIPOS_PRODUTO_EXCLUIR,
                    // Adicionais
                    PermissionConstants.ADICIONAIS_ACESSAR, PermissionConstants.ADICIONAIS_CRIAR,
                    PermissionConstants.ADICIONAIS_EDITAR, PermissionConstants.ADICIONAIS_EXCLUIR,
                    // Observacoes
                    PermissionConstants.OBSERVACOES_ACESSAR, PermissionConstants.OBSERVACOES_CRIAR,
                    PermissionConstants.OBSERVACOES_EDITAR, PermissionConstants.OBSERVACOES_EXCLUIR,
                    // Relatorios completo
                    PermissionConstants.RELATORIOS_ACESSAR, PermissionConstants.RELATORIOS_VENDAS,
                    PermissionConstants.RELATORIOS_LUCRATIVIDADE, PermissionConstants.RELATORIOS_VENDEDOR,
                    PermissionConstants.RELATORIOS_ENTREGADOR, PermissionConstants.RELATORIOS_CLIENTE,
                    PermissionConstants.RELATORIOS_PRODUTOS, PermissionConstants.RELATORIOS_CAIXA,
                    PermissionConstants.RELATORIOS_GARCOM, PermissionConstants.RELATORIOS_TAXAS_ENTREGADOR,
                    PermissionConstants.RELATORIOS_VALES_CAIXA, PermissionConstants.RELATORIOS_AUDITORIA,
                    // Impressora
                    PermissionConstants.CONFIG_IMPRESSORA_ACESSAR, PermissionConstants.CONFIG_IMPRESSORA_EDITAR,
                    PermissionConstants.CONFIG_IMPRESSORA_DRIVER,
                    // Multiimpressoras
                    PermissionConstants.MULTIIMPRESSORAS_ACESSAR, PermissionConstants.MULTIIMPRESSORAS_ATIVAR,
                    PermissionConstants.MULTIIMPRESSORAS_CRIAR_REGRA, PermissionConstants.MULTIIMPRESSORAS_EDITAR_REGRA,
                    PermissionConstants.MULTIIMPRESSORAS_EXCLUIR_REGRA, PermissionConstants.MULTIIMPRESSORAS_TESTAR_IMPRESSORA,
                    PermissionConstants.MULTIIMPRESSORAS_CONFIGURAR_DRIVER,
                    // Entregas
                    PermissionConstants.ENTREGAS_ACESSAR, PermissionConstants.ENTREGAS_GERENCIAR,
                    // Entrada de Notas completo
                    PermissionConstants.ENTRADA_NOTAS_ACESSAR, PermissionConstants.ENTRADA_NOTAS_CRIAR,
                    PermissionConstants.ENTRADA_NOTAS_CONFIRMAR, PermissionConstants.ENTRADA_NOTAS_CANCELAR,
                    // Fornecedores
                    PermissionConstants.FORNECEDORES_ACESSAR, PermissionConstants.FORNECEDORES_CRIAR,
                    PermissionConstants.FORNECEDORES_EDITAR, PermissionConstants.FORNECEDORES_EXCLUIR,
                    // Configuracoes Gerais
                    PermissionConstants.CONFIG_GERAL_ACESSAR,
                    // Contas a Receber
                    PermissionConstants.CONTAS_RECEBER_ACESSAR, PermissionConstants.CONTAS_RECEBER_RECEBER,
                    PermissionConstants.CONTAS_RECEBER_CANCELAR, PermissionConstants.CONTAS_RECEBER_RELATORIO,
                    // Taxa Entrega
                    PermissionConstants.TAXA_ENTREGA_ACESSAR, PermissionConstants.TAXA_ENTREGA_CRIAR,
                    PermissionConstants.TAXA_ENTREGA_EDITAR, PermissionConstants.TAXA_ENTREGA_EXCLUIR,
                    // WhatsApp Bot
                    PermissionConstants.WHATSBOT_ACESSAR, PermissionConstants.WHATSBOT_CONFIGURAR,
                    // Painel Chamados
                    PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                    PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                    // Gerenciador Chamados
                    PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR,
                    // Garcons
                    PermissionConstants.GARCONS_ACESSAR, PermissionConstants.GARCONS_CRIAR,
                    PermissionConstants.GARCONS_EDITAR, PermissionConstants.GARCONS_EXCLUIR,
                    // Mesas
                    PermissionConstants.MESAS_ACESSAR, PermissionConstants.MESAS_CRIAR,
                    PermissionConstants.MESAS_EDITAR, PermissionConstants.MESAS_EXCLUIR,
                    // Gerenciar Mesas
                    PermissionConstants.GERENCIAR_MESAS_ACESSAR,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS,
                    // Painel Cozinha
                    PermissionConstants.PAINEL_COZINHA_ACESSAR, PermissionConstants.PAINEL_COZINHA_WEB,
                    // Painel Senhas
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_LIMPAR, PermissionConstants.PAINEL_SENHAS_TESTAR_SOM,
                    PermissionConstants.PAINEL_SENHAS_WEB,
                    // Estacionamento
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_CANCELAR,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA, PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    PermissionConstants.ESTACIONAMENTO_CONFIGURAR_VALOR,
                    // Armarios Sauna
                    PermissionConstants.ARMARIOS_SAUNA_ACESSAR, PermissionConstants.ARMARIOS_SAUNA_CRIAR,
                    PermissionConstants.ARMARIOS_SAUNA_EDITAR, PermissionConstants.ARMARIOS_SAUNA_EXCLUIR,
                    PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR,
                    PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS,
                    // Ordem de Servico
                    PermissionConstants.ORDEM_SERVICO_ACESSAR, PermissionConstants.ORDEM_SERVICO_CRIAR,
                    PermissionConstants.ORDEM_SERVICO_EDITAR, PermissionConstants.ORDEM_SERVICO_INATIVAR,
                    PermissionConstants.ORDEM_SERVICO_VER, PermissionConstants.ORDEM_SERVICO_IMPRIMIR,
                    PermissionConstants.ORDEM_SERVICO_FOTOS, PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS,
                    // Servicos
                    PermissionConstants.SERVICOS_ACESSAR, PermissionConstants.SERVICOS_CRIAR,
                    PermissionConstants.SERVICOS_EDITAR, PermissionConstants.SERVICOS_EXCLUIR,
                    // Agenda
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR, PermissionConstants.AGENDA_CANCELAR,
                    // Cardapio QR Code
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR,
                    // Diagnostico
                    PermissionConstants.DIAGNOSTICO_ACESSAR,
                    // Contas a Pagar
                    PermissionConstants.CONTAS_PAGAR_ACESSAR, PermissionConstants.CONTAS_PAGAR_CRIAR,
                    PermissionConstants.CONTAS_PAGAR_EDITAR, PermissionConstants.CONTAS_PAGAR_PAGAR,
                    PermissionConstants.CONTAS_PAGAR_CANCELAR, PermissionConstants.CONTAS_PAGAR_RELATORIO,
                    // Caixas Nominais
                    PermissionConstants.CAIXAS_NOMINAIS_ACESSAR, PermissionConstants.CAIXAS_NOMINAIS_CRIAR,
                    PermissionConstants.CAIXAS_NOMINAIS_EDITAR, PermissionConstants.CAIXAS_NOMINAIS_EXCLUIR,
                    // Turnos
                    PermissionConstants.TURNOS_ACESSAR, PermissionConstants.TURNOS_CRIAR,
                    PermissionConstants.TURNOS_EDITAR, PermissionConstants.TURNOS_EXCLUIR,
                    // Vinculos
                    PermissionConstants.VINCULOS_ACESSAR, PermissionConstants.VINCULOS_CRIAR,
                    PermissionConstants.VINCULOS_EXCLUIR, PermissionConstants.VINCULOS_RELATORIO,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // OPERACIONAL - Operacoes do dia-a-dia
            // ===================================================================
            if (operacional > 0) concederPermissoes(conn, operacional, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                    PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.DASHBOARD_BTN_WEB_COZINHA,
                    PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO, PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE,
                    PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO, PermissionConstants.DASHBOARD_BTN_AGENDA,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS,
                    // Vendas
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_APLICAR_DESCONTO, PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                    PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS, PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    // Historico
                    PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                    // Comandas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                    PermissionConstants.COMANDAS_IMPRIMIR, PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Caixa
                    PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                    PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Clientes
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    // Painel Senhas
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    // Web Cozinha
                    PermissionConstants.PAINEL_COZINHA_WEB,
                    // Estacionamento (operacional basico)
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_HISTORICO,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    // Ordem de Servico
                    PermissionConstants.ORDEM_SERVICO_ACESSAR, PermissionConstants.ORDEM_SERVICO_CRIAR,
                    PermissionConstants.ORDEM_SERVICO_EDITAR, PermissionConstants.ORDEM_SERVICO_VER,
                    PermissionConstants.ORDEM_SERVICO_IMPRIMIR, PermissionConstants.ORDEM_SERVICO_FOTOS,
                    PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS,
                    // Agenda
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR,
                    // Cardapio QR Code
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR,
                    // Painel Chamados
                    PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                    PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // CAIXA - Focado em operacoes de caixa e vendas
            // ===================================================================
            if (caixa > 0) concederPermissoes(conn, caixa, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                    PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    // Vendas
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_APLICAR_DESCONTO, PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                    PermissionConstants.VENDAS_ESCOLHER_VENDEDOR, PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA, PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS,
                    PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    // Caixa completo
                    PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                    PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_VALE_DEBITO,
                    PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    // Comandas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                    PermissionConstants.COMANDAS_IMPRIMIR, PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Historico
                    PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                    // Clientes
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Painel Senhas
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    // Estacionamento (finalizar e historico)
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // ATENDENTE - Atendimento ao cliente
            // ===================================================================
            if (atendente > 0) concederPermissoes(conn, atendente, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                    PermissionConstants.DASHBOARD_BTN_HISTORICO, PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO, PermissionConstants.DASHBOARD_BTN_AGENDA,
                    // Vendas basicas
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_ESCOLHER_CLIENTE, PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA,
                    // Comandas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_IMPRIMIR,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Clientes
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    PermissionConstants.CLIENTES_EDITAR,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Historico (consulta)
                    PermissionConstants.HISTORICO_ACESSAR,
                    // Painel Chamados
                    PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                    PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                    // Painel Senhas
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    // Estacionamento (entrada e historico)
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_HISTORICO, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    // Ordem de Servico (criar e ver)
                    PermissionConstants.ORDEM_SERVICO_ACESSAR, PermissionConstants.ORDEM_SERVICO_CRIAR,
                    PermissionConstants.ORDEM_SERVICO_VER, PermissionConstants.ORDEM_SERVICO_IMPRIMIR,
                    PermissionConstants.ORDEM_SERVICO_FOTOS, PermissionConstants.ORDEM_SERVICO_LEITOR_CODIGO_BARRAS,
                    // Agenda
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // GARCOM - Servico de mesa
            // ===================================================================
            if (garcom > 0) concederPermissoes(conn, garcom, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_COMANDAS, PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA, PermissionConstants.DASHBOARD_BTN_PRODUTOS,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    PermissionConstants.DASHBOARD_BTN_WEB_COZINHA, PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS,
                    // Comandas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_IMPRIMIR,
                    PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Gerenciar Mesas
                    PermissionConstants.GERENCIAR_MESAS_ACESSAR,
                    PermissionConstants.GERENCIAR_MESAS_LEITOR_CODIGO_BARRAS,
                    // Painel Cozinha
                    PermissionConstants.PAINEL_COZINHA_ACESSAR, PermissionConstants.PAINEL_COZINHA_WEB,
                    // Cardapio QR Code
                    PermissionConstants.CARDAPIO_QRCODE_ACESSAR,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Painel Chamados
                    PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_CHAMADOS_CRIAR,
                    PermissionConstants.PAINEL_CHAMADOS_CHAMAR, PermissionConstants.PAINEL_CHAMADOS_ATENDER,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // BALCAO - Vendas no balcao
            // ===================================================================
            if (balcao > 0) concederPermissoes(conn, balcao, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                    PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.DASHBOARD_BTN_HISTORICO,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO,
                    PermissionConstants.DASHBOARD_BTN_AGENDA,
                    // Vendas
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_APLICAR_DESCONTO, PermissionConstants.VENDAS_ESCOLHER_CLIENTE,
                    PermissionConstants.VENDAS_ESCOLHER_VENDEDOR, PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA, PermissionConstants.VENDAS_IMPRIMIR_DUAS_VIAS,
                    PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    // Comandas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_FECHAR,
                    PermissionConstants.COMANDAS_IMPRIMIR, PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Caixa
                    PermissionConstants.CAIXA_ACESSAR, PermissionConstants.CAIXA_ABRIR,
                    PermissionConstants.CAIXA_FECHAR, PermissionConstants.CAIXA_REIMPRIMIR_FECHAMENTO,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Clientes
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    // Historico
                    PermissionConstants.HISTORICO_ACESSAR, PermissionConstants.HISTORICO_REIMPRIMIR,
                    // Painel Senhas
                    PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.PAINEL_SENHAS_CHAMAR,
                    PermissionConstants.PAINEL_SENHAS_TESTAR_SOM, PermissionConstants.PAINEL_SENHAS_WEB,
                    // Estacionamento
                    PermissionConstants.ESTACIONAMENTO_ACESSAR, PermissionConstants.ESTACIONAMENTO_ENTRADA,
                    PermissionConstants.ESTACIONAMENTO_FINALIZAR_SAIDA, PermissionConstants.ESTACIONAMENTO_HISTORICO,
                    PermissionConstants.ESTACIONAMENTO_IMPRIMIR_CHEGADA, PermissionConstants.ESTACIONAMENTO_IMPRIMIR_ENTREGA,
                    PermissionConstants.ESTACIONAMENTO_LEITOR_PLACA,
                    // Agenda
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // VENDEDOR - Vendas e consultas
            // ===================================================================
            if (vendedor > 0) concederPermissoes(conn, vendedor, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.DASHBOARD_BTN_COMANDAS,
                    PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_CLIENTES,
                    PermissionConstants.DASHBOARD_BTN_HISTORICO, PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA,
                    PermissionConstants.DASHBOARD_BTN_SOBRE, PermissionConstants.DASHBOARD_BTN_AGENDA,
                    // Vendas
                    PermissionConstants.VENDAS_ACESSAR, PermissionConstants.VENDAS_CRIAR,
                    PermissionConstants.VENDAS_ESCOLHER_CLIENTE, PermissionConstants.VENDAS_LEITOR_CODIGO_BARRAS,
                    PermissionConstants.VENDAS_IMPRIMIR_CANHOTO_SENHA, PermissionConstants.VENDAS_EXIBIR_SENHA_CUPOM,
                    // Comandas basicas
                    PermissionConstants.COMANDAS_ACESSAR, PermissionConstants.COMANDAS_CRIAR,
                    PermissionConstants.COMANDAS_EDITAR, PermissionConstants.COMANDAS_LEITOR_CODIGO_BARRAS,
                    // Produtos (consulta)
                    PermissionConstants.PRODUTOS_ACESSAR,
                    // Clientes
                    PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CLIENTES_CRIAR,
                    // Historico (consulta)
                    PermissionConstants.HISTORICO_ACESSAR,
                    // Agenda
                    PermissionConstants.AGENDA_ACESSAR, PermissionConstants.AGENDA_CRIAR,
                    PermissionConstants.AGENDA_EDITAR,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // ESTOQUISTA - Controle de estoque
            // ===================================================================
            if (estoquista > 0) concederPermissoes(conn, estoquista, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS,
                    PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS, PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO,
                    PermissionConstants.DASHBOARD_BTN_ADICIONAIS, PermissionConstants.DASHBOARD_BTN_FORNECEDORES,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    // Produtos completo
                    PermissionConstants.PRODUTOS_ACESSAR, PermissionConstants.PRODUTOS_CRIAR,
                    PermissionConstants.PRODUTOS_EDITAR, PermissionConstants.PRODUTOS_EXCLUIR,
                    // Gerenciar Produtos
                    PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR, PermissionConstants.GERENCIAR_PRODUTOS_EDITAR,
                    PermissionConstants.GERENCIAR_PRODUTOS_INATIVAR,
                    // Entrada de Notas
                    PermissionConstants.ENTRADA_NOTAS_ACESSAR, PermissionConstants.ENTRADA_NOTAS_CRIAR,
                    PermissionConstants.ENTRADA_NOTAS_CONFIRMAR,
                    // Tipos Produto (consulta)
                    PermissionConstants.TIPOS_PRODUTO_ACESSAR,
                    // Adicionais
                    PermissionConstants.ADICIONAIS_ACESSAR, PermissionConstants.ADICIONAIS_CRIAR,
                    PermissionConstants.ADICIONAIS_EDITAR,
                    // Fornecedores (consulta)
                    PermissionConstants.FORNECEDORES_ACESSAR,
                    // Driver de impressora (para impressao de etiquetas)
                    PermissionConstants.CONFIG_IMPRESSORA_DRIVER,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            // ===================================================================
            // ENTREGADOR - Modo entregador e entregas
            // ===================================================================
            if (entregador > 0) concederPermissoes(conn, entregador, new String[]{
                    // Dashboard
                    PermissionConstants.DASHBOARD_BTN_MODO_ENTREGADOR,
                    PermissionConstants.DASHBOARD_BTN_GERENCIAR_ENTREGAS,
                    PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.DASHBOARD_BTN_SOBRE,
                    // Funcionalidades
                    PermissionConstants.MODO_ENTREGADOR_ACESSAR,
                    PermissionConstants.ENTREGAS_ACESSAR, PermissionConstants.ENTREGAS_GERENCIAR,
                    // Trocar Senha
                    PermissionConstants.TROCAR_SENHA
            });

            Log.d(TAG, "Revisao completa de permissoes v8.0.23.0 aplicada com sucesso.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao aplicar revisao de permissoes v8.0.23.0: " + e.getMessage(), e);
        }
    }

    /**
     * Auditoria final de permissoes.
     *
     * Mantem o catalogo do banco fiel ao PermissionConstants e corrige bancos
     * antigos em que um perfil recebeu o botao do dashboard mas nao recebeu a
     * permissao real da acao, ou vice-versa.
     *
     * Regras:
     * - Administrador sempre recebe todas as permissoes.
     * - Perfis personalizaveis nao sao alterados para preservar escolhas do admin.
     * - Perfis padrao recebem pares coerentes botao/acao apenas quando ja tinham
     *   pelo menos um dos dois lados.
     */
    private static void validarIntegridadePermissoes(Connection conn) throws SQLException {
        List<Permissao> todas = PermissionConstants.getTodasPermissoes();
        PreparedStatement psCheck = conn.prepareStatement(
                "SELECT COUNT(*) FROM permissoes WHERE chave = ?");
        StringBuilder faltantes = new StringBuilder();
        for (Permissao p : todas) {
            psCheck.setString(1, p.getChave());
            ResultSet rs = psCheck.executeQuery();
            boolean existe = rs.next() && rs.getInt(1) > 0;
            rs.close();
            if (!existe) {
                if (faltantes.length() > 0) faltantes.append(", ");
                faltantes.append(p.getChave());
            }
        }
        psCheck.close();
        if (faltantes.length() > 0) {
            throw new SQLException("Catalogo de permissoes incompleto: " + faltantes);
        }

        int adminId = getPerfilId(conn, "Administrador");
        if (adminId > 0) {
            try (PreparedStatement psAdmin = conn.prepareStatement(
                    "INSERT IGNORE INTO perfil_permissoes (perfil_id, permissao_id) "
                            + "SELECT ?, id FROM permissoes")) {
                psAdmin.setInt(1, adminId);
                psAdmin.executeUpdate();
            }
        }

        sincronizarParesDashboardAcao(conn);
        Log.d(TAG, "Auditoria final de permissoes concluida: " + todas.size() + " permissoes validadas.");
    }

    private static void sincronizarParesDashboardAcao(Connection conn) throws SQLException {
        String[][] pares = new String[][]{
                {PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.VENDAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_COMANDAS, PermissionConstants.COMANDAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.PRODUTOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS, PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.CLIENTES_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.CAIXA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_RELATORIOS, PermissionConstants.RELATORIOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_HISTORICO, PermissionConstants.HISTORICO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_EMPRESA, PermissionConstants.EMPRESA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_VENDEDORES, PermissionConstants.VENDEDORES_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ENTREGADORES, PermissionConstants.ENTREGADORES_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_USUARIOS, PermissionConstants.USUARIOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_PERFIS, PermissionConstants.PERFIS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_FORMAS_PAGAMENTO, PermissionConstants.FORMAS_PAGAMENTO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO, PermissionConstants.TIPOS_PRODUTO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ADICIONAIS, PermissionConstants.ADICIONAIS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_OBSERVACOES, PermissionConstants.OBSERVACOES_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_IMPRESSORA, PermissionConstants.CONFIG_IMPRESSORA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_MULTIIMPRESSORAS, PermissionConstants.MULTIIMPRESSORAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_BACKUP, PermissionConstants.BACKUP_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_LICENCA, PermissionConstants.LICENCA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.TROCAR_SENHA},
                {PermissionConstants.DASHBOARD_BTN_MODO_ENTREGADOR, PermissionConstants.MODO_ENTREGADOR_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GERENCIAR_ENTREGAS, PermissionConstants.ENTREGAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CONTAS_RECEBER, PermissionConstants.CONTAS_RECEBER_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_WHATSBOT, PermissionConstants.WHATSBOT_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS, PermissionConstants.ENTRADA_NOTAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_TAXA_ENTREGA, PermissionConstants.TAXA_ENTREGA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS, PermissionConstants.PAINEL_CHAMADOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GERENCIADOR_CHAMADOS, PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GARCONS, PermissionConstants.GARCONS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CADASTRO_MESAS, PermissionConstants.MESAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS, PermissionConstants.GERENCIAR_MESAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE, PermissionConstants.CARDAPIO_QRCODE_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA, PermissionConstants.PAINEL_COZINHA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_WEB_COZINHA, PermissionConstants.PAINEL_COZINHA_WEB},
                {PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.PAINEL_SENHAS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO, PermissionConstants.ESTACIONAMENTO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA, PermissionConstants.ARMARIOS_SAUNA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA, PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO, PermissionConstants.ORDEM_SERVICO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CADASTRO_SERVICO, PermissionConstants.SERVICOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_ATUALIZAR, PermissionConstants.ATUALIZAR_SISTEMA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_DIAGNOSTICO, PermissionConstants.DIAGNOSTICO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_SERVIDOR_MYSQL, PermissionConstants.SERVIDOR_MYSQL_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CRIAR_BANCO_MYSQL, PermissionConstants.CRIAR_BANCO_MYSQL},
                {PermissionConstants.DASHBOARD_BTN_USUARIOS_MYSQL, PermissionConstants.USUARIOS_MYSQL_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_MYSQL_ESPELHO, PermissionConstants.MYSQL_ESPELHO_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_AGENDA, PermissionConstants.AGENDA_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CONFIGURACOES, PermissionConstants.CONFIG_GERAL_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_FORNECEDORES, PermissionConstants.FORNECEDORES_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CONTAS_PAGAR, PermissionConstants.CONTAS_PAGAR_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_CAIXAS_NOMINAIS, PermissionConstants.CAIXAS_NOMINAIS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_TURNOS, PermissionConstants.TURNOS_ACESSAR},
                {PermissionConstants.DASHBOARD_BTN_VINCULOS, PermissionConstants.VINCULOS_ACESSAR}
        };

        try (PreparedStatement psPerfis = conn.prepareStatement(
                "SELECT id FROM perfis WHERE ativo = 1 AND COALESCE(personalizavel,0) = 0")) {
            ResultSet rs = psPerfis.executeQuery();
            while (rs.next()) {
                int perfilId = rs.getInt(1);
                for (String[] par : pares) {
                    boolean temDashboard = perfilTemPermissao(conn, perfilId, par[0]);
                    boolean temAcao = perfilTemPermissao(conn, perfilId, par[1]);
                    if (temDashboard && !temAcao) {
                        concederPermissaoUnica(conn, perfilId, par[1]);
                    } else if (temAcao && !temDashboard) {
                        concederPermissaoUnica(conn, perfilId, par[0]);
                    }
                }
            }
            rs.close();
        }
    }

    private static boolean perfilTemPermissao(Connection conn, int perfilId, String chave) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM perfil_permissoes pp "
                        + "JOIN permissoes p ON p.id = pp.permissao_id "
                        + "WHERE pp.perfil_id = ? AND p.chave = ?")) {
            ps.setInt(1, perfilId);
            ps.setString(2, chave);
            ResultSet rs = ps.executeQuery();
            boolean tem = rs.next() && rs.getInt(1) > 0;
            rs.close();
            return tem;
        }
    }

    private static void concederPermissaoUnica(Connection conn, int perfilId, String chave) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO perfil_permissoes (perfil_id, permissao_id) "
                        + "SELECT ?, id FROM permissoes WHERE chave = ?")) {
            ps.setInt(1, perfilId);
            ps.setString(2, chave);
            ps.executeUpdate();
        }
    }

    /**
     * Adiciona a coluna perfil_id na tabela usuarios se nao existir.
     */
    private static void adicionarColunaPerfil(Connection conn) throws SQLException {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'perfil_id'");
            rs.next();
            boolean existe = rs.getInt(1) > 0;
            rs.close();
            stmt.close();

            if (!existe) {
                Statement stmtAlter = conn.createStatement();
                stmtAlter.executeUpdate(
                        "ALTER TABLE usuarios ADD COLUMN perfil_id INT DEFAULT NULL");
                stmtAlter.close();
                Log.d(TAG, "Coluna perfil_id adicionada a tabela usuarios");

                try {
                    Statement stmtFK = conn.createStatement();
                    stmtFK.executeUpdate(
                            "ALTER TABLE usuarios ADD CONSTRAINT fk_usuario_perfil " +
                            "FOREIGN KEY (perfil_id) REFERENCES perfis(id) ON DELETE SET NULL");
                    stmtFK.close();
                } catch (Exception fkError) {
                    Log.w(TAG, "FK ja existe ou nao pode ser criada: " + fkError.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar/adicionar coluna perfil_id: " + e.getMessage());
        }
    }

    /**
     * Migra usuarios existentes para o sistema de perfis baseado no campo 'nivel'.
     */
    private static void migrarUsuariosExistentes(Connection conn) throws SQLException {
        Statement stmtCheck = conn.createStatement();
        ResultSet rsCheck = stmtCheck.executeQuery(
                "SELECT COUNT(*) FROM usuarios WHERE perfil_id IS NULL OR perfil_id = 0");
        rsCheck.next();
        int semPerfil = rsCheck.getInt(1);
        rsCheck.close();
        stmtCheck.close();

        if (semPerfil == 0) {
            Log.d(TAG, "Todos os usuarios ja possuem perfil");
            return;
        }

        Log.d(TAG, "Migrando " + semPerfil + " usuarios para o sistema de perfis...");

        int adminId = getPerfilId(conn, "Administrador");
        int gerenteId = getPerfilId(conn, "Gerente");
        int operacionalId = getPerfilId(conn, "Operacional");
        int caixaId = getPerfilId(conn, "Caixa");
        int vendedorId = getPerfilId(conn, "Vendedor");
        int entregadorId = getPerfilId(conn, "Entregador");

        atualizarPerfilPorNivel(conn, "admin", adminId);
        atualizarPerfilPorNivel(conn, "gerente", gerenteId > 0 ? gerenteId : adminId);
        atualizarPerfilPorNivel(conn, "operador", operacionalId > 0 ? operacionalId : caixaId);
        atualizarPerfilPorNivel(conn, "caixa", caixaId > 0 ? caixaId : operacionalId);
        atualizarPerfilPorNivel(conn, "vendedor", vendedorId);
        atualizarPerfilPorNivel(conn, "entregador", entregadorId);

        // Default para quem nao tem nivel definido
        PreparedStatement psDefault = conn.prepareStatement(
                "UPDATE usuarios SET perfil_id = ? WHERE perfil_id IS NULL OR perfil_id = 0");
        psDefault.setInt(1, operacionalId > 0 ? operacionalId : adminId);
        int updated = psDefault.executeUpdate();
        psDefault.close();

        Log.d(TAG, "Migracao concluida. " + updated + " usuarios atualizados com perfil padrao.");
    }

    private static int getPerfilId(Connection conn, String nome) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM perfis WHERE nome = ? AND ativo = 1");
        ps.setString(1, nome);
        ResultSet rs = ps.executeQuery();
        int id = 0;
        if (rs.next()) id = rs.getInt("id");
        rs.close();
        ps.close();
        return id;
    }

    private static void atualizarPerfilPorNivel(Connection conn, String nivel,
                                                  int perfilId) throws SQLException {
        if (perfilId <= 0) return;
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios SET perfil_id = ? WHERE nivel = ? AND (perfil_id IS NULL OR perfil_id = 0)");
        ps.setInt(1, perfilId);
        ps.setString(2, nivel);
        int updated = ps.executeUpdate();
        ps.close();
        if (updated > 0) {
            Log.d(TAG, "Migrados " + updated + " usuarios com nivel '" + nivel + "' para perfil ID " + perfilId);
        }
    }
}
