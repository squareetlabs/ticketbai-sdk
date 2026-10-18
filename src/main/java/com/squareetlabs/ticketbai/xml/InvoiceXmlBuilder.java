package com.squareetlabs.ticketbai.xml;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.model.Breakdown;
import com.squareetlabs.ticketbai.model.Codes.Coded;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceLine;
import com.squareetlabs.ticketbai.model.InvoiceReference;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.Rectification;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.model.TaxBreakdown;
import com.squareetlabs.ticketbai.model.VatDetail;
import com.squareetlabs.ticketbai.util.Amounts;
import com.squareetlabs.ticketbai.util.Dates;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Construye el fichero de alta {@code TicketBai} (XSD 1.2.2) <b>sin firmar</b>, con los elementos en el orden del
 * esquema. La firma ({@code ds:Signature}) se añade después como último hijo de la raíz.
 * <p>
 * Formatos: fechas {@code dd-mm-aaaa}, hora {@code hh:mm:ss}, importes con dos decimales (ocho en las líneas de
 * detalle), tipos con dos decimales y booleanos {@code S}/{@code N}.
 */
public final class InvoiceXmlBuilder {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private InvoiceXmlBuilder() {
    }

    /**
     * @param invoice      datos de la factura
     * @param previous     eslabón de la factura anterior del mismo emisor, o {@code null} si es la primera
     * @param software     software garante registrado
     * @param deviceSerial número de serie del dispositivo ({@code NumSerieDispositivo}), o {@code null}
     */
    public static Document build(Invoice invoice, ChainLink previous, Software software, String deviceSerial) {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(software, "software");
        Document doc = newDocument();
        Element root = doc.createElementNS(TicketBaiXml.INVOICE_NS, TicketBaiXml.PREFIX + ":TicketBai");
        doc.appendChild(root);
        Writer w = new Writer(doc);

        Element header = w.add(root, "Cabecera");
        w.text(header, "IDVersionTBAI", TicketBaiXml.VERSION);

        subjects(w, w.add(root, "Sujetos"), invoice);
        invoice(w, w.add(root, "Factura"), invoice);
        fingerprint(w, w.add(root, "HuellaTBAI"), previous, software, deviceSerial);
        return doc;
    }

    private static void subjects(Writer w, Element subjects, Invoice invoice) {
        Element issuer = w.add(subjects, "Emisor");
        w.text(issuer, "NIF", invoice.getIssuer().getNif());
        w.text(issuer, "ApellidosNombreRazonSocial", invoice.getIssuer().getName());
        if (!invoice.getRecipients().isEmpty()) {
            Element recipients = w.add(subjects, "Destinatarios");
            for (Party recipient : invoice.getRecipients()) {
                Element id = w.add(recipients, "IDDestinatario");
                identification(w, id, recipient);
                w.text(id, "ApellidosNombreRazonSocial", recipient.getName());
                w.optional(id, "CodigoPostal", recipient.getPostalCode());
                w.optional(id, "Direccion", recipient.getAddress());
            }
        }
        w.yesNo(subjects, "VariosDestinatarios", invoice.getRecipients().size() > 1);
        w.code(subjects, "EmitidaPorTercerosODestinatario", invoice.getIssuedBy());
    }

    private static void identification(Writer w, Element parent, Party party) {
        if (party.hasNif()) {
            w.text(parent, "NIF", party.getNif());
            return;
        }
        Element other = w.add(parent, "IDOtro");
        w.optional(other, "CodigoPais", party.getCountryCode());
        w.code(other, "IDType", party.getOtherIdType());
        w.text(other, "ID", party.getOtherId());
    }

    private static void invoice(Writer w, Element factura, Invoice invoice) {
        Element header = w.add(factura, "CabeceraFactura");
        w.optional(header, "SerieFactura", invoice.getSeries());
        w.text(header, "NumFactura", invoice.getNumber());
        w.text(header, "FechaExpedicionFactura", Dates.format(invoice.getIssueDate()));
        w.text(header, "HoraExpedicionFactura", TIME.format(invoice.getIssueTime()));
        w.yesNo(header, "FacturaSimplificada", invoice.isSimplified());
        w.yesNo(header, "FacturaEmitidaSustitucionSimplificada", invoice.isSubstitutesSimplified());
        rectification(w, header, invoice.getRectification());

        Element data = w.add(factura, "DatosFactura");
        if (invoice.getOperationDate() != null) {
            w.text(data, "FechaOperacion", Dates.format(invoice.getOperationDate()));
        }
        w.text(data, "DescripcionFactura", invoice.getDescription());
        if (!invoice.getLines().isEmpty()) {
            Element lines = w.add(data, "DetallesFactura");
            for (InvoiceLine line : invoice.getLines()) {
                Element detail = w.add(lines, "IDDetalleFactura");
                w.text(detail, "DescripcionDetalle", line.getDescription());
                w.text(detail, "Cantidad", Amounts.format8(line.getQuantity()));
                w.text(detail, "ImporteUnitario", Amounts.format8(line.getUnitPrice()));
                if (line.getDiscount() != null) {
                    w.text(detail, "Descuento", Amounts.format8(line.getDiscount()));
                }
                w.text(detail, "ImporteTotal", Amounts.format8(line.getTotal()));
            }
        }
        w.amount(data, "ImporteTotalFactura", invoice.getTotalAmount());
        w.optionalAmount(data, "RetencionSoportada", invoice.getWithholding());
        w.optionalAmount(data, "BaseImponibleACoste", invoice.getTaxableBaseAtCost());
        Element keys = w.add(data, "Claves");
        invoice.getRegimeKeys().forEach(key -> w.code(w.add(keys, "IDClave"), "ClaveRegimenIvaOpTrascendencia", key));

        breakdown(w, w.add(factura, "TipoDesglose"), invoice.getBreakdown());
    }

    private static void rectification(Writer w, Element header, Rectification rectification) {
        if (rectification == null) {
            return;
        }
        Element element = w.add(header, "FacturaRectificativa");
        w.code(element, "Codigo", rectification.getCode());
        w.code(element, "Tipo", rectification.getType());
        if (rectification.getRectifiedBase() != null) {
            Element amounts = w.add(element, "ImporteRectificacionSustitutiva");
            w.amount(amounts, "BaseRectificada", rectification.getRectifiedBase());
            w.amount(amounts, "CuotaRectificada", rectification.getRectifiedQuota());
            w.optionalAmount(amounts, "CuotaRecargoRectificada", rectification.getRectifiedSurchargeQuota());
        }
        List<InvoiceReference> rectified = rectification.getRectifiedInvoices();
        if (!rectified.isEmpty()) {
            Element list = w.add(header, "FacturasRectificadasSustituidas");
            for (InvoiceReference reference : rectified) {
                Element id = w.add(list, "IDFacturaRectificadaSustituida");
                w.optional(id, "SerieFactura", reference.getSeries());
                w.text(id, "NumFactura", reference.getNumber());
                w.text(id, "FechaExpedicionFactura", Dates.format(reference.getIssueDate()));
            }
        }
    }

    private static void breakdown(Writer w, Element type, Breakdown breakdown) {
        if (!breakdown.isByOperationType()) {
            taxBreakdown(w, w.add(type, "DesgloseFactura"), breakdown.getInvoice());
            return;
        }
        Element byOperation = w.add(type, "DesgloseTipoOperacion");
        if (breakdown.getServices() != null) {
            taxBreakdown(w, w.add(byOperation, "PrestacionServicios"), breakdown.getServices());
        }
        if (breakdown.getGoods() != null) {
            taxBreakdown(w, w.add(byOperation, "Entrega"), breakdown.getGoods());
        }
    }

    private static void taxBreakdown(Writer w, Element parent, TaxBreakdown breakdown) {
        if (breakdown.isSubject()) {
            Element subject = w.add(parent, "Sujeta");
            if (!breakdown.getExempt().isEmpty()) {
                Element exempt = w.add(subject, "Exenta");
                for (Map.Entry<?, BigDecimal> entry : breakdown.getExempt().entrySet()) {
                    Element detail = w.add(exempt, "DetalleExenta");
                    w.code(detail, "CausaExencion", (Coded) entry.getKey());
                    w.amount(detail, "BaseImponible", entry.getValue());
                }
            }
            if (!breakdown.getNonExempt().isEmpty()) {
                Element nonExempt = w.add(subject, "NoExenta");
                breakdown.getNonExempt().forEach((nonExemptType, details) -> {
                    Element detail = w.add(nonExempt, "DetalleNoExenta");
                    w.code(detail, "TipoNoExenta", nonExemptType);
                    Element vat = w.add(detail, "DesgloseIVA");
                    for (VatDetail d : details) {
                        Element line = w.add(vat, "DetalleIVA");
                        w.amount(line, "BaseImponible", d.getTaxableBase());
                        w.optionalAmount(line, "TipoImpositivo", d.getRate());
                        w.optionalAmount(line, "CuotaImpuesto", d.getQuota());
                        w.optionalAmount(line, "TipoRecargoEquivalencia", d.getSurchargeRate());
                        w.optionalAmount(line, "CuotaRecargoEquivalencia", d.getSurchargeQuota());
                        if (d.getSurchargeOrSimplifiedRegime() != null) {
                            w.yesNo(line, "OperacionEnRecargoDeEquivalenciaORegimenSimplificado",
                                    d.getSurchargeOrSimplifiedRegime());
                        }
                    }
                });
            }
        }
        if (!breakdown.getNotSubject().isEmpty()) {
            Element notSubject = w.add(parent, "NoSujeta");
            breakdown.getNotSubject().forEach((cause, amount) -> {
                Element detail = w.add(notSubject, "DetalleNoSujeta");
                w.code(detail, "Causa", cause);
                w.amount(detail, "Importe", amount);
            });
        }
    }

    private static void fingerprint(Writer w, Element fingerprint, ChainLink previous, Software software,
                                    String deviceSerial) {
        if (previous != null) {
            Element chain = w.add(fingerprint, "EncadenamientoFacturaAnterior");
            w.optional(chain, "SerieFacturaAnterior", previous.getSeries());
            w.text(chain, "NumFacturaAnterior", previous.getNumber());
            w.text(chain, "FechaExpedicionFacturaAnterior", Dates.format(previous.getIssueDate()));
            w.text(chain, "SignatureValueFirmaFacturaAnterior", previous.getSignatureValuePrefix());
        }
        Element sw = w.add(fingerprint, "Software");
        w.text(sw, "LicenciaTBAI", software.getLicense());
        identification(w, w.add(sw, "EntidadDesarrolladora"), software.getDeveloper());
        w.text(sw, "Nombre", software.getName());
        w.text(sw, "Version", software.getVersion());
        w.optional(fingerprint, "NumSerieDispositivo", deviceSerial);
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

    /** Escritura de elementos sin espacio de nombres (los hijos de la raíz no van cualificados en el XSD). */
    private static final class Writer {
        private final Document doc;

        Writer(Document doc) {
            this.doc = doc;
        }

        Element add(Element parent, String name) {
            Element element = doc.createElementNS(null, name);
            parent.appendChild(element);
            return element;
        }

        void text(Element parent, String name, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Falta el valor obligatorio " + name);
            }
            add(parent, name).setTextContent(value);
        }

        void optional(Element parent, String name, String value) {
            if (value != null && !value.isBlank()) {
                add(parent, name).setTextContent(value);
            }
        }

        void code(Element parent, String name, Coded value) {
            text(parent, name, Objects.requireNonNull(value, name).code());
        }

        void yesNo(Element parent, String name, boolean value) {
            text(parent, name, value ? "S" : "N");
        }

        void amount(Element parent, String name, BigDecimal value) {
            text(parent, name, Amounts.format(Objects.requireNonNull(value, name)));
        }

        void optionalAmount(Element parent, String name, BigDecimal value) {
            if (value != null) {
                amount(parent, name, value);
            }
        }
    }
}
