package com.squareetlabs.ticketbai.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Anulación ({@code AnulaTicketBai}) de una factura ya emitida. Se identifica por el NIF del emisor, la serie, el número
 * y la fecha de expedición, que tienen que coincidir <b>exactamente</b> con los del fichero de alta. No lleva
 * encadenamiento.
 */
public final class Annulment {

    private final Party issuer;
    private final InvoiceReference invoice;

    public Annulment(String issuerNif, String issuerName, String series, String number, LocalDate issueDate) {
        this.issuer = Party.withNif(issuerNif, Objects.requireNonNull(issuerName, "issuerName"));
        this.invoice = new InvoiceReference(series, number, issueDate);
    }

    /** Anulación de una factura emitida con este SDK. */
    public static Annulment of(Invoice invoice) {
        return new Annulment(invoice.getIssuer().getNif(), invoice.getIssuer().getName(), invoice.getSeries(),
                invoice.getNumber(), invoice.getIssueDate());
    }

    public Party getIssuer() {
        return issuer;
    }

    public InvoiceReference getInvoice() {
        return invoice;
    }
}
