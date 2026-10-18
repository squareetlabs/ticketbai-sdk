package com.squareetlabs.ticketbai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmountsTest {

    @Test
    @DisplayName("Dos decimales con punto, sin notación científica ni -0.00")
    void format() {
        assertEquals("1210.00", Amounts.format(new BigDecimal("1210")));
        assertEquals("1542.75", Amounts.format(new BigDecimal("1542.750")));
        assertEquals("-12.10", Amounts.format(new BigDecimal("-12.1")));
        assertEquals("0.00", Amounts.format(new BigDecimal("-0.00")));
        assertEquals("1000000.00", Amounts.format(new BigDecimal("1E+6")));
    }

    @Test
    @DisplayName("Rechaza más de dos decimales significativos en lugar de redondear")
    void rejectsMoreDecimals() {
        assertThrows(IllegalArgumentException.class, () -> Amounts.format(new BigDecimal("10.005")));
    }
}
