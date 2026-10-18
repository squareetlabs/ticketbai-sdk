package com.squareetlabs.ticketbai.transport;

import java.util.List;

/**
 * Respuesta de la Diputación a un envío ({@code TicketBaiResponse}): estado {@code 00} recibido o {@code 01}
 * rechazado, el código TicketBAI, la fecha de recepción, el CSV y los resultados de validación (avisos o motivos de
 * rechazo, en castellano y en euskera).
 */
public final class SubmissionResult {

    /** Estado de la recepción. */
    public enum Status {
        /** {@code 00}: recibido (puede traer avisos). */
        RECEIVED,
        /** {@code 01}: rechazado; se corrige con Zuzendu (subsanar), sin regenerar el fichero. */
        REJECTED
    }

    /** Un resultado de validación: código de tres dígitos y descripción en castellano y en euskera. */
    public static final class ValidationResult {
        private final String code;
        private final String description;
        private final String descriptionEu;

        public ValidationResult(String code, String description, String descriptionEu) {
            this.code = code;
            this.description = description;
            this.descriptionEu = descriptionEu;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String getDescriptionEu() {
            return descriptionEu;
        }

        @Override
        public String toString() {
            return code + " " + description;
        }
    }

    private final Status status;
    private final String statusCode;
    private final String identifier;
    private final String receivedAt;
    private final String description;
    private final String csv;
    private final List<ValidationResult> validationResults;
    private final byte[] rawResponse;

    public SubmissionResult(Status status, String statusCode, String identifier, String receivedAt, String description,
                            String csv, List<ValidationResult> validationResults, byte[] rawResponse) {
        this.status = status;
        this.statusCode = statusCode;
        this.identifier = identifier;
        this.receivedAt = receivedAt;
        this.description = description;
        this.csv = csv;
        this.validationResults = List.copyOf(validationResults);
        this.rawResponse = rawResponse.clone();
    }

    public Status getStatus() {
        return status;
    }

    public boolean isReceived() {
        return status == Status.RECEIVED;
    }

    /** {@code Estado} tal como llegó ({@code 00}, {@code 01}). */
    public String getStatusCode() {
        return statusCode;
    }

    /** {@code IdentificadorTBAI}, si la respuesta lo trae. */
    public String getIdentifier() {
        return identifier;
    }

    /** {@code FechaRecepcion} ({@code dd-mm-aaaa hh:mm:ss}). */
    public String getReceivedAt() {
        return receivedAt;
    }

    public String getDescription() {
        return description;
    }

    public String getCsv() {
        return csv;
    }

    public List<ValidationResult> getValidationResults() {
        return validationResults;
    }

    /** Respuesta completa, para conservarla junto al fichero enviado. */
    public byte[] getRawResponse() {
        return rawResponse.clone();
    }
}
