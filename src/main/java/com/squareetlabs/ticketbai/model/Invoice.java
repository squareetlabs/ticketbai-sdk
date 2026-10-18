package com.squareetlabs.ticketbai.model;

import com.squareetlabs.ticketbai.model.Codes.IssuedBy;
import com.squareetlabs.ticketbai.model.Codes.RegimeKey;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Factura TicketBAI (fichero de alta) sin la huella ni la firma, que se añaden al firmar. */
public final class Invoice {

    private final Party issuer;
    private final List<Party> recipients;
    private final IssuedBy issuedBy;
    private final String series;
    private final String number;
    private final LocalDate issueDate;
    private final LocalTime issueTime;
    private final boolean simplified;
    private final boolean substitutesSimplified;
    private final Rectification rectification;
    private final LocalDate operationDate;
    private final String description;
    private final List<InvoiceLine> lines;
    private final BigDecimal totalAmount;
    private final BigDecimal withholding;
    private final BigDecimal taxableBaseAtCost;
    private final List<RegimeKey> regimeKeys;
    private final Breakdown breakdown;

    private Invoice(Builder b) {
        this.issuer = Objects.requireNonNull(b.issuer, "issuer");
        if (!issuer.hasNif()) {
            throw new IllegalArgumentException("El emisor se identifica con NIF");
        }
        this.recipients = Collections.unmodifiableList(new ArrayList<>(b.recipients));
        this.issuedBy = b.issuedBy;
        this.series = b.series;
        this.number = Objects.requireNonNull(b.number, "number");
        this.issueDate = Objects.requireNonNull(b.issueDate, "issueDate");
        this.issueTime = Objects.requireNonNull(b.issueTime, "issueTime");
        this.simplified = b.simplified;
        this.substitutesSimplified = b.substitutesSimplified;
        this.rectification = b.rectification;
        this.operationDate = b.operationDate;
        this.description = Objects.requireNonNull(b.description, "description");
        this.lines = Collections.unmodifiableList(new ArrayList<>(b.lines));
        this.totalAmount = Objects.requireNonNull(b.totalAmount, "totalAmount");
        this.withholding = b.withholding;
        this.taxableBaseAtCost = b.taxableBaseAtCost;
        if (b.regimeKeys.isEmpty() || b.regimeKeys.size() > 3) {
            throw new IllegalArgumentException("La factura lleva de 1 a 3 claves de régimen");
        }
        this.regimeKeys = Collections.unmodifiableList(new ArrayList<>(b.regimeKeys));
        this.breakdown = Objects.requireNonNull(b.breakdown, "breakdown");
        if (recipients.size() > 100) {
            throw new IllegalArgumentException("La factura admite como máximo 100 destinatarios");
        }
        if (lines.size() > 1000) {
            throw new IllegalArgumentException("La factura admite como máximo 1000 líneas de detalle");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Party getIssuer() {
        return issuer;
    }

    public List<Party> getRecipients() {
        return recipients;
    }

    public IssuedBy getIssuedBy() {
        return issuedBy;
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

    public boolean isSimplified() {
        return simplified;
    }

    public boolean isSubstitutesSimplified() {
        return substitutesSimplified;
    }

    public Rectification getRectification() {
        return rectification;
    }

    public LocalDate getOperationDate() {
        return operationDate;
    }

    public String getDescription() {
        return description;
    }

    public List<InvoiceLine> getLines() {
        return lines;
    }

    /** {@code ImporteTotalFactura}: con IVA y recargo, <b>sin descontar la retención</b>. */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getWithholding() {
        return withholding;
    }

    public BigDecimal getTaxableBaseAtCost() {
        return taxableBaseAtCost;
    }

    public List<RegimeKey> getRegimeKeys() {
        return regimeKeys;
    }

    public Breakdown getBreakdown() {
        return breakdown;
    }

    /** Constructor de {@link Invoice}. */
    public static final class Builder {
        private Party issuer;
        private final List<Party> recipients = new ArrayList<>();
        private IssuedBy issuedBy = IssuedBy.ISSUER;
        private String series;
        private String number;
        private LocalDate issueDate;
        private LocalTime issueTime;
        private boolean simplified;
        private boolean substitutesSimplified;
        private Rectification rectification;
        private LocalDate operationDate;
        private String description;
        private final List<InvoiceLine> lines = new ArrayList<>();
        private BigDecimal totalAmount;
        private BigDecimal withholding;
        private BigDecimal taxableBaseAtCost;
        private final List<RegimeKey> regimeKeys = new ArrayList<>();
        private Breakdown breakdown;

        private Builder() {
        }

        public Builder issuer(String nif, String name) {
            this.issuer = Party.withNif(nif, Objects.requireNonNull(name, "name"));
            return this;
        }

        public Builder recipient(Party recipient) {
            recipients.add(Objects.requireNonNull(recipient, "recipient"));
            return this;
        }

        public Builder issuedBy(IssuedBy issuedBy) {
            this.issuedBy = Objects.requireNonNull(issuedBy, "issuedBy");
            return this;
        }

        public Builder series(String series) {
            this.series = series;
            return this;
        }

        public Builder number(String number) {
            this.number = number;
            return this;
        }

        public Builder issuedAt(LocalDate date, LocalTime time) {
            this.issueDate = date;
            this.issueTime = time == null ? null : time.withNano(0);
            return this;
        }

        public Builder simplified(boolean simplified) {
            this.simplified = simplified;
            return this;
        }

        public Builder substitutesSimplified(boolean substitutesSimplified) {
            this.substitutesSimplified = substitutesSimplified;
            return this;
        }

        public Builder rectification(Rectification rectification) {
            this.rectification = rectification;
            return this;
        }

        /** {@code FechaOperacion}: solo si es distinta de la fecha de expedición. */
        public Builder operationDate(LocalDate operationDate) {
            this.operationDate = operationDate;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder line(InvoiceLine line) {
            lines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        /** {@code RetencionSoportada}. No se descuenta de {@link #totalAmount}. */
        public Builder withholding(BigDecimal withholding) {
            this.withholding = withholding;
            return this;
        }

        public Builder taxableBaseAtCost(BigDecimal taxableBaseAtCost) {
            this.taxableBaseAtCost = taxableBaseAtCost;
            return this;
        }

        public Builder regimeKey(RegimeKey key) {
            regimeKeys.add(Objects.requireNonNull(key, "key"));
            return this;
        }

        public Builder breakdown(Breakdown breakdown) {
            this.breakdown = breakdown;
            return this;
        }

        public Invoice build() {
            return new Invoice(this);
        }
    }
}
