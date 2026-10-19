package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.chain.ChainLink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El último eslabón de una página del LROE. La fecha y la hora de expedición son locales y sin huso: en la hora repetida
 * del cambio de hora (último domingo de octubre, de 02:00 a 02:59 dos veces) una factura de la primera pasada puede
 * tener una hora posterior a la de una factura emitida después. Manda el encadenamiento, no la hora.
 */
class LroeLatestTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 25);

    private static String signature(char c) {
        return String.valueOf(c).repeat(ChainLink.SIGNATURE_PREFIX_LENGTH);
    }

    private static LroeQueryResult.Invoice invoice(String number, LocalTime time, char signature, LroeQueryResult.Invoice previous) {
        return new LroeQueryResult.Invoice("A", number, DAY, time, new BigDecimal("121.00"), signature(signature),
                previous == null ? null : previous.toChainLink(), "Correcto", null, null, "25-10-2026 03:15:00");
    }

    private static LroeQueryResult page(List<LroeQueryResult.Invoice> invoices) {
        return new LroeQueryResult("25-10-2026 04:00:00", 1, true, 1, false, invoices.size(), invoices, new byte[0]);
    }

    @Test
    @DisplayName("Hora repetida: la factura de las 02:10 (+01:00) encadena la de las 02:50 (+02:00) y es la última")
    void repeatedHourFollowsTheChain() {
        LroeQueryResult.Invoice firstPass = invoice("0001", LocalTime.of(2, 50), 'a', null);
        LroeQueryResult.Invoice secondPass = invoice("0002", LocalTime.of(2, 10), 'b', firstPass);

        assertEquals("0002", page(List.of(firstPass, secondPass)).latest().orElseThrow().getNumber());
        assertEquals("0002", page(List.of(secondPass, firstPass)).latest().orElseThrow().getNumber());
    }

    @Test
    @DisplayName("Hora repetida: con la misma hora local en las dos pasadas, la última es la que nadie encadena")
    void sameLocalTimeInBothPasses() {
        LroeQueryResult.Invoice firstPass = invoice("0001", LocalTime.of(2, 30), 'a', null);
        LroeQueryResult.Invoice secondPass = invoice("0002", LocalTime.of(2, 30), 'b', firstPass);

        assertEquals("0002", page(List.of(secondPass, firstPass)).latest().orElseThrow().getNumber());
        assertEquals("0002", page(List.of(firstPass, secondPass)).latest().orElseThrow().getNumber());
    }

    @Test
    @DisplayName("Una factura sin firma en la respuesta no impide elegir el último eslabón")
    void invoiceWithoutSignature() {
        LroeQueryResult.Invoice first = invoice("0001", LocalTime.of(9, 0), 'a', null);
        LroeQueryResult.Invoice second = invoice("0002", LocalTime.of(10, 0), 'b', first);
        LroeQueryResult.Invoice unsigned = new LroeQueryResult.Invoice("A", "0003", DAY, LocalTime.of(8, 0),
                new BigDecimal("121.00"), null, null, "Correcto", null, null, "25-10-2026 03:15:00");

        assertEquals("0002", page(List.of(unsigned, first, second)).latest().orElseThrow().getNumber());
    }

    @Test
    @DisplayName("Sin encadenamiento entre las facturas de la página, la más reciente por fecha y hora")
    void unchainedPageUsesDateAndTime() {
        LroeQueryResult.Invoice early = invoice("0001", LocalTime.of(9, 0), 'a', null);
        LroeQueryResult.Invoice late = invoice("0002", LocalTime.of(10, 0), 'b', null);

        assertEquals("0002", page(List.of(late, early)).latest().orElseThrow().getNumber());
    }

    @Test
    @DisplayName("Una página vacía no tiene último eslabón")
    void emptyPage() {
        assertTrue(page(List.of()).latest().isEmpty());
    }
}
