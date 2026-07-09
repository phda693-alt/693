package com.pdv.app.whatsbot;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.activities.BaseActivity;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity principal do Bot WhatsApp.
 * Permite configurar, ativar/desativar e monitorar o bot.
 */
public class WhatsAppBotActivity extends BaseActivity {
    private static final String TAG = "WhatsAppBotActivity";

    private WhatsAppBotConfig config;
    private WhatsAppBotLogger logger;

    // Views
    private Switch switchBotAtivo;
    private TextView tvStatus, tvEstatisticas, tvSessoesAtivas;
    private EditText etNomeEmpresa, etHorarioInicio, etHorarioFim;
    private EditText etMsgBoasVindas, etMsgAusencia, etMsgEncerramento;
    private EditText etNumeroAdmin, etDelayResposta, etMaxProdutos;
    private Switch switchAtenderFora, switchCatalogo, switchPreco;
    private Switch switchPedidos, switchCupomAuto, switchNotificarVenda;
    private Switch switchGrupos, switchApenasContatos, switchLog;
    private Switch switchImpressaoAuto;
    private Button btnPermissao, btnTestarBot, btnVerLogs, btnLimparLogs;
    private Button btnSalvar, btnResetEstatisticas;
    private LinearLayout layoutConfig, layoutLogs;
    private RecyclerView rvLogs;
    private TextView tvLogs;

    // v5.0.0 - Views de IA
    private Switch switchIA, switchIAIntencao, switchIARespostas, switchIASugestoes, switchIAFrustracao;
    private EditText etIAApiKey, etIAApiUrl, etIAModel;
    private TextView tvIAStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whatsbot);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.WHATSBOT_ACESSAR)) {
            return;
        }

        config = new WhatsAppBotConfig(this);
        logger = new WhatsAppBotLogger(this);

        initViews();
        carregarConfiguracoes();
        atualizarStatus();

        // Carregar nome da empresa do banco se nao configurado
        carregarNomeEmpresaDoBanco();
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatus();
    }

    private void initViews() {
        // Status
        switchBotAtivo = findViewById(R.id.switchBotAtivo);
        tvStatus = findViewById(R.id.tvBotStatus);
        tvEstatisticas = findViewById(R.id.tvEstatisticas);
        tvSessoesAtivas = findViewById(R.id.tvSessoesAtivas);

        // Configuracoes gerais
        etNomeEmpresa = findViewById(R.id.etNomeEmpresa);
        etHorarioInicio = findViewById(R.id.etHorarioInicio);
        etHorarioFim = findViewById(R.id.etHorarioFim);
        etNumeroAdmin = findViewById(R.id.etNumeroAdmin);
        etDelayResposta = findViewById(R.id.etDelayResposta);
        etMaxProdutos = findViewById(R.id.etMaxProdutos);

        // Mensagens
        etMsgBoasVindas = findViewById(R.id.etMsgBoasVindas);
        etMsgAusencia = findViewById(R.id.etMsgAusencia);
        etMsgEncerramento = findViewById(R.id.etMsgEncerramento);

        // Switches de funcionalidades
        switchAtenderFora = findViewById(R.id.switchAtenderFora);
        switchCatalogo = findViewById(R.id.switchCatalogo);
        switchPreco = findViewById(R.id.switchPreco);
        switchPedidos = findViewById(R.id.switchPedidos);
        switchCupomAuto = findViewById(R.id.switchCupomAuto);
        switchNotificarVenda = findViewById(R.id.switchNotificarVenda);
        switchGrupos = findViewById(R.id.switchGrupos);
        switchApenasContatos = findViewById(R.id.switchApenasContatos);
        switchLog = findViewById(R.id.switchLog);
        switchImpressaoAuto = findViewById(R.id.switchImpressaoAuto);

        // Botoes
        btnPermissao = findViewById(R.id.btnPermissao);
        btnTestarBot = findViewById(R.id.btnTestarBot);
        btnVerLogs = findViewById(R.id.btnVerLogs);
        btnLimparLogs = findViewById(R.id.btnLimparLogs);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnResetEstatisticas = findViewById(R.id.btnResetEstatisticas);

        // Layouts
        layoutConfig = findViewById(R.id.layoutConfig);
        layoutLogs = findViewById(R.id.layoutLogs);
        tvLogs = findViewById(R.id.tvLogs);

        // v5.0.0 - IA Views
        switchIA = findViewById(R.id.switchIA);
        switchIAIntencao = findViewById(R.id.switchIAIntencao);
        switchIARespostas = findViewById(R.id.switchIARespostas);
        switchIASugestoes = findViewById(R.id.switchIASugestoes);
        switchIAFrustracao = findViewById(R.id.switchIAFrustracao);
        etIAApiKey = findViewById(R.id.etIAApiKey);
        etIAApiUrl = findViewById(R.id.etIAApiUrl);
        etIAModel = findViewById(R.id.etIAModel);
        tvIAStatus = findViewById(R.id.tvIAStatus);

        // Listener para atualizar status da IA
        if (switchIA != null) {
            switchIA.setOnCheckedChangeListener((btn, checked) -> {
                atualizarStatusIA();
            });
        }

        // Listeners
        switchBotAtivo.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !isNotificationServiceEnabled()) {
                switchBotAtivo.setChecked(false);
                mostrarDialogPermissao();
                return;
            }
            config.setBotAtivo(checked);
            atualizarStatus();
        });

        btnPermissao.setOnClickListener(v -> abrirConfigNotificacao());

        btnTestarBot.setOnClickListener(v -> testarBot());

        btnVerLogs.setOnClickListener(v -> {
            if (layoutLogs.getVisibility() == View.VISIBLE) {
                layoutLogs.setVisibility(View.GONE);
                btnVerLogs.setText("Ver Logs");
            } else {
                layoutLogs.setVisibility(View.VISIBLE);
                btnVerLogs.setText("Ocultar Logs");
                carregarLogs();
            }
        });

        btnLimparLogs.setOnClickListener(v -> {
            showConfirm("Limpar Logs", "Deseja limpar todos os logs do bot?", () -> {
                logger.limparTodosLogs();
                carregarLogs();
                showToast("Logs limpos!");
            });
        });

        btnSalvar.setOnClickListener(v -> salvarConfiguracoes());

        btnResetEstatisticas.setOnClickListener(v -> {
            showConfirm("Resetar", "Deseja zerar todas as estatisticas?", () -> {
                config.resetEstatisticas();
                atualizarStatus();
                showToast("Estatisticas zeradas!");
            });
        });
    }

    private void carregarConfiguracoes() {
        etNomeEmpresa.setText(config.getNomeEmpresa());
        etHorarioInicio.setText(config.getHorarioInicio());
        etHorarioFim.setText(config.getHorarioFim());
        etNumeroAdmin.setText(config.getNumeroAdmin());
        etDelayResposta.setText(String.valueOf(config.getDelayResposta()));
        etMaxProdutos.setText(String.valueOf(config.getMaxProdutosCatalogo()));

        etMsgBoasVindas.setText(config.getMsgBoasVindas());
        etMsgAusencia.setText(config.getMsgAusencia());
        etMsgEncerramento.setText(config.getMsgEncerramento());

        switchBotAtivo.setChecked(config.isBotAtivo());
        switchAtenderFora.setChecked(config.isAtenderForaHorario());
        switchCatalogo.setChecked(config.isEnviarCatalogo());
        switchPreco.setChecked(config.isConsultarPreco());
        switchPedidos.setChecked(config.isAceitarPedidos());
        switchCupomAuto.setChecked(config.isEnviarCupomAuto());
        switchNotificarVenda.setChecked(config.isNotificarVenda());
        switchGrupos.setChecked(config.isResponderGrupos());
        switchApenasContatos.setChecked(config.isResponderApenasContatos());
        switchLog.setChecked(config.isLogAtivo());
        switchImpressaoAuto.setChecked(config.isImpressaoAutoWhatsApp());

        // v5.0.0 - Carregar configuracoes de IA
        if (switchIA != null) {
            switchIA.setChecked(config.isIAEnabled());
            etIAApiKey.setText(config.getIAApiKey());
            etIAApiUrl.setText(config.getIAApiUrl());
            etIAModel.setText(config.getIAModel());
            switchIAIntencao.setChecked(config.isIAInterpretarIntencao());
            switchIARespostas.setChecked(config.isIARespostasInteligentes());
            switchIASugestoes.setChecked(config.isIASugestoesProdutos());
            switchIAFrustracao.setChecked(config.isIADetectarFrustracao());
            atualizarStatusIA();
        }
    }

    private void salvarConfiguracoes() {
        try {
            String nome = etNomeEmpresa.getText().toString().trim();
            if (nome.isEmpty()) {
                showError("Informe o nome da empresa.");
                return;
            }

            config.setNomeEmpresa(nome);
            config.setHorarioInicio(etHorarioInicio.getText().toString().trim());
            config.setHorarioFim(etHorarioFim.getText().toString().trim());
            config.setNumeroAdmin(etNumeroAdmin.getText().toString().trim());

            try {
                config.setDelayResposta(Integer.parseInt(etDelayResposta.getText().toString().trim()));
            } catch (NumberFormatException e) {
                config.setDelayResposta(2);
            }

            try {
                config.setMaxProdutosCatalogo(Integer.parseInt(etMaxProdutos.getText().toString().trim()));
            } catch (NumberFormatException e) {
                config.setMaxProdutosCatalogo(20);
            }

            config.setMsgBoasVindas(etMsgBoasVindas.getText().toString());
            config.setMsgAusencia(etMsgAusencia.getText().toString());
            config.setMsgEncerramento(etMsgEncerramento.getText().toString());

            config.setAtenderForaHorario(switchAtenderFora.isChecked());
            config.setEnviarCatalogo(switchCatalogo.isChecked());
            config.setConsultarPreco(switchPreco.isChecked());
            config.setAceitarPedidos(switchPedidos.isChecked());
            config.setEnviarCupomAuto(switchCupomAuto.isChecked());
            config.setNotificarVenda(switchNotificarVenda.isChecked());
            config.setResponderGrupos(switchGrupos.isChecked());
            config.setResponderApenasContatos(switchApenasContatos.isChecked());
            config.setLogAtivo(switchLog.isChecked());
            config.setImpressaoAutoWhatsApp(switchImpressaoAuto.isChecked());

            // v5.0.0 - Salvar configuracoes de IA
            if (switchIA != null) {
                config.setIAEnabled(switchIA.isChecked());
                config.setIAApiKey(etIAApiKey.getText().toString().trim());
                String apiUrl = etIAApiUrl.getText().toString().trim();
                if (!apiUrl.isEmpty()) {
                    config.setIAApiUrl(apiUrl);
                }
                String model = etIAModel.getText().toString().trim();
                if (!model.isEmpty()) {
                    config.setIAModel(model);
                }
                config.setIAInterpretarIntencao(switchIAIntencao.isChecked());
                config.setIARespostasInteligentes(switchIARespostas.isChecked());
                config.setIASugestoesProdutos(switchIASugestoes.isChecked());
                config.setIADetectarFrustracao(switchIAFrustracao.isChecked());
            }

            showSuccess("Configuracoes salvas com sucesso!");
            logger.logSistema("Configuracoes atualizadas pelo usuario");

        } catch (Exception e) {
            showError("Erro ao salvar: " + e.getMessage());
        }
    }

    private void atualizarStatus() {
        boolean permissao = isNotificationServiceEnabled();
        boolean ativo = config.isBotAtivo();

        if (ativo && permissao) {
            tvStatus.setText("🟢 Bot ATIVO e funcionando");
            tvStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else if (ativo && !permissao) {
            tvStatus.setText("🟡 Bot ativo mas SEM PERMISSAO");
            tvStatus.setTextColor(getResources().getColor(R.color.colorWarning));
        } else {
            tvStatus.setText("🔴 Bot DESATIVADO");
            tvStatus.setTextColor(getResources().getColor(R.color.colorDanger));
        }

        btnPermissao.setVisibility(permissao ? View.GONE : View.VISIBLE);

        // Estatisticas
        tvEstatisticas.setText(logger.getEstatisticasFormatadas());

        // Sessoes ativas
        if (WhatsAppBotService.isServiceRunning()) {
            WhatsAppBotService service = WhatsAppBotService.getInstance();
            if (service != null && service.getEngine() != null) {
                tvSessoesAtivas.setText("Sessoes ativas: " + service.getEngine().getSessoesAtivas());
            } else {
                tvSessoesAtivas.setText("Sessoes ativas: 0");
            }
        } else {
            tvSessoesAtivas.setText("Servico nao iniciado");
        }
    }

    private void testarBot() {
        WhatsAppBotEngine testEngine = new WhatsAppBotEngine(this);

        // Simular conversa
        new AlertDialog.Builder(this)
                .setTitle("Testar Bot")
                .setMessage("Envie uma mensagem de teste para o bot:")
                .setView(criarInputDialog())
                .setPositiveButton("Enviar", (d, w) -> {
                    EditText input = ((AlertDialog) d).findViewById(R.id.etDialogInput);
                    if (input != null) {
                        String msg = input.getText().toString().trim();
                        if (!msg.isEmpty()) {
                            showLoading("Processando...");
                            new Thread(() -> {
                                String resposta = testEngine.processarMensagem("Teste", msg);
                                hideLoading();
                                runOnUiThread(() -> {
                                    new AlertDialog.Builder(WhatsAppBotActivity.this)
                                            .setTitle("Resposta do Bot")
                                            .setMessage(resposta)
                                            .setPositiveButton("OK", null)
                                            .show();
                                });
                            }).start();
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private EditText criarInputDialog() {
        EditText input = new EditText(this);
        input.setId(R.id.etDialogInput);
        input.setHint("Digite uma mensagem...");
        input.setTextColor(getResources().getColor(R.color.text_primary));
        input.setHintTextColor(getResources().getColor(R.color.text_hint));
        input.setPadding(40, 20, 40, 20);
        return input;
    }

    private void carregarLogs() {
        List<String> logs = logger.getLogsHoje();
        if (logs.isEmpty()) {
            tvLogs.setText("Nenhum log registrado hoje.");
        } else {
            StringBuilder sb = new StringBuilder();
            // Mostrar os ultimos 50 logs
            int start = Math.max(0, logs.size() - 50);
            for (int i = start; i < logs.size(); i++) {
                sb.append(logs.get(i)).append("\n");
            }
            tvLogs.setText(sb.toString());
        }
    }

    private void carregarNomeEmpresaDoBanco() {
        if (!config.getNomeEmpresa().equals("PDV Pro")) return;

        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT nome_fantasia, razao_social FROM empresa LIMIT 1");
                if (rs.next()) {
                    String nome = rs.getString("nome_fantasia");
                    if (nome == null || nome.isEmpty()) nome = rs.getString("razao_social");
                    if (nome != null && !nome.isEmpty()) {
                        final String nomeEmpresa = nome;
                        config.setNomeEmpresa(nomeEmpresa);
                        runOnUiThread(() -> etNomeEmpresa.setText(nomeEmpresa));
                    }
                }
                rs.close();
                stmt.close();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao carregar nome empresa: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Verifica se o servico de escuta de notificacoes esta habilitado.
     */
    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Abre as configuracoes de acesso a notificacoes.
     */
    private void abrirConfigNotificacao() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
            showToast("Habilite o 'PDV Pro' na lista de acesso a notificacoes");
        } catch (Exception e) {
            showError("Nao foi possivel abrir as configuracoes. Acesse manualmente:\n\n"
                    + "Configuracoes > Apps > Acesso especial > Acesso a notificacoes");
        }
    }

    /**
     * Mostra dialog explicando a necessidade da permissao.
     */
    private void mostrarDialogPermissao() {
        new AlertDialog.Builder(this)
                .setTitle("Permissao Necessaria")
                .setMessage("Para o bot funcionar, e necessario conceder permissao de acesso as notificacoes.\n\n"
                        + "Isso permite que o bot leia e responda mensagens do WhatsApp automaticamente.\n\n"
                        + "Deseja abrir as configuracoes agora?")
                .setPositiveButton("Abrir Configuracoes", (d, w) -> abrirConfigNotificacao())
                .setNegativeButton("Depois", null)
                .show();
    }

    /**
     * v5.0.0 - Atualiza o status visual da IA.
     */
    private void atualizarStatusIA() {
        if (tvIAStatus == null) return;

        boolean iaAtiva = switchIA != null && switchIA.isChecked();
        String apiKey = etIAApiKey != null ? etIAApiKey.getText().toString().trim() : "";

        if (iaAtiva && !apiKey.isEmpty()) {
            String modelo = etIAModel != null ? etIAModel.getText().toString().trim() : "gpt-4.1-nano";
            tvIAStatus.setText("\uD83D\uDFE2 IA ativa | Modelo: " + modelo);
            tvIAStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
        } else if (iaAtiva && apiKey.isEmpty()) {
            tvIAStatus.setText("\uD83D\uDFE1 IA ativa mas sem chave API configurada");
            tvIAStatus.setTextColor(getResources().getColor(R.color.colorWarning));
        } else {
            tvIAStatus.setText("\uD83D\uDD34 IA desativada");
            tvIAStatus.setTextColor(getResources().getColor(R.color.colorDanger));
        }
    }
}
