package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.XmlWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** XML de la consulta {@code C00} del subcapítulo 1.1 (modelos 240 y 140). */
public final class LroeQueryBuilder {

    private LroeQueryBuilder() {
    }

    public static Document build(LroeQuery query) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        boolean model140 = query.getModel() == LroeModel.MODEL_140;
        Element root = model140
                ? doc.createElementNS(LroeRequestBuilder.NS_BASE + "LROE_PF_140_1_1_Ingresos_ConfacturaConSG_ConsultaPeticion_V1_0_0.xsd",
                "lrpficfcsgcp:LROEPF140IngresosConFacturaConSGConsultaPeticion")
                : doc.createElementNS(LroeRequestBuilder.NS_BASE + "LROE_PJ_240_1_1_FacturasEmitidas_ConSG_ConsultaPeticion_V1_0_0.xsd",
                "lrpjfecsgcp:LROEPJ240FacturasEmitidasConSGConsultaPeticion");
        doc.appendChild(root);

        Element header = add(doc, root, "Cabecera");
        text(doc, header, "Modelo", query.getModel().code());
        text(doc, header, "Capitulo", "1");
        text(doc, header, "Subcapitulo", "1.1");
        text(doc, header, "Operacion", "C00");
        text(doc, header, "Version", "1.0");
        text(doc, header, "Ejercicio", String.valueOf(query.getFiscalYear().getValue()));
        Element taxpayer = add(doc, header, "ObligadoTributario");
        text(doc, taxpayer, "NIF", query.getTaxpayer().getNif());
        text(doc, taxpayer, "ApellidosNombreRazonSocial", query.getTaxpayer().getFullName());

        Element filter = add(doc, root, model140 ? "FiltroConsultaIngresosConSG" : "FiltroConsultaFacturasEmitidasConSG");
        if (query.getRecipientNif() != null) {
            text(doc, add(doc, filter, "Destinatario"), "NIF", query.getRecipientNif());
        }
        if (query.getSeries() != null || query.getNumber() != null || query.getIssuedFrom() != null
                || query.getIssuedTo() != null) {
            Element invoice = add(doc, filter, "CabeceraFactura");
            if (query.getSeries() != null) {
                text(doc, invoice, "SerieFactura", query.getSeries());
            }
            if (query.getNumber() != null) {
                text(doc, invoice, "NumFactura", query.getNumber());
            }
            if (query.getIssuedFrom() != null || query.getIssuedTo() != null) {
                Element dates = add(doc, invoice, "FechaExpedicionFactura");
                if (query.getIssuedFrom() != null) {
                    text(doc, dates, "Desde", Dates.format(query.getIssuedFrom()));
                }
                if (query.getIssuedTo() != null) {
                    text(doc, dates, "Hasta", Dates.format(query.getIssuedTo()));
                }
            }
        }
        if (query.getStatus() != null) {
            text(doc, filter, "Estado", query.getStatus().code());
        }
        text(doc, filter, "NumPaginaConsulta", String.valueOf(query.getPage()));
        return doc;
    }

    public static byte[] toBytes(LroeQuery query) {
        return XmlWriter.toBytes(build(query));
    }

    private static Element add(Document doc, Element parent, String name) {
        Element element = doc.createElementNS(null, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document doc, Element parent, String name, String value) {
        add(doc, parent, name).setTextContent(value);
    }
}
