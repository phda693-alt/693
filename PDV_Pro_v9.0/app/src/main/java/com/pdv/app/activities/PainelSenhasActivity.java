package com.pdv.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.pdv.app.R;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.permissions.PermissionManager;
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.SenhaChamadoStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Painel local para chamar e listar as senhas geradas no canhoto de senha.
 *
 * As senhas sao gravadas quando a venda e finalizada com a opcao
 * "Imprimir canhoto de senha" marcada.
 */
public class PainelSenhasActivity extends BaseActivity implements TextToSpeech.OnInitListener {
    private TextView tvSenhaAtual, tvClienteAtual, tvStatusSenhas, tvTotalSenhas;
    private ListView listSenhas;
    private Button btnChamarSelecionada, btnChamarProxima, btnLimpar, btnZerarSenhas, btnAtualizar;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> linhas = new ArrayList<>();
    private final ArrayList<SenhaChamadoStore.SenhaItem> itens = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private int selecionado = -1;
    private TextToSpeech tts;
    private boolean ttsPronto = false;

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            carregarSenhas(false);
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // v8.0.23.0 - Adicionada verificacao de permissao PAINEL_SENHAS_ACESSAR
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.PAINEL_SENHAS_ACESSAR)) { return; }
        setContentView(R.layout.activity_painel_senhas);

        tvSenhaAtual = findViewById(R.id.tvSenhaAtual);
        tvClienteAtual = findViewById(R.id.tvClienteAtual);
        tvStatusSenhas = findViewById(R.id.tvStatusSenhas);
        tvTotalSenhas = findViewById(R.id.tvTotalSenhas);
        listSenhas = findViewById(R.id.listSenhas);
        btnChamarSelecionada = findViewById(R.id.btnChamarSelecionada);
        btnChamarProxima = findViewById(R.id.btnChamarProxima);
        btnLimpar = findViewById(R.id.btnLimparSenhas);
        btnZerarSenhas = findViewById(R.id.btnZerarSenhasAtendimento);
        btnAtualizar = findViewById(R.id.btnAtualizarSenhas);

        adapter = new ArrayAdapter<>(this, R.layout.item_senha_painel, linhas);
        listSenhas.setAdapter(adapter);
        listSenhas.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        listSenhas.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            selecionado = position;
            listSenhas.setItemChecked(position, true);
            atualizarBotoes();
        });

        // v8.0.23.0 - Permissoes granulares aplicadas por botao
        PermissionManager pm = PermissionManager.getInstance(this);

        // Botao Chamar Senha Selecionada - requer PAINEL_SENHAS_CHAMAR
        if (pm.temPermissao(PermissionConstants.PAINEL_SENHAS_CHAMAR)) {
            btnChamarSelecionada.setOnClickListener(v -> chamarSelecionada());
            btnChamarProxima.setOnClickListener(v -> chamarProximaAguardando());
        } else {
            btnChamarSelecionada.setEnabled(false);
            btnChamarProxima.setEnabled(false);
        }

        btnAtualizar.setOnClickListener(v -> carregarSenhas(true));

        // Botao Limpar Painel - requer PAINEL_SENHAS_LIMPAR
        if (pm.temPermissao(PermissionConstants.PAINEL_SENHAS_LIMPAR)) {
            btnLimpar.setOnClickListener(v -> confirmarLimpeza());
            if (btnZerarSenhas != null) btnZerarSenhas.setOnClickListener(v -> confirmarZerarSenhasAtendimento());
        } else {
            btnLimpar.setEnabled(false);
            if (btnZerarSenhas != null) btnZerarSenhas.setEnabled(false);
        }

        tts = new TextToSpeech(this, this);
        carregarSenhas(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarSenhas(false);
        handler.postDelayed(autoRefresh, 3000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int r = tts.setLanguage(new Locale("pt", "BR"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setPitch(1.05f);
            tts.setSpeechRate(0.82f);
            ttsPronto = true;
        }
    }

    private void carregarSenhas(boolean mostrarToast) {
        int vendaSelecionada = getVendaSelecionada();
        itens.clear();
        itens.addAll(SenhaChamadoStore.listar(this));

        linhas.clear();
        int aguardando = 0;
        for (SenhaChamadoStore.SenhaItem item : itens) {
            if (!item.foiChamado()) aguardando++;
            linhas.add(formatarLinha(item));
        }
        adapter.notifyDataSetChanged();

        selecionado = encontrarIndiceVenda(vendaSelecionada);
        if (selecionado >= 0) listSenhas.setItemChecked(selecionado, true);

        tvTotalSenhas.setText("Total: " + itens.size() + " senha(s) • Aguardando: " + aguardando);
        tvStatusSenhas.setText(itens.isEmpty()
                ? "Nenhuma senha criada ainda. Finalize uma venda com o canhoto marcado."
                : "Atualizacao automatica ativa • toque em uma senha para chamar");

        atualizarBotoes();
        if (mostrarToast) showToast("Painel de senhas atualizado");
    }

    private String formatarLinha(SenhaChamadoStore.SenhaItem item) {
        String status = item.foiChamado() ? "CHAMADA" : "AGUARDANDO";
        String hora = item.foiChamado()
                ? "Chamou: " + SenhaChamadoStore.formatarHora(item.chamadoEm)
                : "Criada: " + SenhaChamadoStore.formatarHora(item.criadoEm);
        return "Senha " + SenhaChamadoStore.formatarSenhaNumero(item.senha) + "  •  " + status + "\n"
                + item.cliente + "  •  Venda #" + item.vendaId + "  •  R$ " + FormatUtils.formatMoney(item.total) + "\n"
                + hora;
    }

    private int getVendaSelecionada() {
        if (selecionado >= 0 && selecionado < itens.size()) return itens.get(selecionado).vendaId;
        return -1;
    }

    private int encontrarIndiceVenda(int vendaId) {
        if (vendaId <= 0) return -1;
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i).vendaId == vendaId) return i;
        }
        return -1;
    }

    private void atualizarBotoes() {
        boolean tem = !itens.isEmpty();
        boolean temSelecionada = selecionado >= 0 && selecionado < itens.size();
        btnChamarSelecionada.setEnabled(temSelecionada);
        btnChamarProxima.setEnabled(tem);
        btnLimpar.setEnabled(tem);
        if (btnZerarSenhas != null) btnZerarSenhas.setEnabled(true);
    }

    private void chamarSelecionada() {
        if (selecionado < 0 || selecionado >= itens.size()) {
            showToast("Selecione uma senha na lista");
            return;
        }
        chamarSenha(itens.get(selecionado));
    }

    private void chamarProximaAguardando() {
        for (SenhaChamadoStore.SenhaItem item : itens) {
            if (!item.foiChamado()) {
                chamarSenha(item);
                return;
            }
        }
        if (!itens.isEmpty()) chamarSenha(itens.get(0));
    }

    private void chamarSenha(SenhaChamadoStore.SenhaItem item) {
        if (item == null) return;
        SenhaChamadoStore.marcarChamado(this, item.vendaId);
        tvSenhaAtual.setText(SenhaChamadoStore.formatarSenhaNumero(item.senha));
        tvClienteAtual.setText(item.cliente + " • Venda #" + item.vendaId);
        falarSenha(item);
        carregarSenhas(false);
    }

    private void falarSenha(SenhaChamadoStore.SenhaItem item) {
        if (!ttsPronto || tts == null || item == null) return;
        String num = SenhaChamadoStore.formatarSenhaNumero(item.senha);
        StringBuilder digitos = new StringBuilder();
        for (int i = 0; i < num.length(); i++) {
            if (i > 0) digitos.append(", ");
            digitos.append(num.charAt(i));
        }
        String texto = "Atenção. Senha " + digitos + ". " + item.cliente + ", dirija-se ao atendimento.";
        tts.stop();
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "senha_" + item.senha);
    }

    private void confirmarZerarSenhasAtendimento() {
        new AlertDialog.Builder(this)
                .setTitle("Zerar senhas de atendimento")
                .setMessage("Deseja zerar o contador das senhas do canhoto e limpar o painel?\n\nA proxima venda com canhoto marcado voltara para a senha 001.\n\nEssa acao nao apaga vendas, produtos nem pagamentos.")
                .setPositiveButton("Zerar Senhas", (d, w) -> {
                    SenhaChamadoStore.zerarAtendimento(this);
                    tvSenhaAtual.setText("--");
                    tvClienteAtual.setText("Nenhuma senha chamada");
                    selecionado = -1;
                    carregarSenhas(false);
                    showToast("Senhas de atendimento zeradas. Proxima senha: 001");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarLimpeza() {
        new AlertDialog.Builder(this)
                .setTitle("Limpar painel de senhas")
                .setMessage("Deseja apagar todas as senhas listadas no painel?\n\nEssa acao nao apaga vendas, apenas limpa o painel de chamada.")
                .setPositiveButton("Limpar", (d, w) -> {
                    SenhaChamadoStore.limparTudo(this);
                    tvSenhaAtual.setText("--");
                    tvClienteAtual.setText("Nenhuma senha chamada");
                    selecionado = -1;
                    carregarSenhas(false);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
