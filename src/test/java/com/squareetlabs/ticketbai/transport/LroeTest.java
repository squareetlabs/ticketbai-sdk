package com.squareetlabs.ticketbai.transport;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.TicketBai;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.signature.SignatureVerifier;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.MutualTlsServer;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.transport.lroe.LroeBatch;
import com.squareetlabs.ticketbai.transport.lroe.LroeClient;
import com.squareetlabs.ticketbai.transport.lroe.LroeOperation;
import com.squareetlabs.ticketbai.transport.lroe.LroeRequestBuilder;
import com.squareetlabs.ticketbai.transport.lroe.LroeResponseParser;
import com.squareetlabs.ticketbai.transport.lroe.LroeResult;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LroeTest {

    private static final String DIR = "bizkaia-lroe/";
    private static final String REQUEST = DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034.xml";

    @Test
    @DisplayName("Los envíos oficiales de alta y anulación del modelo 240 cumplen sus XSD")
    void officialRequestsAreValid() {
        assertEquals(List.of(), XsdValidator.validate(Examples.read(REQUEST), XsdValidator.Schema.LROE_240_INVOICES));
        assertEquals(List.of(), XsdValidator.validate(Examples.read(DIR + "Ejemplo_Anulacion_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034.xml"),
                XsdValidator.Schema.LROE_240_ANNULMENTS));
    }

    @Test
    @DisplayName("Los TicketBAI del envío oficial son ficheros de Bizkaia válidos: XSD y firma con la política de Bizkaia")
    void officialTicketBaiFilesAreValidBizkaiaFiles() {
        for (byte[] file : officialTicketBaiFiles()) {
            assertEquals(List.of(), XsdValidator.validate(file, XsdValidator.Schema.INVOICE));
            List<String> problems = SignatureVerifier.verify(file, Territory.BIZKAIA);
            // La firma y sus referencias son válidas. Los ejemplos se firmaron con la política v1.0 de Bizkaia; el SDK
            // firma con la v1.2 vigente (OF 601/2025), así que solo difieren el identificador y el hash de la política.
            assertEquals(2, problems.size(), problems.toString());
            assertTrue(problems.stream().allMatch(p -> p.startsWith("El hash de la política") || p.startsWith("La política")),
                    problems.toString());
        }
    }

    @Test
    @DisplayName("Con los mismos ficheros, el envío construido es el oficial elemento a elemento")
    void rebuildsTheOfficialRequest() {
        List<SignedXml> files = officialTicketBaiFiles().stream().map(SignedXml::of).collect(Collectors.toList());
        LroeBatch batch = LroeBatch.of("B00000034", "HOTEL ADIBIDEZ", Year.of(2022), LroeOperation.INVOICES, files);

        Document built = LroeRequestBuilder.build(batch);
        Document official = Xml.parse(Examples.read(REQUEST));

        List<String> diffs = XmlTreeComparator.differences(official.getDocumentElement(), built.getDocumentElement(), Set.of());
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));
        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(batch), XsdValidator.Schema.LROE_240_INVOICES));
    }

    @Test
    @DisplayName("Un lote es de un solo obligado, de 1 a 1000 ficheros y del tipo de la operación")
    void batchRules() {
        List<SignedXml> files = officialTicketBaiFiles().stream().map(SignedXml::of).collect(Collectors.toList());

        assertThrows(IllegalArgumentException.class,
                () -> LroeBatch.of("A01111111", "OTRO", Year.of(2022), LroeOperation.INVOICES, files));
        assertThrows(IllegalArgumentException.class,
                () -> LroeBatch.of("B00000034", "HOTEL ADIBIDEZ", Year.of(2022), LroeOperation.ANNULMENTS, files));
        assertThrows(IllegalArgumentException.class,
                () -> LroeBatch.of("B00000034", "HOTEL ADIBIDEZ", Year.of(2022), LroeOperation.INVOICES, List.of()));
    }

    @Test
    @DisplayName("Lee las respuestas oficiales: correcta, parcialmente correcta, incorrecta y de anulación")
    void parsesOfficialResponses() {
        LroeResult ok = LroeResponseParser.parse("Correcto", "ID1", null, null, gunzip(DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Correcta_Resp.gz"));
        LroeResult partial = LroeResponseParser.parse("Parcialmente correcto", "ID2", null, null, gunzip(DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Parc_Correcta_Resp.gz"));
        LroeResult wrong = LroeResponseParser.parse("Incorrecto", null, "B4_1000002", "Error", gunzip(DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Incorrecta_Resp.gz"));
        LroeResult annul = LroeResponseParser.parse("Correcto", "ID3", null, null, gunzip(DIR + "Ejemplo_Anulacion_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Correcta_Resp.gz"));

        assertEquals(LroeResult.Status.CORRECT, ok.getStatus());
        assertEquals("28-04-2020 16:56:36", ok.getPresentedAt());
        assertEquals(List.of(LroeResult.RecordStatus.CORRECT, LroeResult.RecordStatus.CORRECT), statuses(ok));
        assertEquals("B2022", ok.getRecords().get(0).getSeries());
        assertEquals("0100", ok.getRecords().get(0).getNumber());
        assertTrue(statuses(partial).contains(LroeResult.RecordStatus.CORRECT));
        assertTrue(partial.getRecords().stream().anyMatch(r -> r.getErrorCode() != null), partial.getRecords().toString());
        assertEquals(LroeResult.Status.INCORRECT, wrong.getStatus());
        assertNull(wrong.getPresentedAt());
        assertEquals("B4_2000003", wrong.getRecords().get(0).getErrorCode());
        assertEquals("Registro duplicado.", wrong.getRecords().get(0).getErrorDescription());
        assertEquals(LroeResult.Status.CORRECT, annul.getStatus());
        assertEquals(2, annul.getRecords().size());
        assertEquals(List.of(), XsdValidator.validate(gunzip(DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Correcta_Resp.gz"),
                XsdValidator.Schema.LROE_240_INVOICES_RESPONSE));
    }

    @Test
    @DisplayName("Envía el lote con GZIP, las cabeceras n3 y TLS mutuo, y lee la respuesta comprimida")
    void submitsOverMutualTls() throws Exception {
        List<SignedXml> files = new ArrayList<>();
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            TicketBai tbai = TicketBai.builder().territory(Territory.BIZKAIA).environment(Environment.TEST)
                    .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();
            files.add(tbai.issue(TestInvoices.simple("1"), null).getSignedXml());
        }
        LroeBatch batch = LroeBatch.of("B12345674", "CONSTRUCCIONES DE PRUEBA, S.L. Ñ", Year.of(2026), LroeOperation.INVOICES, files);

        try (MutualTlsServer server = new MutualTlsServer()) {
            server.respond(200, gzip(Examples.read(DIR + "Ejemplo_1_LROE_PJ_240_FacturasEmitidasConSG_B00000034_Correcta_Resp.gz"), true),
                    Map.of("eus-bizkaia-n3-tipo-respuesta", "Correcto", "eus-bizkaia-n3-identificativo", "N3-ID-1",
                            "Content-Encoding", "gzip"));
            LroeClient client = new LroeClient(server.uri("/N3B4000M/aurkezpena"), tls(), Duration.ofSeconds(5), Duration.ofSeconds(5));

            LroeResult result = client.submit(batch);

            assertEquals("POST", server.getLastMethod());
            assertEquals("gzip", server.getLastHeader("Content-Encoding"));
            assertEquals("gzip", server.getLastHeader("Accept-Encoding"));
            assertEquals("application/octet-stream", server.getLastContentType());
            assertEquals("1.0", server.getLastHeader("eus-bizkaia-n3-version"));
            assertEquals("application/xml", server.getLastHeader("eus-bizkaia-n3-content-type"));
            assertEquals("{\"con\":\"LROE\",\"apa\":\"1.1\",\"inte\":{\"nif\":\"B12345674\",\"nrs\":\"CONSTRUCCIONES DE PRUEBA, S.L. \\u00d1\"},"
                    + "\"drs\":{\"mode\":\"240\",\"ejer\":\"2026\"}}", server.getLastHeader("eus-bizkaia-n3-data"));
            byte[] sent = gunzipBytes(server.getLastBody());
            assertArrayEquals(LroeRequestBuilder.toBytes(batch), sent);
            assertEquals(List.of(), XsdValidator.validate(sent, XsdValidator.Schema.LROE_240_INVOICES));
            String ticketBai = new String(Base64.getDecoder().decode(Xml.parse(sent).getElementsByTagNameNS("*", "TicketBai")
                    .item(0).getTextContent()), StandardCharsets.UTF_8);
            assertTrue(ticketBai.contains("<NumFactura>1</NumFactura>"));

            assertEquals(LroeResult.Status.CORRECT, result.getStatus());
            assertEquals("N3-ID-1", result.getEntryId());
            assertEquals(2, result.getRecords().size());
        }
    }

    @Test
    @DisplayName("Sin cabecera de tipo de respuesta, o con HTTP de error, es un fallo de transporte")
    void transportFailures() throws Exception {
        try (MutualTlsServer server = new MutualTlsServer()) {
            LroeClient client = new LroeClient(server.uri("/N3B4000M/aurkezpena"), tls(), Duration.ofSeconds(5), Duration.ofSeconds(5));
            LroeBatch batch = LroeBatch.of("B00000034", "HOTEL ADIBIDEZ", Year.of(2022), LroeOperation.INVOICES,
                    officialTicketBaiFiles().stream().map(SignedXml::of).collect(Collectors.toList()));

            server.respond(200, new byte[0], Map.of());
            assertThrows(TransportException.class, () -> client.submit(batch));
            server.respond(503, new byte[0], Map.of());
            assertEquals(503, assertThrows(TransportException.class, () -> client.submit(batch)).getHttpStatus());
        }
    }

    private static List<byte[]> officialTicketBaiFiles() {
        NodeList nodes = Xml.parse(Examples.read(REQUEST)).getElementsByTagNameNS("*", "TicketBai");
        List<byte[]> files = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            files.add(Base64.getMimeDecoder().decode(nodes.item(i).getTextContent().trim()));
        }
        assertEquals(2, files.size());
        return files;
    }

    private static List<LroeResult.RecordStatus> statuses(LroeResult result) {
        return result.getRecords().stream().map(LroeResult.Record::getStatus).collect(Collectors.toList());
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

    /** Devuelve {@code data} tal cual si ya está comprimido. */
    private static byte[] gzip(byte[] data, boolean alreadyGzipped) throws Exception {
        if (alreadyGzipped) {
            return data;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        }
        return out.toByteArray();
    }

    private static TlsConfig tls() throws Exception {
        try (InputStream in = LroeTest.class.getResourceAsStream("/certs/test-signer.p12")) {
            return TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray())
                    .trust(MutualTlsServer.certificate("/certs/test-server.cer")).build();
        }
    }
}
