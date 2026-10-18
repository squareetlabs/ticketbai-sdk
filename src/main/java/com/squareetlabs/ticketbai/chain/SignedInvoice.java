package com.squareetlabs.ticketbai.chain;

import com.squareetlabs.ticketbai.identifier.TbaiIdentifier;

import java.time.LocalDate;

/** Datos de un fichero de alta TicketBAI ya firmado que hacen falta para encadenar y para el código TicketBAI. */
public final class SignedInvoice {

    private final String issuerNif;
    private final String series;
    private final String number;
    private final LocalDate issueDate;
    private final String signatureValue;
    private final ChainLink previousLink;

    SignedInvoice(String issuerNif, String series, String number, LocalDate issueDate, String signatureValue,
                  ChainLink previousLink) {
        this.issuerNif = issuerNif;
        this.series = series;
        this.number = number;
        this.issueDate = issueDate;
        this.signatureValue = signatureValue;
        this.previousLink = previousLink;
    }

    public String getIssuerNif() {
        return issuerNif;
    }

    public String getSeries() {
        return series;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    /** {@code SignatureValue} completo, sin espacios ni saltos de línea. */
    public String getSignatureValue() {
        return signatureValue;
    }

    /** Encadenamiento que lleva el fichero, o {@code null} si es la primera factura. */
    public ChainLink getPreviousLink() {
        return previousLink;
    }

    /** Eslabón que la siguiente factura tiene que llevar en {@code EncadenamientoFacturaAnterior}. */
    public ChainLink toChainLink() {
        return ChainLink.of(series, number, issueDate, signatureValue);
    }

    public TbaiIdentifier toTbaiIdentifier() {
        return TbaiIdentifier.of(issuerNif, issueDate, signatureValue);
    }
}
