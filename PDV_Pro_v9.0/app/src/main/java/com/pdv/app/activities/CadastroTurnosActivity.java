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
 * Tela de Cadastro de Turnos.
 * Permite criar, editar e inativar turnos para vínculo com usuários e caixas.
 */
public class CadastroTurnosActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private GenericAdapter<Map<String, Object>> adapter;
    private EditText etBusca;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_lista);

        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.TURNOS_ACESSAR)) {
            return;
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Cadastro de Turnos");

        Button btnNovo = findViewById(R.id.btnNovo);
        PermissionHelper.controlarVisibilidade(this, btnNovo, PermissionConstants.TURNOS_CRIAR);
        btnNovo.setOnClickListener(v -> showEditDialog(null));

        etBusca = findViewById(R.id.etBusca);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GenericAdapter<>(R.layout.item_list_generic, (holder, item, pos) -> {
            String nome = safeStr(item.get("nome"));
            String horaInicio = safeStr(item.get("hora_inicio"));
            String horaFim = safeStr(item.get("hora_fim"));
            int ativo = item.get("ativo") != null ? ((Number) item.get("ativo")).intValue() : 1;

            holder.setText(R.id.tvLine1, nome);
            String horario = "";
            if (!horaInicio.isEmpty() && !horaFim.isEmpty()) {
                horario = horaInicio + " - " + horaFim + " | ";
            } else if (!horaInicio.isEmpty()) {
                horario = "Inicio: " + horaInicio + " | ";
            }
            holder.setText(R.id.tvLine2, horario + (ativo == 1 ? "Ativo" : "Inativo"));

            ImageView iv = holder.find(R.id.ivFoto);
            if (iv != null) iv.setVisibility(View.GONE);

            Button btnEditar = holder.find(R.id.btnEditar);
            if (btnEditar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.TURNOS_EDITAR)) {
                    btnEditar.setVisibility(View.VISIBLE);
                    btnEditar.setOnClickListener(v -> showEditDialog(item));
                } else {
                    btnEditar.setVisibility(View.GONE);
                }
            }

            Button btnInativar = holder.find(R.id.btnInativar);
            if (btnInativar != null) {
                if (PermissionHelper.verificarSilencioso(this, PermissionConstants.TURNOS_EXCLUIR)) {
                    btnInativar.setVisibility(View.VISIBLE);
                    btnInativar.setText(ativo == 1 ? "Inativar" : "Ativar");
                    btnInativar.setOnClickListener(v -> toggleAtivo(item));
                } else {
                    btnInativar.setVisibility(View.GONE);
                }
            }

            Button btnDuplicar = holder.find(R.id.btnDuplicar);
            if (btnDuplicar != null) btnDuplicar.setVisibility(View.GONE);
        });

        // Clique no item: so abre edicao se o usuario tiver permissao de editar
        adapter.setOnItemClickListener((item, pos) -> {
            if (PermissionHelper.verificarSilencioso(this, PermissionConstants.TURNOS_EDITAR)) {
                showEditDialog(item);
            } else {
                PermissionHelper.mostrarAcessoNegado(this, PermissionConstants.TURNOS_EDITAR);
            }
        });
        recyclerView.setAdapter(adapter);

        Button btnBuscar = findViewById(R.id.btnBuscar);
        if (btnBuscar != null) btnBuscar.setOnClickListener(v -> loadData());

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                String busca = etBusca != null ? etBusca.getText().toString().trim() : "";

                String sql = "SELECT * FROM turnos";
                if (!busca.isEmpty()) {
                    sql += " WHERE nome LIKE '%" + busca.replace("'", "''") + "%'";
                }
                sql += " ORDER BY ativo DESC, nome ASC";

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);

                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getInt("id"));
                    m.put("nome", rs.getString("nome"));
                    m.put("hora_inicio", rs.getString("hora_inicio"));
                    m.put("hora_fim", rs.getString("hora_fim"));
                    m.put("descricao", rs.getString("descricao"));
                    m.put("ativo", rs.getInt("ativo"));
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

    private void showEditDialog(Map<String, Object> record) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_generic_form, null);
        LinearLayout container = dialogView.findViewById(R.id.formContainer);

        EditText etNome = criarInput(container, "Nome do Turno *");
        EditText etHoraInicio = criarInput(container, "Hora Inicio (HH:mm)");
        EditText etHoraFim = criarInput(container, "Hora Fim (HH:mm)");
        EditText etDesc = criarInput(container, "Descricao (opcional)");

        if (record != null) {
            etNome.setText(safeStr(record.get("nome")));
            etHoraInicio.setText(safeStr(record.get("hora_inicio")));
            etHoraFim.setText(safeStr(record.get("hora_fim")));
            etDesc.setText(safeStr(record.get("descricao")));
        }

        new AlertDialog.Builder(this)
                .setTitle(record == null ? "Novo Turno" : "Editar Turno")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    String nome = etNome.getText().toString().trim();
                    String horaInicio = etHoraInicio.getText().toString().trim();
                    String horaFim = etHoraFim.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();
                    if (nome.isEmpty()) {
                        showError("Informe o nome do turno.");
                        return;
                    }
                    int id = record != null ? ((Number) record.get("id")).intValue() : 0;
                    saveRecord(id, nome, horaInicio, horaFim, desc);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveRecord(int id, String nome, String horaInicio, String horaFim, String descricao) {
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps;
                if (id == 0) {
                    ps = conn.prepareStatement(
                            "INSERT INTO turnos (nome, hora_inicio, hora_fim, descricao, ativo) VALUES (?,?,?,?,1)");
                    ps.setString(1, nome);
                    ps.setString(2, horaInicio.isEmpty() ? null : horaInicio);
                    ps.setString(3, horaFim.isEmpty() ? null : horaFim);
                    ps.setString(4, descricao.isEmpty() ? null : descricao);
                } else {
                    ps = conn.prepareStatement(
                            "UPDATE turnos SET nome = ?, hora_inicio = ?, hora_fim = ?, descricao = ? WHERE id = ?");
                    ps.setString(1, nome);
                    ps.setString(2, horaInicio.isEmpty() ? null : horaInicio);
                    ps.setString(3, horaFim.isEmpty() ? null : horaFim);
                    ps.setString(4, descricao.isEmpty() ? null : descricao);
                    ps.setInt(5, id);
                }
                ps.executeUpdate();
                ps.close();
                hideLoading();
                showToast("Turno salvo com sucesso!");
                loadData();
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_SALVAR);
            }
        }).start();
    }

    private void toggleAtivo(Map<String, Object> record) {
        int id = ((Number) record.get("id")).intValue();
        int ativo = record.get("ativo") != null ? ((Number) record.get("ativo")).intValue() : 1;
        int novoAtivo = ativo == 1 ? 0 : 1;
        String acao = novoAtivo == 1 ? "ativar" : "inativar";

        showConfirm("Confirmar", "Deseja " + acao + " este turno?", () -> {
            showLoading("Salvando...");
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE turnos SET ativo = ? WHERE id = ?");
                    ps.setInt(1, novoAtivo);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                    ps.close();
                    hideLoading();
                    showToast("Turno " + (novoAtivo == 1 ? "ativado" : "inativado") + "!");
                    loadData();
                } catch (Exception e) {
                    hideLoading();
                    showErrorFromException(e, ErrorHandler.CTX_SALVAR);
                }
            }).start();
        });
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

    private String safeStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
