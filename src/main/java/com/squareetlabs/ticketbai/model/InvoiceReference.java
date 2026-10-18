package com.squareetlabs.ticketbai.model;

import java.time.LocalDate;
import java.util.Objects;

/** Factura identificada por serie (opcional), número y fecha de expedición: la rectificada o sustituida. */
public final class InvoiceReference {

    private final String series;
    private final String number;
    private final LocalDate issueDate;

    public InvoiceReference(String series, String number, LocalDate issueDate) {
        this.series = series;
        this.number = Objects.requireNonNull(number, "number");
        this.issueDate = Objects.requireNonNull(issueDate, "issueDate");
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
}
