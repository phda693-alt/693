package com.pdv.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;

import com.pdv.app.R;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.webserver.ChamadoWebServer;

import java.io.IOException;
import java.util.Locale;

/**
 * Painel web de senhas.
 * Abre a pagina /senhas no servidor local do PDV e permite acessar tambem
 * por outro aparelho na mesma rede pelo IP exibido no topo.
 */
public class PainelSenhasWebActivity extends BaseActivity {
    private static final String TAG = "PainelSenhasWeb";
    private static final int SERVER_PORT = 8090;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private ChamadoWebServer webServer;
    private TextToSpeech tts;
    private boolean ttsPronto = false;
    private ToneGenerator toneGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // v8.0.23.0 - Corrigido: esta tela e o Painel Web de Senhas, nao o painel local.
        // Usa PAINEL_SENHAS_WEB que e a permissao especifica desta funcionalidade.
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PAINEL_SENHAS_WEB)) { return; }
        setContentView(R.layout.activity_painel_senhas_web);

        webView = findViewById(R.id.webViewSenhas);
        progressBar = findViewById(R.id.progressBarSenhas);
        tvStatus = findViewById(R.id.tvStatusSenhasWeb);

        inicializarAudioEVoz();
        iniciarServidor();
    }


    private void inicializarAudioEVoz() {
        try {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel iniciar ToneGenerator: " + e.getMessage());
        }

        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int result = tts.setLanguage(new Locale("pt", "BR"));
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.getDefault());
                    }
                    tts.setPitch(1.08f);
                    tts.setSpeechRate(0.82f);
                    ttsPronto = true;
                    runOnUiThread(() -> tvStatus.setText("Painel web ativo • som e voz prontos"));
                } else {
                    ttsPronto = false;
                    runOnUiThread(() -> showToast("Aviso: voz do Android nao disponivel neste aparelho"));
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel iniciar TextToSpeech: " + e.getMessage());
        }
    }

    private void tocarSomChamada() {
        try {
            if (toneGenerator != null) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260);
                webView.postDelayed(() -> {
                    try { if (toneGenerator != null) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 420); } catch (Exception ignored) {}
                }, 330);
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao tocar som da senha: " + e.getMessage());
        }
    }

    private void falarSenhaNativo(String senha, String cliente) {
        tocarSomChamada();
        if (!ttsPronto || tts == null) {
            showToast("Voz ainda nao esta pronta. Verifique o volume de midia/TTS do Android.");
            return;
        }
        try {
            String numero = senha == null ? "" : senha.trim();
            String nome = cliente == null ? "" : cliente.trim();
            String texto = "Atenção. Senha " + numero + ".";
            if (!nome.isEmpty() && !nome.equalsIgnoreCase("Cliente nao informado")) {
                texto += " " + nome + ".";
            }
            tts.stop();
            tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "senha_web_" + System.currentTimeMillis());
        } catch (Exception e) {
            Log.w(TAG, "Erro ao falar senha: " + e.getMessage());
        }
    }

    public class SenhaVoiceBridge {
        @JavascriptInterface
        public void falarSenha(String senha, String cliente) {
            runOnUiThread(() -> falarSenhaNativo(senha, cliente));
        }

        @JavascriptInterface
        public void tocarSom() {
            runOnUiThread(() -> tocarSomChamada());
        }

        @JavascriptInterface
        public boolean isVozPronta() {
            return ttsPronto;
        }
    }

    private void iniciarServidor() {
        showLoading("Iniciando painel web de senhas...");
        new Thread(() -> {
            try {
                webServer = ChamadoWebServer.getInstance(this, SERVER_PORT);
                if (!webServer.isAlive()) {
                    webServer.start();
                    Log.d(TAG, "Servidor web iniciado na porta " + SERVER_PORT);
                }

                Thread.sleep(400);

                runOnUiThread(() -> {
                    hideLoading();
                    configurarWebView();
                    String urlLocal = "http://127.0.0.1:" + SERVER_PORT + "/senhas";
                    webView.loadUrl(urlLocal);

                    String ip = webServer.getLocalIpAddress();
                    tvStatus.setText("Painel web ativo • http://" + ip + ":" + SERVER_PORT + "/senhas");
                });
            } catch (IOException e) {
                Log.e(TAG, "Erro ao iniciar servidor: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    hideLoading();
                    showError("Erro ao iniciar painel web de senhas:\n" + e.getMessage());
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        webView.addJavascriptInterface(new SenhaVoiceBridge(), "AndroidSenhaVoice");

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

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.destroy();
            } catch (Exception ignored) {}
        }
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) {}
        try { if (toneGenerator != null) toneGenerator.release(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
