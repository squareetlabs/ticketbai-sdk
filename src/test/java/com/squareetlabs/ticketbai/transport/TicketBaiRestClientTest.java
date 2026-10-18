package com.squareetlabs.ticketbai.transport;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.IssuedInvoice;
import com.squareetlabs.ticketbai.TicketBai;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.MutualTlsServer;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.transport.rest.RestEndpoints;
import com.squareetlabs.ticketbai.transport.rest.TicketBaiRestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBaiRestClientTest {

    private MutualTlsServer server;
    private IssuedInvoice issued;

    @BeforeEach
    void setUp() throws Exception {
        server = new MutualTlsServer();
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            issued = TicketBai.builder().territory(Territory.ARABA).environment(Environment.TEST)
                    .software(TestInvoices.TEST_SOFTWARE).signer(signer).build()
                    .issue(TestInvoices.simple("1"), null);
        }
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("Envía el fichero firmado tal cual por POST application/xml con TLS mutuo y lee la respuesta con avisos")
    void submitsAndReadsAReceivedResponseWithWarnings() throws Exception {
        server.respond(200, Examples.read("araba/ejemplo2-respuesta.xml"));

        SubmissionResult result = client(tls(true)).submitInvoice(issued.getSignedXml());

        assertEquals("POST", server.getLastMethod());
        assertEquals("application/xml;charset=UTF-8", server.getLastContentType());
        assertArrayEquals(issued.getSignedXml().getBytes(), server.getLastBody(), "El fichero llega sin cambios");
        assertTrue(result.isReceived());
        assertEquals("00", result.getStatusCode());
        assertEquals("TBAI-A01111111-051121-a6f7Gx3zT2h52-080", result.getIdentifier());
        assertEquals("05-11-2021 12:06:09", result.getReceivedAt());
        assertEquals("Recibido", result.getDescription());
        assertEquals("009,011", result.getValidationResults().stream().map(SubmissionResult.ValidationResult::getCode)
                .collect(Collectors.joining(",")));
        assertArrayEquals(Examples.read("araba/ejemplo2-respuesta.xml"), result.getRawResponse());
    }

    @Test
    @DisplayName("Un rechazo (01) llega como resultado con sus códigos, no como excepción")
    void rejectedResponse() {
        server.respond(200, ("<ns2:TicketBaiResponse xmlns:ns2=\"urn:ticketbai:emision\"><Salida>"
                + "<FechaRecepcion>18-10-2026 10:00:00</FechaRecepcion><Estado>01</Estado><Descripcion>Rechazado</Descripcion>"
                + "<Azalpena>Baztertua</Azalpena><ResultadosValidacion><Codigo>005</Codigo>"
                + "<Descripcion>Factura ya registrada</Descripcion><Azalpena>Faktura erregistratuta</Azalpena>"
                + "</ResultadosValidacion></Salida></ns2:TicketBaiResponse>").getBytes(StandardCharsets.UTF_8));

        SubmissionResult result = client(tls(true)).submitAnnulment(issued.getSignedXml());

        assertEquals(SubmissionResult.Status.REJECTED, result.getStatus());
        assertEquals("005", result.getValidationResults().get(0).getCode());
        assertTrue(server.uri("/baja").getPath().endsWith("/baja"));
    }

    @Test
    @DisplayName("Sin confiar en el certificado del servidor, la conexión falla (nunca se desactiva la comprobación)")
    void untrustedServer() {
        TransportException e = assertThrows(TransportException.class,
                () -> client(tls(false)).submitInvoice(issued.getSignedXml()));
        assertEquals(0, e.getHttpStatus());
    }

    @Test
    @DisplayName("Con un certificado de cliente que el servidor no acepta, la conexión falla")
    void clientCertificateNotAccepted() throws Exception {
        TlsConfig wrongClient;
        try (InputStream in = getClass().getResourceAsStream("/certs/test-server.p12")) {
            wrongClient = TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray())
                    .trust(MutualTlsServer.certificate("/certs/test-server.cer")).build();
        }
        TicketBaiRestClient client = client(wrongClient);

        assertThrows(TransportException.class, () -> client.submitInvoice(issued.getSignedXml()));
    }

    @Test
    @DisplayName("Un error HTTP o una respuesta con formato inesperado es un fallo de transporte (reintentar)")
    void httpErrorAndUnexpectedBody() {
        server.respond(500, "error".getBytes(StandardCharsets.UTF_8));
        TransportException http = assertThrows(TransportException.class,
                () -> client(tls(true)).submitInvoice(issued.getSignedXml()));
        assertEquals(500, http.getHttpStatus());

        server.respond(200, "<html>mantenimiento</html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(TransportException.class, () -> client(tls(true)).submitInvoice(issued.getSignedXml()));
    }

    @Test
    @DisplayName("Bizkaia no tiene servicio REST de TicketBAI: va por el LROE")
    void bizkaiaHasNoRestEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> RestEndpoints.of(Territory.BIZKAIA, Environment.TEST));
        assertEquals("https://tbai-z.prep.gipuzkoa.eus/sarrerak/alta",
                RestEndpoints.of(Territory.GIPUZKOA, Environment.TEST).getInvoices().toString());
        assertEquals("https://ticketbai.araba.eus/TicketBAI/v1/anulaciones/",
                RestEndpoints.of(Territory.ARABA, Environment.PRODUCTION).getAnnulments().toString());
    }

    private TicketBaiRestClient client(TlsConfig tls) {
        return new TicketBaiRestClient(new RestEndpoints(server.uri("/alta"), server.uri("/baja")), tls,
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    private static TlsConfig tls(boolean trustServer) {
        try (InputStream in = TicketBaiRestClientTest.class.getResourceAsStream("/certs/test-signer.p12")) {
            TlsConfig.Builder builder = TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray());
            if (trustServer) {
                builder.trust(MutualTlsServer.certificate("/certs/test-server.cer"));
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Las raíces Sectigo R46 y E46 vienen incluidas, con las huellas comprobadas")
    void bundledSectigoRoots() throws Exception {
        java.util.List<java.security.cert.X509Certificate> roots = TlsConfig.bundledRoots();
        java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");

        assertEquals(2, roots.size());
        assertEquals("7BB647A62AEEAC88BF257AA522D01FFEA395E0AB45C73F93F65654EC38F25A06",
                hex(sha256.digest(roots.get(0).getEncoded())));
        assertEquals("C90F26F0FB1B4018B22227519B5CA2B53E2CA5B3BE5CF18EFE1BEF47380C5383",
                hex(sha256.digest(roots.get(1).getEncoded())));
        assertTrue(roots.get(0).getSubjectX500Principal().getName().contains("Sectigo Public Server Authentication Root R46"));
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
