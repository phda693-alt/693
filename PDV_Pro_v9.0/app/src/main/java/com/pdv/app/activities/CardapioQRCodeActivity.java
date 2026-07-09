package com.pdv.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.webserver.ChamadoWebServer;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Activity para Cardápio Digital via QR Code.
 * 
 * Funcionalidades:
 * - Gera QR Code para cada mesa do estabelecimento
 * - O QR Code contém a URL do cardápio digital com o número da mesa
 * - Clientes escaneiam o QR Code e fazem pedidos pelo celular
 * - Os pedidos são recebidos em tempo real no sistema PDV Pro
 * - Integração com sistema de mesas (itens vão direto para a mesa)
 * - Painel de gerenciamento de pedidos web recebidos
 * 
 * v6.7.0 - Sistema de Pedidos via QR Code
 * v6.8.0 - Correção de crash: layoutQRCode é ScrollView, não LinearLayout
 */
public class CardapioQRCodeActivity extends BaseActivity {
    private static final String TAG = "CardapioQRCode";
    private static final int SERVER_PORT = 8090;

    private Spinner spMesa;
    private ImageView imgQRCode;
    private TextView tvUrlCardapio;
    private TextView tvStatus;
    private WebView webViewPedidos;
    private View layoutQRCode;          // CORRIGIDO: era LinearLayout, mas no XML é ScrollView
    private LinearLayout layoutPedidos;
    private Button btnGerarQR;
    private Button btnVerPedidos;
    private Button btnVoltarQR;
    private ChamadoWebServer webServer;
    private List<int[]> mesasList = new ArrayList<>(); // [id, numero]
    private boolean servidorIniciado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_cardapio_qrcode);
            if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CARDAPIO_QRCODE_ACESSAR)) return;

            spMesa = findViewById(R.id.spMesaQR);
            imgQRCode = findViewById(R.id.imgQRCode);
            tvUrlCardapio = findViewById(R.id.tvUrlCardapio);
            tvStatus = findViewById(R.id.tvStatusQR);
            webViewPedidos = findViewById(R.id.webViewPedidos);
            layoutQRCode = findViewById(R.id.layoutQRCode);       // Agora como View genérico
            layoutPedidos = findViewById(R.id.layoutPedidos);
            btnGerarQR = findViewById(R.id.btnGerarQR);
            btnVerPedidos = findViewById(R.id.btnVerPedidos);
            btnVoltarQR = findViewById(R.id.btnVoltarQR);

            // Botão voltar
            View btnVoltar = findViewById(R.id.btnVoltarCardapio);
            if (btnVoltar != null) {
                btnVoltar.setOnClickListener(v -> finish());
            }

            if (btnGerarQR != null) {
                btnGerarQR.setOnClickListener(v -> gerarQRCode());
            }
            if (btnVerPedidos != null) {
                btnVerPedidos.setOnClickListener(v -> mostrarPedidos());
            }
            if (btnVoltarQR != null) {
                btnVoltarQR.setOnClickListener(v -> mostrarQRCode());
            }

            iniciarServidor();
            carregarMesas();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar CardapioQRCodeActivity: " + e.getMessage(), e);
            showError("Erro ao abrir Cardápio QR Code:\n" + e.getMessage());
        }
    }

    private void iniciarServidor() {
        new Thread(() -> {
            try {
                webServer = ChamadoWebServer.getInstance(this, SERVER_PORT);
                if (!webServer.isAlive()) {
                    webServer.start();
                    Log.d(TAG, "Servidor web iniciado na porta " + SERVER_PORT);
                }
                webServer.inicializarTabela();
                servidorIniciado = true;

                String ip = getLocalIpAddress();
                runOnUiThread(() -> {
                    try {
                        if (tvStatus != null && !isFinishing() && !isDestroyed()) {
                            tvStatus.setText("Servidor ativo • IP: " + ip + " • Porta " + SERVER_PORT);
                            tvStatus.setVisibility(View.VISIBLE);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao atualizar status: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao iniciar servidor: " + e.getMessage());
                runOnUiThread(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) {
                            showError("Erro ao iniciar servidor web:\n" + e.getMessage());
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Erro ao exibir mensagem de erro: " + ex.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro inesperado ao iniciar servidor: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) {
                            showError("Erro inesperado ao iniciar servidor:\n" + e.getMessage());
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Erro ao exibir mensagem: " + ex.getMessage());
                    }
                });
            }
        }).start();
    }

    private void carregarMesas() {
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                if (conn == null || conn.isClosed()) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            showError("Não foi possível conectar ao banco de dados.\nVerifique as configurações de conexão.");
                        }
                    });
                    return;
                }

                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, numero FROM mesas WHERE ativa = 1 ORDER BY numero ASC");

                mesasList.clear();
                List<String> nomes = new ArrayList<>();
                nomes.add("-- Selecione a Mesa --");

                while (rs.next()) {
                    int id = rs.getInt("id");
                    int numero = rs.getInt("numero");
                    mesasList.add(new int[]{id, numero});
                    nomes.add("Mesa " + numero);
                }
                rs.close();
                stmt.close();

                runOnUiThread(() -> {
                    try {
                        if (spMesa != null && !isFinishing() && !isDestroyed()) {
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                                    android.R.layout.simple_spinner_item, nomes);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spMesa.setAdapter(adapter);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao configurar spinner de mesas: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar mesas: " + e.getMessage());
                runOnUiThread(() -> {
                    try {
                        if (!isFinishing() && !isDestroyed()) {
                            showError("Erro ao carregar mesas: " + e.getMessage());
                        }
                    } catch (Exception ex) {
                        Log.w(TAG, "Erro ao exibir mensagem: " + ex.getMessage());
                    }
                });
            }
        }).start();
    }

    private void gerarQRCode() {
        try {
            if (spMesa == null) {
                showError("Componente de seleção de mesa não encontrado");
                return;
            }

            int selectedIdx = spMesa.getSelectedItemPosition();
            if (selectedIdx <= 0 || selectedIdx > mesasList.size()) {
                showError("Selecione uma mesa para gerar o QR Code");
                return;
            }

            int mesaNumero = mesasList.get(selectedIdx - 1)[1];
            String ip = getLocalIpAddress();
            String url = "http://" + ip + ":" + SERVER_PORT + "/cardapio?mesa=" + mesaNumero;

            try {
                Bitmap qrBitmap = generateQRCode(url, 600);
                if (imgQRCode != null) {
                    imgQRCode.setImageBitmap(qrBitmap);
                    imgQRCode.setVisibility(View.VISIBLE);
                }
                if (tvUrlCardapio != null) {
                    tvUrlCardapio.setText("URL: " + url + "\n\nMesa " + mesaNumero + " • Escaneie o QR Code acima");
                    tvUrlCardapio.setVisibility(View.VISIBLE);
                }

                showSuccess("QR Code gerado para Mesa " + mesaNumero + "!\nClientes podem escanear para fazer pedidos.");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao gerar QR Code: " + e.getMessage());
                showError("Erro ao gerar QR Code: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro inesperado ao gerar QR Code: " + e.getMessage(), e);
            showError("Erro inesperado: " + e.getMessage());
        }
    }

    private Bitmap generateQRCode(String text, int size) throws Exception {
        MultiFormatWriter writer = new MultiFormatWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    private void mostrarPedidos() {
        try {
            if (layoutQRCode != null) {
                layoutQRCode.setVisibility(View.GONE);
            }
            if (layoutPedidos != null) {
                layoutPedidos.setVisibility(View.VISIBLE);
            }
            if (btnVerPedidos != null) {
                btnVerPedidos.setVisibility(View.GONE);
            }
            if (btnVoltarQR != null) {
                btnVoltarQR.setVisibility(View.VISIBLE);
            }
            configurarWebViewPedidos();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao mostrar pedidos: " + e.getMessage(), e);
            showError("Erro ao abrir painel de pedidos: " + e.getMessage());
        }
    }

    private void mostrarQRCode() {
        try {
            if (layoutQRCode != null) {
                layoutQRCode.setVisibility(View.VISIBLE);
            }
            if (layoutPedidos != null) {
                layoutPedidos.setVisibility(View.GONE);
            }
            if (btnVerPedidos != null) {
                btnVerPedidos.setVisibility(View.VISIBLE);
            }
            if (btnVoltarQR != null) {
                btnVoltarQR.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao mostrar QR Code: " + e.getMessage(), e);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebViewPedidos() {
        try {
            if (webViewPedidos == null) {
                Log.e(TAG, "WebView de pedidos não encontrada");
                return;
            }

            WebSettings settings = webViewPedidos.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

            webViewPedidos.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Log.e(TAG, "WebView error: " + description + " URL: " + failingUrl);
                }
            });
            webViewPedidos.setWebChromeClient(new WebChromeClient());

            // Carregar página de gerenciamento de pedidos web
            String url = "http://127.0.0.1:" + SERVER_PORT + "/pedidos-web";
            webViewPedidos.loadUrl(url);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao configurar WebView: " + e.getMessage(), e);
            showError("Erro ao configurar painel de pedidos: " + e.getMessage());
        }
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "127.0.0.1";
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    @Override
    protected void onDestroy() {
        try {
            // Limpar WebView para evitar vazamento de memória
            if (webViewPedidos != null) {
                webViewPedidos.stopLoading();
                webViewPedidos.loadUrl("about:blank");
                webViewPedidos.clearHistory();
                webViewPedidos.clearCache(false);
                webViewPedidos.destroy();
                webViewPedidos = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao destruir WebView: " + e.getMessage());
        }
        super.onDestroy();
    }
}
