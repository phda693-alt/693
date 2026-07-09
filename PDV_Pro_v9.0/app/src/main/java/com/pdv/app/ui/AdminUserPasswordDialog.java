package com.pdv.app.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.UserActionLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Solicita e valida a senha de qualquer usuario ativo com perfil Administrador. */
public final class AdminUserPasswordDialog {
    private AdminUserPasswordDialog() {}

    public static void show(Activity activity, String title, String message, Runnable authorized) {
        EditText input = new EditText(activity);
        input.setHint("Senha de um administrador");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(32, 20, 32, 20);

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setPositiveButton("Autorizar", (d, w) -> {
                    String password = input.getText().toString();
                    if (password.trim().isEmpty()) return;
                    ProgressDialog progress = ProgressDialog.show(activity, "Autorizacao",
                            "Validando administrador...", true, false);
                    new Thread(() -> {
                        boolean ok = validate(activity, password);
                        activity.runOnUiThread(() -> {
                            try { progress.dismiss(); } catch (Exception ignored) {}
                            if (ok) {
                                UserActionLogger.log(activity, "AUTORIZACAO_ADMIN", title,
                                        "Autorizacao administrativa concedida");
                                authorized.run();
                            } else {
                                new AlertDialog.Builder(activity)
                                        .setTitle("Autorizacao negada")
                                        .setMessage("A senha nao pertence a um usuario Administrador ativo.")
                                        .setPositiveButton("Entendi", null)
                                        .show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static boolean validate(Activity activity, String password) {
        try {
            Connection conn = DatabaseHelper.getInstance(activity).getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.id FROM usuarios u LEFT JOIN perfis p ON u.perfil_id=p.id "
                            + "WHERE u.senha=? AND u.ativo=1 AND "
                            + "(LOWER(u.nivel)='admin' OR LOWER(p.nome)='administrador' OR p.sistematico=1) LIMIT 1");
            ps.setString(1, password);
            ResultSet rs = ps.executeQuery();
            boolean ok = rs.next();
            rs.close();
            ps.close();
            return ok;
        } catch (Exception ignored) {
            return false;
        }
    }
}
