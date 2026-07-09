package com.pdv.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * Activity do Gerenciador de Chamadas.
 * 
 * Este é o painel de controle onde o operador gerencia as chamadas,
 * com suporte a:
 * - Chamada por voz (TTS nativo Android)
 * - Sinal sonoro (ToneGenerator + SoundPool)
 * - Controle de volume
 * - Auto-chamada automática
 * - Interface futurística via WebView
 * 
 * v6.4.0 - Sistema de Chamados Inteligente
 */
public class GerenciadorChamadosActivity extends BaseActivity implements TextToSpeech.OnInitListener {
    private static final String TAG = "GerenciadorChamados";
    private static final int SERVER_PORT = 8090;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private ChamadoWebServer webServer;
    private ToneGenerator toneGenerator;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR)) {
            return;
        }
        setContentView(R.layout.activity_gerenciador_chamados);

        webView = findViewById(R.id.webViewGerenciador);
        progressBar = findViewById(R.id.progressBarGerenciador);
        tvStatus = findViewById(R.id.tvStatusGerenciador);
        handler = new Handler(Looper.getMainLooper());

        // Inicializar TTS
        tts = new TextToSpeech(this, this);

        // Inicializar ToneGenerator para sinais sonoros
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

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("pt", "BR"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
                Log.w(TAG, "pt-BR nao disponivel para TTS");
            }
            tts.setPitch(1.1f);
            tts.setSpeechRate(0.85f);
            ttsReady = true;
            Log.d(TAG, "TTS inicializado com sucesso");
        } else {
            Log.e(TAG, "Falha ao inicializar TTS: " + status);
            runOnUiThread(() -> showToast("Aviso: Sintese de voz nao disponivel"));
        }
    }

    private void iniciarServidor() {
        showLoading("Iniciando gerenciador de chamadas...");
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
                    String url = "http://127.0.0.1:" + SERVER_PORT + "/gerenciador";
                    webView.loadUrl(url);
                    tvStatus.setText("Gerenciador ativo • Porta " + SERVER_PORT);
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao iniciar servidor: " + e.getMessage());
                runOnUiThread(() -> {
                    hideLoading();
                    showError("Erro ao iniciar gerenciador:\n" + e.getMessage());
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

        webView.addJavascriptInterface(new GerenciadorInterface(), "AndroidGerenciador");

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
     * Interface JavaScript avançada para o Gerenciador de Chamadas.
     */
    public class GerenciadorInterface {
        
        @JavascriptInterface
        public void falar(String texto) {
            if (ttsReady && tts != null) {
                tts.speak(texto, TextToSpeech.QUEUE_ADD, null, "ger_" + System.currentTimeMillis());
            }
        }

        @JavascriptInterface
        public void falarChamado(int numero, String clienteNome, String tipo) {
            if (ttsReady && tts != null) {
                String numStr = String.valueOf(numero);
                StringBuilder digitos = new StringBuilder();
                for (int i = 0; i < numStr.length(); i++) {
                    if (i > 0) digitos.append(", ");
                    digitos.append(numStr.charAt(i));
                }

                String texto1 = "Atenção!";
                String texto2 = "Chamado número " + digitos + ".";
                String texto3 = clienteNome + ", por favor, dirija-se ao atendimento.";
                String texto4 = "Chamado " + digitos + ". " + clienteNome + ".";

                tts.speak(texto1, TextToSpeech.QUEUE_ADD, null, "g1");
                tts.playSilence(300, TextToSpeech.QUEUE_ADD, null);
                tts.speak(texto2, TextToSpeech.QUEUE_ADD, null, "g2");
                tts.speak(texto3, TextToSpeech.QUEUE_ADD, null, "g3");
                tts.playSilence(2000, TextToSpeech.QUEUE_ADD, null);
                tts.speak(texto4, TextToSpeech.QUEUE_ADD, null, "g4");
            }
        }

        @JavascriptInterface
        public void tocarSinal() {
            try {
                if (toneGenerator != null) {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300);
                    handler.postDelayed(() -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                    }, 400);
                    handler.postDelayed(() -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 500);
                    }, 800);
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao tocar sinal: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void pararFala() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean isTTSReady() {
            return ttsReady;
        }

        @JavascriptInterface
        public void setVelocidadeFala(float rate) {
            if (tts != null) tts.setSpeechRate(Math.max(0.5f, Math.min(2.0f, rate)));
        }

        @JavascriptInterface
        public void setTomFala(float pitch) {
            if (tts != null) tts.setPitch(Math.max(0.5f, Math.min(2.0f, pitch)));
        }

        @JavascriptInterface
        public int getServerPort() {
            return SERVER_PORT;
        }

        @JavascriptInterface
        public void vibrar() {
            try {
                android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(new long[]{0, 200, 100, 200}, -1);
                }
            } catch (Exception ignored) {}
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
        if (toneGenerator != null) {
            toneGenerator.release();
        }
        super.onDestroy();
    }
}
