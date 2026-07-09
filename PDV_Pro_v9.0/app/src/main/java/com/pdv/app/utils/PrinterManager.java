package com.pdv.app.utils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;

/**
 * PrinterManager v7.0.0 - Otimizado ao maximo para impressao real.
 * 
 * CORRECAO v7.0.0: Eliminadas as 2 impressoes em branco que saiam apos o corte.
 * Causa: ESC_FEED_3 + GS_CUT_PARTIAL separados geravam papel em branco extra.
 * Solucao: Usar comando unico GS V 66 n (0x1D 0x56 0x42 0x01) com feed minimo.
 *
 * Suporta TODOS os tipos de impressora:
 * - Rede TCP/IP (ESC/POS RAW via socket)
 * - Bluetooth direto (SPP/RFCOMM para impressoras termicas)
 * - USB (via UsbManager do Android)
 * - SMB/CIFS (impressora compartilhada Windows)
 * - SMB Direto (caminho \\SERVIDOR\IMPRESSORA)
 * - Bluetooth Windows (via PC/Notebook)
 *
 * Otimizacoes implementadas:
 * - Encoding correto para caracteres brasileiros (acentos)
 * - Comandos ESC/POS completos (init, density, alignment, cut, feed)
 * - Retry automatico em todas as conexoes (3 tentativas)
 * - Timeouts adequados para cada tipo de conexao
 * - Tratamento robusto de erros com logs detalhados
 * - Suporte a papel 58mm e 80mm
 * - Corte de papel (full cut e partial cut)
 * - Feed de linhas antes do corte
 * - Multiplas estrategias de conexao Bluetooth
 */
public class PrinterManager {
    private static final String TAG = "PrinterManager";
    private Context context;

    // Tipos de impressora
    public static final String TIPO_REDE = "rede";
    public static final String TIPO_BLUETOOTH = "bluetooth";
    public static final String TIPO_USB = "usb";
    public static final String TIPO_SMB = "smb";
    public static final String TIPO_SMB_DIRETO = "smb_direto";
    public static final String TIPO_BT_WINDOWS = "bt_windows";
    public static final String TIPO_PRINT_SERVER = "print_server";
    public static final String TIPO_REDE_RAW = "rede_raw";
    public static final String TIPO_NENHUMA = "nenhuma";

    // UUID padrao para Serial Port Profile (SPP) - Bluetooth
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Configuracoes de retry e timeout
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1500;
    private static final int NETWORK_CONNECT_TIMEOUT_MS = 8000;
    private static final int NETWORK_SO_TIMEOUT_MS = 10000;
    private static final int BT_CONNECT_TIMEOUT_MS = 15000;
    private static final int USB_TIMEOUT_MS = 5000;

    // ==================== COMANDOS ESC/POS ====================
    // Inicializacao
    private static final byte[] ESC_INIT = {0x1B, 0x40};                    // Initialize printer
    // Code pages para caracteres brasileiros
    private static final byte[] ESC_CODEPAGE_CP850 = {0x1B, 0x74, 0x02};   // CP850 (Latin-1 Multilingual)
    private static final byte[] ESC_CODEPAGE_CP437 = {0x1B, 0x74, 0x00};   // CP437 (USA)
    private static final byte[] ESC_CODEPAGE_WPC1252 = {0x1B, 0x74, 0x10}; // Windows-1252
    private static final byte[] ESC_CODEPAGE_LATIN1 = {0x1B, 0x74, 0x06};  // ISO 8859-1 Latin-1
    // Alinhamento
    private static final byte[] ESC_ALIGN_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] ESC_ALIGN_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] ESC_ALIGN_RIGHT = {0x1B, 0x61, 0x02};
    // Estilo de texto
    private static final byte[] ESC_BOLD_ON = {0x1B, 0x45, 0x01};
    private static final byte[] ESC_BOLD_OFF = {0x1B, 0x45, 0x00};
    private static final byte[] ESC_DOUBLE_HEIGHT_ON = {0x1B, 0x21, 0x10};
    private static final byte[] ESC_DOUBLE_HEIGHT_OFF = {0x1B, 0x21, 0x00};
    // v8.0.12.8 - Fonte 20/negrito para senha/canhoto.
    // <senha20> e o marcador interno da fonte 20 da senha. Ele nao aparece no papel.
    // Impressoras termicas ESC/POS nao usam tamanho em pt/sp como Android; usam ampliacao GS ! n.
    // O valor 0x77 e o maior tamanho padrao: largura 8x e altura 8x.
    // Assim a senha fica o mais proximo possivel de fonte 20, centralizada e em negrito real.
    private static final byte[] GS_CHAR_SIZE_NORMAL = {0x1D, 0x21, 0x00};
    private static final byte[] GS_CHAR_SIZE_8X = {0x1D, 0x21, 0x77};
    private static final byte[] ESC_UNDERLINE_ON = {0x1B, 0x2D, 0x01};
    private static final byte[] ESC_UNDERLINE_OFF = {0x1B, 0x2D, 0x00};
    // Espacamento e densidade
    private static final byte[] ESC_LINE_SPACING_DEFAULT = {0x1B, 0x32};    // Default line spacing
    private static final byte[] ESC_LINE_SPACING_TIGHT = {0x1B, 0x33, 0x16}; // Tight spacing (22 dots)
    // Corte de papel
    private static final byte[] GS_CUT_FULL = {0x1D, 0x56, 0x00};          // Full cut
    private static final byte[] GS_CUT_PARTIAL = {0x1D, 0x56, 0x01};       // Partial cut
    private static final byte[] GS_CUT_FEED_FULL = {0x1D, 0x56, 0x41, 0x03}; // Feed + full cut
    private static final byte[] GS_CUT_FEED_PARTIAL = {0x1D, 0x56, 0x42, 0x03}; // Feed + partial cut
    // Feed de linhas
    private static final byte[] ESC_FEED_3 = {0x1B, 0x64, 0x03};           // Feed 3 lines
    private static final byte[] ESC_FEED_5 = {0x1B, 0x64, 0x05};           // Feed 5 lines
    // Beep (para impressoras que suportam)
    private static final byte[] ESC_BEEP = {0x1B, 0x42, 0x02, 0x02};       // Beep 2 times
    // Densidade de impressao
    private static final byte[] GS_PRINT_DENSITY = {0x1D, 0x7C, 0x04};     // Print density level 4 (max)

    public PrinterManager(Context context) {
        this.context = context;
    }

    // ==================== GETTERS DE CONFIGURACAO ====================

    public String getTipoImpressora() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("tipo", TIPO_NENHUMA);
    }

    public String getIpImpressora() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("ip", "192.168.1.100");
    }

    public int getPortaImpressora() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getInt("porta", 9100);
    }

    public String getMacBluetooth() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("mac_bluetooth", "");
    }

    public int getTamanhoPapel() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getInt("tamanho_papel", 58);
    }

    // --- SMB/CIFS getters ---
    public String getSmbHost() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_host", "192.168.1.100");
    }

    public String getSmbShare() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_share", "");
    }

    public String getSmbUser() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_user", "");
    }

    public String getSmbPassword() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_password", "");
    }

    public String getSmbDomain() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_domain", "WORKGROUP");
    }

    // --- SMB Direto getters ---
    public String getSmbDiretoCaminho() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_direto_caminho", "");
    }

    public String getSmbDiretoDomain() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_direto_domain", "WORKGROUP");
    }

    public String getSmbDiretoUser() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_direto_user", "");
    }

    public String getSmbDiretoPassword() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("smb_direto_password", "");
    }

    // --- Print Server getters ---
    public String getPrintServerIp() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("print_server_ip", "192.168.1.100");
    }

    public int getPrintServerPorta() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getInt("print_server_porta", 9200);
    }

    public String getPrintServerImpressora() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("print_server_impressora", "");
    }

    // --- Bluetooth Windows getters ---
    public String getBtWinDeviceName() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_device_name", "");
    }

    public String getBtWinDeviceMac() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_device_mac", "");
    }

    public String getBtWinPrinterName() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_win_printer_name", "");
    }

    // --- Bluetooth Direto getters ---
    public String getBtDeviceName() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("bt_device_name", "");
    }

    // --- Rede RAW getters ---
    public String getRedeRawIp() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("rede_raw_ip", "192.168.1.200");
    }

    public int getRedeRawPorta() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getInt("rede_raw_porta", 9100);
    }

    public String getRedeRawProtocolo() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("rede_raw_protocolo", "RAW");
    }

    public int getColunasTexto() {
        ThermalPrinterDriver.Profile profile = getDriverProfile();
        if (getTamanhoPapel() == 80) return profile.columns80 > 0 ? profile.columns80 : 48;
        return 32;
    }

    public String getDriverId() {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        return prefs.getString("driver_id", ThermalPrinterDriver.DRIVER_AUTO);
    }

    public ThermalPrinterDriver.Profile getDriverProfile() {
        return ThermalPrinterDriver.getProfile(getDriverId());
    }

    public void saveDriverConfig(String driverId) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit().putString("driver_id", driverId == null ? ThermalPrinterDriver.DRIVER_AUTO : driverId).apply();
    }

    // ==================== SAVE CONFIGS ====================

    public void saveConfig(String tipo, String ip, int porta, String macBt, int tamanhoPapel) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("tipo", tipo)
                .putString("ip", ip)
                .putInt("porta", porta)
                .putString("mac_bluetooth", macBt)
                .putInt("tamanho_papel", tamanhoPapel)
                .apply();
    }

    public void saveSmbConfig(String smbHost, String smbShare, String smbUser, String smbPassword, String smbDomain) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("smb_host", smbHost)
                .putString("smb_share", smbShare)
                .putString("smb_user", smbUser)
                .putString("smb_password", smbPassword)
                .putString("smb_domain", smbDomain)
                .apply();
    }

    public void saveSmbDiretoConfig(String caminho, String user, String password, String domain) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("smb_direto_caminho", caminho)
                .putString("smb_direto_user", user)
                .putString("smb_direto_password", password)
                .putString("smb_direto_domain", domain)
                .apply();
    }

    public void savePrintServerConfig(String ip, int porta, String impressora) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("print_server_ip", ip)
                .putInt("print_server_porta", porta)
                .putString("print_server_impressora", impressora)
                .apply();
    }

    public void saveBtWindowsConfig(String deviceName, String deviceMac, String printerName) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("bt_win_device_name", deviceName)
                .putString("bt_win_device_mac", deviceMac)
                .putString("bt_win_printer_name", printerName)
                .apply();
    }

    public void saveBtDeviceName(String deviceName) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("bt_device_name", deviceName)
                .apply();
    }

    public void saveRedeRawConfig(String ip, int porta, String protocolo) {
        SharedPreferences prefs = context.getSharedPreferences("printer_config", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("rede_raw_ip", ip)
                .putInt("rede_raw_porta", porta)
                .putString("rede_raw_protocolo", protocolo)
                .apply();
    }

    // ==================== UTILIDADES SMB ====================

    public static String windowsPathToSmbUrl(String caminhoWindows) {
        if (caminhoWindows == null || caminhoWindows.trim().isEmpty()) {
            return null;
        }

        String caminho = caminhoWindows.trim();

        if (caminho.startsWith("\\\\")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("//")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("smb://")) {
            return caminho;
        }

        caminho = caminho.replace('\\', '/');

        while (caminho.contains("//")) {
            caminho = caminho.replace("//", "/");
        }

        if (caminho.endsWith("/")) {
            caminho = caminho.substring(0, caminho.length() - 1);
        }

        int slashIndex = caminho.indexOf('/');
        if (slashIndex <= 0 || slashIndex >= caminho.length() - 1) {
            return null;
        }

        return "smb://" + caminho;
    }

    public static String extrairServidor(String caminhoWindows) {
        if (caminhoWindows == null || caminhoWindows.trim().isEmpty()) {
            return "";
        }

        String caminho = caminhoWindows.trim();

        if (caminho.startsWith("\\\\")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("//")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("smb://")) {
            caminho = caminho.substring(6);
        }

        caminho = caminho.replace('\\', '/');

        int slashIndex = caminho.indexOf('/');
        if (slashIndex > 0) {
            return caminho.substring(0, slashIndex);
        }
        return caminho;
    }

    public static String extrairCompartilhamento(String caminhoWindows) {
        if (caminhoWindows == null || caminhoWindows.trim().isEmpty()) {
            return "";
        }

        String caminho = caminhoWindows.trim();

        if (caminho.startsWith("\\\\")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("//")) {
            caminho = caminho.substring(2);
        } else if (caminho.startsWith("smb://")) {
            caminho = caminho.substring(6);
        }

        caminho = caminho.replace('\\', '/');

        if (caminho.endsWith("/")) {
            caminho = caminho.substring(0, caminho.length() - 1);
        }

        int slashIndex = caminho.indexOf('/');
        if (slashIndex > 0 && slashIndex < caminho.length() - 1) {
            return caminho.substring(slashIndex + 1);
        }
        return "";
    }

    public static boolean validarCaminhoWindows(String caminho) {
        if (caminho == null || caminho.trim().isEmpty()) {
            return false;
        }
        String smbUrl = windowsPathToSmbUrl(caminho);
        return smbUrl != null;
    }

    // ==================== METODO PRINCIPAL DE IMPRESSAO ====================

    /**
     * Imprime texto em qualquer tipo de impressora configurada.
     * Aplica automaticamente comandos ESC/POS otimizados para impressao real.
     * Inclui retry automatico em caso de falha.
     *
     * @param texto Texto a ser impresso (pode conter caracteres brasileiros com acentos)
     * @return true se a impressao foi enviada com sucesso
     */
    public boolean imprimirTexto(String texto) {
        // Adicionar frase de suporte ao final de todas as impressoes
        int width = getTamanhoPapel() == 80 ? 48 : 32;
        String line = width == 48 ? "------------------------------------------------" : "--------------------------------";
        String footer = "\n" + line + "\n" + center("Contato do Sistema", width) + "\n" + center("(85) 98123-7727", width) + "\n";
        
        // Evitar duplicacao se o texto ja contiver o contato
        if (texto != null && !texto.contains("(85) 98123-7727")) {
            texto = texto + footer;
        }

        String tipo = getTipoImpressora();
        if (TIPO_NENHUMA.equals(tipo)) {
            Log.w(TAG, "Nenhuma impressora configurada");
            return false;
        }

        Log.i(TAG, "Iniciando impressao via: " + tipo);

        // Preparar dados ESC/POS otimizados (processa marcadores <b></b> para negrito)
        byte[] dadosEscPos = prepararDadosEscPos(texto);

        switch (tipo) {
            case TIPO_REDE:
                return imprimirRedeComRetry(dadosEscPos);
            case TIPO_BLUETOOTH:
                return imprimirBluetoothComRetry(dadosEscPos);
            case TIPO_USB:
                return imprimirUsbComRetry(dadosEscPos);
            case TIPO_SMB:
                return imprimirSmbComRetry(dadosEscPos);
            case TIPO_SMB_DIRETO:
                return imprimirSmbDiretoComRetry(dadosEscPos);
            case TIPO_BT_WINDOWS:
                // v7.0.3 - Limpar marcadores de negrito para impressoras que recebem texto puro
                return imprimirBluetoothWindows(limparMarcadoresEscPos(texto));
            case TIPO_REDE_RAW:
                return imprimirRedeRawComRetry(dadosEscPos);
            case TIPO_PRINT_SERVER:
                // v7.0.3 - Limpar marcadores de negrito para impressoras que recebem texto puro
                return imprimirViaPrintServer(limparMarcadoresEscPos(texto));
            default:
                Log.w(TAG, "Tipo de impressora desconhecido: " + tipo);
                return false;
        }
    }

    /**
     * v8.0.12.3 - Remove marcadores ESC/POS do texto.
     * Usado para impressoras que recebem texto puro (BT Windows, Print Server)
     * e tambem como fallback caso alguma conexao nao aceite comandos binarios.
     */
    private String limparMarcadoresEscPos(String texto) {
        if (texto == null) return "";

        // Corrige canhoto em rotas de texto puro, BT Windows e Print Server.
        // Antes o marcador <senha20> era apenas removido e a senha saía na esquerda.
        // Agora a senha vira uma linha centralizada manualmente no papel.
        texto = centralizarSenha20EmTextoPuro(texto);

        return texto
                .replace("<b>", "")
                .replace("</b>", "")
                .replace("<size8>", "")
                .replace("</size8>", "")
                .replace("<font20>", "")
                .replace("</font20>", "")
                .replace("<sem_margem>", "")
                .replace("<center>", "")
                .replace("</center>", "")
                .replace("<left>", "")
                .replace("</left>", "")
                .replaceAll("(?s)<qr>.*?</qr>", "");
    }

    private String centralizarSenha20EmTextoPuro(String texto) {
        if (texto == null || !texto.contains("<senha20>")) return texto;
        int largura = getTamanhoPapel() == 80 ? 48 : 32;
        StringBuilder saida = new StringBuilder();
        int pos = 0;
        while (true) {
            int ini = texto.indexOf("<senha20>", pos);
            if (ini < 0) {
                saida.append(texto.substring(pos));
                break;
            }
            saida.append(texto.substring(pos, ini));
            int fim = texto.indexOf("</senha20>", ini + 9);
            if (fim < 0) {
                saida.append(texto.substring(ini));
                break;
            }
            String senha = texto.substring(ini + 9, fim).trim();
            saida.append(center(senha, largura));
            pos = fim + 10;
        }
        return saida.toString();
    }

    /** Compatibilidade com chamadas antigas. */
    private String limparMarcadoresNegrito(String texto) {
        return limparMarcadoresEscPos(texto);
    }

    // ==================== PREPARACAO DE DADOS ESC/POS ====================

    /**
     * Prepara os dados completos ESC/POS com encoding correto para caracteres brasileiros.
     * 
     * v7.0.3 - Suporte a marcadores de negrito <b> e </b>:
     * O texto e dividido em segmentos pelos marcadores. Para cada segmento entre
     * <b> e </b>, sao inseridos os comandos ESC_BOLD_ON antes e ESC_BOLD_OFF depois.
     *
     * CORRECAO v7.0.0: Eliminadas as 2 impressoes em branco que saiam apos o corte.
     */
    private byte[] prepararDadosEscPos(String texto) {
        try {
            // Remover newlines excessivos do inicio/fim para controle preciso.
            String textoLimpo = texto;
            boolean semMargem = textoLimpo != null && textoLimpo.contains("<sem_margem>");
            if (textoLimpo == null) textoLimpo = "";
            textoLimpo = textoLimpo.replace("<sem_margem>", "");
            while (textoLimpo.startsWith("\n") || textoLimpo.startsWith("\r")) {
                textoLimpo = textoLimpo.substring(1);
            }
            while (textoLimpo.endsWith("\n") || textoLimpo.endsWith("\r")) {
                textoLimpo = textoLimpo.substring(0, textoLimpo.length() - 1);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ThermalPrinterDriver.Profile driver = getDriverProfile();

            // 1. Inicializar impressora
            baos.write(ESC_INIT);

            // 2. Configurar code page conforme o driver/modelo selecionado
            baos.write(driver.codePageCommand());

            // 3. Configurar espacamento de linha padrao
            baos.write(ESC_LINE_SPACING_DEFAULT);

            // 4. Alinhar a esquerda por padrao
            baos.write(ESC_ALIGN_LEFT);

            // 5. v8.0.12.3 - Processar texto com marcadores de negrito, alinhamento e tamanho gigante
            processarTextoComMarcadoresEscPos(baos, textoLimpo);

            // 6. Canhoto compacto nao recebe linha extra inferior.
            if (!semMargem) {
                baos.write(new byte[]{0x0A}); // 1 line feed apenas
            }

            // 7. Corte de papel. Em canhotos compactos (<sem_margem>), usa corte sem avanço.
            baos.write(semMargem ? cutCommandSemAvanco(driver) : driver.cutCommand());

            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao preparar dados ESC/POS", e);
            // Fallback: retornar texto simples com init e cut
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(ESC_INIT);
                try { baos.write(getDriverProfile().codePageCommand()); } catch (Exception ignored) {}
                String textoFb = limparMarcadoresEscPos(texto);
                while (textoFb.endsWith("\n")) {
                    textoFb = textoFb.substring(0, textoFb.length() - 1);
                }
                boolean semMargemFb = texto != null && texto.contains("<sem_margem>");
                baos.write(textoFb.getBytes("CP850"));
                if (!semMargemFb) baos.write(new byte[]{0x0A}); // 1 line feed apenas quando nao for canhoto compacto
                try {
                    ThermalPrinterDriver.Profile driverFb = getDriverProfile();
                    baos.write(semMargemFb ? cutCommandSemAvanco(driverFb) : driverFb.cutCommand());
                } catch (Exception ignored) {
                    baos.write(new byte[]{0x1D, 0x56, 0x01});
                }
                return baos.toByteArray();
            } catch (Exception ex) {
                return limparMarcadoresEscPos(texto).getBytes();
            }
        }
    }


    /**
     * Corte sem avanco de papel para canhotos compactos.
     * Evita margem inferior criada por comandos do tipo cut + feed.
     */
    private byte[] cutCommandSemAvanco(ThermalPrinterDriver.Profile driver) {
        if (driver != null && "none".equalsIgnoreCase(driver.cutMode)) {
            return new byte[0];
        }
        // Corte parcial sem feed. Mais seguro para termicas 58/80mm.
        return new byte[]{0x1D, 0x56, 0x01};
    }

    /**
     * v8.0.12.3 - Processa marcadores simples no texto para comandos ESC/POS.
     *
     * Marcadores aceitos:
     * - <b>...</b>          = negrito
     * - <size8>...</size8>  = fonte gigante 8x8, maior tamanho padrao ESC/POS
     * - <font20>...</font20>= compatibilidade antiga para fonte grande
     * - <senha20>...</senha20>= senha do canhoto em fonte 20: centralizada + negrito + ampliacao maxima ESC/POS
     * - <center>...</center>= alinhamento centralizado real da impressora
     * - <left>...</left>    = volta ao alinhamento esquerdo
     * - <qr>...</qr>        = QR Code ESC/POS centralizado, quando suportado pela impressora
     *
     * Observacao: impressoras termicas ESC/POS normalmente limitam o tamanho de fonte
     * a 8x8 no comando GS ! n. Usamos o maximo compativel para atender ao pedido
     * de senha extremamente grande sem quebrar a impressao.
     */
    private void processarTextoComMarcadoresEscPos(ByteArrayOutputStream baos, String texto) throws IOException {
        if (texto == null || texto.isEmpty()) return;

        int pos = 0;
        while (pos < texto.length()) {
            int next = proximoMarcador(texto, pos);
            if (next < 0) {
                baos.write(converterTextoParaBytes(texto.substring(pos)));
                break;
            }

            if (next > pos) {
                baos.write(converterTextoParaBytes(texto.substring(pos, next)));
            }

            if (texto.startsWith("<sem_margem>", next)) {
                pos = next + 12;
            } else if (texto.startsWith("<senha20>", next)) {
                int fimSenha = texto.indexOf("</senha20>", next + 9);
                if (fimSenha >= 0) {
                    String senha = texto.substring(next + 9, fimSenha).trim();
                    escreverSenhaCanhotoGrande(baos, senha);
                    pos = fimSenha + 10;
                } else {
                    pos = next + 9;
                }
            } else if (texto.startsWith("<b>", next)) {
                baos.write(ESC_BOLD_ON);
                pos = next + 3;
            } else if (texto.startsWith("</b>", next)) {
                baos.write(ESC_BOLD_OFF);
                pos = next + 4;
            } else if (texto.startsWith("<size8>", next)) {
                baos.write(GS_CHAR_SIZE_8X);
                pos = next + 7;
            } else if (texto.startsWith("</size8>", next)) {
                baos.write(GS_CHAR_SIZE_NORMAL);
                pos = next + 8;
            } else if (texto.startsWith("<font20>", next)) {
                // Fonte 20 solicitada para o canhoto: usa o maior tamanho ESC/POS compativel.
                baos.write(GS_CHAR_SIZE_8X);
                pos = next + 8;
            } else if (texto.startsWith("</font20>", next)) {
                baos.write(GS_CHAR_SIZE_NORMAL);
                pos = next + 9;
            } else if (texto.startsWith("<center>", next)) {
                baos.write(ESC_ALIGN_CENTER);
                pos = next + 8;
            } else if (texto.startsWith("</center>", next)) {
                baos.write(ESC_ALIGN_LEFT);
                pos = next + 9;
            } else if (texto.startsWith("<left>", next)) {
                baos.write(ESC_ALIGN_LEFT);
                pos = next + 6;
            } else if (texto.startsWith("</left>", next)) {
                baos.write(ESC_ALIGN_LEFT);
                pos = next + 7;
            } else if (texto.startsWith("<qr>", next)) {
                int fimQr = texto.indexOf("</qr>", next + 4);
                if (fimQr >= 0) {
                    String conteudoQr = texto.substring(next + 4, fimQr).trim();
                    escreverQrCodeEscPos(baos, conteudoQr);
                    pos = fimQr + 5;
                } else {
                    baos.write(converterTextoParaBytes("[QR CODE]"));
                    pos = next + 4;
                }
            } else if (texto.startsWith("[LOGO_CUPOM]", next)) {
                // Logo do cupom - imprimir imagem ESC/POS se disponivel
                try {
                    android.graphics.Bitmap logoBitmap = LogoManager.carregarLogo(context);
                    if (logoBitmap != null) {
                        byte[] logoBytes = converterBitmapParaEscPos(logoBitmap);
                        if (logoBytes != null && logoBytes.length > 0) {
                            baos.write(ESC_ALIGN_CENTER);
                            baos.write(logoBytes);
                            baos.write(new byte[]{0x0A});
                            baos.write(ESC_ALIGN_LEFT);
                        }
                    }
                } catch (Exception logoEx) {
                    Log.w(TAG, "Erro ao imprimir logo: " + logoEx.getMessage());
                }
                pos = next + 12; // [LOGO_CUPOM] tem 12 chars
            } else {
                // Marcador desconhecido: imprime como texto para nao perder conteudo.
                baos.write(converterTextoParaBytes(texto.substring(next, next + 1)));
                pos = next + 1;
            }
        }

        // Garantir que qualquer estilo fique normal ao final.
        baos.write(GS_CHAR_SIZE_NORMAL);
        baos.write(ESC_BOLD_OFF);
        baos.write(ESC_ALIGN_LEFT);
    }

    /** Compatibilidade com a versao anterior: agora delega para o parser completo. */
    private void processarTextoComNegrito(ByteArrayOutputStream baos, String texto) throws IOException {
        processarTextoComMarcadoresEscPos(baos, texto);
    }

    private int proximoMarcador(String texto, int inicio) {
        String[] tags = {"<sem_margem>", "<senha20>", "</senha20>", "<b>", "</b>", "<size8>", "</size8>", "<font20>", "</font20>", "<center>", "</center>", "<left>", "</left>", "<qr>", "[LOGO_CUPOM]"};
        int menor = -1;
        for (String tag : tags) {
            int idx = texto.indexOf(tag, inicio);
            if (idx >= 0 && (menor < 0 || idx < menor)) menor = idx;
        }
        return menor;
    }



    /**
     * Senha do canhoto com centralizacao real, negrito e fonte 20 do sistema.
     * O marcador <senha20> representa o tamanho 20 solicitado para a letra da senha.
     * Como ESC/POS nao trabalha em pontos tipograficos, convertemos a fonte 20
     * para GS ! 0x77, o maior tamanho real suportado pela maioria das termicas.
     */
    private void escreverSenhaCanhotoGrande(ByteArrayOutputStream baos, String senha) throws IOException {
        if (senha == null) senha = "";
        senha = senha.trim();

        // v8.0.12.8 - Fonte 20 da senha sem linhas extras antes/depois.
        // Centraliza usando comando real ESC/POS e mantem fallback em texto puro no limparMarcadoresEscPos.
        baos.write(GS_CHAR_SIZE_NORMAL);
        baos.write(ESC_BOLD_OFF);
        baos.write(ESC_ALIGN_CENTER);
        baos.write(ESC_BOLD_ON);
        baos.write(GS_CHAR_SIZE_8X);
        baos.write(converterTextoParaBytes(senha));
        baos.write(new byte[]{0x0A});
        baos.write(GS_CHAR_SIZE_NORMAL);
        baos.write(ESC_BOLD_OFF);
        baos.write(ESC_ALIGN_LEFT);
    }

    /**
     * v8.0.12.6 - Imprime QR Code nativo ESC/POS.
     * Funciona em impressoras termicas compativeis com o comando GS ( k.
     * Se a impressora nao suportar QR nativo, o texto do canhoto continua sendo impresso normalmente.
     */
    private void escreverQrCodeEscPos(ByteArrayOutputStream baos, String conteudo) throws IOException {
        if (conteudo == null || conteudo.trim().isEmpty()) return;
        ThermalPrinterDriver.Profile driver = getDriverProfile();
        if (!driver.qrSupported) {
            baos.write(ESC_ALIGN_CENTER);
            baos.write(converterTextoParaBytes("[QR CODE NAO SUPORTADO NESTE DRIVER]\n"));
            baos.write(ESC_ALIGN_LEFT);
            return;
        }

        byte[] data = conteudo.trim().getBytes(driver.charset);
        int len = data.length + 3;
        int pL = len % 256;
        int pH = len / 256;

        // Garantir estilos normais antes do QR
        baos.write(GS_CHAR_SIZE_NORMAL);
        baos.write(ESC_BOLD_OFF);
        baos.write(ESC_ALIGN_CENTER);

        // Modelo QR Code 2
        baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00});
        // Tamanho do modulo conforme driver/modelo selecionado
        int qrSize = Math.max(3, Math.min(12, driver.qrModuleSize));
        baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) qrSize});
        // Correcao de erro nivel M
        baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31});
        // Armazenar dados do QR
        baos.write(new byte[]{0x1D, 0x28, 0x6B, (byte) pL, (byte) pH, 0x31, 0x50, 0x30});
        baos.write(data);
        // Imprimir QR
        baos.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});
        baos.write(new byte[]{0x0A});

        baos.write(ESC_ALIGN_LEFT);
    }

    /**
     * Converte um Bitmap para o formato ESC/POS raster (GS v 0) para impressao de logo.
     * Usa o formato de imagem raster padrao ESC/POS compativel com a maioria das termicas.
     */
    private byte[] converterBitmapParaEscPos(Bitmap bitmap) {
        if (bitmap == null) return new byte[0];
        try {
            // Converter para escala de cinza e binarizar
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            // Ajustar largura para multiplo de 8 (requisito ESC/POS)
            int widthBytes = (width + 7) / 8;
            int widthAligned = widthBytes * 8;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Comando GS v 0 (raster bit image)
            // GS v 0 m xL xH yL yH d1...dk
            // m=0 (normal density), xL/xH = bytes por linha, yL/yH = linhas
            int xL = widthBytes & 0xFF;
            int xH = (widthBytes >> 8) & 0xFF;
            int yL = height & 0xFF;
            int yH = (height >> 8) & 0xFF;

            baos.write(new byte[]{0x1D, 0x76, 0x30, 0x00, (byte) xL, (byte) xH, (byte) yL, (byte) yH});

            // Converter pixels para bits
            for (int y = 0; y < height; y++) {
                for (int xByte = 0; xByte < widthBytes; xByte++) {
                    int byteVal = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = xByte * 8 + bit;
                        if (x < width) {
                            int pixel = bitmap.getPixel(x, y);
                            int r = Color.red(pixel);
                            int g = Color.green(pixel);
                            int b = Color.blue(pixel);
                            int gray = (r * 299 + g * 587 + b * 114) / 1000;
                            // Pixel escuro (< 128) = 1 (imprime), claro = 0
                            if (gray < 128) {
                                byteVal |= (0x80 >> bit);
                            }
                        }
                    }
                    baos.write(byteVal);
                }
            }

            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao converter bitmap para ESC/POS: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Converte texto para bytes usando o encoding mais compativel com impressoras termicas.
     * Tenta CP850 (melhor para acentos brasileiros), depois Windows-1252, depois ISO-8859-1.
     *
     * Mapeamento de caracteres especiais brasileiros:
     * - a, e, i, o, u (acentos agudos, graves, circunflexos, til)
     * - c cedilha
     * - Todos suportados em CP850
     */
    private byte[] converterTextoParaBytes(String texto) {
        if (texto == null) return new byte[0];

        try {
            ThermalPrinterDriver.Profile driver = getDriverProfile();
            if ("US-ASCII".equalsIgnoreCase(driver.charset)) {
                return removerAcentos(texto).getBytes("US-ASCII");
            }
            return texto.getBytes(driver.charset);
        } catch (Exception e1) {
            try {
                // Fallback: Windows-1252
                return texto.getBytes("Windows-1252");
            } catch (Exception e2) {
                try {
                    // Fallback: ISO-8859-1
                    return texto.getBytes("ISO-8859-1");
                } catch (Exception e3) {
                    // Ultimo fallback: remover acentos e usar ASCII
                    return removerAcentos(texto).getBytes();
                }
            }
        }
    }

    /**
     * Remove acentos de caracteres brasileiros como ultimo fallback.
     */
    private String removerAcentos(String texto) {
        if (texto == null) return "";
        return texto
                .replace("á", "a").replace("à", "a").replace("â", "a").replace("ã", "a").replace("ä", "a")
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
                .replace("í", "i").replace("ì", "i").replace("î", "i").replace("ï", "i")
                .replace("ó", "o").replace("ò", "o").replace("ô", "o").replace("õ", "o").replace("ö", "o")
                .replace("ú", "u").replace("ù", "u").replace("û", "u").replace("ü", "u")
                .replace("ç", "c").replace("ñ", "n")
                .replace("Á", "A").replace("À", "A").replace("Â", "A").replace("Ã", "A").replace("Ä", "A")
                .replace("É", "E").replace("È", "E").replace("Ê", "E").replace("Ë", "E")
                .replace("Í", "I").replace("Ì", "I").replace("Î", "I").replace("Ï", "I")
                .replace("Ó", "O").replace("Ò", "O").replace("Ô", "O").replace("Õ", "O").replace("Ö", "O")
                .replace("Ú", "U").replace("Ù", "U").replace("Û", "U").replace("Ü", "U")
                .replace("Ç", "C").replace("Ñ", "N");
    }

    // ==================== IMPRESSAO VIA REDE TCP/IP ====================

    /**
     * Imprime via rede TCP/IP com retry automatico.
     */
    private boolean imprimirRedeComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao Rede - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirRede(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via Rede apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Envia dados RAW para impressora via socket TCP/IP.
     * Otimizado com timeouts adequados e conexao robusta.
     */
    private boolean imprimirRede(byte[] dados) {
        Socket socket = null;
        OutputStream os = null;
        try {
            String ip = getIpImpressora().trim();
            int porta = getPortaImpressora();

            if (ip.isEmpty()) {
                Log.e(TAG, "IP da impressora nao configurado");
                return false;
            }

            Log.d(TAG, "Conectando a impressora de rede: " + ip + ":" + porta);

            socket = new Socket();
            socket.setSoTimeout(NETWORK_SO_TIMEOUT_MS);
            socket.setTcpNoDelay(true); // Desabilitar Nagle para envio imediato
            socket.setSendBufferSize(dados.length + 1024);
            socket.setKeepAlive(false);

            // Conectar com timeout
            socket.connect(new InetSocketAddress(ip, porta), NETWORK_CONNECT_TIMEOUT_MS);

            os = socket.getOutputStream();

            // Enviar dados em blocos para evitar buffer overflow em impressoras lentas
            int offset = 0;
            int blockSize = 1024;
            while (offset < dados.length) {
                int len = Math.min(blockSize, dados.length - offset);
                os.write(dados, offset, len);
                os.flush();
                offset += len;
                // Pequeno delay entre blocos para impressoras lentas
                if (offset < dados.length) {
                    Thread.sleep(10);
                }
            }

            os.flush();

            // Aguardar um pouco para a impressora processar
            Thread.sleep(200);

            Log.i(TAG, "Impressao via Rede concluida com sucesso: " + ip + ":" + porta + " (" + dados.length + " bytes)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via rede: " + e.getMessage(), e);
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    // ==================== IMPRESSAO VIA BLUETOOTH DIRETO ====================

    /**
     * Imprime via Bluetooth direto com retry automatico.
     * Conecta diretamente a uma impressora termica Bluetooth pareada.
     */
    private boolean imprimirBluetoothComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao Bluetooth - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirBluetooth(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via Bluetooth apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Implementacao completa de impressao via Bluetooth direto.
     * Utiliza multiplas estrategias de conexao para maxima compatibilidade:
     * 1. RFCOMM SPP seguro (createRfcommSocketToServiceRecord)
     * 2. RFCOMM SPP inseguro (createInsecureRfcommSocketToServiceRecord)
     * 3. RFCOMM RAW canal 1 (via reflexao - fallback)
     */
    private boolean imprimirBluetooth(byte[] dados) {
        String macAddress = getMacBluetooth().trim();
        if (macAddress.isEmpty()) {
            Log.e(TAG, "MAC Bluetooth nao configurado");
            return false;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Log.e(TAG, "Bluetooth nao disponivel neste dispositivo");
            return false;
        }

        if (!adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth esta desativado");
            return false;
        }

        // Cancelar discovery para melhorar performance de conexao
        try {
            adapter.cancelDiscovery();
        } catch (SecurityException ignored) {}

        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(macAddress);
        } catch (Exception e) {
            Log.e(TAG, "MAC Bluetooth invalido: " + macAddress, e);
            return false;
        }

        // Estrategia 1: RFCOMM SPP seguro
        Log.d(TAG, "Bluetooth: Tentando conexao SPP segura com " + macAddress);
        BluetoothSocket socket = null;
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            boolean result = enviarDadosBluetooth(socket, dados);
            if (result) {
                Log.i(TAG, "Impressao Bluetooth SPP seguro concluida com sucesso");
                return true;
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Sem permissao Bluetooth: " + se.getMessage());
            fecharSocket(socket);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Falha na estrategia SPP seguro: " + e.getMessage());
            fecharSocket(socket);
            socket = null;
        }

        // Estrategia 2: RFCOMM SPP inseguro
        Log.d(TAG, "Bluetooth: Tentando conexao SPP insegura com " + macAddress);
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            boolean result = enviarDadosBluetooth(socket, dados);
            if (result) {
                Log.i(TAG, "Impressao Bluetooth SPP inseguro concluida com sucesso");
                return true;
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Sem permissao Bluetooth: " + se.getMessage());
            fecharSocket(socket);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Falha na estrategia SPP inseguro: " + e.getMessage());
            fecharSocket(socket);
            socket = null;
        }

        // Estrategia 3: RFCOMM RAW via reflexao (canal 1)
        Log.d(TAG, "Bluetooth: Tentando conexao RAW canal 1 com " + macAddress);
        try {
            java.lang.reflect.Method m = device.getClass().getMethod(
                    "createRfcommSocket", new Class[]{int.class});
            socket = (BluetoothSocket) m.invoke(device, 1);
            socket.connect();
            boolean result = enviarDadosBluetooth(socket, dados);
            if (result) {
                Log.i(TAG, "Impressao Bluetooth RAW canal 1 concluida com sucesso");
                return true;
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Sem permissao Bluetooth: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Falha na estrategia RAW canal 1: " + e.getMessage());
        } finally {
            fecharSocket(socket);
        }

        // Estrategia 4: RFCOMM RAW via reflexao (canal 2)
        Log.d(TAG, "Bluetooth: Tentando conexao RAW canal 2 com " + macAddress);
        try {
            java.lang.reflect.Method m = device.getClass().getMethod(
                    "createRfcommSocket", new Class[]{int.class});
            socket = (BluetoothSocket) m.invoke(device, 2);
            socket.connect();
            boolean result = enviarDadosBluetooth(socket, dados);
            if (result) {
                Log.i(TAG, "Impressao Bluetooth RAW canal 2 concluida com sucesso");
                return true;
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Sem permissao Bluetooth: " + se.getMessage());
        } catch (Exception e) {
            Log.w(TAG, "Falha na estrategia RAW canal 2: " + e.getMessage());
        } finally {
            fecharSocket(socket);
        }

        Log.e(TAG, "Todas as estrategias de conexao Bluetooth falharam para " + macAddress);
        return false;
    }

    /**
     * Envia dados para uma conexao Bluetooth ja estabelecida.
     * Envia em blocos com delay para evitar buffer overflow.
     */
    private boolean enviarDadosBluetooth(BluetoothSocket socket, byte[] dados) {
        if (socket == null || !socket.isConnected()) return false;

        OutputStream os = null;
        try {
            os = socket.getOutputStream();

            // Enviar em blocos de 512 bytes (impressoras BT tem buffer menor)
            int offset = 0;
            int blockSize = 512;
            while (offset < dados.length) {
                int len = Math.min(blockSize, dados.length - offset);
                os.write(dados, offset, len);
                os.flush();
                offset += len;
                // Delay entre blocos para impressoras BT lentas
                if (offset < dados.length) {
                    Thread.sleep(20);
                }
            }

            os.flush();

            // Aguardar impressora processar
            Thread.sleep(500);

            Log.i(TAG, "Dados Bluetooth enviados: " + dados.length + " bytes");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar dados Bluetooth: " + e.getMessage(), e);
            return false;
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

    // ==================== IMPRESSAO VIA USB ====================

    /**
     * Imprime via USB com retry automatico.
     * Usa UsbManager do Android para comunicacao direta com impressoras USB.
     */
    private boolean imprimirUsbComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao USB - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirUsb(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via USB apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Implementacao completa de impressao via USB.
     * Detecta automaticamente impressoras USB conectadas e envia dados RAW.
     *
     * Compativel com:
     * - Impressoras termicas USB (classe 7 - Printer)
     * - Impressoras com interface Bulk Transfer
     * - Impressoras com interface de controle
     */
    private boolean imprimirUsb(byte[] dados) {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                Log.e(TAG, "UsbManager nao disponivel");
                return false;
            }

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList == null || deviceList.isEmpty()) {
                Log.e(TAG, "Nenhum dispositivo USB conectado");
                return false;
            }

            Log.d(TAG, "Dispositivos USB encontrados: " + deviceList.size());

            // Procurar impressora USB (classe 7 = Printer)
            UsbDevice printerDevice = null;
            UsbInterface printerInterface = null;
            UsbEndpoint endpointOut = null;

            for (UsbDevice device : deviceList.values()) {
                Log.d(TAG, "USB Device: " + device.getDeviceName()
                        + " VID:" + String.format("0x%04X", device.getVendorId())
                        + " PID:" + String.format("0x%04X", device.getProductId())
                        + " Class:" + device.getDeviceClass());

                // Verificar cada interface do dispositivo
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);

                    // Classe 7 = Printer
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                        printerDevice = device;
                        printerInterface = usbInterface;

                        // Encontrar endpoint de saida (OUT)
                        for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                            UsbEndpoint ep = usbInterface.getEndpoint(j);
                            if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                                endpointOut = ep;
                                break;
                            }
                        }
                        break;
                    }

                    // Fallback: verificar por Bulk Transfer OUT (algumas impressoras nao reportam classe 7)
                    if (printerDevice == null) {
                        for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                            UsbEndpoint ep = usbInterface.getEndpoint(j);
                            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                                    && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                                // Verificar se parece ser uma impressora pelo VID/PID
                                if (isLikelyPrinterDevice(device)) {
                                    printerDevice = device;
                                    printerInterface = usbInterface;
                                    endpointOut = ep;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (printerDevice != null && endpointOut != null) break;
            }

            if (printerDevice == null || printerInterface == null || endpointOut == null) {
                Log.e(TAG, "Nenhuma impressora USB encontrada entre os dispositivos conectados");
                return false;
            }

            // Verificar permissao USB
            if (!usbManager.hasPermission(printerDevice)) {
                Log.e(TAG, "Sem permissao USB para o dispositivo: " + printerDevice.getDeviceName()
                        + ". O usuario precisa conceder permissao.");
                return false;
            }

            // Abrir conexao USB
            UsbDeviceConnection connection = usbManager.openDevice(printerDevice);
            if (connection == null) {
                Log.e(TAG, "Falha ao abrir conexao USB com: " + printerDevice.getDeviceName());
                return false;
            }

            try {
                // Reivindicar interface
                boolean claimed = connection.claimInterface(printerInterface, true);
                if (!claimed) {
                    Log.e(TAG, "Falha ao reivindicar interface USB");
                    return false;
                }

                // Enviar dados em blocos
                int offset = 0;
                int blockSize = endpointOut.getMaxPacketSize();
                if (blockSize <= 0) blockSize = 64; // Fallback

                Log.d(TAG, "USB: Enviando " + dados.length + " bytes em blocos de " + blockSize);

                while (offset < dados.length) {
                    int len = Math.min(blockSize, dados.length - offset);
                    byte[] block = new byte[len];
                    System.arraycopy(dados, offset, block, 0, len);

                    int transferred = connection.bulkTransfer(endpointOut, block, len, USB_TIMEOUT_MS);
                    if (transferred < 0) {
                        Log.e(TAG, "Falha no bulk transfer USB no offset " + offset);
                        return false;
                    }

                    offset += len;
                }

                // Liberar interface
                connection.releaseInterface(printerInterface);

                Log.i(TAG, "Impressao USB concluida com sucesso: " + dados.length + " bytes enviados para "
                        + printerDevice.getDeviceName());
                return true;
            } finally {
                connection.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via USB: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifica se um dispositivo USB e provavelmente uma impressora
     * baseado no Vendor ID (VID) de fabricantes conhecidos.
     */
    private boolean isLikelyPrinterDevice(UsbDevice device) {
        int vid = device.getVendorId();
        // VIDs de fabricantes de impressoras termicas comuns
        int[] printerVids = {
                0x04B8, // Epson
                0x0519, // Star Micronics
                0x0DD4, // Custom
                0x0FE6, // ICS Electronics (Bematech)
                0x20D1, // Bematech
                0x0416, // Winbond (Daruma)
                0x0483, // STMicroelectronics (varias impressoras)
                0x1504, // Elgin
                0x1FC9, // NXP (varias impressoras)
                0x0FE6, // Kontron
                0x0525, // PLX Technology (impressoras genericas)
                0x1A86, // QinHeng Electronics (CH340 - adaptadores USB-Serial)
                0x067B, // Prolific (PL2303 - adaptadores USB-Serial)
                0x0403, // FTDI (adaptadores USB-Serial)
                0x10C4, // Silicon Labs (CP210x - adaptadores USB-Serial)
                0x1CBE, // Tanca
                0x0DD4, // Gertec
                0x28E9, // GD32 (impressoras genericas chinesas)
                0x0FE6, // ICS
                0x154F, // SNBC (impressoras termicas)
                0x0493, // Sweda
        };

        for (int pvid : printerVids) {
            if (vid == pvid) return true;
        }

        // Verificar pela classe do dispositivo
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_PRINTER) return true;

        // Verificar pelo nome do produto (se disponivel)
        String productName = device.getProductName();
        if (productName != null) {
            String upper = productName.toUpperCase();
            if (upper.contains("PRINTER") || upper.contains("PRINT")
                    || upper.contains("RECEIPT") || upper.contains("THERMAL")
                    || upper.contains("POS") || upper.contains("TM-")
                    || upper.contains("MP-") || upper.contains("EPSON")
                    || upper.contains("BEMATECH") || upper.contains("ELGIN")
                    || upper.contains("DARUMA") || upper.contains("TANCA")
                    || upper.contains("SWEDA") || upper.contains("STAR")
                    || upper.contains("CITIZEN") || upper.contains("CUSTOM")) {
                return true;
            }
        }

        return false;
    }

    // ==================== IMPRESSAO VIA SMB/CIFS ====================

    /**
     * Imprime via SMB/CIFS com retry automatico.
     */
    private boolean imprimirSmbComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao SMB - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirSmb(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via SMB apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Imprime via impressora compartilhada na rede Windows (SMB/CIFS).
     */
    private boolean imprimirSmb(byte[] dados) {
        SmbFileOutputStream smbOut = null;
        try {
            String host = getSmbHost().trim();
            String share = getSmbShare().trim();
            String user = getSmbUser().trim();
            String password = getSmbPassword();
            String domain = getSmbDomain().trim();

            if (host.isEmpty() || share.isEmpty()) {
                Log.e(TAG, "Host ou compartilhamento SMB nao configurado");
                return false;
            }

            configurarJcifs();

            String smbUrl = "smb://" + host + "/" + share;

            NtlmPasswordAuthentication auth = criarAuthSmb(domain, user, password);

            SmbFile smbPrinter = new SmbFile(smbUrl, auth);
            smbOut = new SmbFileOutputStream(smbPrinter);

            // Enviar dados em blocos
            int offset = 0;
            int blockSize = 4096;
            while (offset < dados.length) {
                int len = Math.min(blockSize, dados.length - offset);
                smbOut.write(dados, offset, len);
                offset += len;
            }

            smbOut.flush();
            smbOut.close();
            smbOut = null;

            Log.i(TAG, "Impressao SMB/CIFS concluida com sucesso em " + smbUrl + " (" + dados.length + " bytes)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via SMB/CIFS: " + e.getMessage(), e);
            return false;
        } finally {
            if (smbOut != null) {
                try { smbOut.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== IMPRESSAO VIA SMB DIRETO ====================

    /**
     * Imprime via SMB Direto com retry automatico.
     */
    private boolean imprimirSmbDiretoComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao SMB Direto - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirSmbDireto(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via SMB Direto apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Imprime via conexao direta SMB usando caminho Windows.
     */
    private boolean imprimirSmbDireto(byte[] dados) {
        SmbFileOutputStream smbOut = null;
        try {
            String caminho = getSmbDiretoCaminho().trim();
            String user = getSmbDiretoUser().trim();
            String password = getSmbDiretoPassword();
            String domain = getSmbDiretoDomain().trim();

            if (caminho.isEmpty()) {
                Log.e(TAG, "Caminho SMB direto nao configurado");
                return false;
            }

            String smbUrl = windowsPathToSmbUrl(caminho);
            if (smbUrl == null) {
                Log.e(TAG, "Caminho SMB direto invalido: " + caminho);
                return false;
            }

            configurarJcifs();

            NtlmPasswordAuthentication auth = criarAuthSmb(domain, user, password);

            SmbFile smbPrinter = new SmbFile(smbUrl, auth);
            smbOut = new SmbFileOutputStream(smbPrinter);

            // Enviar dados em blocos
            int offset = 0;
            int blockSize = 4096;
            while (offset < dados.length) {
                int len = Math.min(blockSize, dados.length - offset);
                smbOut.write(dados, offset, len);
                offset += len;
            }

            smbOut.flush();
            smbOut.close();
            smbOut = null;

            Log.i(TAG, "Impressao SMB Direto concluida com sucesso em " + smbUrl + " (" + dados.length + " bytes)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via SMB Direto: " + e.getMessage(), e);
            return false;
        } finally {
            if (smbOut != null) {
                try { smbOut.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ==================== IMPRESSAO VIA BLUETOOTH WINDOWS ====================

    /**
     * Imprime via Bluetooth Windows (via PC/Notebook).
     * Usa BluetoothWindowsPrintManager para comunicacao com o computador.
     */
    private boolean imprimirBluetoothWindows(String texto) {
        String macAddress = getBtWinDeviceMac();
        if (macAddress == null || macAddress.isEmpty()) {
            Log.e(TAG, "MAC do dispositivo Bluetooth Windows nao configurado");
            return false;
        }

        BluetoothWindowsPrintManager btManager = new BluetoothWindowsPrintManager(context);

        if (!btManager.isBluetoothAvailable()) {
            Log.e(TAG, "Bluetooth nao disponivel");
            return false;
        }

        if (!btManager.isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth desativado");
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);

        btManager.imprimirViaBluetoothWindows(macAddress, texto,
                new BluetoothWindowsPrintManager.PrintCallback() {
                    @Override
                    public void onPrintSuccess() {
                        Log.i(TAG, "Impressao Bluetooth Windows concluida com sucesso");
                        result.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onPrintError(String message) {
                        Log.e(TAG, "Erro na impressao Bluetooth Windows: " + message);
                        result.set(false);
                        latch.countDown();
                    }

                    @Override
                    public void onProgress(String status) {
                        Log.d(TAG, "Progresso BT Windows: " + status);
                    }
                });

        try {
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Timeout na impressao Bluetooth Windows");
                return false;
            }
            return result.get();
        } catch (InterruptedException e) {
            Log.e(TAG, "Impressao Bluetooth Windows interrompida", e);
            return false;
        }
    }

    // ==================== UTILIDADES COMUNS ====================

    /**
     * Configura propriedades do jCIFS para conexoes SMB.
     */
    private void configurarJcifs() {
        jcifs.Config.setProperty("jcifs.smb.client.responseTimeout", "10000");
        jcifs.Config.setProperty("jcifs.smb.client.soTimeout", "15000");
        jcifs.Config.setProperty("jcifs.netbios.retryTimeout", "5000");
        jcifs.Config.setProperty("jcifs.smb.client.useExtendedSecurity", "false");
        jcifs.Config.setProperty("jcifs.smb.client.disablePlainTextPasswords", "false");
        jcifs.Config.setProperty("jcifs.resolveOrder", "DNS,BCAST");
    }

    /**
     * Cria autenticacao SMB.
     */
    private NtlmPasswordAuthentication criarAuthSmb(String domain, String user, String password) {
        if (user == null || user.trim().isEmpty()) {
            return NtlmPasswordAuthentication.ANONYMOUS;
        }
        return new NtlmPasswordAuthentication(
                (domain == null || domain.trim().isEmpty()) ? "WORKGROUP" : domain.trim(),
                user.trim(),
                password != null ? password : ""
        );
    }

    // ==================== TESTES DE CONEXAO ====================

    /**
     * Testa a conexao com a impressora de rede.
     */
    public boolean testarConexaoRede() {
        Socket socket = null;
        try {
            String ip = getIpImpressora().trim();
            int porta = getPortaImpressora();

            if (ip.isEmpty()) return false;

            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, porta), NETWORK_CONNECT_TIMEOUT_MS);

            boolean connected = socket.isConnected();
            Log.i(TAG, "Teste de conexao Rede " + (connected ? "bem-sucedido" : "falhou") + ": " + ip + ":" + porta);
            return connected;
        } catch (Exception e) {
            Log.e(TAG, "Falha no teste de conexao Rede", e);
            return false;
        } finally {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Testa a conexao Bluetooth direta.
     */
    public boolean testarConexaoBluetooth() {
        String macAddress = getMacBluetooth().trim();
        if (macAddress.isEmpty()) return false;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;

        try {
            adapter.cancelDiscovery();
        } catch (SecurityException ignored) {}

        BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(macAddress);
        } catch (Exception e) {
            return false;
        }

        BluetoothSocket socket = null;

        // Tentar SPP
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            Log.i(TAG, "Teste Bluetooth SPP bem-sucedido: " + macAddress);
            return true;
        } catch (Exception e) {
            fecharSocket(socket);
            socket = null;
        }

        // Tentar inseguro
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            Log.i(TAG, "Teste Bluetooth inseguro bem-sucedido: " + macAddress);
            return true;
        } catch (Exception e) {
            fecharSocket(socket);
            socket = null;
        }

        // Tentar RAW canal 1
        try {
            java.lang.reflect.Method m = device.getClass().getMethod(
                    "createRfcommSocket", new Class[]{int.class});
            socket = (BluetoothSocket) m.invoke(device, 1);
            socket.connect();
            Log.i(TAG, "Teste Bluetooth RAW bem-sucedido: " + macAddress);
            return true;
        } catch (Exception e) {
            fecharSocket(socket);
        }

        return false;
    }

    /**
     * Testa a conexao USB.
     */
    public boolean testarConexaoUsb() {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) return false;

            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList == null || deviceList.isEmpty()) return false;

            for (UsbDevice device : deviceList.values()) {
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface usbInterface = device.getInterface(i);
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                        Log.i(TAG, "Impressora USB encontrada: " + device.getDeviceName());
                        return true;
                    }
                }
                if (isLikelyPrinterDevice(device)) {
                    Log.i(TAG, "Provavel impressora USB encontrada: " + device.getDeviceName());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro no teste USB", e);
            return false;
        }
    }

    /**
     * Testa a conexao SMB/CIFS.
     */
    public boolean testarConexaoSmb() {
        try {
            String host = getSmbHost().trim();
            String share = getSmbShare().trim();
            String user = getSmbUser().trim();
            String password = getSmbPassword();
            String domain = getSmbDomain().trim();

            if (host.isEmpty() || share.isEmpty()) return false;

            configurarJcifs();

            String smbUrl = "smb://" + host + "/" + share;
            NtlmPasswordAuthentication auth = criarAuthSmb(domain, user, password);

            SmbFile smbPrinter = new SmbFile(smbUrl, auth);
            smbPrinter.exists();
            Log.i(TAG, "Teste de conexao SMB/CIFS bem-sucedido: " + smbUrl);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Falha no teste de conexao SMB/CIFS", e);
            return false;
        }
    }

    /**
     * Testa a conexao SMB direta.
     */
    public boolean testarConexaoSmbDireto(String caminho, String user, String password, String domain) {
        try {
            if (caminho == null || caminho.trim().isEmpty()) return false;

            String smbUrl = windowsPathToSmbUrl(caminho.trim());
            if (smbUrl == null) return false;

            configurarJcifs();

            NtlmPasswordAuthentication auth = criarAuthSmb(
                    domain != null ? domain.trim() : "",
                    user != null ? user.trim() : "",
                    password != null ? password : ""
            );

            SmbFile smbPrinter = new SmbFile(smbUrl, auth);
            smbPrinter.exists();
            Log.i(TAG, "Teste de conexao SMB Direto bem-sucedido: " + smbUrl);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Falha no teste de conexao SMB Direto", e);
            return false;
        }
    }

    /**
     * Testa a conexao Bluetooth Windows.
     */
    public boolean testarConexaoBtWindows() {
        String macAddress = getBtWinDeviceMac();
        if (macAddress == null || macAddress.isEmpty()) return false;

        BluetoothWindowsPrintManager btManager = new BluetoothWindowsPrintManager(context);
        return btManager.testarConexao(macAddress);
    }

    /**
     * Verifica se alguma impressora esta configurada.
     */
    public boolean isImpressoraConfigurada() {
        return !TIPO_NENHUMA.equals(getTipoImpressora());
    }

    /**
     * Imprime uma pagina de teste para verificar se a impressora esta funcionando.
     * Inclui caracteres especiais brasileiros para testar encoding.
     */
    public boolean imprimirPaginaTeste() {
        int colunas = getColunasTexto();
        String line = repeat("-", colunas);

        StringBuilder sb = new StringBuilder();
        sb.append(center("=== PAGINA DE TESTE ===", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append(center("PDV Pro v8.0.0", colunas)).append("\n");
        sb.append(center("Impressora configurada!", colunas)).append("\n");
        sb.append(line).append("\n");
        sb.append("Tipo: ").append(getTipoImpressora()).append("\n");
        sb.append("Papel: ").append(getTamanhoPapel()).append("mm\n");
        sb.append("Colunas: ").append(colunas).append("\n");
        sb.append(line).append("\n");
        sb.append("Teste de caracteres:\n");
        sb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n");
        sb.append("abcdefghijklmnopqrstuvwxyz\n");
        sb.append("0123456789\n");
        sb.append("!@#$%&*()-+=[]{}|;:',.<>?/\n");
        sb.append(line).append("\n");
        sb.append("Acentos brasileiros:\n");
        sb.append("aeiou AEIOU\n");
        sb.append("acoes informacoes\n");
        sb.append("cafe voce ate\n");
        sb.append(line).append("\n");
        sb.append(center("Impressao OK!", colunas)).append("\n");
        sb.append(center("phdatech (85) 98123-7727", colunas)).append("\n");

        return imprimirTexto(sb.toString());
    }

    // ==================== IMPRESSAO VIA PRINT SERVER (PC) ====================

    /**
     * Imprime via Servidor de Impressao Python rodando no PC.
     * Envia o texto via HTTP POST para o servidor.
     */
    private boolean imprimirViaPrintServer(String texto) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao Print Server - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (enviarParaPrintServer(texto)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via Print Server apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Envia texto para o servidor de impressao Python via HTTP POST.
     */
    private boolean enviarParaPrintServer(String texto) {
        HttpURLConnection conn = null;
        try {
            String ip = getPrintServerIp().trim();
            int porta = getPrintServerPorta();
            String impressora = getPrintServerImpressora();

            if (ip.isEmpty()) {
                Log.e(TAG, "IP do Print Server nao configurado");
                return false;
            }

            String urlStr = "http://" + ip + ":" + porta + "/print";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_SO_TIMEOUT_MS);
            conn.setDoOutput(true);

            // Montar JSON - v2.0: inclui largura do papel para controle de cupom
            JSONObject json = new JSONObject();
            json.put("texto", texto);
            json.put("documento", "PDV Pro");
            json.put("largura", getTamanhoPapel()); // Envia 58 ou 80mm
            if (impressora != null && !impressora.isEmpty()) {
                json.put("impressora", impressora);
            }

            byte[] postData = json.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(postData.length));

            OutputStream os = conn.getOutputStream();
            os.write(postData);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Ler resposta
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject respJson = new JSONObject(response.toString());
                boolean success = respJson.optBoolean("success", false);
                String message = respJson.optString("message", "");

                if (success) {
                    Log.i(TAG, "Impressao via Print Server concluida: " + message);
                    return true;
                } else {
                    Log.e(TAG, "Print Server retornou erro: " + message);
                    return false;
                }
            } else {
                Log.e(TAG, "Print Server retornou HTTP " + responseCode);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar para Print Server: " + e.getMessage(), e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Envia dados RAW (ESC/POS) para o servidor de impressao Python via HTTP POST.
     */
    public boolean enviarRawParaPrintServer(byte[] dados) {
        HttpURLConnection conn = null;
        try {
            String ip = getPrintServerIp().trim();
            int porta = getPrintServerPorta();
            String impressora = getPrintServerImpressora();

            if (ip.isEmpty()) {
                Log.e(TAG, "IP do Print Server nao configurado");
                return false;
            }

            String urlStr = "http://" + ip + ":" + porta + "/print_raw";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(NETWORK_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(NETWORK_SO_TIMEOUT_MS);
            conn.setDoOutput(true);

            // Converter para base64
            String base64Data = android.util.Base64.encodeToString(dados, android.util.Base64.NO_WRAP);

            JSONObject json = new JSONObject();
            json.put("data", base64Data);
            json.put("documento", "PDV Pro");
            if (impressora != null && !impressora.isEmpty()) {
                json.put("impressora", impressora);
            }

            byte[] postData = json.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(postData.length));

            OutputStream os = conn.getOutputStream();
            os.write(postData);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject respJson = new JSONObject(response.toString());
                return respJson.optBoolean("success", false);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao enviar RAW para Print Server: " + e.getMessage(), e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Testa a conexao com o servidor de impressao Python.
     */
    public boolean testarConexaoPrintServer() {
        HttpURLConnection conn = null;
        try {
            String ip = getPrintServerIp().trim();
            int porta = getPrintServerPorta();

            if (ip.isEmpty()) return false;

            String urlStr = "http://" + ip + ":" + porta + "/ping";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject respJson = new JSONObject(response.toString());
                boolean success = respJson.optBoolean("success", false);
                Log.i(TAG, "Teste Print Server: " + (success ? "OK" : "FALHA"));
                return success;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Falha no teste Print Server: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Lista as impressoras disponiveis no servidor de impressao Python.
     */
    public List<String> listarImpressorasPrintServer() {
        List<String> impressoras = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String ip = getPrintServerIp().trim();
            int porta = getPrintServerPorta();

            if (ip.isEmpty()) return impressoras;

            String urlStr = "http://" + ip + ":" + porta + "/printers";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject respJson = new JSONObject(response.toString());
                if (respJson.optBoolean("success", false)) {
                    JSONArray arr = respJson.optJSONArray("impressoras");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject imp = arr.getJSONObject(i);
                            String nome = imp.optString("nome", "");
                            boolean padrao = imp.optBoolean("padrao", false);
                            if (!nome.isEmpty()) {
                                impressoras.add(padrao ? nome + " [PADRAO]" : nome);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar impressoras do Print Server: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return impressoras;
    }

    // ==================== IMPRESSAO VIA REDE RAW/LPR ====================

    /**
     * Imprime via Rede IP Direto (RAW/LPR) com retry automatico.
     * Suporta protocolos RAW (porta 9100), LPR (porta 515) e IPP (porta 631).
     */
    private boolean imprimirRedeRawComRetry(byte[] dados) {
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            Log.i(TAG, "Impressao Rede RAW - Tentativa " + tentativa + "/" + MAX_RETRIES);
            if (imprimirRedeRaw(dados)) {
                return true;
            }
            if (tentativa < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        Log.e(TAG, "Falha na impressao via Rede RAW apos " + MAX_RETRIES + " tentativas");
        return false;
    }

    /**
     * Envia dados para impressora via Rede IP Direto.
     * Protocolo RAW: envia dados ESC/POS diretamente via socket TCP.
     * Protocolo LPR: envia via Line Printer Remote protocol.
     */
    private boolean imprimirRedeRaw(byte[] dados) {
        String protocolo = getRedeRawProtocolo();
        String ip = getRedeRawIp().trim();
        int porta = getRedeRawPorta();

        if (ip.isEmpty()) {
            Log.e(TAG, "IP da impressora Rede RAW nao configurado");
            return false;
        }

        if ("LPR".equalsIgnoreCase(protocolo)) {
            return imprimirLpr(ip, porta, dados);
        } else {
            // RAW ou IPP - envia via socket direto
            return imprimirRedeRawSocket(ip, porta, dados);
        }
    }

    /**
     * Envia dados RAW via socket TCP direto.
     */
    private boolean imprimirRedeRawSocket(String ip, int porta, byte[] dados) {
        Socket socket = null;
        OutputStream os = null;
        try {
            Log.d(TAG, "Conectando a impressora Rede RAW: " + ip + ":" + porta);

            socket = new Socket();
            socket.setSoTimeout(NETWORK_SO_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(dados.length + 1024);
            socket.setKeepAlive(false);
            socket.connect(new InetSocketAddress(ip, porta), NETWORK_CONNECT_TIMEOUT_MS);

            os = socket.getOutputStream();

            int offset = 0;
            int blockSize = 1024;
            while (offset < dados.length) {
                int len = Math.min(blockSize, dados.length - offset);
                os.write(dados, offset, len);
                os.flush();
                offset += len;
                if (offset < dados.length) {
                    Thread.sleep(10);
                }
            }

            os.flush();
            Thread.sleep(200);

            Log.i(TAG, "Impressao Rede RAW concluida: " + ip + ":" + porta + " (" + dados.length + " bytes)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via Rede RAW: " + e.getMessage(), e);
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Envia dados via protocolo LPR/LPD.
     */
    private boolean imprimirLpr(String ip, int porta, byte[] dados) {
        Socket socket = null;
        OutputStream os = null;
        java.io.InputStream is = null;
        try {
            if (porta <= 0) porta = 515;
            Log.d(TAG, "Conectando via LPR: " + ip + ":" + porta);

            socket = new Socket();
            socket.setSoTimeout(NETWORK_SO_TIMEOUT_MS);
            socket.connect(new InetSocketAddress(ip, porta), NETWORK_CONNECT_TIMEOUT_MS);

            os = socket.getOutputStream();
            is = socket.getInputStream();

            // LPR Protocol: enviar comando de impressao
            // Byte 0x02 = Receive a printer job
            String queueName = "lp";
            os.write(0x02);
            os.write((queueName + "\n").getBytes());
            os.flush();

            // Aguardar ACK
            int ack = is.read();
            if (ack != 0) {
                Log.e(TAG, "LPR: Servidor rejeitou o job (ACK=" + ack + ")");
                return false;
            }

            // Enviar subcomando: Receive data file
            String jobId = String.valueOf(System.currentTimeMillis() % 1000);
            String dataFileCmd = "\003" + dados.length + " dfA" + jobId + "android\n";
            os.write(dataFileCmd.getBytes());
            os.flush();

            ack = is.read();
            if (ack != 0) {
                Log.e(TAG, "LPR: Servidor rejeitou o arquivo de dados (ACK=" + ack + ")");
                return false;
            }

            // Enviar dados
            os.write(dados);
            os.write(0); // Fim do arquivo
            os.flush();

            ack = is.read();
            if (ack != 0) {
                Log.w(TAG, "LPR: ACK final nao recebido (ACK=" + ack + ")");
            }

            Log.i(TAG, "Impressao LPR concluida: " + ip + ":" + porta + " (" + dados.length + " bytes)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao imprimir via LPR: " + e.getMessage(), e);
            return false;
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (os != null) os.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Testa a conexao com a impressora via Rede RAW/LPR.
     */
    public boolean testarConexaoRedeRaw() {
        Socket socket = null;
        try {
            String ip = getRedeRawIp().trim();
            int porta = getRedeRawPorta();

            if (ip.isEmpty()) return false;

            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, porta), NETWORK_CONNECT_TIMEOUT_MS);

            boolean connected = socket.isConnected();
            Log.i(TAG, "Teste Rede RAW " + (connected ? "OK" : "FALHA") + ": " + ip + ":" + porta);
            return connected;
        } catch (Exception e) {
            Log.e(TAG, "Falha no teste Rede RAW", e);
            return false;
        } finally {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    // ==================== LISTAGEM DE DISPOSITIVOS BLUETOOTH PAREADOS ====================

    /**
     * Informacoes de um dispositivo Bluetooth pareado.
     */
    public static class BluetoothPrinterInfo {
        public String name;
        public String address;
        public int majorClass;
        public int deviceClass;
        public String description;
        public boolean isLikelyPrinter;

        public BluetoothPrinterInfo(String name, String address) {
            this.name = name != null ? name : "Dispositivo Desconhecido";
            this.address = address;
            this.description = "";
            this.isLikelyPrinter = false;
        }

        @Override
        public String toString() {
            return name + " [" + address + "]";
        }
    }

    /**
     * Lista TODOS os dispositivos Bluetooth pareados (emparelhados).
     * Identifica automaticamente quais sao provavelmente impressoras.
     * Nao requer scan/discovery - usa apenas dispositivos ja pareados.
     */
    public List<BluetoothPrinterInfo> listarDispositivosBtPareados() {
        List<BluetoothPrinterInfo> devices = new ArrayList<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return devices;

        try {
            java.util.Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            if (pairedDevices == null) return devices;

            for (BluetoothDevice device : pairedDevices) {
                String name = null;
                String address = null;
                int majorClass = 0;
                int deviceClassVal = 0;

                try { name = device.getName(); } catch (SecurityException e) { name = null; }
                try { address = device.getAddress(); } catch (Exception e) { address = "00:00:00:00:00:00"; }

                try {
                    if (device.getBluetoothClass() != null) {
                        majorClass = device.getBluetoothClass().getMajorDeviceClass();
                        deviceClassVal = device.getBluetoothClass().getDeviceClass();
                    }
                } catch (Exception ignored) {}

                BluetoothPrinterInfo info = new BluetoothPrinterInfo(name, address);
                info.majorClass = majorClass;
                info.deviceClass = deviceClassVal;

                // Classificar dispositivo
                info.isLikelyPrinter = isLikelyBluetoothPrinter(name, majorClass, deviceClassVal);

                if (info.isLikelyPrinter) {
                    info.description = "Impressora Bluetooth";
                } else {
                    info.description = classificarDispositivoBt(majorClass, deviceClassVal, name);
                }

                devices.add(info);
            }

            // Ordenar: impressoras primeiro, depois por nome
            java.util.Collections.sort(devices, (a, b) -> {
                if (a.isLikelyPrinter && !b.isLikelyPrinter) return -1;
                if (!a.isLikelyPrinter && b.isLikelyPrinter) return 1;
                return a.name.compareToIgnoreCase(b.name);
            });

        } catch (SecurityException e) {
            Log.e(TAG, "Permissao Bluetooth negada", e);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao listar dispositivos Bluetooth pareados", e);
        }
        return devices;
    }

    /**
     * Verifica se um dispositivo Bluetooth e provavelmente uma impressora.
     */
    private boolean isLikelyBluetoothPrinter(String name, int majorClass, int deviceClass) {
        // Major class 0x0600 = Imaging (inclui impressoras)
        if (majorClass == 0x0600) return true;

        // Verificar minor class para impressora dentro de Imaging
        // Minor class bits para impressora: bit 7 (0x80)
        if ((deviceClass & 0x0680) == 0x0680) return true;

        // Verificar pelo nome
        if (name != null) {
            String upper = name.toUpperCase();
            if (upper.contains("PRINTER") || upper.contains("PRINT")
                    || upper.contains("TM-") || upper.contains("MP-")
                    || upper.contains("RECEIPT") || upper.contains("THERMAL")
                    || upper.contains("POS") || upper.contains("EPSON")
                    || upper.contains("BEMATECH") || upper.contains("ELGIN")
                    || upper.contains("DARUMA") || upper.contains("TANCA")
                    || upper.contains("SWEDA") || upper.contains("STAR ")
                    || upper.contains("CITIZEN") || upper.contains("CUSTOM")
                    || upper.contains("GERTEC") || upper.contains("DIEBOLD")
                    || upper.contains("CONTROL ID") || upper.contains("PRINT ID")
                    || upper.contains("58MM") || upper.contains("80MM")
                    || upper.contains("TSP") || upper.contains("SP700")
                    || upper.contains("CT-") || upper.contains("MP421")
                    || upper.contains("I9") || upper.contains("L42")
                    || upper.contains("GENERIC") || upper.contains("TEXT ONLY")
                    || upper.contains("RPP") || upper.contains("SPP-")
                    || upper.contains("INNER") || upper.contains("LEOPARDO")
                    || upper.contains("DATECS") || upper.contains("RONGTA")
                    || upper.contains("XPRINTER") || upper.contains("GOOJPRT")
                    || upper.contains("MUNBYN") || upper.contains("HOIN")
                    || upper.contains("ZJIANG") || upper.contains("MILESTONE")
                    || upper.contains("SEWOO") || upper.contains("WOOSIM")
                    || upper.contains("BIXOLON") || upper.contains("ZEBRA")
                    || upper.contains("BROTHER") || upper.contains("FUJITSU")
                    || upper.contains("SEIKO") || upper.contains("CASHINO")
                    || upper.contains("GAINSCHA") || upper.contains("OCPP")
                    || upper.contains("NYEAR") || upper.contains("ISSYZONE")
                    || upper.contains("NETUM") || upper.contains("IPOSX")
                    || upper.contains("QSPRINTER") || upper.contains("MHT-")
                    || upper.contains("PT-") || upper.contains("BTP-")
                    || upper.contains("IMP")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Classifica um dispositivo Bluetooth pelo tipo.
     */
    private String classificarDispositivoBt(int majorClass, int deviceClass, String name) {
        switch (majorClass) {
            case 0x0100: return "Computador";
            case 0x0200: return "Telefone";
            case 0x0300: return "Ponto de Acesso";
            case 0x0400: return "Audio/Video";
            case 0x0500: return "Periferico";
            case 0x0600: return "Impressora/Imagem";
            case 0x0700: return "Wearable";
            case 0x0800: return "Brinquedo";
            case 0x0900: return "Saude";
            default: return "Dispositivo Bluetooth";
        }
    }

    // Utilidades de formatacao
    private String center(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = (width - text.length()) / 2;
        return repeat(" ", pad) + text;
    }

    private String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
