package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Envío al LROE de Bizkaia (subcapítulo 1.1): ficheros TicketBAI firmados de <b>un solo obligado</b>, un solo modelo,
 * ejercicio y operación, hasta 1.000 por envío. Los ficheros van tal cual, en Base64. En el modelo 140 cada ingreso de
 * alta o modificación lleva además sus datos de renta; en el 240, cada factura puede llevar
 * {@link OtherTaxInformation otra información con trascendencia tributaria}.
 */
public final class LroeBatch {

    public static final int MAX_FILES = 1000;
    private static final int MAX_INCOME_DETAILS = 10;

    /** Un fichero del envío y, en el 140, sus datos de renta o, en el 240, su otra información tributaria. */
    public static final class Entry {
        private final byte[] file;
        private final List<IncomeDetail> incomeDetails;
        private final OtherTaxInformation otherTaxInformation;

        Entry(byte[] file, List<IncomeDetail> incomeDetails, OtherTaxInformation otherTaxInformation) {
            this.file = file;
            this.incomeDetails = incomeDetails;
            this.otherTaxInformation = otherTaxInformation;
        }

        /** Otra información con trascendencia tributaria (modelo 240), o {@code null}. */
        public OtherTaxInformation getOtherTaxInformation() {
            return otherTaxInformation;
        }

        public byte[] getFile() {
            return file.clone();
        }

        public List<IncomeDetail> getIncomeDetails() {
            return incomeDetails;
        }
    }

    private final LroeModel model;
    private final LroeTaxpayer taxpayer;
    private final Year fiscalYear;
    private final LroeOperation operation;
    private final List<Entry> entries;

    private LroeBatch(Builder b) {
        this.model = b.model;
        this.taxpayer = b.taxpayer;
        this.fiscalYear = b.fiscalYear;
        this.operation = b.operation;
        this.entries = Collections.unmodifiableList(new ArrayList<>(b.entries));
    }

    public static Builder builder(LroeModel model, LroeTaxpayer taxpayer, Year fiscalYear, LroeOperation operation) {
        return new Builder(model, taxpayer, fiscalYear, operation);
    }

    /** Atajo para el modelo 240 (persona jurídica) sin datos adicionales. */
    public static LroeBatch of(String nif, String name, Year fiscalYear, LroeOperation operation, List<SignedXml> files) {
        Builder builder = builder(LroeModel.MODEL_240, LroeTaxpayer.legalEntity(nif, name), fiscalYear, operation);
        Objects.requireNonNull(files, "files").forEach(builder::add);
        return builder.build();
    }

    public LroeModel getModel() {
        return model;
    }

    public LroeTaxpayer getTaxpayer() {
        return taxpayer;
    }

    public Year getFiscalYear() {
        return fiscalYear;
    }

    public LroeOperation getOperation() {
        return operation;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** Constructor de {@link LroeBatch}. */
    public static final class Builder {
        private final LroeModel model;
        private final LroeTaxpayer taxpayer;
        private final Year fiscalYear;
        private final LroeOperation operation;
        private final List<Entry> entries = new ArrayList<>();

        private Builder(LroeModel model, LroeTaxpayer taxpayer, Year fiscalYear, LroeOperation operation) {
            this.model = Objects.requireNonNull(model, "model");
            this.taxpayer = Objects.requireNonNull(taxpayer, "taxpayer");
            this.fiscalYear = Objects.requireNonNull(fiscalYear, "fiscalYear");
            this.operation = Objects.requireNonNull(operation, "operation");
            if (model == LroeModel.MODEL_140 && !taxpayer.isIndividual()) {
                throw new IllegalArgumentException("El modelo 140 es de personas físicas: usa LroeTaxpayer.individual");
            }
            if (model == LroeModel.MODEL_240 && taxpayer.isIndividual()) {
                throw new IllegalArgumentException("El modelo 240 es de personas jurídicas: usa LroeTaxpayer.legalEntity");
            }
        }

        /**
         * Añade un fichero firmado: {@code TicketBai} para el alta y la modificación, {@code AnulaTicketBai} para la
         * anulación. En el modelo 140 (alta y modificación) hay que pasar de 1 a 10 detalles de renta.
         */
        public Builder add(SignedXml file, IncomeDetail... incomeDetails) {
            return addEntry(file, null, incomeDetails);
        }

        /**
         * Añade una factura del modelo 240 (alta o modificación) con su otra información con trascendencia tributaria.
         *
         * @throws IllegalArgumentException si el cupón o la facturación de las disposiciones adicionales segunda y quinta
         *                                  no son compatibles con el fichero ({@code B4_2000301}, {@code B4_2000302})
         */
        public Builder add(SignedXml file, OtherTaxInformation otherTaxInformation) {
            Objects.requireNonNull(otherTaxInformation, "otherTaxInformation");
            if (model != LroeModel.MODEL_240 || operation == LroeOperation.ANNULMENTS) {
                throw new IllegalArgumentException("La otra información tributaria solo va en el alta y la modificación del modelo 240");
            }
            return addEntry(file, otherTaxInformation.isEmpty() ? null : otherTaxInformation);
        }

        private Builder addEntry(SignedXml file, OtherTaxInformation otherTaxInformation, IncomeDetail... incomeDetails) {
            Objects.requireNonNull(file, "file");
            byte[] content = file.getBytes();
            String expectedRoot = operation == LroeOperation.ANNULMENTS ? "AnulaTicketBai" : "TicketBai";
            Element root = Xml.parse(content).getDocumentElement();
            if (!expectedRoot.equals(root.getLocalName())) {
                throw new IllegalArgumentException("Para " + operation + " cada fichero tiene que ser " + expectedRoot
                        + ", no " + root.getLocalName());
            }
            String issuer = Xml.requiredText(Xml.firstChild(root, "Emisor"), "NIF");
            if (!taxpayer.getNif().equals(issuer)) {
                throw new IllegalArgumentException("Un envío LROE es de un solo obligado: " + issuer + " no es "
                        + taxpayer.getNif());
            }
            List<IncomeDetail> details = incomeDetails == null ? List.of() : List.copyOf(Arrays.asList(incomeDetails));
            boolean needsIncome = model == LroeModel.MODEL_140 && operation != LroeOperation.ANNULMENTS;
            if (needsIncome && (details.isEmpty() || details.size() > MAX_INCOME_DETAILS)) {
                throw new IllegalArgumentException("En el modelo 140 cada ingreso lleva de 1 a 10 detalles de renta");
            }
            if (!needsIncome && !details.isEmpty()) {
                throw new IllegalArgumentException("Los datos de renta solo van en el alta y la modificación del modelo 140");
            }
            if (otherTaxInformation != null) {
                checkAgainstFile(otherTaxInformation, root);
            }
            if (entries.size() == MAX_FILES) {
                throw new IllegalArgumentException("Un envío LROE lleva como máximo 1000 ficheros");
            }
            entries.add(new Entry(content, details, otherTaxInformation));
            return this;
        }

        private static void checkAgainstFile(OtherTaxInformation info, Element ticketBai) {
            if (Boolean.TRUE.equals(info.getCoupon())) {
                Element rectification = Xml.firstChild(ticketBai, "FacturaRectificativa");
                String code = rectification == null ? null : Xml.text(rectification, "Codigo");
                if (!"R1".equals(code) && !"R5".equals(code)) {
                    throw new IllegalArgumentException("El cupón solo puede ser S en una rectificativa R1 o R5 (B4_2000301)");
                }
            }
            if (Boolean.TRUE.equals(info.getAdditionalProvisionsTwoAndFive())) {
                String issuedBy = Xml.text(ticketBai, "EmitidaPorTercerosODestinatario");
                if ("T".equals(issuedBy) || "D".equals(issuedBy)) {
                    throw new IllegalArgumentException("La facturación de las disposiciones adicionales segunda y quinta no puede "
                            + "ser S en una factura emitida por un tercero o por el destinatario (B4_2000302)");
                }
            }
        }

        public LroeBatch build() {
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Un envío LROE lleva de 1 a 1000 ficheros: 0");
            }
            return new LroeBatch(this);
        }
    }
}
