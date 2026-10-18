package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.util.XmlWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Base64;

/**
 * XML del envío LROE del modelo 240, subcapítulo 1.1 (alta {@code A00} o anulación {@code AN0}), con cada fichero
 * TicketBAI en Base64. Sin comprimir: el cliente lo comprime con GZIP.
 */
public final class LroeRequestBuilder {

    static final String NS_BASE = "https://www.batuz.eus/fitxategiak/batuz/LROE/esquemas/";
    static final String INVOICES_NS = NS_BASE + "LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AltaPeticion_V1_0_2.xsd";
    static final String ANNULMENTS_NS = NS_BASE + "LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AnulacionPeticion_V1_0_0.xsd";

    private LroeRequestBuilder() {
    }

    public static Document build(LroeBatch batch) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            doc = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        boolean invoices = batch.getOperation() == LroeOperation.INVOICES;
        Element root = invoices
                ? doc.createElementNS(INVOICES_NS, "lrpjfecsgap:LROEPJ240FacturasEmitidasConSGAltaPeticion")
                : doc.createElementNS(ANNULMENTS_NS, "lrpjfecsgan:LROEPJ240FacturasEmitidasConSGAnulacionPeticion");
        doc.appendChild(root);

        Element header = add(doc, root, "Cabecera");
        text(doc, header, "Modelo", "240");
        text(doc, header, "Capitulo", "1");
        text(doc, header, "Subcapitulo", "1.1");
        text(doc, header, "Operacion", batch.getOperation().code());
        text(doc, header, "Version", "1.0");
        text(doc, header, "Ejercicio", String.valueOf(batch.getFiscalYear().getValue()));
        Element taxpayer = add(doc, header, "ObligadoTributario");
        text(doc, taxpayer, "NIF", batch.getNif());
        text(doc, taxpayer, "ApellidosNombreRazonSocial", batch.getName());

        Element list = add(doc, root, "FacturasEmitidas");
        for (byte[] file : batch.getFiles()) {
            Element item = add(doc, list, "FacturaEmitida");
            text(doc, item, invoices ? "TicketBai" : "AnulacionTicketBai", Base64.getEncoder().encodeToString(file));
        }
        return doc;
    }

    public static byte[] toBytes(LroeBatch batch) {
        return XmlWriter.toBytes(build(batch));
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
