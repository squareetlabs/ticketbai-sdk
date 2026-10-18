package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Lee la respuesta Kontsulta de Gipuzkoa (1.0) y de Álava (1.2.3), sin depender de su espacio de nombres. */
public final class KontsultaResponseParser {

    private KontsultaResponseParser() {
    }

    public static KontsultaResult parse(byte[] xml, int httpStatus) {
        Element root;
        try {
            root = Xml.parse(xml).getDocumentElement();
        } catch (IllegalArgumentException e) {
            throw new TransportException("Respuesta Kontsulta con formato inesperado: " + e.getMessage(), httpStatus, e);
        }
        Element result = Xml.firstChild(root, "ResultadoConsulta");
        if (result == null) {
            throw new TransportException("Respuesta Kontsulta sin ResultadoConsulta", httpStatus, null);
        }
        Element header = Xml.firstChild(root, "Cabecera");
        Element paging = Xml.firstChild(result, "Paginacion");
        Element error = Xml.firstChild(result, "Error");
        String errorDescription = error == null ? null : Xml.text(error, "Descripcion");
        List<KontsultaResult.Invoice> invoices = new ArrayList<>();
        Element list = Xml.firstChild(root, "FacturasEmitidas");
        if (list != null) {
            for (Node n = list.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element && "Factura".equals(n.getLocalName())) {
                    invoices.add(invoice((Element) n));
                }
            }
        }
        return new KontsultaResult(header == null ? null : Xml.text(header, "NIF"),
                header == null ? null : Xml.text(header, "ApellidosNombreRazonSocial"), Xml.text(result, "FechaHoraConsulta"),
                integer(Xml.text(result, "NumPaginaConsulta")), "S".equals(Xml.text(result, "ExistenRegistros")),
                paging == null ? 0 : integer(Xml.text(paging, "NumPaginasTotales")),
                paging != null && "S".equals(Xml.text(paging, "ExistenMasRegistros")),
                paging == null ? 0 : integer(Xml.text(paging, "NumRegistrosTotales")),
                errorDescription == null || errorDescription.isEmpty() ? null : errorDescription, invoices, xml);
    }

    private static KontsultaResult.Invoice invoice(Element item) {
        Element data = Xml.firstChild(item, "DatosFactura");
        Element header = Xml.firstChild(data, "CabeceraFactura");
        Element situation = Xml.firstChild(item, "SituacionRegistro");
        Element presentation = Xml.firstChild(item, "DatosPresentacion");
        String operation = Xml.text(header, "FechaOperacionFactura");
        String total = Xml.text(data, "ImporteTotalFactura");
        List<String> codes = new ArrayList<>();
        Element tbaiErrors = situation == null ? null : Xml.firstChild(situation, "CodigosErrorTicketBAI");
        if (tbaiErrors != null) {
            NodeList nodes = tbaiErrors.getElementsByTagNameNS("*", "CodigoError");
            for (int i = 0; i < nodes.getLength(); i++) {
                codes.add(nodes.item(i).getTextContent().trim());
            }
        }
        return new KontsultaResult.Invoice(Xml.text(header, "SerieFactura"), Xml.requiredText(header, "NumFactura"),
                dateTime(Xml.requiredText(header, "FechaHoraExpedicionFactura")),
                operation == null || operation.isEmpty() ? null : Dates.parse(operation),
                total == null ? null : new BigDecimal(total), Xml.text(data, "IdentificadorTBAI"),
                Xml.text(data, "SignatureValue"), situation == null ? null : Xml.text(situation, "Estado"), codes,
                presentation == null ? null : Xml.text(presentation, "FechaHoraPresentacionAlta"));
    }

    /** {@code FechaHoraType}: la hora es opcional. */
    private static LocalDateTime dateTime(String value) {
        return value.length() > 10 ? LocalDateTime.parse(value, KontsultaXmlBuilder.DATE_TIME)
                : Dates.parse(value).atStartOfDay();
    }

    private static int integer(String value) {
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }
}
