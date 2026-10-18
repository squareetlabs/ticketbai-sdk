package com.squareetlabs.ticketbai.model;

import com.squareetlabs.ticketbai.model.Codes.RectificationCode;
import com.squareetlabs.ticketbai.model.Codes.RectificationType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Datos de una factura rectificativa: código (R1–R5), tipo (por sustitución o por diferencias), importes rectificados
 * (solo por sustitución) y facturas rectificadas.
 */
public final class Rectification {

    private final RectificationCode code;
    private final RectificationType type;
    private final BigDecimal rectifiedBase;
    private final BigDecimal rectifiedQuota;
    private final BigDecimal rectifiedSurchargeQuota;
    private final List<InvoiceReference> rectifiedInvoices;

    private Rectification(RectificationCode code, RectificationType type, BigDecimal rectifiedBase,
                          BigDecimal rectifiedQuota, BigDecimal rectifiedSurchargeQuota,
                          List<InvoiceReference> rectifiedInvoices) {
        this.code = Objects.requireNonNull(code, "code");
        this.type = Objects.requireNonNull(type, "type");
        this.rectifiedBase = rectifiedBase;
        this.rectifiedQuota = rectifiedQuota;
        this.rectifiedSurchargeQuota = rectifiedSurchargeQuota;
        this.rectifiedInvoices = rectifiedInvoices == null ? Collections.emptyList() : List.copyOf(rectifiedInvoices);
    }

    /** Rectificativa por diferencias ({@code I}). */
    public static Rectification byDifferences(RectificationCode code, List<InvoiceReference> rectifiedInvoices) {
        return new Rectification(code, RectificationType.DIFFERENCES, null, null, null, rectifiedInvoices);
    }

    /** Rectificativa por sustitución ({@code S}) con la base y la cuota de la factura sustituida. */
    public static Rectification bySubstitution(RectificationCode code, BigDecimal rectifiedBase, BigDecimal rectifiedQuota,
                                               BigDecimal rectifiedSurchargeQuota, List<InvoiceReference> rectifiedInvoices) {
        return new Rectification(code, RectificationType.SUBSTITUTION, Objects.requireNonNull(rectifiedBase, "rectifiedBase"),
                Objects.requireNonNull(rectifiedQuota, "rectifiedQuota"), rectifiedSurchargeQuota, rectifiedInvoices);
    }

    public RectificationCode getCode() {
        return code;
    }

    public RectificationType getType() {
        return type;
    }

    public BigDecimal getRectifiedBase() {
        return rectifiedBase;
    }

    public BigDecimal getRectifiedQuota() {
        return rectifiedQuota;
    }

    public BigDecimal getRectifiedSurchargeQuota() {
        return rectifiedSurchargeQuota;
    }

    public List<InvoiceReference> getRectifiedInvoices() {
        return rectifiedInvoices;
    }
}
