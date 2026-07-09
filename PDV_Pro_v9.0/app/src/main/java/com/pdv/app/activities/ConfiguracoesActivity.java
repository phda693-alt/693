package com.pdv.app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.pdv.app.R;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.LogoManager;
import com.pdv.app.utils.TaxaServicoPreferences;
import com.pdv.app.utils.UserActionLogger;

import java.io.InputStream;

/** Configuracoes gerais de cobranca do PDV. */
public class ConfiguracoesActivity extends BaseActivity {
    private CheckBox cbMesas;
    private CheckBox cbComandas;
    private CheckBox cbArmarios;
    private ImageView ivLogoPreview;

    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuracoes);
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CONFIG_GERAL_ACESSAR)) return;

        cbMesas = findViewById(R.id.cbTaxaMesas);
        cbComandas = findViewById(R.id.cbTaxaComandas);
        cbArmarios = findViewById(R.id.cbTaxaArmarios);
        Button btnSalvar = findViewById(R.id.btnSalvarConfiguracoes);
        ivLogoPreview = findViewById(R.id.ivLogoPreview);
        Button btnEscolherLogo = findViewById(R.id.btnEscolherLogo);
        Button btnRemoverLogo = findViewById(R.id.btnRemoverLogo);

        cbMesas.setChecked(TaxaServicoPreferences.cobrarMesas(this));
        cbComandas.setChecked(TaxaServicoPreferences.cobrarComandas(this));
        cbArmarios.setChecked(TaxaServicoPreferences.cobrarArmarios(this));

        // Carregar logo existente
        Bitmap logoAtual = LogoManager.carregarLogo(this);
        if (logoAtual != null) {
            ivLogoPreview.setImageBitmap(logoAtual);
        }

        // Registrar launcher para seleção de imagem
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            processarLogoSelecionado(uri);
                        }
                    }
                });

        btnSalvar.setOnClickListener(v -> salvar());

        btnEscolherLogo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        btnRemoverLogo.setOnClickListener(v -> {
            LogoManager.removerLogo(this);
            ivLogoPreview.setImageDrawable(null);
            ivLogoPreview.setBackgroundResource(R.drawable.input_bg);
            showToast("Logo removido com sucesso.");
        });
    }

    private void processarLogoSelecionado(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (bitmap != null) {
                // Redimensionar para tamanho adequado para cupom (max 384px de largura)
                Bitmap resized = LogoManager.redimensionarLogo(bitmap, 384);
                LogoManager.salvarLogo(this, resized);
                ivLogoPreview.setImageBitmap(resized);
                showToast("Logo salvo com sucesso! Sera exibido no topo do cupom.");
            }
        } catch (Exception e) {
            showError("Erro ao carregar imagem: " + e.getMessage());
        }
    }

    private void salvar() {
        TaxaServicoPreferences.salvar(this, cbMesas.isChecked(), cbComandas.isChecked(), cbArmarios.isChecked());
        UserActionLogger.log(this, "ALTERAR_CONFIGURACAO", "Configuracoes",
                "Taxa 10% - mesas=" + cbMesas.isChecked()
                        + ", comandas=" + cbComandas.isChecked()
                        + ", armarios=" + cbArmarios.isChecked());
        showToast("Configuracoes salvas com sucesso.");
    }
}
