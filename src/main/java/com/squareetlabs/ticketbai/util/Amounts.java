package com.squareetlabs.ticketbai.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Formato de importes de TicketBAI: punto decimal y dos decimales, sin separador de miles ni notación científica. */
public final class Amounts {

    private Amounts() {
    }

    /**
     * Importe con dos decimales ({@code 1210} → {@code "1210.00"}). Rechaza un importe con más de dos decimales
     * significativos: el valor tiene que coincidir con el del fichero, y redondearlo aquí lo ocultaría.
     */
    public static String format(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        try {
            BigDecimal scaled = amount.setScale(2, RoundingMode.UNNECESSARY);
            return scaled.signum() == 0 ? "0.00" : scaled.toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("El importe tiene más de dos decimales: " + amount.toPlainString(), e);
        }
    }
}
