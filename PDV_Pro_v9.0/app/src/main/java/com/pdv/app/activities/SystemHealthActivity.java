package com.pdv.app.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.pdv.app.BuildConfig;
import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.receivers.PhServerAutoStartReceiver;
import com.pdv.app.server.MariaDbServerManager;
import com.pdv.app.utils.BackupManager;
import com.pdv.app.utils.CrashReportManager;
import com.pdv.app.utils.LicencaManager;

import java.io.File;
import java.util.Locale;

/**
 * Central de saude operacional do PDV.
 * Reune diagnosticos de versao, banco, licenca, backup e permissoes.
 */
public class SystemHealthActivity extends BaseActivity {
    private TextView tvResumo;
    private TextView tvAppStatus;
    private TextView tvDbStatus;
    private TextView tvLicenseStatus;
    private TextView tvBackupStatus;
    private TextView tvPermissionStatus;
    private TextView tvStorageStatus;
    private TextView tvServerStatus;
    private TextView tvCrashStatus;

    private String lastDiagnostic = "";
    private String lastDbResult = "Conexao ainda nao testada nesta tela.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // v8.0.23.0 - Adicionada verificacao de permissao DIAGNOSTICO_ACESSAR
        // Esta tela e a Central de Diagnostico do sistema e requer permissao especifica
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.DIAGNOSTICO_ACESSAR)) { return; }
        setContentView(R.layout.activity_system_health);

        bindViews();
        setupActions();
        refreshDiagnostics();
        testDatabase(false);
    }

    private void bindViews() {
        tvResumo = findViewById(R.id.tvResumo);
        tvAppStatus = findViewById(R.id.tvAppStatus);
        tvDbStatus = findViewById(R.id.tvDbStatus);
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus);
        tvStorageStatus = findViewById(R.id.tvStorageStatus);
        tvServerStatus = findViewById(R.id.tvServerStatus);
        tvCrashStatus = findViewById(R.id.tvCrashStatus);
    }

    private void setupActions() {
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnAtualizarDiagnostico).setOnClickListener(v -> {
            refreshDiagnostics();
            showToast("Diagnostico atualizado.");
        });
        findViewById(R.id.btnTestarConexao).setOnClickListener(v -> testDatabase(true));
        findViewById(R.id.btnAbrirBanco).setOnClickListener(v ->
                openActivity(ConfigBancoActivity.class));
        findViewById(R.id.btnAbrirBackup).setOnClickListener(v ->
                openActivity(BackupConfigActivity.class));
        findViewById(R.id.btnAbrirLicenca).setOnClickListener(v ->
                openActivity(LicencaActivity.class));
        findViewById(R.id.btnCopiarDiagnostico).setOnClickListener(v -> copyDiagnostic());
    }

    private void refreshDiagnostics() {
        DatabaseHelper db = DatabaseHelper.getInstance(this);
        BackupManager backup = new BackupManager(this);
        SharedPreferences session = getSharedPreferences("session", MODE_PRIVATE);

        String user = session.getString("user_nome", "Usuario nao identificado");
        String appStatus = "APP\n"
                + "Versao: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "Modo: " + (BuildConfig.DEBUG ? "Debug" : "Release") + "\n"
                + "Usuario: " + user + "\n"
                + "Android: API " + Build.VERSION.SDK_INT;

        String dbStatus = "BANCO DE DADOS\n"
                + "Servidor: " + db.getHost() + ":" + db.getPort() + "\n"
                + "Base: " + db.getDatabase() + "\n"
                + "Usuario: " + db.getUsername() + "\n"
                + "Status: " + lastDbResult;

        String backupStatus = "BACKUP\n"
                + "FTP: " + safeText(backup.getFtpHost(), "Nao configurado") + "\n"
                + "Automatico: " + (backup.isAutoBackupEnabled() ? "Ativo" : "Desativado") + "\n"
                + "SQL completo: " + (backup.isSqlBackupEnabled() ? "Ativo" : "Desativado");

        String permissionStatus = "PERMISSOES\n" + buildPermissionSummary();
        String storageStatus = "ARMAZENAMENTO\n" + buildStorageSummary();
        String serverStatus = "SERVIDOR LOCAL\n"
                + "MariaDB integrado: " + (MariaDbServerManager.isInstalled(this) ? "Disponivel" : "Indisponivel") + "\n"
                + "Modo: dentro do PDV Pro, sem APK PHSERVER separado\n"
                + "Autostart: " + PhServerAutoStartReceiver.getLastResult(this) + "\n"
                + "Status: " + MariaDbServerManager.getLastStatus(this);
        String crashStatus = "FALHAS FATAIS\n" + CrashReportManager.getLastCrashSummary(this);

        tvAppStatus.setText(appStatus);
        tvDbStatus.setText(dbStatus);
        tvBackupStatus.setText(backupStatus);
        tvPermissionStatus.setText(permissionStatus);
        tvStorageStatus.setText(storageStatus);
        tvServerStatus.setText(serverStatus);
        tvCrashStatus.setText(crashStatus);
        tvResumo.setText("Versao " + BuildConfig.VERSION_NAME + " pronta para operacao assistida.");

        updateLicenseAsync();
        lastDiagnostic = composeDiagnostic(appStatus, dbStatus, backupStatus, permissionStatus,
                storageStatus, serverStatus, crashStatus);
    }

    private void updateLicenseAsync() {
        tvLicenseStatus.setText("LICENCA\nVerificando cache e banco...");
        new Thread(() -> {
            String licenseStatus;
            try {
                String dataExp = LicencaManager.getDataExpiracao(this);
                int dias = LicencaManager.getDiasParaVencimento(this);
                boolean expirada = LicencaManager.isLicencaExpirada(this);

                if (dataExp == null || dataExp.trim().isEmpty()) {
                    licenseStatus = "LICENCA\nNenhuma data de expiracao encontrada.";
                } else if (expirada || dias < 0) {
                    licenseStatus = "LICENCA\nStatus: vencida\nExpiracao: " + dataExp;
                } else if (dias == 0) {
                    licenseStatus = "LICENCA\nStatus: vence hoje\nExpiracao: " + dataExp;
                } else {
                    licenseStatus = "LICENCA\nStatus: ativa\nExpiracao: " + dataExp
                            + "\nDias restantes: " + dias;
                }
            } catch (Exception e) {
                licenseStatus = "LICENCA\nNao foi possivel verificar agora.\n" + e.getMessage();
            }

            String finalStatus = licenseStatus;
            runOnUiThread(() -> {
                tvLicenseStatus.setText(finalStatus);
                refreshDiagnosticTextOnly();
            });
        }, "PDV-License-Diagnostic").start();
    }

    private void testDatabase(boolean showProgress) {
        if (showProgress) {
            showLoading("Testando conexao com o banco...");
        } else {
            lastDbResult = "Testando em segundo plano...";
            refreshDiagnostics();
        }

        new Thread(() -> {
            long start = System.currentTimeMillis();
            boolean ok = false;
            String message;
            try {
                ok = DatabaseHelper.getInstance(this).testConnection();
                long elapsed = System.currentTimeMillis() - start;
                message = ok
                        ? "Conexao OK em " + elapsed + " ms."
                        : "Falha no teste de conexao.";
            } catch (Exception e) {
                message = "Erro ao testar: " + e.getMessage();
            }

            if (showProgress) hideLoading();
            boolean finalOk = ok;
            String finalMessage = message;
            runOnUiThread(() -> {
                lastDbResult = finalMessage;
                refreshDiagnostics();
                if (showProgress) {
                    if (finalOk) {
                        showSuccess(finalMessage);
                    } else {
                        showError(finalMessage);
                    }
                }
            });
        }, "PDV-DB-Diagnostic").start();
    }

    private void openActivity(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void copyDiagnostic() {
        refreshDiagnosticTextOnly();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("PDV Pro Diagnostico", lastDiagnostic));
            showSuccess("Resumo tecnico copiado para a area de transferencia.");
        } else {
            showError("Nao foi possivel acessar a area de transferencia.");
        }
    }

    private void refreshDiagnosticTextOnly() {
        lastDiagnostic = composeDiagnostic(
                textOf(tvAppStatus),
                textOf(tvDbStatus),
                textOf(tvBackupStatus),
                textOf(tvPermissionStatus),
                textOf(tvStorageStatus),
                textOf(tvServerStatus),
                textOf(tvCrashStatus)
        ) + "\n\n" + textOf(tvLicenseStatus);
    }

    private String composeDiagnostic(String app, String db, String backup, String permissions,
                                     String storage, String server, String crash) {
        return app + "\n\n" + db + "\n\n" + textOf(tvLicenseStatus) + "\n\n"
                + backup + "\n\n" + permissions + "\n\n" + storage + "\n\n"
                + server + "\n\n" + crash;
    }

    private String buildPermissionSummary() {
        PermissionCheck[] checks = {
                new PermissionCheck("Camera", Manifest.permission.CAMERA, 1),
                new PermissionCheck("Localizacao precisa", Manifest.permission.ACCESS_FINE_LOCATION, 1),
                new PermissionCheck("Localizacao aproximada", Manifest.permission.ACCESS_COARSE_LOCATION, 1),
                new PermissionCheck("Bluetooth", Manifest.permission.BLUETOOTH_CONNECT, Build.VERSION_CODES.S),
                new PermissionCheck("Busca Bluetooth", Manifest.permission.BLUETOOTH_SCAN, Build.VERSION_CODES.S),
                new PermissionCheck("Notificacoes", Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU),
                new PermissionCheck("Imagens", Manifest.permission.READ_MEDIA_IMAGES, Build.VERSION_CODES.TIRAMISU)
        };

        int total = 0;
        int granted = 0;
        StringBuilder details = new StringBuilder();
        for (PermissionCheck check : checks) {
            if (Build.VERSION.SDK_INT < check.minSdk) continue;
            total++;
            boolean ok = ContextCompat.checkSelfPermission(this, check.permission)
                    == PackageManager.PERMISSION_GRANTED;
            if (ok) granted++;
            details.append(check.label)
                    .append(": ")
                    .append(ok ? "OK" : "pendente")
                    .append('\n');
        }
        return "Concedidas: " + granted + "/" + total + "\n" + details.toString().trim();
    }

    private String buildStorageSummary() {
        File dir = getFilesDir();
        StatFs stat = new StatFs(dir.getAbsolutePath());
        long available = stat.getAvailableBytes();
        long total = stat.getTotalBytes();
        double percent = total > 0 ? (available * 100.0 / total) : 0.0;
        return "Livre: " + formatBytes(available) + "\n"
                + "Total: " + formatBytes(total) + "\n"
                + "Disponivel: " + String.format(Locale.US, "%.1f%%", percent);
    }

    private String textOf(TextView view) {
        return view == null || view.getText() == null ? "" : view.getText().toString();
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private static final class PermissionCheck {
        final String label;
        final String permission;
        final int minSdk;

        PermissionCheck(String label, String permission, int minSdk) {
            this.label = label;
            this.permission = permission;
            this.minSdk = minSdk;
        }
    }
}
