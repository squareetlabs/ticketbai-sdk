package com.squareetlabs.ticketbai.transport.lroe;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bloque {@code OtraInformacionTrascendenciaTributaria} de una factura del modelo 240 (subcapítulo 1.1): datos que no van
 * en el fichero TicketBAI. Todos los campos son opcionales; un bloque vacío no se envía.
 * <p>
 * Reglas de Batuz (validaciones LROE v1.0.5): la referencia catastral es obligatoria salvo en las situaciones 3 y 4
 * ({@code B4_2000300}); el cupón solo puede ser S en una rectificativa R1 o R5 ({@code B4_2000301}); la facturación de las
 * disposiciones adicionales segunda y quinta solo puede ser S si la factura no la emite un tercero ni el destinatario
 * ({@code B4_2000302}). Las dos últimas se comprueban contra el fichero al añadirlo al {@link LroeBatch}.
 */
public final class OtherTaxInformation {

    static final int MAX_PROPERTIES = 1000;
    private static final int MAX_AGREEMENT = 15;
    private static final int MAX_EXTERNAL_REFERENCE = 60;
    private static final int MAX_CADASTRAL_REFERENCE = 25;
    private static final int MAX_NAME = 120;

    /** {@code SituacionInmueble}. */
    public enum PropertySituation {
        /** Con referencia catastral, en España salvo País Vasco y Navarra. */
        SPAIN_WITH_CADASTRAL_REFERENCE("1", true),
        /** Con referencia catastral o número fijo, en el País Vasco o Navarra. */
        BASQUE_COUNTRY_OR_NAVARRE("2", true),
        /** En cualquiera de las situaciones anteriores, sin referencia catastral ni número fijo. */
        WITHOUT_CADASTRAL_REFERENCE("3", false),
        /** En el extranjero. */
        ABROAD("4", false);

        private final String code;
        private final boolean requiresReference;

        PropertySituation(String code, boolean requiresReference) {
            this.code = code;
            this.requiresReference = requiresReference;
        }

        public String code() {
            return code;
        }
    }

    /** {@code DetalleInmueble}. */
    public static final class Property {
        private final PropertySituation situation;
        private final String cadastralReference;

        private Property(PropertySituation situation, String cadastralReference) {
            this.situation = situation;
            this.cadastralReference = cadastralReference;
        }

        public PropertySituation getSituation() {
            return situation;
        }

        public String getCadastralReference() {
            return cadastralReference;
        }
    }

    private String representativeNif;
    private final List<Property> properties = new ArrayList<>();
    private BigDecimal propertyTransferSubjectToVat;
    private String billingAgreementNumber;
    private String externalReference;
    private Boolean coupon;
    private String succeededEntityNif;
    private String succeededEntityName;
    private Boolean additionalProvisionsTwoAndFive;
    private Boolean hydrocarbonsFromDeposit;

    private OtherTaxInformation() {
    }

    public static OtherTaxInformation create() {
        return new OtherTaxInformation();
    }

    /** {@code NIFRepresentanteDeclarado}: NIF del representante del destinatario. */
    public OtherTaxInformation representativeNif(String nif) {
        this.representativeNif = nif;
        return this;
    }

    /**
     * Añade un inmueble ({@code Inmuebles/DetalleInmueble}, hasta 1.000).
     *
     * @throws IllegalArgumentException si falta la referencia catastral en las situaciones 1 y 2 ({@code B4_2000300})
     */
    public OtherTaxInformation property(PropertySituation situation, String cadastralReference) {
        Objects.requireNonNull(situation, "situation");
        boolean blank = cadastralReference == null || cadastralReference.isBlank();
        if (situation.requiresReference && blank) {
            throw new IllegalArgumentException("La referencia catastral es obligatoria en la situación " + situation.code()
                    + " (B4_2000300)");
        }
        maxLength("ReferenciaCatastral", cadastralReference, MAX_CADASTRAL_REFERENCE);
        if (properties.size() == MAX_PROPERTIES) {
            throw new IllegalArgumentException("Como máximo 1000 inmuebles por factura");
        }
        properties.add(new Property(situation, blank ? null : cadastralReference));
        return this;
    }

    /** {@code ImporteTransmisionInmueblesSujetoAIVA}. */
    public OtherTaxInformation propertyTransferSubjectToVat(BigDecimal amount) {
        this.propertyTransferSubjectToVat = amount;
        return this;
    }

    /** {@code NumRegistroAcuerdoFacturacion}: número de registro del acuerdo de facturación (hasta 15 caracteres). */
    public OtherTaxInformation billingAgreementNumber(String number) {
        maxLength("NumRegistroAcuerdoFacturacion", number, MAX_AGREEMENT);
        this.billingAgreementNumber = number;
        return this;
    }

    /** {@code ReferenciaExterna}: referencia propia del obligado (hasta 60 caracteres). */
    public OtherTaxInformation externalReference(String reference) {
        maxLength("ReferenciaExterna", reference, MAX_EXTERNAL_REFERENCE);
        this.externalReference = reference;
        return this;
    }

    /** {@code Cupon}: solo puede ser {@code true} en una rectificativa R1 o R5 ({@code B4_2000301}). */
    public OtherTaxInformation coupon(boolean coupon) {
        this.coupon = coupon;
        return this;
    }

    /** {@code EntidadSucedida}: la entidad a la que sucede el obligado (operaciones de reestructuración). */
    public OtherTaxInformation succeededEntity(String nif, String name) {
        maxLength("EntidadSucedida/NombreRazon", name, MAX_NAME);
        this.succeededEntityNif = Objects.requireNonNull(nif, "nif");
        this.succeededEntityName = Objects.requireNonNull(name, "name");
        return this;
    }

    /**
     * {@code FacturacionDispAdicionalSegundaYQuinta}: facturación según las disposiciones adicionales segunda y quinta.
     * Solo puede ser {@code true} si la factura no la emite un tercero ni el destinatario ({@code B4_2000302}).
     */
    public OtherTaxInformation additionalProvisionsTwoAndFive(boolean value) {
        this.additionalProvisionsTwoAndFive = value;
        return this;
    }

    /** {@code HidrocarburosExtraidosDeposito}: hidrocarburos extraídos de un depósito. */
    public OtherTaxInformation hydrocarbonsFromDeposit(boolean value) {
        this.hydrocarbonsFromDeposit = value;
        return this;
    }

    public String getRepresentativeNif() {
        return representativeNif;
    }

    public List<Property> getProperties() {
        return Collections.unmodifiableList(properties);
    }

    public BigDecimal getPropertyTransferSubjectToVat() {
        return propertyTransferSubjectToVat;
    }

    public String getBillingAgreementNumber() {
        return billingAgreementNumber;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public Boolean getCoupon() {
        return coupon;
    }

    public String getSucceededEntityNif() {
        return succeededEntityNif;
    }

    public String getSucceededEntityName() {
        return succeededEntityName;
    }

    public Boolean getAdditionalProvisionsTwoAndFive() {
        return additionalProvisionsTwoAndFive;
    }

    public Boolean getHydrocarbonsFromDeposit() {
        return hydrocarbonsFromDeposit;
    }

    /** Sin ningún campo informado. */
    public boolean isEmpty() {
        return representativeNif == null && properties.isEmpty() && propertyTransferSubjectToVat == null
                && billingAgreementNumber == null && externalReference == null && coupon == null && succeededEntityNif == null
                && additionalProvisionsTwoAndFive == null && hydrocarbonsFromDeposit == null;
    }

    private static void maxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " admite como máximo " + max + " caracteres");
        }
    }
}
