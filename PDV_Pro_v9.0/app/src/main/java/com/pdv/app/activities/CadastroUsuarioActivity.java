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
import com.pdv.app.models.Perfil;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.security.BiometricAuthHelper;
import com.pdv.app.security.BiometricCredentialStore;
import com.pdv.app.utils.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.pdv.app.utils.ErrorHandler;

public class CadastroUsuarioActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;
    private List<Perfil> perfisDisponiveis = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.USUARIOS_ACESSAR)) {
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Usuarios");

        etBusca = findViewById(R.id.etBusca);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            holder.setText(R.id.tvLine1, safeStr(item.get("line1")));
            holder.setText(R.id.tvLine2, safeStr(item.get("line2")));
            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            // Botao Digitais
            Button btnDigitais = holder.find(R.id.btnDuplicar);
            if (btnDigitais != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.USUARIOS_EDITAR)) {
                    btnDigitais.setVisibility(View.VISIBLE);
                    btnDigitais.setText("Digitais");
                    btnDigitais.setOnClickListener(v -> showBiometricDialog(item));
                } else {
                    btnDigitais.setVisibility(View.GONE);
                }
            }

            // Botao Editar
            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.USUARIOS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            // Botao Inativar
            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.USUARIOS_EXCLUIR)) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setOnClickListener(v -> {
                        showConfirm("Inativar", "Deseja inativar este usuario?",
                                () -> inativarRecord(((Number) item.get("id")).intValue()));
                    });
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }
        });
        recyclerView.setAdapter(adapter);

        // Controlar visibilidade do botao Novo
        View btnNovo = findViewById(R.id.btnNovo);
        PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.USUARIOS_CRIAR);
        btnNovo.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.USUARIOS_CRIAR)) {
                showEditDialog(null);
            }
        });

        findViewById(R.id.btnBuscar).setOnClickListener(v -> loadData());
        etBusca.setOnEditorActionListener((v, a, e) -> { loadData(); return true; });

        // Carregar perfis disponiveis em background
        carregarPerfis();
        loadData();
    }

    @Override
    protected void onResume() { super.onResume(); loadData(); }

    private String safeStr(Object o) { return o != null ? o.toString() : ""; }

    /**
     * Carrega a lista de perfis disponiveis para o Spinner.
     */
    private void carregarPerfis() {
        new Thread(() -> {
            try {
                perfisDisponiveis = PermissionManager.getInstance(this).listarPerfis();
            } catch (Exception e) {
                android.util.Log.e("CadUsuario", "Erro ao carregar perfis: " + e.getMessage());
            }
        }).start();
    }

    private void loadData() {
        showLoading("Carregando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String sql = "SELECT u.*, p.nome as perfil_nome FROM usuarios u " +
                             "LEFT JOIN perfis p ON u.perfil_id = p.id " +
                             "WHERE u.ativo = 1 ORDER BY u.id DESC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getInt("id"));
                    String perfilNome = rs.getString("perfil_nome");
                    if (perfilNome == null || perfilNome.isEmpty()) {
                        perfilNome = "Sem Perfil";
                    }
                    map.put("line1", rs.getString("nome"));
                    map.put("line2", "Login: " + safeStr(rs.getString("login")) +
                            " | Perfil: " + perfilNome);
                    map.put("nome", rs.getString("nome"));
                    map.put("login", rs.getString("login"));
                    map.put("senha", rs.getString("senha"));
                    map.put("nivel", rs.getString("nivel"));
                    map.put("perfil_id", rs.getInt("perfil_id"));
                    map.put("perfil_nome", perfilNome);
                    list.add(map);
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> adapter.setItems(list));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showBiometricDialog(Map<String, Object> record) {
        int userId = ((Number) record.get("id")).intValue();
        showLoading("Carregando digitais...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                List<BiometricCredentialStore.Credential> digitais =
                        BiometricCredentialStore.listForUser(conn, userId);
                hideLoading();
                runOnUiThread(() -> showBiometricListDialog(record, digitais));
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_CARREGAR);
            }
        }).start();
    }

    private void showBiometricListDialog(Map<String, Object> record,
                                         List<BiometricCredentialStore.Credential> digitais) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Digitais - " + safeStr(record.get("nome")))
                .setMessage(digitais.isEmpty()
                        ? "Nenhuma digital cadastrada para este usuario."
                        : "Toque em uma digital para inativar o vinculo.");

        if (!digitais.isEmpty()) {
            CharSequence[] items = new CharSequence[digitais.size()];
            for (int i = 0; i < digitais.size(); i++) {
                BiometricCredentialStore.Credential c = digitais.get(i);
                items[i] = c.descricao + "\n" + c.detail();
            }
            builder.setItems(items, (dialog, which) ->
                    confirmarInativarDigital(record, digitais.get(which)));
        }

        builder.setPositiveButton("Cadastrar nova", (d, w) -> cadastrarDigitalDoCadastro(record))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void confirmarInativarDigital(Map<String, Object> record,
                                          BiometricCredentialStore.Credential digital) {
        new AlertDialog.Builder(this)
                .setTitle("Inativar digital")
                .setMessage("Deseja inativar o vinculo \"" + digital.descricao + "\"?\n\n"
                        + "O usuario podera cadastrar outra digital depois.")
                .setPositiveButton("Inativar", (d, w) -> inativarDigital(record, digital.id))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void inativarDigital(Map<String, Object> record, int credentialId) {
        showLoading("Inativando digital...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                BiometricCredentialStore.disableCredential(conn, credentialId);
                hideLoading();
                showToast("Digital inativada.");
                showBiometricDialog(record);
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }

    private void cadastrarDigitalDoCadastro(Map<String, Object> record) {
        String error = BiometricAuthHelper.getFingerprintAvailabilityError(this);
        if (error != null) {
            showError(error);
            return;
        }

        EditText etDescricao = new EditText(this);
        etDescricao.setHint("Ex: Indicador direito");
        etDescricao.setSingleLine(true);
        etDescricao.setTextColor(0xFFFFFFFF);
        etDescricao.setHintTextColor(0xFF90A4AE);
        etDescricao.setPadding(24, 12, 24, 12);

        new AlertDialog.Builder(this)
                .setTitle("Cadastrar Digital")
                .setMessage("Peça para o usuario tocar no sensor quando o aparelho solicitar.")
                .setView(etDescricao)
                .setPositiveButton("Continuar", (d, w) -> {
                    String descricao = etDescricao.getText().toString().trim();
                    if (descricao.isEmpty()) descricao = "Digital";
                    autenticarCadastroDigital(record, descricao);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void autenticarCadastroDigital(Map<String, Object> record, String descricao) {
        BiometricAuthHelper.authenticate(
                this,
                "Cadastrar Digital",
                "Confirmar digital de " + safeStr(record.get("nome")),
                new BiometricAuthHelper.Callback() {
                    @Override
                    public void onSuccess() {
                        salvarDigitalDoCadastro(record, descricao);
                    }

                    @Override
                    public void onError(String message) {
                        tratarErroBiometria(message);
                    }
                });
    }

    private void salvarDigitalDoCadastro(Map<String, Object> record, String descricao) {
        showLoading("Salvando digital...");
        new Thread(() -> {
            try {
                int userId = ((Number) record.get("id")).intValue();
                String login = safeStr(record.get("login"));
                String senha = safeStr(record.get("senha"));

                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                BiometricCredentialStore.SessionUser user =
                        BiometricCredentialStore.findActiveUserByPassword(conn, login, senha);
                if (user == null || user.id != userId) {
                    hideLoading();
                    showError("Nao foi possivel validar a senha atual deste usuario.\n\n"
                            + "Abra o usuario, confirme a senha e tente cadastrar a digital novamente.");
                    return;
                }

                BiometricCredentialStore.addCredential(conn, userId, descricao, login, senha);
                hideLoading();
                showToast("Digital cadastrada.");
                showBiometricDialog(record);
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void tratarErroBiometria(String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "Autenticacao biometrica nao concluida.";
        }
        String lower = message.toLowerCase();
        if (lower.contains("cancel")) {
            showToast(message);
        } else {
            showError(message);
        }
    }

    private void showEditDialog(Map<String, Object> record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        // Campo Nome
        EditText et_nome = criarInput(container, "Nome");
        if (record != null && record.get("nome") != null) et_nome.setText(safeStr(record.get("nome")));

        // Campo Login
        EditText et_login = criarInput(container, "Login");
        if (record != null && record.get("login") != null) et_login.setText(safeStr(record.get("login")));

        // Campo Senha
        EditText et_senha = criarInput(container, "Senha");
        if (record != null && record.get("senha") != null) et_senha.setText(safeStr(record.get("senha")));

        // Label para Perfil
        TextView tvPerfilLabel = new TextView(this);
        tvPerfilLabel.setText("Perfil de Acesso:");
        tvPerfilLabel.setTextColor(0xFF00BCD4);
        tvPerfilLabel.setTextSize(14);
        tvPerfilLabel.setPadding(8, 24, 0, 8);
        container.addView(tvPerfilLabel);

        // Spinner de Perfil (substitui o campo texto "nivel")
        Spinner spPerfil = new Spinner(this);
        spPerfil.setBackgroundResource(R.drawable.input_bg);
        spPerfil.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams lpSpinner = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSpinner.setMargins(0, 4, 0, 8);
        spPerfil.setLayoutParams(lpSpinner);

        // Popular spinner com perfis
        List<String> nomesPerfis = new ArrayList<>();
        nomesPerfis.add("-- Selecione um Perfil --");
        for (Perfil p : perfisDisponiveis) {
            nomesPerfis.add(p.getNome() + (p.isSistematico() ? " \uD83D\uDD12" : ""));
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, nomesPerfis);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spPerfil.setAdapter(spinnerAdapter);

        // Selecionar perfil atual
        if (record != null) {
            int perfilIdAtual = record.get("perfil_id") != null ? ((Number) record.get("perfil_id")).intValue() : 0;
            for (int i = 0; i < perfisDisponiveis.size(); i++) {
                if (perfisDisponiveis.get(i).getId() == perfilIdAtual) {
                    spPerfil.setSelection(i + 1); // +1 por causa do "Selecione"
                    break;
                }
            }
        }
        container.addView(spPerfil);

        // Info sobre o perfil
        TextView tvPerfilInfo = new TextView(this);
        tvPerfilInfo.setText("O perfil define quais funcionalidades o usuario pode acessar.");
        tvPerfilInfo.setTextColor(0xFF607D8B);
        tvPerfilInfo.setTextSize(12);
        tvPerfilInfo.setPadding(8, 4, 0, 16);
        container.addView(tvPerfilInfo);

        // Separador de Vinculos
        TextView tvVinculoTitle = new TextView(this);
        tvVinculoTitle.setText("Vinculos de Caixa e Turno");
        tvVinculoTitle.setTextColor(0xFF00BCD4);
        tvVinculoTitle.setTextSize(15);
        tvVinculoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvVinculoTitle.setPadding(8, 16, 0, 4);
        container.addView(tvVinculoTitle);

        TextView tvVinculoInfo = new TextView(this);
        tvVinculoInfo.setText("Selecione um caixa e/ou turno para vincular ao usuario. O usuario pode ter multiplos vinculos.");
        tvVinculoInfo.setTextColor(0xFF607D8B);
        tvVinculoInfo.setTextSize(12);
        tvVinculoInfo.setPadding(8, 0, 0, 8);
        container.addView(tvVinculoInfo);

        // Spinner de Caixa
        TextView tvCaixaLabel = new TextView(this);
        tvCaixaLabel.setText("Caixa (opcional):");
        tvCaixaLabel.setTextColor(0xFF90A4AE);
        tvCaixaLabel.setTextSize(13);
        tvCaixaLabel.setPadding(8, 8, 0, 4);
        container.addView(tvCaixaLabel);

        Spinner spCaixa = new Spinner(this);
        spCaixa.setBackgroundResource(R.drawable.input_bg);
        spCaixa.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams lpCaixa = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCaixa.setMargins(0, 4, 0, 8);
        spCaixa.setLayoutParams(lpCaixa);
        container.addView(spCaixa);

        // Spinner de Turno
        TextView tvTurnoLabel = new TextView(this);
        tvTurnoLabel.setText("Turno (opcional):");
        tvTurnoLabel.setTextColor(0xFF90A4AE);
        tvTurnoLabel.setTextSize(13);
        tvTurnoLabel.setPadding(8, 8, 0, 4);
        container.addView(tvTurnoLabel);

        Spinner spTurno = new Spinner(this);
        spTurno.setBackgroundResource(R.drawable.input_bg);
        spTurno.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams lpTurno = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTurno.setMargins(0, 4, 0, 8);
        spTurno.setLayoutParams(lpTurno);
        container.addView(spTurno);

        // Carregar caixas e turnos
        final List<Integer> caixaIds = new ArrayList<>();
        final List<String> caixaNomes = new ArrayList<>();
        final List<Integer> turnoIds = new ArrayList<>();
        final List<String> turnoNomes = new ArrayList<>();
        caixaIds.add(0); caixaNomes.add("-- Sem caixa --");
        turnoIds.add(0); turnoNomes.add("-- Sem turno --");

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, nome FROM caixas_nominais WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) { caixaIds.add(rs.getInt("id")); caixaNomes.add(rs.getString("nome")); }
                rs.close();
                rs = stmt.executeQuery("SELECT id, nome FROM turnos WHERE ativo = 1 ORDER BY nome");
                while (rs.next()) { turnoIds.add(rs.getInt("id")); turnoNomes.add(rs.getString("nome")); }
                rs.close();
                stmt.close();
                runOnUiThread(() -> {
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                    ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, turnoNomes);
                    ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spTurno.setAdapter(ta);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, caixaNomes);
                    ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCaixa.setAdapter(ca);
                    ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, turnoNomes);
                    ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spTurno.setAdapter(ta);
                });
            }
        }).start();

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo Usuario" : "Editar Usuario")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    int perfilIndex = spPerfil.getSelectedItemPosition();
                    int perfilId = 0;
                    String nivel = "operador";
                    if (perfilIndex > 0 && perfilIndex <= perfisDisponiveis.size()) {
                        Perfil perfilSelecionado = perfisDisponiveis.get(perfilIndex - 1);
                        perfilId = perfilSelecionado.getId();
                        // Derivar nivel do nome do perfil para compatibilidade
                        String nomePerfil = perfilSelecionado.getNome().toLowerCase();
                        if (nomePerfil.contains("admin")) nivel = "admin";
                        else if (nomePerfil.contains("gerente")) nivel = "gerente";
                        else if (nomePerfil.contains("caixa")) nivel = "caixa";
                        else if (nomePerfil.contains("vendedor")) nivel = "vendedor";
                        else if (nomePerfil.contains("entregador")) nivel = "entregador";
                        else nivel = "operador";
                    }
                    int caixaPos = spCaixa.getSelectedItemPosition();
                    int caixaId = caixaPos >= 0 && caixaPos < caixaIds.size() ? caixaIds.get(caixaPos) : 0;
                    int turnoPos = spTurno.getSelectedItemPosition();
                    int turnoId = turnoPos >= 0 && turnoPos < turnoIds.size() ? turnoIds.get(turnoPos) : 0;
                    int userId = record != null ? ((Number) record.get("id")).intValue() : 0;
                    saveRecord(userId, et_nome.getText().toString().trim(),
                            et_login.getText().toString().trim(),
                            et_senha.getText().toString().trim(),
                            nivel, perfilId, caixaId, turnoId);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText criarInput(LinearLayout container, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF90A4AE);
        et.setBackgroundResource(R.drawable.input_bg);
        et.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 8, 0, 8);
        et.setLayoutParams(lp);
        container.addView(et);
        return et;
    }

    private void saveRecord(int id, String val_nome, String val_login, String val_senha,
                            String val_nivel, int perfilId) {
        saveRecord(id, val_nome, val_login, val_senha, val_nivel, perfilId, 0, 0);
    }

    private void saveRecord(int id, String val_nome, String val_login, String val_senha,
                            String val_nivel, int perfilId, int caixaId, int turnoId) {
        if (val_nome.isEmpty()) { showError("Por favor, preencha o campo Nome antes de salvar."); return; }
        if (val_login.isEmpty()) { showError("Por favor, preencha o campo Login antes de salvar."); return; }
        if (val_senha.isEmpty()) { showError("Por favor, preencha o campo Senha antes de salvar."); return; }
        if (perfilId == 0) { showError("Por favor, selecione um Perfil de Acesso para o usuario."); return; }

        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement(
                            "INSERT INTO usuarios (nome,login,senha,nivel,perfil_id,ativo) VALUES (?,?,?,?,?,1)",
                            Statement.RETURN_GENERATED_KEYS);
                } else {
                    ps = conn.prepareStatement(
                            "UPDATE usuarios SET nome=?,login=?,senha=?,nivel=?,perfil_id=? WHERE id=?");
                }
                ps.setString(1, val_nome);
                ps.setString(2, val_login);
                ps.setString(3, val_senha);
                ps.setString(4, val_nivel);
                ps.setInt(5, perfilId);
                if (id > 0) ps.setInt(6, id);
                ps.executeUpdate();

                // Obter ID do usuario (novo ou existente)
                int userId = id;
                if (id == 0) {
                    ResultSet keys = ps.getGeneratedKeys();
                    if (keys.next()) userId = keys.getInt(1);
                    keys.close();
                }
                ps.close();

                // Criar vinculo se caixa ou turno selecionado
                if ((caixaId > 0 || turnoId > 0) && userId > 0) {
                    PreparedStatement psVinc = conn.prepareStatement(
                            "INSERT INTO usuario_caixa_turno (usuario_id, caixa_nominal_id, turno_id) VALUES (?,?,?)");
                    psVinc.setInt(1, userId);
                    if (caixaId > 0) psVinc.setInt(2, caixaId); else psVinc.setNull(2, Types.INTEGER);
                    if (turnoId > 0) psVinc.setInt(3, turnoId); else psVinc.setNull(3, Types.INTEGER);
                    psVinc.executeUpdate();
                    psVinc.close();
                }

                hideLoading();
                showToast("Salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void inativarRecord(int id) {
        showLoading("Inativando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE usuarios SET ativo = 0 WHERE id = ?");
                ps.setInt(1, id);
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Inativado com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_EXCLUIR);
            }
        }).start();
    }
}
