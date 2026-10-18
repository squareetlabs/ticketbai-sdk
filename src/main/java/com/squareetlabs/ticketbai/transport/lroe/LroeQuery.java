package com.squareetlabs.ticketbai.transport.lroe;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;

/**
 * Consulta ({@code C00}) de las anotaciones del subcapítulo 1.1 de un obligado y un ejercicio, por páginas, con filtros
 * opcionales: NIF del destinatario, serie, número, rango de fechas de expedición y estado.
 */
public final class LroeQuery {

    /** {@code EstadoRegistroConsulta}. */
    public enum RecordStatus {
        CORRECT("Correcto"), ACCEPTED_WITH_ERRORS("AceptadoConErrores"), ANNULLED("Anulado");

        private final String code;

        RecordStatus(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final LroeModel model;
    private final LroeTaxpayer taxpayer;
    private final Year fiscalYear;
    private int page = 1;
    private String recipientNif;
    private String series;
    private String number;
    private LocalDate issuedFrom;
    private LocalDate issuedTo;
    private RecordStatus status;

    private LroeQuery(LroeModel model, LroeTaxpayer taxpayer, Year fiscalYear) {
        this.model = Objects.requireNonNull(model, "model");
        this.taxpayer = Objects.requireNonNull(taxpayer, "taxpayer");
        this.fiscalYear = Objects.requireNonNull(fiscalYear, "fiscalYear");
    }

    public static LroeQuery of(LroeModel model, LroeTaxpayer taxpayer, Year fiscalYear) {
        return new LroeQuery(model, taxpayer, fiscalYear);
    }

    /** Página (desde 1). */
    public LroeQuery page(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("La página empieza en 1");
        }
        this.page = page;
        return this;
    }

    public LroeQuery recipientNif(String recipientNif) {
        this.recipientNif = recipientNif;
        return this;
    }

    public LroeQuery series(String series) {
        this.series = series;
        return this;
    }

    public LroeQuery number(String number) {
        this.number = number;
        return this;
    }

    /** Rango de fechas de expedición (cualquiera de los dos extremos puede ser {@code null}). */
    public LroeQuery issuedBetween(LocalDate from, LocalDate to) {
        this.issuedFrom = from;
        this.issuedTo = to;
        return this;
    }

    public LroeQuery status(RecordStatus status) {
        this.status = status;
        return this;
    }

    public LroeModel getModel() {
        return model;
    }

    public LroeTaxpayer getTaxpayer() {
        return taxpayer;
    }

    public Year getFiscalYear() {
        return fiscalYear;
    }

    public int getPage() {
        return page;
    }

    public String getRecipientNif() {
        return recipientNif;
    }

    public String getSeries() {
        return series;
    }

    public String getNumber() {
        return number;
    }

    public LocalDate getIssuedFrom() {
        return issuedFrom;
    }

    public LocalDate getIssuedTo() {
        return issuedTo;
    }

    public RecordStatus getStatus() {
        return status;
    }
}
