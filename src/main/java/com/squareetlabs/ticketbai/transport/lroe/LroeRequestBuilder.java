package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.util.Amounts;
import com.squareetlabs.ticketbai.util.XmlWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Base64;

/**
 * XML del envío LROE del subcapítulo 1.1 (modelos 240 y 140; alta, modificación o anulación), con cada fichero
 * TicketBAI en Base64. Sin comprimir: el cliente lo comprime con GZIP. El alta y la modificación comparten esquema.
 */
public final class LroeRequestBuilder {

    static final String NS_BASE = "https://www.batuz.eus/fitxategiak/batuz/LROE/esquemas/";

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
        boolean annulment = batch.getOperation() == LroeOperation.ANNULMENTS;
        boolean model140 = batch.getModel() == LroeModel.MODEL_140;
        Element root = doc.createElementNS(namespace(model140, annulment), rootName(model140, annulment));
        doc.appendChild(root);

        Element header = add(doc, root, "Cabecera");
        text(doc, header, "Modelo", batch.getModel().code());
        text(doc, header, "Capitulo", "1");
        text(doc, header, "Subcapitulo", "1.1");
        text(doc, header, "Operacion", batch.getOperation().code());
        text(doc, header, "Version", "1.0");
        text(doc, header, "Ejercicio", String.valueOf(batch.getFiscalYear().getValue()));
        Element taxpayer = add(doc, header, "ObligadoTributario");
        text(doc, taxpayer, "NIF", batch.getTaxpayer().getNif());
        text(doc, taxpayer, "ApellidosNombreRazonSocial", batch.getTaxpayer().getFullName());

        Element list = add(doc, root, model140 ? "Ingresos" : "FacturasEmitidas");
        for (LroeBatch.Entry entry : batch.getEntries()) {
            Element item = add(doc, list, model140 ? "Ingreso" : "FacturaEmitida");
            text(doc, item, annulment ? "AnulacionTicketBai" : "TicketBai",
                    Base64.getEncoder().encodeToString(entry.getFile()));
            if (model140 && !annulment) {
                Element renta = add(doc, item, "Renta");
                for (IncomeDetail detail : entry.getIncomeDetails()) {
                    income(doc, add(doc, renta, "DetalleRenta"), detail);
                }
            }
        }
        return doc;
    }

    public static byte[] toBytes(LroeBatch batch) {
        return XmlWriter.toBytes(build(batch));
    }

    private static void income(Document doc, Element detail, IncomeDetail income) {
        if (income.getTerritory() != null) {
            text(doc, detail, "TerritorioAltaActividad", income.getTerritory().code());
        }
        text(doc, detail, "Epigrafe", income.getActivityCode());
        if (income.getPremisesReference() != null) {
            text(doc, detail, "NumeroFijoOReferenciaCatastral", income.getPremisesReference());
        }
        if (income.getIncomeTaxAmount() != null) {
            text(doc, detail, "IngresoAComputarIRPFDiferenteBaseImpoIVA", "S");
            if (income.getIncomeTaxCause() != null) {
                text(doc, detail, "CausaIngresoIRPFDiferenteBaseImpoIVA", income.getIncomeTaxCause().code());
            }
            text(doc, detail, "ImporteIngresoIRPF", Amounts.format(income.getIncomeTaxAmount()));
        }
        if (income.getCashCriterion() != null) {
            text(doc, detail, "CriterioCobrosYPagos", income.getCashCriterion() ? "S" : "N");
        }
    }

    static String namespace(boolean model140, boolean annulment) {
        if (model140) {
            return NS_BASE + (annulment ? "LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AnulacionPeticion_V1_0_0.xsd"
                    : "LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AltaPeticion_V1_0_2.xsd");
        }
        return NS_BASE + (annulment ? "LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AnulacionPeticion_V1_0_0.xsd"
                : "LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AltaPeticion_V1_0_2.xsd");
    }

    private static String rootName(boolean model140, boolean annulment) {
        if (model140) {
            return "lrpficfcsgap:LROEPF140IngresosConFacturaConSG" + (annulment ? "AnulacionPeticion" : "AltaPeticion");
        }
        return annulment ? "lrpjfecsgan:LROEPJ240FacturasEmitidasConSGAnulacionPeticion"
                : "lrpjfecsgap:LROEPJ240FacturasEmitidasConSGAltaPeticion";
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
