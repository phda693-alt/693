package com.pdv.app.utils;

import java.util.Random;

/**
 * Utilitario para geracao e validacao de codigos de barras EAN-13.
 *
 * Estrutura EAN-13:
 * - Digitos 1-3: Prefixo do pais (789 = Brasil)
 * - Digitos 4-7: Codigo da empresa (gerado aleatoriamente para uso interno)
 * - Digitos 8-12: Codigo do produto (gerado aleatoriamente)
 * - Digito 13: Digito verificador (calculado pelo algoritmo padrao EAN)
 *
 * O digito verificador e calculado conforme norma GS1:
 * 1. Soma dos digitos em posicoes impares (1,3,5,7,9,11) multiplicados por 1
 * 2. Soma dos digitos em posicoes pares (2,4,6,8,10,12) multiplicados por 3
 * 3. Digito verificador = (10 - (soma total % 10)) % 10
 */
public class EanUtils {

    private static final Random random = new Random();

    /**
     * Gera um codigo EAN-13 valido com prefixo Brasil (789).
     * Os 9 digitos intermediarios sao gerados aleatoriamente e o
     * 13o digito e o verificador calculado pelo algoritmo padrao.
     *
     * @return String com 13 digitos representando um EAN-13 valido
     */
    public static String gerarEan13() {
        // Prefixo Brasil = 789
        StringBuilder sb = new StringBuilder("789");

        // Gerar 9 digitos aleatorios (posicoes 4 a 12)
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }

        // Calcular digito verificador (posicao 13)
        int digitoVerificador = calcularDigitoVerificador(sb.toString());
        sb.append(digitoVerificador);

        return sb.toString();
    }

    /**
     * Calcula o digito verificador EAN-13 a partir dos 12 primeiros digitos.
     * Segue o algoritmo padrao GS1 (norma ISO/IEC 15420).
     *
     * @param dozeDigitos String contendo exatamente 12 digitos
     * @return O digito verificador (0-9)
     */
    public static int calcularDigitoVerificador(String dozeDigitos) {
        if (dozeDigitos == null || dozeDigitos.length() != 12) {
            throw new IllegalArgumentException("Sao necessarios exatamente 12 digitos para calcular o verificador EAN-13");
        }

        int soma = 0;
        for (int i = 0; i < 12; i++) {
            int digito = Character.getNumericValue(dozeDigitos.charAt(i));
            // Posicoes impares (indice 0,2,4,...) multiplicam por 1
            // Posicoes pares (indice 1,3,5,...) multiplicam por 3
            if (i % 2 == 0) {
                soma += digito;
            } else {
                soma += digito * 3;
            }
        }

        int resto = soma % 10;
        return (resto == 0) ? 0 : (10 - resto);
    }

    /**
     * Valida se um codigo EAN-13 e valido verificando o digito verificador.
     *
     * @param ean13 String com 13 digitos
     * @return true se o codigo for um EAN-13 valido
     */
    public static boolean validarEan13(String ean13) {
        if (ean13 == null || ean13.length() != 13) {
            return false;
        }

        // Verificar se todos sao digitos
        for (char c : ean13.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }

        // Calcular digito verificador esperado
        int digitoEsperado = calcularDigitoVerificador(ean13.substring(0, 12));
        int digitoAtual = Character.getNumericValue(ean13.charAt(12));

        return digitoEsperado == digitoAtual;
    }
}
