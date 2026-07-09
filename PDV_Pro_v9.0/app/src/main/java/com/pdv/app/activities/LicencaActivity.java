package com.pdv.app.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.LicencaManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import com.pdv.app.utils.ErrorHandler;

public class LicencaActivity extends BaseActivity {
    private TextView tvContraChave;
    private EditText etChaveAtivacao, etDataExpiracao;
    private Button btnAtivar, btnLimparLicenca, btnCalendario;

    // Componentes do card de status da licenca
    private LinearLayout cardStatusLicenca;
    private TextView tvStatusLicencaTitulo;
    private View viewStatusIndicador;
    private TextView tvStatusLicenca;
    private TextView tvDataVencimento;
    private LinearLayout layoutDiasRestantes;
    private TextView tvDiasRestantes;

    private static final String SENHA_LIMPEZA = "2394";

    private Calendar calendar;
    private SimpleDateFormat sdfDisplay;
    private SimpleDateFormat sdfInterno;

    // Flag para evitar loop na mascara de data
    private boolean isUpdating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_licenca);

        // Inicializar formatadores de data (igual ao Gerador de Licencas)
        calendar = Calendar.getInstance();
        sdfDisplay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sdfInterno = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        tvContraChave = findViewById(R.id.tvContraChave);
        etChaveAtivacao = findViewById(R.id.etChaveAtivacao);
        etDataExpiracao = findViewById(R.id.etDataExpiracao);
        btnAtivar = findViewById(R.id.btnAtivar);
        btnLimparLicenca = findViewById(R.id.btnLimparLicenca);
        btnCalendario = findViewById(R.id.btnCalendario);

        // Inicializar componentes do card de status
        cardStatusLicenca = findViewById(R.id.cardStatusLicenca);
        tvStatusLicencaTitulo = findViewById(R.id.tvStatusLicencaTitulo);
        viewStatusIndicador = findViewById(R.id.viewStatusIndicador);
        tvStatusLicenca = findViewById(R.id.tvStatusLicenca);
        tvDataVencimento = findViewById(R.id.tvDataVencimento);
        layoutDiasRestantes = findViewById(R.id.layoutDiasRestantes);
        tvDiasRestantes = findViewById(R.id.tvDiasRestantes);

        String contraChave = LicencaManager.gerarContraChave(this);
        tvContraChave.setText("Contra-Chave: " + contraChave);

        // Adicionar mascara de data dd/MM/yyyy no campo de data
        adicionarMascaraData(etDataExpiracao);

        // Carregar e exibir status da licenca com data de vencimento
        carregarStatusLicenca();

        AnimUtils.slideUp(cardStatusLicenca, 50);
        AnimUtils.slideUp(tvContraChave, 150);
        AnimUtils.slideUp(etChaveAtivacao, 250);
        AnimUtils.slideUp(etDataExpiracao, 350);
        AnimUtils.slideUp(btnAtivar, 450);
        AnimUtils.slideUp(btnLimparLicenca, 550);

        btnAtivar.setOnClickListener(v -> ativarLicenca(contraChave));
        btnLimparLicenca.setOnClickListener(v -> solicitarSenhaLimpeza());
        btnCalendario.setOnClickListener(v -> showDatePicker());
    }

    /**
     * Adiciona mascara automatica dd/MM/yyyy ao campo de data.
     * Insere as barras automaticamente conforme o usuario digita.
     */
    private void adicionarMascaraData(final EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private String current = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) {
                    return;
                }

                String str = s.toString();
                if (str.equals(current)) {
                    return;
                }

                // Remover tudo que nao e digito
                String clean = str.replaceAll("[^\\d]", "");

                // Limitar a 8 digitos (ddMMyyyy)
                if (clean.length() > 8) {
                    clean = clean.substring(0, 8);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i == 2 || i == 4) {
                        formatted.append("/");
                    }
                    formatted.append(clean.charAt(i));
                }

                current = formatted.toString();
                isUpdating = true;
                editText.setText(current);
                editText.setSelection(current.length());
                isUpdating = false;
            }
        });
    }

    /**
     * Exibe o DatePickerDialog para selecionar a data (igual ao Gerador de Licencas).
     */
    private void showDatePicker() {
        // Tentar usar a data atual do campo, se valida
        Calendar cal = Calendar.getInstance();
        String dataAtual = etDataExpiracao.getText().toString().trim();
        if (dataAtual.matches("\\d{2}/\\d{2}/\\d{4}")) {
            try {
                sdfDisplay.setLenient(false);
                Date d = sdfDisplay.parse(dataAtual);
                if (d != null) {
                    cal.setTime(d);
                }
            } catch (Exception e) {
                // Usar data atual como fallback
            }
        }

        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    isUpdating = true;
                    etDataExpiracao.setText(sdfDisplay.format(calendar.getTime()));
                    isUpdating = false;
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));
        dpd.getDatePicker().setMinDate(System.currentTimeMillis());
        dpd.show();
    }

    /**
     * Carrega e exibe o status atual da licenca, incluindo data de vencimento e dias restantes.
     */
    private void carregarStatusLicenca() {
        new Thread(() -> {
            try {
                String dataExpiracao = LicencaManager.getDataExpiracao(LicencaActivity.this);
                boolean licencaValida = LicencaManager.verificarLicenca(LicencaActivity.this);
                boolean licencaExpirada = LicencaManager.isLicencaExpirada(LicencaActivity.this);

                runOnUiThread(() -> {
                    try {
                        exibirStatusLicenca(dataExpiracao, licencaValida, licencaExpirada);
                    } catch (Exception e) {
                        // Em caso de erro, ainda mostra o card com status desconhecido
                        exibirStatusSemLicenca();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> exibirStatusSemLicenca());
            }
        }).start();
    }

    /**
     * Exibe o status da licenca no card informativo.
     */
    private void exibirStatusLicenca(String dataExpiracao, boolean licencaValida, boolean licencaExpirada) {
        cardStatusLicenca.setVisibility(View.VISIBLE);

        if (dataExpiracao != null && !dataExpiracao.isEmpty()) {
            // Formatar a data para DD/MM/AAAA
            String dataFormatada = formatarDataBR(dataExpiracao);
            tvDataVencimento.setText(dataFormatada);

            if (licencaValida) {
                // Licenca ativa
                tvStatusLicenca.setText("Licenca Ativa");
                tvStatusLicenca.setTextColor(getResources().getColor(R.color.colorSuccess));
                tvDataVencimento.setTextColor(getResources().getColor(R.color.colorSuccess));
                viewStatusIndicador.setBackgroundResource(R.drawable.status_open);
                cardStatusLicenca.setBackgroundResource(R.drawable.card_licenca_ativa);

                // Calcular e exibir dias restantes
                long diasRestantes = calcularDiasRestantes(dataExpiracao);
                if (diasRestantes >= 0) {
                    layoutDiasRestantes.setVisibility(View.VISIBLE);
                    tvDiasRestantes.setText(String.valueOf(diasRestantes));
                    if (diasRestantes <= 7) {
                        // Alerta amarelo quando faltam 7 dias ou menos
                        tvDiasRestantes.setTextColor(getResources().getColor(R.color.colorWarning));
                    } else if (diasRestantes <= 30) {
                        // Alerta laranja quando faltam 30 dias ou menos
                        tvDiasRestantes.setTextColor(getResources().getColor(R.color.colorGold));
                    } else {
                        tvDiasRestantes.setTextColor(getResources().getColor(R.color.colorSuccess));
                    }
                }
            } else if (licencaExpirada) {
                // Licenca expirada
                tvStatusLicenca.setText("Licenca Vencida");
                tvStatusLicenca.setTextColor(getResources().getColor(R.color.colorDanger));
                tvDataVencimento.setTextColor(getResources().getColor(R.color.colorDanger));
                viewStatusIndicador.setBackgroundResource(R.drawable.status_closed);
                cardStatusLicenca.setBackgroundResource(R.drawable.card_licenca_expirada);

                // Mostrar dias vencidos
                long diasVencidos = calcularDiasRestantes(dataExpiracao);
                if (diasVencidos < 0) {
                    layoutDiasRestantes.setVisibility(View.VISIBLE);
                    tvDiasRestantes.setText("Vencida ha " + Math.abs(diasVencidos) + " dia(s)");
                    tvDiasRestantes.setTextColor(getResources().getColor(R.color.colorDanger));
                }
            } else {
                // Licenca invalida (chave incorreta)
                tvStatusLicenca.setText("Licenca Invalida");
                tvStatusLicenca.setTextColor(getResources().getColor(R.color.colorDanger));
                tvDataVencimento.setTextColor(getResources().getColor(R.color.colorWarning));
                viewStatusIndicador.setBackgroundResource(R.drawable.status_closed);
                cardStatusLicenca.setBackgroundResource(R.drawable.card_licenca_expirada);
                layoutDiasRestantes.setVisibility(View.GONE);
            }
        } else {
            // Sem licenca cadastrada
            exibirStatusSemLicenca();
        }
    }

    /**
     * Exibe o card com status "Sem licenca cadastrada".
     */
    private void exibirStatusSemLicenca() {
        cardStatusLicenca.setVisibility(View.VISIBLE);
        tvStatusLicenca.setText("Sem licenca cadastrada");
        tvStatusLicenca.setTextColor(getResources().getColor(R.color.colorWarning));
        tvDataVencimento.setText("--/--/----");
        tvDataVencimento.setTextColor(getResources().getColor(R.color.colorWarning));
        viewStatusIndicador.setBackgroundResource(R.drawable.status_closed);
        cardStatusLicenca.setBackgroundResource(R.drawable.card_licenca_sem);
        layoutDiasRestantes.setVisibility(View.GONE);
    }

    /**
     * Formata uma data de yyyy-MM-dd para DD/MM/AAAA.
     */
    private String formatarDataBR(String dataISO) {
        try {
            SimpleDateFormat from = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat to = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date d = from.parse(dataISO);
            if (d != null) {
                return to.format(d);
            }
        } catch (Exception e) {
            // Se falhar a formatacao, retorna a data original
        }
        return dataISO;
    }

    /**
     * Calcula a quantidade de dias restantes ate a data de expiracao.
     * Retorna valor positivo se ainda nao venceu, negativo se ja venceu.
     */
    private long calcularDiasRestantes(String dataExpiracao) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date expDate = sdf.parse(dataExpiracao);
            Date hoje = new Date();
            String hojeStr = sdf.format(hoje);
            Date hojeNormalizada = sdf.parse(hojeStr);

            if (expDate != null && hojeNormalizada != null) {
                long diffMs = expDate.getTime() - hojeNormalizada.getTime();
                return TimeUnit.DAYS.convert(diffMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            // Erro ao calcular dias
        }
        return 0;
    }

    private void solicitarSenhaLimpeza() {
        // Criar EditText para entrada da senha
        final EditText etSenha = new EditText(this);
        etSenha.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etSenha.setHint("Digite a senha de confirmacao");

        // Criar layout com padding para o EditText
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int paddingDp = 24;
        float density = getResources().getDisplayMetrics().density;
        int paddingPx = (int) (paddingDp * density);
        container.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        container.addView(etSenha);

        new AlertDialog.Builder(this)
                .setTitle("Limpeza Total de Licenca")
                .setMessage("ATENCAO: Esta acao ira remover TODOS os registros da tabela de licenca (banco de dados e cache local).\n\nDigite a senha de confirmacao para continuar:")
                .setView(container)
                .setPositiveButton("Confirmar", (dialog, which) -> {
                    String senhaDigitada = etSenha.getText().toString().trim();
                    if (SENHA_LIMPEZA.equals(senhaDigitada)) {
                        executarLimpezaLicenca();
                    } else {
                        showError("A senha informada esta incorreta.\n\nA operacao de limpeza da licenca nao foi realizada por seguranca.");
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void executarLimpezaLicenca() {
        showLoading("Limpando tabela de licenca...");

        new Thread(() -> {
            try {
                boolean success = LicencaManager.limparTabelaLicenca(this);
                hideLoading();
                if (success) {
                    runOnUiThread(() -> {
                        showSuccess("Tabela de licenca limpa com sucesso!\n\nTodos os registros de licenca foram removidos do banco de dados e do cache local.");
                        // Limpar campos da tela
                        etChaveAtivacao.setText("");
                        etDataExpiracao.setText("");
                        // Atualizar o card de status para refletir que nao ha licenca
                        exibirStatusSemLicenca();
                    });
                } else {
                    showError("Nao foi possivel limpar os dados da licenca.\n\nVerifique sua conexao com o servidor e tente novamente.");
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LICENCA);
            }
        }).start();
    }

    private void ativarLicenca(String contraChave) {
        String chave = etChaveAtivacao.getText().toString().trim();
        String dataExp = etDataExpiracao.getText().toString().trim();

        if (chave.isEmpty() || dataExp.isEmpty()) {
            showError("Por favor, preencha a chave de ativacao e a data de expiracao.\n\nFormato da data: DD/MM/AAAA (exemplo: 31/12/2026)");
            return;
        }

        // Validar formato da data antes de prosseguir (dd/MM/yyyy)
        if (!dataExp.matches("\\d{2}/\\d{2}/\\d{4}")) {
            showError("O formato da data esta incorreto.\n\nUse o formato: DD/MM/AAAA\nExemplo: 31/12/2026");
            return;
        }

        // Converter de dd/MM/yyyy para yyyy-MM-dd para uso interno
        final String dataExpInterno;
        try {
            SimpleDateFormat sdfInput = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            sdfInput.setLenient(false);
            SimpleDateFormat sdfInt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date dataExpiracaoParsed = sdfInput.parse(dataExp);
            dataExpInterno = sdfInt.format(dataExpiracaoParsed);

            Date hoje = new Date();
            String hojeStr = sdfInput.format(hoje);
            Date hojeNormalizada = sdfInput.parse(hojeStr);

            if (dataExpiracaoParsed == null || dataExpiracaoParsed.before(hojeNormalizada)) {
                showError("Data de expiracao invalida!\n\nA data informada (" + dataExp + ") e anterior a data atual (" + hojeStr + ").\n\nA licenca deve ter uma data de expiracao igual ou posterior ao dia de hoje.");
                return;
            }
        } catch (Exception e) {
            showError("Data de expiracao invalida. Verifique se a data e valida no formato DD/MM/AAAA.");
            return;
        }

        showLoading("Validando licenca...");

        new Thread(() -> {
            try {
                // ativarLicenca agora salva tanto no banco quanto no cache local
                boolean success = LicencaManager.ativarLicenca(this, contraChave, chave, dataExpInterno);
                hideLoading();
                if (success) {
                    runOnUiThread(() -> {
                        showToast("Licenca ativada com sucesso!");
                        startActivity(new Intent(this, MainActivity.class));
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                        finish();
                    });
                } else {
                    showError("A chave de ativacao informada e invalida ou esta expirada.\n\nVerifique se a chave e a data de expiracao estao corretas e tente novamente.");
                }
            } catch (Exception e) {
                hideLoading();
                showErrorFromException(e, ErrorHandler.CTX_LICENCA);
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        // Check if license is valid, if not, don't allow going back
        new Thread(() -> {
            boolean valid = LicencaManager.verificarLicenca(this);
            if (valid) {
                runOnUiThread(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } else {
                showToast("Ative a licenca para continuar");
            }
        }).start();
    }
}
