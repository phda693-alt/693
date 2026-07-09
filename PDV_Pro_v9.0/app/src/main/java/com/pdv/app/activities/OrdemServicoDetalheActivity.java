package com.pdv.app.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.utils.FormatUtils;
import com.pdv.app.utils.OSPhotoSyncManager;
import com.pdv.app.utils.OrderPrintManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tela de visualizacao completa da Ordem de Servico.
 * Exibe todos os dados, servicos, produtos e todas as fotos cadastradas na OS.
 */
public class OrdemServicoDetalheActivity extends BaseActivity {
    private int osId;
    private TextView tvTitulo, tvResumo, tvDados, tvFotosStatus;
    private RecyclerView rvItens, rvFotos;
    private GenericAdapter<Map<String, Object>> itensAdapter;
    private GenericAdapter<OSPhotoSyncManager.OSPhoto> fotosAdapter;
    private Map<String, Object> osCompleta;
    private OSPhotoSyncManager photoSyncManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_os_detalhes);

        osId = getIntent().getIntExtra("os_id", 0);
        if (osId <= 0) {
            showError("Ordem de servico nao encontrada.");
            finish();
            return;
        }

        tvTitulo = findViewById(R.id.tvTituloOSDetalhe);
        tvResumo = findViewById(R.id.tvResumoOSDetalhe);
        tvDados = findViewById(R.id.tvDadosOSDetalhe);
        tvFotosStatus = findViewById(R.id.tvFotosStatusOSDetalhe);
        rvItens = findViewById(R.id.rvItensOSDetalhe);
        rvFotos = findViewById(R.id.rvFotosOSDetalhe);
        Button btnVoltar = findViewById(R.id.btnVoltarOSDetalhe);
        Button btnImprimir = findViewById(R.id.btnImprimirOSDetalhe);

        btnVoltar.setOnClickListener(v -> finish());
        btnImprimir.setOnClickListener(v -> imprimirOS());

        rvItens.setLayoutManager(new LinearLayoutManager(this));
        itensAdapter = new GenericAdapter<>(R.layout.item_os_item, (holder, item, pos) -> {
            holder.setText(R.id.tvItemNome, safe(item.get("tipo")).toUpperCase(Locale.ROOT) + " - " + safe(item.get("descricao")));
            double qtd = number(item.get("quantidade"));
            double preco = number(item.get("preco_unitario"));
            double total = number(item.get("total"));
            String usuario = safe(item.get("usuario_nome"));
            String det = String.format(Locale.US, "%.3f x R$ %.2f = R$ %.2f", qtd, preco, total);
            if (!usuario.isEmpty()) det += " | Usuario: " + usuario;
            holder.setText(R.id.tvItemDetalhe, det);
            Button remover = holder.find(R.id.btnRemoverItem);
            if (remover != null) remover.setVisibility(View.GONE);
        });
        rvItens.setAdapter(itensAdapter);

        rvFotos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoSyncManager = new OSPhotoSyncManager(this);
        fotosAdapter = new GenericAdapter<>(R.layout.item_foto, (holder, foto, pos) -> {
            ImageView iv = holder.find(R.id.ivFoto);
            Button remover = holder.find(R.id.btnRemoverFoto);
            TextView tvStatus = holder.find(R.id.tvFotoStatus);
            if (remover != null) remover.setVisibility(View.GONE);
            if (iv != null) {
                Bitmap bitmap = photoSyncManager.loadBitmap(foto);
                if (bitmap != null) {
                    iv.setImageBitmap(bitmap);
                    iv.setOnClickListener(v -> abrirFotoGrande(foto));
                } else {
                    iv.setImageResource(R.drawable.ic_image_placeholder);
                    iv.setOnClickListener(v -> abrirFotoGrande(foto));
                }
            }
            if (tvStatus != null) {
                String origem = photoSyncManager.getDisplaySource(foto);
                tvStatus.setText(origem);
                if ("LOCAL".equals(origem)) {
                    tvStatus.setTextColor(getResources().getColor(R.color.accent_cyan));
                } else if ("FTP".equals(origem)) {
                    tvStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
                } else {
                    tvStatus.setTextColor(getResources().getColor(R.color.colorWarning));
                }
            }
        });
        rvFotos.setAdapter(fotosAdapter);

        carregarDetalhes();
    }

    private void carregarDetalhes() {
        showLoading("Carregando ordem de servico...");
        new Thread(() -> {
            try {
                DatabaseHelper db = DatabaseHelper.getInstance(this);
                Connection conn = db.getConnection();

                PreparedStatement ps = conn.prepareStatement(
                        "SELECT os.*, c.nome AS cliente_cadastrado_nome, " +
                                "(SELECT COALESCE(SUM(oi.total),0) FROM os_itens oi WHERE oi.os_id = os.id) AS total_itens " +
                                "FROM ordens_servico os " +
                                "LEFT JOIN clientes c ON os.cliente_id = c.id " +
                                "WHERE os.id = ? LIMIT 1");
                ps.setInt(1, osId);
                ResultSet rs = ps.executeQuery();
                Map<String, Object> os = new LinkedHashMap<>();
                if (rs.next()) {
                    os.put("id", rs.getInt("id"));
                    os.put("numero", rs.getInt("numero"));
                    os.put("cliente_nome", rs.getString("cliente_nome"));
                    os.put("cliente_cadastrado_nome", rs.getString("cliente_cadastrado_nome"));
                    os.put("equipamento", rs.getString("equipamento"));
                    os.put("equipamento_detalhado", getStr(rs, "equipamento_detalhado"));
                    os.put("defeito_relatado", rs.getString("defeito_relatado"));
                    os.put("defeitos", getStr(rs, "defeitos"));
                    os.put("solucoes", getStr(rs, "solucoes"));
                    os.put("status", rs.getString("status"));
                    os.put("valor_servico", rs.getDouble("valor_servico"));
                    os.put("total_itens", rs.getDouble("total_itens"));
                    os.put("desconto_valor", getDouble(rs, "desconto_valor"));
                    os.put("desconto_percentual", getDouble(rs, "desconto_percentual"));
                    os.put("observacao", rs.getString("observacao"));
                    os.put("data_abertura", getStr(rs, "data_abertura"));
                    os.put("data_atualizacao", getStr(rs, "data_atualizacao"));
                    os.put("usuario_abertura_nome", getStr(rs, "usuario_abertura_nome"));
                    os.put("usuario_fechamento_nome", getStr(rs, "usuario_fechamento_nome"));
                }
                rs.close();
                ps.close();

                List<Map<String, Object>> itens = new ArrayList<>();
                PreparedStatement pi = conn.prepareStatement("SELECT * FROM os_itens WHERE os_id = ? ORDER BY id ASC");
                pi.setInt(1, osId);
                ResultSet ri = pi.executeQuery();
                while (ri.next()) {
                    Map<String, Object> it = new LinkedHashMap<>();
                    it.put("tipo", ri.getString("tipo"));
                    it.put("descricao", ri.getString("descricao"));
                    it.put("quantidade", ri.getDouble("quantidade"));
                    it.put("preco_unitario", ri.getDouble("preco_unitario"));
                    it.put("total", ri.getDouble("total"));
                    it.put("usuario_nome", getStr(ri, "usuario_nome"));
                    itens.add(it);
                }
                ri.close();
                pi.close();

                List<OSPhotoSyncManager.OSPhoto> fotos = carregarFotosDaOS(conn);

                os.put("itens", itens);
                os.put("fotos", fotos);
                osCompleta = os;

                hideLoading();
                runOnUiThread(() -> {
                    preencherTela(os, itens, fotos);
                    resolverFotosFtpEmSegundoPlano(fotos);
                });
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao carregar OS: " + e.getMessage());
            }
        }).start();
    }

    private List<OSPhotoSyncManager.OSPhoto> carregarFotosDaOS(Connection conn) throws Exception {
        List<OSPhotoSyncManager.OSPhoto> fotos = new ArrayList<>();
        try {
            PreparedStatement pf = conn.prepareStatement(
                    "SELECT id, caminho_foto, ftp_caminho, ftp_status, usuario_nome FROM os_fotos WHERE os_id = ? ORDER BY id ASC");
            pf.setInt(1, osId);
            ResultSet rf = pf.executeQuery();
            while (rf.next()) {
                fotos.add(new OSPhotoSyncManager.OSPhoto(
                        rf.getInt("id"),
                        osId,
                        rf.getString("caminho_foto"),
                        rf.getString("ftp_caminho"),
                        rf.getString("ftp_status"),
                        rf.getString("usuario_nome")));
            }
            rf.close();
            pf.close();
        } catch (Exception e) {
            PreparedStatement pf = conn.prepareStatement("SELECT id, caminho_foto, usuario_nome FROM os_fotos WHERE os_id = ? ORDER BY id ASC");
            pf.setInt(1, osId);
            ResultSet rf = pf.executeQuery();
            while (rf.next()) {
                fotos.add(new OSPhotoSyncManager.OSPhoto(
                        rf.getInt("id"),
                        osId,
                        rf.getString("caminho_foto"),
                        null,
                        OSPhotoSyncManager.STATUS_PENDING,
                        getStr(rf, "usuario_nome")));
            }
            rf.close();
            pf.close();
        }
        return fotos;
    }

    private void preencherTela(Map<String, Object> os, List<Map<String, Object>> itens, List<OSPhotoSyncManager.OSPhoto> fotos) {
        int numero = ((Number) os.get("numero")).intValue();
        String cliente = safe(os.get("cliente_cadastrado_nome"));
        if (cliente.isEmpty()) cliente = safe(os.get("cliente_nome"));
        double totalBruto = number(os.get("total_itens"));
        if (totalBruto <= 0) totalBruto = number(os.get("valor_servico"));
        double descValor = number(os.get("desconto_valor"));
        double descPercent = number(os.get("desconto_percentual"));
        double desconto = descPercent > 0 ? (totalBruto * descPercent / 100.0) : descValor;
        double totalFinal = Math.max(0, totalBruto - desconto);

        tvTitulo.setText("OS #" + numero);
        tvResumo.setText("Cliente: " + cliente + "\n" +
                "Status: " + safe(os.get("status")) + "\n" +
                "Equipamento: " + safe(os.get("equipamento")) + "\n" +
                "Total: R$ " + FormatUtils.formatMoney(totalFinal) + "\n" +
                "Fotos: " + fotos.size() + " | Itens: " + itens.size());

        atualizarResumoFotos(fotos);

        tvDados.setText("Data abertura: " + FormatUtils.formatDate(safe(os.get("data_abertura"))) + "\n" +
                "Atualizacao: " + FormatUtils.formatDate(safe(os.get("data_atualizacao"))) + "\n" +
                "Aberta por: " + safe(os.get("usuario_abertura_nome")) + "\n" +
                "Fechada por: " + safe(os.get("usuario_fechamento_nome")) + "\n\n" +
                "Equipamento detalhado:\n" + safe(os.get("equipamento_detalhado")) + "\n\n" +
                "Defeito relatado:\n" + safe(os.get("defeito_relatado")) + "\n\n" +
                "Defeitos detalhados:\n" + safe(os.get("defeitos")) + "\n\n" +
                "Solucoes aplicadas:\n" + safe(os.get("solucoes")) + "\n\n" +
                "Observacao:\n" + safe(os.get("observacao")));

        itensAdapter.setItems(itens);
        fotosAdapter.setItems(fotos);
    }

    private void atualizarResumoFotos(List<OSPhotoSyncManager.OSPhoto> fotos) {
        int locais = 0;
        int ftp = 0;
        int pendentes = 0;
        for (OSPhotoSyncManager.OSPhoto foto : fotos) {
            String origem = photoSyncManager.getDisplaySource(foto);
            if ("LOCAL".equals(origem)) locais++;
            else if ("FTP".equals(origem)) ftp++;
            else pendentes++;
        }
        if (tvFotosStatus != null) {
            tvFotosStatus.setText("Local: " + locais + "  |  FTP: " + ftp + "  |  Pendentes: " + pendentes);
        }
    }

    private void resolverFotosFtpEmSegundoPlano(List<OSPhotoSyncManager.OSPhoto> fotos) {
        if (fotos == null || fotos.isEmpty()) return;
        boolean precisaFtp = false;
        for (OSPhotoSyncManager.OSPhoto foto : fotos) {
            if (!photoSyncManager.hasLocalFile(foto.localPath)
                    && foto.ftpPath != null && !foto.ftpPath.trim().isEmpty()) {
                precisaFtp = true;
                break;
            }
        }
        if (!precisaFtp) return;

        new Thread(() -> {
            boolean atualizou = false;
            for (OSPhotoSyncManager.OSPhoto foto : fotos) {
                if (photoSyncManager.hasLocalFile(foto.localPath)
                        || foto.ftpPath == null || foto.ftpPath.trim().isEmpty()) {
                    continue;
                }
                try {
                    String local = photoSyncManager.ensureLocalCopy(foto);
                    if (local != null) {
                        atualizou = true;
                        atualizarCaminhoLocalFoto(foto.id, local);
                    }
                } catch (Exception ignored) {
                }
            }
            if (atualizou) {
                runOnUiThread(() -> {
                    fotosAdapter.setItems(new ArrayList<>(fotos));
                    atualizarResumoFotos(fotos);
                    showToast("Fotos recuperadas do FTP.");
                });
            }
        }, "OS-Photo-FTP-Resolver").start();
    }

    private void atualizarCaminhoLocalFoto(int fotoId, String localPath) {
        try {
            Connection conn = DatabaseHelper.getInstance(this).getConnection();
            PreparedStatement ps = conn.prepareStatement("UPDATE os_fotos SET caminho_foto = ? WHERE id = ?");
            ps.setString(1, localPath);
            ps.setInt(2, fotoId);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ignored) {
        }
    }

    private void imprimirOS() {
        if (osCompleta == null) {
            showToast("Aguarde carregar a OS.");
            return;
        }
        showLoading("Imprimindo ordem de servico...");
        new Thread(() -> {
            try {
                boolean ok = new OrderPrintManager(this).imprimirOS(osCompleta);
                hideLoading();
                if (ok) showToast("Ordem de servico enviada para impressao!");
                else showError("Falha ao imprimir a ordem de servico.");
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao imprimir: " + e.getMessage());
            }
        }).start();
    }

    private void abrirFotoGrande(OSPhotoSyncManager.OSPhoto foto) {
        Bitmap bitmap = photoSyncManager.loadBitmap(foto);
        if (bitmap != null) {
            exibirFotoGrande(bitmap, photoSyncManager.getDisplaySource(foto));
            return;
        }

        if (foto.ftpPath == null || foto.ftpPath.trim().isEmpty()) {
            showError("Esta foto nao esta disponivel localmente e ainda nao possui copia FTP.");
            return;
        }

        showLoading("Baixando foto do FTP...");
        new Thread(() -> {
            try {
                String local = photoSyncManager.ensureLocalCopy(foto);
                Bitmap baixada = photoSyncManager.loadBitmap(foto);
                if (local != null) atualizarCaminhoLocalFoto(foto.id, local);
                hideLoading();
                if (baixada != null) {
                    runOnUiThread(() -> {
                        fotosAdapter.notifyDataSetChanged();
                        atualizarResumoFotos((List<OSPhotoSyncManager.OSPhoto>) osCompleta.get("fotos"));
                        exibirFotoGrande(baixada, "FTP");
                    });
                } else {
                    showError("Nao foi possivel abrir a foto do FTP.");
                }
            } catch (Exception e) {
                hideLoading();
                showError("Erro ao baixar foto do FTP: " + e.getMessage());
            }
        }, "OS-Photo-Fullscreen").start();
    }

    private void exibirFotoGrande(Bitmap bitmap, String origem) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setPadding(12, 12, 12, 12);
        image.setImageBitmap(bitmap);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Foto da OS - " + origem)
                .setView(image)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private String safe(Object o) { return o == null ? "" : o.toString(); }
    private double number(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    private String getStr(ResultSet rs, String col) { try { return rs.getString(col); } catch (Exception e) { return ""; } }
    private double getDouble(ResultSet rs, String col) { try { return rs.getDouble(col); } catch (Exception e) { return 0; } }
}
