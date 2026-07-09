package com.pdv.app.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.pdv.app.R;
import com.pdv.app.database.DatabaseHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servico de rastreamento GPS do entregador - v6.9.3 CORRIGIDO.
 * 
 * Correcoes aplicadas:
 * 1. Usa AMBOS os providers (GPS + Network) simultaneamente para melhor cobertura
 * 2. Seleciona a melhor localizacao entre os providers
 * 3. Garante UNIQUE KEY no entregador_id para ON DUPLICATE KEY UPDATE funcionar
 * 4. Implementa fila de localizacoes pendentes para retry em caso de falha no banco
 * 5. Usa WakeLock para manter o servico ativo em background
 * 6. Handler periodico como fallback para forcar atualizacao mesmo sem movimento
 * 7. Salva ultima localizacao em SharedPreferences como cache local
 * 8. Broadcast local para atualizar a UI da ModoEntregadorActivity em tempo real
 * 9. Tratamento robusto de erros de conexao com banco
 * 10. Intervalo de atualizacao reduzido para 10 segundos
 */
public class EntregadorGpsService extends Service implements LocationListener {
    private static final String TAG = "EntregadorGpsService";
    private static final String CHANNEL_ID = "entregador_gps_channel";
    private static final int NOTIFICATION_ID = 2001;
    private static final long MIN_TIME_MS = 10000; // 10 segundos (era 15s)
    private static final float MIN_DISTANCE_M = 5f; // 5 metros (era 10m)
    private static final long FORCE_UPDATE_INTERVAL_MS = 30000; // Forcar atualizacao a cada 30s
    private static final int MAX_PENDING_LOCATIONS = 50; // Max localizacoes em fila

    public static final String ACTION_STOP_TRACKING = "STOP_TRACKING";
    public static final String ACTION_LOCATION_UPDATE = "com.pdv.app.LOCATION_UPDATE";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_ACCURACY = "accuracy";
    public static final String EXTRA_TIME = "time";

    private LocationManager locationManager;
    private DatabaseHelper dbHelper;
    private int entregadorId = -1;
    private boolean isTracking = false;
    private PowerManager.WakeLock wakeLock;
    private Handler forceUpdateHandler;
    private ExecutorService dbExecutor;

    // Melhor localizacao atual
    private Location bestLocation = null;
    private long lastDbSaveTime = 0;

    // Fila de localizacoes pendentes (quando o banco falha)
    private final List<PendingLocation> pendingLocations = new ArrayList<>();

    private static class PendingLocation {
        double lat, lng;
        float speed, accuracy;
        long timestamp;
        PendingLocation(double lat, double lng, float speed, float accuracy) {
            this.lat = lat;
            this.lng = lng;
            this.speed = speed;
            this.accuracy = accuracy;
            this.timestamp = System.currentTimeMillis();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = DatabaseHelper.getInstance(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        dbExecutor = Executors.newSingleThreadExecutor();
        forceUpdateHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_TRACKING.equals(action)) {
                stopTracking();
                stopSelf();
                return START_NOT_STICKY;
            }
            int newId = intent.getIntExtra("entregador_id", -1);
            if (newId > 0) {
                entregadorId = newId;
            }
        }

        if (entregadorId <= 0) {
            SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
            entregadorId = prefs.getInt("entregador_id", -1);
        }

        if (entregadorId > 0) {
            startForeground(NOTIFICATION_ID, buildNotification("Rastreamento GPS ativo - Aguardando sinal..."));
            startTracking();
        } else {
            Log.e(TAG, "Entregador ID invalido");
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Adquire WakeLock para manter o servico ativo mesmo com tela desligada.
     */
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PDVPro:EntregadorGpsWakeLock");
                wakeLock.acquire(12 * 60 * 60 * 1000L); // 12 horas max
                Log.d(TAG, "WakeLock adquirido");
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao adquirir WakeLock: " + e.getMessage());
        }
    }

    /**
     * Libera o WakeLock.
     */
    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock liberado");
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao liberar WakeLock: " + e.getMessage());
        }
    }

    private void startTracking() {
        if (isTracking) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissao de localizacao nao concedida");
            stopSelf();
            return;
        }

        try {
            // Registrar em AMBOS os providers para melhor cobertura
            boolean gpsEnabled = false;
            boolean networkEnabled = false;

            try {
                gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception e) {
                Log.w(TAG, "GPS provider nao disponivel");
            }

            try {
                networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            } catch (Exception e) {
                Log.w(TAG, "Network provider nao disponivel");
            }

            if (gpsEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
                Log.d(TAG, "GPS provider registrado");

                // Obter ultima localizacao conhecida do GPS
                Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastGps != null) {
                    processNewLocation(lastGps);
                }
            }

            if (networkEnabled) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
                Log.d(TAG, "Network provider registrado");

                // Obter ultima localizacao conhecida da rede
                Location lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (lastNetwork != null && bestLocation == null) {
                    processNewLocation(lastNetwork);
                }
            }

            if (!gpsEnabled && !networkEnabled) {
                Log.e(TAG, "Nenhum provider de localizacao disponivel!");
                updateNotification("ERRO: GPS e Rede desativados!");
                // Nao para o servico, pois o usuario pode ativar depois
            }

            isTracking = true;

            // Salvar estado
            SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
            prefs.edit()
                .putBoolean("tracking_active", true)
                .putInt("entregador_id", entregadorId)
                .apply();

            // Garantir estrutura do banco e registrar inicio
            dbExecutor.execute(() -> {
                garantirUniqueKey();
                registrarInicioRastreamento();
            });

            // Iniciar handler de atualizacao forcada
            startForceUpdateHandler();

            Log.d(TAG, "Rastreamento GPS iniciado para entregador " + entregadorId);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar rastreamento", e);
            updateNotification("ERRO ao iniciar GPS: " + e.getMessage());
        }
    }

    private void stopTracking() {
        isTracking = false;

        // Parar atualizacoes de localizacao
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (Exception e) {
                Log.w(TAG, "Erro ao remover updates: " + e.getMessage());
            }
        }

        // Parar handler de atualizacao forcada
        stopForceUpdateHandler();

        // Salvar estado
        SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
        prefs.edit().putBoolean("tracking_active", false).apply();

        // Registrar fim no banco
        dbExecutor.execute(this::registrarFimRastreamento);

        // Liberar WakeLock
        releaseWakeLock();

        Log.d(TAG, "Rastreamento GPS parado");
    }

    /**
     * Handler que forca atualizacao periodica mesmo sem movimento.
     * Garante que a localizacao no banco nao fique desatualizada.
     */
    private void startForceUpdateHandler() {
        forceUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isTracking) return;

                // Se tem localizacao, forcar salvamento no banco
                if (bestLocation != null) {
                    long timeSinceLastSave = System.currentTimeMillis() - lastDbSaveTime;
                    if (timeSinceLastSave >= FORCE_UPDATE_INTERVAL_MS) {
                        Log.d(TAG, "Forcando atualizacao periodica no banco");
                        double lat = bestLocation.getLatitude();
                        double lng = bestLocation.getLongitude();
                        float speed = bestLocation.getSpeed();
                        float accuracy = bestLocation.getAccuracy();
                        dbExecutor.execute(() -> salvarLocalizacao(lat, lng, speed, accuracy));
                    }
                }

                // Tentar reenviar localizacoes pendentes
                if (!pendingLocations.isEmpty()) {
                    dbExecutor.execute(() -> reenviarPendentes());
                }

                // Reagendar
                forceUpdateHandler.postDelayed(this, FORCE_UPDATE_INTERVAL_MS);
            }
        }, FORCE_UPDATE_INTERVAL_MS);
    }

    private void stopForceUpdateHandler() {
        if (forceUpdateHandler != null) {
            forceUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || entregadorId <= 0) return;
        processNewLocation(location);
    }

    /**
     * Processa uma nova localizacao recebida de qualquer provider.
     * Seleciona a melhor localizacao baseado em precisao e tempo.
     */
    private void processNewLocation(Location location) {
        if (location == null) return;

        // Verificar se esta localizacao e melhor que a atual
        if (isBetterLocation(location, bestLocation)) {
            bestLocation = location;

            double lat = location.getLatitude();
            double lng = location.getLongitude();
            float speed = location.getSpeed();
            float accuracy = location.getAccuracy();
            String provider = location.getProvider() != null ? location.getProvider().toUpperCase() : "?";

            Log.d(TAG, String.format(Locale.US,
                "Nova localizacao [%s]: %.6f, %.6f (precisao: %.1fm, vel: %.1fm/s)",
                provider, lat, lng, accuracy, speed));

            // Atualizar notificacao com coordenadas e provider
            String notifText = String.format(Locale.US,
                "GPS [%s]: %.6f, %.6f (±%.0fm)", provider, lat, lng, accuracy);
            updateNotification(notifText);

            // Salvar em SharedPreferences como cache local (para a UI)
            SharedPreferences prefs = getSharedPreferences("gps_config", MODE_PRIVATE);
            prefs.edit()
                .putFloat("last_lat", (float) lat)
                .putFloat("last_lng", (float) lng)
                .putFloat("last_speed", speed)
                .putFloat("last_accuracy", accuracy)
                .putLong("last_time", System.currentTimeMillis())
                .apply();

            // Enviar broadcast para atualizar a UI da ModoEntregadorActivity
            Intent updateIntent = new Intent(ACTION_LOCATION_UPDATE);
            updateIntent.putExtra(EXTRA_LATITUDE, lat);
            updateIntent.putExtra(EXTRA_LONGITUDE, lng);
            updateIntent.putExtra(EXTRA_SPEED, speed);
            updateIntent.putExtra(EXTRA_ACCURACY, accuracy);
            updateIntent.putExtra(EXTRA_TIME, System.currentTimeMillis());
            updateIntent.setPackage(getPackageName());
            sendBroadcast(updateIntent);

            // Salvar no banco em thread separada
            dbExecutor.execute(() -> salvarLocalizacao(lat, lng, speed, accuracy));
        }
    }

    /**
     * Determina se a nova localizacao e melhor que a atual.
     * Considera tempo e precisao.
     */
    private boolean isBetterLocation(Location newLocation, Location currentBest) {
        if (currentBest == null) return true;

        // Verificar se a nova e significativamente mais recente
        long timeDelta = newLocation.getTime() - currentBest.getTime();
        boolean isSignificantlyNewer = timeDelta > MIN_TIME_MS * 2;
        boolean isSignificantlyOlder = timeDelta < -MIN_TIME_MS * 2;
        boolean isNewer = timeDelta > 0;

        if (isSignificantlyNewer) return true;
        if (isSignificantlyOlder) return false;

        // Verificar precisao
        float accuracyDelta = newLocation.getAccuracy() - currentBest.getAccuracy();
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        if (isMoreAccurate) return true;
        if (isNewer && !isSignificantlyLessAccurate) return true;

        return false;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Provider status changed: " + provider + " -> " + status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "Provider habilitado: " + provider);
        // Re-registrar para este provider
        if (isTracking && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(provider, MIN_TIME_MS, MIN_DISTANCE_M, this, Looper.getMainLooper());
            } catch (Exception e) {
                Log.w(TAG, "Erro ao re-registrar provider " + provider);
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "Provider desabilitado: " + provider);
        updateNotification("AVISO: " + provider + " desativado!");
    }

    /**
     * Garante que existe uma UNIQUE KEY no entregador_id da tabela rastreamento_entregador.
     * Sem isso, o ON DUPLICATE KEY UPDATE nao funciona e cria registros duplicados.
     */
    private void garantirUniqueKey() {
        try {
            Connection conn = dbHelper.getConnection();

            // Verificar se ja existe unique key
            boolean hasUniqueKey = false;
            try {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SHOW INDEX FROM rastreamento_entregador WHERE Column_name = 'entregador_id' AND Non_unique = 0");
                hasUniqueKey = rs.next();
                rs.close();
                stmt.close();
            } catch (Exception e) {
                Log.w(TAG, "Erro ao verificar indice: " + e.getMessage());
            }

            if (!hasUniqueKey) {
                Log.d(TAG, "Criando UNIQUE KEY no entregador_id...");
                try {
                    Statement stmt = conn.createStatement();
                    // Primeiro, remover duplicatas mantendo apenas o mais recente
                    stmt.executeUpdate(
                        "DELETE r1 FROM rastreamento_entregador r1 "
                        + "INNER JOIN rastreamento_entregador r2 "
                        + "WHERE r1.entregador_id = r2.entregador_id AND r1.id < r2.id");

                    // Agora criar o indice unico
                    stmt.executeUpdate(
                        "ALTER TABLE rastreamento_entregador ADD UNIQUE KEY uk_entregador (entregador_id)");
                    stmt.close();
                    Log.d(TAG, "UNIQUE KEY criada com sucesso!");
                } catch (Exception e) {
                    // Se ja existe ou erro, ignorar
                    Log.w(TAG, "Aviso ao criar UNIQUE KEY: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao garantir UNIQUE KEY", e);
        }
    }

    /**
     * Salva a localizacao no banco de dados.
     * Se falhar, adiciona a fila de pendentes para retry posterior.
     */
    private void salvarLocalizacao(double lat, double lng, float speed, float accuracy) {
        try {
            Connection conn = dbHelper.getConnection();

            // Atualizar ou inserir na tabela rastreamento_entregador
            // Com UNIQUE KEY no entregador_id, ON DUPLICATE KEY UPDATE funciona corretamente
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO rastreamento_entregador (entregador_id, latitude, longitude, velocidade, precisao, data_hora, ativo) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), 1) "
                + "ON DUPLICATE KEY UPDATE latitude=VALUES(latitude), longitude=VALUES(longitude), "
                + "velocidade=VALUES(velocidade), precisao=VALUES(precisao), data_hora=NOW(), ativo=1");
            ps.setInt(1, entregadorId);
            ps.setDouble(2, lat);
            ps.setDouble(3, lng);
            ps.setFloat(4, speed);
            ps.setFloat(5, accuracy);
            int rows = ps.executeUpdate();
            ps.close();

            Log.d(TAG, "Localizacao salva no rastreamento_entregador (rows affected: " + rows + ")");

            // Tambem salvar historico
            ps = conn.prepareStatement(
                "INSERT INTO historico_localizacao (entregador_id, latitude, longitude, velocidade, precisao, data_hora) "
                + "VALUES (?, ?, ?, ?, ?, NOW())");
            ps.setInt(1, entregadorId);
            ps.setDouble(2, lat);
            ps.setDouble(3, lng);
            ps.setFloat(4, speed);
            ps.setFloat(5, accuracy);
            ps.executeUpdate();
            ps.close();

            lastDbSaveTime = System.currentTimeMillis();

            Log.d(TAG, String.format(Locale.US,
                "Localizacao salva com sucesso: %.6f, %.6f (entregador %d)", lat, lng, entregadorId));

        } catch (Exception e) {
            Log.e(TAG, "Erro ao salvar localizacao no banco - adicionando a fila de pendentes", e);

            // Adicionar a fila de pendentes para retry
            synchronized (pendingLocations) {
                if (pendingLocations.size() < MAX_PENDING_LOCATIONS) {
                    pendingLocations.add(new PendingLocation(lat, lng, speed, accuracy));
                    Log.d(TAG, "Localizacao adicionada a fila de pendentes (" + pendingLocations.size() + " pendentes)");
                } else {
                    // Remover a mais antiga e adicionar a nova
                    pendingLocations.remove(0);
                    pendingLocations.add(new PendingLocation(lat, lng, speed, accuracy));
                    Log.w(TAG, "Fila de pendentes cheia - removida mais antiga");
                }
            }
        }
    }

    /**
     * Tenta reenviar localizacoes que falharam anteriormente.
     */
    private void reenviarPendentes() {
        List<PendingLocation> toSend;
        synchronized (pendingLocations) {
            if (pendingLocations.isEmpty()) return;
            toSend = new ArrayList<>(pendingLocations);
        }

        Log.d(TAG, "Tentando reenviar " + toSend.size() + " localizacoes pendentes...");

        try {
            Connection conn = dbHelper.getConnection();
            int enviados = 0;

            for (PendingLocation pl : toSend) {
                try {
                    // Salvar apenas no historico (a posicao atual ja foi atualizada)
                    PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO historico_localizacao (entregador_id, latitude, longitude, velocidade, precisao, data_hora) "
                        + "VALUES (?, ?, ?, ?, ?, FROM_UNIXTIME(?/1000))");
                    ps.setInt(1, entregadorId);
                    ps.setDouble(2, pl.lat);
                    ps.setDouble(3, pl.lng);
                    ps.setFloat(4, pl.speed);
                    ps.setFloat(5, pl.accuracy);
                    ps.setLong(6, pl.timestamp);
                    ps.executeUpdate();
                    ps.close();
                    enviados++;
                } catch (Exception e) {
                    Log.w(TAG, "Falha ao reenviar pendente: " + e.getMessage());
                    break; // Se falhou, para de tentar
                }
            }

            if (enviados > 0) {
                synchronized (pendingLocations) {
                    // Remover os que foram enviados com sucesso
                    for (int i = 0; i < enviados && !pendingLocations.isEmpty(); i++) {
                        pendingLocations.remove(0);
                    }
                }
                Log.d(TAG, enviados + " localizacoes pendentes reenviadas com sucesso");
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro ao reenviar pendentes", e);
        }
    }

    private void registrarInicioRastreamento() {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO rastreamento_entregador (entregador_id, latitude, longitude, velocidade, precisao, data_hora, ativo) "
                + "VALUES (?, 0, 0, 0, 0, NOW(), 1) "
                + "ON DUPLICATE KEY UPDATE ativo=1, data_hora=NOW()");
            ps.setInt(1, entregadorId);
            ps.executeUpdate();
            ps.close();
            Log.d(TAG, "Inicio de rastreamento registrado no banco");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar inicio", e);
        }
    }

    private void registrarFimRastreamento() {
        try {
            Connection conn = dbHelper.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE rastreamento_entregador SET ativo=0 WHERE entregador_id=?");
            ps.setInt(1, entregadorId);
            ps.executeUpdate();
            ps.close();
            Log.d(TAG, "Fim de rastreamento registrado no banco");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao registrar fim", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rastreamento do Entregador",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notificacao do rastreamento GPS do entregador");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, EntregadorGpsService.class);
        stopIntent.setAction(ACTION_STOP_TRACKING);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PDV Pro - Modo Entregador")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_delivery)
            .setOngoing(true)
            .addAction(R.drawable.ic_exit, "Parar", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(text));
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao atualizar notificacao: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        stopTracking();
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        super.onDestroy();
    }
}
