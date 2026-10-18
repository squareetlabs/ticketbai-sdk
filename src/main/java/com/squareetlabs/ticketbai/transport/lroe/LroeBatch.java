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
 * alta o modificación lleva además sus datos de renta.
 */
public final class LroeBatch {

    public static final int MAX_FILES = 1000;
    private static final int MAX_INCOME_DETAILS = 10;

    /** Un fichero del envío y, en el 140, sus datos de renta. */
    public static final class Entry {
        private final byte[] file;
        private final List<IncomeDetail> incomeDetails;

        Entry(byte[] file, List<IncomeDetail> incomeDetails) {
            this.file = file;
            this.incomeDetails = incomeDetails;
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
            if (entries.size() == MAX_FILES) {
                throw new IllegalArgumentException("Un envío LROE lleva como máximo 1000 ficheros");
            }
            entries.add(new Entry(content, details));
            return this;
        }

        public LroeBatch build() {
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Un envío LROE lleva de 1 a 1000 ficheros: 0");
            }
            return new LroeBatch(this);
        }
    }
}
