package com.squareetlabs.ticketbai.transport.lroe;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Datos de renta de un ingreso del modelo 140 ({@code DetalleRenta}): epígrafe del IAE (obligatorio) y, cuando
 * procede, territorio de alta, número fijo o referencia catastral, importe a computar en el IRPF distinto de la base
 * del IVA (con su causa) y criterio de cobros y pagos. Un ingreso lleva de 1 a 10.
 */
public final class IncomeDetail {

    /** {@code TerritorioAltaActividad}. */
    public enum Territory {
        ARABA("1"), BIZKAIA("2"), GIPUZKOA("3"), NAVARRA("4"), STATE("5");

        private final String code;

        Territory(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** {@code CausaIngresoIRPFDiferenteBaseImpoIVA}. */
    public enum DifferentIncomeCause {
        ADVANCE_PAYMENTS("01"), ASSET_TRANSFER("02"), SELF_CONSUMPTION("03"), SEVERAL_ACTIVITIES("04"),
        ACCRUAL_OR_INSTALMENTS("05"), ASSET_TRANSFER_IN_INSTALMENTS("06"), OTHER("07");

        private final String code;

        DifferentIncomeCause(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final String activityCode;
    private Territory territory;
    private String premisesReference;
    private BigDecimal incomeTaxAmount;
    private DifferentIncomeCause incomeTaxCause;
    private Boolean cashCriterion;

    private IncomeDetail(String activityCode) {
        this.activityCode = activityCode;
    }

    /** Detalle con el epígrafe del IAE ({@code Epigrafe}, hasta 7 dígitos). */
    public static IncomeDetail activity(String activityCode) {
        Objects.requireNonNull(activityCode, "activityCode");
        if (!activityCode.matches("\\d{1,7}")) {
            throw new IllegalArgumentException("El epígrafe del IAE tiene hasta 7 dígitos: " + activityCode);
        }
        return new IncomeDetail(activityCode);
    }

    public IncomeDetail territory(Territory territory) {
        this.territory = territory;
        return this;
    }

    /** {@code NumeroFijoOReferenciaCatastral} (hasta 25 caracteres). */
    public IncomeDetail premisesReference(String reference) {
        this.premisesReference = reference;
        return this;
    }

    /**
     * El ingreso a computar en el IRPF es distinto de la base imponible del IVA ({@code IngresoAComputarIRPFDiferenteBaseImpoIVA}
     * = S): importe y, si se conoce, causa.
     */
    public IncomeDetail incomeTaxAmount(BigDecimal amount, DifferentIncomeCause cause) {
        this.incomeTaxAmount = Objects.requireNonNull(amount, "amount");
        this.incomeTaxCause = cause;
        return this;
    }

    /** {@code CriterioCobrosYPagos}. */
    public IncomeDetail cashCriterion(boolean cashCriterion) {
        this.cashCriterion = cashCriterion;
        return this;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public Territory getTerritory() {
        return territory;
    }

    public String getPremisesReference() {
        return premisesReference;
    }

    public BigDecimal getIncomeTaxAmount() {
        return incomeTaxAmount;
    }

    public DifferentIncomeCause getIncomeTaxCause() {
        return incomeTaxCause;
    }

    public Boolean getCashCriterion() {
        return cashCriterion;
    }
}
