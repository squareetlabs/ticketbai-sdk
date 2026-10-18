package com.squareetlabs.ticketbai;

import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.ZuzenduAction;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.MutualTlsServer;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.transport.SubmissionResult;
import com.squareetlabs.ticketbai.transport.TlsConfig;
import com.squareetlabs.ticketbai.transport.rest.RestEndpoints;
import com.squareetlabs.ticketbai.transport.rest.TicketBaiRestClient;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.util.XmlWriter;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZuzenduTest {

    private TicketBaiSigner signer;
    private TicketBai tbai;
    private IssuedInvoice original;

    @BeforeEach
    void setUp() {
        signer = TestInvoices.signer();
        tbai = TicketBai.builder().territory(Territory.GIPUZKOA).environment(Environment.TEST)
                .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();
        original = tbai.issue(TestInvoices.simple("7"), null);
    }

    @Test
    @DisplayName("Subsanar: cumple el XSD, lleva la huella del original sin cambios y los 100 caracteres de su firma, sin firmar")
    void invoiceCorrection() {
        Invoice corrected = invoiceWithRecipient(Party.withNif("11111111H", "CLIENTE CORREGIDO").withAddress("20002", "Calle Nueva 2"),
                new BigDecimal("1210"));

        ZuzenduFile file = tbai.correct(corrected, original.getSignedXml(), ZuzenduAction.SUBSANAR);

        assertEquals(List.of(), XsdValidator.validate(file.getBytes(), XsdValidator.Schema.ZUZENDU_INVOICE));
        Element root = Xml.parse(file.getBytes()).getDocumentElement();
        assertEquals("urn:ticketbai:zuzendu-alta", root.getNamespaceURI());
        assertEquals("SUBSANAR", Xml.text(root, "Accion"));
        assertEquals("1.0", Xml.text(root, "IDVersion"));
        assertEquals(original.getSignedXml().getSignatureValue().substring(0, 100), Xml.text(root, "SignatureValueFirmaFactura"));
        assertEquals("CLIENTE CORREGIDO", Xml.text(Xml.firstChild(root, "Destinatarios"), "ApellidosNombreRazonSocial"));
        assertEquals(0, root.getElementsByTagNameNS(Xml.DSIG_NS, "Signature").getLength(), "Zuzendu no se firma");
        assertArrayEquals(huella(original.getSignedXml().getBytes()), huella(file.getBytes()), "Misma huella que el original");
    }

    @Test
    @DisplayName("Modificar no admite cambiar el importe total; ninguna acción admite cambiar el número")
    void immutableData() {
        Invoice base = TestInvoices.simple("7");
        Invoice otherTotal = Invoice.builder()
                .issuer(base.getIssuer().getNif(), base.getIssuer().getName())
                .recipient(base.getRecipients().get(0))
                .series(base.getSeries()).number(base.getNumber()).issuedAt(base.getIssueDate(), LocalTime.of(10, 30))
                .description(base.getDescription())
                .line(new com.squareetlabs.ticketbai.model.InvoiceLine("Partida 1", BigDecimal.ONE, new BigDecimal("1100"), null,
                        new BigDecimal("1331")))
                .totalAmount(new BigDecimal("1331"))
                .regimeKey(base.getRegimeKeys().get(0))
                .breakdown(com.squareetlabs.ticketbai.model.Breakdown.ofInvoice(com.squareetlabs.ticketbai.model.TaxBreakdown.builder()
                        .nonExempt(com.squareetlabs.ticketbai.model.Codes.NonExemptType.S1,
                                com.squareetlabs.ticketbai.model.VatDetail.of(new BigDecimal("1100"), new BigDecimal("21"),
                                        new BigDecimal("231")))
                        .build()))
                .build();
        IllegalArgumentException total = assertThrows(IllegalArgumentException.class,
                () -> tbai.correct(otherTotal, original.getSignedXml(), ZuzenduAction.MODIFICAR));
        assertTrue(total.getMessage().contains("rectificativa"), total.getMessage());

        IllegalArgumentException number = assertThrows(IllegalArgumentException.class,
                () -> tbai.correct(TestInvoices.simple("8"), original.getSignedXml(), ZuzenduAction.SUBSANAR));
        assertTrue(number.getMessage().contains("el número"), number.getMessage());

        assertTrue(XsdValidator.validate(tbai.correct(TestInvoices.simple("7"), original.getSignedXml(), ZuzenduAction.MODIFICAR)
                .getBytes(), XsdValidator.Schema.ZUZENDU_INVOICE).isEmpty());
    }

    @Test
    @DisplayName("Subsanar una anulación: sus datos (o los corregidos), su huella y su firma, sin firmar")
    void annulmentCorrection() {
        SignedXml annulment = tbai.annul(Annulment.of(TestInvoices.simple("7")));

        ZuzenduFile same = tbai.correctAnnulment(null, annulment);
        ZuzenduFile fixed = tbai.correctAnnulment(new Annulment("B12345674", "CONSTRUCCIONES DE PRUEBA SL", "F2026", "77",
                LocalDate.of(2026, 10, 18)), annulment);

        assertEquals(List.of(), XsdValidator.validate(same.getBytes(), XsdValidator.Schema.ZUZENDU_ANNULMENT));
        assertEquals(List.of(), XsdValidator.validate(fixed.getBytes(), XsdValidator.Schema.ZUZENDU_ANNULMENT));
        Element root = Xml.parse(fixed.getBytes()).getDocumentElement();
        assertEquals("urn:ticketbai:zuzendu-baja", root.getNamespaceURI());
        assertEquals("77", Xml.text(root, "NumFactura"));
        assertEquals(annulment.getSignatureValue().substring(0, 100), Xml.text(root, "SignatureValueFirmaAnulacion"));
        assertEquals(ZuzenduFile.Kind.ANNULMENT, fixed.getKind());
    }

    @Test
    @DisplayName("El fichero original tiene que ser del tipo correcto, y Bizkaia no tiene Zuzendu")
    void wrongOriginalOrTerritory() {
        assertThrows(IllegalArgumentException.class, () -> tbai.correctAnnulment(null, original.getSignedXml()));
        TicketBai bizkaia = TicketBai.builder().territory(Territory.BIZKAIA).environment(Environment.TEST)
                .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();
        assertThrows(IllegalStateException.class,
                () -> bizkaia.correct(TestInvoices.simple("7"), original.getSignedXml(), ZuzenduAction.SUBSANAR));
    }

    @Test
    @DisplayName("Se envía al servicio Zuzendu con TLS mutuo; las URL son las de cada territorio")
    void submitsToTheZuzenduService() throws Exception {
        ZuzenduFile file = tbai.correct(TestInvoices.simple("7"), original.getSignedXml(), ZuzenduAction.MODIFICAR);
        try (MutualTlsServer server = new MutualTlsServer()) {
            server.respond(200, Examples.read("araba/ejemplo1-respuesta.xml"));
            TicketBaiRestClient client = new TicketBaiRestClient(new RestEndpoints(server.uri("/alta"), server.uri("/baja"),
                    server.uri("/zuzendu-alta"), server.uri("/zuzendu-baja")), tls(), Duration.ofSeconds(5), Duration.ofSeconds(5));

            SubmissionResult result = client.submitCorrection(file);

            assertTrue(result.isReceived());
            assertArrayEquals(file.getBytes(), server.getLastBody());
            assertEquals("application/xml;charset=UTF-8", server.getLastContentType());
        }
        assertEquals("https://tbai-z.egoitza.gipuzkoa.eus/sarrerak/zuzendu-alta",
                RestEndpoints.of(Territory.GIPUZKOA, Environment.PRODUCTION).getInvoiceCorrections().toString());
        assertEquals("https://pruebas-ticketbai.araba.eus/TicketBAI/v1/facturas/subsanarmodificar",
                RestEndpoints.of(Territory.ARABA, Environment.TEST).getInvoiceCorrections().toString());
        assertEquals("https://ticketbai.araba.eus/TicketBAI/v1/anulaciones/baja",
                RestEndpoints.of(Territory.ARABA, Environment.PRODUCTION).getAnnulmentCorrections().toString());
        assertFalse(RestEndpoints.of(Territory.GIPUZKOA, Environment.TEST).getAnnulmentCorrections().toString().isEmpty());
    }

    private static Invoice invoiceWithRecipient(Party recipient, BigDecimal total) {
        Invoice base = TestInvoices.simple("7");
        Invoice.Builder b = Invoice.builder()
                .issuer(base.getIssuer().getNif(), base.getIssuer().getName())
                .recipient(recipient)
                .series(base.getSeries()).number(base.getNumber())
                .issuedAt(base.getIssueDate(), LocalTime.of(10, 30))
                .description(base.getDescription())
                .totalAmount(total)
                .regimeKey(base.getRegimeKeys().get(0))
                .breakdown(base.getBreakdown());
        base.getLines().forEach(b::line);
        return b.build();
    }

    private static byte[] huella(byte[] xml) {
        Document doc = Xml.parse(xml);
        return XmlWriter.toBytes(Xml.firstChild(doc.getDocumentElement(), "HuellaTBAI"));
    }

    private static TlsConfig tls() throws Exception {
        try (InputStream in = ZuzenduTest.class.getResourceAsStream("/certs/test-signer.p12")) {
            return TlsConfig.builder().clientCertificate(in.readAllBytes(), "ticketbai-test".toCharArray())
                    .trust(MutualTlsServer.certificate("/certs/test-server.cer")).build();
        }
    }
}
