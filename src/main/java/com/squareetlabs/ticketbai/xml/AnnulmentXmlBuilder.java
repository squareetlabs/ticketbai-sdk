package com.squareetlabs.ticketbai.xml;

import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.util.Dates;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Objects;

/** Construye el fichero de anulación {@code AnulaTicketBai} (XSD 1.2.2) <b>sin firmar</b>. */
public final class AnnulmentXmlBuilder {

    private AnnulmentXmlBuilder() {
    }

    public static Document build(Annulment annulment, Software software, String deviceSerial) {
        Objects.requireNonNull(annulment, "annulment");
        Objects.requireNonNull(software, "software");
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element root = doc.createElementNS(TicketBaiXml.ANNULMENT_NS, TicketBaiXml.PREFIX + ":AnulaTicketBai");
        doc.appendChild(root);

        text(doc, add(doc, root, "Cabecera"), "IDVersionTBAI", TicketBaiXml.VERSION);
        Element id = add(doc, root, "IDFactura");
        Element issuer = add(doc, id, "Emisor");
        text(doc, issuer, "NIF", annulment.getIssuer().getNif());
        text(doc, issuer, "ApellidosNombreRazonSocial", annulment.getIssuer().getName());
        Element header = add(doc, id, "CabeceraFactura");
        if (annulment.getInvoice().getSeries() != null && !annulment.getInvoice().getSeries().isBlank()) {
            text(doc, header, "SerieFactura", annulment.getInvoice().getSeries());
        }
        text(doc, header, "NumFactura", annulment.getInvoice().getNumber());
        text(doc, header, "FechaExpedicionFactura", Dates.format(annulment.getInvoice().getIssueDate()));

        Element fingerprint = add(doc, root, "HuellaTBAI");
        Element sw = add(doc, fingerprint, "Software");
        text(doc, sw, "LicenciaTBAI", software.getLicense());
        Element developer = add(doc, sw, "EntidadDesarrolladora");
        Party party = software.getDeveloper();
        if (party.hasNif()) {
            text(doc, developer, "NIF", party.getNif());
        } else {
            Element other = add(doc, developer, "IDOtro");
            if (party.getCountryCode() != null) {
                text(doc, other, "CodigoPais", party.getCountryCode());
            }
            text(doc, other, "IDType", party.getOtherIdType().code());
            text(doc, other, "ID", party.getOtherId());
        }
        text(doc, sw, "Nombre", software.getName());
        text(doc, sw, "Version", software.getVersion());
        if (deviceSerial != null && !deviceSerial.isBlank()) {
            text(doc, fingerprint, "NumSerieDispositivo", deviceSerial);
        }
        return doc;
    }

    private static Element add(Document doc, Element parent, String name) {
        Element element = doc.createElementNS(null, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document doc, Element parent, String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta el valor obligatorio " + name);
        }
        add(doc, parent, name).setTextContent(value);
    }
}
