package com.squareetlabs.ticketbai;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.identifier.QrUrlBuilder;
import com.squareetlabs.ticketbai.identifier.TbaiIdentifier;
import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import com.squareetlabs.ticketbai.xml.AnnulmentXmlBuilder;
import com.squareetlabs.ticketbai.xml.InvoiceXmlBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Punto de entrada del SDK para un territorio y un entorno: construye, firma y valida los ficheros TicketBAI de un
 * emisor. No envía nada a Hacienda (eso lo hacen los clientes de transporte).
 *
 * <pre>{@code
 * TicketBai tbai = TicketBai.builder()
 *         .territory(Territory.GIPUZKOA).environment(Environment.TEST)
 *         .software(software).signer(signer)
 *         .build();
 * IssuedInvoice issued = tbai.issue(invoice, previousLink);   // previousLink: null en la primera factura
 * }</pre>
 * <p>
 * El encadenamiento es responsabilidad de quien llama: tiene que guardar {@link IssuedInvoice#getChainLink()} y pasarlo
 * a la siguiente factura del emisor, en orden y sin emitir dos facturas del mismo emisor a la vez.
 */
public final class TicketBai {

    private final Territory territory;
    private final Environment environment;
    private final Software software;
    private final String deviceSerial;
    private final TicketBaiSigner signer;

    private TicketBai(Builder b) {
        this.territory = Objects.requireNonNull(b.territory, "territory");
        this.environment = Objects.requireNonNull(b.environment, "environment");
        this.software = Objects.requireNonNull(b.software, "software");
        this.deviceSerial = b.deviceSerial;
        this.signer = Objects.requireNonNull(b.signer, "signer");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Construye y firma el fichero de alta, comprueba que cumple el XSD y calcula el código TicketBAI y el QR.
     *
     * @param previous eslabón de la factura anterior del mismo emisor, o {@code null} si es la primera
     * @throws IllegalStateException si el fichero firmado no cumple el XSD (datos que el modelo no puede impedir, como
     *                               un texto demasiado largo)
     */
    public IssuedInvoice issue(Invoice invoice, ChainLink previous) {
        SignedXml signed = signer.sign(InvoiceXmlBuilder.build(invoice, previous, software, deviceSerial), territory);
        requireValid(signed, XsdValidator.Schema.INVOICE);
        TbaiIdentifier identifier = TbaiIdentifier.of(invoice.getIssuer().getNif(), invoice.getIssueDate(),
                signed.getSignatureValue());
        String qrUrl = QrUrlBuilder.build(territory, environment, identifier, invoice.getSeries(), invoice.getNumber(),
                invoice.getTotalAmount());
        ChainLink link = ChainLink.of(invoice.getSeries(), invoice.getNumber(), invoice.getIssueDate(),
                signed.getSignatureValue());
        return new IssuedInvoice(signed, identifier, qrUrl, link);
    }

    /** Construye y firma el fichero de anulación y comprueba que cumple el XSD. */
    public SignedXml annul(Annulment annulment) {
        SignedXml signed = signer.sign(AnnulmentXmlBuilder.build(annulment, software, deviceSerial), territory);
        requireValid(signed, XsdValidator.Schema.ANNULMENT);
        return signed;
    }

    public Territory getTerritory() {
        return territory;
    }

    public Environment getEnvironment() {
        return environment;
    }

    private static void requireValid(SignedXml signed, XsdValidator.Schema schema) {
        List<String> errors = XsdValidator.validate(signed.getBytes(), schema);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("El fichero TicketBAI no cumple el XSD: " + String.join("; ", errors));
        }
    }

    /** Constructor de {@link TicketBai}. */
    public static final class Builder {
        private Territory territory;
        private Environment environment;
        private Software software;
        private String deviceSerial;
        private TicketBaiSigner signer;

        private Builder() {
        }

        public Builder territory(Territory territory) {
            this.territory = territory;
            return this;
        }

        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        public Builder software(Software software) {
            this.software = software;
            return this;
        }

        /** {@code NumSerieDispositivo}, opcional. */
        public Builder deviceSerial(String deviceSerial) {
            this.deviceSerial = deviceSerial;
            return this;
        }

        public Builder signer(TicketBaiSigner signer) {
            this.signer = signer;
            return this;
        }

        public TicketBai build() {
            return new TicketBai(this);
        }
    }
}
