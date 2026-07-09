package com.pdv.app.activities;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

public class BackupConfigActivity extends BaseActivity {
    private EditText etFtpHost, etFtpUser, etFtpPassword;
    private CheckBox cbAutoBackup, cbSqlBackup;
    private Button btnSalvar, btnBackupAgora, btnBackupSqlAgora, btnListarBackups, btnApagarDadosBD, btnApagarArquivosFTP;
    private RecyclerView rvBackups;
    private GenericAdapter<String> backupsAdapter;

    // Senhas de seguranca
    private static final String SENHA_RESTAURAR_BACKUP = "2394";
    private static final String SENHA_APAGAR_DADOS = "4872";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_config);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.BACKUP_ACESSAR)) {
            return;
        }

        etFtpHost = findViewById(R.id.etFtpHost);
        etFtpUser = findViewById(R.id.etFtpUser);
        etFtpPassword = findViewById(R.id.etFtpPassword);
        cbAutoBackup = findViewById(R.id.cbAutoBackup);
        cbSqlBackup = findViewById(R.id.cbSqlBackup);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnBackupAgora = findViewById(R.id.btnBackupAgora);
        btnBackupSqlAgora = findViewById(R.id.btnBackupSqlAgora);
        btnListarBackups = findViewById(R.id.btnListarBackups);
        btnApagarDadosBD = findViewById(R.id.btnApagarDadosBD);
        btnApagarArquivosFTP = findViewById(R.id.btnApagarArquivosFTP);
        rvBackups = findViewById(R.id.rvBackups);

        rvBackups.setLayoutManager(new LinearLayoutManager(this));
        backupsAdapter = new GenericAdapter<>(R.layout.item_backup, (holder, item, pos) -> {
            holder.setText(R.id.tvBackupName, item);
        });
        backupsAdapter.setOnItemClickListener((item, pos) -> {
            if (item.endsWith(".sql")) {
                showInfo("Arquivo SQL",
                        "O arquivo '" + item + "' e um dump SQL completo.\n\n" +
                        "Arquivos .sql nao podem ser restaurados diretamente pelo app.\n" +
                        "Use um cliente MySQL (phpMyAdmin, MySQL Workbench, etc.) para importa-lo.");
            } else {
                solicitarSenhaRestaurarBackup(item);
            }
        });
        rvBackups.setAdapter(backupsAdapter);

        BackupManager bm = new BackupManager(this);
        etFtpHost.setText(bm.getFtpHost());
        etFtpUser.setText(bm.getFtpUser());
        etFtpPassword.setText(bm.getFtpPassword());
        cbAutoBackup.setChecked(bm.isAutoBackupEnabled());
        cbSqlBackup.setChecked(bm.isSqlBackupEnabled());

        btnSalvar.setOnClickListener(v -> salvarConfig());
        btnBackupAgora.setOnClickListener(v -> fazerBackup());
        btnBackupSqlAgora.setOnClickListener(v -> fazerBackupSql());
        btnListarBackups.setOnClickListener(v -> listarBackups());
        btnApagarDadosBD.setOnClickListener(v -> solicitarSenhaApagarDadosBD());
        btnApagarArquivosFTP.setOnClickListener(v -> solicitarSenhaApagarArquivosFTP());

        AnimUtils.animateItems(etFtpHost, etFtpUser, etFtpPassword, cbAutoBackup, cbSqlBackup,
                btnSalvar, btnBackupAgora, btnBackupSqlAgora, btnListarBackups, btnApagarDadosBD, btnApagarArquivosFTP);
    }

    private void salvarConfig() {
        BackupManager bm = new BackupManager(this);
        bm.saveConfig(
                etFtpHost.getText().toString().trim(),
                etFtpUser.getText().toString().trim(),
                etFtpPassword.getText().toString().trim(),
                cbAutoBackup.isChecked(),
                cbSqlBackup.isChecked()
        );
        showToast("Configuracao salva!");
    }

    // =========================================================================
    // BACKUP JSON (formato original)
    // =========================================================================

    private void fazerBackup() {
        showLoading("Realizando backup JSON...");
        new Thread(() -> {
            BackupManager bm = new BackupManager(this);
            boolean success = bm.realizarBackupJson();
            hideLoading();
            if (success) showSuccess("Backup JSON realizado com sucesso!");
            else showError("Nao foi possivel realizar o backup JSON.\n\nVerifique as configuracoes do servidor FTP:\n- Endereco do servidor\n- Usuario e senha\n- Conexao com a internet");
        }).start();
    }

    // =========================================================================
    // BACKUP SQL - NOVO
    // =========================================================================

    /**
     * Executa o backup em formato .sql manualmente (botao "FAZER BACKUP SQL AGORA").
     * Gera um dump MySQL completo (DROP + CREATE TABLE + INSERT INTO) e envia ao FTP.
     */
    private void fazerBackupSql() {
        showLoading("Gerando dump SQL e enviando ao FTP...");
        new Thread(() -> {
            BackupManager bm = new BackupManager(this);
            boolean success = bm.realizarBackupSql();
            hideLoading();
            if (success) {
                showSuccess("Backup SQL enviado com sucesso ao FTP!\n\n" +
                        "O arquivo .sql pode ser importado via phpMyAdmin,\n" +
                        "MySQL Workbench ou linha de comando MySQL.");
            } else {
                showError("Nao foi possivel enviar o backup SQL ao FTP.\n\n" +
                        "Verifique as configuracoes do servidor FTP:\n" +
                        "- Endereco do servidor\n- Usuario e senha\n- Conexao com a internet");
            }
        }).start();
    }

    // =========================================================================
    // LISTAR BACKUPS
    // =========================================================================

    private void listarBackups() {
        showLoading("Listando backups...");
        new Thread(() -> {
            BackupManager bm = new BackupManager(this);
            List<String> backups = bm.listarBackups();
            hideLoading();
            runOnUiThread(() -> {
                if (backups.isEmpty()) {
                    showToast("Nenhum backup encontrado");
                } else {
                    backupsAdapter.setItems(backups);
                    showToast(backups.size() + " backup(s) encontrado(s). Toque .json para restaurar.");
                }
            });
        }).start();
    }

    // =========================================================================
    // RESTAURAR BACKUP COM SENHA 2394
    // =========================================================================

    private void solicitarSenhaRestaurarBackup(String fileName) {
        runOnUiThread(() -> {
            final EditText inputSenha = new EditText(this);
            inputSenha.setHint("Digite a senha de seguranca");
            inputSenha.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            inputSenha.setTextColor(getResources().getColor(R.color.text_primary));
            inputSenha.setHintTextColor(getResources().getColor(R.color.text_hint));
            inputSenha.setBackgroundResource(R.drawable.input_bg);
            inputSenha.setPadding(32, 24, 32, 24);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(48, 32, 48, 16);
            container.addView(inputSenha);

            new AlertDialog.Builder(this)
                    .setTitle("\uD83D\uDD12  Senha de Seguranca")
                    .setMessage("Para restaurar o backup:\n" + fileName + "\n\nDigite a senha de seguranca:")
                    .setView(container)
                    .setPositiveButton("Confirmar", (dialog, which) -> {
                        String senhaDigitada = inputSenha.getText().toString().trim();
                        if (SENHA_RESTAURAR_BACKUP.equals(senhaDigitada)) {
                            showConfirm("Restaurar Backup",
                                    "Tem certeza que deseja restaurar o backup:\n" + fileName + "?\n\nEsta acao ira substituir todos os dados atuais.",
                                    () -> restaurarBackup(fileName));
                        } else {
                            showError("Senha incorreta!\n\nA restauracao do backup foi cancelada.");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    private void restaurarBackup(String fileName) {
        showLoading("Restaurando backup...");
        new Thread(() -> {
            BackupManager bm = new BackupManager(this);
            boolean success = bm.restaurarBackup(fileName);
            hideLoading();
            if (success) showSuccess("Backup restaurado com sucesso!");
            else showError("Nao foi possivel restaurar o backup.\n\nVerifique sua conexao com o servidor FTP e tente novamente.");
        }).start();
    }

    // =========================================================================
    // APAGAR TODOS OS DADOS DO BANCO DE DADOS (EXCETO TABELA USUARIOS)
    // COM SENHA 4872
    // =========================================================================

    private void solicitarSenhaApagarDadosBD() {
        runOnUiThread(() -> {
            final EditText inputSenha = new EditText(this);
            inputSenha.setHint("Digite a senha de seguranca");
            inputSenha.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            inputSenha.setTextColor(getResources().getColor(R.color.text_primary));
            inputSenha.setHintTextColor(getResources().getColor(R.color.text_hint));
            inputSenha.setBackgroundResource(R.drawable.input_bg);
            inputSenha.setPadding(32, 24, 32, 24);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(48, 32, 48, 16);
            container.addView(inputSenha);

            new AlertDialog.Builder(this)
                    .setTitle("\u26A0  Apagar Dados do Banco")
                    .setMessage("ATENCAO: Esta acao ira APAGAR TODOS os dados do banco de dados, EXCETO a tabela de usuarios.\n\nDigite a senha de seguranca para continuar:")
                    .setView(container)
                    .setPositiveButton("Confirmar", (dialog, which) -> {
                        String senhaDigitada = inputSenha.getText().toString().trim();
                        if (SENHA_APAGAR_DADOS.equals(senhaDigitada)) {
                            showConfirm("\u26A0  CONFIRMACAO FINAL",
                                    "Voce tem CERTEZA ABSOLUTA que deseja apagar TODOS os dados do banco de dados?\n\n" +
                                    "A tabela de USUARIOS sera preservada.\n\n" +
                                    "Esta acao NAO pode ser desfeita!",
                                    () -> executarApagarDadosBD());
                        } else {
                            showError("Senha incorreta!\n\nA operacao foi cancelada.");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    private void executarApagarDadosBD() {
        showLoading("Apagando dados do banco de dados...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();

                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 0");

                String[] tabelas = {
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
                    "itens_venda_adicionais",
                    "pagamentos_venda",
                    "itens_venda",
                    "vales_debito",
                    "vendas",
                    "itens_comanda",
                    "comandas",
                    "caixa",
                    "recebimentos_conta",
                    "contas_receber",
                    "itens_nota_entrada",
                    "notas_entrada",
                    "observacoes_cupom",
                    "formas_pagamento",
                    "historico_localizacao",
                    "rastreamento_entregador",
                    "taxa_entrega_bairro",
                    "cliente_bairro_whatsapp",
                    "mesas",
                    "garcons",
                    "entregadores",
                    "vendedores",
                    "clientes",
                    "tipo_produto_adicionais",
                    "adicionais",
                    "produtos",
                    "tipos_produto",
                    "empresa",
                    "licenca",
                    "perfil_permissoes",
                    "permissoes",
                    "perfis"
                };

                int tabelasLimpas = 0;
                StringBuilder erros = new StringBuilder();
                for (String tabela : tabelas) {
                    try {
                        stmt.executeUpdate("DELETE FROM `" + tabela + "`");
                        tabelasLimpas++;
                    } catch (Exception e) {
                        erros.append("- ").append(tabela).append(": ").append(e.getMessage()).append("\n");
                    }
                }

                stmt.executeUpdate("SET FOREIGN_KEY_CHECKS = 1");
                stmt.close();

                hideLoading();
                final int totalLimpas = tabelasLimpas;
                final String errosStr = erros.toString();
                if (errosStr.isEmpty()) {
                    showSuccess("Todos os dados foram apagados com sucesso!\n\n" +
                            totalLimpas + " tabelas limpas.\n" +
                            "A tabela de usuarios foi preservada.");
                } else {
                    showSuccess("Dados apagados!\n\n" +
                            totalLimpas + " tabelas limpas.\n" +
                            "A tabela de usuarios foi preservada.\n\n" +
                            "Alguns avisos:\n" + errosStr);
                }
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao apagar dados do banco:\n\n" + e.getMessage());
            }
        }).start();
    }

    // =========================================================================
    // APAGAR TODOS OS ARQUIVOS DO SERVIDOR FTP COM SENHA 4872
    // =========================================================================

    private void solicitarSenhaApagarArquivosFTP() {
        runOnUiThread(() -> {
            final EditText inputSenha = new EditText(this);
            inputSenha.setHint("Digite a senha de seguranca");
            inputSenha.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            inputSenha.setTextColor(getResources().getColor(R.color.text_primary));
            inputSenha.setHintTextColor(getResources().getColor(R.color.text_hint));
            inputSenha.setBackgroundResource(R.drawable.input_bg);
            inputSenha.setPadding(32, 24, 32, 24);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(48, 32, 48, 16);
            container.addView(inputSenha);

            new AlertDialog.Builder(this)
                    .setTitle("\u26A0  Apagar Arquivos do FTP")
                    .setMessage("ATENCAO: Esta acao ira APAGAR TODOS os arquivos do servidor FTP (incluindo todos os backups).\n\nDigite a senha de seguranca para continuar:")
                    .setView(container)
                    .setPositiveButton("Confirmar", (dialog, which) -> {
                        String senhaDigitada = inputSenha.getText().toString().trim();
                        if (SENHA_APAGAR_DADOS.equals(senhaDigitada)) {
                            showConfirm("\u26A0  CONFIRMACAO FINAL",
                                    "Voce tem CERTEZA ABSOLUTA que deseja apagar TODOS os arquivos do servidor FTP?\n\n" +
                                    "Todos os backups armazenados serao PERMANENTEMENTE excluidos.\n\n" +
                                    "Esta acao NAO pode ser desfeita!",
                                    () -> executarApagarArquivosFTP());
                        } else {
                            showError("Senha incorreta!\n\nA operacao foi cancelada.");
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });
    }

    private void executarApagarArquivosFTP() {
        showLoading("Apagando arquivos do servidor FTP...");
        new Thread(() -> {
            FTPClient ftp = new FTPClient();
            try {
                BackupManager bm = new BackupManager(this);
                ftp.setConnectTimeout(15000);
                ftp.connect(bm.getFtpHost(), 21);
                ftp.login(bm.getFtpUser(), bm.getFtpPassword());
                ftp.enterLocalPassiveMode();

                FTPFile[] files = ftp.listFiles();
                int arquivosApagados = 0;
                StringBuilder erros = new StringBuilder();
                if (files != null) {
                    for (FTPFile file : files) {
                        if (file.isFile()) {
                            try {
                                boolean deleted = ftp.deleteFile(file.getName());
                                if (deleted) {
                                    arquivosApagados++;
                                } else {
                                    erros.append("- ").append(file.getName()).append(": falha ao excluir\n");
                                }
                            } catch (Exception e) {
                                erros.append("- ").append(file.getName()).append(": ").append(e.getMessage()).append("\n");
                            }
                        }
                    }
                }

                ftp.logout();
                ftp.disconnect();
                hideLoading();

                final int totalApagados = arquivosApagados;
                final String errosStr = erros.toString();
                if (errosStr.isEmpty()) {
                    showSuccess("Todos os arquivos do FTP foram apagados com sucesso!\n\n" +
                            totalApagados + " arquivos excluidos.");
                } else {
                    showSuccess("Arquivos apagados!\n\n" +
                            totalApagados + " arquivos excluidos.\n\n" +
                            "Alguns avisos:\n" + errosStr);
                }
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao apagar arquivos do FTP:\n\n" + e.getMessage() +
                        "\n\nVerifique as configuracoes do servidor FTP e sua conexao com a internet.");
            } finally {
                try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
            }
        }).start();
    }
}
