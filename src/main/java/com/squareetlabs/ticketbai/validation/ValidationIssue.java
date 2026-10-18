package com.squareetlabs.ticketbai.validation;

import java.util.Objects;

/** Problema encontrado al validar una factura antes de firmarla. */
public final class ValidationIssue {

    /** ERROR: Hacienda rechazaría el fichero o la factura es incoherente. WARNING: Hacienda lo aceptaría con aviso. */
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String field;
    private final String message;
    private final String reference;

    public ValidationIssue(Severity severity, String field, String message, String reference) {
        this.severity = Objects.requireNonNull(severity, "severity");
        this.field = Objects.requireNonNull(field, "field");
        this.message = Objects.requireNonNull(message, "message");
        this.reference = reference;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** Elemento del fichero afectado ({@code DatosFactura/ImporteTotalFactura}...). */
    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    /** Fuente de la regla: código de validación de la Diputación o documento oficial. */
    public String getReference() {
        return reference;
    }

    @Override
    public String toString() {
        return severity + " " + field + ": " + message + (reference == null ? "" : " [" + reference + "]");
    }
}
