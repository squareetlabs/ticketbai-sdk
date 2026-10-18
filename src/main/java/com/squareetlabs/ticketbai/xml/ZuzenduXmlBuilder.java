package com.squareetlabs.ticketbai.xml;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.model.ZuzenduAction;
import com.squareetlabs.ticketbai.util.Amounts;
import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Ficheros Zuzendu (Gipuzkoa y Álava) para corregir un fichero ya enviado. <b>No se firman</b>: llevan la misma
 * {@code HuellaTBAI} del fichero original y los 100 primeros caracteres de su firma, que los identifican. El fichero
 * original se conserva sin cambios.
 */
public final class ZuzenduXmlBuilder {

    public static final String ALTA_NS = "urn:ticketbai:zuzendu-alta";
    public static final String BAJA_NS = "urn:ticketbai:zuzendu-baja";
    private static final String VERSION = "1.0";

    private ZuzenduXmlBuilder() {
    }

    /**
     * Subsanación o modificación de un fichero de alta.
     *
     * @param corrected      factura con los datos corregidos: mismo emisor, serie, número y fecha que el original y, con
     *                       {@link ZuzenduAction#MODIFICAR}, el mismo importe total
     * @param originalSigned fichero de alta original firmado
     */
    public static Document invoiceCorrection(Invoice corrected, byte[] originalSigned, ZuzenduAction action) {
        Objects.requireNonNull(corrected, "corrected");
        Objects.requireNonNull(action, "action");
        Element original = root(originalSigned, "TicketBai");
        Element header = Xml.firstChild(original, "CabeceraFactura");
        same("el NIF del emisor", Xml.requiredText(Xml.firstChild(original, "Emisor"), "NIF"), corrected.getIssuer().getNif());
        same("la serie", emptyToNull(Xml.text(header, "SerieFactura")), emptyToNull(corrected.getSeries()));
        same("el número", Xml.requiredText(header, "NumFactura"), corrected.getNumber());
        same("la fecha de expedición", Xml.requiredText(header, "FechaExpedicionFactura"), Dates.format(corrected.getIssueDate()));
        if (action == ZuzenduAction.MODIFICAR) {
            BigDecimal originalTotal = new BigDecimal(Xml.requiredText(original, "ImporteTotalFactura"));
            if (originalTotal.compareTo(corrected.getTotalAmount()) != 0) {
                throw new IllegalArgumentException("Al modificar no se puede cambiar el importe total ("
                        + originalTotal.toPlainString() + " → " + Amounts.format(corrected.getTotalAmount())
                        + "): hace falta una factura rectificativa");
            }
        }

        // Sujetos y Factura salen de la factura corregida; la huella, del original sin cambios.
        Document full = InvoiceXmlBuilder.build(corrected, null, PLACEHOLDER_SOFTWARE, null);
        Document doc = newDocument();
        Element root = doc.createElementNS(ALTA_NS, "T:SubsanacionModificacionTicketBAI");
        doc.appendChild(root);
        Element cabecera = add(doc, root, "Cabecera");
        text(doc, cabecera, "IDVersion", VERSION);
        text(doc, cabecera, "Accion", action.name());
        root.appendChild(doc.importNode(Xml.firstChild(full.getDocumentElement(), "Sujetos"), true));
        root.appendChild(doc.importNode(Xml.firstChild(full.getDocumentElement(), "Factura"), true));
        root.appendChild(doc.importNode(Xml.firstChild(original, "HuellaTBAI"), true));
        text(doc, root, "SignatureValueFirmaFactura", signaturePrefix(original));
        return doc;
    }

    /**
     * Subsanación de un fichero de anulación rechazado.
     *
     * @param corrected      datos corregidos de la factura a anular, o {@code null} para reenviar los del original
     * @param originalSigned fichero de anulación original firmado
     */
    public static Document annulmentCorrection(Annulment corrected, byte[] originalSigned) {
        Element original = root(originalSigned, "AnulaTicketBai");
        Document doc = newDocument();
        Element root = doc.createElementNS(BAJA_NS, "T:SubsanacionAnulacionTicketBAI");
        doc.appendChild(root);
        text(doc, add(doc, root, "Cabecera"), "IDVersion", VERSION);
        if (corrected == null) {
            root.appendChild(doc.importNode(Xml.firstChild(original, "IDFactura"), true));
        } else {
            Document built = AnnulmentXmlBuilder.build(corrected, PLACEHOLDER_SOFTWARE, null);
            root.appendChild(doc.importNode(Xml.firstChild(built.getDocumentElement(), "IDFactura"), true));
        }
        root.appendChild(doc.importNode(Xml.firstChild(original, "HuellaTBAI"), true));
        text(doc, root, "SignatureValueFirmaAnulacion", signaturePrefix(original));
        return doc;
    }

    /** Solo para construir Sujetos e IDFactura: su huella no se usa (se copia la del original). */
    private static final Software PLACEHOLDER_SOFTWARE = new Software("-",
            com.squareetlabs.ticketbai.model.Party.withNif("-", null), "-", "-");

    private static Element root(byte[] signed, String expected) {
        Element root = Xml.parse(Objects.requireNonNull(signed, "originalSigned")).getDocumentElement();
        if (!expected.equals(root.getLocalName())) {
            throw new IllegalArgumentException("El fichero original tiene que ser " + expected + ", no " + root.getLocalName());
        }
        return root;
    }

    private static String signaturePrefix(Element original) {
        NodeList values = original.getElementsByTagNameNS(Xml.DSIG_NS, "SignatureValue");
        if (values.getLength() != 1) {
            throw new IllegalArgumentException("El fichero original tiene que estar firmado");
        }
        String signature = values.item(0).getTextContent().replaceAll("\\s", "");
        return signature.substring(0, Math.min(ChainLink.SIGNATURE_PREFIX_LENGTH, signature.length()));
    }

    private static void same(String field, String original, String corrected) {
        if (!Objects.equals(original, corrected)) {
            throw new IllegalArgumentException("Zuzendu no puede cambiar " + field + " de la factura: «" + original
                    + "» en el original y «" + corrected + "» en la corrección");
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element add(Document doc, Node parent, String name) {
        Element element = doc.createElementNS(null, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document doc, Node parent, String name, String value) {
        add(doc, parent, name).setTextContent(value);
    }
}
