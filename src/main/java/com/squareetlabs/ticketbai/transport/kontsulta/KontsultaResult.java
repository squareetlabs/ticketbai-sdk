package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.chain.ChainLink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Respuesta de Kontsulta (facturas enviadas o última factura): paginación, error de la consulta si lo hay y, por cada
 * factura, los datos para conciliar su estado y recuperar la cadena.
 */
public final class KontsultaResult {

    /** Factura admitida en TicketBAI. */
    public static final class Invoice {
        private final String series;
        private final String number;
        private final LocalDateTime issuedAt;
        private final LocalDate operationDate;
        private final BigDecimal totalAmount;
        private final String identifier;
        private final String signatureValuePrefix;
        private final String statusCode;
        private final List<String> ticketBaiErrorCodes;
        private final String registeredAt;

        public Invoice(String series, String number, LocalDateTime issuedAt, LocalDate operationDate, BigDecimal totalAmount,
                       String identifier, String signatureValuePrefix, String statusCode, List<String> ticketBaiErrorCodes,
                       String registeredAt) {
            this.series = series;
            this.number = number;
            this.issuedAt = issuedAt;
            this.operationDate = operationDate;
            this.totalAmount = totalAmount;
            this.identifier = identifier;
            this.signatureValuePrefix = signatureValuePrefix;
            this.statusCode = statusCode;
            this.ticketBaiErrorCodes = List.copyOf(ticketBaiErrorCodes);
            this.registeredAt = registeredAt;
        }

        public String getSeries() {
            return series;
        }

        public String getNumber() {
            return number;
        }

        public LocalDateTime getIssuedAt() {
            return issuedAt;
        }

        public LocalDate getOperationDate() {
            return operationDate;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        /** Código TicketBAI de 39 caracteres. */
        public String getIdentifier() {
            return identifier;
        }

        /** Los 100 primeros caracteres de la firma ({@code SignatureValue}). */
        public String getSignatureValuePrefix() {
            return signatureValuePrefix;
        }

        /** {@code Estado} tal como llega. */
        public String getStatusCode() {
            return statusCode;
        }

        /** {@code Estado} como enumerado, o {@code null} si el código no se conoce. */
        public KontsultaRecordStatus getStatus() {
            return KontsultaRecordStatus.fromCode(statusCode);
        }

        /** Códigos de error TicketBAI (solo si la consulta pidió {@code MostrarErrores}). */
        public List<String> getTicketBaiErrorCodes() {
            return ticketBaiErrorCodes;
        }

        /** {@code FechaHoraPresentacionAlta} (solo Gipuzkoa), o {@code null}. */
        public String getRegisteredAt() {
            return registeredAt;
        }

        /** Eslabón con el que la siguiente factura encadenaría esta. */
        public ChainLink toChainLink() {
            return ChainLink.ofPrefix(series, number, issuedAt.toLocalDate(), signatureValuePrefix);
        }
    }

    private final String issuerNif;
    private final String issuerName;
    private final String queriedAt;
    private final int page;
    private final boolean hasRecords;
    private final int totalPages;
    private final boolean moreRecords;
    private final int totalRecords;
    private final String errorDescription;
    private final List<Invoice> invoices;
    private final byte[] rawResponse;

    public KontsultaResult(String issuerNif, String issuerName, String queriedAt, int page, boolean hasRecords, int totalPages,
                           boolean moreRecords, int totalRecords, String errorDescription, List<Invoice> invoices,
                           byte[] rawResponse) {
        this.issuerNif = issuerNif;
        this.issuerName = issuerName;
        this.queriedAt = queriedAt;
        this.page = page;
        this.hasRecords = hasRecords;
        this.totalPages = totalPages;
        this.moreRecords = moreRecords;
        this.totalRecords = totalRecords;
        this.errorDescription = errorDescription;
        this.invoices = List.copyOf(invoices);
        this.rawResponse = rawResponse.clone();
    }

    public String getIssuerNif() {
        return issuerNif;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public String getQueriedAt() {
        return queriedAt;
    }

    public int getPage() {
        return page;
    }

    public boolean hasRecords() {
        return hasRecords;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasMoreRecords() {
        return moreRecords;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    /**
     * {@code Error/Descripcion} de la consulta, o {@code null} si llega vacío. El esquema exige el bloque {@code Error}
     * en toda respuesta y no documenta cuándo lo rellena: se devuelve tal cual, sin interpretarlo.
     */
    public String getErrorDescription() {
        return errorDescription;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public byte[] getRawResponse() {
        return rawResponse.clone();
    }

    /**
     * La factura más reciente por fecha y hora de expedición (en «última factura», la única). La fecha y la hora son
     * locales y sin huso, y Kontsulta no devuelve el encadenamiento de cada factura: en la hora repetida del cambio de
     * hora (último domingo de octubre, de 02:00 a 02:59 dos veces) la más reciente por hora puede no ser la última
     * emitida. Para el último eslabón de la cadena, usa la consulta de «última factura»
     * ({@link KontsultaClient#latest(String)}).
     */
    public Optional<Invoice> latest() {
        return invoices.stream().filter(i -> i.getIssuedAt() != null).max(Comparator.comparing(Invoice::getIssuedAt));
    }
}
