package com.pdv.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

import java.sql.*;
import java.util.*;

/**
 * VinculosActivity - Gerencia vínculos entre usuários, caixas e turnos.
 * Um usuário pode estar vinculado a múltiplos caixas e turnos.
 * Inclui relatório de vínculos.
 */
public class VinculosActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private Spinner spFiltroUsuario;
    private List<Integer> usuarioIds = new ArrayList<>();
    private List<String> usuarioNomes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vinculos);

        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.VINCULOS_ACESSAR)) {
            return;
        }

        spFiltroUsuario = findViewById(R.id.spFiltroUsuario);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_vinculo, (holder, item, pos) -> {
            holder.setText(R.id.tvUsuario, safeStr(item.get("usuario_nome")));
            holder.setText(R.id.tvCaixa, "Caixa: " + safeStr(item.get("caixa_nome")));
            holder.setText(R.id.tvTurno, "Turno: " + safeStr(item.get("turno_nome")));

            Button btnExcluir = holder.find(R.id.btnExcluir);
            if (btnExcluir != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.VINCULOS_EXCLUIR)) {
                    btnExcluir.setVisibility(View.VISIBLE);
                    btnExcluir.setOnClickListener(v -> excluirVinculo(item));
                } else {
                    btnExcluir.setVisibility(View.GONE);
                }
            }
        });

        recyclerView.setAdapter(adapter);

        Button btnNovoVinculo = findViewById(R.id.btnNovoVinculo);
        PermissionHelper.controlarVisibilidade(this, btnNovoVinculo, PermissionConstants.VINCULOS_CRIAR);
        btnNovoVinculo.setOnClickListener(v -> showNovoVinculo());

        Button btnRelatorio = findViewById(R.id.btnRelatorio);
        PermissionHelper.controlarVisibilidade(this, btnRelatorio, PermissionConstants.VINCULOS_RELATORIO);
        btnRelatorio.setOnClickListener(v -> gerarRelatorio());

        Button btnFiltrar = findViewById(R.id.btnFiltrar);
        if (btnFiltrar != null) btnFiltrar.setOnClickListener(v -> loadData());

        carregarUsuariosSpinner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void carregarUsuariosSpinner() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM usuarios WHERE ativo = 1 ORDER BY nome");

                usuarioIds.clear();
                usuarioNomes.clear();
                usuarioIds.add(0);
                usuarioNomes.add("Todos os Usuarios");

                while (rs.next()) {
                    usuarioIds.add(rs.getInt("id"));
                    usuarioNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    ArrayAdapter<String> ua = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, usuarioNomes);
                    ua.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spFiltroUsuario.setAdapter(ua);
                    loadData();
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                int usuarioPos = spFiltroUsuario.getSelectedItemPosition();
                int usuarioId = usuarioPos >= 0 && usuarioPos < usuarioIds.size() ? usuarioIds.get(usuarioPos) : 0;

                StringBuilder sql = new StringBuilder();
                sql.append("SELECT uct.id, u.nome as usuario_nome, u.id as usuario_id, ");
                sql.append("cn.nome as caixa_nome, cn.id as caixa_id, ");
                sql.append("t.nome as turno_nome, t.id as turno_id ");
                sql.append("FROM usuario_caixa_turno uct ");
                sql.append("LEFT JOIN usuarios u ON uct.usuario_id = u.id ");
                sql.append("LEFT JOIN caixas_nominais cn ON uct.caixa_nominal_id = cn.id ");
                sql.append("LEFT JOIN turnos t ON uct.turno_id = t.id ");
                sql.append("WHERE 1=1 ");

                if (usuarioId > 0) {
                    sql.append("AND uct.usuario_id = ").append(usuarioId).append(" ");
                }
                sql.append("ORDER BY u.nome ASC, cn.nome ASC, t.nome ASC");

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql.toString());

                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("usuario_id", rs.getInt("usuario_id"));
                    m.put("usuario_nome", rs.getString("usuario_nome"));
                    m.put("caixa_id", rs.getInt("caixa_id"));
                    m.put("caixa_nome", rs.getString("caixa_nome") != null ? rs.getString("caixa_nome") : "Sem caixa");
                    m.put("turno_id", rs.getInt("turno_id"));
                    m.put("turno_nome", rs.getString("turno_nome") != null ? rs.getString("turno_nome") : "Sem turno");
                    list.add(m);
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showNovoVinculo() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        TextView tvUsuarioLabel = new TextView(this);
        tvUsuarioLabel.setText("Usuario *:");
        tvUsuarioLabel.setTextColor(0xFF00BCD4);
        tvUsuarioLabel.setTextSize(13);
        layout.addView(tvUsuarioLabel);

        Spinner spUsuario = new Spinner(this);
        spUsuario.setBackgroundResource(R.drawable.input_bg);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 16);
        spUsuario.setLayoutParams(lp);
        layout.addView(spUsuario);

        TextView tvCaixaLabel = new TextView(this);
        tvCaixaLabel.setText("Caixa (opcional):");
        tvCaixaLabel.setTextColor(0xFF00BCD4);
        tvCaixaLabel.setTextSize(13);
        layout.addView(tvCaixaLabel);

        Spinner spCaixa = new Spinner(this);
        spCaixa.setBackgroundResource(R.drawable.input_bg);
        spCaixa.setLayoutParams(lp);
        layout.addView(spCaixa);

        TextView tvTurnoLabel = new TextView(this);
        tvTurnoLabel.setText("Turno (opcional):");
        tvTurnoLabel.setTextColor(0xFF00BCD4);
        tvTurnoLabel.setTextSize(13);
        layout.addView(tvTurnoLabel);

        Spinner spTurno = new Spinner(this);
        spTurno.setBackgroundResource(R.drawable.input_bg);
        spTurno.setLayoutParams(lp);
        layout.addView(spTurno);

        final List<Integer> uIds = new ArrayList<>();
        final List<String> uNomes = new ArrayList<>();
        final List<Integer> cIds = new ArrayList<>();
        final List<String> cNomes = new ArrayList<>();
        final List<Integer> tIds = new ArrayList<>();
        final List<String> tNomes = new ArrayList<>();

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();

                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM usuarios WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    uIds.add(rs.getInt("id"));
                    uNomes.add(rs.getString("nome"));
                }
                rs.close();

                cIds.add(0);
                cNomes.add("-- Sem caixa --");
                rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    cIds.add(rs.getInt("id"));
                    cNomes.add(rs.getString("nome"));
                }
                rs.close();

                tIds.add(0);
                tNomes.add("-- Sem turno --");
                rs = stmt.executeQuery("SELECT id, nome FROM turnos WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) {
                    tIds.add(rs.getInt("id"));
                    tNomes.add(rs.getString("nome"));
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    ArrayAdapter<String> ua = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, uNomes);
                    ua.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spUsuario.setAdapter(ua);

                    ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);

                    ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tNomes);
                    ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spTurno.setAdapter(ta);
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle("Novo Vinculo")
                .setView(layout)
                .setPositiveButton("Vincular", (d, w) -> {
                    int uPos = spUsuario.getSelectedItemPosition();
                    int cPos = spCaixa.getSelectedItemPosition();
                    int tPos = spTurno.getSelectedItemPosition();

                    if (uIds.isEmpty() || uPos < 0 || uPos >= uIds.size()) {
                        showError("Selecione um usuario.");
                        return;
                    }
                    int uId = uIds.get(uPos);
                    int cId = cPos >= 0 && cPos < cIds.size() ? cIds.get(cPos) : 0;
                    int tId = tPos >= 0 && tPos < tIds.size() ? tIds.get(tPos) : 0;

                    if (cId == 0 && tId == 0) {
                        showError("Selecione pelo menos um caixa ou turno.");
                        return;
                    }
                    salvarVinculo(uId, cId, tId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void salvarVinculo(int usuarioId, int caixaId, int turnoId) {
        showLoading("Salvando vinculo...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO usuario_caixa_turno (usuario_id, caixa_nominal_id, turno_id) VALUES (?,?,?)");
                ps.setInt(1, usuarioId);
                if (caixaId > 0) ps.setInt(2, caixaId); else ps.setNull(2, Types.INTEGER);
                if (turnoId > 0) ps.setInt(3, turnoId); else ps.setNull(3, Types.INTEGER);
                ps.executeUpdate();
                ps.close();

                hideLoading();
                showToast("Vinculo criado com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void excluirVinculo(Map<String, Object> item) {
        showConfirm("Excluir Vinculo", "Deseja excluir este vinculo?", () -> {
            showLoading("Excluindo...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    int id = ((Number) item.get("id")).intValue();
                    PreparedStatement ps = conn.prepareStatement("DELETE FROM usuario_caixa_turno WHERE id = ?");
                    ps.setInt(1, id);
                    ps.executeUpdate();
                    ps.close();
                    hideLoading();
                    showToast("Vinculo excluido!");
                    loadData();
                } catch (Exception e) {
                    hideLoading();
                    showErrorFromException(e, ErrorHandler.CTX_SALVAR);
                }
            }).start();
        });
    }

    private void gerarRelatorio() {
        showLoading("Gerando relatorio...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT u.nome as usuario_nome, u.login, " +
                        "cn.nome as caixa_nome, t.nome as turno_nome, t.hora_inicio, t.hora_fim " +
                        "FROM usuario_caixa_turno uct " +
                        "LEFT JOIN usuarios u ON uct.usuario_id = u.id " +
                        "LEFT JOIN caixas_nominais cn ON uct.caixa_nominal_id = cn.id " +
                        "LEFT JOIN turnos t ON uct.turno_id = t.id " +
                        "ORDER BY u.nome ASC, cn.nome ASC, t.nome ASC");

                StringBuilder sb = new StringBuilder();
                sb.append("RELATORIO DE VINCULOS\n");
                sb.append("======================\n\n");

                String lastUsuario = "";
                while (rs.next()) {
                    String usuario = rs.getString("usuario_nome") + " (" + rs.getString("login") + ")";
                    if (!usuario.equals(lastUsuario)) {
                        if (!lastUsuario.isEmpty()) sb.append("\n");
                        sb.append("USUARIO: ").append(usuario).append("\n");
                        lastUsuario = usuario;
                    }
                    String caixa = rs.getString("caixa_nome");
                    String turno = rs.getString("turno_nome");
                    String horaInicio = rs.getString("hora_inicio");
                    String horaFim = rs.getString("hora_fim");

                    if (caixa != null) {
                        sb.append("  Caixa: ").append(caixa).append("\n");
                    }
                    if (turno != null) {
                        sb.append("  Turno: ").append(turno);
                        if (horaInicio != null && horaFim != null) {
                            sb.append(" (").append(horaInicio).append(" - ").append(horaFim).append(")");
                        }
                        sb.append("\n");
                    }
                    sb.append("  ---\n");
                }
                rs.close();
                stmt.close();

                if (sb.toString().equals("RELATORIO DE VINCULOS\n======================\n\n")) {
                    sb.append("Nenhum vinculo cadastrado.");
                }

                hideLoading();
                final String texto = sb.toString();
                runOnUiThread(() -> {
                    android.widget.ScrollView sv = new android.widget.ScrollView(this);
                    TextView tv = new TextView(this);
                    tv.setText(texto);
                    tv.setTextColor(0xFFB0BEC5);
                    tv.setTextSize(13);
                    int p = (int) (16 * getResources().getDisplayMetrics().density);
                    tv.setPadding(p, p, p, p);
                    sv.addView(tv);

                    new AlertDialog.Builder(this)
                            .setTitle("Relatorio de Vinculos")
                            .setView(sv)
                            .setPositiveButton("Fechar", null)
                            .show();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
