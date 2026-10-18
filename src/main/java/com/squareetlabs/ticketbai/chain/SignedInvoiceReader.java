package com.squareetlabs.ticketbai.chain;

import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Lee de un fichero de alta TicketBAI firmado los datos de {@link SignedInvoice}. */
public final class SignedInvoiceReader {

    private SignedInvoiceReader() {
    }

    public static SignedInvoice read(byte[] signedXml) {
        Element root = Xml.parse(signedXml).getDocumentElement();
        if (!"TicketBai".equals(root.getLocalName())) {
            throw new IllegalArgumentException("No es un fichero de alta TicketBAI: raíz " + root.getLocalName());
        }
        Element issuer = Xml.firstChild(root, "Emisor");
        Element header = Xml.firstChild(root, "CabeceraFactura");
        if (issuer == null || header == null) {
            throw new IllegalArgumentException("Faltan Emisor o CabeceraFactura");
        }
        NodeList signatureValues = root.getElementsByTagNameNS(Xml.DSIG_NS, "SignatureValue");
        if (signatureValues.getLength() != 1) {
            throw new IllegalArgumentException("El fichero tiene que llevar exactamente una firma");
        }
        String signatureValue = signatureValues.item(0).getTextContent().replaceAll("\\s", "");

        ChainLink previous = null;
        Element chain = Xml.firstChild(root, "EncadenamientoFacturaAnterior");
        if (chain != null) {
            previous = ChainLink.ofPrefix(Xml.text(chain, "SerieFacturaAnterior"),
                    Xml.requiredText(chain, "NumFacturaAnterior"),
                    Dates.parse(Xml.requiredText(chain, "FechaExpedicionFacturaAnterior")),
                    Xml.requiredText(chain, "SignatureValueFirmaFacturaAnterior"));
        }
        return new SignedInvoice(Xml.requiredText(issuer, "NIF"), Xml.text(header, "SerieFactura"),
                Xml.requiredText(header, "NumFactura"), Dates.parse(Xml.requiredText(header, "FechaExpedicionFactura")),
                signatureValue, previous);
    }
}
