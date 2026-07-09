package com.pdv.app.activities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pdv.app.R;
import com.pdv.app.utils.AnimUtils;
import com.pdv.app.utils.PrinterManager;
import com.pdv.app.utils.ThermalPrinterDriver;
import com.pdv.app.utils.BluetoothWindowsPrintManager;
import com.pdv.app.permissions.PermissionConstants;
import com.pdv.app.permissions.PermissionHelper;
import com.pdv.app.utils.ErrorHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * ConfigImpressoraActivity v7.1.0
 * Tela de configuracao de impressora com suporte completo a TODOS os tipos:
 * - Rede TCP/IP
 * - Bluetooth direto (com busca automatica nos dispositivos emparelhados)
 * - USB
 * - SMB/CIFS
 * - SMB Direto
 * - Bluetooth Windows
 * - Servidor de Impressao (PC) via Python
 * - Rede IP Direto (RAW/LPR/IPP)
 *
 * v7.1.0 - Melhorias:
 * - Bluetooth: busca automatica nos dispositivos emparelhados (pareados)
 *   em vez de digitar o MAC manualmente
 * - Nova opcao: Rede IP Direto (RAW/LPR) para impressoras com IP fixo
 * - UI melhorada com painel de impressora selecionada
 */
public class ConfigImpressoraActivity extends BaseActivity {
    private static final int REQUEST_SMB_BROWSER = 5001;
    private static final int REQUEST_BT_WINDOWS_BROWSER = 5002;
    private static final int REQUEST_BT_PERMISSIONS = 5003;
    private static final int REQUEST_ENABLE_BT = 5004;

    private RadioGroup rgTipo, rgPapel;
    private RadioButton rbRede, rbBluetooth, rbUsb, rbSmb, rbSmbDireto, rbBtWindows, rbPrintServer, rbRedeRaw, rbNenhuma, rb58mm, rb80mm;
    private EditText etIp, etPorta, etMacBt;
    private EditText etSmbHost, etSmbShare, etSmbDomain, etSmbUser, etSmbPassword;
    private EditText etSmbDiretoCaminho, etSmbDiretoDomain, etSmbDiretoUser, etSmbDiretoPassword;
    private TextView tvSmbDiretoStatus;
    private LinearLayout layoutRede, layoutBluetooth, layoutSmb, layoutSmbDireto, layoutBtWindows, layoutPrintServer;
    private LinearLayout layoutBtWinSelecionado;
    private LinearLayout layoutPrintServerSelecionada;
    private LinearLayout layoutRedeRaw;
    private LinearLayout layoutBtSelecionado, layoutBtManual;
    private TextView tvBtWinDeviceName, tvBtWinDeviceMac, tvBtWinPrinterName, tvBtWinStatus;
    private TextView tvPrintServerImpressora, tvPrintServerStatus;
    private TextView tvBtDeviceName, tvBtDeviceMac, tvBtDeviceType, tvBtStatus;
    private TextView tvBtDigitarManual;
    private EditText etPrintServerIp, etPrintServerPorta;
    private EditText etRedeRawIp, etRedeRawPorta;
    private Spinner spProtocolo;
    private Spinner spDriverImpressora;
    private Button btnSalvar, btnTestarSmb, btnNavegar, btnTestarSmbDireto;
    private Button btnBuscarBtWindows, btnTestarBtWindows;
    private Button btnTestarRede, btnTestarBluetooth, btnTestarUsb;
    private Button btnImprimirTeste;
    private Button btnTestarPrintServer, btnBuscarImpressorasServer;
    private Button btnBuscarBluetooth, btnTestarRedeRaw;

    // Dados do dispositivo Bluetooth Windows selecionado
    private String btWinDeviceName = "";
    private String btWinDeviceMac = "";
    private String btWinPrinterName = "";

    // Dados do Print Server
    private String printServerImpressora = "";

    // Dados do Bluetooth direto selecionado
    private String btDiretoName = "";
    private String btDiretoMac = "";
    private String btDiretoType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_impressora);

        // Verificar permissao de acesso
        if (!PermissionHelper.verificarAcesso(this, PermissionConstants.CONFIG_IMPRESSORA_ACESSAR)) {
            return;
        }

        initViews();
        loadConfig();
        setupListeners();
        updateVisibility();

        // Validar o caminho atual se ja existir
        String caminhoAtual = etSmbDiretoCaminho.getText().toString();
        if (!caminhoAtual.isEmpty()) {
            validarCaminhoDiretoEmTempoReal(caminhoAtual);
        }

        AnimUtils.animateItems(rgTipo, rgPapel, layoutRede, layoutBluetooth, layoutSmb, layoutSmbDireto, layoutBtWindows, layoutPrintServer, btnSalvar);
    }

    private void initViews() {
        rgTipo = findViewById(R.id.rgTipo);
        rgPapel = findViewById(R.id.rgPapel);
        rbRede = findViewById(R.id.rbRede);
        rbBluetooth = findViewById(R.id.rbBluetooth);
        rbUsb = findViewById(R.id.rbUsb);
        rbSmb = findViewById(R.id.rbSmb);
        rbSmbDireto = findViewById(R.id.rbSmbDireto);
        rbBtWindows = findViewById(R.id.rbBtWindows);
        rbPrintServer = findViewById(R.id.rbPrintServer);
        rbRedeRaw = findViewById(R.id.rbRedeRaw);
        rbNenhuma = findViewById(R.id.rbNenhuma);
        rb58mm = findViewById(R.id.rb58mm);
        rb80mm = findViewById(R.id.rb80mm);
        etIp = findViewById(R.id.etIp);
        etPorta = findViewById(R.id.etPorta);
        etMacBt = findViewById(R.id.etMacBt);
        layoutRede = findViewById(R.id.layoutRede);
        layoutBluetooth = findViewById(R.id.layoutBluetooth);
        layoutSmb = findViewById(R.id.layoutSmb);
        layoutSmbDireto = findViewById(R.id.layoutSmbDireto);
        layoutBtWindows = findViewById(R.id.layoutBtWindows);
        layoutPrintServer = findViewById(R.id.layoutPrintServer);
        layoutRedeRaw = findViewById(R.id.layoutRedeRaw);
        btnSalvar = findViewById(R.id.btnSalvar);
        btnTestarSmb = findViewById(R.id.btnTestarSmb);
        btnNavegar = findViewById(R.id.btnNavegar);
        btnTestarSmbDireto = findViewById(R.id.btnTestarSmbDireto);

        // SMB fields
        etSmbHost = findViewById(R.id.etSmbHost);
        etSmbShare = findViewById(R.id.etSmbShare);
        etSmbDomain = findViewById(R.id.etSmbDomain);
        etSmbUser = findViewById(R.id.etSmbUser);
        etSmbPassword = findViewById(R.id.etSmbPassword);

        // SMB Direto fields
        etSmbDiretoCaminho = findViewById(R.id.etSmbDiretoCaminho);
        etSmbDiretoDomain = findViewById(R.id.etSmbDiretoDomain);
        etSmbDiretoUser = findViewById(R.id.etSmbDiretoUser);
        etSmbDiretoPassword = findViewById(R.id.etSmbDiretoPassword);
        tvSmbDiretoStatus = findViewById(R.id.tvSmbDiretoStatus);

        // Bluetooth Windows fields
        btnBuscarBtWindows = findViewById(R.id.btnBuscarBtWindows);
        btnTestarBtWindows = findViewById(R.id.btnTestarBtWindows);
        layoutBtWinSelecionado = findViewById(R.id.layoutBtWinSelecionado);
        tvBtWinDeviceName = findViewById(R.id.tvBtWinDeviceName);
        tvBtWinDeviceMac = findViewById(R.id.tvBtWinDeviceMac);
        tvBtWinPrinterName = findViewById(R.id.tvBtWinPrinterName);
        tvBtWinStatus = findViewById(R.id.tvBtWinStatus);

        // Print Server fields
        etPrintServerIp = findViewById(R.id.etPrintServerIp);
        etPrintServerPorta = findViewById(R.id.etPrintServerPorta);
        btnTestarPrintServer = findViewById(R.id.btnTestarPrintServer);
        btnBuscarImpressorasServer = findViewById(R.id.btnBuscarImpressorasServer);
        layoutPrintServerSelecionada = findViewById(R.id.layoutPrintServerSelecionada);
        tvPrintServerImpressora = findViewById(R.id.tvPrintServerImpressora);
        tvPrintServerStatus = findViewById(R.id.tvPrintServerStatus);

        // Bluetooth direto - novos campos (busca automatica)
        btnBuscarBluetooth = findViewById(R.id.btnBuscarBluetooth);
        layoutBtSelecionado = findViewById(R.id.layoutBtSelecionado);
        layoutBtManual = findViewById(R.id.layoutBtManual);
        tvBtDeviceName = findViewById(R.id.tvBtDeviceName);
        tvBtDeviceMac = findViewById(R.id.tvBtDeviceMac);
        tvBtDeviceType = findViewById(R.id.tvBtDeviceType);
        tvBtStatus = findViewById(R.id.tvBtStatus);
        tvBtDigitarManual = findViewById(R.id.tvBtDigitarManual);

        // Rede RAW fields
        etRedeRawIp = findViewById(R.id.etRedeRawIp);
        etRedeRawPorta = findViewById(R.id.etRedeRawPorta);
        spProtocolo = findViewById(R.id.spProtocolo);
        spDriverImpressora = findViewById(R.id.spDriverImpressora);
        btnTestarRedeRaw = findViewById(R.id.btnTestarRedeRaw);

        // Botoes de teste por tipo - podem ser null se nao existirem no layout
        btnTestarRede = findViewById(R.id.btnTestarRede);
        btnTestarBluetooth = findViewById(R.id.btnTestarBluetooth);
        btnTestarUsb = findViewById(R.id.btnTestarUsb);
        btnImprimirTeste = findViewById(R.id.btnImprimirTeste);

        // Setup Spinner de protocolo e driver/modelo
        setupProtocoloSpinner();
        setupDriverSpinner();
    }

    private void setupDriverSpinner() {
        if (spDriverImpressora != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, ThermalPrinterDriver.getDisplayNames());
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spDriverImpressora.setAdapter(adapter);
        }
    }

    private void setupProtocoloSpinner() {
        if (spProtocolo != null) {
            String[] protocolos = {"RAW (Porta 9100)", "LPR/LPD (Porta 515)", "IPP (Porta 631)"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, protocolos);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spProtocolo.setAdapter(adapter);

            spProtocolo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (etRedeRawPorta != null) {
                        switch (position) {
                            case 0: // RAW
                                if (etRedeRawPorta.getText().toString().isEmpty()
                                        || "515".equals(etRedeRawPorta.getText().toString())
                                        || "631".equals(etRedeRawPorta.getText().toString())) {
                                    etRedeRawPorta.setText("9100");
                                }
                                break;
                            case 1: // LPR
                                if (etRedeRawPorta.getText().toString().isEmpty()
                                        || "9100".equals(etRedeRawPorta.getText().toString())
                                        || "631".equals(etRedeRawPorta.getText().toString())) {
                                    etRedeRawPorta.setText("515");
                                }
                                break;
                            case 2: // IPP
                                if (etRedeRawPorta.getText().toString().isEmpty()
                                        || "9100".equals(etRedeRawPorta.getText().toString())
                                        || "515".equals(etRedeRawPorta.getText().toString())) {
                                    etRedeRawPorta.setText("631");
                                }
                                break;
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void loadConfig() {
        PrinterManager pm = new PrinterManager(this);
        String tipo = pm.getTipoImpressora();

        if (PrinterManager.TIPO_REDE.equals(tipo)) rbRede.setChecked(true);
        else if (PrinterManager.TIPO_BLUETOOTH.equals(tipo)) rbBluetooth.setChecked(true);
        else if (PrinterManager.TIPO_USB.equals(tipo)) rbUsb.setChecked(true);
        else if (PrinterManager.TIPO_SMB.equals(tipo)) rbSmb.setChecked(true);
        else if (PrinterManager.TIPO_SMB_DIRETO.equals(tipo)) rbSmbDireto.setChecked(true);
        else if (PrinterManager.TIPO_BT_WINDOWS.equals(tipo)) rbBtWindows.setChecked(true);
        else if (PrinterManager.TIPO_PRINT_SERVER.equals(tipo)) rbPrintServer.setChecked(true);
        else if (PrinterManager.TIPO_REDE_RAW.equals(tipo)) {
            if (rbRedeRaw != null) rbRedeRaw.setChecked(true);
        }
        else rbNenhuma.setChecked(true);

        etIp.setText(pm.getIpImpressora());
        etPorta.setText(String.valueOf(pm.getPortaImpressora()));
        etMacBt.setText(pm.getMacBluetooth());

        // Load SMB config
        etSmbHost.setText(pm.getSmbHost());
        etSmbShare.setText(pm.getSmbShare());
        etSmbDomain.setText(pm.getSmbDomain());
        etSmbUser.setText(pm.getSmbUser());
        etSmbPassword.setText(pm.getSmbPassword());

        // Load SMB Direto config
        etSmbDiretoCaminho.setText(pm.getSmbDiretoCaminho());
        etSmbDiretoDomain.setText(pm.getSmbDiretoDomain());
        etSmbDiretoUser.setText(pm.getSmbDiretoUser());
        etSmbDiretoPassword.setText(pm.getSmbDiretoPassword());

        // Load Bluetooth Windows config
        btWinDeviceName = pm.getBtWinDeviceName();
        btWinDeviceMac = pm.getBtWinDeviceMac();
        btWinPrinterName = pm.getBtWinPrinterName();
        atualizarInfoBtWindows();

        // Load Print Server config
        etPrintServerIp.setText(pm.getPrintServerIp());
        etPrintServerPorta.setText(String.valueOf(pm.getPrintServerPorta()));
        printServerImpressora = pm.getPrintServerImpressora();
        atualizarInfoPrintServer();

        // Load Bluetooth direto config (nome do dispositivo selecionado)
        btDiretoName = pm.getBtDeviceName();
        btDiretoMac = pm.getMacBluetooth();
        if (btDiretoMac != null && !btDiretoMac.isEmpty() && !btDiretoName.isEmpty()) {
            atualizarInfoBtDireto();
        }

        // Load Rede RAW config
        if (etRedeRawIp != null) {
            String redeRawIp = pm.getRedeRawIp();
            if (redeRawIp != null && !redeRawIp.isEmpty() && !"192.168.1.200".equals(redeRawIp)) {
                etRedeRawIp.setText(redeRawIp);
            }
            etRedeRawPorta.setText(String.valueOf(pm.getRedeRawPorta()));
            String protocolo = pm.getRedeRawProtocolo();
            if (spProtocolo != null) {
                if ("LPR".equalsIgnoreCase(protocolo)) spProtocolo.setSelection(1);
                else if ("IPP".equalsIgnoreCase(protocolo)) spProtocolo.setSelection(2);
                else spProtocolo.setSelection(0);
            }
        }

        if (pm.getTamanhoPapel() == 80) rb80mm.setChecked(true);
        else rb58mm.setChecked(true);

        if (spDriverImpressora != null) {
            spDriverImpressora.setSelection(ThermalPrinterDriver.findPosition(pm.getDriverId()));
        }
    }

    private void setupListeners() {
        rgTipo.setOnCheckedChangeListener((g, id) -> updateVisibility());

        PrinterManager pm = new PrinterManager(this);

        btnSalvar.setOnClickListener(v -> salvar(pm));
        btnTestarSmb.setOnClickListener(v -> testarConexaoSmb(pm));
        btnNavegar.setOnClickListener(v -> abrirNavegadorRede());
        btnTestarSmbDireto.setOnClickListener(v -> testarConexaoSmbDireto(pm));

        // Bluetooth Windows
        btnBuscarBtWindows.setOnClickListener(v -> abrirBuscaBluetoothWindows());
        btnTestarBtWindows.setOnClickListener(v -> testarConexaoBtWindows(pm));

        // Print Server
        btnTestarPrintServer.setOnClickListener(v -> testarConexaoPrintServer(pm));
        btnBuscarImpressorasServer.setOnClickListener(v -> buscarImpressorasPrintServer(pm));

        // Bluetooth direto - busca automatica nos emparelhados
        if (btnBuscarBluetooth != null) {
            btnBuscarBluetooth.setOnClickListener(v -> buscarImpressorasBluetooth());
        }

        // Link para digitar MAC manualmente
        if (tvBtDigitarManual != null) {
            tvBtDigitarManual.setOnClickListener(v -> {
                if (layoutBtManual != null) {
                    boolean isVisible = layoutBtManual.getVisibility() == View.VISIBLE;
                    layoutBtManual.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                    tvBtDigitarManual.setText(isVisible ? "Digitar MAC manualmente" : "Ocultar campo MAC manual");
                }
            });
        }

        // Rede RAW
        if (btnTestarRedeRaw != null) {
            btnTestarRedeRaw.setOnClickListener(v -> testarConexaoRedeRaw(pm));
        }

        // Botoes de teste por tipo (podem ser null se nao existirem no layout)
        if (btnTestarRede != null) {
            btnTestarRede.setOnClickListener(v -> testarConexaoRede(pm));
        }
        if (btnTestarBluetooth != null) {
            btnTestarBluetooth.setOnClickListener(v -> testarConexaoBluetooth(pm));
        }
        if (btnTestarUsb != null) {
            btnTestarUsb.setOnClickListener(v -> testarConexaoUsb(pm));
        }
        if (btnImprimirTeste != null) {
            btnImprimirTeste.setOnClickListener(v -> imprimirPaginaTeste(pm));
        }

        // TextWatcher para validacao em tempo real do caminho SMB direto
        etSmbDiretoCaminho.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validarCaminhoDiretoEmTempoReal(s.toString());
            }
        });
    }

    private void updateVisibility() {
        layoutRede.setVisibility(rbRede.isChecked() ? View.VISIBLE : View.GONE);
        layoutBluetooth.setVisibility(rbBluetooth.isChecked() ? View.VISIBLE : View.GONE);
        layoutSmb.setVisibility(rbSmb.isChecked() ? View.VISIBLE : View.GONE);
        layoutSmbDireto.setVisibility(rbSmbDireto.isChecked() ? View.VISIBLE : View.GONE);
        layoutBtWindows.setVisibility(rbBtWindows.isChecked() ? View.VISIBLE : View.GONE);
        layoutPrintServer.setVisibility(rbPrintServer.isChecked() ? View.VISIBLE : View.GONE);
        if (layoutRedeRaw != null && rbRedeRaw != null) {
            layoutRedeRaw.setVisibility(rbRedeRaw.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    // ==================== BLUETOOTH DIRETO - BUSCA AUTOMATICA ====================

    /**
     * Busca impressoras Bluetooth nos dispositivos emparelhados (pareados).
     * Nao precisa digitar o MAC manualmente - lista automaticamente todos os
     * dispositivos Bluetooth pareados e identifica quais sao impressoras.
     */
    private void buscarImpressorasBluetooth() {
        // Verificar se Bluetooth esta disponivel
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            showError("Este dispositivo nao possui Bluetooth.\n\n"
                    + "Nao e possivel usar impressoras Bluetooth neste aparelho.");
            return;
        }

        // Verificar se Bluetooth esta ativado
        if (!btAdapter.isEnabled()) {
            try {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } catch (SecurityException e) {
                showError("Sem permissao para ativar o Bluetooth.\n\n"
                        + "Por favor, ative o Bluetooth manualmente nas configuracoes do Android.");
            }
            return;
        }

        // Verificar permissoes (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> permissionsNeeded = new ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]),
                        REQUEST_BT_PERMISSIONS);
                return;
            }
        }

        // Buscar dispositivos pareados
        executarBuscaBluetooth();
    }

    /**
     * Executa a busca nos dispositivos Bluetooth emparelhados.
     */
    private void executarBuscaBluetooth() {
        showLoading("Buscando impressoras Bluetooth nos dispositivos emparelhados...");

        new Thread(() -> {
            try {
                PrinterManager pm = new PrinterManager(this);
                List<PrinterManager.BluetoothPrinterInfo> devices = pm.listarDispositivosBtPareados();
                hideLoading();

                if (devices.isEmpty()) {
                    showError("Nenhum dispositivo Bluetooth emparelhado encontrado.\n\n"
                            + "Para usar uma impressora Bluetooth:\n\n"
                            + "1. Va em Configuracoes do Android\n"
                            + "2. Abra Bluetooth\n"
                            + "3. Ligue a impressora\n"
                            + "4. Parear (emparelhar) a impressora\n"
                            + "5. Volte aqui e toque em BUSCAR novamente\n\n"
                            + "Dica: Certifique-se de que a impressora esta ligada e em modo de pareamento.");
                    return;
                }

                // Separar impressoras provaveis dos outros dispositivos
                List<PrinterManager.BluetoothPrinterInfo> impressoras = new ArrayList<>();
                List<PrinterManager.BluetoothPrinterInfo> outros = new ArrayList<>();

                for (PrinterManager.BluetoothPrinterInfo d : devices) {
                    if (d.isLikelyPrinter) {
                        impressoras.add(d);
                    } else {
                        outros.add(d);
                    }
                }

                // Montar lista para o dialog
                List<PrinterManager.BluetoothPrinterInfo> listaFinal = new ArrayList<>();
                listaFinal.addAll(impressoras);
                listaFinal.addAll(outros);

                String[] nomes = new String[listaFinal.size()];
                for (int i = 0; i < listaFinal.size(); i++) {
                    PrinterManager.BluetoothPrinterInfo d = listaFinal.get(i);
                    String prefix = d.isLikelyPrinter ? "\uD83D\uDDA8 " : "\uD83D\uDD35 ";
                    nomes[i] = prefix + d.name + "\n      " + d.address + " - " + d.description;
                }

                runOnUiThread(() -> {
                    String titulo = "Selecione a Impressora Bluetooth";
                    if (!impressoras.isEmpty()) {
                        titulo += "\n(" + impressoras.size() + " impressora(s) identificada(s))";
                    }

                    new android.app.AlertDialog.Builder(ConfigImpressoraActivity.this)
                            .setTitle(titulo)
                            .setItems(nomes, (dialog, which) -> {
                                PrinterManager.BluetoothPrinterInfo selected = listaFinal.get(which);
                                selecionarDispositivoBluetooth(selected);
                            })
                            .setNegativeButton("Cancelar", null)
                            .setNeutralButton("Atualizar", (dialog, which) -> {
                                buscarImpressorasBluetooth();
                            })
                            .show();
                });

            } catch (Exception e) {
                hideLoading();
                showError("Erro ao buscar dispositivos Bluetooth:\n\n" + e.getMessage()
                        + "\n\nVerifique se o Bluetooth esta ativado e tente novamente.");
            }
        }).start();
    }

    /**
     * Seleciona um dispositivo Bluetooth da lista de emparelhados.
     */
    private void selecionarDispositivoBluetooth(PrinterManager.BluetoothPrinterInfo device) {
        btDiretoName = device.name;
        btDiretoMac = device.address;
        btDiretoType = device.description;

        // Atualizar o campo MAC oculto para compatibilidade
        if (etMacBt != null) {
            etMacBt.setText(device.address);
        }

        // Atualizar painel de informacoes
        atualizarInfoBtDireto();

        showToast("Impressora selecionada: " + device.name);
    }

    /**
     * Atualiza o painel de informacoes do dispositivo Bluetooth selecionado.
     */
    private void atualizarInfoBtDireto() {
        if (layoutBtSelecionado == null) return;

        if (btDiretoMac != null && !btDiretoMac.isEmpty()) {
            layoutBtSelecionado.setVisibility(View.VISIBLE);

            if (tvBtDeviceName != null) {
                tvBtDeviceName.setText(btDiretoName != null && !btDiretoName.isEmpty()
                        ? btDiretoName : "Dispositivo Bluetooth");
            }
            if (tvBtDeviceMac != null) {
                tvBtDeviceMac.setText("MAC: " + btDiretoMac);
            }
            if (tvBtDeviceType != null) {
                tvBtDeviceType.setText(btDiretoType != null && !btDiretoType.isEmpty()
                        ? btDiretoType : "Dispositivo Bluetooth");
            }
            if (tvBtStatus != null) {
                tvBtStatus.setText("Pronto para testar - Clique em TESTAR CONEXAO");
                tvBtStatus.setTextColor(getResources().getColor(R.color.colorWarning));
            }
        } else {
            layoutBtSelecionado.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                executarBuscaBluetooth();
            } else {
                showError("Permissoes de Bluetooth necessarias nao foram concedidas.\n\n"
                        + "Para buscar impressoras Bluetooth, o app precisa das permissoes de "
                        + "Bluetooth.\n\n"
                        + "Voce pode conceder nas Configuracoes do Android > Aplicativos > PDV Pro > Permissoes.\n\n"
                        + "Alternativa: Use o campo 'Digitar MAC manualmente' abaixo.");
                // Mostrar campo manual como fallback
                if (layoutBtManual != null) {
                    layoutBtManual.setVisibility(View.VISIBLE);
                    if (tvBtDigitarManual != null) {
                        tvBtDigitarManual.setText("Ocultar campo MAC manual");
                    }
                }
            }
        }
    }

    // ==================== TESTES DE CONEXAO POR TIPO ====================

    /**
     * Testa a conexao com a impressora de rede TCP/IP.
     */
    private void testarConexaoRede(PrinterManager pm) {
        String ip = etIp.getText().toString().trim();
        String portaStr = etPorta.getText().toString().trim();

        if (ip.isEmpty()) {
            showError("Informe o IP da impressora.");
            return;
        }

        int porta = 9100;
        try { porta = Integer.parseInt(portaStr); } catch (Exception ignored) {}

        // Salvar temporariamente para teste
        pm.saveConfig(PrinterManager.TIPO_REDE, ip, porta, "", rb80mm.isChecked() ? 80 : 58);

        showLoading("Testando conexao com " + ip + ":" + porta + "...");
        new Thread(() -> {
            boolean ok = pm.testarConexaoRede();
            hideLoading();
            if (ok) {
                showSuccess("Conexao com a impressora de rede bem-sucedida!\n\n"
                        + "IP: " + ip + "\n\n"
                        + "A impressora esta pronta para uso.");
            } else {
                showError("Falha na conexao com a impressora de rede.\n\n"
                        + "IP: " + ip + "\n\n"
                        + "Verifique:\n"
                        + "- A impressora esta ligada\n"
                        + "- O IP esta correto\n"
                        + "- A porta esta correta (padrao: 9100)\n"
                        + "- Ambos estao na mesma rede");
            }
        }).start();
    }

    /**
     * Testa a conexao Bluetooth direta.
     */
    private void testarConexaoBluetooth(PrinterManager pm) {
        String mac = etMacBt.getText().toString().trim();
        if (mac.isEmpty()) {
            showError("Nenhuma impressora Bluetooth selecionada.\n\n"
                    + "Toque em 'BUSCAR IMPRESSORAS BLUETOOTH' para selecionar uma impressora "
                    + "dos dispositivos emparelhados.\n\n"
                    + "Ou use 'Digitar MAC manualmente' para informar o endereco MAC.");
            return;
        }

        // Salvar temporariamente para teste
        pm.saveConfig(PrinterManager.TIPO_BLUETOOTH, "", 9100, mac, rb80mm.isChecked() ? 80 : 58);

        showLoading("Testando conexao Bluetooth...\n\n"
                + (btDiretoName != null && !btDiretoName.isEmpty() ? "Impressora: " + btDiretoName + "\n" : "")
                + "MAC: " + mac + "\n\nAguarde...");
        new Thread(() -> {
            boolean ok = pm.testarConexaoBluetooth();
            hideLoading();
            if (ok) {
                runOnUiThread(() -> {
                    if (tvBtStatus != null) {
                        tvBtStatus.setText("Conexao testada com sucesso!");
                        tvBtStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
                    }
                });
                showSuccess("Conexao Bluetooth bem-sucedida!\n\n"
                        + (btDiretoName != null && !btDiretoName.isEmpty() ? "Impressora: " + btDiretoName + "\n" : "")
                        + "MAC: " + mac + "\n\n"
                        + "A impressora Bluetooth esta pronta para uso.\n\n"
                        + "Clique em SALVAR para confirmar a configuracao.");
            } else {
                runOnUiThread(() -> {
                    if (tvBtStatus != null) {
                        tvBtStatus.setText("Falha no teste de conexao");
                        tvBtStatus.setTextColor(getResources().getColor(R.color.colorDanger));
                    }
                });
                showError("Falha na conexao Bluetooth.\n\n"
                        + "MAC: " + mac + "\n\n"
                        + "Verifique:\n"
                        + "- A impressora esta ligada\n"
                        + "- O Bluetooth do celular esta ativado\n"
                        + "- A impressora esta pareada nas configuracoes Bluetooth\n"
                        + "- A impressora esta proxima (ate ~10 metros)");
            }
        }).start();
    }

    /**
     * Testa a conexao USB.
     */
    private void testarConexaoUsb(PrinterManager pm) {
        showLoading("Verificando impressoras USB conectadas...");
        new Thread(() -> {
            boolean ok = pm.testarConexaoUsb();
            hideLoading();
            if (ok) {
                showSuccess("Impressora USB encontrada!\n\n"
                        + "A impressora USB esta pronta para uso.\n\n"
                        + "Nota: Se solicitado, conceda permissao de acesso ao dispositivo USB.");
            } else {
                showError("Nenhuma impressora USB encontrada.\n\n"
                        + "Verifique:\n"
                        + "- A impressora esta conectada via cabo USB\n"
                        + "- A impressora esta ligada\n"
                        + "- O cabo USB esta funcionando\n"
                        + "- Se necessario, use um adaptador USB OTG");
            }
        }).start();
    }

    /**
     * Imprime uma pagina de teste na impressora configurada.
     */
    private void imprimirPaginaTeste(PrinterManager pm) {
        if (!pm.isImpressoraConfigurada()) {
            showError("Nenhuma impressora configurada.\n\nSelecione um tipo de impressora e salve a configuracao primeiro.");
            return;
        }

        showLoading("Imprimindo pagina de teste...");
        new Thread(() -> {
            boolean ok = pm.imprimirPaginaTeste();
            hideLoading();
            if (ok) {
                showSuccess("Pagina de teste enviada com sucesso!\n\n"
                        + "Verifique se a impressao saiu corretamente.\n"
                        + "Se os caracteres acentuados nao apareceram corretamente, "
                        + "tente trocar o tamanho do papel.");
            } else {
                showError("Falha ao imprimir pagina de teste.\n\n"
                        + "Verifique a conexao com a impressora e tente novamente.");
            }
        }).start();
    }

    // ==================== REDE RAW/LPR ====================

    /**
     * Testa a conexao com a impressora via Rede IP Direto (RAW/LPR).
     */
    private void testarConexaoRedeRaw(PrinterManager pm) {
        if (etRedeRawIp == null) return;

        String ip = etRedeRawIp.getText().toString().trim();
        if (ip.isEmpty()) {
            showError("Informe o IP da impressora.\n\nExemplo: 192.168.1.200");
            return;
        }

        int porta = 9100;
        try {
            if (etRedeRawPorta != null && !etRedeRawPorta.getText().toString().isEmpty()) {
                porta = Integer.parseInt(etRedeRawPorta.getText().toString().trim());
            }
        } catch (Exception ignored) {}

        String protocolo = "RAW";
        if (spProtocolo != null) {
            int pos = spProtocolo.getSelectedItemPosition();
            if (pos == 1) protocolo = "LPR";
            else if (pos == 2) protocolo = "IPP";
        }

        // Salvar temporariamente para teste
        pm.saveRedeRawConfig(ip, porta, protocolo);

        final String ipFinal = ip;
        final int portaFinal = porta;
        final String protocoloFinal = protocolo;

        showLoading("Testando conexao Rede IP Direto...\n\n"
                + "IP: " + ip + "\n"
                + "Porta: " + porta + "\n"
                + "Protocolo: " + protocolo + "\n\n"
                + "Aguarde...");

        new Thread(() -> {
            boolean ok = pm.testarConexaoRedeRaw();
            hideLoading();
            if (ok) {
                showSuccess("Conexao Rede IP Direto bem-sucedida!\n\n"
                        + "IP: " + ipFinal + "\n"
                        + "Porta: " + portaFinal + "\n"
                        + "Protocolo: " + protocoloFinal + "\n\n"
                        + "A impressora esta pronta para uso.\n"
                        + "Clique em SALVAR para confirmar.");
            } else {
                showError("Falha na conexao Rede IP Direto.\n\n"
                        + "IP: " + ipFinal + "\n"
                        + "Porta: " + portaFinal + "\n\n"
                        + "Verifique:\n"
                        + "- A impressora esta ligada\n"
                        + "- O IP esta correto\n"
                        + "- A porta esta correta (RAW: 9100, LPR: 515, IPP: 631)\n"
                        + "- Ambos estao na mesma rede\n"
                        + "- A impressora suporta o protocolo selecionado");
            }
        }).start();
    }

    // ==================== PRINT SERVER (PC) ====================

    /**
     * Testa a conexao com o servidor de impressao Python.
     */
    private void testarConexaoPrintServer(PrinterManager pm) {
        String ip = etPrintServerIp.getText().toString().trim();
        String portaStr = etPrintServerPorta.getText().toString().trim();

        if (ip.isEmpty()) {
            showError("Informe o IP do computador onde o servidor de impressao esta rodando.\n\n"
                    + "Exemplo: 192.168.1.100\n\n"
                    + "Voce pode ver o IP no servidor quando ele iniciar.");
            return;
        }

        int porta = 9200;
        try { porta = Integer.parseInt(portaStr); } catch (Exception ignored) {}

        // Salvar temporariamente para teste
        pm.savePrintServerConfig(ip, porta, printServerImpressora);

        final String ipFinal = ip;
        final int portaFinal = porta;

        showLoading("Testando conexao com o servidor de impressao...\n\n"
                + "IP: " + ip + "\n"
                + "Porta: " + porta + "\n\n"
                + "Aguarde...");

        new Thread(() -> {
            boolean ok = pm.testarConexaoPrintServer();
            hideLoading();
            if (ok) {
                runOnUiThread(() -> {
                    if (tvPrintServerStatus != null) {
                        tvPrintServerStatus.setText("Servidor online - Conexao OK!");
                        tvPrintServerStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
                    }
                });
                showSuccess("Conexao com o servidor de impressao bem-sucedida!\n\n"
                        + "IP: " + ipFinal + "\n"
                        + "Porta: " + portaFinal + "\n\n"
                        + "O servidor esta online e pronto para receber impressoes.\n\n"
                        + "Agora clique em 'BUSCAR IMPRESSORAS DO PC' para selecionar a impressora.");
            } else {
                runOnUiThread(() -> {
                    if (tvPrintServerStatus != null) {
                        tvPrintServerStatus.setText("Falha na conexao");
                        tvPrintServerStatus.setTextColor(getResources().getColor(R.color.colorDanger));
                    }
                });
                showError("Falha na conexao com o servidor de impressao.\n\n"
                        + "IP: " + ipFinal + "\n"
                        + "Porta: " + portaFinal + "\n\n"
                        + "Verifique:\n"
                        + "- O PDV_Print_Server.py esta rodando no PC\n"
                        + "- O IP e a porta estao corretos\n"
                        + "- O celular e o PC estao na mesma rede Wi-Fi\n"
                        + "- O firewall do Windows permite a porta " + portaFinal + "\n\n"
                        + "Dica: Execute no PC:\n"
                        + "python PDV_Print_Server.py");
            }
        }).start();
    }

    /**
     * Busca as impressoras disponiveis no servidor de impressao Python.
     */
    private void buscarImpressorasPrintServer(PrinterManager pm) {
        String ip = etPrintServerIp.getText().toString().trim();
        String portaStr = etPrintServerPorta.getText().toString().trim();

        if (ip.isEmpty()) {
            showError("Informe o IP do computador primeiro.");
            return;
        }

        int porta = 9200;
        try { porta = Integer.parseInt(portaStr); } catch (Exception ignored) {}

        pm.savePrintServerConfig(ip, porta, printServerImpressora);

        showLoading("Buscando impressoras no servidor...\n\n"
                + "IP: " + ip + ":" + porta);

        new Thread(() -> {
            List<String> impressoras = pm.listarImpressorasPrintServer();
            hideLoading();

            if (impressoras.isEmpty()) {
                showError("Nenhuma impressora encontrada no servidor.\n\n"
                        + "Verifique:\n"
                        + "- O servidor esta rodando\n"
                        + "- O PC tem impressoras instaladas\n"
                        + "- A conexao esta funcionando (teste primeiro)");
                return;
            }

            // Mostrar dialog para selecionar impressora
            final String[] items = impressoras.toArray(new String[0]);
            runOnUiThread(() -> {
                new android.app.AlertDialog.Builder(ConfigImpressoraActivity.this)
                        .setTitle("Selecione a Impressora do PC")
                        .setItems(items, (dialog, which) -> {
                            String selecionada = items[which];
                            // Remover sufixo [PADRAO] se existir
                            if (selecionada.endsWith(" [PADRAO]")) {
                                selecionada = selecionada.replace(" [PADRAO]", "");
                            }
                            printServerImpressora = selecionada;
                            atualizarInfoPrintServer();
                            showSuccess("Impressora selecionada: " + selecionada + "\n\n"
                                    + "Clique em SALVAR para confirmar a configuracao.");
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }).start();
    }

    /**
     * Atualiza as informacoes do Print Server na tela.
     */
    private void atualizarInfoPrintServer() {
        if (printServerImpressora != null && !printServerImpressora.isEmpty()) {
            if (layoutPrintServerSelecionada != null) {
                layoutPrintServerSelecionada.setVisibility(View.VISIBLE);
            }
            if (tvPrintServerImpressora != null) {
                tvPrintServerImpressora.setText(printServerImpressora);
            }
            if (tvPrintServerStatus != null) {
                tvPrintServerStatus.setText("Impressora configurada");
                tvPrintServerStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
            }
        } else {
            if (layoutPrintServerSelecionada != null) {
                layoutPrintServerSelecionada.setVisibility(View.GONE);
            }
        }
    }

    // ==================== BLUETOOTH WINDOWS ====================

    private void abrirBuscaBluetoothWindows() {
        Intent intent = new Intent(this, BluetoothWindowsBrowserActivity.class);
        startActivityForResult(intent, REQUEST_BT_WINDOWS_BROWSER);
    }

    private void atualizarInfoBtWindows() {
        if (btWinDeviceMac != null && !btWinDeviceMac.isEmpty()) {
            layoutBtWinSelecionado.setVisibility(View.VISIBLE);
            btnTestarBtWindows.setVisibility(View.VISIBLE);

            tvBtWinDeviceName.setText(btWinDeviceName != null && !btWinDeviceName.isEmpty()
                    ? btWinDeviceName : "Dispositivo Bluetooth");
            tvBtWinDeviceMac.setText("MAC: " + btWinDeviceMac);

            if (btWinPrinterName != null && !btWinPrinterName.isEmpty()) {
                tvBtWinPrinterName.setText(btWinPrinterName);
                tvBtWinStatus.setText("Configurado - Computador + Impressora selecionados");
                tvBtWinStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
            } else {
                tvBtWinPrinterName.setText("(nenhuma selecionada)");
                tvBtWinStatus.setText("Computador selecionado - Impressora nao definida");
                tvBtWinStatus.setTextColor(getResources().getColor(R.color.colorWarning));
            }
        } else {
            layoutBtWinSelecionado.setVisibility(View.GONE);
            btnTestarBtWindows.setVisibility(View.GONE);
        }
    }

    private void testarConexaoBtWindows(PrinterManager pm) {
        if (btWinDeviceMac == null || btWinDeviceMac.isEmpty()) {
            showError("Nenhum dispositivo Bluetooth Windows selecionado.\n\n"
                    + "Toque em 'BUSCAR COMPUTADORES BLUETOOTH' para selecionar um dispositivo.");
            return;
        }

        String printerInfo = "";
        if (btWinPrinterName != null && !btWinPrinterName.isEmpty()) {
            printerInfo = "\nImpressora: " + btWinPrinterName;
        }

        showLoading("Testando conexao Bluetooth...\n\n"
                + "Computador: " + btWinDeviceName + "\n"
                + "MAC: " + btWinDeviceMac
                + printerInfo + "\n\n"
                + "Aguarde...");

        new Thread(() -> {
            BluetoothWindowsPrintManager btManager = new BluetoothWindowsPrintManager(this);
            boolean ok = btManager.testarConexao(btWinDeviceMac);
            hideLoading();

            if (ok) {
                runOnUiThread(() -> {
                    tvBtWinStatus.setText("Conexao testada com sucesso!");
                    tvBtWinStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
                });
                String successMsg = "Conexao Bluetooth bem-sucedida!\n\n"
                        + "Computador: " + btWinDeviceName + "\n"
                        + "MAC: " + btWinDeviceMac;
                if (btWinPrinterName != null && !btWinPrinterName.isEmpty()) {
                    successMsg += "\nImpressora: " + btWinPrinterName;
                }
                successMsg += "\n\nA impressao sera enviada via Bluetooth para a impressora "
                        + "do Painel de Controle do Windows.\n\n"
                        + "Clique em SALVAR para confirmar a configuracao.";
                showSuccess(successMsg);
            } else {
                runOnUiThread(() -> {
                    tvBtWinStatus.setText("Falha no teste de conexao");
                    tvBtWinStatus.setTextColor(getResources().getColor(R.color.colorDanger));
                });
                showError("Falha na conexao Bluetooth.\n\n"
                        + "Computador: " + btWinDeviceName + "\n"
                        + "MAC: " + btWinDeviceMac + "\n\n"
                        + "Verifique:\n"
                        + "- O Bluetooth do computador Windows esta ativado\n"
                        + "- Os dispositivos estao pareados\n"
                        + "- O computador esta proximo (ate ~10 metros)\n"
                        + "- O servico Bluetooth do Windows esta funcionando\n\n"
                        + "Dica: Tente parear os dispositivos primeiro nas configuracoes do Android.");
            }
        }).start();
    }

    // ==================== SMB ====================

    private void validarCaminhoDiretoEmTempoReal(String caminho) {
        if (caminho == null || caminho.trim().isEmpty()) {
            tvSmbDiretoStatus.setVisibility(View.GONE);
            return;
        }

        tvSmbDiretoStatus.setVisibility(View.VISIBLE);

        if (PrinterManager.validarCaminhoWindows(caminho)) {
            String servidor = PrinterManager.extrairServidor(caminho);
            String compartilhamento = PrinterManager.extrairCompartilhamento(caminho);
            String smbUrl = PrinterManager.windowsPathToSmbUrl(caminho);

            tvSmbDiretoStatus.setTextColor(getResources().getColor(R.color.colorSuccess));
            tvSmbDiretoStatus.setText("Formato valido\n"
                    + "Servidor: " + servidor + "\n"
                    + "Impressora: " + compartilhamento + "\n"
                    + "URL: " + smbUrl);
        } else {
            tvSmbDiretoStatus.setTextColor(getResources().getColor(R.color.colorDanger));
            tvSmbDiretoStatus.setText("Formato invalido. Use: \\\\SERVIDOR\\IMPRESSORA\n"
                    + "Exemplo: \\\\DESKTOP-K8R2HBI\\mp-4200 th");
        }
    }

    private void abrirNavegadorRede() {
        Intent intent = new Intent(this, SmbBrowserActivity.class);
        intent.putExtra(SmbBrowserActivity.EXTRA_SMB_DOMAIN, etSmbDomain.getText().toString().trim());
        intent.putExtra(SmbBrowserActivity.EXTRA_SMB_USER, etSmbUser.getText().toString().trim());
        intent.putExtra(SmbBrowserActivity.EXTRA_SMB_PASSWORD, etSmbPassword.getText().toString());
        startActivityForResult(intent, REQUEST_SMB_BROWSER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                executarBuscaBluetooth();
            } else {
                showError("Bluetooth nao foi ativado.\n\nPor favor, ative o Bluetooth para buscar impressoras.");
            }
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null) return;

        if (requestCode == REQUEST_SMB_BROWSER) {
            String host = data.getStringExtra(SmbBrowserActivity.EXTRA_SMB_HOST);
            String share = data.getStringExtra(SmbBrowserActivity.EXTRA_SMB_SHARE);
            String domain = data.getStringExtra(SmbBrowserActivity.EXTRA_SMB_DOMAIN);
            String user = data.getStringExtra(SmbBrowserActivity.EXTRA_SMB_USER);
            String password = data.getStringExtra(SmbBrowserActivity.EXTRA_SMB_PASSWORD);

            if (host != null) etSmbHost.setText(host);
            if (share != null) etSmbShare.setText(share);
            if (domain != null && !domain.isEmpty()) etSmbDomain.setText(domain);
            if (user != null) etSmbUser.setText(user);
            if (password != null) etSmbPassword.setText(password);

            showSuccess("Impressora selecionada!\n\nHost: " + host + "\nCompartilhamento: " + share
                    + "\n\nCaminho: smb://" + host + "/" + share
                    + "\n\nClique em SALVAR para confirmar.");

        } else if (requestCode == REQUEST_BT_WINDOWS_BROWSER) {
            btWinDeviceName = data.getStringExtra(BluetoothWindowsBrowserActivity.EXTRA_BT_DEVICE_NAME);
            btWinDeviceMac = data.getStringExtra(BluetoothWindowsBrowserActivity.EXTRA_BT_DEVICE_MAC);
            btWinPrinterName = data.getStringExtra(BluetoothWindowsBrowserActivity.EXTRA_BT_PRINTER_NAME);

            if (btWinDeviceName == null) btWinDeviceName = "";
            if (btWinDeviceMac == null) btWinDeviceMac = "";
            if (btWinPrinterName == null) btWinPrinterName = "";

            atualizarInfoBtWindows();

            String successMsg = "Configuracao Bluetooth Windows concluida!\n\n"
                    + "Computador: " + btWinDeviceName + "\n"
                    + "MAC: " + btWinDeviceMac;
            if (!btWinPrinterName.isEmpty()) {
                successMsg += "\nImpressora: " + btWinPrinterName
                        + "\n\n(Impressora do Painel de Controle do Windows)";
            }
            successMsg += "\n\nClique em SALVAR para confirmar a configuracao.";
            showSuccess(successMsg);
        }
    }

    private void testarConexaoSmb(PrinterManager pm) {
        String smbHost = etSmbHost.getText().toString().trim();
        String smbShare = etSmbShare.getText().toString().trim();
        String smbUser = etSmbUser.getText().toString().trim();
        String smbPassword = etSmbPassword.getText().toString();
        String smbDomain = etSmbDomain.getText().toString().trim();

        if (smbHost.isEmpty()) {
            showError("Por favor, informe o endereco IP ou nome do computador onde a impressora esta compartilhada.");
            return;
        }
        if (smbShare.isEmpty()) {
            showError("Por favor, informe o nome do compartilhamento da impressora na rede.\n\nExemplo: ImpressoraTermica");
            return;
        }

        pm.saveSmbConfig(smbHost, smbShare, smbUser, smbPassword, smbDomain);

        showLoading("Testando conexao SMB/CIFS...");
        new Thread(() -> {
            boolean ok = pm.testarConexaoSmb();
            hideLoading();
            if (ok) {
                showSuccess("Conexao SMB/CIFS bem-sucedida!\n\nA impressora compartilhada foi encontrada em:\nsmb://" + smbHost + "/" + smbShare);
            } else {
                showError("Falha na conexao SMB/CIFS.\n\nVerifique:\n"
                        + "- O computador esta ligado e na mesma rede\n"
                        + "- O nome do compartilhamento esta correto\n"
                        + "- O usuario e senha estao corretos\n"
                        + "- O firewall do Windows permite acesso SMB (porta 445)");
            }
        }).start();
    }

    private void testarConexaoSmbDireto(PrinterManager pm) {
        String caminho = etSmbDiretoCaminho.getText().toString().trim();
        String user = etSmbDiretoUser.getText().toString().trim();
        String password = etSmbDiretoPassword.getText().toString();
        String domain = etSmbDiretoDomain.getText().toString().trim();

        if (caminho.isEmpty()) {
            showError("Por favor, informe o caminho da impressora.\n\nExemplo: \\\\DESKTOP-K8R2HBI\\mp-4200 th");
            return;
        }

        if (!PrinterManager.validarCaminhoWindows(caminho)) {
            showError("O caminho informado esta no formato incorreto.\n\n"
                    + "Use o formato: \\\\SERVIDOR\\IMPRESSORA\n\n"
                    + "Exemplos:\n"
                    + "\\\\DESKTOP-K8R2HBI\\mp-4200 th\n"
                    + "\\\\192.168.1.100\\EPSON_TM");
            return;
        }

        String smbUrl = PrinterManager.windowsPathToSmbUrl(caminho);
        String servidor = PrinterManager.extrairServidor(caminho);
        String compartilhamento = PrinterManager.extrairCompartilhamento(caminho);

        showLoading("Testando conexao direta...\n\nServidor: " + servidor + "\nImpressora: " + compartilhamento);

        new Thread(() -> {
            boolean ok = pm.testarConexaoSmbDireto(caminho, user, password, domain);
            hideLoading();
            if (ok) {
                showSuccess("Conexao direta bem-sucedida!\n\n"
                        + "Servidor: " + servidor + "\n"
                        + "Impressora: " + compartilhamento + "\n"
                        + "URL: " + smbUrl + "\n\n"
                        + "A impressora esta pronta para uso.\n"
                        + "Clique em SALVAR para confirmar.");
            } else {
                showError("Falha na conexao direta.\n\n"
                        + "Caminho: " + caminho + "\n"
                        + "URL tentada: " + smbUrl + "\n\n"
                        + "Verifique:\n"
                        + "- O computador '" + servidor + "' esta ligado\n"
                        + "- Ambos estao na mesma rede Wi-Fi\n"
                        + "- A impressora '" + compartilhamento + "' esta compartilhada\n"
                        + "- O firewall do Windows permite acesso (porta 445)\n"
                        + "- Se necessario, informe usuario e senha");
            }
        }).start();
    }

    // ==================== SALVAR ====================

    private void salvar(PrinterManager pm) {
        String tipo;
        if (rbRede.isChecked()) tipo = PrinterManager.TIPO_REDE;
        else if (rbBluetooth.isChecked()) tipo = PrinterManager.TIPO_BLUETOOTH;
        else if (rbUsb.isChecked()) tipo = PrinterManager.TIPO_USB;
        else if (rbSmb.isChecked()) tipo = PrinterManager.TIPO_SMB;
        else if (rbSmbDireto.isChecked()) tipo = PrinterManager.TIPO_SMB_DIRETO;
        else if (rbBtWindows.isChecked()) tipo = PrinterManager.TIPO_BT_WINDOWS;
        else if (rbPrintServer.isChecked()) tipo = PrinterManager.TIPO_PRINT_SERVER;
        else if (rbRedeRaw != null && rbRedeRaw.isChecked()) tipo = PrinterManager.TIPO_REDE_RAW;
        else tipo = PrinterManager.TIPO_NENHUMA;

        String ip = etIp.getText().toString().trim();
        int porta = 9100;
        try { porta = Integer.parseInt(etPorta.getText().toString().trim()); } catch (Exception ignored) {}
        String macBt = etMacBt.getText().toString().trim();
        int tamanhoPapel = rb80mm.isChecked() ? 80 : 58;

        pm.saveConfig(tipo, ip, porta, macBt, tamanhoPapel);
        if (spDriverImpressora != null) {
            String[] ids = ThermalPrinterDriver.getIds();
            int posDriver = spDriverImpressora.getSelectedItemPosition();
            if (posDriver >= 0 && posDriver < ids.length) pm.saveDriverConfig(ids[posDriver]);
        }

        // Salvar nome do dispositivo Bluetooth selecionado
        if (PrinterManager.TIPO_BLUETOOTH.equals(tipo)) {
            pm.saveBtDeviceName(btDiretoName != null ? btDiretoName : "");
        }

        // Save SMB config
        String smbHost = etSmbHost.getText().toString().trim();
        String smbShare = etSmbShare.getText().toString().trim();
        String smbUser = etSmbUser.getText().toString().trim();
        String smbPassword = etSmbPassword.getText().toString();
        String smbDomain = etSmbDomain.getText().toString().trim();
        pm.saveSmbConfig(smbHost, smbShare, smbUser, smbPassword, smbDomain);

        // Save SMB Direto config
        String smbDiretoCaminho = etSmbDiretoCaminho.getText().toString().trim();
        String smbDiretoUser = etSmbDiretoUser.getText().toString().trim();
        String smbDiretoPassword = etSmbDiretoPassword.getText().toString();
        String smbDiretoDomain = etSmbDiretoDomain.getText().toString().trim();

        // Validar caminho SMB direto antes de salvar (se selecionado)
        if (PrinterManager.TIPO_SMB_DIRETO.equals(tipo)) {
            if (smbDiretoCaminho.isEmpty()) {
                showError("Por favor, informe o caminho da impressora.\n\nExemplo: \\\\DESKTOP-K8R2HBI\\mp-4200 th");
                return;
            }
            if (!PrinterManager.validarCaminhoWindows(smbDiretoCaminho)) {
                showError("O caminho informado esta no formato incorreto.\n\n"
                        + "Use o formato: \\\\SERVIDOR\\IMPRESSORA\n\n"
                        + "Exemplos:\n"
                        + "\\\\DESKTOP-K8R2HBI\\mp-4200 th\n"
                        + "\\\\192.168.1.100\\EPSON_TM");
                return;
            }
        }

        pm.saveSmbDiretoConfig(smbDiretoCaminho, smbDiretoUser, smbDiretoPassword, smbDiretoDomain);

        // Validar e salvar Bluetooth Windows
        if (PrinterManager.TIPO_BT_WINDOWS.equals(tipo)) {
            if (btWinDeviceMac == null || btWinDeviceMac.isEmpty()) {
                showError("Nenhum dispositivo Bluetooth Windows selecionado.\n\n"
                        + "Toque em 'BUSCAR COMPUTADORES BLUETOOTH' para selecionar um computador.");
                return;
            }
        }

        pm.saveBtWindowsConfig(btWinDeviceName, btWinDeviceMac, btWinPrinterName);

        // Validar e salvar Print Server
        if (PrinterManager.TIPO_PRINT_SERVER.equals(tipo)) {
            String psIp = etPrintServerIp.getText().toString().trim();
            if (psIp.isEmpty()) {
                showError("Informe o IP do computador onde o servidor de impressao esta rodando.");
                return;
            }
        }

        String psIp = etPrintServerIp.getText().toString().trim();
        int psPorta = 9200;
        try { psPorta = Integer.parseInt(etPrintServerPorta.getText().toString().trim()); } catch (Exception ignored) {}
        pm.savePrintServerConfig(psIp, psPorta, printServerImpressora);

        // Validar e salvar Rede RAW
        if (PrinterManager.TIPO_REDE_RAW.equals(tipo)) {
            if (etRedeRawIp != null) {
                String redeRawIp = etRedeRawIp.getText().toString().trim();
                if (redeRawIp.isEmpty()) {
                    showError("Informe o IP da impressora para conexao Rede IP Direto.");
                    return;
                }
                int redeRawPorta = 9100;
                try {
                    if (etRedeRawPorta != null && !etRedeRawPorta.getText().toString().isEmpty()) {
                        redeRawPorta = Integer.parseInt(etRedeRawPorta.getText().toString().trim());
                    }
                } catch (Exception ignored) {}

                String protocolo = "RAW";
                if (spProtocolo != null) {
                    int pos = spProtocolo.getSelectedItemPosition();
                    if (pos == 1) protocolo = "LPR";
                    else if (pos == 2) protocolo = "IPP";
                }

                pm.saveRedeRawConfig(redeRawIp, redeRawPorta, protocolo);
            }
        }

        // Validar Bluetooth direto
        if (PrinterManager.TIPO_BLUETOOTH.equals(tipo)) {
            if (macBt.isEmpty()) {
                showError("Nenhuma impressora Bluetooth selecionada.\n\n"
                        + "Toque em 'BUSCAR IMPRESSORAS BLUETOOTH' para selecionar uma impressora "
                        + "dos dispositivos emparelhados.\n\n"
                        + "Ou use 'Digitar MAC manualmente' para informar o endereco MAC.");
                return;
            }
        }

        showToast("Configuracao salva!");
        finish();
    }
}
