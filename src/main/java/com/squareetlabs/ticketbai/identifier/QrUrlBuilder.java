package com.squareetlabs.ticketbai.identifier;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.util.Amounts;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Contenido del QR de TicketBAI (Anexo V): {@code <url base>?id=<código>&s=<serie>&nf=<número>&i=<importe>&cr=<CRC-8>}.
 * Los valores van codificados como URL (UTF-8, el espacio como {@code %20}). El CRC-8 se calcula sobre la URL ya
 * codificada, sin el parámetro {@code cr} ni su {@code &}.
 */
public final class QrUrlBuilder {

    private QrUrlBuilder() {
    }

    /**
     * @param series      {@code SerieFactura}; {@code null} o vacía si la factura no tiene serie (el parámetro {@code s}
     *                    va vacío)
     * @param number      {@code NumFactura}
     * @param totalAmount {@code ImporteTotalFactura}: con IVA y sin descontar la retención, con el mismo valor que en el
     *                    fichero
     */
    public static String build(Territory territory, Environment environment, TbaiIdentifier identifier, String series,
                               String number, BigDecimal totalAmount) {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(number, "number");
        String withoutCrc = territory.getQrBaseUrl(environment)
                + "?id=" + encode(identifier.getValue())
                + "&s=" + encode(series == null ? "" : series)
                + "&nf=" + encode(number)
                + "&i=" + encode(Amounts.format(totalAmount));
        return withoutCrc + "&cr=" + Crc8.compute(withoutCrc);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
