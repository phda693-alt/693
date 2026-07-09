package com.pdv.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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
import java.util.Locale;

/**
 * Activity do Painel de Chamados dos Clientes.
 * 
 * Funcionalidades:
 * - WebView com servidor web embutido
 * - Integração com TTS (Text-to-Speech) nativo do Android
 * - Sinal sonoro via AudioManager
 * - Interface JavaScript para comunicação bidirecional
 * - Design futurístico com animações avançadas
 * 
 * v6.4.0 - Sistema de Chamados Inteligente
 */
public class PainelChamadosActivity extends BaseActivity implements TextToSpeech.OnInitListener {
    private static final String TAG = "PainelChamados";
    private static final int SERVER_PORT = 8090;
    
    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private ChamadoWebServer webServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PAINEL_CHAMADOS_ACESSAR)) {
            return;
        }
        setContentView(R.layout.activity_painel_chamados);

        webView = findViewById(R.id.webViewChamados);
        progressBar = findViewById(R.id.progressBarChamados);
        tvStatus = findViewById(R.id.tvStatusChamados);

        // Inicializar TTS
        tts = new TextToSpeech(this, this);

        // Inicializar servidor web
        iniciarServidor();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("pt", "BR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
                Log.w(TAG, "pt-BR nao disponivel, usando idioma padrao");
            }
            tts.setPitch(1.1f);
            tts.setSpeechRate(0.85f);
            ttsReady = true;
            Log.d(TAG, "TTS inicializado com sucesso");
        } else {
            Log.e(TAG, "Falha ao inicializar TTS: " + status);
        }
    }

    private void iniciarServidor() {
        showLoading("Iniciando servidor de chamados...");
        new Thread(() -> {
            try {
                webServer = ChamadoWebServer.getInstance(this, SERVER_PORT);
                if (!webServer.isAlive()) {
                    webServer.start();
                    Log.d(TAG, "Servidor web iniciado na porta " + SERVER_PORT);
                }
                webServer.inicializarTabela();
                
                // Aguardar servidor estar pronto
                Thread.sleep(500);
                
                runOnUiThread(() -> {
                    hideLoading();
                    configurarWebView();
                    String url = "http://127.0.0.1:" + SERVER_PORT + "/";
                    webView.loadUrl(url);
                    tvStatus.setText("Servidor ativo • Porta " + SERVER_PORT);
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao iniciar servidor: " + e.getMessage());
                runOnUiThread(() -> {
                    hideLoading();
                    showError("Erro ao iniciar servidor de chamados:\n" + e.getMessage());
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

        // Interface JavaScript para TTS nativo
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidTTS");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "Pagina carregada: " + url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "Erro WebView: " + description);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Log.w(TAG, "Nao foi possivel abrir URL externa: " + url);
                }
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
     * Interface JavaScript para comunicação com o Android nativo.
     * Permite que as páginas web chamem funções nativas como TTS.
     */
    public class WebAppInterface {
        @JavascriptInterface
        public void falar(String texto) {
            if (ttsReady && tts != null) {
                tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "chamado_" + System.currentTimeMillis());
            }
        }

        @JavascriptInterface
        public void falarChamado(int numero, String clienteNome) {
            if (ttsReady && tts != null) {
                String numStr = String.valueOf(numero);
                StringBuilder digitos = new StringBuilder();
                for (int i = 0; i < numStr.length(); i++) {
                    if (i > 0) digitos.append(", ");
                    digitos.append(numStr.charAt(i));
                }
                
                String texto1 = "Atenção! Chamado número " + digitos + ".";
                String texto2 = clienteNome + ", por favor, dirija-se ao atendimento.";
                String texto3 = "Chamado " + digitos + ". " + clienteNome + ".";
                
                tts.speak(texto1, TextToSpeech.QUEUE_ADD, null, "ch1_" + System.currentTimeMillis());
                tts.speak(texto2, TextToSpeech.QUEUE_ADD, null, "ch2_" + System.currentTimeMillis());
                tts.playSilence(2000, TextToSpeech.QUEUE_ADD, null);
                tts.speak(texto3, TextToSpeech.QUEUE_ADD, null, "ch3_" + System.currentTimeMillis());
            }
        }

        @JavascriptInterface
        public void pararFala() {
            if (tts != null) {
                tts.stop();
            }
        }

        @JavascriptInterface
        public boolean isTTSReady() {
            return ttsReady;
        }

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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        // Não parar o servidor aqui pois pode ser usado por outra activity
        super.onDestroy();
    }
}
