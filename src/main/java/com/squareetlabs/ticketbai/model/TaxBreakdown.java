package com.squareetlabs.ticketbai.model;

import com.squareetlabs.ticketbai.model.Codes.ExemptionCause;
import com.squareetlabs.ticketbai.model.Codes.NonExemptType;
import com.squareetlabs.ticketbai.model.Codes.NotSubjectCause;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Desglose de una factura o de un tipo de operación: sujeta (exenta con su causa, o no exenta S1/S2 con el detalle de
 * IVA) y no sujeta (con su causa). Es el contenido de {@code DesgloseFactura}, {@code PrestacionServicios} o
 * {@code Entrega}.
 */
public final class TaxBreakdown {

    private final Map<ExemptionCause, BigDecimal> exempt;
    private final Map<NonExemptType, List<VatDetail>> nonExempt;
    private final Map<NotSubjectCause, BigDecimal> notSubject;

    private TaxBreakdown(Builder builder) {
        this.exempt = Collections.unmodifiableMap(new LinkedHashMap<>(builder.exempt));
        Map<NonExemptType, List<VatDetail>> copy = new EnumMap<>(NonExemptType.class);
        builder.nonExempt.forEach((type, details) -> copy.put(type, Collections.unmodifiableList(new ArrayList<>(details))));
        this.nonExempt = Collections.unmodifiableMap(copy);
        this.notSubject = Collections.unmodifiableMap(new LinkedHashMap<>(builder.notSubject));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<ExemptionCause, BigDecimal> getExempt() {
        return exempt;
    }

    public Map<NonExemptType, List<VatDetail>> getNonExempt() {
        return nonExempt;
    }

    public Map<NotSubjectCause, BigDecimal> getNotSubject() {
        return notSubject;
    }

    public boolean isSubject() {
        return !exempt.isEmpty() || !nonExempt.isEmpty();
    }

    public boolean isEmpty() {
        return !isSubject() && notSubject.isEmpty();
    }

    /** Constructor de {@link TaxBreakdown}. */
    public static final class Builder {
        private final Map<ExemptionCause, BigDecimal> exempt = new EnumMap<>(ExemptionCause.class);
        private final Map<NonExemptType, List<VatDetail>> nonExempt = new EnumMap<>(NonExemptType.class);
        private final Map<NotSubjectCause, BigDecimal> notSubject = new EnumMap<>(NotSubjectCause.class);

        private Builder() {
        }

        /** Base exenta por {@code cause}. */
        public Builder exempt(ExemptionCause cause, BigDecimal taxableBase) {
            exempt.merge(Objects.requireNonNull(cause, "cause"), Objects.requireNonNull(taxableBase, "taxableBase"),
                    BigDecimal::add);
            return this;
        }

        /** Detalle de IVA sujeto y no exento ({@code S1} sin inversión, {@code S2} con inversión del sujeto pasivo). */
        public Builder nonExempt(NonExemptType type, VatDetail detail) {
            nonExempt.computeIfAbsent(Objects.requireNonNull(type, "type"), t -> new ArrayList<>())
                    .add(Objects.requireNonNull(detail, "detail"));
            return this;
        }

        /** Importe no sujeto por {@code cause}. */
        public Builder notSubject(NotSubjectCause cause, BigDecimal amount) {
            notSubject.merge(Objects.requireNonNull(cause, "cause"), Objects.requireNonNull(amount, "amount"),
                    BigDecimal::add);
            return this;
        }

        public TaxBreakdown build() {
            return new TaxBreakdown(this);
        }
    }
}
