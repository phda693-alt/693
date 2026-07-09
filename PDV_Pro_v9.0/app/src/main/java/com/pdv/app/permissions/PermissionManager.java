package com.pdv.app.permissions;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.models.Perfil;
import com.pdv.app.models.Permissao;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Gerenciador central de permissoes do sistema PDV Pro.
 * 
 * v6.0.0 - Sistema de Permissoes Avancado:
 * - Permissoes por perfil (base)
 * - Permissoes individuais por usuario (override do perfil)
 * - Controle de visibilidade E habilitacao independentes
 * - Verificacao de caixa aberto para vendas
 * 
 * Hierarquia de permissoes:
 *   1. Perfil define as permissoes base
 *   2. Permissoes individuais do usuario podem ADICIONAR ou REMOVER permissoes
 *   3. Administrador sistematico tem TODAS as permissoes (nao pode ser alterado)
 * 
 * Padrao Singleton com cache em memoria para performance.
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static PermissionManager instance;
    private Context context;

    // Cache de permissoes do usuario logado (resultado final apos merge perfil+usuario)
    private Set<String> permissoesCache;
    private int cachedUserId = -1;
    private int cachedPerfilId = -1;
    private String cachedPerfilNome = "";
    private boolean isAdmin = false;

    private PermissionManager(Context context) {
        this.context = context.getApplicationContext();
        this.permissoesCache = new HashSet<>();
    }

    public static synchronized PermissionManager getInstance(Context context) {
        if (instance == null) {
            instance = new PermissionManager(context);
        }
        return instance;
    }

    // =========================================================================
    // VERIFICACAO DE PERMISSOES
    // =========================================================================

    public boolean temPermissao(String chavePermissao) {
        if (isAdmin) return true;
        if (permissoesCache == null || permissoesCache.isEmpty()) {
            carregarCacheLocal();
        }
        return permissoesCache.contains(chavePermissao);
    }

    public boolean temTodasPermissoes(String... chaves) {
        if (isAdmin) return true;
        for (String chave : chaves) {
            if (!temPermissao(chave)) return false;
        }
        return true;
    }

    public boolean temAlgumaPermissao(String... chaves) {
        if (isAdmin) return true;
        for (String chave : chaves) {
            if (temPermissao(chave)) return true;
        }
        return false;
    }

    public boolean isAdministrador() { return isAdmin; }
    public String getPerfilNome() { return cachedPerfilNome; }
    public int getPerfilId() { return cachedPerfilId; }
    public int getCachedUserId() { return cachedUserId; }

    // =========================================================================
    // VERIFICACAO DE CAIXA ABERTO
    // =========================================================================

    /**
     * Verifica se existe um caixa aberto no sistema.
     * DEVE ser chamado em background thread.
     * 
     * @return true se ha um caixa aberto
     */
    public boolean isCaixaAberto() {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM caixa WHERE status = 'aberto'");
            rs.next();
            boolean aberto = rs.getInt(1) > 0;
            rs.close();
            stmt.close();
            return aberto;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar caixa aberto: " + e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // CARREGAMENTO DE PERMISSOES (com suporte a overrides por usuario)
    // =========================================================================

    /**
     * Carrega as permissoes do usuario a partir do banco de dados.
     * 
     * Fluxo:
     * 1. Carrega permissoes do perfil (base)
     * 2. Aplica overrides individuais do usuario (adicionar/remover)
     * 3. Resultado final = permissoes do perfil + adicionadas - removidas
     */
    public void carregarPermissoes(int userId) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();

            // Buscar perfil do usuario (incluindo campo nivel como fallback)
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.perfil_id, u.nivel, p.nome as perfil_nome, p.sistematico " +
                    "FROM usuarios u " +
                    "LEFT JOIN perfis p ON u.perfil_id = p.id " +
                    "WHERE u.id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            int perfilId = 0;
            String perfilNome = "Sem Perfil";
            boolean sistematico = false;
            String nivelUsuario = "operador";

            if (rs.next()) {
                perfilId = rs.getInt("perfil_id");
                perfilNome = rs.getString("perfil_nome");
                sistematico = rs.getInt("sistematico") == 1;
                nivelUsuario = rs.getString("nivel");
                if (nivelUsuario == null) nivelUsuario = "operador";
                if (perfilNome == null) perfilNome = "Sem Perfil";
            }
            rs.close();
            ps.close();

            // ============================================================
            // FALLBACK: Se o usuario nao tem perfil atribuido (perfil_id NULL/0),
            // tentar atribuir automaticamente baseado no campo 'nivel'
            // ============================================================
            if (perfilId <= 0 || "Sem Perfil".equals(perfilNome)) {
                perfilId = tentarAtribuirPerfilAutomatico(conn, userId, nivelUsuario);
                if (perfilId > 0) {
                    // Recarregar dados do perfil atribuido
                    PreparedStatement psReload = conn.prepareStatement(
                            "SELECT p.nome, p.sistematico FROM perfis p WHERE p.id = ?");
                    psReload.setInt(1, perfilId);
                    ResultSet rsReload = psReload.executeQuery();
                    if (rsReload.next()) {
                        perfilNome = rsReload.getString("nome");
                        sistematico = rsReload.getInt("sistematico") == 1;
                        if (perfilNome == null) perfilNome = "Sem Perfil";
                    }
                    rsReload.close();
                    psReload.close();
                    Log.d(TAG, "Perfil atribuido automaticamente: " + perfilNome + " (ID: " + perfilId + ")");
                }
            }

            this.cachedUserId = userId;
            this.cachedPerfilId = perfilId;
            this.cachedPerfilNome = perfilNome;

            // Administrador sistematico tem TODAS as permissoes
            // Tambem verifica pelo campo nivel='admin' como fallback robusto
            boolean isAdminByPerfil = sistematico && "Administrador".equalsIgnoreCase(perfilNome);
            boolean isAdminByNivel = "admin".equalsIgnoreCase(nivelUsuario);

            if (isAdminByPerfil || isAdminByNivel) {
                this.isAdmin = true;
                this.permissoesCache = new HashSet<>(PermissionConstants.getTodasChaves());
                // Se detectou admin pelo nivel mas nao pelo perfil, atualizar o nome exibido
                if (isAdminByNivel && !isAdminByPerfil) {
                    this.cachedPerfilNome = "Administrador";
                }
            } else {
                this.isAdmin = false;
                this.permissoesCache = new HashSet<>();

                // PASSO 1: Carregar permissoes do perfil (base)
                if (perfilId > 0) {
                    PreparedStatement psPerms = conn.prepareStatement(
                            "SELECT pe.chave FROM perfil_permissoes pp " +
                            "JOIN permissoes pe ON pp.permissao_id = pe.id " +
                            "WHERE pp.perfil_id = ?");
                    psPerms.setInt(1, perfilId);
                    ResultSet rsPerms = psPerms.executeQuery();
                    while (rsPerms.next()) {
                        permissoesCache.add(rsPerms.getString("chave"));
                    }
                    rsPerms.close();
                    psPerms.close();
                }

                // PASSO 2: Aplicar overrides individuais do usuario
                try {
                    PreparedStatement psOverrides = conn.prepareStatement(
                            "SELECT pe.chave, up.tipo FROM usuario_permissoes up " +
                            "JOIN permissoes pe ON up.permissao_id = pe.id " +
                            "WHERE up.usuario_id = ?");
                    psOverrides.setInt(1, userId);
                    ResultSet rsOverrides = psOverrides.executeQuery();
                    while (rsOverrides.next()) {
                        String chave = rsOverrides.getString("chave");
                        String tipo = rsOverrides.getString("tipo");
                        if ("adicionar".equals(tipo)) {
                            permissoesCache.add(chave);
                        } else if ("remover".equals(tipo)) {
                            permissoesCache.remove(chave);
                        }
                    }
                    rsOverrides.close();
                    psOverrides.close();
                } catch (Exception overrideEx) {
                    // Tabela pode nao existir ainda - nao e critico
                    Log.w(TAG, "Overrides de usuario nao disponiveis: " + overrideEx.getMessage());
                }
            }

            // Salvar em cache local
            salvarCacheLocal();

            Log.d(TAG, "Permissoes carregadas para usuario " + userId +
                    " (perfil: " + perfilNome + ", admin: " + isAdmin +
                    ", permissoes: " + permissoesCache.size() + ")");

        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar permissoes, tentando cache local", e);
            carregarCacheLocal();
        }
    }

    private void salvarCacheLocal() {
        SharedPreferences prefs = context.getSharedPreferences("permissions_cache", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("user_id", cachedUserId);
        editor.putInt("perfil_id", cachedPerfilId);
        editor.putString("perfil_nome", cachedPerfilNome);
        editor.putBoolean("is_admin", isAdmin);
        editor.putStringSet("permissoes", permissoesCache);
        editor.apply();
    }

    private void carregarCacheLocal() {
        SharedPreferences prefs = context.getSharedPreferences("permissions_cache", Context.MODE_PRIVATE);
        cachedUserId = prefs.getInt("user_id", -1);
        cachedPerfilId = prefs.getInt("perfil_id", -1);
        cachedPerfilNome = prefs.getString("perfil_nome", "Sem Perfil");
        isAdmin = prefs.getBoolean("is_admin", false);
        Set<String> saved = prefs.getStringSet("permissoes", null);
        permissoesCache = saved != null ? new HashSet<>(saved) : new HashSet<>();
    }

    public void invalidarCache() {
        permissoesCache.clear();
        cachedUserId = -1;
        cachedPerfilId = -1;
        cachedPerfilNome = "";
        isAdmin = false;
        SharedPreferences prefs = context.getSharedPreferences("permissions_cache", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.d(TAG, "Cache de permissoes invalidado");
    }

    // =========================================================================
    // ATRIBUICAO AUTOMATICA DE PERFIL (FALLBACK)
    // =========================================================================

    /**
     * Tenta atribuir automaticamente um perfil ao usuario baseado no campo 'nivel'.
     * Isso corrige o caso em que o perfil_id ficou NULL/0 (ex: admin criado antes
     * do sistema de permissoes, ou migracao que falhou).
     *
     * @param conn Conexao com o banco
     * @param userId ID do usuario
     * @param nivel Campo nivel do usuario (admin, gerente, operador, etc.)
     * @return ID do perfil atribuido, ou 0 se nao conseguiu
     */
    private int tentarAtribuirPerfilAutomatico(Connection conn, int userId, String nivel) {
        try {
            String nomePerfil;
            switch (nivel != null ? nivel.toLowerCase() : "") {
                case "admin":
                    nomePerfil = "Administrador";
                    break;
                case "gerente":
                    nomePerfil = "Gerente";
                    break;
                case "operador":
                    nomePerfil = "Operacional";
                    break;
                case "caixa":
                    nomePerfil = "Caixa";
                    break;
                case "atendente":
                    nomePerfil = "Atendente";
                    break;
                case "garcom":
                    nomePerfil = "Garcom";
                    break;
                case "balcao":
                    nomePerfil = "Balcao";
                    break;
                case "vendedor":
                    nomePerfil = "Vendedor";
                    break;
                case "estoquista":
                    nomePerfil = "Estoquista";
                    break;
                case "entregador":
                    nomePerfil = "Entregador";
                    break;
                default:
                    nomePerfil = "Operacional";
                    break;
            }

            // Buscar o perfil correspondente
            PreparedStatement psBusca = conn.prepareStatement(
                    "SELECT id FROM perfis WHERE nome = ? AND ativo = 1");
            psBusca.setString(1, nomePerfil);
            ResultSet rsBusca = psBusca.executeQuery();
            int perfilId = 0;
            if (rsBusca.next()) {
                perfilId = rsBusca.getInt("id");
            }
            rsBusca.close();
            psBusca.close();

            if (perfilId > 0) {
                // Atualizar o usuario com o perfil encontrado
                PreparedStatement psUpdate = conn.prepareStatement(
                        "UPDATE usuarios SET perfil_id = ? WHERE id = ? AND (perfil_id IS NULL OR perfil_id = 0)");
                psUpdate.setInt(1, perfilId);
                psUpdate.setInt(2, userId);
                int updated = psUpdate.executeUpdate();
                psUpdate.close();

                if (updated > 0) {
                    Log.d(TAG, "Perfil '" + nomePerfil + "' (ID: " + perfilId +
                            ") atribuido automaticamente ao usuario ID " + userId +
                            " (nivel: " + nivel + ")");
                }
                return perfilId;
            } else {
                Log.w(TAG, "Perfil '" + nomePerfil + "' nao encontrado para atribuicao automatica");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tentar atribuir perfil automatico: " + e.getMessage());
        }
        return 0;
    }

    // =========================================================================
    // CRUD DE PERFIS
    // =========================================================================

    public List<Perfil> listarPerfis() throws SQLException {
        List<Perfil> perfis = new ArrayList<>();
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM perfis WHERE ativo = 1 ORDER BY sistematico DESC, nome ASC");
        while (rs.next()) {
            Perfil p = new Perfil();
            p.setId(rs.getInt("id"));
            p.setNome(rs.getString("nome"));
            p.setDescricao(rs.getString("descricao"));
            p.setSistematico(rs.getInt("sistematico") == 1);
            p.setAtivo(rs.getInt("ativo") == 1);
            try {
                p.setPersonalizavel(rs.getInt("personalizavel") == 1);
            } catch (Exception e) {
                p.setPersonalizavel(false);
            }
            perfis.add(p);
        }
        rs.close();
        stmt.close();
        return perfis;
    }

    public int criarPerfil(String nome, String descricao) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO perfis (nome, descricao, sistematico, ativo) VALUES (?, ?, 0, 1)",
                Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, nome);
        ps.setString(2, descricao);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        int id = 0;
        if (keys.next()) id = keys.getInt(1);
        keys.close();
        ps.close();
        return id;
    }

    public void atualizarPerfil(int id, String nome, String descricao) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE perfis SET nome = ?, descricao = ? WHERE id = ? AND sistematico = 0");
        ps.setString(1, nome);
        ps.setString(2, descricao);
        ps.setInt(3, id);
        ps.executeUpdate();
        ps.close();
    }

    public void excluirPerfil(int id) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE perfis SET ativo = 0 WHERE id = ? AND sistematico = 0");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    // =========================================================================
    // GERENCIAMENTO DE PERMISSOES DO PERFIL
    // =========================================================================

    public List<Permissao> getPermissoesDoPerfil(int perfilId) throws SQLException {
        List<Permissao> todas = PermissionConstants.getTodasPermissoes();
        Set<String> concedidas = new HashSet<>();
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT pe.chave FROM perfil_permissoes pp " +
                "JOIN permissoes pe ON pp.permissao_id = pe.id " +
                "WHERE pp.perfil_id = ?");
        ps.setInt(1, perfilId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            concedidas.add(rs.getString("chave"));
        }
        rs.close();
        ps.close();

        for (Permissao p : todas) {
            p.setConcedida(concedidas.contains(p.getChave()));
        }
        return todas;
    }

    public void salvarPermissoesDoPerfil(int perfilId, List<String> chavesPermitidas) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();

        PreparedStatement psDel = conn.prepareStatement(
                "DELETE FROM perfil_permissoes WHERE perfil_id = ?");
        psDel.setInt(1, perfilId);
        psDel.executeUpdate();
        psDel.close();

        if (!chavesPermitidas.isEmpty()) {
            PreparedStatement psIns = conn.prepareStatement(
                    "INSERT INTO perfil_permissoes (perfil_id, permissao_id) " +
                    "SELECT ?, id FROM permissoes WHERE chave = ?");
            for (String chave : chavesPermitidas) {
                psIns.setInt(1, perfilId);
                psIns.setString(2, chave);
                psIns.addBatch();
            }
            psIns.executeBatch();
            psIns.close();
        }

        if (perfilId == cachedPerfilId) {
            invalidarCache();
        }

        Log.d(TAG, "Permissoes do perfil " + perfilId + " atualizadas: " + chavesPermitidas.size() + " permissoes");
    }

    public void concederPermissao(int perfilId, String chavePermissao) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "INSERT IGNORE INTO perfil_permissoes (perfil_id, permissao_id) " +
                "SELECT ?, id FROM permissoes WHERE chave = ?");
        ps.setInt(1, perfilId);
        ps.setString(2, chavePermissao);
        ps.executeUpdate();
        ps.close();

        if (perfilId == cachedPerfilId) {
            permissoesCache.add(chavePermissao);
            salvarCacheLocal();
        }
    }

    public void revogarPermissao(int perfilId, String chavePermissao) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM perfil_permissoes WHERE perfil_id = ? AND permissao_id = " +
                "(SELECT id FROM permissoes WHERE chave = ?)");
        ps.setInt(1, perfilId);
        ps.setString(2, chavePermissao);
        ps.executeUpdate();
        ps.close();

        if (perfilId == cachedPerfilId) {
            permissoesCache.remove(chavePermissao);
            salvarCacheLocal();
        }
    }

    public void concederTodasPermissoes(int perfilId) throws SQLException {
        salvarPermissoesDoPerfil(perfilId, PermissionConstants.getTodasChaves());
    }

    public void revogarTodasPermissoes(int perfilId) throws SQLException {
        salvarPermissoesDoPerfil(perfilId, new ArrayList<>());
    }

    // =========================================================================
    // PERMISSOES INDIVIDUAIS POR USUARIO (OVERRIDES)
    // =========================================================================

    /**
     * Retorna as permissoes efetivas de um usuario especifico,
     * considerando perfil + overrides individuais.
     * Cada Permissao tera o campo 'concedida' preenchido e um campo extra
     * indicando se e override ou herdada do perfil.
     */
    public List<Permissao> getPermissoesEfetivasDoUsuario(int userId) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();

        // 1. Buscar perfil do usuario
        int perfilId = 0;
        PreparedStatement psPerfil = conn.prepareStatement(
                "SELECT perfil_id FROM usuarios WHERE id = ?");
        psPerfil.setInt(1, userId);
        ResultSet rsPerfil = psPerfil.executeQuery();
        if (rsPerfil.next()) perfilId = rsPerfil.getInt("perfil_id");
        rsPerfil.close();
        psPerfil.close();

        // 2. Carregar permissoes do perfil
        Set<String> permissoesPerfil = new HashSet<>();
        if (perfilId > 0) {
            PreparedStatement psPerms = conn.prepareStatement(
                    "SELECT pe.chave FROM perfil_permissoes pp " +
                    "JOIN permissoes pe ON pp.permissao_id = pe.id " +
                    "WHERE pp.perfil_id = ?");
            psPerms.setInt(1, perfilId);
            ResultSet rsPerms = psPerms.executeQuery();
            while (rsPerms.next()) {
                permissoesPerfil.add(rsPerms.getString("chave"));
            }
            rsPerms.close();
            psPerms.close();
        }

        // 3. Carregar overrides do usuario
        Map<String, String> overrides = new LinkedHashMap<>(); // chave -> tipo (adicionar/remover)
        try {
            PreparedStatement psOv = conn.prepareStatement(
                    "SELECT pe.chave, up.tipo FROM usuario_permissoes up " +
                    "JOIN permissoes pe ON up.permissao_id = pe.id " +
                    "WHERE up.usuario_id = ?");
            psOv.setInt(1, userId);
            ResultSet rsOv = psOv.executeQuery();
            while (rsOv.next()) {
                overrides.put(rsOv.getString("chave"), rsOv.getString("tipo"));
            }
            rsOv.close();
            psOv.close();
        } catch (Exception e) {
            Log.w(TAG, "Tabela usuario_permissoes nao disponivel: " + e.getMessage());
        }

        // 4. Montar lista final
        List<Permissao> todas = PermissionConstants.getTodasPermissoes();
        for (Permissao p : todas) {
            boolean doPerfil = permissoesPerfil.contains(p.getChave());
            String override = overrides.get(p.getChave());

            if (override != null) {
                if ("adicionar".equals(override)) {
                    p.setConcedida(true);
                } else if ("remover".equals(override)) {
                    p.setConcedida(false);
                }
            } else {
                p.setConcedida(doPerfil);
            }
        }

        return todas;
    }

    /**
     * Retorna os overrides individuais de um usuario.
     * @return Map de chave -> tipo ("adicionar" ou "remover")
     */
    public Map<String, String> getOverridesDoUsuario(int userId) throws SQLException {
        Map<String, String> overrides = new LinkedHashMap<>();
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT pe.chave, up.tipo FROM usuario_permissoes up " +
                    "JOIN permissoes pe ON up.permissao_id = pe.id " +
                    "WHERE up.usuario_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                overrides.put(rs.getString("chave"), rs.getString("tipo"));
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            Log.w(TAG, "Erro ao carregar overrides: " + e.getMessage());
        }
        return overrides;
    }

    /**
     * Salva os overrides individuais de um usuario.
     * Remove todos os overrides anteriores e insere os novos.
     * 
     * @param userId ID do usuario
     * @param adicionadas Chaves de permissoes a ADICIONAR (que o perfil nao tem)
     * @param removidas Chaves de permissoes a REMOVER (que o perfil tem)
     */
    public void salvarOverridesDoUsuario(int userId, List<String> adicionadas, List<String> removidas) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        boolean oldAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // Remover e recriar dentro da mesma transacao evita perder as
            // permissoes anteriores se qualquer insercao falhar.
            PreparedStatement psDel = conn.prepareStatement(
                    "DELETE FROM usuario_permissoes WHERE usuario_id = ?");
            psDel.setInt(1, userId);
            psDel.executeUpdate();
            psDel.close();

            inserirOverrides(conn, userId, adicionadas, "adicionar");
            inserirOverrides(conn, userId, removidas, "remover");
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { conn.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) {}
        }

        // Se e o usuario logado, recarregar permissoes
        if (userId == cachedUserId) {
            carregarPermissoes(userId);
        }

        int total = adicionadas.size() + removidas.size();
        Log.d(TAG, "Overrides do usuario " + userId + " salvos: " +
                adicionadas.size() + " adicionadas, " + removidas.size() + " removidas");
    }

    private void inserirOverrides(Connection conn, int userId, List<String> chaves, String tipo)
            throws SQLException {
        if (chaves.isEmpty()) return;
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO usuario_permissoes (usuario_id, permissao_id, tipo) "
                        + "SELECT ?, id, ? FROM permissoes WHERE chave = ? "
                        + "ON DUPLICATE KEY UPDATE tipo = VALUES(tipo)");
        for (String chave : chaves) {
            ps.setInt(1, userId);
            ps.setString(2, tipo);
            ps.setString(3, chave);
            ps.addBatch();
        }
        ps.executeBatch();
        ps.close();
    }

    /**
     * Remove todos os overrides individuais de um usuario (volta ao padrao do perfil).
     */
    public void limparOverridesDoUsuario(int userId) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM usuario_permissoes WHERE usuario_id = ?");
        ps.setInt(1, userId);
        ps.executeUpdate();
        ps.close();

        if (userId == cachedUserId) {
            carregarPermissoes(userId);
        }
    }

    // =========================================================================
    // LISTAR USUARIOS (para tela de permissoes por usuario)
    // =========================================================================

    /**
     * Lista todos os usuarios ativos com seus perfis.
     * Retorna Map com id, nome, login, perfil_nome, perfil_id
     */
    public List<Map<String, Object>> listarUsuariosComPerfil() throws SQLException {
        List<Map<String, Object>> usuarios = new ArrayList<>();
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT u.id, u.nome, u.login, u.nivel, " +
                "COALESCE(p.nome, 'Sem Perfil') as perfil_nome, " +
                "COALESCE(u.perfil_id, 0) as perfil_id, " +
                "p.sistematico " +
                "FROM usuarios u " +
                "LEFT JOIN perfis p ON u.perfil_id = p.id " +
                "WHERE u.ativo = 1 " +
                "ORDER BY u.nome");
        while (rs.next()) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", rs.getInt("id"));
            user.put("nome", rs.getString("nome"));
            user.put("login", rs.getString("login"));
            user.put("nivel", rs.getString("nivel"));
            user.put("perfil_nome", rs.getString("perfil_nome"));
            user.put("perfil_id", rs.getInt("perfil_id"));
            user.put("sistematico", rs.getInt("sistematico") == 1);
            usuarios.add(user);
        }
        rs.close();
        stmt.close();
        return usuarios;
    }

    /**
     * Conta quantos overrides um usuario tem.
     */
    public int contarOverridesDoUsuario(int userId) throws SQLException {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(context);
            Connection conn = db.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM usuario_permissoes WHERE usuario_id = ?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            int count = 0;
            if (rs.next()) count = rs.getInt(1);
            rs.close();
            ps.close();
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // =========================================================================
    // ATRIBUICAO DE PERFIL AO USUARIO
    // =========================================================================

    public void atribuirPerfil(int userId, int perfilId) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE usuarios SET perfil_id = ? WHERE id = ?");
        ps.setInt(1, perfilId);
        ps.setInt(2, userId);
        ps.executeUpdate();
        ps.close();

        if (userId == cachedUserId) {
            carregarPermissoes(userId);
        }
    }

    public int contarUsuariosPorPerfil(int perfilId) throws SQLException {
        DatabaseHelper db = DatabaseHelper.getInstance(context);
        Connection conn = db.getConnection();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM usuarios WHERE perfil_id = ? AND ativo = 1");
        ps.setInt(1, perfilId);
        ResultSet rs = ps.executeQuery();
        int count = 0;
        if (rs.next()) count = rs.getInt(1);
        rs.close();
        ps.close();
        return count;
    }
}
