package com.squareetlabs.ticketbai.chain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Eslabón de la cadena ({@code EncadenamientoFacturaAnterior}): serie, número y fecha de expedición de la factura
 * anterior y los 100 primeros caracteres de su {@code SignatureValue}. No es un hash, como en VERI*FACTU: es un trozo de
 * la firma anterior.
 */
public final class ChainLink {

    public static final int SIGNATURE_PREFIX_LENGTH = 100;

    private final String series;
    private final String number;
    private final LocalDate issueDate;
    private final String signatureValuePrefix;

    private ChainLink(String series, String number, LocalDate issueDate, String signatureValuePrefix) {
        this.series = series == null || series.isEmpty() ? null : series;
        this.number = Objects.requireNonNull(number, "number");
        this.issueDate = Objects.requireNonNull(issueDate, "issueDate");
        this.signatureValuePrefix = signatureValuePrefix;
    }

    /**
     * Eslabón de una factura a partir de su {@code SignatureValue} completo (se quitan los espacios y saltos de línea y se
     * toman los 100 primeros caracteres).
     */
    public static ChainLink of(String series, String number, LocalDate issueDate, String signatureValue) {
        Objects.requireNonNull(signatureValue, "signatureValue");
        String signature = signatureValue.replaceAll("\\s", "");
        if (signature.length() < SIGNATURE_PREFIX_LENGTH) {
            throw new IllegalArgumentException("El SignatureValue tiene menos de 100 caracteres");
        }
        return new ChainLink(series, number, issueDate, signature.substring(0, SIGNATURE_PREFIX_LENGTH));
    }

    /** Eslabón tal como viene en {@code EncadenamientoFacturaAnterior} (el prefijo de firma ya recortado). */
    public static ChainLink ofPrefix(String series, String number, LocalDate issueDate, String signatureValuePrefix) {
        Objects.requireNonNull(signatureValuePrefix, "signatureValuePrefix");
        if (signatureValuePrefix.length() > SIGNATURE_PREFIX_LENGTH) {
            throw new IllegalArgumentException("El prefijo de firma tiene más de 100 caracteres");
        }
        return new ChainLink(series, number, issueDate, signatureValuePrefix);
    }

    /** {@code SerieFacturaAnterior}, o {@code null} si la factura anterior no tiene serie. */
    public String getSeries() {
        return series;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public String getSignatureValuePrefix() {
        return signatureValuePrefix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChainLink)) {
            return false;
        }
        ChainLink other = (ChainLink) o;
        return Objects.equals(series, other.series) && number.equals(other.number) && issueDate.equals(other.issueDate)
                && signatureValuePrefix.equals(other.signatureValuePrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(series, number, issueDate, signatureValuePrefix);
    }

    @Override
    public String toString() {
        return (series == null ? "" : series + "/") + number + " " + issueDate;
    }
}
