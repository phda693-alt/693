package com.phda.phserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Tela "Gerenciar Usuarios" do PHSERVER.
 *
 * Botoes:
 *   - CRIAR USUARIO              (CREATE USER + opcional GRANT ALL + FLUSH)
 *   - LISTAR USUARIOS            (SELECT User, Host FROM mysql.user)
 *   - TROCAR SENHA               (ALTER USER ... IDENTIFIED BY ...)
 *   - LIBERAR ESCUTA 0.0.0.0     (preference address=all + RESTART)
 *   - ACESSO REMOTO (GRANT ALL)  (CREATE/ALTER/GRANT em user@'%' + QR)
 *   - REVOGAR ACESSO REMOTO      (REVOKE + DROP USER 'name'@'%' + FLUSH)
 *   - EXCLUIR USUARIO            (DROP USER + FLUSH, com confirmacao)
 *
 * UI 100% programatica.
 */
public class ManageUsersActivity extends Activity {

    private EditText hostInput;
    private EditText portInput;
    private EditText adminInput;
    private EditText adminPassInput;

    private EditText userNameInput;
    private EditText userHostInput;
    private EditText userPassInput;
    private CheckBox grantAllCheck;

    private Button createBtn;
    private Button listBtn;
    private Button changePassBtn;
    private Button bindAddrBtn;
    private Button remoteBtn;
    private Button revokeRemoteBtn;
    private Button dropBtn;

    private TextView resultView;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final int COLOR_BG       = 0xFF101820;
    private static final int COLOR_CREATE   = 0xFF00BFA5;
    private static final int COLOR_LIST     = 0xFF2563EB;
    private static final int COLOR_DROP     = 0xFFD32F2F;
    private static final int COLOR_PASS     = 0xFFFF9800;
    private static final int COLOR_REMOTE   = 0xFFFFC107;
    private static final int COLOR_REVOKE   = 0xFF6B7280;
    private static final int COLOR_BIND     = 0xFF8B5CF6;
    private static final int COLOR_GREEN    = 0xFF39FF14;
    private static final int COLOR_RED      = 0xFFFF5252;
    private static final int COLOR_GRAY     = 0xFFB0BEC5;
    private static final int COLOR_WHITE    = 0xFFFFFFFF;
    private static final int COLOR_HINT     = 0xFF607D8B;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("PDV Pro - Usuarios MySQL");

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
        header.setText("Gerenciar usuarios");
        header.setTextColor(COLOR_WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        header.setPadding(0, 0, 0, 8 * dp);
        root.addView(header);

        TextView hint = new TextView(this);
        hint.setText("Conecta como administrador (root) e gerencia usuarios "
                + "do servidor MariaDB/MySQL local.");
        hint.setTextColor(COLOR_GRAY);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setPadding(0, 0, 0, 16 * dp);
        root.addView(hint);

        sectionTitle(root, dp, "Conexao");
        hostInput = labeledEdit(root, dp, "Host", "127.0.0.1", InputType.TYPE_CLASS_TEXT);
        portInput = labeledEdit(root, dp, "Porta", "3306", InputType.TYPE_CLASS_NUMBER);
        adminInput = labeledEdit(root, dp, "Usuario admin", "root", InputType.TYPE_CLASS_TEXT);
        adminPassInput = labeledEdit(root, dp, "Senha admin (em branco se default)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        sectionTitle(root, dp, "Usuario alvo");
        userNameInput = labeledEdit(root, dp, "Nome do usuario", "novo_user",
                InputType.TYPE_CLASS_TEXT);
        userHostInput = labeledEdit(root, dp, "Host do usuario (% para qualquer)", "%",
                InputType.TYPE_CLASS_TEXT);
        userPassInput = labeledEdit(root, dp, "Senha (criar / nova senha ao trocar)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        grantAllCheck = new CheckBox(this);
        grantAllCheck.setText("Conceder GRANT ALL PRIVILEGES ON *.* (admin total)");
        grantAllCheck.setTextColor(COLOR_WHITE);
        grantAllCheck.setChecked(true);
        LinearLayout.LayoutParams gcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gcLp.topMargin = 8 * dp;
        root.addView(grantAllCheck, gcLp);

        sectionTitle(root, dp, "Acoes");

        createBtn = makeButton(dp, "CRIAR USUARIO", COLOR_CREATE);
        addBtn(root, createBtn, dp);
        createBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runCreate(); }
        });

        listBtn = makeButton(dp, "LISTAR USUARIOS", COLOR_LIST);
        addBtn(root, listBtn, dp);
        listBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runList(); }
        });

        changePassBtn = makeButton(dp, "TROCAR SENHA", COLOR_PASS);
        addBtn(root, changePassBtn, dp);
        changePassBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runChangePass(); }
        });

        bindAddrBtn = makeButton(dp, "LIBERAR ESCUTA EM 0.0.0.0 (REINICIA SERVIDOR)", COLOR_BIND);
        addBtn(root, bindAddrBtn, dp);
        bindAddrBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmAndBindAll(); }
        });

        remoteBtn = makeButton(dp, "ACESSO REMOTO (GRANT ALL)", COLOR_REMOTE);
        remoteBtn.setTextColor(0xFF000000);
        addBtn(root, remoteBtn, dp);
        remoteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runGrantRemote(); }
        });

        revokeRemoteBtn = makeButton(dp, "REVOGAR ACESSO REMOTO", COLOR_REVOKE);
        addBtn(root, revokeRemoteBtn, dp);
        revokeRemoteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmAndRevokeRemote(); }
        });

        dropBtn = makeButton(dp, "EXCLUIR USUARIO", COLOR_DROP);
        addBtn(root, dropBtn, dp);
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

    // ==================== Acoes ====================

    private static class ConnInfo {
        final String host; final int port;
        final String admin; final String adminPass;
        ConnInfo(String h, int p, String a, String ap) {
            host = h; port = p; admin = a; adminPass = ap;
        }
    }

    private ConnInfo readConn() {
        return new ConnInfo(
                trimOr(hostInput.getText().toString(), "127.0.0.1"),
                parseIntOr(portInput.getText().toString(), 3306),
                trimOr(adminInput.getText().toString(), "root"),
                adminPassInput.getText().toString());
    }

    private String userName() { return userNameInput.getText().toString().trim(); }
    private String userHost() { return userHostInput.getText().toString().trim(); }
    private String userPass() { return userPassInput.getText().toString(); }

    private boolean validateUserHost(boolean requirePass) {
        String name = userName();
        String host = userHost();
        if (name.isEmpty()) { setError("Informe o nome do usuario."); return false; }
        if (!isSafeIdentifier(name)) {
            setError("Nome invalido. Use letras, numeros e underline (max 64).");
            return false;
        }
        if (host.isEmpty()) {
            setError("Informe o host (ex: %, localhost).");
            return false;
        }
        if (!isSafeHost(host)) {
            setError("Host invalido. Use %, _, alfanumerico, ponto, dois pontos, hifen, barra.");
            return false;
        }
        if (requirePass && userPass().length() == 0) {
            setError("Informe a senha.");
            return false;
        }
        return true;
    }

    private void runCreate() {
        if (!validateUserHost(true)) return;
        final ConnInfo c = readConn();
        final String name = userName();
        final String host = userHost();
        final String pass = userPass();
        final boolean grant = grantAllCheck.isChecked();
        setBusy("Criando usuario '" + name + "'@'" + host + "' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String userExpr = "'" + escapeSql(name) + "'@'" + escapeSql(host) + "'";
                    String createSql = "CREATE USER " + userExpr
                            + " IDENTIFIED BY '" + escapeSql(pass) + "'";
                    log.append(redactPass(createSql, pass)).append("\n");
                    String r1 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, createSql, 8000);
                    log.append("  -> ").append(r1).append("\n");
                    if (r1.startsWith("ERR ")) { postResult(log.toString(), false); return; }
                    if (grant) {
                        String grantSql = "GRANT ALL PRIVILEGES ON *.* TO " + userExpr
                                + " WITH GRANT OPTION";
                        log.append("\n").append(grantSql).append("\n");
                        String r2 = MySqlMiniClient.executeStatement(
                                c.host, c.port, c.admin, c.adminPass, grantSql, 8000);
                        log.append("  -> ").append(r2).append("\n");
                        if (r2.startsWith("ERR ")) { postResult(log.toString(), false); return; }
                        log.append("\nFLUSH PRIVILEGES\n");
                        String r3 = MySqlMiniClient.executeStatement(
                                c.host, c.port, c.admin, c.adminPass, "FLUSH PRIVILEGES", 8000);
                        log.append("  -> ").append(r3).append("\n");
                        if (r3.startsWith("ERR ")) { postResult(log.toString(), false); return; }
                    }
                    log.append("\nUsuario criado com sucesso.");
                    ok = true;
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
        setBusy("Listando usuarios em " + c.host + ":" + c.port + " ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String sql = "SELECT User, Host FROM mysql.user ORDER BY User, Host";
                    log.append(sql).append("\n\n");
                    MySqlMiniClient.QueryResult r = MySqlMiniClient.executeQuery(
                            c.host, c.port, c.admin, c.adminPass, sql, 8000);
                    if (r.isError()) {
                        log.append(r.errorMessage);
                    } else if (r.rows.isEmpty()) {
                        log.append("(nenhum usuario)"); ok = true;
                    } else {
                        log.append("Total: ").append(r.rows.size()).append("\n");
                        log.append("--------------------------------\n");
                        for (List<String> row : r.rows) {
                            String u = row.size() > 0 ? row.get(0) : "(?)";
                            String h = row.size() > 1 ? row.get(1) : "(?)";
                            log.append("  '").append(u).append("'@'").append(h).append("'\n");
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
        if (!validateUserHost(false)) return;
        final String name = userName();
        final String host = userHost();
        new AlertDialog.Builder(this)
                .setTitle("Excluir usuario")
                .setMessage("Tem certeza que deseja excluir o usuario '"
                        + name + "'@'" + host + "'?\n\nQualquer aplicativo "
                        + "que use essas credenciais deixara de funcionar.")
                .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        runDrop(name, host);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runDrop(final String name, final String host) {
        final ConnInfo c = readConn();
        setBusy("Removendo usuario '" + name + "'@'" + host + "' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String sql = "DROP USER '" + escapeSql(name) + "'@'"
                            + escapeSql(host) + "'";
                    log.append(sql).append("\n");
                    String r1 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, sql, 8000);
                    log.append("  -> ").append(r1).append("\n");
                    if (r1.startsWith("ERR ")) { postResult(log.toString(), false); return; }
                    log.append("\nFLUSH PRIVILEGES\n");
                    String r2 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, "FLUSH PRIVILEGES", 8000);
                    log.append("  -> ").append(r2).append("\n");
                    ok = !r2.startsWith("ERR ");
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    private void runChangePass() {
        if (!validateUserHost(true)) return;
        final ConnInfo c = readConn();
        final String name = userName();
        final String host = userHost();
        final String pass = userPass();
        setBusy("Trocando senha de '" + name + "'@'" + host + "' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String alterSql = "ALTER USER '" + escapeSql(name) + "'@'"
                            + escapeSql(host) + "' IDENTIFIED BY '" + escapeSql(pass) + "'";
                    log.append(redactPass(alterSql, pass)).append("\n");
                    String r1 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, alterSql, 8000);
                    log.append("  -> ").append(r1).append("\n");
                    if (r1.startsWith("ERR ")) {
                        log.append("\n[ALTER USER falhou, tentando SET PASSWORD ...]\n");
                        String setSql = "SET PASSWORD FOR '" + escapeSql(name) + "'@'"
                                + escapeSql(host) + "' = PASSWORD('" + escapeSql(pass) + "')";
                        log.append(redactPass(setSql, pass)).append("\n");
                        String r2 = MySqlMiniClient.executeStatement(
                                c.host, c.port, c.admin, c.adminPass, setSql, 8000);
                        log.append("  -> ").append(r2).append("\n");
                        if (r2.startsWith("ERR ")) { postResult(log.toString(), false); return; }
                    }
                    log.append("\nFLUSH PRIVILEGES\n");
                    String r3 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, "FLUSH PRIVILEGES", 8000);
                    log.append("  -> ").append(r3).append("\n");
                    ok = !r3.startsWith("ERR ");
                    if (ok) log.append("\nSenha alterada com sucesso.");
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    /** Concede acesso remoto: garante user@'%', GRANT ALL, FLUSH, mostra QR. */
    private void runGrantRemote() {
        if (!validateUserHost(true)) return;
        final ConnInfo c = readConn();
        final String name = userName();
        final String pass = userPass();
        setBusy("Concedendo acesso remoto a '" + name + "'@'%' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                String connectionUri = null;
                String firstIp = null;
                try {
                    String userExpr = "'" + escapeSql(name) + "'@'%'";

                    String createSql = "CREATE USER IF NOT EXISTS " + userExpr
                            + " IDENTIFIED BY '" + escapeSql(pass) + "'";
                    log.append(redactPass(createSql, pass)).append("\n");
                    String r1 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, createSql, 8000);
                    log.append("  -> ").append(r1).append("\n");
                    if (r1.startsWith("ERR ")) { postResult(log.toString(), false); return; }

                    String alterSql = "ALTER USER " + userExpr
                            + " IDENTIFIED BY '" + escapeSql(pass) + "'";
                    log.append("\n").append(redactPass(alterSql, pass)).append("\n");
                    String r2 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, alterSql, 8000);
                    log.append("  -> ").append(r2).append("\n");

                    String grantSql = "GRANT ALL PRIVILEGES ON *.* TO " + userExpr
                            + " WITH GRANT OPTION";
                    log.append("\n").append(grantSql).append("\n");
                    String r3 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, grantSql, 8000);
                    log.append("  -> ").append(r3).append("\n");
                    if (r3.startsWith("ERR ")) { postResult(log.toString(), false); return; }

                    log.append("\nFLUSH PRIVILEGES\n");
                    String r4 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, "FLUSH PRIVILEGES", 8000);
                    log.append("  -> ").append(r4).append("\n");
                    if (r4.startsWith("ERR ")) { postResult(log.toString(), false); return; }

                    log.append("\n=================================================\n");
                    log.append("CONFIGURACAO PARA ACESSO REMOTO\n");
                    log.append("=================================================\n");
                    appendServerVar(log, c, "bind_address");
                    appendServerVar(log, c, "skip_networking");
                    appendServerVar(log, c, "port");

                    log.append("\nIPs locais deste aparelho:\n");
                    firstIp = appendLocalIpsAndReturnFirst(log);

                    log.append("\nUsuario '").append(name).append("'@'%' agora tem ");
                    log.append("permissao TOTAL para conectar de qualquer host.\n");

                    String ipForUri = firstIp != null ? firstIp : "<IP-do-aparelho>";
                    connectionUri = "mysql://" + name + ":" + pass + "@"
                            + ipForUri + ":" + c.port + "/";
                    log.append("\nString de conexao (clique 'OK' para ver QR Code):\n");
                    log.append("  ").append("mysql://").append(name).append(":***@")
                            .append(ipForUri).append(":").append(c.port).append("/\n");
                    log.append("  jdbc:mariadb://").append(ipForUri).append(":")
                            .append(c.port).append("/\n");

                    if (pass == null || pass.length() == 0) {
                        log.append("\nATENCAO: senha em branco + acesso global = ");
                        log.append("INSEGURO em redes nao confiaveis.\n");
                    }
                    ok = true;
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                final String text = log.toString();
                final boolean success = ok;
                final String uri = connectionUri;
                final String ip = firstIp;
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        postResultDirect(text, success);
                        if (success && uri != null) {
                            showQrDialog(uri, ip);
                        }
                    }
                });
            }
        });
    }

    /** Mostra um diálogo com o QR Code da string de conexão. */
    private void showQrDialog(String connectionUri, String ip) {
        int dp = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());

        int qrSizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 280, getResources().getDisplayMetrics());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(16 * dp, 16 * dp, 16 * dp, 16 * dp);

        try {
            Bitmap bmp = QrBitmap.encode(connectionUri, qrSizePx, 4);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bmp);
            iv.setAdjustViewBounds(true);
            root.addView(iv, new LinearLayout.LayoutParams(qrSizePx, qrSizePx));
        } catch (Throwable th) {
            TextView err = new TextView(this);
            err.setText("Falha ao gerar QR: " + th.getMessage());
            err.setTextColor(0xFFFF5252);
            root.addView(err);
        }

        TextView lbl = new TextView(this);
        StringBuilder s = new StringBuilder();
        s.append("Aponte o leitor de QR do PC/outro celular.\n\n");
        s.append("URI codificada (com a senha em texto puro):\n");
        s.append("mysql://USER:SENHA@");
        s.append(ip != null ? ip : "<IP>").append(":3306/\n");
        s.append("\nEvite enviar essa imagem por canais publicos.");
        lbl.setText(s.toString());
        lbl.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12 * dp;
        root.addView(lbl, lp);

        new AlertDialog.Builder(this)
                .setTitle("QR Code da conexao")
                .setView(root)
                .setPositiveButton("Fechar", null)
                .show();
    }

    /** Revoga acesso remoto: REVOKE + DROP USER 'name'@'%' + FLUSH. */
    private void confirmAndRevokeRemote() {
        final String name = userName();
        if (name.isEmpty() || !isSafeIdentifier(name)) {
            setError("Informe um nome de usuario valido.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Revogar acesso remoto")
                .setMessage("Vai revogar privilegios e remover '" + name + "'@'%'.\n\n"
                        + "O usuario continua existindo se houver entradas em outros hosts "
                        + "(ex: localhost). Continuar?")
                .setPositiveButton("Revogar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        runRevokeRemote(name);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runRevokeRemote(final String name) {
        final ConnInfo c = readConn();
        setBusy("Revogando acesso remoto de '" + name + "'@'%' ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String userExpr = "'" + escapeSql(name) + "'@'%'";

                    String revokeSql = "REVOKE ALL PRIVILEGES, GRANT OPTION FROM " + userExpr;
                    log.append(revokeSql).append("\n");
                    String r1 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, revokeSql, 8000);
                    log.append("  -> ").append(r1).append("\n");
                    // erro aqui geralmente significa que o user nao existia em '%' - seguir

                    String dropSql = "DROP USER IF EXISTS " + userExpr;
                    log.append("\n").append(dropSql).append("\n");
                    String r2 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, dropSql, 8000);
                    log.append("  -> ").append(r2).append("\n");
                    if (r2.startsWith("ERR ")) { postResult(log.toString(), false); return; }

                    log.append("\nFLUSH PRIVILEGES\n");
                    String r3 = MySqlMiniClient.executeStatement(
                            c.host, c.port, c.admin, c.adminPass, "FLUSH PRIVILEGES", 8000);
                    log.append("  -> ").append(r3).append("\n");
                    ok = !r3.startsWith("ERR ");
                    if (ok) {
                        log.append("\nAcesso remoto revogado para '").append(name)
                                .append("'@'%'.");
                    }
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    /** Configura a preferencia 'address' do app para 'all' e reinicia o servidor. */
    private void confirmAndBindAll() {
        new AlertDialog.Builder(this)
                .setTitle("Liberar escuta em 0.0.0.0")
                .setMessage("Esta acao define a interface de rede do servidor para "
                        + "'todos os enderecos' (0.0.0.0) e REINICIA o servidor para "
                        + "aplicar.\n\nIsso permite conexoes de outros aparelhos da "
                        + "rede. Continuar?")
                .setPositiveButton("Liberar e reiniciar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        runBindAll();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void runBindAll() {
        setBusy("Configurando interface = 'all' (0.0.0.0) ...");
        runOnBg(new Runnable() {
            @Override public void run() {
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                try {
                    String before = ServerConfigHelper.getAddress(ManageUsersActivity.this);
                    log.append("Preference 'address' antes: ")
                            .append(before == null ? "(default)" : before).append("\n");
                    int rows = ServerConfigHelper.setAddress(
                            ManageUsersActivity.this, "all");
                    log.append("ContentProvider.update -> linhas afetadas: ")
                            .append(rows).append("\n");
                    String after = ServerConfigHelper.getAddress(ManageUsersActivity.this);
                    log.append("Preference 'address' depois: ")
                            .append(after == null ? "(default)" : after).append("\n");

                    if (rows < 1 || !"all".equals(after)) {
                        log.append("\nNao foi possivel atualizar a preferencia.\n");
                        log.append("Verifique se voce esta dentro do PDV Pro ");
                        log.append("e se o app esta instalado.\n");
                        postResult(log.toString(), false);
                        return;
                    }

                    log.append("\nDisparando RESTART do servidor ");
                    log.append("(action com.esminis.server.RESTART) ...\n");
                    ServerConfigHelper.requestRestart(ManageUsersActivity.this);
                    log.append("Pedido de reinicio enviado.\n");

                    log.append("\nApos o reinicio (alguns segundos), o servidor passara ");
                    log.append("a aceitar conexoes de qualquer interface.\n");
                    log.append("\nIPs deste aparelho:\n");
                    appendLocalIpsAndReturnFirst(log);
                    ok = true;
                } catch (Throwable th) {
                    log.append("Falha: ").append(th.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(th.getMessage()));
                }
                postResult(log.toString(), ok);
            }
        });
    }

    private static void appendServerVar(StringBuilder log, ConnInfo c, String var) {
        try {
            String sql = "SHOW VARIABLES LIKE '" + escapeSql(var) + "'";
            MySqlMiniClient.QueryResult r = MySqlMiniClient.executeQuery(
                    c.host, c.port, c.admin, c.adminPass, sql, 5000);
            if (r.isError() || r.rows.isEmpty()) {
                log.append("  ").append(var).append(" = (?)\n");
                return;
            }
            List<String> row = r.rows.get(0);
            String value = row.size() > 1 ? row.get(1) : "(?)";
            log.append("  ").append(var).append(" = ").append(value);
            if ("bind_address".equals(var)) {
                if ("127.0.0.1".equals(value) || "localhost".equals(value)
                        || "::1".equals(value)) {
                    log.append("   <- AVISO: so aceita conexao local!");
                } else if (value.length() == 0 || "*".equals(value)
                        || "0.0.0.0".equals(value) || "::".equals(value)) {
                    log.append("   <- ok, aceita conexao externa");
                }
            } else if ("skip_networking".equals(var)
                    && ("ON".equalsIgnoreCase(value) || "1".equals(value))) {
                log.append("   <- AVISO: networking desligado!");
            }
            log.append("\n");
        } catch (Throwable th) {
            log.append("  ").append(var).append(" = (erro: ")
                    .append(th.getClass().getSimpleName()).append(")\n");
        }
    }

    /** Append IPs locais e devolve o primeiro IPv4 encontrado (ou null). */
    private static String appendLocalIpsAndReturnFirst(StringBuilder log) {
        String first = null;
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(nis)) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress a : Collections.list(addrs)) {
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    String addr = a.getHostAddress();
                    log.append("  ").append(ni.getName()).append(": ").append(addr).append("\n");
                    if (first == null && addr != null && addr.indexOf(':') < 0) {
                        first = addr;
                    }
                }
            }
        } catch (Throwable th) {
            log.append("  (nao foi possivel listar IPs: ")
                    .append(th.getClass().getSimpleName()).append(")\n");
        }
        return first;
    }

    // ==================== Helpers de UI ====================

    private void sectionTitle(LinearLayout parent, int dp, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(COLOR_HINT);
        tv.setAllCaps(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16 * dp;
        lp.bottomMargin = 4 * dp;
        parent.addView(tv, lp);
    }

    private void addBtn(LinearLayout parent, Button b, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8 * dp;
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
        dropBtn.setEnabled(false);
        changePassBtn.setEnabled(false);
        if (remoteBtn != null) remoteBtn.setEnabled(false);
        if (revokeRemoteBtn != null) revokeRemoteBtn.setEnabled(false);
        if (bindAddrBtn != null) bindAddrBtn.setEnabled(false);
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
                postResultDirect(text, success);
            }
        });
    }

    private void postResultDirect(String text, boolean success) {
        resultView.setTextColor(success ? COLOR_GREEN : COLOR_RED);
        resultView.setText(text);
        createBtn.setEnabled(true);
        listBtn.setEnabled(true);
        dropBtn.setEnabled(true);
        changePassBtn.setEnabled(true);
        if (remoteBtn != null) remoteBtn.setEnabled(true);
        if (revokeRemoteBtn != null) revokeRemoteBtn.setEnabled(true);
        if (bindAddrBtn != null) bindAddrBtn.setEnabled(true);
    }

    private static void runOnBg(Runnable r) {
        Thread t = new Thread(r, "phserver-user-op");
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

    private static boolean isSafeHost(String s) {
        if (s.length() == 0 || s.length() > 255) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '%' || c == '_' || c == '.' || c == ':'
                    || c == '-' || c == '/';
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
            else if (c == '\0') out.append("\\0");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == 0x1A) out.append("\\Z");
            else out.append(c);
        }
        return out.toString();
    }

    private static String redactPass(String sql, String pass) {
        if (pass == null || pass.length() == 0) return sql;
        return sql.replace("'" + escapeSql(pass) + "'", "'***'");
    }
}
