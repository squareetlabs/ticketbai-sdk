package com.squareetlabs.ticketbai.transport.rest;

import com.squareetlabs.ticketbai.transport.SubmissionResult;
import com.squareetlabs.ticketbai.transport.SubmissionResult.Status;
import com.squareetlabs.ticketbai.transport.SubmissionResult.ValidationResult;
import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee {@code TicketBaiResponse} sin depender de su espacio de nombres (Álava usa {@code urn:ticketbai:emision}). Una
 * respuesta con un formato inesperado es un fallo de transporte: hay que reintentar.
 */
final class ResponseParser {

    private ResponseParser() {
    }

    static SubmissionResult parse(byte[] body) {
        Element salida;
        try {
            Element root = Xml.parse(body).getDocumentElement();
            salida = "Salida".equals(root.getLocalName()) ? root : Xml.firstChild(root, "Salida");
        } catch (IllegalArgumentException e) {
            throw new TransportException("Respuesta con formato inesperado: " + e.getMessage(), 200, e);
        }
        if (salida == null) {
            throw new TransportException("Respuesta sin el elemento Salida", 200, null);
        }
        String state = Xml.text(salida, "Estado");
        Status status;
        if ("00".equals(state)) {
            status = Status.RECEIVED;
        } else if ("01".equals(state)) {
            status = Status.REJECTED;
        } else {
            throw new TransportException("Estado de respuesta desconocido: " + state, 200, null);
        }
        List<ValidationResult> results = new ArrayList<>();
        for (Node n = salida.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && "ResultadosValidacion".equals(n.getLocalName())) {
                Element r = (Element) n;
                results.add(new ValidationResult(Xml.text(r, "Codigo"), Xml.text(r, "Descripcion"), Xml.text(r, "Azalpena")));
            }
        }
        return new SubmissionResult(status, state, Xml.text(salida, "IdentificadorTBAI"), Xml.text(salida, "FechaRecepcion"),
                directText(salida, "Descripcion"), Xml.text(salida, "CSV"), results, body);
    }

    /** Texto de un hijo directo (la Descripcion de Salida, no la de un ResultadosValidacion). */
    private static String directText(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && localName.equals(n.getLocalName())) {
                return n.getTextContent().trim();
            }
        }
        return null;
    }
}
