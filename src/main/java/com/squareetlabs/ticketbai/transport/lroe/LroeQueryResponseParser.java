package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Lee la respuesta de la consulta del LROE (modelos 240 y 140), sin depender de su espacio de nombres. */
public final class LroeQueryResponseParser {

    private LroeQueryResponseParser() {
    }

    public static LroeQueryResult parse(byte[] xml) {
        Element root;
        try {
            root = Xml.parse(xml).getDocumentElement();
        } catch (IllegalArgumentException e) {
            throw new TransportException("Respuesta de consulta del LROE con formato inesperado: " + e.getMessage(), 200, e);
        }
        Element result = Xml.firstChild(root, "ResultadoConsulta");
        if (result == null) {
            throw new TransportException("Respuesta de consulta del LROE sin ResultadoConsulta", 200, null);
        }
        Element paging = Xml.firstChild(result, "Paginacion");
        List<LroeQueryResult.Invoice> invoices = new ArrayList<>();
        Element list = firstOf(root, "FacturasEmitidas", "Ingresos");
        if (list != null) {
            for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    invoices.add(invoice((Element) n));
                }
            }
        }
        return new LroeQueryResult(Xml.text(result, "FechaConsulta"), Integer.parseInt(Xml.requiredText(result, "NumPaginaConsulta")),
                "S".equals(Xml.text(result, "ExistenRegistros")),
                paging == null ? 0 : Integer.parseInt(Xml.requiredText(paging, "NumPaginasTotales")),
                paging != null && "S".equals(Xml.text(paging, "ExistenMasRegistros")),
                paging == null ? 0 : Integer.parseInt(Xml.requiredText(paging, "NumRegistrosTotales")), invoices, xml);
    }

    private static LroeQueryResult.Invoice invoice(Element item) {
        Element header = Xml.firstChild(item, "CabeceraFactura");
        Element situation = Xml.firstChild(item, "SituacionRegistro");
        Element presentation = Xml.firstChild(item, "DatosPresentacion");
        Element chain = Xml.firstChild(item, "EncadenamientoFacturaAnterior");
        ChainLink previous = chain == null ? null : ChainLink.ofPrefix(Xml.text(chain, "SerieFacturaAnterior"),
                Xml.requiredText(chain, "NumFacturaAnterior"), Dates.parse(Xml.requiredText(chain, "FechaExpedicionFacturaAnterior")),
                Xml.requiredText(chain, "SignatureValueFirmaFacturaAnterior"));
        String time = Xml.text(header, "HoraExpedicionFactura");
        String total = Xml.text(item, "ImporteTotalFactura");
        return new LroeQueryResult.Invoice(Xml.text(header, "SerieFactura"), Xml.requiredText(header, "NumFactura"),
                Dates.parse(Xml.requiredText(header, "FechaExpedicionFactura")), time == null ? null : LocalTime.parse(time),
                total == null ? null : new BigDecimal(total), directSignature(item), previous,
                situation == null ? null : Xml.text(situation, "EstadoRegistro"),
                situation == null ? null : Xml.text(situation, "CodigoErrorRegistro"),
                situation == null ? null : Xml.text(situation, "DescripcionErrorRegistroES"),
                presentation == null ? null : Xml.text(presentation, "FechaPresentacionAlta"));
    }

    /** {@code TicketBai/Signature} (no el {@code SignatureValueFirmaFacturaAnterior} del encadenamiento). */
    private static String directSignature(Element item) {
        NodeList signatures = item.getElementsByTagNameNS("*", "Signature");
        return signatures.getLength() == 0 ? null : signatures.item(0).getTextContent().trim();
    }

    private static Element firstOf(Element root, String... names) {
        for (String name : names) {
            Element element = Xml.firstChild(root, name);
            if (element != null) {
                return element;
            }
        }
        return null;
    }
}
