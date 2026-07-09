package com.pdv.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pdv.app.R;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.webserver.ChamadoWebServer;

import java.io.IOException;

/**
 * Activity do Painel da Cozinha.
 * 
 * Exibe o painel web da cozinha onde os cozinheiros acompanham
 * os pedidos em tempo real, com:
 * - Alerta sonoro (beep) para novos pedidos
 * - Vibração ao receber novo pedido
 * - Atualização automática a cada 3 segundos
 * - Controle de status: pendente -> preparando -> pronto -> entregue
 * - Visualização detalhada dos itens de cada pedido
 * - Interface otimizada para telas de cozinha
 * 
 * v6.9.4 - Painel Web da Cozinha com Beep Sonoro
 */
public class PainelCozinhaActivity extends BaseActivity {
    private static final String TAG = "PainelCozinha";
    private static final int SERVER_PORT = 8090;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ChamadoWebServer webServer;
    private ToneGenerator toneGenerator;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PAINEL_COZINHA_ACESSAR)) {
            return;
        }
        setContentView(R.layout.activity_painel_cozinha);

        webView = findViewById(R.id.webViewCozinha);
        progressBar = findViewById(R.id.progressBarCozinha);
        tvStatus = findViewById(R.id.tvStatusCozinha);
        handler = new Handler(Looper.getMainLooper());

        // Inicializar ToneGenerator para sinais sonoros (beep)
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
        } catch (Exception e) {
            Log.w(TAG, "Erro ao criar ToneGenerator: " + e.getMessage());
        }

        // Configurar volume de mídia como padrão
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Inicializar servidor web
        iniciarServidor();
    }

    private void iniciarServidor() {
        showLoading("Iniciando painel da cozinha...");
        new Thread(() -> {
            try {
                webServer = ChamadoWebServer.getInstance(this, SERVER_PORT);
                if (!webServer.isAlive()) {
                    webServer.start();
                    Log.d(TAG, "Servidor web iniciado na porta " + SERVER_PORT);
                }
                webServer.inicializarTabela();

                Thread.sleep(500);

                runOnUiThread(() -> {
                    hideLoading();
                    configurarWebView();
                    String url = "http://127.0.0.1:" + SERVER_PORT + "/cozinha";
                    webView.loadUrl(url);

                    String ip = webServer.getLocalIpAddress();
                    tvStatus.setText("Cozinha ativa • " + ip + ":" + SERVER_PORT + "/cozinha");
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao iniciar servidor: " + e.getMessage());
                runOnUiThread(() -> {
                    hideLoading();
                    showError("Erro ao iniciar painel da cozinha:\n" + e.getMessage());
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        webView.addJavascriptInterface(new CozinhaInterface(), "AndroidCozinha");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * Interface JavaScript para o Painel da Cozinha.
     * Permite que a página web acione beeps e vibrações nativas.
     */
    public class CozinhaInterface {

        /**
         * Toca um beep sonoro de alerta para novos pedidos.
         * Sequência: beep curto + pausa + beep longo
         */
        @JavascriptInterface
        public void tocarBeep() {
            try {
                if (toneGenerator != null) {
                    // Beep 1 - alerta curto
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
                    handler.postDelayed(() -> {
                        // Beep 2 - alerta médio
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                    }, 300);
                    handler.postDelayed(() -> {
                        // Beep 3 - confirmação
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 400);
                    }, 700);
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao tocar beep: " + e.getMessage());
            }
        }

        /**
         * Toca um beep duplo rápido para pedido marcado como pronto.
         */
        @JavascriptInterface
        public void tocarBeepPronto() {
            try {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150);
                    handler.postDelayed(() -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150);
                    }, 200);
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao tocar beep pronto: " + e.getMessage());
            }
        }

        /**
         * Toca um beep urgente (3 beeps rápidos) para pedidos pendentes há muito tempo.
         */
        @JavascriptInterface
        public void tocarBeepUrgente() {
            try {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao tocar beep urgente: " + e.getMessage());
            }
        }

        /**
         * Vibra o dispositivo para alertar novo pedido.
         */
        @JavascriptInterface
        public void vibrar() {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(new long[]{0, 300, 150, 300}, -1);
                }
            } catch (Exception ignored) {}
        }

        /**
         * Retorna a porta do servidor.
         */
        @JavascriptInterface
        public int getServerPort() {
            return SERVER_PORT;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }
}
