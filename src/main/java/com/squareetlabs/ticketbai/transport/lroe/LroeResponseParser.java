package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.transport.lroe.LroeResult.Record;
import com.squareetlabs.ticketbai.transport.lroe.LroeResult.RecordStatus;
import com.squareetlabs.ticketbai.transport.lroe.LroeResult.Status;
import com.squareetlabs.ticketbai.util.Dates;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/** Lee la respuesta del LROE: estado global de las cabeceras y resultado de cada registro del XML. */
public final class LroeResponseParser {

    private LroeResponseParser() {
    }

    /**
     * @param responseType {@code eus-bizkaia-n3-tipo-respuesta}
     * @param entryId      {@code eus-bizkaia-n3-identificativo}
     * @param errorCode    {@code eus-bizkaia-n3-codigo-respuesta}
     * @param errorMessage {@code eus-bizkaia-n3-mensaje-respuesta}
     * @param xml          cuerpo XML ya descomprimido (puede estar vacío si el envío se rechazó entero)
     */
    public static LroeResult parse(String responseType, String entryId, String errorCode, String errorMessage, byte[] xml) {
        Status status = status(responseType);
        List<Record> records = new ArrayList<>();
        String presentedAt = null;
        if (xml != null && xml.length > 0) {
            Element root;
            try {
                root = Xml.parse(xml).getDocumentElement();
            } catch (IllegalArgumentException e) {
                if (status == Status.INCORRECT) {
                    return new LroeResult(status, entryId, errorCode, errorMessage, null, records, xml);
                }
                throw new TransportException("Respuesta del LROE con formato inesperado: " + e.getMessage(), 200, e);
            }
            Element presentation = Xml.firstChild(root, "DatosPresentacion");
            presentedAt = presentation == null ? null : Xml.text(presentation, "FechaPresentacion");
            NodeList list = root.getElementsByTagNameNS("*", "Registro");
            for (int i = 0; i < list.getLength(); i++) {
                records.add(record((Element) list.item(i)));
            }
        }
        return new LroeResult(status, entryId, errorCode, errorMessage, presentedAt, records, xml);
    }

    private static Record record(Element registro) {
        Element id = Xml.firstChild(registro, "IDFactura");
        Element situation = Xml.firstChild(registro, "SituacionRegistro");
        if (situation == null) {
            throw new TransportException("Registro del LROE sin SituacionRegistro", 200, null);
        }
        String date = id == null ? null : Xml.text(id, "FechaExpedicionFactura");
        return new Record(id == null ? null : Xml.text(id, "SerieFactura"), id == null ? null : Xml.text(id, "NumFactura"),
                date == null ? null : Dates.parse(date), recordStatus(Xml.requiredText(situation, "EstadoRegistro")),
                Xml.text(situation, "CodigoErrorRegistro"), Xml.text(situation, "DescripcionErrorRegistroES"),
                Xml.text(situation, "DescripcionErrorRegistroEU"));
    }

    private static Status status(String value) {
        if (value == null) {
            throw new TransportException("La respuesta del LROE no trae eus-bizkaia-n3-tipo-respuesta", 200, null);
        }
        switch (value.trim()) {
            case "Correcto":
                return Status.CORRECT;
            case "Parcialmente correcto":
                return Status.PARTIALLY_CORRECT;
            case "Incorrecto":
                return Status.INCORRECT;
            default:
                throw new TransportException("Tipo de respuesta del LROE desconocido: " + value, 200, null);
        }
    }

    private static RecordStatus recordStatus(String value) {
        switch (value) {
            case "Correcto":
                return RecordStatus.CORRECT;
            case "AceptadoConErrores":
                return RecordStatus.ACCEPTED_WITH_ERRORS;
            case "Incorrecto":
                return RecordStatus.INCORRECT;
            case "Anulado":
                return RecordStatus.ANNULLED;
            default:
                throw new TransportException("Estado de registro del LROE desconocido: " + value, 200, null);
        }
    }
}
