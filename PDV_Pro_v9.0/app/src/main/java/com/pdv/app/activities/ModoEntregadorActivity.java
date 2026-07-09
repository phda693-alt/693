package com.pdv.app.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;
import com.pdv.app.services.EntregadorGpsService;
import com.pdv.app.utils.FormatUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity do Modo Entregador - v6.9.3 CORRIGIDA.
 *
 * Correcoes aplicadas:
 * 1. Recebe broadcasts do servico GPS para atualizar UI em tempo real
 * 2. Handler periodico que atualiza a localizacao a cada 10 segundos
 * 3. Exibe localizacao do cache local (SharedPreferences) como fallback
 * 4. Query corrigida com ORDER BY data_hora DESC para pegar a mais recente
 * 5. Solicita ACCESS_BACKGROUND_LOCATION para Android 10+
 * 6. Exibe informacoes detalhadas (precisao, velocidade, provider)
 * 7. Indicador visual de atualizacao em tempo real
 */
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;

public class ModoEntregadorActivity extends BaseActivity {
    private static final String TAG = "ModoEntregadorActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int BACKGROUND_PERMISSION_REQUEST_CODE = 1002;
    private static final long UI_UPDATE_INTERVAL_MS = 10000; // Atualizar UI a cada 10 segundos

    private Spinner spinnerEntregador;
    private Button btnAtivar, btnDesativar;
    private TextView tvStatus, tvUltimaLocalizacao;
    private LinearLayout layoutEntregas;
    private DatabaseHelper dbHelper;

    private List<EntregadorItem> entregadores = new ArrayList<>();
    private boolean isTrackingActive = false;
    private Handler uiUpdateHandler;
    private Runnable uiUpdateRunnable;

    // Receiver para atualizacoes de localizacao do servico GPS
    private BroadcastReceiver locationReceiver;

    private static class EntregadorItem {
        int id;
        String nome;
        EntregadorItem(int id, String nome) {
            this.id = id;
            this.nome = nome;
        }
        @Override
        public String toString() { return nome; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modo_entregador);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.MODO_ENTREGADOR_ACESSAR)) {
            return;
        }
        dbHelper = DatabaseHelper.getInstance(this);

        spinnerEntregador = findViewById(R.id.spinnerEntregador);
        btnAtivar = findViewById(R.id.btnAtivarGps);
        btnDesativar = findViewById(R.id.btnDesativarGps);
        tvStatus = findViewById(R.id.tvStatusGps);
        tvUltimaLocalizacao = findViewById(R.id.tvUltimaLocalizacao);
        layoutEntregas = findViewById(R.id.layoutEntregas);

        btnAtivar.setOnClickListener(v -> ativarRastreamento());
        btnDesativar.setOnClickListener(v -> desativarRastreamento());

        // Verificar estado atual
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        isTrackingActive = prefs.getBoolean("tracking_active", false);
        atualizarUI();

        carregarEntregadores();

        // Configurar handler de atualizacao periodica da UI
        uiUpdateHandler = new Handler(Looper.getMainLooper());
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isTrackingActive) {
                    atualizarLocalizacaoUI();
                }
                uiUpdateHandler.postDelayed(this, UI_UPDATE_INTERVAL_MS);
            }
        };

        // Configurar receiver de broadcast para atualizacoes em tempo real
        locationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (EntregadorGpsService.ACTION_LOCATION_UPDATE.equals(intent.getAction())) {
                    double lat = intent.getDoubleExtra(EntregadorGpsService.EXTRA_LATITUDE, 0);
                    double lng = intent.getDoubleExtra(EntregadorGpsService.EXTRA_LONGITUDE, 0);
                    float speed = intent.getFloatExtra(EntregadorGpsService.EXTRA_SPEED, 0);
                    float accuracy = intent.getFloatExtra(EntregadorGpsService.EXTRA_ACCURACY, 0);
                    long time = intent.getLongExtra(EntregadorGpsService.EXTRA_TIME, 0);

                    atualizarLocalizacaoNaTela(lat, lng, speed, accuracy, time);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        isTrackingActive = prefs.getBoolean("tracking_active", false);
        atualizarUI();
        carregarEntregasPendentes();

        // Registrar receiver para atualizacoes de localizacao
        IntentFilter filter = new IntentFilter(EntregadorGpsService.ACTION_LOCATION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationReceiver, filter);
        }

        // Iniciar atualizacao periodica da UI
        uiUpdateHandler.postDelayed(uiUpdateRunnable, UI_UPDATE_INTERVAL_MS);

        // Mostrar localizacao do cache imediatamente
        mostrarLocalizacaoDoCache();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Desregistrar receiver
        try {
            unregisterReceiver(locationReceiver);
        } catch (Exception e) {
            // Ignorar se nao estava registrado
        }
        // Parar atualizacao periodica
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
    }

    private void carregarEntregadores() {
        new Thread(() -> {
            try {
                Connection conn = dbHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, nome FROM entregadores WHERE ativo = 1 ORDER BY nome");
                ResultSet rs = ps.executeQuery();

                entregadores.clear();
                while (rs.next()) {
                    entregadores.add(new EntregadorItem(rs.getInt("id"), rs.getString("nome")));
                }
                rs.close();
                ps.close();

                runOnUiThread(() -> {
                    ArrayAdapter<EntregadorItem> adapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, entregadores);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerEntregador.setAdapter(adapter);

                    // Selecionar entregador salvo
                    SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
                    int savedId = prefs.getInt("entregador_id", -1);
                    for (int i = 0; i < entregadores.size(); i++) {
                        if (entregadores.get(i).id == savedId) {
                            spinnerEntregador.setSelection(i);
                            break;
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar entregadores", e);
                runOnUiThread(() -> showError("Erro ao carregar entregadores: " + e.getMessage()));
            }
        }).start();
    }

    private void ativarRastreamento() {
        if (entregadores.isEmpty()) {
            showError("Nenhum entregador cadastrado. Cadastre um entregador primeiro.");
            return;
        }

        EntregadorItem selected = (EntregadorItem) spinnerEntregador.getSelectedItem();
        if (selected == null) {
            showError("Selecione um entregador.");
            return;
        }

        // Verificar permissoes de localizacao
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        // Para Android 10+, solicitar permissao de localizacao em background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // Solicitar permissao de background separadamente (exigencia do Android 10+)
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    BACKGROUND_PERMISSION_REQUEST_CODE);
                // Continuar mesmo sem background - o servico funciona como foreground
            }
        }

        // Salvar entregador selecionado
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        prefs.edit().putInt("entregador_id", selected.id).apply();

        // Iniciar servico
        Intent serviceIntent = new Intent(this, EntregadorGpsService.class);
        serviceIntent.putExtra("entregador_id", selected.id);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        isTrackingActive = true;
        atualizarUI();
        showSuccess("Rastreamento GPS ativado para " + selected.nome);
    }

    private void desativarRastreamento() {
        Intent serviceIntent = new Intent(this, EntregadorGpsService.class);
        serviceIntent.setAction(EntregadorGpsService.ACTION_STOP_TRACKING);
        startService(serviceIntent);

        isTrackingActive = false;
        atualizarUI();
        showSuccess("Rastreamento GPS desativado");
    }

    private void atualizarUI() {
        if (isTrackingActive) {
            tvStatus.setText("Status: ATIVO - Rastreando...");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
            btnAtivar.setVisibility(View.GONE);
            btnDesativar.setVisibility(View.VISIBLE);
            spinnerEntregador.setEnabled(false);
        } else {
            tvStatus.setText("Status: INATIVO");
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorDanger));
            btnAtivar.setVisibility(View.VISIBLE);
            btnDesativar.setVisibility(View.GONE);
            spinnerEntregador.setEnabled(true);
        }

        // Carregar localizacao
        atualizarLocalizacaoUI();
    }

    /**
     * Mostra a localizacao salva no cache local (SharedPreferences).
     * Usado como fallback rapido enquanto o banco nao responde.
     */
    private void mostrarLocalizacaoDoCache() {
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        float lat = prefs.getFloat("last_lat", 0);
        float lng = prefs.getFloat("last_lng", 0);
        float speed = prefs.getFloat("last_speed", 0);
        float accuracy = prefs.getFloat("last_accuracy", 0);
        long time = prefs.getLong("last_time", 0);

        if (lat != 0 && lng != 0 && time > 0) {
            atualizarLocalizacaoNaTela(lat, lng, speed, accuracy, time);
        }
    }

    /**
     * Atualiza a localizacao exibida na tela com os dados fornecidos.
     * Chamado pelo broadcast receiver e pelo cache local.
     */
    private void atualizarLocalizacaoNaTela(double lat, double lng, float speed, float accuracy, long time) {
        if (isFinishing()) return;

        String timeStr;
        if (time > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            timeStr = sdf.format(new Date(time));
        } else {
            timeStr = "N/A";
        }

        // Converter velocidade de m/s para km/h
        float speedKmh = speed * 3.6f;

        String locationText = String.format(Locale.US,
            "Latitude: %.6f\nLongitude: %.6f\nVelocidade: %.1f km/h\nPrecisao: ±%.0f metros\nAtualizado: %s",
            lat, lng, speedKmh, accuracy, timeStr);

        tvUltimaLocalizacao.setText(locationText);
        tvUltimaLocalizacao.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
    }

    /**
     * Atualiza a localizacao na UI buscando do banco de dados.
     * Usado como atualizacao periodica complementar ao broadcast.
     */
    private void atualizarLocalizacaoUI() {
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        int entId = prefs.getInt("entregador_id", -1);
        if (entId <= 0) return;

        new Thread(() -> {
            try {
                Connection conn = dbHelper.getConnection();
                // CORRIGIDO: Adicionado ORDER BY data_hora DESC para pegar a mais recente
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT latitude, longitude, velocidade, precisao, data_hora, ativo "
                    + "FROM rastreamento_entregador "
                    + "WHERE entregador_id = ? "
                    + "ORDER BY data_hora DESC LIMIT 1");
                ps.setInt(1, entId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    double lat = rs.getDouble("latitude");
                    double lng = rs.getDouble("longitude");
                    float velocidade = rs.getFloat("velocidade");
                    float precisao = rs.getFloat("precisao");
                    String dataHora = rs.getString("data_hora");
                    boolean ativo = rs.getBoolean("ativo");

                    runOnUiThread(() -> {
                        if (lat != 0 && lng != 0) {
                            // Converter velocidade de m/s para km/h
                            float speedKmh = velocidade * 3.6f;

                            String locationText = String.format(Locale.US,
                                "Latitude: %.6f\nLongitude: %.6f\nVelocidade: %.1f km/h\nPrecisao: ±%.0f metros\nAtualizado: %s\nGPS: %s",
                                lat, lng, speedKmh, precisao, dataHora,
                                ativo ? "ATIVO" : "INATIVO");

                            tvUltimaLocalizacao.setText(locationText);
                            tvUltimaLocalizacao.setTextColor(
                                ContextCompat.getColor(this,
                                    ativo ? R.color.colorSuccess : R.color.text_secondary));
                        } else {
                            tvUltimaLocalizacao.setText("Aguardando primeira localizacao...\n(Certifique-se de que o GPS esta ativado)");
                            tvUltimaLocalizacao.setTextColor(
                                ContextCompat.getColor(this, R.color.accent_gold));
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        tvUltimaLocalizacao.setText("Sem dados de localizacao.\nAtive o rastreamento para comecar.");
                        tvUltimaLocalizacao.setTextColor(
                            ContextCompat.getColor(this, R.color.text_secondary));
                    });
                }
                rs.close();
                ps.close();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar localizacao do banco", e);
                // Fallback: mostrar do cache local
                runOnUiThread(this::mostrarLocalizacaoDoCache);
            }
        }).start();
    }

    private void carregarEntregasPendentes() {
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        int entId = prefs.getInt("entregador_id", -1);
        if (entId <= 0) return;

        new Thread(() -> {
            try {
                Connection conn = dbHelper.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT v.id, v.data_venda, v.total_liquido, v.status, v.observacao, "
                    + "c.nome as cliente_nome "
                    + "FROM vendas v LEFT JOIN clientes c ON v.cliente_id = c.id "
                    + "WHERE v.entregador_id = ? AND v.status IN ('pendente', 'finalizada', 'em_rota') "
                    + "ORDER BY v.data_venda DESC LIMIT 20");
                ps.setInt(1, entId);
                ResultSet rs = ps.executeQuery();

                StringBuilder sb = new StringBuilder();
                int count = 0;
                while (rs.next()) {
                    count++;
                    sb.append("Pedido #").append(rs.getInt("id"));
                    String cliente = rs.getString("cliente_nome");
                    if (cliente != null && !cliente.isEmpty()) {
                        sb.append(" - ").append(cliente);
                    }
                    sb.append("\nR$ ").append(FormatUtils.formatMoney(rs.getDouble("total_liquido")));
                    String status = rs.getString("status");
                    sb.append(" | ").append(status != null ? status.toUpperCase() : "N/A");
                    sb.append("\n\n");
                }
                rs.close();
                ps.close();

                final int totalCount = count;
                final String entregas = sb.toString();

                runOnUiThread(() -> {
                    TextView tvEntregas = findViewById(R.id.tvEntregasPendentes);
                    if (tvEntregas != null) {
                        if (totalCount > 0) {
                            tvEntregas.setText(entregas);
                        } else {
                            tvEntregas.setText("Nenhuma entrega pendente no momento.");
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar entregas", e);
            }
        }).start();
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        ActivityCompat.requestPermissions(this,
            permissions.toArray(new String[0]),
            PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ativarRastreamento();
            } else {
                showError("Permissao de localizacao necessaria para o rastreamento GPS.\n\n"
                    + "Va em Configuracoes > Aplicativos > PDV Pro > Permissoes > Localizacao e ative.");
            }
        } else if (requestCode == BACKGROUND_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSuccess("Permissao de localizacao em segundo plano concedida!");
            } else {
                showInfo("Aviso", "Sem permissao de localizacao em segundo plano, "
                    + "o rastreamento pode ser menos preciso quando o app estiver minimizado.\n\n"
                    + "Para melhor funcionamento, va em Configuracoes > Aplicativos > PDV Pro > "
                    + "Permissoes > Localizacao e selecione 'Permitir o tempo todo'.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uiUpdateHandler != null) {
            uiUpdateHandler.removeCallbacksAndMessages(null);
        }
    }
}
