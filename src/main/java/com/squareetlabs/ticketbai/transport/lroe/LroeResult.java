package com.squareetlabs.ticketbai.transport.lroe;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta del LROE a un envío: estado global (cabecera {@code eus-bizkaia-n3-tipo-respuesta}), identificativo de la
 * entrada y el resultado de cada fichero. Lo que no quede {@link RecordStatus#CORRECT} se corrige y se vuelve a enviar.
 */
public final class LroeResult {

    /** Estado global de la petición. */
    public enum Status {
        CORRECT, PARTIALLY_CORRECT, INCORRECT
    }

    /** Estado de un registro. */
    public enum RecordStatus {
        CORRECT, ACCEPTED_WITH_ERRORS, INCORRECT, ANNULLED
    }

    /** Resultado de un fichero del envío. */
    public static final class Record {
        private final String series;
        private final String number;
        private final LocalDate issueDate;
        private final RecordStatus status;
        private final String errorCode;
        private final String errorDescription;
        private final String errorDescriptionEu;

        public Record(String series, String number, LocalDate issueDate, RecordStatus status, String errorCode,
                      String errorDescription, String errorDescriptionEu) {
            this.series = series;
            this.number = number;
            this.issueDate = issueDate;
            this.status = status;
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
            this.errorDescriptionEu = errorDescriptionEu;
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

        public RecordStatus getStatus() {
            return status;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorDescription() {
            return errorDescription;
        }

        public String getErrorDescriptionEu() {
            return errorDescriptionEu;
        }

        @Override
        public String toString() {
            return (series == null ? "" : series + "/") + number + " " + status + (errorCode == null ? "" : " " + errorCode);
        }
    }

    private final Status status;
    private final String entryId;
    private final String errorCode;
    private final String errorMessage;
    private final String presentedAt;
    private final List<Record> records;
    private final byte[] rawResponse;

    public LroeResult(Status status, String entryId, String errorCode, String errorMessage, String presentedAt,
                      List<Record> records, byte[] rawResponse) {
        this.status = status;
        this.entryId = entryId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.presentedAt = presentedAt;
        this.records = List.copyOf(records);
        this.rawResponse = rawResponse == null ? new byte[0] : rawResponse.clone();
    }

    public Status getStatus() {
        return status;
    }

    /** {@code eus-bizkaia-n3-identificativo}: identificativo de la entrada (si el estado no es INCORRECT). */
    public String getEntryId() {
        return entryId;
    }

    /** {@code eus-bizkaia-n3-codigo-respuesta} (solo si INCORRECT). */
    public String getErrorCode() {
        return errorCode;
    }

    /** {@code eus-bizkaia-n3-mensaje-respuesta} (solo si INCORRECT). */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** {@code DatosPresentacion/FechaPresentacion}, si la respuesta la trae. */
    public String getPresentedAt() {
        return presentedAt;
    }

    public List<Record> getRecords() {
        return records;
    }

    /** Respuesta XML descomprimida, para conservarla. */
    public byte[] getRawResponse() {
        return rawResponse.clone();
    }
}
