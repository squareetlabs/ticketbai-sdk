package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Modelo 140, anulación del 240 y modificación (M00), contra los ejemplos oficiales del LROE. */
class LroeModelsTest {

    private static final String DIR = "bizkaia-lroe/";
    private static final String PF_ALTA = DIR + "Ejemplo_1_LROE_PF_140_IngresosConFacturaConSG_79732487C.xml";
    private static final String PF_ANULACION = DIR + "Ejemplo_Anulacion_1_LROE_PF_140_IngresosConFacturaConSG_79732487C.xml";
    private static final String PJ_ALTA = DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034.xml";
    private static final String PJ_ANULACION = DIR + "Ejemplo_Anulacion_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034.xml";
    private static final LroeTaxpayer ANE = LroeTaxpayer.individual("79732487C", "ANE", "ARRIOLA", "LEJARDI");

    @Test
    @DisplayName("140: los envíos oficiales de alta y anulación cumplen sus XSD")
    void official140RequestsAreValid() {
        assertEquals(List.of(), XsdValidator.validate(Examples.read(PF_ALTA), XsdValidator.Schema.LROE_140_INVOICES));
        assertEquals(List.of(), XsdValidator.validate(Examples.read(PF_ANULACION), XsdValidator.Schema.LROE_140_ANNULMENTS));
    }

    @Test
    @DisplayName("140: con los mismos ficheros y datos de renta, el alta sale igual que la oficial")
    void rebuilds140Invoices() {
        List<SignedXml> files = files(PF_ALTA, "TicketBai");
        LroeBatch batch = LroeBatch.builder(LroeModel.MODEL_140, ANE, Year.of(2022), LroeOperation.INVOICES)
                .add(files.get(0), IncomeDetail.activity("197330"))
                .add(files.get(1), IncomeDetail.activity("197330").incomeTaxAmount(new BigDecimal("400.00"), null))
                .build();

        assertSame(PF_ALTA, batch);
        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(batch), XsdValidator.Schema.LROE_140_INVOICES));
    }

    @Test
    @DisplayName("Anulaciones: los envíos oficiales del 140 y del 240 salen iguales")
    void rebuildsAnnulments() {
        LroeBatch.Builder pf = LroeBatch.builder(LroeModel.MODEL_140, ANE, Year.of(2022), LroeOperation.ANNULMENTS);
        files(PF_ANULACION, "AnulacionTicketBai").forEach(pf::add);
        LroeBatch.Builder pj = LroeBatch.builder(LroeModel.MODEL_240, LroeTaxpayer.legalEntity("B00000034", "HOTEL ADIBIDEZ"),
                Year.of(2022), LroeOperation.ANNULMENTS);
        files(PJ_ANULACION, "AnulacionTicketBai").forEach(pj::add);

        assertSame(PF_ANULACION, pf.build());
        assertSame(PJ_ANULACION, pj.build());
        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(pj.build()), XsdValidator.Schema.LROE_240_ANNULMENTS));
    }

    @Test
    @DisplayName("Modificación (M00): mismo esquema que el alta en los dos modelos")
    void modifications() {
        List<SignedXml> pfFiles = files(PF_ALTA, "TicketBai");
        LroeBatch pf = LroeBatch.builder(LroeModel.MODEL_140, ANE, Year.of(2022), LroeOperation.MODIFICATIONS)
                .add(pfFiles.get(0), IncomeDetail.activity("197330").territory(IncomeDetail.Territory.BIZKAIA)
                        .premisesReference("1234567").cashCriterion(true)
                        .incomeTaxAmount(new BigDecimal("100"), IncomeDetail.DifferentIncomeCause.ADVANCE_PAYMENTS))
                .build();
        LroeBatch pj = LroeBatch.of("B00000034", "HOTEL ADIBIDEZ", Year.of(2022), LroeOperation.MODIFICATIONS,
                files(PJ_ALTA, "TicketBai"));

        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(pf), XsdValidator.Schema.LROE_140_INVOICES));
        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(pj), XsdValidator.Schema.LROE_240_INVOICES));
        assertEquals("M00", Xml.text(Xml.parse(LroeRequestBuilder.toBytes(pj)).getDocumentElement(), "Operacion"));
    }

    @Test
    @DisplayName("Cabecera n3 del 140: nombre y apellidos por separado, como el ejemplo de la especificación")
    void n3HeaderOfAnIndividual() {
        LroeBatch batch = LroeBatch.builder(LroeModel.MODEL_140, ANE, Year.of(2022), LroeOperation.ANNULMENTS)
                .add(files(PF_ANULACION, "AnulacionTicketBai").get(0)).build();

        assertEquals("{\"con\":\"LROE\",\"apa\":\"1.1\",\"inte\":{\"nif\":\"79732487C\",\"nrs\":\"ANE\",\"ap1\":\"ARRIOLA\","
                + "\"ap2\":\"LEJARDI\"},\"drs\":{\"mode\":\"140\",\"ejer\":\"2022\"}}", LroeClient.n3Data(batch));
    }

    @Test
    @DisplayName("Reglas: el 140 lleva datos de renta y una persona física; el 240 no lleva renta y es persona jurídica")
    void batchRules() {
        SignedXml pfFile = files(PF_ALTA, "TicketBai").get(0);
        LroeTaxpayer company = LroeTaxpayer.legalEntity("79732487C", "X");

        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_140, ANE, Year.of(2022),
                LroeOperation.INVOICES).add(pfFile));
        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_140, company, Year.of(2022),
                LroeOperation.INVOICES));
        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_240, ANE, Year.of(2022),
                LroeOperation.INVOICES));
        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_240, company, Year.of(2022),
                LroeOperation.INVOICES).add(pfFile, IncomeDetail.activity("197330")));
        assertThrows(IllegalArgumentException.class, () -> IncomeDetail.activity("12345678"));
    }

    @Test
    @DisplayName("140: lee la respuesta oficial parcialmente correcta")
    void parses140Response() throws Exception {
        byte[] xml;
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(Examples.read(
                DIR + "Ejemplo_1_LROE_PF_140_IngresosConFacturaConSG_79732487C_Parc_Correcta_Resp.gz")))) {
            xml = in.readAllBytes();
        }

        LroeResult result = LroeResponseParser.parse("Parcialmente correcto", "ID", null, null, xml);

        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.LROE_140_INVOICES_RESPONSE));
        assertEquals(LroeResult.RecordStatus.INCORRECT, result.getRecords().get(0).getStatus());
        assertEquals("0399", result.getRecords().get(0).getNumber());
        assertEquals(LroeResult.RecordStatus.CORRECT, result.getRecords().get(1).getStatus());
    }

    private static void assertSame(String example, LroeBatch batch) {
        Document official = Xml.parse(Examples.read(example));
        List<String> diffs = XmlTreeComparator.differences(official.getDocumentElement(),
                LroeRequestBuilder.build(batch).getDocumentElement(), Set.of());
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));
    }

    private static List<SignedXml> files(String example, String element) {
        NodeList nodes = Xml.parse(Examples.read(example)).getElementsByTagNameNS("*", element);
        List<SignedXml> files = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            files.add(SignedXml.of(Base64.getMimeDecoder().decode(nodes.item(i).getTextContent().trim())));
        }
        assertEquals(2, files.size(), example);
        return files;
    }
}
