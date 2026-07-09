package com.pdv.app.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import com.pdv.app.utils.ErrorHandler;

/**
 * Activity que permite ao usuario navegar na rede SMB/CIFS,
 * explorando computadores e compartilhamentos ate encontrar
 * a impressora compartilhada desejada.
 *
 * Fluxo de navegacao:
 * 1. Escaneia a rede local buscando hosts via IP scan
 * 2. Ao selecionar um host, lista os compartilhamentos
 * 3. Ao selecionar um compartilhamento (impressora), retorna o resultado
 */
public class SmbBrowserActivity extends BaseActivity {
    private static final String TAG = "SmbBrowser";

    public static final String EXTRA_SMB_HOST = "smb_host";
    public static final String EXTRA_SMB_SHARE = "smb_share";
    public static final String EXTRA_SMB_DOMAIN = "smb_domain";
    public static final String EXTRA_SMB_USER = "smb_user";
    public static final String EXTRA_SMB_PASSWORD = "smb_password";

    // Niveis de navegacao
    private static final int LEVEL_HOSTS = 0;
    private static final int LEVEL_SHARES = 1;

    private RecyclerView recyclerView;
    private LinearLayout layoutEmpty;
    private ProgressBar progressBar;
    private TextView tvPath, tvInfo, tvEmpty;
    private EditText etDomain, etUser, etPassword, etManualIp;
    private Button btnConectar, btnNivelAnterior, btnCancelar;
    private ImageView btnVoltar, btnRefresh;

    private GenericAdapter<SmbItem> adapter;
    private int currentLevel = LEVEL_HOSTS;
    private String currentHost = "";
    private String localSubnet = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smb_browser);

        initViews();
        setupAdapter();
        setupListeners();
        loadPresetCredentials();

        // Detectar subnet e iniciar scan
        detectLocalSubnet();
        scanNetwork();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        progressBar = findViewById(R.id.progressBar);
        tvPath = findViewById(R.id.tvPath);
        tvInfo = findViewById(R.id.tvInfo);
        tvEmpty = findViewById(R.id.tvEmpty);
        etDomain = findViewById(R.id.etDomain);
        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        etManualIp = findViewById(R.id.etManualIp);
        btnConectar = findViewById(R.id.btnConectar);
        btnNivelAnterior = findViewById(R.id.btnNivelAnterior);
        btnCancelar = findViewById(R.id.btnCancelar);
        btnVoltar = findViewById(R.id.btnVoltar);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void setupAdapter() {
        adapter = new GenericAdapter<>(R.layout.item_smb_browser, (holder, item, position) -> {
            holder.setText(R.id.tvName, item.name);
            holder.setText(R.id.tvType, item.description);
            ImageView ivIcon = holder.find(R.id.ivIcon);
            if (item.type == SmbItem.TYPE_HOST) {
                ivIcon.setImageResource(R.drawable.ic_computer);
            } else if (item.type == SmbItem.TYPE_SHARE) {
                ivIcon.setImageResource(R.drawable.ic_folder_shared);
            } else if (item.type == SmbItem.TYPE_PRINTER) {
                ivIcon.setImageResource(R.drawable.ic_printer);
            }
        });

        adapter.setOnItemClickListener((item, position) -> {
            if (item.type == SmbItem.TYPE_HOST) {
                navigateToHost(item.address);
            } else if (item.type == SmbItem.TYPE_SHARE || item.type == SmbItem.TYPE_PRINTER) {
                selectShare(item);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnVoltar.setOnClickListener(v -> onBackPressed());

        btnRefresh.setOnClickListener(v -> {
            if (currentLevel == LEVEL_HOSTS) {
                scanNetwork();
            } else {
                navigateToHost(currentHost);
            }
        });

        btnConectar.setOnClickListener(v -> {
            String ip = etManualIp.getText().toString().trim();
            if (!ip.isEmpty()) {
                navigateToHost(ip);
            } else {
                showToast("Informe um IP para conectar");
            }
        });

        btnNivelAnterior.setOnClickListener(v -> goBack());

        btnCancelar.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    private void loadPresetCredentials() {
        // Carregar credenciais passadas via Intent (se existirem)
        Intent intent = getIntent();
        if (intent != null) {
            String domain = intent.getStringExtra(EXTRA_SMB_DOMAIN);
            String user = intent.getStringExtra(EXTRA_SMB_USER);
            String password = intent.getStringExtra(EXTRA_SMB_PASSWORD);
            if (domain != null && !domain.isEmpty()) etDomain.setText(domain);
            if (user != null && !user.isEmpty()) etUser.setText(user);
            if (password != null && !password.isEmpty()) etPassword.setText(password);
        }
    }

    private void detectLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    String hostAddr = addr.getHostAddress();
                    if (hostAddr != null && hostAddr.contains(".") && !hostAddr.startsWith("127.")) {
                        int lastDot = hostAddr.lastIndexOf('.');
                        localSubnet = hostAddr.substring(0, lastDot);
                        Log.i(TAG, "Subnet detectada: " + localSubnet);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao detectar subnet", e);
        }
        localSubnet = "192.168.1";
    }

    /**
     * Escaneia a rede local buscando hosts com porta 445 (SMB) aberta.
     */
    private void scanNetwork() {
        currentLevel = LEVEL_HOSTS;
        currentHost = "";
        updateUI();
        showProgress(true);
        updateInfo("Escaneando rede " + localSubnet + ".0/24 ... Aguarde...");
        adapter.setItems(new ArrayList<>());

        new Thread(() -> {
            List<SmbItem> hosts = new ArrayList<>();

            // Scan de IPs na subnet local
            for (int i = 1; i <= 254; i++) {
                final String ip = localSubnet + "." + i;
                try {
                    InetAddress address = InetAddress.getByName(ip);
                    if (address.isReachable(150)) {
                        // Tentar verificar se tem SMB (porta 445)
                        boolean hasSMB = checkSmbPort(ip);
                        String hostName = "";
                        try {
                            hostName = address.getCanonicalHostName();
                            if (hostName.equals(ip)) hostName = "";
                        } catch (Exception ignored) {}

                        String displayName = ip;
                        String desc = "Computador na rede";
                        if (!hostName.isEmpty() && !hostName.equals(ip)) {
                            displayName = hostName + " (" + ip + ")";
                        }
                        if (hasSMB) {
                            desc = "Compartilhamento SMB disponivel";
                        } else {
                            desc = "Host acessivel (SMB nao confirmado)";
                        }

                        SmbItem item = new SmbItem(displayName, desc, ip, SmbItem.TYPE_HOST);
                        item.hasSMB = hasSMB;
                        hosts.add(item);

                        // Atualizar lista em tempo real
                        final List<SmbItem> currentHosts = new ArrayList<>(hosts);
                        runOnUiThread(() -> {
                            if (currentLevel == LEVEL_HOSTS) {
                                // Ordenar: hosts com SMB primeiro
                                Collections.sort(currentHosts, (a, b) -> {
                                    if (a.hasSMB && !b.hasSMB) return -1;
                                    if (!a.hasSMB && b.hasSMB) return 1;
                                    return a.address.compareTo(b.address);
                                });
                                adapter.setItems(currentHosts);
                                showEmpty(false);
                                updateInfo("Encontrados " + currentHosts.size() + " dispositivo(s) - Escaneando...");
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }

            final List<SmbItem> finalHosts = new ArrayList<>(hosts);
            runOnUiThread(() -> {
                showProgress(false);
                if (currentLevel == LEVEL_HOSTS) {
                    Collections.sort(finalHosts, (a, b) -> {
                        if (a.hasSMB && !b.hasSMB) return -1;
                        if (!a.hasSMB && b.hasSMB) return 1;
                        return a.address.compareTo(b.address);
                    });
                    adapter.setItems(finalHosts);
                    if (finalHosts.isEmpty()) {
                        showEmpty(true);
                        updateInfo("Nenhum dispositivo encontrado na rede " + localSubnet + ".0/24");
                    } else {
                        showEmpty(false);
                        updateInfo(finalHosts.size() + " dispositivo(s) encontrado(s). Toque para ver compartilhamentos.");
                    }
                }
            });
        }).start();
    }

    private boolean checkSmbPort(String ip) {
        java.net.Socket socket = null;
        try {
            socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(ip, 445), 200);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Navega para um host especifico, listando seus compartilhamentos SMB.
     */
    private void navigateToHost(String host) {
        currentLevel = LEVEL_SHARES;
        currentHost = host;
        updateUI();
        showProgress(true);
        updateInfo("Conectando a " + host + " ...");
        adapter.setItems(new ArrayList<>());

        new Thread(() -> {
            List<SmbItem> shares = new ArrayList<>();
            try {
                // Configurar jCIFS
                jcifs.Config.setProperty("jcifs.smb.client.responseTimeout", "10000");
                jcifs.Config.setProperty("jcifs.smb.client.soTimeout", "15000");
                jcifs.Config.setProperty("jcifs.netbios.retryTimeout", "5000");
                jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");

                NtlmPasswordAuthentication auth = getAuth();

                String smbUrl = "smb://" + host + "/";
                SmbFile smbRoot = new SmbFile(smbUrl, auth);
                SmbFile[] files = smbRoot.listFiles();

                if (files != null) {
                    for (SmbFile f : files) {
                        String name = f.getName();
                        // Remover trailing /
                        if (name.endsWith("/")) {
                            name = name.substring(0, name.length() - 1);
                        }

                        // Ignorar compartilhamentos ocultos (terminam com $)
                        // mas mostrar se o usuario quiser
                        String desc;
                        int type;
                        int smbType = f.getType();

                        if (smbType == SmbFile.TYPE_PRINTER) {
                            desc = "Impressora compartilhada";
                            type = SmbItem.TYPE_PRINTER;
                        } else if (smbType == SmbFile.TYPE_SHARE) {
                            desc = "Compartilhamento";
                            type = SmbItem.TYPE_SHARE;
                        } else {
                            desc = "Recurso de rede";
                            type = SmbItem.TYPE_SHARE;
                        }

                        // Marcar compartilhamentos ocultos
                        if (name.endsWith("$")) {
                            desc += " (oculto)";
                        }

                        SmbItem item = new SmbItem(name, desc, host, type);
                        item.shareName = name;
                        shares.add(item);
                    }
                }
            } catch (jcifs.smb.SmbAuthException authEx) {
                Log.e(TAG, "Erro de autenticacao SMB", authEx);
                runOnUiThread(() -> {
                    showProgress(false);
                    showError("Falha na autenticacao de rede.\n\nVerifique o usuario e senha de acesso ao compartilhamento.\n"
                            + "Se o compartilhamento nao exige senha, deixe os campos de usuario e senha vazios.");
                });
                return;
            } catch (Exception e) {
                Log.e(TAG, "Erro ao listar compartilhamentos", e);

                // Tentar metodo alternativo: conectar direto e listar
                try {
                    jcifs.Config.setProperty("jcifs.smb.client.responseTimeout", "10000");
                    jcifs.Config.setProperty("jcifs.smb.client.soTimeout", "15000");
                    jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");

                    NtlmPasswordAuthentication auth = getAuth();
                    String smbUrl = "smb://" + host + "/";
                    SmbFile smbRoot = new SmbFile(smbUrl, auth);
                    String[] names = smbRoot.list();
                    if (names != null) {
                        for (String name : names) {
                            if (name.endsWith("/")) {
                                name = name.substring(0, name.length() - 1);
                            }
                            String desc = "Compartilhamento";
                            if (name.endsWith("$")) desc += " (oculto)";
                            SmbItem item = new SmbItem(name, desc, host, SmbItem.TYPE_SHARE);
                            item.shareName = name;
                            shares.add(item);
                        }
                    }
                } catch (Exception e2) {
                    Log.e(TAG, "Erro no metodo alternativo", e2);
                    runOnUiThread(() -> {
                        showProgress(false);
                        showEmpty(true);
                        updateInfo("Nao foi possivel conectar. Verifique se o computador esta ligado e acessivel.");
                        if (tvEmpty != null) {
                            tvEmpty.setText("Nao foi possivel listar compartilhamentos.\n\nVerifique:\n- Credenciais de acesso\n- Firewall (porta 445)\n- Compartilhamentos existentes");
                        }
                    });
                    return;
                }
            }

            final List<SmbItem> finalShares = new ArrayList<>(shares);
            runOnUiThread(() -> {
                showProgress(false);
                if (currentLevel == LEVEL_SHARES) {
                    // Ordenar: impressoras primeiro, depois compartilhamentos, ocultos por ultimo
                    Collections.sort(finalShares, (a, b) -> {
                        if (a.type == SmbItem.TYPE_PRINTER && b.type != SmbItem.TYPE_PRINTER) return -1;
                        if (a.type != SmbItem.TYPE_PRINTER && b.type == SmbItem.TYPE_PRINTER) return 1;
                        boolean aHidden = a.shareName != null && a.shareName.endsWith("$");
                        boolean bHidden = b.shareName != null && b.shareName.endsWith("$");
                        if (!aHidden && bHidden) return -1;
                        if (aHidden && !bHidden) return 1;
                        return a.name.compareToIgnoreCase(b.name);
                    });
                    adapter.setItems(finalShares);
                    if (finalShares.isEmpty()) {
                        showEmpty(true);
                        updateInfo("Nenhum compartilhamento encontrado em " + host);
                    } else {
                        showEmpty(false);
                        updateInfo(finalShares.size() + " compartilhamento(s) encontrado(s). Toque para selecionar.");
                    }
                }
            });
        }).start();
    }

    /**
     * Seleciona um compartilhamento como impressora e retorna o resultado.
     */
    private void selectShare(SmbItem item) {
        String shareDesc = item.type == SmbItem.TYPE_PRINTER ? "impressora" : "compartilhamento";
        showConfirm("Selecionar " + shareDesc,
                "Deseja usar este " + shareDesc + " como impressora?\n\n"
                        + "Host: " + item.address + "\n"
                        + "Compartilhamento: " + item.shareName + "\n\n"
                        + "Caminho: smb://" + item.address + "/" + item.shareName,
                () -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(EXTRA_SMB_HOST, item.address);
                    resultIntent.putExtra(EXTRA_SMB_SHARE, item.shareName);
                    resultIntent.putExtra(EXTRA_SMB_DOMAIN, etDomain.getText().toString().trim());
                    resultIntent.putExtra(EXTRA_SMB_USER, etUser.getText().toString().trim());
                    resultIntent.putExtra(EXTRA_SMB_PASSWORD, etPassword.getText().toString());
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                });
    }

    private NtlmPasswordAuthentication getAuth() {
        String domain = etDomain.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (user.isEmpty()) {
            return NtlmPasswordAuthentication.ANONYMOUS;
        }
        return new NtlmPasswordAuthentication(
                domain.isEmpty() ? "WORKGROUP" : domain,
                user,
                password
        );
    }

    private void goBack() {
        if (currentLevel == LEVEL_SHARES) {
            scanNetwork();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_SHARES) {
            scanNetwork();
        } else {
            setResult(Activity.RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (currentLevel == LEVEL_HOSTS) {
                tvPath.setText("smb://  (Rede Local)");
                btnNivelAnterior.setVisibility(View.GONE);
            } else {
                tvPath.setText("smb://" + currentHost + "/");
                btnNivelAnterior.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showProgress(boolean show) {
        runOnUiThread(() -> progressBar.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void showEmpty(boolean show) {
        runOnUiThread(() -> {
            layoutEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    private void updateInfo(String text) {
        runOnUiThread(() -> tvInfo.setText(text));
    }

    /**
     * Classe interna para representar um item na lista de navegacao SMB.
     */
    public static class SmbItem {
        public static final int TYPE_HOST = 0;
        public static final int TYPE_SHARE = 1;
        public static final int TYPE_PRINTER = 2;

        public String name;
        public String description;
        public String address;
        public int type;
        public String shareName;
        public boolean hasSMB;

        public SmbItem(String name, String description, String address, int type) {
            this.name = name;
            this.description = description;
            this.address = address;
            this.type = type;
            this.shareName = "";
            this.hasSMB = false;
        }
    }
}
