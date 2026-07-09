package com.pdv.app.activities;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.pdv.app.R;
import com.pdv.app.utils.AnimUtils;

public class SobreActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sobre);

        ImageView ivLogo = findViewById(R.id.ivLogoSobre);
        TextView tvAppName = findViewById(R.id.tvAppNameSobre);
        TextView tvVersao = findViewById(R.id.tvVersao);
        LinearLayout cardSobre = findViewById(R.id.cardSobre);
        LinearLayout cardDesenvolvedor = findViewById(R.id.cardDesenvolvedor);
        LinearLayout layoutTelefone = findViewById(R.id.layoutTelefone);
        Button btnVoltar = findViewById(R.id.btnVoltar);

        // Exibir versão real do app dinamicamente
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (tvVersao != null) {
                tvVersao.setText("Versão " + pInfo.versionName + " — Sistema Comercial Completo");
            }
        } catch (Exception e) {
            if (tvVersao != null) {
                tvVersao.setText("Versão 9.0.0 — Sistema Comercial Completo");
            }
        }

        // Animações de entrada
        AnimUtils.scaleIn(ivLogo, 100);
        AnimUtils.fadeIn(tvAppName, 300);
        AnimUtils.fadeIn(tvVersao, 400);
        AnimUtils.slideUp(cardSobre, 500);
        AnimUtils.slideUp(cardDesenvolvedor, 650);
        AnimUtils.slideUp(btnVoltar, 800);

        // Clicar no telefone abre o discador
        if (layoutTelefone != null) {
            layoutTelefone.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:+5511999999999"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao abrir discador", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Botão voltar
        if (btnVoltar != null) {
            btnVoltar.setOnClickListener(v -> finish());
        }

        // Pulse no logo
        if (ivLogo != null) {
            ivLogo.postDelayed(() -> AnimUtils.pulseRepeat(ivLogo, 2), 800);
        }
    }
}
