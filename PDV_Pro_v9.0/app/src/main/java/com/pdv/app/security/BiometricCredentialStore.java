package com.pdv.app.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia dos vinculos usuario/senha autorizados por biometria.
 */
public final class BiometricCredentialStore {
    private BiometricCredentialStore() {}

    public static final class Credential {
        public final int id;
        public final int usuarioId;
        public final String usuarioNome;
        public final String login;
        public final String descricao;
        public final String criadoEm;
        public final String ultimoUso;

        public Credential(int id, int usuarioId, String usuarioNome, String login,
                          String descricao, String criadoEm, String ultimoUso) {
            this.id = id;
            this.usuarioId = usuarioId;
            this.usuarioNome = usuarioNome;
            this.login = login;
            this.descricao = descricao;
            this.criadoEm = criadoEm;
            this.ultimoUso = ultimoUso;
        }

        public String label() {
            return descricao + " - " + usuarioNome + " (" + login + ")";
        }

        public String detail() {
            String detail = "Criada em: " + (criadoEm == null ? "--" : criadoEm);
            if (ultimoUso != null && !ultimoUso.trim().isEmpty()) {
                detail += "\nUltimo uso: " + ultimoUso;
            }
            return detail;
        }
    }

    public static final class SessionUser {
        public final int id;
        public final String nome;
        public final String nivel;
        public final String login;

        public SessionUser(int id, String nome, String nivel, String login) {
            this.id = id;
            this.nome = nome;
            this.nivel = nivel;
            this.login = login;
        }
    }

    /**
     * Garante que a tabela usuarios_biometria e seus indices existam.
     *
     * A migracao principal roda no topo da inicializacao do sistema, mas este
     * fallback deixa o login/cadastro biometrico resiliente em bancos antigos,
     * restaurados manualmente ou criados antes da tabela de biometria existir.
     */
    public static void ensureTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS usuarios_biometria ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY,"
                            + "usuario_id INT NOT NULL,"
                            + "descricao VARCHAR(100) NOT NULL DEFAULT 'Digital',"
                            + "login VARCHAR(50) NOT NULL,"
                            + "senha VARCHAR(100) NOT NULL,"
                            + "ativo TINYINT(1) DEFAULT 1,"
                            + "criado_em DATETIME DEFAULT CURRENT_TIMESTAMP,"
                            + "ultimo_uso DATETIME NULL"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN usuario_id INT DEFAULT 0");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN descricao VARCHAR(100) NOT NULL DEFAULT 'Digital'");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN login VARCHAR(50) DEFAULT ''");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN senha VARCHAR(100) DEFAULT ''");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN ativo TINYINT(1) DEFAULT 1");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN criado_em DATETIME DEFAULT CURRENT_TIMESTAMP");
            addColumnSafe(stmt, "ALTER TABLE usuarios_biometria ADD COLUMN ultimo_uso DATETIME NULL");

            createIndexSafe(stmt, "CREATE INDEX idx_usuarios_biometria_usuario "
                    + "ON usuarios_biometria(usuario_id, ativo)");
            createIndexSafe(stmt, "CREATE INDEX idx_usuarios_biometria_ativo "
                    + "ON usuarios_biometria(ativo, ultimo_uso)");
        }
    }

    public static SessionUser findActiveUserByPassword(Connection conn, String login, String senha)
            throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id,nome,nivel,login FROM usuarios WHERE login=? AND senha=? AND ativo=1")) {
            ps.setString(1, login);
            ps.setString(2, senha);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SessionUser(
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getString("nivel"),
                            rs.getString("login"));
                }
            }
        }
        return null;
    }

    public static int addCredential(Connection conn, int usuarioId, String descricao,
                                    String login, String senha) throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuarios_biometria (usuario_id,descricao,login,senha,ativo) "
                        + "VALUES (?,?,?,?,1)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, usuarioId);
            ps.setString(2, descricao);
            ps.setString(3, login);
            ps.setString(4, senha);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public static List<Credential> listActive(Connection conn) throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT b.id,b.usuario_id,b.descricao,b.login,b.criado_em,b.ultimo_uso,u.nome "
                        + "FROM usuarios_biometria b "
                        + "JOIN usuarios u ON u.id=b.usuario_id "
                        + "WHERE b.ativo=1 AND u.ativo=1 "
                        + "ORDER BY COALESCE(b.ultimo_uso,b.criado_em) DESC,b.id DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                return readCredentials(rs);
            }
        }
    }

    public static List<Credential> listForUser(Connection conn, int usuarioId) throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT b.id,b.usuario_id,b.descricao,b.login,b.criado_em,b.ultimo_uso,u.nome "
                        + "FROM usuarios_biometria b "
                        + "JOIN usuarios u ON u.id=b.usuario_id "
                        + "WHERE b.ativo=1 AND b.usuario_id=? "
                        + "ORDER BY b.id DESC")) {
            ps.setInt(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return readCredentials(rs);
            }
        }
    }

    public static SessionUser getSessionUserForCredential(Connection conn, int credentialId)
            throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT b.usuario_id,b.login,u.nome,u.nivel "
                        + "FROM usuarios_biometria b "
                        + "JOIN usuarios u ON u.id=b.usuario_id "
                        + "WHERE b.id=? AND b.ativo=1 AND u.ativo=1 "
                        + "AND u.login=b.login AND u.senha=b.senha")) {
            ps.setInt(1, credentialId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SessionUser(
                            rs.getInt("usuario_id"),
                            rs.getString("nome"),
                            rs.getString("nivel"),
                            rs.getString("login"));
                }
            }
        }
        return null;
    }

    public static void markUsed(Connection conn, int credentialId) throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios_biometria SET ultimo_uso=NOW() WHERE id=?")) {
            ps.setInt(1, credentialId);
            ps.executeUpdate();
        }
    }

    public static void disableCredential(Connection conn, int credentialId) throws SQLException {
        ensureTable(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios_biometria SET ativo=0 WHERE id=?")) {
            ps.setInt(1, credentialId);
            ps.executeUpdate();
        }
    }

    private static List<Credential> readCredentials(ResultSet rs) throws SQLException {
        List<Credential> list = new ArrayList<>();
        while (rs.next()) {
            list.add(new Credential(
                    rs.getInt("id"),
                    rs.getInt("usuario_id"),
                    rs.getString("nome"),
                    rs.getString("login"),
                    rs.getString("descricao"),
                    rs.getString("criado_em"),
                    rs.getString("ultimo_uso")));
        }
        return list;
    }

    private static void addColumnSafe(Statement stmt, String sql) throws SQLException {
        try {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message == null || (!message.contains("Duplicate")
                    && !message.contains("already exists")
                    && !message.contains("duplicate"))) {
                throw e;
            }
        }
    }

    private static void createIndexSafe(Statement stmt, String sql) throws SQLException {
        try {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message == null || (!message.contains("Duplicate")
                    && !message.contains("already exists")
                    && !message.contains("duplicate"))) {
                throw e;
            }
        }
    }
}
