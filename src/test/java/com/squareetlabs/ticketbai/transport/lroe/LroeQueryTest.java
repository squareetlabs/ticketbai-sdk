package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.MutualTlsServer;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.transport.TlsConfig;
import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consulta ({@code C00}) del LROE contra los ejemplos oficiales de los modelos 240 y 140. */
class LroeQueryTest {

    private static final String DIR = "bizkaia-lroe/Ejemplo_Consulta_1_LROE_";
    private static final String PJ = DIR + "PJ_240_FacturasEmitidasConSG_B00000034";
    private static final String PF = DIR + "PF_140_IngresosConFacturaConSG_79732487C";
    private static final LroeTaxpayer HOTEL = LroeTaxpayer.legalEntity("B00000034", "HOTEL ADIBIDEZ");
    private static final LroeTaxpayer ANE = LroeTaxpayer.individual("79732487C", "ANE", "ARRIOLA", "LEJARDI")
            .withFullName("ANE ARRIOLA LEJARDI");

    @Test
    @DisplayName("Las peticiones oficiales de consulta del 240 y del 140 salen iguales y cumplen su XSD")
    void rebuildsOfficialRequests() {
        LroeQuery pj = LroeQuery.of(LroeModel.MODEL_240, HOTEL, Year.of(2022)).series("B2022");
        LroeQuery pf = LroeQuery.of(LroeModel.MODEL_140, ANE, Year.of(2022)).series("A2022");

        assertSame(PJ + ".xml", pj);
        assertSame(PF + ".xml", pf);
        assertEquals(List.of(), XsdValidator.validate(Examples.read(PJ + ".xml"), XsdValidator.Schema.LROE_240_QUERY));
        assertEquals(List.of(), XsdValidator.validate(Examples.read(PF + ".xml"), XsdValidator.Schema.LROE_140_QUERY));
        assertEquals(List.of(), XsdValidator.validate(LroeQueryBuilder.toBytes(pj), XsdValidator.Schema.LROE_240_QUERY));
        assertEquals(List.of(), XsdValidator.validate(LroeQueryBuilder.toBytes(pf), XsdValidator.Schema.LROE_140_QUERY));
    }

    @Test
    @DisplayName("Todos los filtros a la vez cumplen el XSD, en el orden que exige")
    void allFiltersAreValid() {
        LroeQuery pj = LroeQuery.of(LroeModel.MODEL_240, HOTEL, Year.of(2022)).recipientNif("B26248146").series("B2022")
                .number("0100").issuedBetween(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31))
                .status(LroeQuery.RecordStatus.ANNULLED).page(3);
        LroeQuery pf = LroeQuery.of(LroeModel.MODEL_140, ANE, Year.of(2022)).issuedBetween(null, LocalDate.of(2022, 6, 30))
                .status(LroeQuery.RecordStatus.ACCEPTED_WITH_ERRORS);

        byte[] xml = LroeQueryBuilder.toBytes(pj);
        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.LROE_240_QUERY));
        assertEquals(List.of(), XsdValidator.validate(LroeQueryBuilder.toBytes(pf), XsdValidator.Schema.LROE_140_QUERY));
        assertEquals("31-12-2022", Xml.text(Xml.parse(xml).getDocumentElement(), "Hasta"));
        assertEquals("3", Xml.text(Xml.parse(xml).getDocumentElement(), "NumPaginaConsulta"));
        assertThrows(IllegalArgumentException.class, () -> pj.page(0));
    }

    @Test
    @DisplayName("240: lee la respuesta con resultados y recupera el eslabón de cada factura")
    void parses240Results() {
        byte[] xml = gunzip(PJ + "_Con_Resultados_Resp.gz");

        LroeQueryResult result = LroeQueryResponseParser.parse(xml);

        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.LROE_240_QUERY_RESPONSE));
        assertTrue(result.hasRecords());
        assertEquals(1, result.getPage());
        assertEquals(1, result.getTotalPages());
        assertFalse(result.hasMoreRecords());
        assertEquals(2, result.getTotalRecords());
        assertEquals(2, result.getInvoices().size());

        LroeQueryResult.Invoice first = result.getInvoices().get(0);
        assertEquals("B2022", first.getSeries());
        assertEquals("0100", first.getNumber());
        assertEquals(LocalDate.of(2022, 1, 30), first.getIssueDate());
        assertEquals(LocalTime.of(18, 0, 17), first.getIssueTime());
        assertEquals(new BigDecimal("2343.00"), first.getTotalAmount());
        assertEquals("Anulado", first.getStatus());
        assertEquals("02-10-2020 12:06:36", first.getRegisteredAt());
        assertEquals(100, first.getSignatureValuePrefix().length());
        assertTrue(first.getSignatureValuePrefix().startsWith("iKgBOMw7"));
        assertEquals("0099", first.getPreviousLink().getNumber());
        assertTrue(first.getPreviousLink().getSignatureValuePrefix().startsWith("BeMkKwXa"));

        ChainLink link = first.toChainLink();
        assertEquals("0100", link.getNumber());
        assertEquals(first.getSignatureValuePrefix(), link.getSignatureValuePrefix());
        assertEquals("0101", result.latest().orElseThrow().getNumber());
    }

    @Test
    @DisplayName("140: lee la respuesta con resultados (lista de ingresos)")
    void parses140Results() {
        byte[] xml = gunzip(PF + "_Con_Resultados_Resp.gz");

        LroeQueryResult result = LroeQueryResponseParser.parse(xml);

        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.LROE_140_QUERY_RESPONSE));
        assertEquals(2, result.getInvoices().size());
        assertEquals("A2022", result.getInvoices().get(0).getSeries());
        assertEquals("0399", result.getInvoices().get(0).getNumber());
        assertEquals(new BigDecimal("31.46"), result.getInvoices().get(0).getTotalAmount());
        assertEquals("0398", result.getInvoices().get(0).getPreviousLink().getNumber());
    }

    @Test
    @DisplayName("Sin resultados: ExistenRegistros N, sin paginación ni facturas")
    void parsesEmptyResults() {
        for (String example : List.of(PJ, PF)) {
            LroeQueryResult result = LroeQueryResponseParser.parse(gunzip(example + "_Sin_Resultados_Resp.gz"));

            assertFalse(result.hasRecords(), example);
            assertEquals(0, result.getTotalRecords(), example);
            assertEquals(List.of(), result.getInvoices(), example);
            assertTrue(result.latest().isEmpty(), example);
        }
        assertEquals(List.of(), XsdValidator.validate(gunzip(PJ + "_Sin_Resultados_Resp.gz"), XsdValidator.Schema.LROE_240_QUERY_RESPONSE));
        assertEquals(List.of(), XsdValidator.validate(gunzip(PF + "_Sin_Resultados_Resp.gz"), XsdValidator.Schema.LROE_140_QUERY_RESPONSE));
    }

    @Test
    @DisplayName("Consulta por TLS mutuo al servicio de consulta, con GZIP y cabeceras n3; «Incorrecto» es un fallo")
    void queriesOverMutualTls() throws Exception {
        LroeQuery query = LroeQuery.of(LroeModel.MODEL_140, ANE, Year.of(2022)).series("A2022");

        try (MutualTlsServer server = new MutualTlsServer()) {
            server.respond(200, Examples.read(PF + "_Con_Resultados_Resp.gz"),
                    Map.of("eus-bizkaia-n3-tipo-respuesta", "Correcto", "Content-Encoding", "gzip"));
            LroeClient client = new LroeClient(server.uri("/N3B4000M/aurkezpena"), server.uri("/N3B4001M/kontsulta"), tls(),
                    Duration.ofSeconds(5), Duration.ofSeconds(5));

            LroeQueryResult result = client.query(query);

            assertEquals("/N3B4001M/kontsulta", server.getLastPath());
            assertEquals("{\"con\":\"LROE\",\"apa\":\"1.1\",\"inte\":{\"nif\":\"79732487C\",\"nrs\":\"ANE\",\"ap1\":\"ARRIOLA\","
                    + "\"ap2\":\"LEJARDI\"},\"drs\":{\"mode\":\"140\",\"ejer\":\"2022\"}}", server.getLastHeader("eus-bizkaia-n3-data"));
            assertArrayEquals(LroeQueryBuilder.toBytes(query), gunzipBytes(server.getLastBody()));
            assertEquals(2, result.getInvoices().size());

            server.respond(200, new byte[0], Map.of("eus-bizkaia-n3-tipo-respuesta", "Incorrecto",
                    "eus-bizkaia-n3-codigo-respuesta", "B4_1000002", "eus-bizkaia-n3-mensaje-respuesta", "Error"));
            assertTrue(assertThrows(TransportException.class, () -> client.query(query)).getMessage().contains("B4_1000002"));
        }
        LroeClient withoutQuery = new LroeClient(java.net.URI.create("https://localhost/x"), tls(), Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        assertThrows(IllegalStateException.class, () -> withoutQuery.query(query));
    }

    @Test
    @DisplayName("Sin nombre explícito, la persona física se escribe «APELLIDO1 APELLIDO2 NOMBRE»")
    void defaultIndividualName() {
        assertEquals("ARRIOLA LEJARDI ANE", LroeTaxpayer.individual("79732487C", "ANE", "ARRIOLA", "LEJARDI").getFullName());
        assertNull(LroeQuery.of(LroeModel.MODEL_240, HOTEL, Year.of(2022)).getSeries());
    }

    private static void assertSame(String example, LroeQuery query) {
        List<String> diffs = XmlTreeComparator.differences(Xml.parse(Examples.read(example)).getDocumentElement(),
                LroeQueryBuilder.build(query).getDocumentElement(), Set.of());
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));
    }

    private static byte[] gunzip(String example) {
        return gunzipBytes(Examples.read(example));
    }

    private static byte[] gunzipBytes(byte[] data) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static TlsConfig tls() throws Exception {
        try (InputStream in = LroeQueryTest.class.getResourceAsStream("/certs/test-signer.p12")) {
            return TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray())
                    .trust(MutualTlsServer.certificate("/certs/test-server.cer")).build();
        }
    }
}
