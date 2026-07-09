package com.phda.phserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Tela "Banco de Dados" do PHSERVER.
 *
 * Botoes:
 *   - CRIAR BANCO        (CREATE DATABASE)
 *   - LISTAR BANCOS      (SHOW DATABASES)
 *   - LISTAR TABELAS     (information_schema.TABLES do banco informado)
 *   - EXCLUIR BANCO      (DROP DATABASE com confirmacao)
 */
public class CreateDatabaseActivity extends Activity {

    private EditText hostInput;
    private EditText portInput;
    private EditText userInput;
    private EditText passInput;
    private EditText dbInput;
    private Button createBtn;
    private Button listBtn;
    private Button listTablesBtn;
    private Button dropBtn;
    private TextView resultView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int COLOR_BG = 0xFF101820;
    private static final int COLOR_NEON = 0xFFFF10F0;
    private static final int COLOR_LIST = 0xFF2563EB;
    private static final int COLOR_TABLES = 0xFF7C3AED;
    private static final int COLOR_DROP = 0xFFD32F2F;
    private static final int COLOR_GREEN = 0xFF39FF14;
    private static final int COLOR_RED = 0xFFFF5252;
    private static final int COLOR_GRAY = 0xFFB0BEC5;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFF607D8B;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("PDV Pro - Banco de Dados");

        int dp = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setPadding(16 * dp, 16 * dp, 16 * dp, 16 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView header = new TextView(this);
        header.setText("Banco de Dados");
        header.setTextColor(COLOR_WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        header.setPadding(0, 0, 0, 8 * dp);
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Conecta no servidor MariaDB/MySQL local. Use os botoes "
                + "abaixo para criar/listar/excluir bancos e listar tabelas.");
        hint.setTextColor(COLOR_GRAY);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, 0, 0, 16 * dp);
        root.addView(hint);

        hostInput = labeledEdit(root, dp, "Host", "127.0.0.1", InputType.TYPE_CLASS_TEXT);
        portInput = labeledEdit(root, dp, "Porta", "3306", InputType.TYPE_CLASS_NUMBER);
        userInput = labeledEdit(root, dp, "Usuario", "root", InputType.TYPE_CLASS_TEXT);
        passInput = labeledEdit(root, dp, "Senha (em branco se default)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dbInput = labeledEdit(root, dp, "Nome do banco (criar/excluir/listar tabelas)",
                "meu_banco", InputType.TYPE_CLASS_TEXT);

        createBtn = makeButton(dp, "CRIAR BANCO", COLOR_NEON);
        addBtn(root, createBtn, dp, 16);
        createBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runCreate(); }
        });

        listBtn = makeButton(dp, "LISTAR BANCOS", COLOR_LIST);
        addBtn(root, listBtn, dp, 8);
        listBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runList(); }
        });

        listTablesBtn = makeButton(dp, "LISTAR TABELAS DO BANCO", COLOR_TABLES);
        addBtn(root, listTablesBtn, dp, 8);
        listTablesBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runListTables(); }
        });

        dropBtn = makeButton(dp, "EXCLUIR BANCO", COLOR_DROP);
        addBtn(root, dropBtn, dp, 8);
        dropBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmAndDrop(); }
        });

        TextView resultTitle = new TextView(this);
        resultTitle.setText("Saida:");
        resultTitle.setTextColor(COLOR_WHITE);
        resultTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams rtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rtLp.topMargin = 16 * dp;
        rtLp.bottomMargin = 4 * dp;
        root.addView(resultTitle, rtLp);

        resultView = new TextView(this);
        resultView.setText("(aguardando)");
        resultView.setTextColor(COLOR_GREEN);
        resultView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        resultView.setTypeface(android.graphics.Typeface.MONOSPACE);
        resultView.setPadding(8 * dp, 8 * dp, 8 * dp, 8 * dp);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(0xFF000000);
        rbg.setStroke(1 * dp, COLOR_GREEN);
        rbg.setCornerRadius(4 * dp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            resultView.setBackground(rbg);
        } else {
            resultView.setBackgroundDrawable(rbg);
        }
        resultView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(resultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    // ============================================================
    // Acoes
    // ============================================================

    private static class ConnInfo {
        final String host; final int port; final String user; final String pass;
        ConnInfo(String h, int p, String u, String pw) {
            host = h; port = p; user = u; pass = pw;
        }
    }

    private ConnInfo readConn() {
        return new ConnInfo(
                trimOr(hostInput.getText().toString(), "127.0.0.1"),
                parseIntOr(portInput.getText().toString(), 3306),
                trimOr(userInput.getText().toString(), "root"),
                passInput.getText().toString());
    }

    private void runCreate() {
        final ConnInfo c = readConn();
        final String db = dbInput.getText().toString().trim();
        if (db.isEmpty()) { setError("Informe o nome do banco."); return; }
        if (!isSafeIdentifier(db)) {
            setError("Nome invalido. Use letras, numeros e underline (max 64).");
            return;
        }
        setBusy("Conectando em " + c.host + ":" + c.port + " ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String sql = "CREATE DATABASE `" + db.replace("`", "``") + "`";
                    log.append("Enviando: ").append(sql).append("\n");
                    String reply = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.user, c.pass, sql, 8000);
                    log.append(reply);
                    ok = !reply.startsWith("ERR ");
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    private void runList() {
        final ConnInfo c = readConn();
        setBusy("Listando bancos em " + c.host + ":" + c.port + " ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    log.append("Enviando: SHOW DATABASES\n\n");
                    MySqlMiniClient.QueryResult r = MySqlMiniClient.executeQuery(
                            c.host, c.port, c.user, c.pass, "SHOW DATABASES", 8000);
                    if (r.isError()) {
                        log.append(r.errorMessage);
                    } else if (r.rows.isEmpty()) {
                        log.append("(nenhum banco)"); ok = true;
                    } else {
                        log.append("Total: ").append(r.rows.size()).append("\n");
                        log.append("--------------------------------\n");
                        for (List<String> row : r.rows) {
                            String name = row.isEmpty() ? "(?)" : row.get(0);
                            log.append("  ").append(name).append("\n");
                        }
                        ok = true;
                    }
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    private void runListTables() {
        final ConnInfo c = readConn();
        final String db = dbInput.getText().toString().trim();
        if (db.isEmpty()) { setError("Informe o nome do banco."); return; }
        if (!isSafeIdentifier(db)) {
            setError("Nome invalido. Use letras, numeros e underline (max 64).");
            return;
        }
        setBusy("Listando tabelas de '" + db + "' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String sql = "SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_ROWS, "
                            + "DATA_LENGTH FROM information_schema.TABLES "
                            + "WHERE TABLE_SCHEMA = '" + escapeSql(db) + "' "
                            + "ORDER BY TABLE_NAME";
                    log.append(sql).append("\n\n");
                    MySqlMiniClient.QueryResult r = MySqlMiniClient.executeQuery(
                            c.host, c.port, c.user, c.pass, sql, 8000);
                    if (r.isError()) {
                        log.append(r.errorMessage);
                    } else if (r.rows.isEmpty()) {
                        log.append("(banco '").append(db)
                                .append("' nao tem tabelas, ou nao existe)");
                        ok = true;
                    } else {
                        log.append("Total: ").append(r.rows.size()).append(" tabela(s)\n");
                        log.append("------------------------------------------------\n");
                        log.append(String.format("%-30s %-10s %-10s %-8s %s%n",
                                "TABELA", "TIPO", "ENGINE", "LINHAS", "DATA(KB)"));
                        for (List<String> row : r.rows) {
                            String name = row.size() > 0 ? str(row.get(0)) : "?";
                            String type = row.size() > 1 ? str(row.get(1)) : "?";
                            String eng  = row.size() > 2 ? str(row.get(2)) : "?";
                            String rows = row.size() > 3 ? str(row.get(3)) : "?";
                            String dl   = row.size() > 4 ? str(row.get(4)) : "?";
                            String dlKb;
                            try {
                                long bytes = Long.parseLong(dl);
                                dlKb = String.valueOf(bytes / 1024);
                            } catch (Exception e) { dlKb = dl; }
                            String shortType = "BASE TABLE".equals(type) ? "TABLE" : type;
                            log.append(String.format("%-30s %-10s %-10s %-8s %s%n",
                                    truncate(name, 30), truncate(shortType, 10),
                                    truncate(eng, 10), truncate(rows, 8), dlKb));
                        }
                        ok = true;
                    }
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    private void confirmAndDrop() {
        final String db = dbInput.getText().toString().trim();
        if (db.isEmpty()) { setError("Informe o nome do banco a excluir."); return; }
        if (!isSafeIdentifier(db)) {
            setError("Nome invalido. Use letras, numeros e underline (max 64).");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Excluir banco")
                .setMessage("Tem certeza que deseja excluir o banco '" + db
                        + "'?\n\nEsta acao e IRREVERSIVEL e apaga todas as "
                        + "tabelas e dados desse banco.")
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        runDrop(db);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runDrop(final String db) {
        final ConnInfo c = readConn();
        setBusy("Removendo banco '" + db + "' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String sql = "DROP DATABASE `" + db.replace("`", "``") + "`";
                    log.append("Enviando: ").append(sql).append("\n");
                    String reply = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.user, c.pass, sql, 8000);
                    log.append(reply);
                    ok = !reply.startsWith("ERR ");
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void addBtn(LinearLayout parent, Button b, int dp, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMarginDp * dp;
        parent.addView(b, lp);
    }

    private Button makeButton(int dp, String text, int bgColor) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(COLOR_WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(8 * dp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            b.setBackground(bg);
        } else {
            b.setBackgroundDrawable(bg);
        }
        return b;
    }

    private EditText labeledEdit(LinearLayout parent, int dp, String label,
                                  String value, int inputType) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(COLOR_GRAY);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8 * dp;
        parent.addView(tv, lp);

        EditText et = new EditText(this);
        et.setInputType(inputType);
        et.setTextColor(COLOR_WHITE);
        et.setText(value);
        et.setHintTextColor(COLOR_HINT);
        et.setSingleLine(true);
        parent.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private void setBusy(String msg) {
        createBtn.setEnabled(false);
        listBtn.setEnabled(false);
        listTablesBtn.setEnabled(false);
        dropBtn.setEnabled(false);
        resultView.setTextColor(COLOR_GRAY);
        resultView.setText(msg);
    }

    private void setError(String msg) {
        resultView.setTextColor(COLOR_RED);
        resultView.setText(msg);
    }

    private void postResult(final String text, final boolean success) {
        mainHandler.post(new Runnable() {
            @Override public void run() {
                resultView.setTextColor(success ? COLOR_GREEN : COLOR_RED);
                resultView.setText(text);
                createBtn.setEnabled(true);
                listBtn.setEnabled(true);
                listTablesBtn.setEnabled(true);
                dropBtn.setEnabled(true);
            }
        });
    }

    private static void runOnBg(Runnable r) {
        Thread t = new Thread(r, "phserver-db-op");
        t.setDaemon(true);
        t.start();
    }

    private static String trimOr(String s, String def) {
        if (s == null) return def;
        s = s.trim();
        return s.isEmpty() ? def : s;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s == null ? "" : s.trim()); }
        catch (Exception e) { return def; }
    }

    private static boolean isSafeIdentifier(String s) {
        if (s.length() == 0 || s.length() > 64) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    private static String escapeSql(String s) {
        StringBuilder out = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '\'') out.append("\\'");
            else out.append(c);
        }
        return out.toString();
    }

    private static String str(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
