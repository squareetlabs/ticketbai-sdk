package com.squareetlabs.ticketbai.model;

import java.math.BigDecimal;
import java.util.Objects;

/** {@code DetalleIVA}: base, tipo y cuota, con el recargo de equivalencia si lo hay. */
public final class VatDetail {

    private final BigDecimal taxableBase;
    private final BigDecimal rate;
    private final BigDecimal quota;
    private final BigDecimal surchargeRate;
    private final BigDecimal surchargeQuota;
    private final Boolean surchargeOrSimplifiedRegime;

    private VatDetail(BigDecimal taxableBase, BigDecimal rate, BigDecimal quota, BigDecimal surchargeRate,
                      BigDecimal surchargeQuota, Boolean surchargeOrSimplifiedRegime) {
        this.taxableBase = Objects.requireNonNull(taxableBase, "taxableBase");
        this.rate = rate;
        this.quota = quota;
        this.surchargeRate = surchargeRate;
        this.surchargeQuota = surchargeQuota;
        this.surchargeOrSimplifiedRegime = surchargeOrSimplifiedRegime;
    }

    public static VatDetail of(BigDecimal taxableBase, BigDecimal rate, BigDecimal quota) {
        return new VatDetail(taxableBase, Objects.requireNonNull(rate, "rate"), Objects.requireNonNull(quota, "quota"),
                null, null, null);
    }

    /** Copia con recargo de equivalencia. */
    public VatDetail withSurcharge(BigDecimal surchargeRate, BigDecimal surchargeQuota) {
        return new VatDetail(taxableBase, rate, quota, surchargeRate, surchargeQuota, surchargeOrSimplifiedRegime);
    }

    /** Copia con {@code OperacionEnRecargoDeEquivalenciaORegimenSimplificado}. */
    public VatDetail withSurchargeOrSimplifiedRegime(boolean value) {
        return new VatDetail(taxableBase, rate, quota, surchargeRate, surchargeQuota, value);
    }

    public BigDecimal getTaxableBase() {
        return taxableBase;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public BigDecimal getQuota() {
        return quota;
    }

    public BigDecimal getSurchargeRate() {
        return surchargeRate;
    }

    public BigDecimal getSurchargeQuota() {
        return surchargeQuota;
    }

    public Boolean getSurchargeOrSimplifiedRegime() {
        return surchargeOrSimplifiedRegime;
    }
}
