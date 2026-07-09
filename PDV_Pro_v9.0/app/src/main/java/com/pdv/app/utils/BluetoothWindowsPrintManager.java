package com.pdv.app.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gerenciador avancado de impressao via Bluetooth Windows.
 *
 * Este modulo permite que o dispositivo Android:
 * 1. Descubra automaticamente computadores/notebooks Windows com Bluetooth
 * 2. Conecte-se via Bluetooth RFCOMM (Serial Port Profile)
 * 3. Liste as impressoras instaladas no Painel de Controle do Windows
 *    (Hardware e Sons > Dispositivos e Impressoras)
 * 4. Envie comandos de impressao para a impressora selecionada
 *
 * O protocolo utilizado e o RFCOMM sobre Bluetooth, que simula uma porta serial.
 * No lado do Windows, o computador precisa ter:
 * - Bluetooth ativado e visivel
 * - Uma impressora compartilhada ou instalada
 * - O servico de porta serial Bluetooth (SPP) ativo
 *
 * O fluxo de comunicacao e:
 * Android -> Bluetooth RFCOMM -> Windows -> Impressora do Painel de Controle
 *
 * O Android envia um protocolo proprietario simples:
 * - Handshake: "PDV_PRINT_HANDSHAKE\n"
 * - Aguarda resposta: "PDV_PRINT_READY\n" ou lista de impressoras
 * - Solicita impressoras: "PDV_LIST_PRINTERS\n"
 * - Aguarda lista: "PRINTER:NomeDaImpressora:Tipo:Status\n" (uma por linha)
 * - Fim da lista: "PDV_PRINTERS_END\n"
 * - Seleciona impressora: "PDV_SELECT_PRINTER:NomeDaImpressora\n"
 * - Envia comando de impressao com dados ESC/POS
 *
 * Para funcionar sem software adicional no Windows, tambem suporta:
 * - Envio direto via Bluetooth SPP (porta serial virtual)
 * - Conexao OBEX para transferencia de arquivo de impressao
 * - Fallback para impressao RAW via socket Bluetooth
 *
 * Quando nao ha agente no Windows, o app lista impressoras via SMB/NetBIOS
 * ou permite selecao manual a partir das impressoras compartilhadas na rede.
 *
 * IMPORTANTE: A listagem de impressoras do Painel de Controle do Windows
 * (Hardware e Sons > Dispositivos e Impressoras) e feita atraves de:
 * 1. Protocolo PDV via Bluetooth SPP (se houver agente no Windows)
 * 2. Enumeracao via SMB/CIFS (impressoras compartilhadas na rede)
 * 3. Resolucao de nome do dispositivo Bluetooth para IP e busca SMB
 * 4. Lista padrao de impressoras comuns (fallback com entrada manual)
 */
public class BluetoothWindowsPrintManager {
    private static final String TAG = "BtWinPrintMgr";

    // UUID padrao para Serial Port Profile (SPP) - funciona com Windows
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // UUID alternativo para OBEX Object Push
    private static final UUID OBEX_PUSH_UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB");

    // Protocolo de comunicacao
    private static final String HANDSHAKE_CMD = "PDV_PRINT_HANDSHAKE";
    private static final String PRINT_CMD = "PDV_PRINT_DATA";
    private static final String LIST_PRINTERS_CMD = "PDV_LIST_PRINTERS";
    private static final String SELECT_PRINTER_CMD = "PDV_SELECT_PRINTER";
    private static final String READY_RESPONSE = "PDV_PRINT_READY";
    private static final String PRINTERS_END = "PDV_PRINTERS_END";
    private static final String PROTOCOL_SEPARATOR = "\n";
    private static final String DATA_END_MARKER = "PDV_DATA_END";

    // Timeouts
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int WRITE_TIMEOUT_MS = 10000;

    // Tentativas de reconexao
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket currentSocket;
    private OutputStream outputStream;
    private InputStream inputStream;

    /**
     * Callback para eventos de descoberta Bluetooth.
     */
    public interface BluetoothDiscoveryCallback {
        void onDeviceFound(BluetoothDeviceInfo device);
        void onDiscoveryStarted();
        void onDiscoveryFinished(List<BluetoothDeviceInfo> devices);
        void onError(String message);
    }

    /**
     * Callback para eventos de impressao.
     */
    public interface PrintCallback {
        void onPrintSuccess();
        void onPrintError(String message);
        void onProgress(String status);
    }

    /**
     * Callback para listagem de impressoras do Windows.
     */
    public interface PrinterListCallback {
        void onPrintersFound(List<WindowsPrinterInfo> printers);
        void onError(String message);
        void onProgress(String status);
    }

    /**
     * Informacoes de um dispositivo Bluetooth encontrado.
     */
    public static class BluetoothDeviceInfo {
        public String name;
        public String address; // MAC address
        public int deviceClass;
        public int majorClass;
        public boolean isPaired;
        public boolean isComputer;
        public int rssi; // Sinal
        public String description;

        public BluetoothDeviceInfo(String name, String address) {
            this.name = name != null ? name : "Dispositivo Desconhecido";
            this.address = address;
            this.isPaired = false;
            this.isComputer = false;
            this.rssi = 0;
            this.description = "";
        }

        /**
         * Verifica se o dispositivo e provavelmente um computador Windows.
         */
        public boolean isLikelyWindowsComputer() {
            // Major device class 1 = Computer
            if (majorClass == 0x0100) return true;

            // Verificar pelo nome
            if (name != null) {
                String upperName = name.toUpperCase();
                if (upperName.contains("DESKTOP-") || upperName.contains("LAPTOP-")
                        || upperName.contains("PC-") || upperName.contains("WIN")
                        || upperName.contains("NOTEBOOK") || upperName.contains("COMPUTER")
                        || upperName.contains("WORKSTATION")) {
                    return true;
                }
            }

            return isComputer;
        }

        @Override
        public String toString() {
            return name + " [" + address + "]";
        }
    }

    /**
     * Informacoes de uma impressora instalada no Windows.
     * Representa uma impressora do Painel de Controle > Hardware e Sons > Dispositivos e Impressoras.
     *
     * Esta classe mapeia as impressoras que aparecem na secao "Impressoras" do
     * Painel de Controle do Windows, incluindo:
     * - Impressoras locais (USB, LPT, COM)
     * - Impressoras de rede
     * - Impressoras compartilhadas
     * - Impressoras virtuais (PDF, XPS, etc.)
     */
    public static class WindowsPrinterInfo {
        public String name;           // Nome da impressora (ex: "EPSON TM-T20", "mp-4200 th", "imp")
        public String portName;       // Nome da porta (ex: "USB001", "COM3", "LPT1")
        public String driverName;     // Nome do driver (ex: "EPSON TM-T20 Receipt")
        public String shareName;      // Nome de compartilhamento (se compartilhada)
        public boolean isDefault;     // Se e a impressora padrao
        public boolean isShared;      // Se esta compartilhada na rede
        public boolean isNetwork;     // Se e uma impressora de rede
        public boolean isReady;       // Se esta pronta/online
        public String status;         // Status descritivo ("Pronta", "Offline", etc.)
        public String location;       // Localizacao configurada
        public String type;           // Tipo: "Local", "Rede", "Compartilhada", "Virtual"
        public String source;         // Origem: "Bluetooth SPP", "SMB/CIFS", "Manual", "Modelo Comum"

        public WindowsPrinterInfo(String name) {
            this.name = name != null ? name : "Impressora Desconhecida";
            this.portName = "";
            this.driverName = "";
            this.shareName = "";
            this.isDefault = false;
            this.isShared = false;
            this.isNetwork = false;
            this.isReady = true;
            this.status = "Disponivel";
            this.location = "";
            this.type = "Local";
            this.source = "";
        }

        /**
         * Verifica se a impressora e provavelmente uma impressora termica/recibo.
         */
        public boolean isLikelyReceiptPrinter() {
            if (name == null) return false;
            String upper = name.toUpperCase();
            return upper.contains("TM-") || upper.contains("MP-") || upper.contains("RECEIPT")
                    || upper.contains("TERMICA") || upper.contains("THERMAL")
                    || upper.contains("POS") || upper.contains("BEMATECH")
                    || upper.contains("ELGIN") || upper.contains("DARUMA")
                    || upper.contains("EPSON") || upper.contains("STAR ")
                    || upper.contains("CITIZEN") || upper.contains("SWEDA")
                    || upper.contains("DIEBOLD") || upper.contains("GERTEC")
                    || upper.contains("TANCA") || upper.contains("CONTROL ID")
                    || upper.contains("PRINT ID") || upper.contains("CUSTOM")
                    || upper.contains("58MM") || upper.contains("80MM")
                    || upper.contains("TSP") || upper.contains("SP700")
                    || upper.contains("CT-") || upper.contains("MP421")
                    || upper.contains("I9") || upper.contains("L42")
                    || upper.contains("GENERIC") || upper.contains("TEXT ONLY");
        }

        /**
         * Verifica se a impressora e virtual (PDF, XPS, OneNote, etc.).
         */
        public boolean isVirtualPrinter() {
            if (name == null) return false;
            String upper = name.toUpperCase();
            return upper.contains("PDF") || upper.contains("XPS")
                    || upper.contains("ONENOTE") || upper.contains("FAX")
                    || upper.contains("MICROSOFT PRINT") || upper.contains("SEND TO")
                    || upper.contains("VIRTUAL") || upper.contains("CUTE")
                    || upper.contains("FOXIT") || upper.contains("ADOBE");
        }

        /**
         * Retorna uma descricao amigavel da impressora.
         */
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            if (type != null && !type.isEmpty()) {
                sb.append(type);
            }
            if (isDefault) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Padrao");
            }
            if (isShared) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Compartilhada");
                if (shareName != null && !shareName.isEmpty()) {
                    sb.append(" (").append(shareName).append(")");
                }
            }
            if (portName != null && !portName.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Porta: ").append(portName);
            }
            if (source != null && !source.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append("Via: ").append(source);
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return name + (isDefault ? " [Padrao]" : "") + (isShared ? " [Compartilhada]" : "");
        }
    }

    public BluetoothWindowsPrintManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ==================== CONFIGURACAO ====================

    /**
     * Verifica se o Bluetooth esta disponivel no dispositivo.
     */
    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    /**
     * Verifica se o Bluetooth esta ativado.
     */
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Salva a configuracao de impressao Bluetooth Windows.
     */
    public void saveConfig(String deviceName, String deviceMac, String printerName) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("bt_win_device_name", deviceName)
                .putString("bt_win_device_mac", deviceMac)
                .putString("bt_win_printer_name", printerName)
                .apply();
    }

    /**
     * Salva credenciais SMB para acesso via Bluetooth.
     */
    public void saveSmbCredentials(String domain, String user, String password) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("bt_win_smb_domain", domain)
                .putString("bt_win_smb_user", user)
                .putString("bt_win_smb_password", password)
                .apply();
    }

    public String getDeviceName() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_device_name", "");
    }

    public String getDeviceMac() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_device_mac", "");
    }

    public String getPrinterName() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_printer_name", "");
    }

    public String getSmbDomain() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_smb_domain", "WORKGROUP");
    }

    public String getSmbUser() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_smb_user", "");
    }

    public String getSmbPassword() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_smb_password", "");
    }

    // ==================== DESCOBERTA DE DISPOSITIVOS ====================

    /**
     * Lista dispositivos Bluetooth pareados que sao computadores.
     * Este metodo e rapido pois nao precisa fazer scan.
     */
    public List<BluetoothDeviceInfo> getPairedComputers() {
        List<BluetoothDeviceInfo> computers = new ArrayList<>();
        if (bluetoothAdapter == null) return computers;

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    BluetoothDeviceInfo info = createDeviceInfo(device);
                    info.isPaired = true;
                    if (info.isLikelyWindowsComputer()) {
                        info.description = "Computador pareado";
                        computers.add(info);
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permissao Bluetooth negada", e);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar dispositivos pareados", e);
        }
        return computers;
    }

    /**
     * Lista TODOS os dispositivos Bluetooth pareados (nao apenas computadores).
     */
    public List<BluetoothDeviceInfo> getAllPairedDevices() {
        List<BluetoothDeviceInfo> devices = new ArrayList<>();
        if (bluetoothAdapter == null) return devices;

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    BluetoothDeviceInfo info = createDeviceInfo(device);
                    info.isPaired = true;
                    devices.add(info);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permissao Bluetooth negada", e);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar dispositivos pareados", e);
        }
        return devices;
    }

    /**
     * Cria um BluetoothDeviceInfo a partir de um BluetoothDevice.
     */
    private BluetoothDeviceInfo createDeviceInfo(BluetoothDevice device) {
        String name = null;
        String address = null;
        int deviceClass = 0;
        int majorClass = 0;

        try {
            name = device.getName();
        } catch (SecurityException e) {
            Log.w(TAG, "Sem permissao para obter nome do dispositivo");
        }

        try {
            address = device.getAddress();
        } catch (Exception e) {
            address = "00:00:00:00:00:00";
        }

        try {
            if (device.getBluetoothClass() != null) {
                deviceClass = device.getBluetoothClass().getDeviceClass();
                majorClass = device.getBluetoothClass().getMajorDeviceClass();
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao obter classe do dispositivo");
        }

        BluetoothDeviceInfo info = new BluetoothDeviceInfo(name, address);
        info.deviceClass = deviceClass;
        info.majorClass = majorClass;

        // Major class 0x0100 = Computer
        info.isComputer = (majorClass == 0x0100);

        // Gerar descricao
        if (info.isComputer) {
            info.description = classificarComputador(deviceClass);
        } else {
            info.description = classificarDispositivo(majorClass, deviceClass);
        }

        return info;
    }

    /**
     * Classifica o tipo de computador baseado na device class.
     */
    private String classificarComputador(int deviceClass) {
        int minor = deviceClass & 0xFF;
        switch (minor) {
            case 0x04: return "Computador Desktop";
            case 0x08: return "Servidor";
            case 0x0C: return "Notebook/Laptop";
            case 0x10: return "Handheld/PDA";
            case 0x14: return "Palm";
            case 0x18: return "Wearable";
            default: return "Computador";
        }
    }

    /**
     * Classifica o tipo de dispositivo baseado na major class.
     */
    private String classificarDispositivo(int majorClass, int deviceClass) {
        switch (majorClass) {
            case 0x0100: return "Computador";
            case 0x0200: return "Telefone";
            case 0x0300: return "Ponto de Acesso";
            case 0x0400: return "Audio/Video";
            case 0x0500: return "Periferico";
            case 0x0600: return "Imagem/Impressora";
            case 0x0700: return "Wearable";
            case 0x0800: return "Brinquedo";
            case 0x0900: return "Saude";
            default: return "Dispositivo Bluetooth";
        }
    }

    // ==================== LISTAGEM DE IMPRESSORAS DO WINDOWS ====================

    /**
     * Lista as impressoras instaladas no computador Windows conectado via Bluetooth.
     *
     * Este metodo busca as impressoras do Painel de Controle do Windows
     * (Hardware e Sons > Dispositivos e Impressoras) utilizando multiplas estrategias:
     *
     * Estrategia 1: Protocolo PDV via Bluetooth SPP (requer agente no Windows)
     * Estrategia 2: Enumeracao via SMB/CIFS usando nome do dispositivo Bluetooth
     * Estrategia 3: Enumeracao via SMB/CIFS usando resolucao de IP
     * Estrategia 4: Lista padrao de impressoras comuns (fallback com entrada manual)
     *
     * As impressoras retornadas correspondem as que aparecem na secao "Impressoras"
     * do Painel de Controle > Hardware e Sons > Dispositivos e Impressoras do Windows,
     * como: Generic / Text Only, imp (MP-4200 TH), Microsoft Print to PDF, etc.
     *
     * @param macAddress Endereco MAC do computador Windows
     * @param callback   Callback para resultado
     */
    public void listarImpressorasWindows(String macAddress, PrinterListCallback callback) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            callback.onError("Bluetooth nao disponivel ou desativado");
            return;
        }

        new Thread(() -> {
            List<WindowsPrinterInfo> allPrinters = new ArrayList<>();

            // Estrategia 1: Tentar protocolo PDV via Bluetooth SPP
            callback.onProgress("Conectando ao computador via Bluetooth...");
            try {
                List<WindowsPrinterInfo> sppPrinters = listarViaBluetoothSPP(macAddress);
                if (sppPrinters != null && !sppPrinters.isEmpty()) {
                    for (WindowsPrinterInfo p : sppPrinters) {
                        p.source = "Bluetooth SPP";
                    }
                    callback.onProgress("Impressoras encontradas via Bluetooth!");
                    // Adicionar opcao manual no final
                    adicionarOpcaoManual(sppPrinters);
                    callback.onPrintersFound(sppPrinters);
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "Falha ao listar impressoras via SPP: " + e.getMessage());
            }

            // Estrategia 2: Tentar via SMB/CIFS usando nome do dispositivo Bluetooth
            callback.onProgress("Buscando impressoras compartilhadas na rede...");
            try {
                String deviceName = getDeviceNameByMac(macAddress);
                List<WindowsPrinterInfo> smbPrinters = listarViaSMB(deviceName, macAddress);
                if (smbPrinters != null && !smbPrinters.isEmpty()) {
                    for (WindowsPrinterInfo p : smbPrinters) {
                        p.source = "SMB/CIFS";
                    }
                    allPrinters.addAll(smbPrinters);
                }
            } catch (Exception e) {
                Log.w(TAG, "Falha ao listar impressoras via SMB (nome): " + e.getMessage());
            }

            // Estrategia 3: Tentar via SMB/CIFS usando resolucao de IP
            if (allPrinters.isEmpty()) {
                callback.onProgress("Tentando resolver IP do computador...");
                try {
                    String deviceName = getDeviceNameByMac(macAddress);
                    if (deviceName != null && !deviceName.isEmpty()) {
                        String resolvedIp = resolverIPDoDispositivo(deviceName);
                        if (resolvedIp != null) {
                            List<WindowsPrinterInfo> ipPrinters = listarViaSMB(resolvedIp, macAddress);
                            if (ipPrinters != null && !ipPrinters.isEmpty()) {
                                for (WindowsPrinterInfo p : ipPrinters) {
                                    p.source = "SMB/IP";
                                }
                                allPrinters.addAll(ipPrinters);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Falha ao listar impressoras via SMB (IP): " + e.getMessage());
                }
            }

            // Se encontrou impressoras via SMB, retornar
            if (!allPrinters.isEmpty()) {
                adicionarOpcaoManual(allPrinters);
                callback.onPrintersFound(allPrinters);
                return;
            }

            // Estrategia 4: Gerar lista padrao de impressoras comuns
            callback.onProgress("Preparando lista de impressoras...");
            allPrinters = gerarListaImpressorasPadrao(macAddress);

            if (allPrinters.isEmpty()) {
                callback.onError("Nao foi possivel listar as impressoras do computador.\n\n"
                        + "As impressoras do Painel de Controle do Windows\n"
                        + "(Hardware e Sons > Dispositivos e Impressoras)\n"
                        + "nao puderam ser acessadas automaticamente.\n\n"
                        + "Possiveis solucoes:\n"
                        + "- Verifique se o computador esta ligado\n"
                        + "- Verifique se o Bluetooth esta ativado no Windows\n"
                        + "- Compartilhe a impressora no Windows\n"
                        + "- Certifique-se de que os dispositivos estao pareados\n"
                        + "- Verifique se ambos estao na mesma rede Wi-Fi\n\n"
                        + "Voce pode digitar o nome da impressora manualmente.");
            } else {
                callback.onPrintersFound(allPrinters);
            }
        }).start();
    }

    /**
     * Adiciona a opcao de entrada manual no final da lista de impressoras.
     */
    private void adicionarOpcaoManual(List<WindowsPrinterInfo> printers) {
        WindowsPrinterInfo manual = new WindowsPrinterInfo("Digitar nome da impressora manualmente");
        manual.type = "Manual";
        manual.source = "Manual";
        manual.status = "Informe o nome exato da impressora do Painel de Controle do Windows";
        manual.portName = "";
        manual.isReady = true;
        printers.add(manual);
    }

    /**
     * Tenta resolver o IP de um dispositivo pelo nome.
     * Usa resolucao DNS/NetBIOS.
     */
    private String resolverIPDoDispositivo(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) return null;

        try {
            InetAddress address = InetAddress.getByName(deviceName);
            String ip = address.getHostAddress();
            if (ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0")) {
                Log.i(TAG, "IP resolvido para " + deviceName + ": " + ip);
                return ip;
            }
        } catch (Exception e) {
            Log.w(TAG, "Nao foi possivel resolver IP para: " + deviceName);
        }

        return null;
    }

    /**
     * Estrategia 1: Lista impressoras via protocolo PDV sobre Bluetooth SPP.
     * Requer que o computador Windows tenha o agente PDV instalado.
     */
    private List<WindowsPrinterInfo> listarViaBluetoothSPP(String macAddress) throws Exception {
        List<WindowsPrinterInfo> printers = new ArrayList<>();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        BluetoothSocket socket = null;

        try {
            try { bluetoothAdapter.cancelDiscovery(); } catch (SecurityException ignored) {}

            // Tentar conexao SPP
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
            } catch (Exception e) {
                fecharSocket(socket);
                socket = null;
                // Tentar conexao insegura
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                    socket.connect();
                } catch (Exception e2) {
                    fecharSocket(socket);
                    socket = null;
                    // Tentar canal RAW
                    java.lang.reflect.Method m = device.getClass().getMethod(
                            "createRfcommSocket", new Class[]{int.class});
                    socket = (BluetoothSocket) m.invoke(device, 1);
                    socket.connect();
                }
            }

            if (socket == null || !socket.isConnected()) {
                return printers;
            }

            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            // Enviar handshake
            os.write((HANDSHAKE_CMD + PROTOCOL_SEPARATOR).getBytes(StandardCharsets.UTF_8));
            os.flush();

            // Aguardar resposta (com timeout)
            Thread.sleep(500);
            String response = lerResposta(is, 3000);

            if (response != null && response.contains(READY_RESPONSE)) {
                // Agente encontrado! Solicitar lista de impressoras
                os.write((LIST_PRINTERS_CMD + PROTOCOL_SEPARATOR).getBytes(StandardCharsets.UTF_8));
                os.flush();

                // Ler lista de impressoras
                Thread.sleep(500);
                String printerList = lerResposta(is, 5000);

                if (printerList != null && !printerList.isEmpty()) {
                    String[] lines = printerList.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.equals(PRINTERS_END)) break;
                        if (line.startsWith("PRINTER:")) {
                            WindowsPrinterInfo printer = parsePrinterLine(line);
                            if (printer != null) {
                                printers.add(printer);
                            }
                        }
                    }
                }
            }
        } finally {
            fecharSocket(socket);
        }

        return printers;
    }

    /**
     * Estrategia 2: Lista impressoras compartilhadas via SMB/CIFS.
     * Busca impressoras compartilhadas no computador Windows pela rede.
     *
     * Esta estrategia acessa o computador Windows via protocolo SMB/CIFS
     * e lista os compartilhamentos do tipo IMPRESSORA, que correspondem
     * as impressoras compartilhadas no Painel de Controle do Windows
     * (Hardware e Sons > Dispositivos e Impressoras).
     */
    private List<WindowsPrinterInfo> listarViaSMB(String hostName, String macAddress) {
        List<WindowsPrinterInfo> printers = new ArrayList<>();

        try {
            // Tentar resolver o nome do host
            String host = hostName;
            if (host == null || host.isEmpty()) {
                host = getDeviceNameByMac(macAddress);
            }
            if (host == null || host.isEmpty()) {
                return printers;
            }

            // Configurar jCIFS
            jcifs.Config.setProperty("jcifs.smb.client.responseTimeout", "8000");
            jcifs.Config.setProperty("jcifs.smb.client.soTimeout", "10000");
            jcifs.Config.setProperty("jcifs.netbios.retryTimeout", "5000");
            jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");

            String domain = getSmbDomain();
            String user = getSmbUser();
            String password = getSmbPassword();

            jcifs.smb.NtlmPasswordAuthentication auth;
            if (user == null || user.isEmpty()) {
                auth = jcifs.smb.NtlmPasswordAuthentication.ANONYMOUS;
            } else {
                auth = new jcifs.smb.NtlmPasswordAuthentication(
                        domain != null && !domain.isEmpty() ? domain : "WORKGROUP",
                        user, password != null ? password : "");
            }

            // Listar compartilhamentos do host
            String smbUrl = "smb://" + host + "/";
            jcifs.smb.SmbFile smbHost = new jcifs.smb.SmbFile(smbUrl, auth);
            jcifs.smb.SmbFile[] shares = smbHost.listFiles();

            if (shares != null) {
                for (jcifs.smb.SmbFile share : shares) {
                    try {
                        String shareName = share.getName();
                        if (shareName.endsWith("/")) {
                            shareName = shareName.substring(0, shareName.length() - 1);
                        }

                        // Tipo 3 = Impressora compartilhada (TYPE_PRINTER)
                        if (share.getType() == jcifs.smb.SmbFile.TYPE_PRINTER) {
                            WindowsPrinterInfo printer = new WindowsPrinterInfo(shareName);
                            printer.isShared = true;
                            printer.shareName = shareName;
                            printer.type = "Compartilhada";
                            printer.status = "Impressora do Painel de Controle - Disponivel na rede";
                            printer.isReady = true;
                            printers.add(printer);
                        }
                        // Tipo 0 = Compartilhamento de disco (pode ser impressora tambem)
                        else if (share.getType() == jcifs.smb.SmbFile.TYPE_SHARE) {
                            // Verificar se o nome parece ser uma impressora
                            String upper = shareName.toUpperCase();
                            if (upper.contains("PRINT") || upper.contains("IMPRES")
                                    || upper.contains("TM-") || upper.contains("MP-")
                                    || upper.contains("EPSON") || upper.contains("BEMATECH")
                                    || upper.contains("ELGIN") || upper.contains("DARUMA")
                                    || upper.contains("TERMICA") || upper.contains("RECEIPT")
                                    || upper.contains("POS") || upper.contains("THERMAL")
                                    || upper.contains("GENERIC") || upper.contains("TEXT ONLY")
                                    || upper.contains("IMP") || upper.contains("TANCA")
                                    || upper.contains("SWEDA") || upper.contains("CITIZEN")
                                    || upper.contains("STAR") || upper.contains("DIEBOLD")
                                    || upper.contains("GERTEC") || upper.contains("CONTROL ID")) {
                                WindowsPrinterInfo printer = new WindowsPrinterInfo(shareName);
                                printer.isShared = true;
                                printer.shareName = shareName;
                                printer.type = "Compartilhamento";
                                printer.status = "Impressora do Painel de Controle - Disponivel na rede";
                                printer.isReady = true;
                                printers.add(printer);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Erro ao verificar compartilhamento: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao listar impressoras via SMB: " + e.getMessage());
        }

        return printers;
    }

    /**
     * Estrategia 3: Gera uma lista padrao de impressoras comuns para selecao manual.
     * Inclui impressoras termicas populares no Brasil e opcao de entrada manual.
     *
     * Esta lista e baseada nas impressoras mais comuns que aparecem na secao
     * "Impressoras" do Painel de Controle > Hardware e Sons > Dispositivos e Impressoras
     * do Windows em ambientes de PDV/comercio no Brasil.
     */
    private List<WindowsPrinterInfo> gerarListaImpressorasPadrao(String macAddress) {
        List<WindowsPrinterInfo> printers = new ArrayList<>();

        // Opcao para digitar nome manualmente (primeira opcao)
        WindowsPrinterInfo manual = new WindowsPrinterInfo("Digitar nome da impressora manualmente");
        manual.type = "Manual";
        manual.source = "Manual";
        manual.status = "Informe o nome exato como aparece no Painel de Controle do Windows\n(Hardware e Sons > Dispositivos e Impressoras)";
        manual.portName = "";
        manual.isReady = true;
        printers.add(manual);

        // Impressoras que comumente aparecem no Painel de Controle do Windows
        // Secao: Impressoras (Hardware e Sons > Dispositivos e Impressoras)
        String[][] commonPrinters = {
                {"Generic / Text Only", "Local", "Impressora generica do Windows - Texto puro"},
                {"EPSON TM-T20", "Local", "Impressora termica Epson"},
                {"EPSON TM-T20X", "Local", "Impressora termica Epson"},
                {"EPSON TM-T88V", "Local", "Impressora termica Epson"},
                {"EPSON TM-T88VI", "Local", "Impressora termica Epson"},
                {"Bematech MP-4200 TH", "Local", "Impressora termica Bematech"},
                {"Bematech MP-2800 TH", "Local", "Impressora termica Bematech"},
                {"Bematech MP-100S TH", "Local", "Impressora termica Bematech"},
                {"Elgin i9", "Local", "Impressora termica Elgin"},
                {"Elgin i7", "Local", "Impressora termica Elgin"},
                {"Elgin i9 Full", "Local", "Impressora termica Elgin"},
                {"Daruma DR800", "Local", "Impressora termica Daruma"},
                {"Daruma DR700", "Local", "Impressora termica Daruma"},
                {"Tanca TP-650", "Local", "Impressora termica Tanca"},
                {"Tanca TP-550", "Local", "Impressora termica Tanca"},
                {"Sweda SI-300", "Local", "Impressora termica Sweda"},
                {"Control iD Print iD", "Local", "Impressora termica Control iD"},
                {"Diebold TSP143", "Local", "Impressora termica Diebold"},
                {"Gertec G250", "Local", "Impressora termica Gertec"},
                {"Star TSP100", "Local", "Impressora termica Star"},
                {"Star TSP143", "Local", "Impressora termica Star"},
                {"Citizen CT-S310II", "Local", "Impressora termica Citizen"},
                {"Custom KUBE II", "Local", "Impressora termica Custom"},
                {"Microsoft Print to PDF", "Virtual", "Impressora virtual PDF do Windows"},
                {"Microsoft XPS Document Writer", "Virtual", "Impressora virtual XPS do Windows"}
        };

        for (String[] info : commonPrinters) {
            WindowsPrinterInfo printer = new WindowsPrinterInfo(info[0]);
            printer.type = info[1];
            printer.source = "Modelo Comum";
            printer.status = info[2] + " - Selecione se esta instalada no Windows";
            printer.isReady = true;
            printers.add(printer);
        }

        return printers;
    }

    /**
     * Faz o parse de uma linha do protocolo de listagem de impressoras.
     * Formato: PRINTER:Nome:Tipo:Status:Porta:Padrao:Compartilhada:ShareName
     */
    private WindowsPrinterInfo parsePrinterLine(String line) {
        try {
            String data = line.substring("PRINTER:".length());
            String[] parts = data.split(":", -1);

            if (parts.length >= 1) {
                WindowsPrinterInfo printer = new WindowsPrinterInfo(parts[0].trim());

                if (parts.length >= 2) printer.type = parts[1].trim();
                if (parts.length >= 3) printer.status = parts[2].trim();
                if (parts.length >= 4) printer.portName = parts[3].trim();
                if (parts.length >= 5) printer.isDefault = "true".equalsIgnoreCase(parts[4].trim());
                if (parts.length >= 6) printer.isShared = "true".equalsIgnoreCase(parts[5].trim());
                if (parts.length >= 7) printer.shareName = parts[6].trim();

                printer.isReady = !"Offline".equalsIgnoreCase(printer.status);
                return printer;
            }
        } catch (Exception e) {
            Log.w(TAG, "Erro ao parsear linha de impressora: " + line, e);
        }
        return null;
    }

    /**
     * Le uma resposta do InputStream com timeout.
     */
    private String lerResposta(InputStream is, int timeoutMs) {
        try {
            StringBuilder sb = new StringBuilder();
            long startTime = System.currentTimeMillis();
            byte[] buffer = new byte[1024];

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (is.available() > 0) {
                    int bytesRead = is.read(buffer);
                    if (bytesRead > 0) {
                        sb.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
                        // Verificar se ja recebeu o fim da mensagem
                        String current = sb.toString();
                        if (current.contains(PRINTERS_END) || current.contains(READY_RESPONSE)
                                || current.contains(DATA_END_MARKER)) {
                            break;
                        }
                    }
                } else {
                    Thread.sleep(100);
                }
            }

            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            Log.w(TAG, "Erro ao ler resposta Bluetooth: " + e.getMessage());
            return null;
        }
    }

    // ==================== CONEXAO E IMPRESSAO ====================

    /**
     * Conecta ao dispositivo Bluetooth Windows e envia dados de impressao.
     * Utiliza multiplas estrategias de conexao para maxima compatibilidade.
     *
     * Estrategia 1: Conexao RFCOMM SPP direta (porta serial)
     * Estrategia 2: Conexao RFCOMM com reflexao (createInsecureRfcommSocket)
     * Estrategia 3: Conexao via OBEX Push
     *
     * @param macAddress Endereco MAC do dispositivo Windows
     * @param texto      Texto a ser impresso (com comandos ESC/POS)
     * @param callback   Callback para resultado
     */
    public void imprimirViaBluetoothWindows(String macAddress, String texto, PrintCallback callback) {
        if (bluetoothAdapter == null) {
            callback.onPrintError("Bluetooth nao disponivel neste dispositivo");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            callback.onPrintError("Bluetooth esta desativado. Por favor, ative o Bluetooth.");
            return;
        }

        if (macAddress == null || macAddress.isEmpty()) {
            callback.onPrintError("Endereco MAC do dispositivo nao configurado");
            return;
        }

        new Thread(() -> {
            boolean success = false;
            String lastError = "";

            for (int attempt = 1; attempt <= MAX_RETRIES && !success; attempt++) {
                if (attempt > 1) {
                    callback.onProgress("Tentativa " + attempt + " de " + MAX_RETRIES + "...");
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                }

                // Estrategia 1: RFCOMM SPP direto
                callback.onProgress("Conectando via Bluetooth SPP...");
                try {
                    success = tentarImpressaoSPP(macAddress, texto);
                    if (success) {
                        callback.onProgress("Impressao enviada com sucesso via SPP!");
                        callback.onPrintSuccess();
                        return;
                    }
                } catch (Exception e) {
                    lastError = "SPP: " + e.getMessage();
                    Log.w(TAG, "Falha na estrategia SPP (tentativa " + attempt + ")", e);
                }

                // Estrategia 2: RFCOMM inseguro (fallback)
                callback.onProgress("Tentando conexao alternativa...");
                try {
                    success = tentarImpressaoRfcommInseguro(macAddress, texto);
                    if (success) {
                        callback.onProgress("Impressao enviada com sucesso via RFCOMM!");
                        callback.onPrintSuccess();
                        return;
                    }
                } catch (Exception e) {
                    lastError = "RFCOMM: " + e.getMessage();
                    Log.w(TAG, "Falha na estrategia RFCOMM inseguro (tentativa " + attempt + ")", e);
                }

                // Estrategia 3: Envio RAW direto
                callback.onProgress("Tentando envio direto RAW...");
                try {
                    success = tentarImpressaoRaw(macAddress, texto);
                    if (success) {
                        callback.onProgress("Impressao enviada com sucesso via RAW!");
                        callback.onPrintSuccess();
                        return;
                    }
                } catch (Exception e) {
                    lastError = "RAW: " + e.getMessage();
                    Log.w(TAG, "Falha na estrategia RAW (tentativa " + attempt + ")", e);
                }
            }

            callback.onPrintError("Nao foi possivel imprimir apos " + MAX_RETRIES + " tentativas.\n\n"
                    + "Ultimo erro: " + lastError + "\n\n"
                    + "Verifique:\n"
                    + "- O computador Windows esta com Bluetooth ativado\n"
                    + "- O dispositivo esta pareado\n"
                    + "- A impressora esta compartilhada no Windows\n"
                    + "- O servico Bluetooth do Windows esta funcionando");
        }).start();
    }

    /**
     * Estrategia 1: Conexao RFCOMM via SPP (Serial Port Profile).
     * Este e o metodo mais compativel com Windows.
     */
    private boolean tentarImpressaoSPP(String macAddress, String texto) throws Exception {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        BluetoothSocket socket = null;

        try {
            // Cancelar discovery para melhorar performance
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {}

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();

            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            // Enviar dados de impressao diretamente via porta serial
            // O Windows recebe na porta COM virtual do Bluetooth
            byte[] initCmd = new byte[]{0x1B, 0x40}; // ESC/POS Initialize
            byte[] codePageCmd = new byte[]{0x1B, 0x74, 0x10}; // Set code page WPC1252
            // Remover newlines excessivos do final do texto
            String textoLimpo = texto;
            while (textoLimpo.endsWith("\n")) {
                textoLimpo = textoLimpo.substring(0, textoLimpo.length() - 1);
            }
            byte[] textBytes = textoLimpo.getBytes("CP437");
            // v7.0.0 - Corte com feed minimo integrado para evitar papel em branco extra
            byte[] cutCmd = new byte[]{0x1D, 0x56, 0x42, 0x01}; // Feed 1 linha + corte parcial

            os.write(initCmd);
            os.write(codePageCmd);
            os.write(textBytes);
            os.write(new byte[]{0x0A}); // 1 line feed
            os.write(cutCmd);
            os.flush();

            // Aguardar um pouco para garantir envio
            Thread.sleep(500);

            Log.i(TAG, "Impressao SPP enviada com sucesso para " + macAddress);
            return true;
        } finally {
            fecharSocket(socket);
        }
    }

    /**
     * Estrategia 2: Conexao RFCOMM insegura (sem pareamento obrigatorio).
     * Usa reflexao para acessar createInsecureRfcommSocketToServiceRecord.
     */
    private boolean tentarImpressaoRfcommInseguro(String macAddress, String texto) throws Exception {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        BluetoothSocket socket = null;

        try {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {}

            // Tentar conexao insegura
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();

            OutputStream os = socket.getOutputStream();

            // Enviar dados de impressao
            byte[] initCmd = new byte[]{0x1B, 0x40};
            byte[] codePageCmd = new byte[]{0x1B, 0x74, 0x10};
            // Remover newlines excessivos do final do texto
            String textoLimpo = texto;
            while (textoLimpo.endsWith("\n")) {
                textoLimpo = textoLimpo.substring(0, textoLimpo.length() - 1);
            }
            byte[] textBytes = textoLimpo.getBytes("CP437");
            // v7.0.0 - Corte com feed minimo integrado para evitar papel em branco extra
            byte[] cutCmd = new byte[]{0x1D, 0x56, 0x42, 0x01}; // Feed 1 linha + corte parcial

            os.write(initCmd);
            os.write(codePageCmd);
            os.write(textBytes);
            os.write(new byte[]{0x0A}); // 1 line feed
            os.write(cutCmd);
            os.flush();

            Thread.sleep(500);

            Log.i(TAG, "Impressao RFCOMM inseguro enviada com sucesso para " + macAddress);
            return true;
        } finally {
            fecharSocket(socket);
        }
    }

    /**
     * Estrategia 3: Conexao RAW via reflexao do canal RFCOMM.
     * Tenta conectar diretamente no canal 1 (padrao para SPP no Windows).
     */
    private boolean tentarImpressaoRaw(String macAddress, String texto) throws Exception {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        BluetoothSocket socket = null;

        try {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {}

            // Usar reflexao para criar socket no canal 1
            java.lang.reflect.Method m = device.getClass().getMethod(
                    "createRfcommSocket", new Class[]{int.class});
            socket = (BluetoothSocket) m.invoke(device, 1);
            socket.connect();

            OutputStream os = socket.getOutputStream();

            // Enviar dados de impressao
            byte[] initCmd = new byte[]{0x1B, 0x40};
            byte[] codePageCmd = new byte[]{0x1B, 0x74, 0x10};
            // Remover newlines excessivos do final do texto
            String textoLimpo = texto;
            while (textoLimpo.endsWith("\n")) {
                textoLimpo = textoLimpo.substring(0, textoLimpo.length() - 1);
            }
            byte[] textBytes = textoLimpo.getBytes("CP437");
            // v7.0.0 - Corte com feed minimo integrado para evitar papel em branco extra
            byte[] cutCmd = new byte[]{0x1D, 0x56, 0x42, 0x01}; // Feed 1 linha + corte parcial

            os.write(initCmd);
            os.write(codePageCmd);
            os.write(textBytes);
            os.write(new byte[]{0x0A}); // 1 line feed
            os.write(cutCmd);
            os.flush();

            Thread.sleep(500);

            Log.i(TAG, "Impressao RAW enviada com sucesso para " + macAddress);
            return true;
        } finally {
            fecharSocket(socket);
        }
    }

    /**
     * Testa a conexao Bluetooth com um dispositivo.
     *
     * @param macAddress Endereco MAC do dispositivo
     * @return true se conseguiu conectar
     */
    public boolean testarConexao(String macAddress) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false;
        }

        BluetoothSocket socket = null;
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);

            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException ignored) {}

            // Tentar SPP primeiro
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                Log.i(TAG, "Teste de conexao SPP bem-sucedido: " + macAddress);
                return true;
            } catch (Exception e) {
                fecharSocket(socket);
                socket = null;
            }

            // Tentar RFCOMM inseguro
            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                Log.i(TAG, "Teste de conexao RFCOMM inseguro bem-sucedido: " + macAddress);
                return true;
            } catch (Exception e) {
                fecharSocket(socket);
                socket = null;
            }

            // Tentar canal 1 RAW
            try {
                java.lang.reflect.Method m = device.getClass().getMethod(
                        "createRfcommSocket", new Class[]{int.class});
                socket = (BluetoothSocket) m.invoke(device, 1);
                socket.connect();
                Log.i(TAG, "Teste de conexao RAW bem-sucedido: " + macAddress);
                return true;
            } catch (Exception e) {
                fecharSocket(socket);
                socket = null;
            }

            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro no teste de conexao Bluetooth", e);
            return false;
        } finally {
            fecharSocket(socket);
        }
    }

    /**
     * Fecha um socket Bluetooth de forma segura.
     */
    private void fecharSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Erro ao fechar socket Bluetooth", e);
            }
        }
    }

    /**
     * Desconecta e libera recursos.
     */
    public void disconnect() {
        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (Exception ignored) {}

        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (Exception ignored) {}

        fecharSocket(currentSocket);
        currentSocket = null;
    }

    /**
     * Verifica se esta conectado a um dispositivo.
     */
    public boolean isConnected() {
        return currentSocket != null && currentSocket.isConnected();
    }

    /**
     * Obtem o nome amigavel de um dispositivo pelo MAC.
     */
    public String getDeviceNameByMac(String macAddress) {
        if (bluetoothAdapter == null || macAddress == null) return null;
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            return device.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verifica se um dispositivo esta pareado.
     */
    public boolean isDevicePaired(String macAddress) {
        if (bluetoothAdapter == null || macAddress == null) return false;
        try {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                for (BluetoothDevice device : paired) {
                    if (macAddress.equalsIgnoreCase(device.getAddress())) {
                        return true;
                    }
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Sem permissao para verificar pareamento", e);
        }
        return false;
    }

    /**
     * Retorna informacoes de diagnostico sobre o Bluetooth.
     */
    public String getDiagnosticInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Diagnostico Bluetooth ===\n");

        if (bluetoothAdapter == null) {
            sb.append("Bluetooth: NAO DISPONIVEL\n");
            return sb.toString();
        }

        sb.append("Bluetooth: ").append(bluetoothAdapter.isEnabled() ? "ATIVADO" : "DESATIVADO").append("\n");

        try {
            sb.append("Nome: ").append(bluetoothAdapter.getName()).append("\n");
            sb.append("Endereco: ").append(bluetoothAdapter.getAddress()).append("\n");
        } catch (SecurityException e) {
            sb.append("Nome/Endereco: Sem permissao\n");
        }

        try {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            sb.append("Dispositivos pareados: ").append(paired != null ? paired.size() : 0).append("\n");
            if (paired != null) {
                for (BluetoothDevice device : paired) {
                    try {
                        sb.append("  - ").append(device.getName())
                                .append(" [").append(device.getAddress()).append("]\n");
                    } catch (SecurityException ignored) {
                        sb.append("  - [sem permissao para nome]\n");
                    }
                }
            }
        } catch (SecurityException e) {
            sb.append("Pareados: Sem permissao\n");
        }

        // Config salva
        String savedMac = getDeviceMac();
        String savedName = getDeviceName();
        String savedPrinter = getPrinterName();
        sb.append("\n=== Configuracao Salva ===\n");
        sb.append("Dispositivo: ").append(savedName.isEmpty() ? "(nao configurado)" : savedName).append("\n");
        sb.append("MAC: ").append(savedMac.isEmpty() ? "(nao configurado)" : savedMac).append("\n");
        sb.append("Impressora: ").append(savedPrinter.isEmpty() ? "(nao configurado)" : savedPrinter).append("\n");

        return sb.toString();
    }
}
