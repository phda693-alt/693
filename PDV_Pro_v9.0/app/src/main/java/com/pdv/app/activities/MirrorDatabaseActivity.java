package com.pdv.app.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.pdv.app.R;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.sync.MirrorSyncManager;
import com.pdv.app.utils.UserActionLogger;

public class MirrorDatabaseActivity extends BaseActivity {
    private EditText etHost, etPort, etDatabase, etUser, etPassword;
    private CheckBox cbEnabled;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mirror_database);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.MYSQL_ESPELHO_ACESSAR)) return;

        etHost = findViewById(R.id.etMirrorHost);
        etPort = findViewById(R.id.etMirrorPort);
        etDatabase = findViewById(R.id.etMirrorDatabase);
        etUser = findViewById(R.id.etMirrorUser);
        etPassword = findViewById(R.id.etMirrorPassword);
        cbEnabled = findViewById(R.id.cbMirrorEnabled);
        tvStatus = findViewById(R.id.tvMirrorStatus);
        Button btnTest = findViewById(R.id.btnMirrorTest);
        Button btnSave = findViewById(R.id.btnMirrorSave);
        Button btnSync = findViewById(R.id.btnMirrorSync);

        SharedPreferences p = MirrorSyncManager.getPrefs(this);
        etHost.setText(p.getString("host", ""));
        etPort.setText(String.valueOf(p.getInt("port", 3306)));
        etDatabase.setText(p.getString("database", "pdv_espelho"));
        etUser.setText(p.getString("user", ""));
        etPassword.setText(p.getString("password", ""));
        cbEnabled.setChecked(p.getBoolean("enabled", false));
        tvStatus.setText(p.getString("last_status", "Ainda nao sincronizado."));

        btnSave.setOnClickListener(v -> save());
        btnTest.setOnClickListener(v -> { save(); test(); });
        PermissionHelper.controlarVisibilidade(this, btnSync, PermissionConstants.MYSQL_ESPELHO_SINCRONIZAR);
        btnSync.setOnClickListener(v -> {
            if (PermissionHelper.verificar(this, PermissionConstants.MYSQL_ESPELHO_SINCRONIZAR)) {
                save(); sync();
            }
        });
    }

    private void save() {
        int port = 3306;
        try { port = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
        MirrorSyncManager.saveConfig(this, cbEnabled.isChecked(), etHost.getText().toString().trim(),
                port, etDatabase.getText().toString().trim(), etUser.getText().toString().trim(),
                etPassword.getText().toString());
        showToast("Configuracao do espelho salva.");
    }

    private void test() {
        showLoading("Testando MySQL espelho...");
        new Thread(() -> {
            boolean ok = MirrorSyncManager.testConnection(this);
            hideLoading();
            if (ok) showSuccess("Conexao realizada e banco espelho verificado.");
            else showError("Nao foi possivel acessar o MySQL espelho. Confira host, porta, banco, usuario e senha.");
        }).start();
    }

    private void sync() {
        UserActionLogger.log(this, "SINCRONIZAR", "MySQL Espelho", "Sincronizacao manual solicitada");
        showLoading("Preparando sincronizacao...");
        MirrorSyncManager.syncNow(this, new MirrorSyncManager.Callback() {
            @Override public void onProgress(String message) { showLoading(message); }
            @Override public void onComplete(boolean success, String message) {
                hideLoading();
                runOnUiThread(() -> {
                    tvStatus.setText(message);
                    if (success) showSuccess(message); else showError(message);
                });
            }
        });
    }
}
