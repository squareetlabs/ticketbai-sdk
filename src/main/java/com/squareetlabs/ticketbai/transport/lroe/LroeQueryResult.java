package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.chain.ChainLink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resultado de una consulta del LROE: paginación y, por cada factura, los datos que permiten <b>recuperar la cadena</b>
 * (serie, número, fecha y los 100 primeros caracteres de su firma) y su estado.
 */
public final class LroeQueryResult {

    /** Factura registrada en el LROE. */
    public static final class Invoice {
        private final String series;
        private final String number;
        private final LocalDate issueDate;
        private final LocalTime issueTime;
        private final BigDecimal totalAmount;
        private final String signatureValuePrefix;
        private final ChainLink previousLink;
        private final String status;
        private final String errorCode;
        private final String errorDescription;
        private final String registeredAt;

        public Invoice(String series, String number, LocalDate issueDate, LocalTime issueTime, BigDecimal totalAmount,
                       String signatureValuePrefix, ChainLink previousLink, String status, String errorCode,
                       String errorDescription, String registeredAt) {
            this.series = series;
            this.number = number;
            this.issueDate = issueDate;
            this.issueTime = issueTime;
            this.totalAmount = totalAmount;
            this.signatureValuePrefix = signatureValuePrefix;
            this.previousLink = previousLink;
            this.status = status;
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
            this.registeredAt = registeredAt;
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

        public LocalTime getIssueTime() {
            return issueTime;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        /** Los 100 primeros caracteres de la firma del fichero ({@code Signature}). */
        public String getSignatureValuePrefix() {
            return signatureValuePrefix;
        }

        /** Encadenamiento que llevaba el fichero, o {@code null}. */
        public ChainLink getPreviousLink() {
            return previousLink;
        }

        /** {@code EstadoRegistro}: Correcto, AceptadoConErrores o Anulado. */
        public String getStatus() {
            return status;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorDescription() {
            return errorDescription;
        }

        /** {@code FechaPresentacionAlta}. */
        public String getRegisteredAt() {
            return registeredAt;
        }

        /** Eslabón con el que la siguiente factura encadenaría esta. */
        public ChainLink toChainLink() {
            return ChainLink.ofPrefix(series, number, issueDate, signatureValuePrefix);
        }
    }

    private final String queriedAt;
    private final int page;
    private final boolean hasRecords;
    private final int totalPages;
    private final boolean moreRecords;
    private final int totalRecords;
    private final List<Invoice> invoices;
    private final byte[] rawResponse;

    public LroeQueryResult(String queriedAt, int page, boolean hasRecords, int totalPages, boolean moreRecords,
                           int totalRecords, List<Invoice> invoices, byte[] rawResponse) {
        this.queriedAt = queriedAt;
        this.page = page;
        this.hasRecords = hasRecords;
        this.totalPages = totalPages;
        this.moreRecords = moreRecords;
        this.totalRecords = totalRecords;
        this.invoices = List.copyOf(invoices);
        this.rawResponse = rawResponse.clone();
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

    /** Hay más páginas después de esta. */
    public boolean hasMoreRecords() {
        return moreRecords;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public byte[] getRawResponse() {
        return rawResponse.clone();
    }

    /**
     * Candidata a último eslabón de la cadena en esta página: la factura que no encadena ninguna otra de la página y, si
     * hay varias, la más reciente por fecha y hora de expedición. Manda el encadenamiento porque la fecha y la hora son
     * locales y sin huso: en la hora repetida del cambio de hora (último domingo de octubre, de 02:00 a 02:59 dos veces)
     * una factura de la primera pasada puede tener una hora posterior a la de otra emitida después. Para recuperar la
     * cadena con seguridad hay que recorrer todas las páginas y comprobar el encadenamiento.
     */
    public Optional<Invoice> latest() {
        Set<ChainLink> chained = invoices.stream().map(Invoice::getPreviousLink).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Invoice> unchained = invoices.stream().filter(i -> !chained.contains(i.toChainLink()))
                .collect(Collectors.toList());
        return (unchained.isEmpty() ? invoices : unchained).stream().max(Comparator.comparing(Invoice::getIssueDate)
                .thenComparing(i -> i.getIssueTime() == null ? LocalTime.MIN : i.getIssueTime()));
    }
}
