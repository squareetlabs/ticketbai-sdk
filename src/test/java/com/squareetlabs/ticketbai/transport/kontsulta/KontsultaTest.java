package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.identifier.TbaiIdentifier;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.MutualTlsServer;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.transport.TlsConfig;
import com.squareetlabs.ticketbai.transport.TransportException;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kontsulta de Gipuzkoa y Álava contra sus ejemplos y XSD oficiales. */
class KontsultaTest {

    private static final String DIR = "kontsulta/";
    private static final String NIF = "99999972C";

    @Test
    @DisplayName("Gipuzkoa: la petición oficial con todos los filtros sale igual y cumple el XSD")
    void rebuildsGipuzkoaQuery() {
        KontsultaQuery query = KontsultaQuery.of(Territory.GIPUZKOA, NIF, Year.of(2022), KontsultaQuery.Period.month(1))
                .series("TBAI-2022-").number("0001")
                .issuedBetween(LocalDateTime.of(2022, 1, 1, 10, 0), LocalDateTime.of(2022, 7, 1, 23, 59))
                .operationBetween(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 3))
                .submittedBetween(LocalDateTime.of(2022, 7, 1, 0, 0), LocalDateTime.of(2022, 7, 1, 23, 59, 59))
                .submissionStatus(KontsultaQuery.SubmissionStatus.ACCEPTED).recordStatus(KontsultaRecordStatus.CORRECT)
                .showErrors(true);

        assertSame(DIR + "gipuzkoa/kontsultaV1.0.xml", KontsultaXmlBuilder.query(query));
        assertEquals(List.of(), XsdValidator.validate(Examples.read(DIR + "gipuzkoa/kontsultaV1.0.xml"),
                XsdValidator.Schema.KONTSULTA_GIPUZKOA));
        assertEquals(List.of(), XsdValidator.validate(bytes(KontsultaXmlBuilder.query(query)), XsdValidator.Schema.KONTSULTA_GIPUZKOA));
    }

    @Test
    @DisplayName("Álava: la petición oficial (sin sus elementos vacíos) sale igual y cumple el XSD 1.2.3")
    void rebuildsArabaQuery() {
        KontsultaQuery query = KontsultaQuery.of(Territory.ARABA, NIF, Year.of(2025), KontsultaQuery.Period.ANNUAL)
                .submissionStatus(KontsultaQuery.SubmissionStatus.ACCEPTED_WITH_ERRORS)
                .recordStatus(KontsultaRecordStatus.CORRECT).showErrors(true);

        assertSame(DIR + "araba/consulta-paginada.xml", KontsultaXmlBuilder.query(query), Set.of("CabeceraFactura"));
        assertEquals(List.of(), XsdValidator.validate(Examples.read(DIR + "araba/consulta-paginada.xml"),
                XsdValidator.Schema.KONTSULTA_ARABA));
        assertEquals(List.of(), XsdValidator.validate(bytes(KontsultaXmlBuilder.query(query)), XsdValidator.Schema.KONTSULTA_ARABA));
        KontsultaQuery minimal = KontsultaQuery.of(Territory.ARABA, NIF, Year.of(2025), KontsultaQuery.Period.Q2).page(4);
        assertEquals(List.of(), XsdValidator.validate(bytes(KontsultaXmlBuilder.query(minimal)), XsdValidator.Schema.KONTSULTA_ARABA));
    }

    @Test
    @DisplayName("Última factura: las peticiones oficiales de los dos territorios salen iguales y cumplen su XSD")
    void rebuildsLatestRequests() {
        Document latest = KontsultaXmlBuilder.latest(NIF);

        assertSame(DIR + "araba/ultima-factura.xml", latest);
        assertSame(DIR + "gipuzkoa/kontsultaUltimaFacturaV1.0.xml", latest);
        assertEquals(List.of(), XsdValidator.validate(bytes(latest), XsdValidator.Schema.KONTSULTA_LATEST_ARABA));
        assertEquals(List.of(), XsdValidator.validate(bytes(latest), XsdValidator.Schema.KONTSULTA_LATEST_GIPUZKOA));
    }

    @Test
    @DisplayName("Estados por territorio: Modificada solo en Gipuzkoa, Sustituida solo en Álava; Bizkaia no tiene Kontsulta")
    void territoryRules() {
        KontsultaQuery gipuzkoa = KontsultaQuery.of(Territory.GIPUZKOA, NIF, Year.of(2025), KontsultaQuery.Period.ANNUAL);
        KontsultaQuery araba = KontsultaQuery.of(Territory.ARABA, NIF, Year.of(2025), KontsultaQuery.Period.ANNUAL);

        gipuzkoa.recordStatus(KontsultaRecordStatus.MODIFIED);
        araba.recordStatus(KontsultaRecordStatus.REPLACED);
        assertThrows(IllegalArgumentException.class, () -> gipuzkoa.recordStatus(KontsultaRecordStatus.REPLACED));
        assertThrows(IllegalArgumentException.class, () -> araba.recordStatus(KontsultaRecordStatus.MODIFIED));
        assertEquals(List.of(), XsdValidator.validate(bytes(KontsultaXmlBuilder.query(gipuzkoa)), XsdValidator.Schema.KONTSULTA_GIPUZKOA));
        assertEquals(List.of(), XsdValidator.validate(bytes(KontsultaXmlBuilder.query(araba)), XsdValidator.Schema.KONTSULTA_ARABA));
        assertThrows(IllegalArgumentException.class, () -> KontsultaQuery.of(Territory.BIZKAIA, NIF, Year.of(2025),
                KontsultaQuery.Period.ANNUAL));
        assertThrows(IllegalArgumentException.class, () -> araba.page(0));
        assertThrows(IllegalArgumentException.class, () -> KontsultaQuery.Period.month(13));
        assertEquals(KontsultaQuery.Period.DECEMBER, KontsultaQuery.Period.month(12));
    }

    @Test
    @DisplayName("URLs oficiales; la producción de Gipuzkoa no está publicada y se exige explícita")
    void endpoints() {
        assertEquals(URI.create("https://ticketbai.araba.eus/TicketBAIConsultas/v1/facturaspaginadas/"),
                KontsultaClient.endpoint(Territory.ARABA, Environment.PRODUCTION, false));
        assertEquals(URI.create("https://pruebas-ticketbai.araba.eus/TicketBAIConsultas/v1/ultimafactura"),
                KontsultaClient.endpoint(Territory.ARABA, Environment.TEST, true));
        assertEquals(URI.create("https://tbai-k.prep.gipuzkoa.eus/kontsulta/azkena"),
                KontsultaClient.endpoint(Territory.GIPUZKOA, Environment.TEST, true));
        assertThrows(IllegalArgumentException.class, () -> KontsultaClient.endpoint(Territory.GIPUZKOA, Environment.PRODUCTION, false));
        assertThrows(IllegalArgumentException.class, () -> KontsultaClient.endpoint(Territory.BIZKAIA, Environment.TEST, false));
    }

    @Test
    @DisplayName("Álava: lee la respuesta (cumple el XSD 1.2.3), con estados, errores y el eslabón de cada factura")
    void parsesArabaResponse() {
        byte[] xml = Examples.read(DIR + "araba/respuesta-sdk.xml");

        KontsultaResult result = KontsultaResponseParser.parse(xml, 200);

        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.KONTSULTA_RESPONSE_ARABA));
        assertEquals(NIF, result.getIssuerNif());
        assertEquals("EMISOR DE PRUEBA SL", result.getIssuerName());
        assertTrue(result.hasRecords());
        assertEquals(2, result.getTotalPages());
        assertTrue(result.hasMoreRecords());
        assertEquals(3, result.getTotalRecords());
        assertNull(result.getErrorDescription());

        KontsultaResult.Invoice replaced = result.getInvoices().get(0);
        assertEquals(KontsultaRecordStatus.REPLACED, replaced.getStatus());
        assertEquals(LocalDateTime.of(2025, 3, 14, 10, 30), replaced.getIssuedAt());
        assertEquals(new BigDecimal("121.00"), replaced.getTotalAmount());
        assertEquals("TBAI-99999972C-140325-K3mQ8vT1aXy0Z-081", TbaiIdentifier.parse(replaced.getIdentifier()).toString());

        KontsultaResult.Invoice last = result.latest().orElseThrow();
        assertEquals("0008", last.getNumber());
        assertEquals(List.of("011", "1103"), last.getTicketBaiErrorCodes());
        ChainLink link = last.toChainLink();
        assertEquals("A2025", link.getSeries());
        assertEquals(LocalDate.of(2025, 3, 15), link.getIssueDate());
        assertEquals(last.getSignatureValuePrefix(), link.getSignatureValuePrefix());
    }

    @Test
    @DisplayName("Gipuzkoa: lee la respuesta 1.0 (hora opcional, sin serie, fecha de alta, estado Modificada)")
    void parsesGipuzkoaResponse() {
        KontsultaResult result = KontsultaResponseParser.parse(Examples.read(DIR + "gipuzkoa/respuesta-sdk.xml"), 200);

        KontsultaResult.Invoice invoice = result.getInvoices().get(0);
        assertNull(invoice.getSeries());
        assertEquals(LocalDateTime.of(2025, 3, 15, 0, 0), invoice.getIssuedAt());
        assertEquals(KontsultaRecordStatus.MODIFIED, invoice.getStatus());
        assertEquals("15-03-2025 12:00:04", invoice.getRegisteredAt());
        assertFalse(result.hasMoreRecords());
        assertNull(result.getIssuerName());
    }

    @Test
    @DisplayName("Consulta y última factura por TLS mutuo; HTTP de error o respuesta sin resultado es un fallo")
    void queriesOverMutualTls() throws Exception {
        KontsultaQuery query = KontsultaQuery.of(Territory.ARABA, NIF, Year.of(2025), KontsultaQuery.Period.Q1).series("A2025");
        byte[] response = Examples.read(DIR + "araba/respuesta-sdk.xml");

        try (MutualTlsServer server = new MutualTlsServer()) {
            KontsultaClient client = new KontsultaClient(server.uri("/TicketBAIConsultas/v1/facturaspaginadas/"),
                    server.uri("/TicketBAIConsultas/v1/ultimafactura"), tls(), Duration.ofSeconds(5), Duration.ofSeconds(5));
            server.respond(200, response, Map.of());

            assertEquals(2, client.query(query).getInvoices().size());
            assertEquals("/TicketBAIConsultas/v1/facturaspaginadas/", server.getLastPath());
            assertEquals("POST", server.getLastMethod());
            assertEquals("application/xml;charset=UTF-8", server.getLastContentType());
            assertArrayEquals(bytes(KontsultaXmlBuilder.query(query)), server.getLastBody());

            client.latest(NIF);
            assertEquals("/TicketBAIConsultas/v1/ultimafactura", server.getLastPath());
            assertArrayEquals(bytes(KontsultaXmlBuilder.latest(NIF)), server.getLastBody());

            server.respond(500, new byte[0], Map.of());
            assertEquals(500, assertThrows(TransportException.class, () -> client.query(query)).getHttpStatus());
            server.respond(200, "<x/>".getBytes(), Map.of());
            assertThrows(TransportException.class, () -> client.latest(NIF));
        }
    }

    private static void assertSame(String example, Document actual) {
        assertSame(example, actual, Set.of());
    }

    private static void assertSame(String example, Document actual, Set<String> ignored) {
        List<String> diffs = XmlTreeComparator.differences(Xml.parse(Examples.read(example)).getDocumentElement(),
                actual.getDocumentElement(), ignored);
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));
    }

    private static byte[] bytes(Document doc) {
        return KontsultaXmlBuilder.toBytes(doc);
    }

    private static TlsConfig tls() throws Exception {
        try (InputStream in = KontsultaTest.class.getResourceAsStream("/certs/test-signer.p12")) {
            return TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray())
                    .trust(MutualTlsServer.certificate("/certs/test-server.cer")).build();
        }
    }
}
