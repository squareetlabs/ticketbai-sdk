package com.squareetlabs.ticketbai.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Formato de importes de TicketBAI: punto decimal, sin separador de miles ni notación científica. */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Importe con dos decimales ({@code 1210} → {@code "1210.00"}). Rechaza un importe con más de dos decimales
     * significativos: el valor tiene que coincidir con el del fichero, y redondearlo aquí lo ocultaría.
     */
    public static String format(BigDecimal amount) {
        return format(amount, 2);
    }

    /** Importe de las líneas de detalle ({@code ImporteSgn12.8Type}): ocho decimales. */
    public static String format8(BigDecimal amount) {
        return format(amount, 8);
    }

    private static String format(BigDecimal amount, int scale) {
        Objects.requireNonNull(amount, "amount");
        try {
            BigDecimal scaled = amount.setScale(scale, RoundingMode.UNNECESSARY);
            return scaled.signum() == 0 ? BigDecimal.ZERO.setScale(scale).toPlainString() : scaled.toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("El importe tiene más de " + scale + " decimales: " + amount.toPlainString(), e);
        }
    }
}
