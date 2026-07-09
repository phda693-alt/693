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
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.ErrorHandler;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity para cadastro de taxas de entrega por bairro.
 * Permite adicionar, editar e remover bairros com suas respectivas taxas.
 */
public class TaxaEntregaBairroActivity extends BaseActivity {

    private RecyclerView rvBairros;
    private GenericAdapter<BairroTaxa> adapter;
    private Button btnAddBairro;
    private List<BairroTaxa> bairros = new ArrayList<>();

    /**
     * Classe auxiliar para representar um bairro com taxa.
     */
    public static class BairroTaxa {
        public int id;
        public String bairro;
        public double taxa;
        public boolean ativo;

        public BairroTaxa() { this.ativo = true; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.TAXA_ENTREGA_ACESSAR)) {
            return;
        }
        setContentView(R.layout.activity_taxa_entrega_bairro);

        rvBairros = findViewById(R.id.rvBairros);
        btnAddBairro = findViewById(R.id.btnAddBairro);

        rvBairros.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GenericAdapter<>(R.layout.item_bairro_taxa, (holder, item, pos) -> {
            holder.setText(R.id.tvBairroNome, item.bairro);
            holder.setText(R.id.tvBairroTaxa, "R$ " + FormatUtils.formatMoney(item.taxa));
            holder.setText(R.id.tvBairroStatus, item.ativo ? "Ativo" : "Inativo");
        });

        adapter.setOnItemClickListener((item, pos) -> {
            showEditDialog(item);
        });

        adapter.setOnItemLongClickListener((item, pos) -> {
            new AlertDialog.Builder(this)
                .setTitle("Opcoes - " + item.bairro)
                .setItems(new String[]{"Editar", item.ativo ? "Inativar" : "Reativar", "Excluir"}, (d, w) -> {
                    switch (w) {
                        case 0: showEditDialog(item); break;
                        case 1: toggleAtivo(item); break;
                        case 2: confirmarExclusao(item); break;
                    }
                })
                .show();
        });

        rvBairros.setAdapter(adapter);

        btnAddBairro.setOnClickListener(v -> showAddDialog());

        carregarBairros();
    }

    private void carregarBairros() {
        showLoading("Carregando bairros...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT * FROM taxa_entrega_bairro ORDER BY bairro");
                bairros.clear();
                while (rs.next()) {
                    BairroTaxa bt = new BairroTaxa();
                    bt.id = rs.getInt("id");
                    bt.bairro = rs.getString("bairro");
                    bt.taxa = rs.getDouble("taxa");
                    bt.ativo = rs.getInt("ativo") == 1;
                    bairros.add(bt);
                }
                rs.close();
                stmt.close();
                hideLoading();
                runOnUiThread(() -> {
                    adapter.setItems(bairros);
                    if (bairros.isEmpty()) {
                        showToast("Nenhum bairro cadastrado. Clique em + para adicionar.");
                    }
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_GENERICO);
            }
        }).start();
    }

    private void showAddDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_bairro_taxa, null);
        EditText etBairro = view.findViewById(R.id.etBairroNome);
        EditText etTaxa = view.findViewById(R.id.etBairroTaxa);

        new AlertDialog.Builder(this)
            .setTitle("Novo Bairro")
            .setView(view)
            .setPositiveButton("Salvar", (d, w) -> {
                String bairro = etBairro.getText().toString().trim();
                String taxaStr = etTaxa.getText().toString().trim();

                if (bairro.isEmpty()) {
                    showError("Informe o nome do bairro.");
                    return;
                }
                if (taxaStr.isEmpty()) {
                    showError("Informe o valor da taxa.");
                    return;
                }

                double taxa = FormatUtils.parseMoney(taxaStr);
                salvarBairro(0, bairro, taxa);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showEditDialog(BairroTaxa item) {
        View view = getLayoutInflater().inflate(R.layout.dialog_bairro_taxa, null);
        EditText etBairro = view.findViewById(R.id.etBairroNome);
        EditText etTaxa = view.findViewById(R.id.etBairroTaxa);

        etBairro.setText(item.bairro);
        etTaxa.setText(FormatUtils.formatMoney(item.taxa));

        new AlertDialog.Builder(this)
            .setTitle("Editar Bairro")
            .setView(view)
            .setPositiveButton("Salvar", (d, w) -> {
                String bairro = etBairro.getText().toString().trim();
                String taxaStr = etTaxa.getText().toString().trim();

                if (bairro.isEmpty()) {
                    showError("Informe o nome do bairro.");
                    return;
                }
                if (taxaStr.isEmpty()) {
                    showError("Informe o valor da taxa.");
                    return;
                }

                double taxa = FormatUtils.parseMoney(taxaStr);
                salvarBairro(item.id, bairro, taxa);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void salvarBairro(int id, String bairro, double taxa) {
        showLoading("Salvando...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                if (id == 0) {
                    // Inserir novo
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO taxa_entrega_bairro (bairro, taxa, ativo) VALUES (?, ?, 1)");
                    ps.setString(1, bairro);
                    ps.setDouble(2, taxa);
                    ps.executeUpdate();
                    ps.close();
                } else {
                    // Atualizar existente
                    PreparedStatement ps = conn.prepareStatement(
                        "UPDATE taxa_entrega_bairro SET bairro = ?, taxa = ? WHERE id = ?");
                    ps.setString(1, bairro);
                    ps.setDouble(2, taxa);
                    ps.setInt(3, id);
                    ps.executeUpdate();
                    ps.close();
                }

                hideLoading();
                runOnUiThread(() -> {
                    showToast("Bairro salvo com sucesso!");
                    carregarBairros();
                });
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_GENERICO);
            }
        }).start();
    }

    private void toggleAtivo(BairroTaxa item) {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "UPDATE taxa_entrega_bairro SET ativo = ? WHERE id = ?");
                ps.setInt(1, item.ativo ? 0 : 1);
                ps.setInt(2, item.id);
                ps.executeUpdate();
                ps.close();
                runOnUiThread(() -> {
                    showToast(item.ativo ? "Bairro inativado" : "Bairro reativado");
                    carregarBairros();
                });
            } catch (Exception e) {
                showErrorFromException(e, ErrorHandler.CTX_GENERICO);
            }
        }).start();
    }

    private void confirmarExclusao(BairroTaxa item) {
        showConfirm("Excluir", "Deseja excluir o bairro " + item.bairro + "?", () -> {
            new Thread(() -> {
                try {
                    DatabaseHelper db = DatabaseHelper.getInstance(this);
                    Connection conn = db.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM taxa_entrega_bairro WHERE id = ?");
                    ps.setInt(1, item.id);
                    ps.executeUpdate();
                    ps.close();
                    runOnUiThread(() -> {
                        showToast("Bairro excluido!");
                        carregarBairros();
                    });
                } catch (Exception e) {
                    showErrorFromException(e, ErrorHandler.CTX_GENERICO);
                }
            }).start();
        });
    }
}
