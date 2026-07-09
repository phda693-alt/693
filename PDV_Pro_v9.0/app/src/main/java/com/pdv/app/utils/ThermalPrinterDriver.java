package com.pdv.app.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Biblioteca interna de perfis/drivers ESC/POS para impressoras termicas.
 *
 * Observacao honesta: Android nao instala drivers Windows dentro do APK. O que o
 * sistema precisa para imprimir em impressoras termicas e um perfil de comandos
 * ESC/POS adequado ao modelo/familia: codepage, charset, corte, QRCode e papel.
 * Esta classe entrega esses perfis dentro do proprio sistema e serve tanto para
 * a impressao unica comum quanto para a impressao em multiimpressoras.
 */
public final class ThermalPrinterDriver {
    public static final String DRIVER_AUTO = "AUTO_ESC_POS_BR";

    private static final List<Profile> PROFILES;

    static {
        ArrayList<Profile> p = new ArrayList<>();

        // Perfil universal: funciona na maioria das termicas chinesas/ESC-POS.
        add(p, DRIVER_AUTO, "AUTO / Generica ESC-POS Brasil", "CP850", 2, 7, true, "partial_feed", 48,
                "Perfil universal ESC/POS com acentos BR, QRCode e corte parcial.");

        // EPSON / compativeis TM.
        add(p, "EPSON_TM_T20", "Epson TM-T20 / TM-T20II / TM-T20III", "CP850", 2, 7, true, "partial_feed", 48, "Epson TM serie T20");
        add(p, "EPSON_TM_T88", "Epson TM-T88III / IV / V / VI / VII", "CP850", 2, 7, true, "partial_feed", 48, "Epson TM serie T88");
        add(p, "EPSON_TM_U220", "Epson TM-U220 / Matricial ESC-POS", "CP850", 2, 5, false, "partial_feed", 42, "Epson TM-U220 sem QR nativo em muitos modelos");
        add(p, "EPSON_TM_M30", "Epson TM-m30 / m30II / m50", "CP850", 2, 7, true, "partial_feed", 48, "Epson compactas modernas");
        add(p, "EPSON_TM_P20", "Epson TM-P20 / P60 / P80 portatil", "CP850", 2, 6, true, "partial_feed", 42, "Epson portatil");

        // Bematech / Elgin / Daruma / Diebold / Sweda.
        add(p, "BEMATECH_MP4200", "Bematech MP-4200 TH / TH FI", "CP860", 3, 6, true, "partial_feed", 48, "Bematech MP-4200 costuma trabalhar bem com CP860/Latin BR");
        add(p, "BEMATECH_MP4000", "Bematech MP-4000 / MP-2500", "CP860", 3, 5, true, "partial_feed", 48, "Bematech MP antigas");
        add(p, "ELGIN_I9", "Elgin i9 / i9 Full / i9 USB", "CP850", 2, 7, true, "partial_feed", 48, "Elgin i9 ESC/POS");
        add(p, "ELGIN_I7", "Elgin i7 / i8", "CP850", 2, 6, true, "partial_feed", 48, "Elgin i7/i8");
        add(p, "ELGIN_L42", "Elgin L42 / L42 Pro Etiqueta", "CP850", 2, 5, false, "none", 48, "Etiqueta Elgin; corte pode nao existir");
        add(p, "DARUMA_DR700", "Daruma DR700 / DR800 / DR1000", "CP850", 2, 6, true, "partial_feed", 48, "Daruma termica ESC/POS");
        add(p, "DIEBOLD_TSP143", "Diebold / Procomp Termica ESC-POS", "CP850", 2, 6, true, "partial_feed", 48, "Diebold/Procomp generica");
        add(p, "SWEDA_SI300", "Sweda SI-300 / SI-250 / Termica", "CP850", 2, 6, true, "partial_feed", 48, "Sweda ESC/POS");

        // Control ID / Gertec / Gerbo.
        add(p, "CONTROLID_PRINTID", "Control iD Print iD / Print iD Touch", "CP850", 2, 7, true, "partial_feed", 48, "Control iD ESC/POS");
        add(p, "GERTEC_G250", "Gertec G250 / G250W / G250E", "CP850", 2, 7, true, "partial_feed", 48, "Gertec G250");
        add(p, "GERTEC_G800", "Gertec G800 / G802 / GPOS700", "CP850", 2, 7, true, "partial_feed", 48, "Gertec linha G");
        add(p, "GERBO_GTP80", "Gerbo GTP-80 / GTP-58", "CP850", 2, 6, true, "partial_feed", 48, "Gerbo generica");

        // Star / Bixolon / Citizen / Seiko / SNBC / Rongta / Xprinter.
        add(p, "STAR_TSP100", "Star TSP100 / TSP143 / TSP650", "CP850", 2, 6, true, "partial_feed", 48, "Star em modo ESC/POS/Star Line");
        add(p, "BIXOLON_SRP350", "Bixolon SRP-350 / SRP-330 / SRP-352", "CP850", 2, 7, true, "partial_feed", 48, "Bixolon SRP");
        add(p, "BIXOLON_SPP_R200", "Bixolon SPP-R200 / R210 / R310 / R410", "CP850", 2, 6, true, "partial_feed", 42, "Bixolon portatil");
        add(p, "CITIZEN_CT_S310", "Citizen CT-S310 / CT-S4000 / CT-S601", "CP850", 2, 7, true, "partial_feed", 48, "Citizen CT-S");
        add(p, "SEIKO_RP_D10", "Seiko RP-D10 / RP-F10 / MP-B20", "CP850", 2, 6, true, "partial_feed", 48, "Seiko ESC/POS");
        add(p, "SNBC_BTP_R880", "SNBC BTP-R880 / BTP-S80 / BTP-L540", "CP850", 2, 7, true, "partial_feed", 48, "SNBC ESC/POS");
        add(p, "RONGTA_RP80", "Rongta RP80 / RP58 / RPP02N", "CP850", 2, 6, true, "partial_feed", 48, "Rongta ESC/POS");
        add(p, "XPRINTER_XP58", "Xprinter XP-58 / XP-58IIH / POS-58", "CP850", 2, 5, true, "partial_feed", 32, "Xprinter 58mm");
        add(p, "XPRINTER_XP80", "Xprinter XP-80 / XP-Q200 / XP-N160", "CP850", 2, 7, true, "partial_feed", 48, "Xprinter 80mm");

        // Genericas de mercado nacional e importadas.
        add(p, "POS58_GENERIC", "Generica POS-58 / Bluetooth 58mm", "CP850", 2, 5, true, "partial_feed", 32, "Generica 58mm Bluetooth/USB");
        add(p, "POS80_GENERIC", "Generica POS-80 / USB/Rede 80mm", "CP850", 2, 7, true, "partial_feed", 48, "Generica 80mm USB/Rede");
        add(p, "SUNMI_V1", "SUNMI V1 / V2 / T2 / T2s", "CP850", 2, 6, true, "partial_feed", 42, "SUNMI integrado/externo ESC/POS");
        add(p, "PAX_A920", "PAX A920 / A930 / A910", "CP850", 2, 5, true, "partial_feed", 32, "POS Android PAX com termica integrada");
        add(p, "NEWLAND_N910", "Newland N910 / N950 / SmartPOS", "CP850", 2, 5, true, "partial_feed", 32, "SmartPOS Newland");
        add(p, "CLOVER_MINI", "Clover Mini / Flex / Station", "CP850", 2, 5, true, "partial_feed", 42, "Clover ESC/POS quando habilitado");
        add(p, "ZJ_5802", "Zjiang ZJ-5802 / ZJ-5890 / ZJ-8001", "CP850", 2, 5, true, "partial_feed", 32, "Zjiang/ZJ 58/80mm");
        add(p, "GPRINTER_GP58", "Gprinter GP-58 / GP-80 / GP-C80180", "CP850", 2, 6, true, "partial_feed", 48, "Gprinter ESC/POS");
        add(p, "HOIN_HOP58", "HOIN HOP-H58 / HOP-E801", "CP850", 2, 5, true, "partial_feed", 32, "HOIN ESC/POS");
        add(p, "MUNBYN_ITPP", "MUNBYN ITPP047 / ITPP068 / ITPP941", "CP850", 2, 6, true, "partial_feed", 48, "Munbyn termica/etiqueta");
        add(p, "NETUM_NT", "NETUM NT-1809 / NT-5890K", "CP850", 2, 5, true, "partial_feed", 32, "NETUM generica");
        add(p, "GOOJPRT_PT", "Goojprt PT-210 / MTP-II / Peripage", "CP850", 2, 5, true, "partial_feed", 32, "Goojprt portatil");
        add(p, "NIIMBOT_LABEL", "Niimbot / Etiqueta bluetooth", "CP850", 2, 4, false, "none", 32, "Etiqueta: pode exigir protocolo proprietario");
        add(p, "TSC_TE244", "TSC TE244 / TTP-244 / DA210 etiqueta", "CP850", 2, 4, false, "none", 48, "Etiqueta; usar modo compatibilidade se suportado");
        add(p, "ZEBRA_GC420", "Zebra GC420 / GK420 / ZD220 / ZD230", "CP850", 2, 4, false, "none", 48, "Zebra usa EPL/ZPL; ESC/POS so em modo compativel");
        add(p, "ARGOX_OS214", "Argox OS-214 / CP-2140 etiqueta", "CP850", 2, 4, false, "none", 48, "Argox etiqueta; requer emulacao compatibilidade");

        // Codepage alternatives para quando acentos saem errados.
        add(p, "ALT_CP437", "Alternativo CP437 USA", "CP437", 0, 6, true, "partial_feed", 48, "Use se a impressora estiver em pagina USA");
        add(p, "ALT_CP850", "Alternativo CP850 Multilingual", "CP850", 2, 6, true, "partial_feed", 48, "Melhor opcao BR na maioria das ESC/POS");
        add(p, "ALT_CP860", "Alternativo CP860 Portugues", "CP860", 3, 6, true, "partial_feed", 48, "Use em Bematech/firmwares PT");
        add(p, "ALT_CP858", "Alternativo CP858 Euro/Latin", "CP858", 19, 6, true, "partial_feed", 48, "Algumas impressoras usam CP858");
        add(p, "ALT_WIN1252", "Alternativo Windows-1252", "Windows-1252", 16, 6, true, "partial_feed", 48, "Use para drivers Windows/SMB que esperam ANSI");
        add(p, "ALT_ISO88591", "Alternativo ISO-8859-1 Latin1", "ISO-8859-1", 6, 6, true, "partial_feed", 48, "Use para firmware Latin1");
        add(p, "ALT_ASCII_SAFE", "Alternativo ASCII sem acentos", "US-ASCII", 0, 6, true, "partial_feed", 48, "Ultimo recurso: evita simbolos estranhos removendo acentos");

        PROFILES = Collections.unmodifiableList(p);
    }

    private ThermalPrinterDriver() {}

    private static void add(List<Profile> list, String id, String name, String charset, int codePage, int qrSize,
                            boolean qr, String cutMode, int columns80, String notes) {
        list.add(new Profile(id, name, charset, codePage, qrSize, qr, cutMode, columns80, notes));
    }

    public static List<Profile> getProfiles() { return PROFILES; }

    public static Profile getProfile(String id) {
        if (id != null) {
            for (Profile p : PROFILES) if (p.id.equals(id)) return p;
        }
        return PROFILES.get(0);
    }

    public static int findPosition(String id) {
        for (int i = 0; i < PROFILES.size(); i++) if (PROFILES.get(i).id.equals(id)) return i;
        return 0;
    }

    public static String[] getDisplayNames() {
        String[] out = new String[PROFILES.size()];
        for (int i = 0; i < PROFILES.size(); i++) out[i] = PROFILES.get(i).name;
        return out;
    }

    public static String[] getIds() {
        String[] out = new String[PROFILES.size()];
        for (int i = 0; i < PROFILES.size(); i++) out[i] = PROFILES.get(i).id;
        return out;
    }

    public static final class Profile {
        public final String id;
        public final String name;
        public final String charset;
        public final int codePage;
        public final int qrModuleSize;
        public final boolean qrSupported;
        public final String cutMode;
        public final int columns80;
        public final String notes;

        private Profile(String id, String name, String charset, int codePage, int qrModuleSize,
                        boolean qrSupported, String cutMode, int columns80, String notes) {
            this.id = id;
            this.name = name;
            this.charset = charset;
            this.codePage = codePage;
            this.qrModuleSize = qrModuleSize;
            this.qrSupported = qrSupported;
            this.cutMode = cutMode;
            this.columns80 = columns80;
            this.notes = notes;
        }

        public byte[] codePageCommand() {
            return new byte[]{0x1B, 0x74, (byte) codePage};
        }

        public byte[] cutCommand() {
            if ("full".equalsIgnoreCase(cutMode)) return new byte[]{0x1D, 0x56, 0x00};
            if ("partial".equalsIgnoreCase(cutMode)) return new byte[]{0x1D, 0x56, 0x01};
            if ("full_feed".equalsIgnoreCase(cutMode)) return new byte[]{0x1D, 0x56, 0x41, 0x03};
            if ("none".equalsIgnoreCase(cutMode)) return new byte[]{0x0A, 0x0A};
            return new byte[]{0x1D, 0x56, 0x42, 0x01};
        }

        @Override public String toString() { return name; }
    }
}
