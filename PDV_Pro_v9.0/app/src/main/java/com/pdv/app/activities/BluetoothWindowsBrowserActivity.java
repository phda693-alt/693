package com.pdv.app.activities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pdv.app.R;
import com.pdv.app.adapters.GenericAdapter;
import com.pdv.app.utils.BluetoothWindowsPrintManager;
import com.pdv.app.utils.BluetoothWindowsPrintManager.BluetoothDeviceInfo;
import com.pdv.app.utils.BluetoothWindowsPrintManager.WindowsPrinterInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity para busca automatica de dispositivos Bluetooth Windows
 * e selecao de impressoras do Painel de Controle do Windows.
 *
 * Fluxo de navegacao:
 * 1. Busca dispositivos Bluetooth pareados e novos (discovery)
 * 2. Filtra e destaca computadores Windows
 * 3. Ao selecionar um dispositivo, conecta e lista as impressoras
 *    do Painel de Controle (Hardware e Sons > Dispositivos e Impressoras)
 * 4. O usuario seleciona a impressora desejada
 * 5. Testa a conexao e retorna o resultado
 *
 * Suporta:
 * - Listagem de dispositivos pareados (instantanea)
 * - Discovery de novos dispositivos
 * - Listagem de impressoras do Windows via Bluetooth/SMB
 * - Selecao de impressoras do Painel de Controle
 * - Entrada manual do nome da impressora
 * - Teste de conexao antes de confirmar
 * - Pareamento automatico quando necessario
 */
public class BluetoothWindowsBrowserActivity extends BaseActivity {
    private static final String TAG = "BtWinBrowser";

    // Extras para comunicacao com a activity chamadora
    public static final String EXTRA_BT_DEVICE_NAME = "bt_device_name";
    public static final String EXTRA_BT_DEVICE_MAC = "bt_device_mac";
    public static final String EXTRA_BT_PRINTER_NAME = "bt_printer_name";
    public static final String EXTRA_SMB_DOMAIN = "smb_domain";
    public static final String EXTRA_SMB_USER = "smb_user";
    public static final String EXTRA_SMB_PASSWORD = "smb_password";

    private static final int REQUEST_ENABLE_BT = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    // Niveis de navegacao
    private static final int LEVEL_DEVICES = 0;
    private static final int LEVEL_PRINTERS = 1;
    private static final int LEVEL_CONFIRM = 2;

    // Views
    private RecyclerView recyclerView;
    private LinearLayout layoutEmpty, layoutNivelInfo, layoutCredenciais;
    private ProgressBar progressBar;
    private TextView tvStatus, tvInfo, tvEmpty, tvNivelPath, tvNivelDesc;
    private View viewStatusIndicator;
    private EditText etDomain, etUser, etPassword;
    private Button btnBuscar, btnNivelAnterior, btnCancelar;
    private ImageView btnVoltar, btnRefresh;

    // Dados
    private GenericAdapter<BluetoothDeviceInfo> deviceAdapter;
    private GenericAdapter<WindowsPrinterInfo> printerAdapter;
    private BluetoothWindowsPrintManager btManager;
    private BluetoothAdapter bluetoothAdapter;
    private List<BluetoothDeviceInfo> allDevices = new ArrayList<>();
    private List<WindowsPrinterInfo> allPrinters = new ArrayList<>();
    private Set<String> foundAddresses = new HashSet<>();
    private int currentLevel = LEVEL_DEVICES;
    private String selectedDeviceMac = "";
    private String selectedDeviceName = "";
    private String selectedPrinterName = "";
    private boolean isDiscovering = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    // BroadcastReceiver para descoberta de dispositivos
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    handleDeviceFound(intent);
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    handleDiscoveryStarted();
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    handleDiscoveryFinished();
                    break;
                case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                    handleBondStateChanged(intent);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_windows_browser);

        btManager = new BluetoothWindowsPrintManager(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initViews();
        setupDeviceAdapter();
        setupPrinterAdapter();
        setupListeners();
        loadPresetCredentials();
        registerBluetoothReceiver();

        // Verificar Bluetooth
        if (!btManager.isBluetoothAvailable()) {
            showError("Este dispositivo nao possui Bluetooth.\n\nNao e possivel usar esta funcionalidade.");
            btnBuscar.setEnabled(false);
            return;
        }

        if (!btManager.isBluetoothEnabled()) {
            requestEnableBluetooth();
        } else {
            checkPermissionsAndStart();
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        layoutNivelInfo = findViewById(R.id.layoutNivelInfo);
        layoutCredenciais = findViewById(R.id.layoutCredenciais);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvNivelPath = findViewById(R.id.tvNivelPath);
        tvNivelDesc = findViewById(R.id.tvNivelDesc);
        viewStatusIndicator = findViewById(R.id.viewStatusIndicator);
        etDomain = findViewById(R.id.etDomain);
        etUser = findViewById(R.id.etUser);
        etPassword = findViewById(R.id.etPassword);
        btnBuscar = findViewById(R.id.btnBuscar);
        btnNivelAnterior = findViewById(R.id.btnNivelAnterior);
        btnCancelar = findViewById(R.id.btnCancelar);
        btnVoltar = findViewById(R.id.btnVoltar);
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    /**
     * Configura o adapter para a lista de dispositivos Bluetooth.
     */
    private void setupDeviceAdapter() {
        deviceAdapter = new GenericAdapter<>(R.layout.item_bluetooth_device, (holder, item, position) -> {
            holder.setText(R.id.tvName, item.name);

            String desc = item.description;
            if (item.isPaired) {
                desc += " (Pareado)";
            }
            holder.setText(R.id.tvType, desc);

            // Icone baseado no tipo
            ImageView ivIcon = holder.find(R.id.ivIcon);
            if (item.isLikelyWindowsComputer()) {
                ivIcon.setImageResource(R.drawable.ic_computer);
            } else if (item.majorClass == 0x0600) {
                // Imaging/Printer
                ivIcon.setImageResource(R.drawable.ic_printer);
            } else {
                ivIcon.setImageResource(R.drawable.ic_bluetooth);
            }

            // Sinal (se disponivel)
            TextView tvSignal = holder.find(R.id.tvSignal);
            if (item.rssi != 0) {
                tvSignal.setVisibility(View.VISIBLE);
                String signalDesc;
                if (item.rssi > -50) signalDesc = "Sinal: Excelente";
                else if (item.rssi > -70) signalDesc = "Sinal: Bom";
                else if (item.rssi > -85) signalDesc = "Sinal: Regular";
                else signalDesc = "Sinal: Fraco";
                tvSignal.setText(signalDesc + " (" + item.rssi + " dBm)");
            } else {
                tvSignal.setVisibility(View.GONE);
            }
        });

        deviceAdapter.setOnItemClickListener((item, position) -> {
            selecionarDispositivo(item);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(deviceAdapter);
    }

    /**
     * Configura o adapter para a lista de impressoras do Windows.
     */
    private void setupPrinterAdapter() {
        printerAdapter = new GenericAdapter<>(R.layout.item_windows_printer, (holder, item, position) -> {
            holder.setText(R.id.tvName, item.name);

            String desc = item.getDescription();
            holder.setText(R.id.tvType, desc);

            // Status
            TextView tvStatus = holder.find(R.id.tvStatus);
            if (item.status != null && !item.status.isEmpty()) {
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(item.status);
                if (item.isReady) {
                    tvStatus.setTextColor(0xFF00E676); // Verde
                } else {
                    tvStatus.setTextColor(0xFFFF5252); // Vermelho
                }
            } else {
                tvStatus.setVisibility(View.GONE);
            }

            // Icone
            ImageView ivIcon = holder.find(R.id.ivIcon);
            if (item.isLikelyReceiptPrinter()) {
                ivIcon.setImageResource(R.drawable.ic_printer);
            } else if (item.isVirtualPrinter()) {
                ivIcon.setImageResource(R.drawable.ic_note);
            } else if ("Manual".equals(item.type)) {
                ivIcon.setImageResource(R.drawable.ic_search_network);
            } else {
                ivIcon.setImageResource(R.drawable.ic_printer);
            }

            // Destaque para impressoras termicas
            if (item.isLikelyReceiptPrinter()) {
                holder.find(R.id.tvName).setBackgroundColor(0x0D2196F3);
            }
        });

        printerAdapter.setOnItemClickListener((item, position) -> {
            selecionarImpressora(item);
        });
    }

    private void setupListeners() {
        btnVoltar.setOnClickListener(v -> onBackPressed());

        btnRefresh.setOnClickListener(v -> {
            if (currentLevel == LEVEL_DEVICES) {
                iniciarBusca();
            } else if (currentLevel == LEVEL_PRINTERS) {
                carregarImpressorasDoWindows();
            }
        });

        btnBuscar.setOnClickListener(v -> {
            if (currentLevel == LEVEL_DEVICES) {
                if (isDiscovering) {
                    pararBusca();
                } else {
                    iniciarBusca();
                }
            } else if (currentLevel == LEVEL_PRINTERS) {
                // Opcao para digitar nome manualmente
                mostrarDialogNomeManual();
            }
        });

        btnNivelAnterior.setOnClickListener(v -> voltarNivel());

        btnCancelar.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    private void loadPresetCredentials() {
        Intent intent = getIntent();
        if (intent != null) {
            String domain = intent.getStringExtra(EXTRA_SMB_DOMAIN);
            String user = intent.getStringExtra(EXTRA_SMB_USER);
            String password = intent.getStringExtra(EXTRA_SMB_PASSWORD);
            if (domain != null && !domain.isEmpty()) etDomain.setText(domain);
            if (user != null && !user.isEmpty()) etUser.setText(user);
            if (password != null && !password.isEmpty()) etPassword.setText(password);
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    // ==================== PERMISSOES ====================

    private void requestEnableBluetooth() {
        try {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } catch (SecurityException e) {
            showError("Sem permissao para ativar o Bluetooth.\n\nPor favor, ative manualmente nas configuracoes.");
        }
    }

    private void checkPermissionsAndStart() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
        } else {
            carregarDispositivosPareados();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                carregarDispositivosPareados();
            } else {
                showError("Permissoes necessarias nao foram concedidas.\n\n"
                        + "Para buscar dispositivos Bluetooth, o app precisa das permissoes de "
                        + "Bluetooth e Localizacao.\n\n"
                        + "Voce pode conceder nas Configuracoes do Android.");
                // Mesmo sem discovery, mostrar pareados
                carregarDispositivosPareados();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkPermissionsAndStart();
            } else {
                showError("Bluetooth nao foi ativado.\n\nPor favor, ative o Bluetooth para continuar.");
            }
        }
    }

    // ==================== BUSCA DE DISPOSITIVOS ====================

    /**
     * Carrega dispositivos Bluetooth ja pareados.
     */
    private void carregarDispositivosPareados() {
        allDevices.clear();
        foundAddresses.clear();

        List<BluetoothDeviceInfo> paired = btManager.getAllPairedDevices();
        for (BluetoothDeviceInfo device : paired) {
            if (!foundAddresses.contains(device.address)) {
                foundAddresses.add(device.address);
                allDevices.add(device);
            }
        }

        ordenarEAtualizarDevices();

        int computerCount = 0;
        for (BluetoothDeviceInfo d : allDevices) {
            if (d.isLikelyWindowsComputer()) computerCount++;
        }

        updateStatus("Bluetooth ativado");
        if (allDevices.isEmpty()) {
            updateInfo("Nenhum dispositivo pareado encontrado.\nToque em BUSCAR para procurar novos dispositivos.");
            showEmpty(true);
        } else {
            updateInfo(allDevices.size() + " dispositivo(s) pareado(s) encontrado(s)"
                    + (computerCount > 0 ? " (" + computerCount + " computador(es))" : "")
                    + ".\nToque em BUSCAR para encontrar mais dispositivos.");
            showEmpty(false);
        }
    }

    /**
     * Inicia a busca (discovery) de dispositivos Bluetooth.
     */
    private void iniciarBusca() {
        if (bluetoothAdapter == null) return;

        if (!bluetoothAdapter.isEnabled()) {
            requestEnableBluetooth();
            return;
        }

        // Primeiro carregar pareados
        carregarDispositivosPareados();

        // Iniciar discovery
        try {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            boolean started = bluetoothAdapter.startDiscovery();
            if (started) {
                isDiscovering = true;
                btnBuscar.setText("PARAR BUSCA");
                showProgress(true);
                updateStatus("Buscando dispositivos...");
                updateInfo("Procurando dispositivos Bluetooth proximos...\nIsso pode levar ate 12 segundos.");
            } else {
                showError("Nao foi possivel iniciar a busca Bluetooth.\n\n"
                        + "Verifique se o Bluetooth esta ativado e as permissoes foram concedidas.");
            }
        } catch (SecurityException e) {
            showError("Sem permissao para buscar dispositivos Bluetooth.\n\n"
                    + "Conceda as permissoes necessarias nas Configuracoes do Android.");
        }
    }

    /**
     * Para a busca de dispositivos.
     */
    private void pararBusca() {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {}

        isDiscovering = false;
        btnBuscar.setText("BUSCAR DISPOSITIVOS BLUETOOTH");
        showProgress(false);
    }

    // ==================== HANDLERS DO BROADCAST RECEIVER ====================

    private void handleDeviceFound(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;

        String address = null;
        try {
            address = device.getAddress();
        } catch (Exception e) {
            return;
        }

        if (address == null || foundAddresses.contains(address)) return;

        BluetoothDeviceInfo info = new BluetoothDeviceInfo(null, address);
        try {
            info.name = device.getName();
        } catch (SecurityException ignored) {}

        if (info.name == null || info.name.isEmpty()) {
            info.name = "Dispositivo " + address;
        }

        try {
            if (device.getBluetoothClass() != null) {
                info.deviceClass = device.getBluetoothClass().getDeviceClass();
                info.majorClass = device.getBluetoothClass().getMajorDeviceClass();
                info.isComputer = (info.majorClass == 0x0100);
            }
        } catch (Exception ignored) {}

        // RSSI (sinal)
        short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
        if (rssi != Short.MIN_VALUE) {
            info.rssi = rssi;
        }

        // Verificar se esta pareado
        try {
            info.isPaired = (device.getBondState() == BluetoothDevice.BOND_BONDED);
        } catch (Exception ignored) {}

        // Gerar descricao
        if (info.isComputer) {
            info.description = "Computador encontrado";
        } else if (info.majorClass == 0x0600) {
            info.description = "Impressora/Imagem";
        } else {
            info.description = "Dispositivo Bluetooth";
        }

        foundAddresses.add(address);
        allDevices.add(info);

        ordenarEAtualizarDevices();

        int total = allDevices.size();
        int computers = 0;
        for (BluetoothDeviceInfo d : allDevices) {
            if (d.isLikelyWindowsComputer()) computers++;
        }

        updateInfo("Encontrados " + total + " dispositivo(s)"
                + (computers > 0 ? " (" + computers + " computador(es))" : "")
                + " - Buscando...");
        showEmpty(false);
    }

    private void handleDiscoveryStarted() {
        Log.i(TAG, "Discovery iniciado");
    }

    private void handleDiscoveryFinished() {
        Log.i(TAG, "Discovery finalizado");
        isDiscovering = false;

        handler.post(() -> {
            btnBuscar.setText("BUSCAR DISPOSITIVOS BLUETOOTH");
            showProgress(false);

            int total = allDevices.size();
            int computers = 0;
            for (BluetoothDeviceInfo d : allDevices) {
                if (d.isLikelyWindowsComputer()) computers++;
            }

            if (total == 0) {
                showEmpty(true);
                updateStatus("Busca finalizada");
                updateInfo("Nenhum dispositivo encontrado.\n\n"
                        + "Verifique:\n"
                        + "- O Bluetooth do computador Windows esta ativado\n"
                        + "- O computador esta visivel/detectavel\n"
                        + "- Estao proximos (ate ~10 metros)");
            } else {
                showEmpty(false);
                updateStatus("Busca finalizada - " + total + " dispositivo(s)");
                updateInfo(total + " dispositivo(s) encontrado(s)"
                        + (computers > 0 ? " (" + computers + " computador(es) Windows)" : "")
                        + ".\nToque em um dispositivo para ver suas impressoras.");
            }
        });
    }

    private void handleBondStateChanged(Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);

        if (device != null && bondState == BluetoothDevice.BOND_BONDED) {
            String address = null;
            try { address = device.getAddress(); } catch (Exception ignored) {}
            if (address != null) {
                // Atualizar o dispositivo na lista como pareado
                for (BluetoothDeviceInfo info : allDevices) {
                    if (address.equals(info.address)) {
                        info.isPaired = true;
                        break;
                    }
                }
                ordenarEAtualizarDevices();
                showToast("Dispositivo pareado com sucesso!");
            }
        }
    }

    // ==================== SELECAO DE DISPOSITIVO ====================

    /**
     * Seleciona um dispositivo Bluetooth e navega para a lista de impressoras.
     */
    private void selecionarDispositivo(BluetoothDeviceInfo device) {
        // Parar busca se estiver em andamento
        pararBusca();

        selectedDeviceMac = device.address;
        selectedDeviceName = device.name;

        String tipo = device.isLikelyWindowsComputer() ? "computador Windows" : "dispositivo";

        showConfirm("Conectar ao " + tipo,
                "Deseja conectar a este " + tipo + " para listar as impressoras?\n\n"
                        + "Nome: " + device.name + "\n"
                        + "Endereco: " + device.address + "\n"
                        + "Tipo: " + device.description + "\n"
                        + (device.isPaired ? "Status: Pareado" : "Status: Nao pareado (sera pareado automaticamente)")
                        + "\n\nO app ira conectar via Bluetooth e buscar as impressoras "
                        + "instaladas no Painel de Controle do Windows\n"
                        + "(Hardware e Sons > Dispositivos e Impressoras).",
                () -> {
                    // Navegar para o nivel de impressoras
                    navegarParaImpressoras(device);
                });
    }

    /**
     * Navega para o nivel de impressoras do dispositivo selecionado.
     */
    private void navegarParaImpressoras(BluetoothDeviceInfo device) {
        currentLevel = LEVEL_PRINTERS;

        // Atualizar UI para nivel de impressoras
        handler.post(() -> {
            layoutNivelInfo.setVisibility(View.VISIBLE);
            tvNivelPath.setText(device.name);
            tvNivelDesc.setText("Impressoras do Windows");
            btnNivelAnterior.setVisibility(View.VISIBLE);
            layoutCredenciais.setVisibility(View.VISIBLE);

            btnBuscar.setText("DIGITAR NOME MANUALMENTE");
            btnBuscar.setVisibility(View.VISIBLE);

            // Trocar adapter para impressoras
            recyclerView.setAdapter(printerAdapter);
        });

        // Carregar impressoras
        carregarImpressorasDoWindows();
    }

    /**
     * Carrega as impressoras do computador Windows selecionado.
     */
    private void carregarImpressorasDoWindows() {
        showProgress(true);
        showEmpty(false);
        updateStatus("Conectando ao " + selectedDeviceName + "...");
        updateInfo("Buscando impressoras do Painel de Controle do Windows...\n"
                + "(Hardware e Sons > Dispositivos e Impressoras)");

        // Salvar credenciais SMB se informadas
        String domain = etDomain.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (!domain.isEmpty() || !user.isEmpty()) {
            btManager.saveSmbCredentials(domain, user, password);
        }

        btManager.listarImpressorasWindows(selectedDeviceMac,
                new BluetoothWindowsPrintManager.PrinterListCallback() {
                    @Override
                    public void onPrintersFound(List<WindowsPrinterInfo> printers) {
                        handler.post(() -> {
                            showProgress(false);
                            allPrinters.clear();
                            allPrinters.addAll(printers);

                            // Ordenar: impressoras termicas primeiro, depois compartilhadas, depois outras
                            Collections.sort(allPrinters, (a, b) -> {
                                // Manual por ultimo
                                if ("Manual".equals(a.type) && !"Manual".equals(b.type)) return 1;
                                if (!"Manual".equals(a.type) && "Manual".equals(b.type)) return -1;

                                // Impressoras termicas primeiro
                                if (a.isLikelyReceiptPrinter() && !b.isLikelyReceiptPrinter()) return -1;
                                if (!a.isLikelyReceiptPrinter() && b.isLikelyReceiptPrinter()) return 1;

                                // Compartilhadas primeiro
                                if (a.isShared && !b.isShared) return -1;
                                if (!a.isShared && b.isShared) return 1;

                                // Padrao primeiro
                                if (a.isDefault && !b.isDefault) return -1;
                                if (!a.isDefault && b.isDefault) return 1;

                                // Virtuais por ultimo
                                if (a.isVirtualPrinter() && !b.isVirtualPrinter()) return 1;
                                if (!a.isVirtualPrinter() && b.isVirtualPrinter()) return -1;

                                return a.name.compareToIgnoreCase(b.name);
                            });

                            printerAdapter.setItems(allPrinters);

                            int total = allPrinters.size();
                            int termicas = 0;
                            int compartilhadas = 0;
                            for (WindowsPrinterInfo p : allPrinters) {
                                if (p.isLikelyReceiptPrinter()) termicas++;
                                if (p.isShared) compartilhadas++;
                            }

                            updateStatus(total + " impressora(s) encontrada(s)");
                            StringBuilder info = new StringBuilder();
                            info.append("Impressoras do Painel de Controle do Windows");
                            if (termicas > 0) {
                                info.append("\n").append(termicas).append(" impressora(s) termica(s) detectada(s)");
                            }
                            if (compartilhadas > 0) {
                                info.append("\n").append(compartilhadas).append(" compartilhada(s) na rede");
                            }
                            info.append("\nToque em uma impressora para selecionar.");
                            updateInfo(info.toString());

                            showEmpty(false);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        handler.post(() -> {
                            showProgress(false);
                            updateStatus("Erro ao buscar impressoras");
                            updateInfo(message);
                            showEmpty(true);
                            tvEmpty.setText("Nao foi possivel listar as impressoras.\n\n"
                                    + "Toque em 'DIGITAR NOME MANUALMENTE'\npara informar o nome da impressora.");
                        });
                    }

                    @Override
                    public void onProgress(String status) {
                        handler.post(() -> updateStatus(status));
                    }
                });
    }

    // ==================== SELECAO DE IMPRESSORA ====================

    /**
     * Seleciona uma impressora do Windows.
     */
    private void selecionarImpressora(WindowsPrinterInfo printer) {
        if ("Manual".equals(printer.type)) {
            // Abrir dialog para digitar nome manualmente
            mostrarDialogNomeManual();
            return;
        }

        selectedPrinterName = printer.name;

        showConfirm("Selecionar Impressora",
                "Deseja usar esta impressora para impressao?\n\n"
                        + "Impressora: " + printer.name + "\n"
                        + "Tipo: " + printer.type + "\n"
                        + (printer.isShared ? "Compartilhamento: " + printer.shareName + "\n" : "")
                        + (printer.portName != null && !printer.portName.isEmpty() ? "Porta: " + printer.portName + "\n" : "")
                        + (printer.isDefault ? "Impressora padrao do Windows\n" : "")
                        + "\nComputador: " + selectedDeviceName + "\n"
                        + "MAC: " + selectedDeviceMac,
                () -> {
                    // Testar conexao e confirmar
                    testarEConfirmar(printer);
                });
    }

    /**
     * Mostra dialog para digitar o nome da impressora manualmente.
     */
    private void mostrarDialogNomeManual() {
        final EditText input = new EditText(this);
        input.setHint("Nome da impressora no Windows");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF607D8B);
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Nome da Impressora")
                .setMessage("Digite o nome exato da impressora como aparece no\n"
                        + "Painel de Controle > Hardware e Sons > Dispositivos e Impressoras\n\n"
                        + "Exemplo: EPSON TM-T20, mp-4200 th, etc.")
                .setView(input)
                .setPositiveButton("Confirmar", (dialog, which) -> {
                    String nome = input.getText().toString().trim();
                    if (nome.isEmpty()) {
                        showError("Por favor, informe o nome da impressora.");
                        return;
                    }
                    selectedPrinterName = nome;
                    WindowsPrinterInfo manual = new WindowsPrinterInfo(nome);
                    manual.type = "Manual";
                    manual.status = "Informado manualmente";
                    testarEConfirmar(manual);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Testa a conexao e confirma a selecao.
     */
    private void testarEConfirmar(WindowsPrinterInfo printer) {
        showLoading("Testando conexao Bluetooth...\n\n"
                + "Computador: " + selectedDeviceName + "\n"
                + "Impressora: " + printer.name + "\n\n"
                + "Isso pode levar alguns segundos.");

        new Thread(() -> {
            boolean connected = btManager.testarConexao(selectedDeviceMac);
            hideLoading();

            if (connected) {
                runOnUiThread(() -> {
                    confirmarSelecao(printer);
                });
            } else {
                // Mesmo sem teste bem-sucedido, permitir selecionar
                runOnUiThread(() -> {
                    showConfirm("Conexao nao confirmada",
                            "Nao foi possivel confirmar a conexao Bluetooth com "
                                    + selectedDeviceName + ".\n\n"
                                    + "Isso pode acontecer se:\n"
                                    + "- O computador nao tem o servico SPP ativo\n"
                                    + "- O firewall esta bloqueando\n"
                                    + "- Os dispositivos nao estao pareados\n\n"
                                    + "Impressora selecionada: " + printer.name + "\n\n"
                                    + "Deseja selecionar mesmo assim?\n"
                                    + "(A impressao sera tentada no momento do uso)",
                            () -> confirmarSelecao(printer));
                });
            }
        }).start();
    }

    /**
     * Confirma a selecao e retorna o resultado.
     */
    private void confirmarSelecao(WindowsPrinterInfo printer) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_BT_DEVICE_NAME, selectedDeviceName);
        resultIntent.putExtra(EXTRA_BT_DEVICE_MAC, selectedDeviceMac);
        resultIntent.putExtra(EXTRA_BT_PRINTER_NAME, printer.name);
        resultIntent.putExtra(EXTRA_SMB_DOMAIN, etDomain.getText().toString().trim());
        resultIntent.putExtra(EXTRA_SMB_USER, etUser.getText().toString().trim());
        resultIntent.putExtra(EXTRA_SMB_PASSWORD, etPassword.getText().toString());

        setResult(Activity.RESULT_OK, resultIntent);

        showSuccess("Impressora selecionada!\n\n"
                + "Computador: " + selectedDeviceName + "\n"
                + "MAC: " + selectedDeviceMac + "\n"
                + "Impressora: " + printer.name + "\n\n"
                + "Clique em OK e depois em SALVAR na tela anterior para confirmar.");

        // Fechar apos um delay para o usuario ver a mensagem
        handler.postDelayed(() -> finish(), 2000);
    }

    // ==================== UI HELPERS ====================

    private void ordenarEAtualizarDevices() {
        // Ordenar: computadores Windows primeiro, depois pareados, depois outros
        List<BluetoothDeviceInfo> sorted = new ArrayList<>(allDevices);
        Collections.sort(sorted, (a, b) -> {
            // Computadores Windows primeiro
            if (a.isLikelyWindowsComputer() && !b.isLikelyWindowsComputer()) return -1;
            if (!a.isLikelyWindowsComputer() && b.isLikelyWindowsComputer()) return 1;

            // Pareados primeiro
            if (a.isPaired && !b.isPaired) return -1;
            if (!a.isPaired && b.isPaired) return 1;

            // Por sinal (mais forte primeiro)
            if (a.rssi != 0 && b.rssi != 0) {
                return Integer.compare(b.rssi, a.rssi);
            }

            // Por nome
            return a.name.compareToIgnoreCase(b.name);
        });

        handler.post(() -> deviceAdapter.setItems(sorted));
    }

    private void voltarNivel() {
        if (currentLevel == LEVEL_PRINTERS || currentLevel == LEVEL_CONFIRM) {
            currentLevel = LEVEL_DEVICES;
            layoutNivelInfo.setVisibility(View.GONE);
            layoutCredenciais.setVisibility(View.GONE);
            btnNivelAnterior.setVisibility(View.GONE);
            btnBuscar.setText("BUSCAR DISPOSITIVOS BLUETOOTH");

            // Trocar adapter de volta para dispositivos
            recyclerView.setAdapter(deviceAdapter);

            carregarDispositivosPareados();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    private void showProgress(boolean show) {
        handler.post(() -> progressBar.setVisibility(show ? View.VISIBLE : View.GONE));
    }

    private void showEmpty(boolean show) {
        handler.post(() -> {
            layoutEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        });
    }

    private void updateStatus(String text) {
        handler.post(() -> tvStatus.setText(text));
    }

    private void updateInfo(String text) {
        handler.post(() -> tvInfo.setText(text));
    }

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_PRINTERS || currentLevel == LEVEL_CONFIRM) {
            voltarNivel();
        } else {
            pararBusca();
            setResult(Activity.RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        pararBusca();
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}
