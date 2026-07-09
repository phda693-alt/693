package com.pdv.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.pdv.app.BuildConfig;
import com.pdv.app.R;
import com.pdv.app.agenda.AgendaReminderService;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.receivers.BootReceiver;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.BackupManager;
import com.pdv.app.utils.LicencaManager;
import com.pdv.app.utils.ErrorHandler;
import com.pdv.app.utils.UserActionLogger;
import com.pdv.app.whatsbot.WhatsAppBotActivity;
import com.pdv.app.activities.ComandasActivity;
import com.pdv.app.activities.ModoEntregadorActivity;
import com.pdv.app.activities.GerenciarEntregasActivity;
import com.pdv.app.activities.CadastroAdicionalActivity;
import com.pdv.app.activities.CadastroGarcomActivity;
import com.pdv.app.activities.CadastroMesaActivity;
import com.pdv.app.activities.GerenciarMesasActivity;
import com.pdv.app.activities.CardapioQRCodeActivity;
import com.pdv.app.activities.CadastroArmarioSaunaActivity;
import com.pdv.app.activities.GerenciarArmariosSaunaActivity;
import com.pdv.app.activities.OrdemServicoActivity;
import com.pdv.app.activities.CadastroServicoActivity;
import com.pdv.app.activities.ContasPagarActivity;
import com.pdv.app.activities.CadastroCaixasNominaisActivity;
import com.pdv.app.activities.CadastroTurnosActivity;
import com.pdv.app.activities.VinculosActivity;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Tela principal do PDV Pro - Dashboard.
 * 
 * v6.0.0 - Sistema de Permissoes Avancado:
 *   - Controle granular de cada botao do dashboard via permissoes
 *   - Botoes do dashboard controlados por permissoes de DASHBOARD (visibilidade)
 *     E permissoes de MODULO (habilitacao/acesso)
 *   - Verificacao de caixa aberto antes de permitir vendas
 *   - Suporte a permissoes personalizadas por usuario (overrides)
 * 
 * v7.0.0 - Botao Atualizar Sistema:
 *   - Busca o arquivo PDV_Pro.apk no servidor FTP configurado na tela de backup
 *   - Baixa o APK e instala a atualizacao substituindo o app atual
 */
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private static final int DIAS_ALERTA_LICENCA = 5;
    private static final String APK_FILE_NAME = "PDV_Pro.apk";
    private static final int REQUEST_INSTALL_PERMISSION = 9001;
    private TextView tvWelcome;
    private TextView tvVersao;
    private EditText etBuscaDashboard;
    private boolean verificandoLicenca = false;
    private View balloonAlerta;
    private TextView tvAlertaMensagem;
    private boolean alertaJaDismissed = false;
    private final Handler agendaHandler = new Handler(Looper.getMainLooper());
    // v8.0.23.0 - Polling local removido: o AgendaReminderService ja faz polling
    // a cada INTERVALO_MS em background. O check local e feito apenas no onResume
    // com throttle interno do AgendaAlertManager (25s). Isso elimina a duplicacao
    // de 3 pontos de verificacao (servico + polling + onResume) para apenas 2.
    private final Runnable agendaCheck = new Runnable() {
        @Override public void run() {
            // Mantido apenas para compatibilidade; o servico cobre o polling principal
            if (PermissionManager.getInstance(MainActivity.this).temPermissao(PermissionConstants.AGENDA_ACESSAR)) {
                com.pdv.app.agenda.AgendaAlertManager.checkAndShow(MainActivity.this);
            }
            // v8.0.23.0 - Intervalo aumentado para 2x o do servico, evitando overlap
            agendaHandler.postDelayed(this, AgendaReminderService.INTERVALO_MS * 2);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvVersao = findViewById(R.id.tvVersao);
        atualizarVersaoDashboard();

        SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
        String nome = prefs.getString("user_nome", "Usuario");
        String perfilNome = PermissionManager.getInstance(this).getPerfilNome();
        tvWelcome.setText("Bem-vindo, " + nome + "!\nPerfil: " + perfilNome);

        // Configurar balao de alerta de licenca
        setupAlertaLicenca();

        // Schedule backup
        BootReceiver.scheduleBackup(this);

        setupDashboard();
        setupBuscaDashboard();
        // v9.1.0 - Entrada animada e escalonada das grades do dashboard (efeito
        // futurista, puramente cosmetico e defensivo - nunca impacta a operacao).
        aplicarEntradaAnimadaDashboard();
        solicitarPermissaoNotificacoes();
        AgendaReminderService.iniciar(this);
        // v8.0.23.0 - Primeiro check local adiado para 5s (servico ja inicia em paralelo)
        // Evita concorrencia imediata entre servico e activity no startup
        agendaHandler.postDelayed(agendaCheck, 5000L);
    }

    /**
     * v9.1.0 - Aplica a entrada animada escalonada (stagger) a todas as grades
     * de botoes do dashboard, dando um acabamento moderno e animado.
     * Metodo aditivo e defensivo: qualquer grade ausente e simplesmente ignorada.
     */
    private void aplicarEntradaAnimadaDashboard() {
        int[] gridIds = new int[]{
                R.id.gridOperacional, R.id.gridGestao, R.id.gridDelivery,
                R.id.gridSistema, R.id.gridOutros
        };
        for (int id : gridIds) {
            try {
                View v = findViewById(id);
                if (v instanceof android.view.ViewGroup) {
                    com.pdv.app.utils.UiFx.staggerChildren((android.view.ViewGroup) v);
                }
            } catch (Throwable ignored) {
                // Efeito cosmetico - nunca deve interromper o carregamento do menu.
            }
        }
    }

    private void solicitarPermissaoNotificacoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9010);
        }
    }

    private void atualizarVersaoDashboard() {
        if (tvVersao != null) {
            tvVersao.setText("v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        verificarLicencaAtual();
        aplicarPermissoesDashboard();
        if (etBuscaDashboard != null && etBuscaDashboard.length() > 0) {
            filtrarDashboard(etBuscaDashboard.getText().toString());
        }
        // Verificar e exibir alerta de vencimento proximo
        verificarAlertaVencimentoLicenca();
        // v8.0.23.0 - Check de agenda no onResume mantido pois o AgendaAlertManager
        // ja possui throttle interno de 25s (nao executa se chamado em menos de 25s)
        // Isso garante que ao retornar de outra tela o alerta seja exibido se necessario
        if (PermissionManager.getInstance(this).temPermissao(PermissionConstants.AGENDA_ACESSAR)) {
            com.pdv.app.agenda.AgendaAlertManager.checkAndShow(this);
        }
    }

    @Override
    protected void onDestroy() {
        agendaHandler.removeCallbacks(agendaCheck);
        super.onDestroy();
    }

    private void verificarLicencaAtual() {
        if (verificandoLicenca) return;
        verificandoLicenca = true;

        new Thread(() -> {
            try {
                boolean licencaValida = LicencaManager.verificarLicenca(this);

                if (!licencaValida) {
                    boolean expirada = LicencaManager.isLicencaExpirada(this);
                    String dataExp = LicencaManager.getDataExpiracao(this);

                    runOnUiThread(() -> {
                        verificandoLicenca = false;

                        if (expirada) {
                            String msg = "Sua licenca esta vencida!";
                            if (dataExp != null) {
                                msg += "\n\nData de expiracao: " + dataExp;
                            }
                            msg += "\n\nPor favor, insira uma nova licenca valida para continuar utilizando o sistema.";

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Licenca Vencida")
                                    .setMessage(msg)
                                    .setCancelable(false)
                                    .setPositiveButton("Renovar Licenca", (dialog, which) -> {
                                        startActivity(new Intent(MainActivity.this, LicencaActivity.class));
                                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                                        finish();
                                    })
                                    .show();
                        } else {
                            startActivity(new Intent(MainActivity.this, LicencaActivity.class));
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            finish();
                        }
                    });
                } else {
                    verificandoLicenca = false;
                    Log.d(TAG, "Licenca verificada: VALIDA");
                }
            } catch (Exception e) {
                verificandoLicenca = false;
                Log.e(TAG, "Erro ao verificar licenca no onResume", e);
            }
        }).start();
    }

    private void doLogoff() {
        showConfirm("Logoff", "Deseja encerrar sua sessao e voltar para a tela de login?", () -> {
            UserActionLogger.log(this, "LOGOUT", "Autenticacao", "Sessao encerrada pelo usuario");
            PermissionManager.getInstance(this).invalidarCache();

            SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
            prefs.edit().clear().apply();

            try {
                DatabaseHelper.getInstance(this).closeConnection();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao fechar conexao no logoff: " + e.getMessage());
            }

            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });
    }

    /**
     * v6.0.0 - Aplica permissoes avancadas no dashboard.
     * 
     * Sistema de duas camadas:
     * 1. Permissao de DASHBOARD (dashboard.btn_xxx) -> controla VISIBILIDADE do botao
     * 2. Permissao de MODULO (modulo.acessar) -> controla HABILITACAO do botao
     * 
     * Isso permite que o admin:
     * - Mostre um botao no dashboard mas desabilite o acesso ao modulo
     * - Oculte um botao do dashboard mesmo que o perfil tenha acesso ao modulo
     * - Personalize por usuario individual via overrides
     */
    private void aplicarPermissoesDashboard() {
        PermissionManager pm = PermissionManager.getInstance(this);

        // Mapeamento: botao -> permissao dashboard (visibilidade) + permissao modulo (habilitacao)
        aplicarPermissaoBotao(R.id.btnVendas, PermissionConstants.DASHBOARD_BTN_VENDAS, PermissionConstants.VENDAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnComandas, PermissionConstants.DASHBOARD_BTN_COMANDAS, PermissionConstants.COMANDAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnProdutos, PermissionConstants.DASHBOARD_BTN_PRODUTOS, PermissionConstants.PRODUTOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnGerenciarProdutos, PermissionConstants.DASHBOARD_BTN_GERENCIAR_PRODUTOS, PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnClientes, PermissionConstants.DASHBOARD_BTN_CLIENTES, PermissionConstants.CLIENTES_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCaixa, PermissionConstants.DASHBOARD_BTN_CAIXA, PermissionConstants.CAIXA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnRelatorios, PermissionConstants.DASHBOARD_BTN_RELATORIOS, PermissionConstants.RELATORIOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnHistorico, PermissionConstants.DASHBOARD_BTN_HISTORICO, PermissionConstants.HISTORICO_ACESSAR);
        aplicarPermissaoBotao(R.id.btnEmpresa, PermissionConstants.DASHBOARD_BTN_EMPRESA, PermissionConstants.EMPRESA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnVendedores, PermissionConstants.DASHBOARD_BTN_VENDEDORES, PermissionConstants.VENDEDORES_ACESSAR);
        aplicarPermissaoBotao(R.id.btnEntregadores, PermissionConstants.DASHBOARD_BTN_ENTREGADORES, PermissionConstants.ENTREGADORES_ACESSAR);
        aplicarPermissaoBotao(R.id.btnUsuarios, PermissionConstants.DASHBOARD_BTN_USUARIOS, PermissionConstants.USUARIOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnPerfis, PermissionConstants.DASHBOARD_BTN_PERFIS, PermissionConstants.PERFIS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnFormasPagamento, PermissionConstants.DASHBOARD_BTN_FORMAS_PAGAMENTO, PermissionConstants.FORMAS_PAGAMENTO_ACESSAR);
        aplicarPermissaoBotao(R.id.btnTiposProduto, PermissionConstants.DASHBOARD_BTN_TIPOS_PRODUTO, PermissionConstants.TIPOS_PRODUTO_ACESSAR);
        aplicarPermissaoBotao(R.id.btnAdicionais, PermissionConstants.DASHBOARD_BTN_ADICIONAIS, PermissionConstants.ADICIONAIS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnObservacoes, PermissionConstants.DASHBOARD_BTN_OBSERVACOES, PermissionConstants.OBSERVACOES_ACESSAR);
        aplicarPermissaoBotao(R.id.btnImpressora, PermissionConstants.DASHBOARD_BTN_IMPRESSORA, PermissionConstants.CONFIG_IMPRESSORA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnMultiImpressoras, PermissionConstants.DASHBOARD_BTN_MULTIIMPRESSORAS, PermissionConstants.MULTIIMPRESSORAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnBackup, PermissionConstants.DASHBOARD_BTN_BACKUP, PermissionConstants.BACKUP_ACESSAR);
        aplicarPermissaoBotao(R.id.btnLicenca, PermissionConstants.DASHBOARD_BTN_LICENCA, PermissionConstants.LICENCA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnTrocarSenha, PermissionConstants.DASHBOARD_BTN_TROCAR_SENHA, PermissionConstants.TROCAR_SENHA);
        aplicarPermissaoBotao(R.id.btnModoEntregador, PermissionConstants.DASHBOARD_BTN_MODO_ENTREGADOR, PermissionConstants.MODO_ENTREGADOR_ACESSAR);
        aplicarPermissaoBotao(R.id.btnGerenciarEntregas, PermissionConstants.DASHBOARD_BTN_GERENCIAR_ENTREGAS, PermissionConstants.ENTREGAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnContasReceber, PermissionConstants.DASHBOARD_BTN_CONTAS_RECEBER, PermissionConstants.CONTAS_RECEBER_ACESSAR);
        aplicarPermissaoBotao(R.id.btnWhatsBot, PermissionConstants.DASHBOARD_BTN_WHATSBOT, PermissionConstants.WHATSBOT_ACESSAR);
        aplicarPermissaoBotao(R.id.btnEntradaNotas, PermissionConstants.DASHBOARD_BTN_ENTRADA_NOTAS, PermissionConstants.ENTRADA_NOTAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnTaxaEntrega, PermissionConstants.DASHBOARD_BTN_TAXA_ENTREGA, PermissionConstants.TAXA_ENTREGA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnPainelChamados, PermissionConstants.DASHBOARD_BTN_PAINEL_CHAMADOS, PermissionConstants.PAINEL_CHAMADOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnGerenciadorChamados, PermissionConstants.DASHBOARD_BTN_GERENCIADOR_CHAMADOS, PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR);

        // Botoes de Garcons, Cadastro de Mesas e Gerenciar Mesas
        aplicarPermissaoBotao(R.id.btnGarcons, PermissionConstants.DASHBOARD_BTN_GARCONS, PermissionConstants.GARCONS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCadastroMesas, PermissionConstants.DASHBOARD_BTN_CADASTRO_MESAS, PermissionConstants.MESAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnGerenciarMesas, PermissionConstants.DASHBOARD_BTN_GERENCIAR_MESAS, PermissionConstants.GERENCIAR_MESAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCardapioQRCode, PermissionConstants.DASHBOARD_BTN_CARDAPIO_QRCODE, PermissionConstants.CARDAPIO_QRCODE_ACESSAR);

        // Botao Painel da Cozinha
        aplicarPermissaoBotao(R.id.btnPainelCozinha, PermissionConstants.DASHBOARD_BTN_PAINEL_COZINHA, PermissionConstants.PAINEL_COZINHA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnWebCozinha, PermissionConstants.DASHBOARD_BTN_WEB_COZINHA, PermissionConstants.PAINEL_COZINHA_WEB);
        aplicarPermissaoBotao(R.id.btnPainelSenhas, PermissionConstants.DASHBOARD_BTN_PAINEL_SENHAS, PermissionConstants.PAINEL_SENHAS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnEstacionamento, PermissionConstants.DASHBOARD_BTN_ESTACIONAMENTO, PermissionConstants.ESTACIONAMENTO_ACESSAR);

        // Botoes de Armarios para Sauna
        aplicarPermissaoBotao(R.id.btnCadastroArmariosSauna, PermissionConstants.DASHBOARD_BTN_CADASTRO_ARMARIOS_SAUNA, PermissionConstants.ARMARIOS_SAUNA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnGerenciarArmariosSauna, PermissionConstants.DASHBOARD_BTN_GERENCIAR_ARMARIOS_SAUNA, PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnOrdemServico, PermissionConstants.DASHBOARD_BTN_ORDEM_SERVICO, PermissionConstants.ORDEM_SERVICO_ACESSAR);
        // Botao Cadastro de Servicos
        aplicarPermissaoBotao(R.id.btnCadastroServico, PermissionConstants.DASHBOARD_BTN_CADASTRO_SERVICO, PermissionConstants.SERVICOS_ACESSAR);
        // Botao Atualizar Sistema
        aplicarPermissaoBotao(R.id.btnAtualizar, PermissionConstants.DASHBOARD_BTN_ATUALIZAR, PermissionConstants.ATUALIZAR_SISTEMA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnDiagnostico, PermissionConstants.DASHBOARD_BTN_DIAGNOSTICO, PermissionConstants.DIAGNOSTICO_ACESSAR);
        aplicarPermissaoBotao(R.id.btnServidorMySQL, PermissionConstants.DASHBOARD_BTN_SERVIDOR_MYSQL, PermissionConstants.SERVIDOR_MYSQL_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCriarBanco, PermissionConstants.DASHBOARD_BTN_CRIAR_BANCO_MYSQL, PermissionConstants.CRIAR_BANCO_MYSQL);
        aplicarPermissaoBotao(R.id.btnGerenciarUsuariosDB, PermissionConstants.DASHBOARD_BTN_USUARIOS_MYSQL, PermissionConstants.USUARIOS_MYSQL_ACESSAR);
        aplicarPermissaoBotao(R.id.btnMySQLEspelho, PermissionConstants.DASHBOARD_BTN_MYSQL_ESPELHO, PermissionConstants.MYSQL_ESPELHO_ACESSAR);
        aplicarPermissaoBotao(R.id.btnAgenda, PermissionConstants.DASHBOARD_BTN_AGENDA, PermissionConstants.AGENDA_ACESSAR);
        aplicarPermissaoBotao(R.id.btnConfiguracoes, PermissionConstants.DASHBOARD_BTN_CONFIGURACOES, PermissionConstants.CONFIG_GERAL_ACESSAR);
        aplicarPermissaoBotao(R.id.btnFornecedores, PermissionConstants.DASHBOARD_BTN_FORNECEDORES, PermissionConstants.FORNECEDORES_ACESSAR);

        // Funcoes adicionadas apos a criacao dos niveis/perfis.
        // Mantem nome da permissao fiel ao botao e acao real executada no click.
        aplicarPermissaoBotao(R.id.btnContasPagar, PermissionConstants.DASHBOARD_BTN_CONTAS_PAGAR, PermissionConstants.CONTAS_PAGAR_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCadastroCaixasNominais, PermissionConstants.DASHBOARD_BTN_CAIXAS_NOMINAIS, PermissionConstants.CAIXAS_NOMINAIS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnCadastroTurnos, PermissionConstants.DASHBOARD_BTN_TURNOS, PermissionConstants.TURNOS_ACESSAR);
        aplicarPermissaoBotao(R.id.btnVinculos, PermissionConstants.DASHBOARD_BTN_VINCULOS, PermissionConstants.VINCULOS_ACESSAR);

        // Botao Sobre - controlado apenas pela permissao de dashboard
        View btnSobre = findViewById(R.id.btnSobre);
        if (btnSobre != null) {
            PermissionHelper.controlarVisibilidade(this, btnSobre, PermissionConstants.DASHBOARD_BTN_SOBRE);
        }

        organizarSecoesDashboard();

        // Botoes que sempre ficam visiveis (nao dependem de permissao)
        // btnSair, btnLogoff - sempre visiveis
    }

    /**
     * Aplica permissao de duas camadas a um botao do dashboard.
     * 
     * @param btnId ID do botao
     * @param permDashboard Permissao de visibilidade no dashboard
     * @param permModulo Permissao de acesso ao modulo (habilitacao)
     */
    private void aplicarPermissaoBotao(int btnId, String permDashboard, String permModulo) {
        View btn = findViewById(btnId);
        if (btn == null) return;

        PermissionManager pm = PermissionManager.getInstance(this);

        // Camada 1: Visibilidade (dashboard)
        boolean visivel = pm.temPermissao(permDashboard);
        btn.setVisibility(visivel ? View.VISIBLE : View.GONE);

        if (visivel) {
            // Camada 2: Habilitacao (modulo)
            boolean habilitado = pm.temPermissao(permModulo);
            PermissionHelper.aplicarEstadoHabilitacao(btn, habilitado);
        }
    }

    /** Reposiciona somente os botoes visiveis e oculta secoes vazias. */
    private void organizarSecoesDashboard() {
        organizarSecao(R.id.gridOperacional, R.id.titleOperacional);
        organizarSecao(R.id.gridGestao, R.id.titleGestao);
        organizarSecao(R.id.gridDelivery, R.id.titleDelivery);
        organizarSecao(R.id.gridSistema, R.id.titleSistema);
        organizarSecao(R.id.gridOutros, R.id.titleOutros);
    }

    private void organizarSecao(int gridId, int tituloId) {
        GridLayout grid = findViewById(gridId);
        View titulo = findViewById(tituloId);
        if (grid == null || titulo == null) return;

        int posicao = 0;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) child.getLayoutParams();
            lp.rowSpec = GridLayout.spec(posicao / 3);
            lp.columnSpec = GridLayout.spec(posicao % 3, 1f);
            lp.width = 0;
            child.setLayoutParams(lp);
            posicao++;
        }
        boolean possuiBotoes = posicao > 0;
        grid.setVisibility(possuiBotoes ? View.VISIBLE : View.GONE);
        titulo.setVisibility(possuiBotoes ? View.VISIBLE : View.GONE);
    }

    /** Busca instantanea por nome de botao ou titulo de secao sem ignorar permissoes. */
    private void setupBuscaDashboard() {
        etBuscaDashboard = findViewById(R.id.etBuscaDashboard);
        if (etBuscaDashboard == null) return;
        etBuscaDashboard.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtrarDashboard(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filtrarDashboard(String consulta) {
        // Restaura primeiro a visibilidade autorizada; a busca nunca revela botoes sem permissao.
        aplicarPermissoesDashboard();
        String termo = normalizarBusca(consulta);
        filtrarSecaoDashboard(R.id.gridOperacional, R.id.titleOperacional, termo);
        filtrarSecaoDashboard(R.id.gridGestao, R.id.titleGestao, termo);
        filtrarSecaoDashboard(R.id.gridDelivery, R.id.titleDelivery, termo);
        filtrarSecaoDashboard(R.id.gridSistema, R.id.titleSistema, termo);
        filtrarSecaoDashboard(R.id.gridOutros, R.id.titleOutros, termo);
    }

    private void filtrarSecaoDashboard(int gridId, int tituloId, String termo) {
        GridLayout grid = findViewById(gridId);
        TextView titulo = findViewById(tituloId);
        if (grid == null || titulo == null) return;
        boolean tituloCorresponde = termo.isEmpty()
                || normalizarBusca(titulo.getText().toString()).contains(termo);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            CharSequence texto = child instanceof TextView ? ((TextView) child).getText() : "";
            boolean corresponde = termo.isEmpty() || tituloCorresponde
                    || normalizarBusca(String.valueOf(texto)).contains(termo);
            child.setVisibility(corresponde ? View.VISIBLE : View.GONE);
        }
        organizarSecao(gridId, tituloId);
    }

    private static String normalizarBusca(String valor) {
        String semAcentos = Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcentos.replace('\n', ' ').toLowerCase(Locale.ROOT).trim();
    }

    private void setupDashboard() {
        int[] btnIds = {
            R.id.btnVendas, R.id.btnComandas, R.id.btnProdutos, R.id.btnGerenciarProdutos, R.id.btnClientes,
            R.id.btnCaixa, R.id.btnRelatorios, R.id.btnHistorico,
            R.id.btnEmpresa, R.id.btnVendedores, R.id.btnEntregadores,
            R.id.btnUsuarios, R.id.btnPerfis, R.id.btnFormasPagamento, R.id.btnTiposProduto,
            R.id.btnAdicionais, R.id.btnObservacoes, R.id.btnImpressora, R.id.btnMultiImpressoras, R.id.btnBackup, R.id.btnDiagnostico,
            R.id.btnLicenca, R.id.btnTrocarSenha, R.id.btnSair,
            R.id.btnLogoff, R.id.btnModoEntregador, R.id.btnGerenciarEntregas, R.id.btnContasReceber, R.id.btnWhatsBot, R.id.btnEntradaNotas, R.id.btnFornecedores, R.id.btnTaxaEntrega, R.id.btnSobre,
            R.id.btnPainelChamados, R.id.btnPainelSenhas, R.id.btnGerenciadorChamados,
            R.id.btnGarcons, R.id.btnCadastroMesas, R.id.btnGerenciarMesas,
            R.id.btnCardapioQRCode,
            R.id.btnPainelCozinha, R.id.btnWebCozinha, R.id.btnEstacionamento,
            R.id.btnCadastroArmariosSauna, R.id.btnGerenciarArmariosSauna,
            R.id.btnOrdemServico, R.id.btnCadastroServico, R.id.btnAtualizar,
            R.id.btnAgenda, R.id.btnMySQLEspelho, R.id.btnConfiguracoes,
            R.id.btnContasPagar, R.id.btnCadastroCaixasNominais, R.id.btnCadastroTurnos, R.id.btnVinculos
        };

        Class<?>[] activities = {
            VendaActivity.class, ComandasActivity.class, CadastroProdutoActivity.class, GerenciarProdutosActivity.class, CadastroClienteActivity.class,
            CaixaActivity.class, RelatoriosActivity.class, HistoricoVendasActivity.class,
            CadastroEmpresaActivity.class, CadastroVendedorActivity.class, CadastroEntregadorActivity.class,
            CadastroUsuarioActivity.class, GerenciarPerfisActivity.class, CadastroFormaPagamentoActivity.class, CadastroTipoProdutoActivity.class,
            CadastroAdicionalActivity.class, CadastroObservacaoActivity.class, ConfigImpressoraActivity.class, MultiImpressorasActivity.class, BackupConfigActivity.class, SystemHealthActivity.class,
            LicencaActivity.class, TrocaSenhaActivity.class, null,
            null, ModoEntregadorActivity.class, GerenciarEntregasActivity.class, ContasReceberActivity.class, WhatsAppBotActivity.class, EntradaNotasActivity.class, CadastroFornecedorActivity.class, TaxaEntregaBairroActivity.class, SobreActivity.class,
            PainelChamadosActivity.class, PainelSenhasWebActivity.class, GerenciadorChamadosActivity.class,
            CadastroGarcomActivity.class, CadastroMesaActivity.class, GerenciarMesasActivity.class,
            CardapioQRCodeActivity.class,
            PainelCozinhaActivity.class, PainelCozinhaActivity.class, EstacionamentoActivity.class,
            CadastroArmarioSaunaActivity.class, GerenciarArmariosSaunaActivity.class,
            OrdemServicoActivity.class, CadastroServicoActivity.class, null, // Atualizar - tratado separadamente
            AgendaActivity.class, MirrorDatabaseActivity.class, ConfiguracoesActivity.class,
            ContasPagarActivity.class, CadastroCaixasNominaisActivity.class, CadastroTurnosActivity.class, VinculosActivity.class
        };

        // Permissoes de modulo correspondentes a cada botao (para verificacao no click)
        String[] permissoesModulo = {
            PermissionConstants.VENDAS_ACESSAR, PermissionConstants.COMANDAS_ACESSAR,
            PermissionConstants.PRODUTOS_ACESSAR, PermissionConstants.GERENCIAR_PRODUTOS_ACESSAR,
            PermissionConstants.CLIENTES_ACESSAR, PermissionConstants.CAIXA_ACESSAR,
            PermissionConstants.RELATORIOS_ACESSAR, PermissionConstants.HISTORICO_ACESSAR,
            PermissionConstants.EMPRESA_ACESSAR, PermissionConstants.VENDEDORES_ACESSAR,
            PermissionConstants.ENTREGADORES_ACESSAR, PermissionConstants.USUARIOS_ACESSAR,
            PermissionConstants.PERFIS_ACESSAR, PermissionConstants.FORMAS_PAGAMENTO_ACESSAR,
            PermissionConstants.TIPOS_PRODUTO_ACESSAR, PermissionConstants.ADICIONAIS_ACESSAR,
            PermissionConstants.OBSERVACOES_ACESSAR,
            PermissionConstants.CONFIG_IMPRESSORA_ACESSAR, PermissionConstants.MULTIIMPRESSORAS_ACESSAR, PermissionConstants.BACKUP_ACESSAR, PermissionConstants.DIAGNOSTICO_ACESSAR,
            PermissionConstants.LICENCA_ACESSAR, PermissionConstants.TROCAR_SENHA,
            null, null,
            PermissionConstants.MODO_ENTREGADOR_ACESSAR, PermissionConstants.ENTREGAS_ACESSAR,
            PermissionConstants.CONTAS_RECEBER_ACESSAR, PermissionConstants.WHATSBOT_ACESSAR,
            PermissionConstants.ENTRADA_NOTAS_ACESSAR, PermissionConstants.FORNECEDORES_ACESSAR, PermissionConstants.TAXA_ENTREGA_ACESSAR,
            null, // Sobre
            PermissionConstants.PAINEL_CHAMADOS_ACESSAR, PermissionConstants.PAINEL_SENHAS_ACESSAR, PermissionConstants.GERENCIADOR_CHAMADOS_ACESSAR,
            PermissionConstants.GARCONS_ACESSAR, PermissionConstants.MESAS_ACESSAR, PermissionConstants.GERENCIAR_MESAS_ACESSAR,
            PermissionConstants.CARDAPIO_QRCODE_ACESSAR,
            PermissionConstants.PAINEL_COZINHA_ACESSAR, PermissionConstants.PAINEL_COZINHA_WEB, PermissionConstants.ESTACIONAMENTO_ACESSAR,
            PermissionConstants.ARMARIOS_SAUNA_ACESSAR, PermissionConstants.GERENCIAR_ARMARIOS_SAUNA_ACESSAR,
            PermissionConstants.ORDEM_SERVICO_ACESSAR,
            PermissionConstants.SERVICOS_ACESSAR,
            PermissionConstants.ATUALIZAR_SISTEMA_ACESSAR,
            PermissionConstants.AGENDA_ACESSAR,
            PermissionConstants.MYSQL_ESPELHO_ACESSAR,
            PermissionConstants.CONFIG_GERAL_ACESSAR,
            PermissionConstants.CONTAS_PAGAR_ACESSAR,
            PermissionConstants.CAIXAS_NOMINAIS_ACESSAR,
            PermissionConstants.TURNOS_ACESSAR,
            PermissionConstants.VINCULOS_ACESSAR
        };

        for (int i = 0; i < btnIds.length; i++) {
            View btn = findViewById(btnIds[i]);
            if (btn == null) continue;
            final int index = i;
            final int btnId = btnIds[i];
            final String permModulo = permissoesModulo[i];

            btn.setOnClickListener(v -> {
                if (btnId == R.id.btnLogoff) {
                    doLogoff();
                } else if (btnId == R.id.btnSair) {
                    PermissionManager.getInstance(this).invalidarCache();
                    SharedPreferences prefs = getSharedPreferences("session", MODE_PRIVATE);
                    prefs.edit().clear().apply();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                } else if (btnId == R.id.btnAtualizar) {
                    // Verificar permissao de modulo antes de atualizar
                    if (permModulo != null && !PermissionHelper.verificar(this, permModulo)) {
                        return;
                    }
                    iniciarAtualizacaoSistema();
                } else if (activities[index] == null) {
                    // Nenhuma activity associada
                    return;
                } else {
                    // Verificar permissao de modulo antes de abrir
                    if (permModulo != null && !PermissionHelper.verificar(this, permModulo)) {
                        return; // Acesso negado - dialog ja exibido pelo verificar()
                    }

                    // v6.0.0 - Verificacao especial para Vendas: exige caixa aberto
                    if (btnId == R.id.btnVendas) {
                        verificarCaixaEAbrirVenda();
                        return;
                    }

                    startActivity(new Intent(this, activities[index]));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
            });
            AnimUtils.slideUp(btn, i * 60L);
        }

        // Aplicar permissoes iniciais
        aplicarPermissoesDashboard();

        // === MariaDB integrado: botoes do servidor MySQL ===
        setupServerButtons();
    }

    /**
     * Configura os botoes de integracao com o MariaDB interno.
     * - Servidor MySQL: inicia/verifica o servidor integrado
     * - Criar Banco: abre CreateDatabaseActivity (interno)
     * - Usuarios MySQL: abre ManageUsersActivity (interno)
     */
    private void setupServerButtons() {
        View btnServidor = findViewById(R.id.btnServidorMySQL);
        View btnCriarBanco = findViewById(R.id.btnCriarBanco);
        View btnGerenciarUsuariosDB = findViewById(R.id.btnGerenciarUsuariosDB);

        if (btnServidor != null) {
            btnServidor.setOnClickListener(v -> {
                if (!PermissionHelper.verificar(this, PermissionConstants.SERVIDOR_MYSQL_ACESSAR)) return;
                iniciarServidorMySQLIntegrado();
            });
        }

        if (btnCriarBanco != null) {
            btnCriarBanco.setOnClickListener(v -> {
                if (!PermissionHelper.verificar(this, PermissionConstants.CRIAR_BANCO_MYSQL)) return;
                startActivity(new Intent(this, com.phda.phserver.CreateDatabaseActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        }

        if (btnGerenciarUsuariosDB != null) {
            btnGerenciarUsuariosDB.setOnClickListener(v -> {
                if (!PermissionHelper.verificar(this, PermissionConstants.USUARIOS_MYSQL_ACESSAR)) return;
                startActivity(new Intent(this, com.phda.phserver.ManageUsersActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        }
    }

    private void iniciarServidorMySQLIntegrado() {
        showLoading("Verificando servidor MySQL integrado...");
        new Thread(() -> {
            boolean ok = com.pdv.app.server.MariaDbServerManager.ensureReadyBlocking(this,
                    message -> runOnUiThread(() -> showLoading(message)));
            hideLoading();
            runOnUiThread(() -> {
                String status = com.pdv.app.server.MariaDbServerManager.getLastStatus(this);
                if (ok) {
                    new AlertDialog.Builder(this)
                            .setTitle("Servidor MySQL Integrado")
                            .setMessage("MariaDB interno ativo em 127.0.0.1:3306.\n\n"
                                    + "Banco: banco\nUsuario: usuario\nSenha: senha\n\n"
                                    + "Nao e necessario instalar o PHSERVER separado.\n\n"
                                    + "Status: " + status)
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Ajustes bateria", (d, w) ->
                                    startActivity(com.pdv.app.server.MariaDbServerManager
                                            .buildBatterySettingsIntent(this)))
                            .show();
                } else {
                    showError("Nao foi possivel iniciar o servidor MySQL integrado.\n\n" + status);
                }
            });
        }, "PDV-Start-Embedded-MySQL").start();
    }

    /**
     * v6.0.0 - Verifica se o caixa esta aberto antes de permitir acesso a tela de vendas.
     * A verificacao e feita em background para nao bloquear a UI thread.
     */
    private void verificarCaixaEAbrirVenda() {
        showLoading("Verificando caixa...");
        new Thread(() -> {
            try {
                boolean caixaAberto = PermissionManager.getInstance(this).isCaixaAberto();
                hideLoading();
                runOnUiThread(() -> {
                    if (caixaAberto) {
                        startActivity(new Intent(this, VendaActivity.class));
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    } else {
                        PermissionHelper.mostrarCaixaFechado(this);
                    }
                });
            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao verificar caixa: " + e.getMessage());
                runOnUiThread(() -> {
                    // Em caso de erro, permitir acesso (fail-open para nao bloquear vendas)
                    startActivity(new Intent(this, VendaActivity.class));
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                });
            }
        }).start();
    }

    // =========================================================================
    // ATUALIZAR SISTEMA VIA FTP
    // Busca o arquivo PDV_Pro.apk no servidor FTP configurado na tela de backup
    // e instala a atualizacao substituindo o app atual
    // =========================================================================

    /**
     * Inicia o processo de atualizacao do sistema.
     * Exibe um dialog de confirmacao antes de iniciar o download.
     */
    private void iniciarAtualizacaoSistema() {
        // Verificar se as configuracoes FTP estao preenchidas
        BackupManager bm = new BackupManager(this);
        String ftpHost = bm.getFtpHost();
        String ftpUser = bm.getFtpUser();
        String ftpPassword = bm.getFtpPassword();

        if (ftpHost == null || ftpHost.trim().isEmpty()) {
            showError("Servidor FTP nao configurado!\n\nConfigure o servidor FTP na tela de Backup e Restauracao antes de atualizar o sistema.");
            return;
        }

        showConfirm("Atualizar Sistema",
                "Deseja verificar e baixar a atualizacao do sistema?\n\n" +
                "O sistema ira procurar o arquivo " + APK_FILE_NAME + " no servidor FTP configurado:\n" +
                ftpHost + "\n\n" +
                "Apos o download, a instalacao sera iniciada automaticamente.",
                () -> verificarEBaixarAtualizacao());
    }

    /**
     * Verifica se o arquivo PDV_Pro.apk existe no servidor FTP,
     * baixa o arquivo e inicia a instalacao.
     */
    private void verificarEBaixarAtualizacao() {
        showLoading("Conectando ao servidor FTP...");

        new Thread(() -> {
            FTPClient ftp = new FTPClient();
            try {
                BackupManager bm = new BackupManager(this);

                // Conectar ao servidor FTP
                ftp.setConnectTimeout(15000);
                ftp.setDataTimeout(60000);
                ftp.connect(bm.getFtpHost(), 21);
                boolean loginOk = ftp.login(bm.getFtpUser(), bm.getFtpPassword());

                if (!loginOk) {
                    hideLoading();
                    showError("Falha ao conectar no servidor FTP.\n\nVerifique o usuario e senha nas configuracoes de Backup.");
                    return;
                }

                ftp.enterLocalPassiveMode();
                ftp.setFileType(FTP.BINARY_FILE_TYPE);

                // Verificar se o arquivo PDV_Pro.apk existe no servidor
                runOnUiThread(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.setMessage("Procurando " + APK_FILE_NAME + " no servidor...");
                    }
                });

                FTPFile[] files = ftp.listFiles();
                FTPFile apkFile = null;

                if (files != null) {
                    for (FTPFile file : files) {
                        if (file.isFile() && APK_FILE_NAME.equalsIgnoreCase(file.getName())) {
                            apkFile = file;
                            break;
                        }
                    }
                }

                if (apkFile == null) {
                    ftp.logout();
                    ftp.disconnect();
                    hideLoading();
                    showError("Arquivo " + APK_FILE_NAME + " nao encontrado no servidor FTP.\n\n" +
                            "Certifique-se de que o arquivo de atualizacao foi enviado para o servidor FTP configurado.");
                    return;
                }

                // Arquivo encontrado - informar tamanho e iniciar download
                final long fileSize = apkFile.getSize();
                final String fileSizeStr = formatFileSize(fileSize);

                runOnUiThread(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.setMessage("Baixando atualizacao...\nTamanho: " + fileSizeStr);
                    }
                });

                // Criar diretorio de download no cache do app
                File cacheDir = new File(getCacheDir(), "updates");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }

                // Limpar APKs antigos do cache
                File[] oldFiles = cacheDir.listFiles();
                if (oldFiles != null) {
                    for (File oldFile : oldFiles) {
                        oldFile.delete();
                    }
                }

                File apkLocalFile = new File(cacheDir, APK_FILE_NAME);

                // Baixar o arquivo APK
                OutputStream outputStream = new FileOutputStream(apkLocalFile);
                boolean downloaded = ftp.retrieveFile(APK_FILE_NAME, outputStream);
                outputStream.close();

                ftp.logout();
                ftp.disconnect();

                hideLoading();

                if (!downloaded || !apkLocalFile.exists() || apkLocalFile.length() == 0) {
                    // Limpar arquivo corrompido
                    if (apkLocalFile.exists()) {
                        apkLocalFile.delete();
                    }
                    showError("Falha ao baixar a atualizacao.\n\nVerifique sua conexao com a internet e tente novamente.");
                    return;
                }

                // Verificar integridade basica (tamanho)
                if (fileSize > 0 && apkLocalFile.length() < fileSize * 0.9) {
                    apkLocalFile.delete();
                    showError("O arquivo de atualizacao foi baixado de forma incompleta.\n\nTente novamente.");
                    return;
                }

                Log.d(TAG, "APK baixado com sucesso: " + apkLocalFile.getAbsolutePath() +
                        " (" + apkLocalFile.length() + " bytes)");

                // Iniciar instalacao do APK
                runOnUiThread(() -> instalarApk(apkLocalFile));

            } catch (Exception e) {
                hideLoading();
                Log.e(TAG, "Erro ao atualizar sistema via FTP", e);
                showError("Erro ao atualizar o sistema:\n\n" + e.getMessage() +
                        "\n\nVerifique as configuracoes do servidor FTP e sua conexao com a internet.");
            } finally {
                try {
                    if (ftp.isConnected()) ftp.disconnect();
                } catch (Exception ignored) {}
            }
        }).start();
    }

    /**
     * Instala o APK baixado.
     * Utiliza FileProvider para Android 7+ (API 24+) e Intent direto para versoes anteriores.
     * Para Android 8+ (API 26+), verifica se a permissao de instalar de fontes desconhecidas esta habilitada.
     */
    private void instalarApk(File apkFile) {
        try {
            // Para Android 8+ (Oreo), verificar permissao de instalar de fontes desconhecidas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    // Salvar o caminho do APK para instalar apos a permissao ser concedida
                    SharedPreferences prefs = getSharedPreferences("update_config", MODE_PRIVATE);
                    prefs.edit().putString("pending_apk_path", apkFile.getAbsolutePath()).apply();

                    new AlertDialog.Builder(this)
                            .setTitle("Permissao Necessaria")
                            .setMessage("Para instalar a atualizacao, e necessario permitir a instalacao de aplicativos de fontes desconhecidas.\n\n" +
                                    "Voce sera redirecionado para as configuracoes do sistema.\n" +
                                    "Ative a opcao e volte para continuar a atualizacao.")
                            .setCancelable(false)
                            .setPositiveButton("Configurar", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
                            })
                            .setNegativeButton("Cancelar", (dialog, which) -> {
                                showToast("Atualizacao cancelada.");
                            })
                            .show();
                    return;
                }
            }

            // Executar a instalacao
            executarInstalacaoApk(apkFile);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao instalar APK", e);
            showError("Erro ao iniciar a instalacao da atualizacao:\n\n" + e.getMessage());
        }
    }

    /**
     * Executa a instalacao do APK usando Intent.
     * Usa FileProvider para Android 7+ para compartilhar o arquivo de forma segura.
     */
    private void executarInstalacaoApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ (Nougat): usar FileProvider
                Uri apkUri = FileProvider.getUriForFile(this,
                        getPackageName() + ".fileprovider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Android 6 e anteriores: usar URI direto
                Uri apkUri = Uri.fromFile(apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            }

            showSuccess("Atualizacao baixada com sucesso!\n\n" +
                    "A instalacao sera iniciada agora.\n" +
                    "Apos a instalacao, o aplicativo sera atualizado automaticamente.");

            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao executar instalacao do APK", e);
            showError("Erro ao iniciar a instalacao:\n\n" + e.getMessage() +
                    "\n\nTente instalar manualmente o arquivo: " + apkFile.getAbsolutePath());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            // Verificar se a permissao foi concedida
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && getPackageManager().canRequestPackageInstalls()) {
                // Permissao concedida - tentar instalar o APK pendente
                SharedPreferences prefs = getSharedPreferences("update_config", MODE_PRIVATE);
                String pendingApkPath = prefs.getString("pending_apk_path", null);
                prefs.edit().remove("pending_apk_path").apply();

                if (pendingApkPath != null) {
                    File apkFile = new File(pendingApkPath);
                    if (apkFile.exists()) {
                        executarInstalacaoApk(apkFile);
                    } else {
                        showError("O arquivo de atualizacao nao foi encontrado.\n\nTente baixar a atualizacao novamente.");
                    }
                }
            } else {
                showToast("Permissao nao concedida. Atualizacao cancelada.");
            }
        }
    }

    /**
     * Formata o tamanho do arquivo para exibicao amigavel.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Configura o balao de alerta de vencimento de licenca.
     * Inicializa as referencias dos views e configura o botao de fechar.
     */
    private void setupAlertaLicenca() {
        balloonAlerta = findViewById(R.id.balloonLicencaAlerta);
        tvAlertaMensagem = findViewById(R.id.tvAlertaLicencaMensagem);
        ImageView btnFechar = findViewById(R.id.btnFecharAlertaLicenca);

        if (btnFechar != null) {
            btnFechar.setOnClickListener(v -> {
                alertaJaDismissed = true;
                if (balloonAlerta != null) {
                    // Animacao de fade out ao fechar
                    AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                    fadeOut.setDuration(300);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {}
                        @Override
                        public void onAnimationEnd(Animation animation) {
                            balloonAlerta.setVisibility(View.GONE);
                        }
                        @Override
                        public void onAnimationRepeat(Animation animation) {}
                    });
                    balloonAlerta.startAnimation(fadeOut);
                }
            });
        }
    }

    /**
     * Verifica se a licenca esta proxima do vencimento (5 dias ou menos)
     * e exibe o balao de alerta com a contagem regressiva de dias
     * e o numero de contato para renovacao.
     * 
     * O balao e exibido automaticamente ao entrar na tela principal
     * e pode ser fechado pelo usuario. Nao sera exibido novamente
     * na mesma sessao apos ser fechado.
     */
    private void verificarAlertaVencimentoLicenca() {
        if (alertaJaDismissed || balloonAlerta == null || tvAlertaMensagem == null) {
            return;
        }

        new Thread(() -> {
            try {
                int diasRestantes = LicencaManager.getDiasParaVencimento(MainActivity.this);
                Log.d(TAG, "Dias restantes para vencimento da licenca: " + diasRestantes);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || alertaJaDismissed) return;

                    if (diasRestantes >= 0 && diasRestantes <= DIAS_ALERTA_LICENCA) {
                        // Montar mensagem de acordo com os dias restantes
                        String mensagem;
                        if (diasRestantes == 0) {
                            mensagem = "ATENCAO! A licenca do sistema vence HOJE!\n" +
                                       "Renove agora para evitar interrupcao no uso do sistema.";
                        } else if (diasRestantes == 1) {
                            mensagem = "ATENCAO! Falta apenas 1 dia para a licenca do sistema vencer.\n" +
                                       "Renove o mais rapido possivel para evitar interrupcao.";
                        } else {
                            mensagem = "ATENCAO! Faltam " + diasRestantes + " dias para a licenca do sistema vencer.\n" +
                                       "Entre em contato para renovar sua licenca.";
                        }
                        tvAlertaMensagem.setText(mensagem);

                        // Exibir o balao com animacao de fade in
                        balloonAlerta.setVisibility(View.VISIBLE);
                        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                        fadeIn.setDuration(500);
                        balloonAlerta.startAnimation(fadeIn);
                    } else {
                        // Licenca com mais de 5 dias ou sem licenca - esconder balao
                        balloonAlerta.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar alerta de vencimento de licenca", e);
            }
        }).start();
    }
}
