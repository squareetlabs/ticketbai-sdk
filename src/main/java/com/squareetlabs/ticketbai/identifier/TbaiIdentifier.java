package com.squareetlabs.ticketbai.identifier;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Código TicketBAI (Anexo V): {@code TBAI-NNNNNNNNN-DDMMAA-FFFFFFFFFFFFF-CRC}, 39 caracteres.
 * <ul>
 *     <li>{@code NNNNNNNNN}: NIF del emisor (9 caracteres);</li>
 *     <li>{@code DDMMAA}: fecha de expedición de la factura;</li>
 *     <li>{@code FFFFFFFFFFFFF}: los 13 primeros caracteres del {@code SignatureValue} de la firma del fichero;</li>
 *     <li>{@code CRC}: CRC-8 de los 36 caracteres anteriores (incluido el último guion), en decimal con tres dígitos.</li>
 * </ul>
 */
public final class TbaiIdentifier {

    public static final int LENGTH = 39;
    private static final int NIF_LENGTH = 9;
    private static final int SIGNATURE_PREFIX_LENGTH = 13;
    private static final String PREFIX = "TBAI-";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("ddMMuu");

    private final String issuerNif;
    private final LocalDate issueDate;
    private final String signaturePrefix;
    private final String crc;
    private final String value;

    private TbaiIdentifier(String issuerNif, LocalDate issueDate, String signaturePrefix) {
        this.issuerNif = issuerNif;
        this.issueDate = issueDate;
        this.signaturePrefix = signaturePrefix;
        String withoutCrc = PREFIX + issuerNif + "-" + DATE.format(issueDate) + "-" + signaturePrefix + "-";
        this.crc = Crc8.compute(withoutCrc);
        this.value = withoutCrc + crc;
    }

    /**
     * Código de una factura.
     *
     * @param issuerNif      NIF del emisor, 9 caracteres
     * @param issueDate      fecha de expedición ({@code FechaExpedicionFactura})
     * @param signatureValue {@code SignatureValue} de la firma del fichero de alta (al menos 13 caracteres)
     */
    public static TbaiIdentifier of(String issuerNif, LocalDate issueDate, String signatureValue) {
        Objects.requireNonNull(issuerNif, "issuerNif");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(signatureValue, "signatureValue");
        if (issuerNif.length() != NIF_LENGTH) {
            throw new IllegalArgumentException("El NIF del emisor tiene que tener 9 caracteres: " + issuerNif);
        }
        String signature = signatureValue.replaceAll("\\s", "");
        if (signature.length() < SIGNATURE_PREFIX_LENGTH) {
            throw new IllegalArgumentException("El SignatureValue tiene menos de 13 caracteres");
        }
        return new TbaiIdentifier(issuerNif, issueDate, signature.substring(0, SIGNATURE_PREFIX_LENGTH));
    }

    /** Lee un código TicketBAI y comprueba su formato y su CRC. */
    public static TbaiIdentifier parse(String value) {
        Objects.requireNonNull(value, "value");
        String[] parts = value.split("-", -1);
        if (value.length() != LENGTH || parts.length != 5 || !"TBAI".equals(parts[0])) {
            throw new IllegalArgumentException("No es un código TicketBAI: " + value);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(parts[2], DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha no válida en el código TicketBAI: " + value, e);
        }
        TbaiIdentifier parsed = of(parts[1], date, parts[3]);
        if (!parsed.value.equals(value)) {
            throw new IllegalArgumentException("El CRC del código TicketBAI no coincide: " + value);
        }
        return parsed;
    }

    public String getIssuerNif() {
        return issuerNif;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public String getSignaturePrefix() {
        return signaturePrefix;
    }

    public String getCrc() {
        return crc;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TbaiIdentifier && ((TbaiIdentifier) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
