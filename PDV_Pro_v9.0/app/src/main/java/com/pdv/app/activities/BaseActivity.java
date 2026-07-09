package com.pdv.app.activities;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pdv.app.R;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.UserActionLogger;

public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    protected ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void showLoading(String message) {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                if (progressDialog == null) {
                    progressDialog = new ProgressDialog(this);
                    progressDialog.setCancelable(false);
                }
                progressDialog.setMessage(message);
                if (!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao exibir loading: " + e.getMessage());
            }
        });
    }

    protected void hideLoading() {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                Log.w(TAG, "Erro ao esconder loading: " + e.getMessage());
            }
        });
    }

    protected void showToast(String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao exibir toast: " + e.getMessage());
            }
        });
    }

    /**
     * Exibe uma mensagem de erro amigavel ao usuario.
     * Utiliza o ErrorHandler para traduzir mensagens tecnicas.
     */
    protected void showError(String message) {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                String displayMessage = translateConnectionError(message);
                new AlertDialog.Builder(this)
                        .setTitle("\u26A0  Atencao")
                        .setMessage(displayMessage)
                        .setPositiveButton("Entendi", null)
                        .setCancelable(true)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao exibir dialog de erro: " + e.getMessage());
                try {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Exibe uma mensagem de erro amigavel baseada em uma excecao e contexto.
     * Usa o ErrorHandler centralizado para traduzir a excecao.
     *
     * @param e        A excecao capturada
     * @param contexto O contexto da operacao (use ErrorHandler.CTX_*)
     */
    protected void showErrorFromException(Exception e, String contexto) {
        String mensagem = ErrorHandler.getMensagemAmigavel(e, contexto);
        showError(mensagem);
    }

    /**
     * Traduz mensagens de erro tecnicas de conexao MySQL para mensagens
     * amigaveis em portugues para o usuario final.
     */
    protected String translateConnectionError(String message) {
        if (message == null) return "Ocorreu um erro inesperado.\n\nTente novamente. Se o problema persistir, reinicie o aplicativo.";
        String lower = message.toLowerCase();
        if (lower.contains("communications link failure")) {
            return "A conexao com o servidor foi perdida.\n\nVerifique se sua internet esta funcionando e tente novamente.";
        } else if (lower.contains("connection reset") || lower.contains("broken pipe")) {
            return "A conexao com o servidor foi interrompida.\n\nTente novamente em alguns instantes.";
        } else if (lower.contains("connection refused")) {
            return "Nao foi possivel conectar ao servidor.\n\nO servidor pode estar desligado ou as configuracoes do banco de dados estao incorretas.";
        } else if (lower.contains("timeout") || lower.contains("timed out")) {
            return "O servidor demorou muito para responder.\n\nSua conexao pode estar lenta. Tente novamente em alguns instantes.";
        } else if (lower.contains("unknown host") || lower.contains("no address associated")) {
            return "Servidor nao encontrado.\n\nVerifique o endereco do servidor nas configuracoes do banco de dados.";
        } else if (lower.contains("access denied")) {
            return "Acesso negado ao banco de dados.\n\nVerifique o usuario e a senha nas configuracoes.";
        } else if (lower.contains("no operations allowed after connection closed")
                || lower.contains("connection is closed")
                || lower.contains("already been closed")) {
            return "A conexao com o servidor expirou.\n\nTente novamente. O sistema ira reconectar automaticamente.";
        } else if (lower.contains("duplicate entry")) {
            return "Este registro ja existe no sistema.\n\nVerifique se os dados informados nao estao duplicados.";
        } else if (lower.contains("network is unreachable") || lower.contains("no route to host")) {
            return "Sem acesso a rede.\n\nVerifique se o Wi-Fi ou dados moveis estao ativados.";
        } else if (lower.contains("out of memory")) {
            return "O aplicativo ficou sem memoria.\n\nFeche outros aplicativos e tente novamente.";
        } else if (lower.contains("numberformatexception") || lower.contains("for input string")) {
            return "Valor numerico invalido.\n\nVerifique se os campos numericos estao preenchidos corretamente.";
        }
        return message;
    }

    protected void showSuccess(String message) {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle("\u2705  Sucesso")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao exibir dialog de sucesso: " + e.getMessage());
                try {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        });
    }

    protected void showConfirm(String title, String message, Runnable onConfirm) {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("Sim", (d, w) -> {
                            try {
                                onConfirm.run();
                            } catch (Exception e) {
                                Log.e(TAG, "Erro ao executar confirmacao", e);
                                showError("Ocorreu um erro ao processar sua solicitacao.\n\nTente novamente.");
                            }
                        })
                        .setNegativeButton("Nao", null)
                        .show();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao exibir dialog de confirmacao: " + e.getMessage());
            }
        });
    }

    /**
     * Exibe um dialog informativo (sem ser erro).
     */
    protected void showInfo(String title, String message) {
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) return;
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao exibir dialog info: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        UserActionLogger.log(this, "ABRIR_TELA", getClass().getSimpleName(),
                "Tela aberta pelo usuario");
    }

    @Override
    protected void onStop() {
        UserActionLogger.log(this, "FECHAR_TELA", getClass().getSimpleName(),
                "Tela deixou o primeiro plano");
        com.pdv.app.sync.MirrorSyncManager.scheduleSync(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
            progressDialog = null;
        } catch (Exception e) {
            Log.w(TAG, "Erro ao destruir activity: " + e.getMessage());
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
