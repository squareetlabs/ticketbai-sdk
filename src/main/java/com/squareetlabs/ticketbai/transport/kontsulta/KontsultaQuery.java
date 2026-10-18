package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.Territory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Objects;

/**
 * Consulta Kontsulta (Gipuzkoa y Álava) de las facturas enviadas de un emisor en un ejercicio y periodo, por páginas,
 * con filtros opcionales. El XML de consulta no se firma.
 */
public final class KontsultaQuery {

    /** Periodo de la consulta: anual, trimestral o mensual. */
    public enum Period {
        ANNUAL("0A"), Q1("1T"), Q2("2T"), Q3("3T"), Q4("4T"),
        JANUARY("01"), FEBRUARY("02"), MARCH("03"), APRIL("04"), MAY("05"), JUNE("06"),
        JULY("07"), AUGUST("08"), SEPTEMBER("09"), OCTOBER("10"), NOVEMBER("11"), DECEMBER("12");

        private final String code;

        Period(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** Periodo mensual (1 a 12). */
        public static Period month(int month) {
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Mes fuera de rango: " + month);
            }
            return values()[4 + month];
        }
    }

    /** {@code EstadoEnvio}. */
    public enum SubmissionStatus {
        ACCEPTED("3"), ACCEPTED_WITH_ERRORS("2");

        private final String code;

        SubmissionStatus(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Territory territory;
    private final String issuerNif;
    private final Year fiscalYear;
    private final Period period;
    private int page = 1;
    private String series;
    private String number;
    private LocalDateTime issuedFrom;
    private LocalDateTime issuedTo;
    private LocalDate operationFrom;
    private LocalDate operationTo;
    private LocalDateTime submittedFrom;
    private LocalDateTime submittedTo;
    private SubmissionStatus submissionStatus;
    private KontsultaRecordStatus recordStatus;
    private Boolean showErrors;

    private KontsultaQuery(Territory territory, String issuerNif, Year fiscalYear, Period period) {
        if (Objects.requireNonNull(territory, "territory") == Territory.BIZKAIA) {
            throw new IllegalArgumentException("Bizkaia no tiene Kontsulta: la consulta se hace en el LROE (LroeQuery)");
        }
        this.territory = territory;
        this.issuerNif = Objects.requireNonNull(issuerNif, "issuerNif");
        this.fiscalYear = Objects.requireNonNull(fiscalYear, "fiscalYear");
        this.period = Objects.requireNonNull(period, "period");
    }

    public static KontsultaQuery of(Territory territory, String issuerNif, Year fiscalYear, Period period) {
        return new KontsultaQuery(territory, issuerNif, fiscalYear, period);
    }

    /** Página (desde 1). */
    public KontsultaQuery page(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("La página empieza en 1");
        }
        this.page = page;
        return this;
    }

    public KontsultaQuery series(String series) {
        this.series = series;
        return this;
    }

    public KontsultaQuery number(String number) {
        this.number = number;
        return this;
    }

    /** Fecha y hora de expedición (cualquiera de los extremos puede ser {@code null}). */
    public KontsultaQuery issuedBetween(LocalDateTime from, LocalDateTime to) {
        this.issuedFrom = from;
        this.issuedTo = to;
        return this;
    }

    public KontsultaQuery operationBetween(LocalDate from, LocalDate to) {
        this.operationFrom = from;
        this.operationTo = to;
        return this;
    }

    /**
     * Fecha y hora de presentación. Gipuzkoa recomienda fijarla al recorrer varias páginas: sin ella, una factura recibida
     * entre dos peticiones puede desplazar los resultados y repetir una factura en dos páginas.
     */
    public KontsultaQuery submittedBetween(LocalDateTime from, LocalDateTime to) {
        this.submittedFrom = from;
        this.submittedTo = to;
        return this;
    }

    public KontsultaQuery submissionStatus(SubmissionStatus submissionStatus) {
        this.submissionStatus = submissionStatus;
        return this;
    }

    /** @throws IllegalArgumentException si el estado no existe en el territorio de la consulta */
    public KontsultaQuery recordStatus(KontsultaRecordStatus recordStatus) {
        if (recordStatus != null && !recordStatus.isSupportedIn(territory)) {
            throw new IllegalArgumentException("El estado " + recordStatus + " no existe en la Kontsulta de " + territory);
        }
        this.recordStatus = recordStatus;
        return this;
    }

    /** Incluir los códigos de error de validación de cada factura. */
    public KontsultaQuery showErrors(boolean showErrors) {
        this.showErrors = showErrors;
        return this;
    }

    public Territory getTerritory() {
        return territory;
    }

    public String getIssuerNif() {
        return issuerNif;
    }

    public Year getFiscalYear() {
        return fiscalYear;
    }

    public Period getPeriod() {
        return period;
    }

    public int getPage() {
        return page;
    }

    public String getSeries() {
        return series;
    }

    public String getNumber() {
        return number;
    }

    public LocalDateTime getIssuedFrom() {
        return issuedFrom;
    }

    public LocalDateTime getIssuedTo() {
        return issuedTo;
    }

    public LocalDate getOperationFrom() {
        return operationFrom;
    }

    public LocalDate getOperationTo() {
        return operationTo;
    }

    public LocalDateTime getSubmittedFrom() {
        return submittedFrom;
    }

    public LocalDateTime getSubmittedTo() {
        return submittedTo;
    }

    public SubmissionStatus getSubmissionStatus() {
        return submissionStatus;
    }

    public KontsultaRecordStatus getRecordStatus() {
        return recordStatus;
    }

    public Boolean getShowErrors() {
        return showErrors;
    }
}
