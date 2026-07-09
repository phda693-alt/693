package com.pdv.app.utils;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * PDV Pro v9.0 - Utilitários de formatação avançados.
 *
 * Centraliza todas as formatações de valores monetários, datas, quantidades,
 * CPF/CNPJ, telefone, CEP e outras strings do sistema.
 */
public class FormatUtils {

    private static final Locale BR = new Locale("pt", "BR");
    private static final DecimalFormatSymbols SYMBOLS;
    private static final DecimalFormat MONEY_FORMAT;
    private static final DecimalFormat QTD_FORMAT;
    private static final DecimalFormat PERCENT_FORMAT;

    static {
        SYMBOLS = new DecimalFormatSymbols(Locale.US);
        SYMBOLS.setDecimalSeparator('.');
        SYMBOLS.setGroupingSeparator(',');
        MONEY_FORMAT = new DecimalFormat("#,##0.00", SYMBOLS);
        QTD_FORMAT = new DecimalFormat("#,##0.###", SYMBOLS);
        PERCENT_FORMAT = new DecimalFormat("##0.00", SYMBOLS);
    }

    // === MOEDA ===

    public static String formatMoney(double value) {
        return MONEY_FORMAT.format(value);
    }

    public static String formatMoneyWithSymbol(double value) {
        return "R$ " + MONEY_FORMAT.format(value);
    }

    public static String formatMoneyCompact(double value) {
        if (value >= 1_000_000) {
            return String.format(BR, "R$ %.1fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format(BR, "R$ %.1fK", value / 1_000);
        }
        return "R$ " + MONEY_FORMAT.format(value);
    }

    public static double parseMoney(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            String clean = text.replace("R$", "").replace(" ", "").trim();
            clean = clean.replace(",", "");
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    public static double parseMoneyBR(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            String clean = text.replace("R$", "").replace(" ", "").trim();
            // Formato BR: 1.234,56
            clean = clean.replace(".", "").replace(",", ".");
            return Double.parseDouble(clean);
        } catch (Exception e) {
            return 0;
        }
    }

    // === QUANTIDADE ===

    public static String formatQuantidade(double value) {
        return QTD_FORMAT.format(value);
    }

    public static String formatQuantidadeWithUnit(double value, String unit) {
        return QTD_FORMAT.format(value) + " " + (unit != null ? unit : "un");
    }

    // === PERCENTUAL ===

    public static String formatPercent(double value) {
        return PERCENT_FORMAT.format(value) + "%";
    }

    public static double parsePercent(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(text.replace("%", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // === DATA E HORA ===

    public static String formatDate(String mysqlDate) {
        if (mysqlDate == null || mysqlDate.isEmpty()) return "";
        try {
            SimpleDateFormat from = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", BR);
            SimpleDateFormat to = new SimpleDateFormat("dd/MM/yyyy HH:mm", BR);
            Date d = from.parse(mysqlDate);
            return to.format(d);
        } catch (Exception e) {
            return mysqlDate;
        }
    }

    public static String formatDateOnly(String mysqlDate) {
        if (mysqlDate == null || mysqlDate.isEmpty()) return "";
        try {
            SimpleDateFormat from = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", BR);
            SimpleDateFormat to = new SimpleDateFormat("dd/MM/yyyy", BR);
            Date d = from.parse(mysqlDate);
            return to.format(d);
        } catch (Exception e) {
            // Tentar formato sem hora
            try {
                SimpleDateFormat from2 = new SimpleDateFormat("yyyy-MM-dd", BR);
                SimpleDateFormat to = new SimpleDateFormat("dd/MM/yyyy", BR);
                Date d = from2.parse(mysqlDate);
                return to.format(d);
            } catch (Exception e2) {
                return mysqlDate;
            }
        }
    }

    public static String formatDateShort(String mysqlDate) {
        if (mysqlDate == null || mysqlDate.isEmpty()) return "";
        try {
            SimpleDateFormat from = new SimpleDateFormat("yyyy-MM-dd", BR);
            SimpleDateFormat to = new SimpleDateFormat("dd/MM", BR);
            Date d = from.parse(mysqlDate);
            return to.format(d);
        } catch (Exception e) {
            return mysqlDate;
        }
    }

    public static String getCurrentDateTime() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", BR).format(new Date());
    }

    public static String getCurrentDate() {
        return new SimpleDateFormat("dd/MM/yyyy", BR).format(new Date());
    }

    public static String getCurrentDateMysql() {
        return new SimpleDateFormat("yyyy-MM-dd", BR).format(new Date());
    }

    public static String getCurrentDateTimeForFile() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", BR).format(new Date());
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss", BR).format(new Date());
    }

    public static String formatTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", BR);
            return sdf.format(timestamp);
        } catch (Exception e) {
            return "";
        }
    }

    public static String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", BR);
            return sdf.format(timestamp);
        } catch (Exception e) {
            return "";
        }
    }

    public static String getRelativeTime(String mysqlDate) {
        if (mysqlDate == null || mysqlDate.isEmpty()) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", BR);
            Date date = sdf.parse(mysqlDate);
            long diff = System.currentTimeMillis() - date.getTime();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            if (minutes < 1) return "agora";
            if (minutes < 60) return minutes + " min atrás";
            if (hours < 24) return hours + "h atrás";
            if (days < 7) return days + "d atrás";
            return formatDateOnly(mysqlDate);
        } catch (Exception e) {
            return mysqlDate;
        }
    }

    // === CPF / CNPJ ===

    public static String formatCpfCnpj(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return digits.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        } else if (digits.length() == 14) {
            return digits.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return value;
    }

    public static boolean isValidCpf(String cpf) {
        if (cpf == null) return false;
        String digits = cpf.replaceAll("[^0-9]", "");
        if (digits.length() != 11) return false;
        if (digits.matches("(\\d)\\1{10}")) return false;
        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) sum += (digits.charAt(i) - '0') * (10 - i);
            int r1 = 11 - (sum % 11);
            if (r1 >= 10) r1 = 0;
            if (r1 != (digits.charAt(9) - '0')) return false;
            sum = 0;
            for (int i = 0; i < 10; i++) sum += (digits.charAt(i) - '0') * (11 - i);
            int r2 = 11 - (sum % 11);
            if (r2 >= 10) r2 = 0;
            return r2 == (digits.charAt(10) - '0');
        } catch (Exception e) {
            return false;
        }
    }

    // === TELEFONE ===

    public static String formatTelefone(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return digits.replaceAll("(\\d{2})(\\d{5})(\\d{4})", "($1) $2-$3");
        } else if (digits.length() == 10) {
            return digits.replaceAll("(\\d{2})(\\d{4})(\\d{4})", "($1) $2-$3");
        }
        return value;
    }

    // === CEP ===

    public static String formatCep(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 8) {
            return digits.replaceAll("(\\d{5})(\\d{3})", "$1-$2");
        }
        return value;
    }

    // === STRINGS ===

    public static String safeString(String s) {
        return s != null ? s : "";
    }

    public static String safeString(String s, String defaultValue) {
        return (s != null && !s.trim().isEmpty()) ? s : defaultValue;
    }

    public static String truncate(String s, int maxLength) {
        if (s == null) return "";
        if (s.length() <= maxLength) return s;
        return s.substring(0, maxLength - 3) + "...";
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    public static String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        String[] words = s.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    // === TEMPO DECORRIDO ===

    public static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) return String.format(BR, "%02d:%02d:%02d", hours, minutes, seconds);
        return String.format(BR, "%02d:%02d", minutes, seconds);
    }

    // === NÚMERO ===

    public static String formatInt(int value) {
        DecimalFormat df = new DecimalFormat("#,##0", new DecimalFormatSymbols(BR));
        return df.format(value);
    }

    public static int parseInt(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            return Double.parseDouble(s.replace(",", ".").replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
