package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.XmlWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** XML (sin firma) de las peticiones Kontsulta: facturas enviadas y última factura. */
public final class KontsultaXmlBuilder {

    static final String NS_QUERY = "urn:ticketbai:kontsulta";
    static final String NS_LATEST = "urn:ticketbai:kontsultaultima";
    /** {@code FechaHoraType}: dd-MM-aaaa HH:mm:ss. */
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm:ss");

    private KontsultaXmlBuilder() {
    }

    public static Document query(KontsultaQuery query) {
        Document doc = newDocument();
        Element root = doc.createElementNS(NS_QUERY, "T:KontsultaTicketBAI");
        doc.appendChild(root);
        Element header = add(doc, root, "Cabecera");
        text(doc, header, "IDVersion", "1.0");
        text(doc, header, "Ejercicio", String.valueOf(query.getFiscalYear().getValue()));
        text(doc, header, "Periodo", query.getPeriod().code());
        text(doc, header, "NifEmisor", query.getIssuerNif());

        Element filter = add(doc, root, "FiltroConsulta");
        if (query.getSeries() != null || query.getNumber() != null || query.getIssuedFrom() != null || query.getIssuedTo() != null
                || query.getOperationFrom() != null || query.getOperationTo() != null || query.getSubmittedFrom() != null
                || query.getSubmittedTo() != null) {
            Element invoice = add(doc, filter, "CabeceraFactura");
            optional(doc, invoice, "SerieFactura", query.getSeries());
            optional(doc, invoice, "NumFactura", query.getNumber());
            dateTimeRange(doc, invoice, "FechaHoraExpedicionFactura", query.getIssuedFrom(), query.getIssuedTo());
            dateRange(doc, invoice, "FechaOperacionFactura", query.getOperationFrom(), query.getOperationTo());
            dateTimeRange(doc, invoice, "FechaHoraPresentacionFactura", query.getSubmittedFrom(), query.getSubmittedTo());
        }
        if (query.getSubmissionStatus() != null) {
            text(doc, filter, "EstadoEnvio", query.getSubmissionStatus().code());
        }
        if (query.getRecordStatus() != null) {
            text(doc, filter, "EstadoRegistro", query.getRecordStatus().code());
        }
        if (query.getShowErrors() != null) {
            text(doc, filter, "MostrarErrores", query.getShowErrors() ? "S" : "N");
        }
        text(doc, filter, "NumPaginaConsulta", String.valueOf(query.getPage()));
        return doc;
    }

    /** Petición de la última factura admitida de un emisor. */
    public static Document latest(String issuerNif) {
        Document doc = newDocument();
        Element root = doc.createElementNS(NS_LATEST, "T:KontsultaUltimoTicketBAI");
        doc.appendChild(root);
        Element header = add(doc, root, "Cabecera");
        text(doc, header, "IDVersion", "1.0");
        text(doc, header, "NifEmisor", issuerNif);
        return doc;
    }

    public static byte[] toBytes(Document doc) {
        return XmlWriter.toBytes(doc);
    }

    private static void dateTimeRange(Document doc, Element parent, String name, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return;
        }
        Element range = add(doc, parent, name);
        optional(doc, range, "DesdeFechaHora", from == null ? null : DATE_TIME.format(from));
        optional(doc, range, "HastaFechaHora", to == null ? null : DATE_TIME.format(to));
    }

    private static void dateRange(Document doc, Element parent, String name, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return;
        }
        Element range = add(doc, parent, name);
        optional(doc, range, "DesdeFecha", from == null ? null : Dates.format(from));
        optional(doc, range, "HastaFecha", to == null ? null : Dates.format(to));
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

    private static Element add(Document doc, Element parent, String name) {
        Element element = doc.createElementNS(null, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document doc, Element parent, String name, String value) {
        add(doc, parent, name).setTextContent(value);
    }

    private static void optional(Document doc, Element parent, String name, String value) {
        if (value != null) {
            text(doc, parent, name, value);
        }
    }
}
