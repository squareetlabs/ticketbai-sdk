package com.squareetlabs.ticketbai.identifier;

import java.nio.charset.StandardCharsets;

/**
 * CRC-8 de TicketBAI (Anexo V de las tres Diputaciones): polinomio x^8 + x^2 + x + 1 (0x07), valor inicial 0, sin
 * reflexión y sin XOR final, sobre los bytes UTF-8 de la cadena. El resultado se escribe en decimal con tres dígitos.
 */
public final class Crc8 {

    private static final int POLYNOMIAL = 0x07;
    private static final int[] TABLE = buildTable();

    private Crc8() {
    }

    /** CRC-8 de {@code text} en decimal con tres dígitos ({@code "000"}–{@code "255"}). */
    public static String compute(String text) {
        int crc = 0;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            crc = TABLE[(crc ^ b) & 0xFF];
        }
        return String.format("%03d", crc);
    }

    private static int[] buildTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ POLYNOMIAL) & 0xFF : (crc << 1) & 0xFF;
            }
            table[i] = crc;
        }
        return table;
    }
}
