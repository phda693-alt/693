package com.pdv.app.validation;

import java.util.regex.Pattern;

/**
 * Validador de entradas comum no PDV Pro brasileiro.
 *
 * <p>Centraliza regras de validacao de CPF, CNPJ, email, telefone,
 * CEP e datas. Todas as funcoes sao puras (sem efeitos colaterais),
 * thread-safe e prontas para uso em qualquer Activity.</p>
 *
 * <p>Esta classe e adicional e nao substitui nenhuma logica
 * existente. Pode ser ignorada com seguranca por qualquer codigo
 * legado que nao queira valida-la aqui.</p>
 */
public final class InputValidator {

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PHONE_REGEX = Pattern.compile(
            "^\\(?\\d{2}\\)?[\\s-]?9?\\d{4}[-\\s]?\\d{4}$");

    private static final Pattern CEP_REGEX = Pattern.compile(
            "^\\d{5}-?\\d{3}$");

    private static final Pattern DATE_BR_REGEX = Pattern.compile(
            "^([0-2]\\d|3[01])/(0\\d|1[0-2])/\\d{4}$");

    private InputValidator() { /* utility */ }

    /** Valida endereco de email com regex robusta. */
    public static boolean isEmail(String s) {
        return s != null && EMAIL_REGEX.matcher(s.trim()).matches();
    }

    /** Valida telefone brasileiro (com ou sem DDD em parenteses). */
    public static boolean isPhoneBR(String s) {
        return s != null && PHONE_REGEX.matcher(s.trim()).matches();
    }

    /** Valida CEP brasileiro (com ou sem hifen). */
    public static boolean isCepBR(String s) {
        return s != null && CEP_REGEX.matcher(s.trim()).matches();
    }

    /** Valida data brasileira no formato dd/MM/aaaa. */
    public static boolean isDateBR(String s) {
        return s != null && DATE_BR_REGEX.matcher(s.trim()).matches();
    }

    /** Valida CPF (com ou sem mascara) usando algoritmo oficial. */
    public static boolean isCpf(String input) {
        if (input == null) return false;
        String cpf = input.replaceAll("\\D", "");
        if (cpf.length() != 11) return false;
        // Rejeita sequencias repetidas (000.000.000-00 etc)
        boolean allEqual = true;
        for (int i = 1; i < cpf.length(); i++) {
            if (cpf.charAt(i) != cpf.charAt(0)) { allEqual = false; break; }
        }
        if (allEqual) return false;

        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                sum += Character.digit(cpf.charAt(i), 10) * (10 - i);
            }
            int d1 = 11 - (sum % 11);
            if (d1 >= 10) d1 = 0;
            if (d1 != Character.digit(cpf.charAt(9), 10)) return false;

            sum = 0;
            for (int i = 0; i < 10; i++) {
                sum += Character.digit(cpf.charAt(i), 10) * (11 - i);
            }
            int d2 = 11 - (sum % 11);
            if (d2 >= 10) d2 = 0;
            return d2 == Character.digit(cpf.charAt(10), 10);
        } catch (Exception e) {
            return false;
        }
    }

    /** Valida CNPJ (com ou sem mascara) usando algoritmo oficial. */
    public static boolean isCnpj(String input) {
        if (input == null) return false;
        String cnpj = input.replaceAll("\\D", "");
        if (cnpj.length() != 14) return false;
        boolean allEqual = true;
        for (int i = 1; i < cnpj.length(); i++) {
            if (cnpj.charAt(i) != cnpj.charAt(0)) { allEqual = false; break; }
        }
        if (allEqual) return false;

        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        try {
            int sum = 0;
            for (int i = 0; i < 12; i++) {
                sum += Character.digit(cnpj.charAt(i), 10) * w1[i];
            }
            int d1 = sum % 11;
            d1 = d1 < 2 ? 0 : 11 - d1;
            if (d1 != Character.digit(cnpj.charAt(12), 10)) return false;

            sum = 0;
            for (int i = 0; i < 13; i++) {
                sum += Character.digit(cnpj.charAt(i), 10) * w2[i];
            }
            int d2 = sum % 11;
            d2 = d2 < 2 ? 0 : 11 - d2;
            return d2 == Character.digit(cnpj.charAt(13), 10);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Calcula um indicador (0 a 4) da forca de uma senha:
     * <ul>
     *   <li>0: muito fraca (vazia / muito curta)</li>
     *   <li>1: fraca (apenas digitos curtos)</li>
     *   <li>2: media (mistura de letras e digitos)</li>
     *   <li>3: forte (letras maiusc/minusc + digitos)</li>
     *   <li>4: muito forte (acima + caracter especial e &gt;= 12 chars)</li>
     * </ul>
     */
    public static int passwordStrength(String s) {
        if (s == null || s.length() < 4) return 0;
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        int score = 0;
        if (s.length() >= 6) score++;
        if (s.length() >= 10 && (hasLower || hasUpper) && hasDigit) score++;
        if (hasLower && hasUpper && hasDigit) score++;
        if (s.length() >= 12 && hasSpecial && hasLower && hasUpper && hasDigit) score++;
        return Math.min(score, 4);
    }
}
