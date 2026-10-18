package com.squareetlabs.ticketbai.signature;

import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.chain.SignedInvoice;
import com.squareetlabs.ticketbai.chain.SignedInvoiceReader;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import com.squareetlabs.ticketbai.xml.InvoiceXmlBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBaiSignerTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Territory.class)
    @DisplayName("El fichero firmado cumple el XSD, la firma es válida y lleva la política del territorio")
    void signsWithTheTerritoryPolicy(Territory territory) {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            SignedXml signed = signer.sign(InvoiceXmlBuilder.build(TestInvoices.simple("1"), null,
                    TestInvoices.TEST_SOFTWARE, null), territory);

            List<String> xsd = XsdValidator.validate(signed.getBytes(), XsdValidator.Schema.INVOICE);
            assertTrue(xsd.isEmpty(), xsd.toString());
            assertEquals(List.of(), SignatureVerifier.verify(signed.getBytes(), territory));
            String xml = new String(signed.getBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains(territory.getSignaturePolicy().getIdentifier()), "Lleva la política");
            assertTrue(xml.contains("SigningCertificate"), "Lleva SigningCertificate");
            assertTrue(xml.contains("http://www.w3.org/2000/09/xmldsig#enveloped-signature"), "Firma enveloped");
        }
    }

    @Test
    @DisplayName("La firma de un territorio no vale con la política de otro")
    void policyOfAnotherTerritory() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            SignedXml signed = signer.sign(InvoiceXmlBuilder.build(TestInvoices.simple("1"), null,
                    TestInvoices.TEST_SOFTWARE, null), Territory.GIPUZKOA);

            assertFalse(SignatureVerifier.verify(signed.getBytes(), Territory.ARABA).isEmpty());
        }
    }

    @Test
    @DisplayName("Un fichero firmado y luego modificado no pasa la verificación")
    void tamperedFile() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            SignedXml signed = signer.sign(InvoiceXmlBuilder.build(TestInvoices.simple("1"), null,
                    TestInvoices.TEST_SOFTWARE, null), Territory.GIPUZKOA);
            byte[] tampered = new String(signed.getBytes(), StandardCharsets.UTF_8)
                    .replace("<ImporteTotalFactura>1210.00<", "<ImporteTotalFactura>1.00<").getBytes(StandardCharsets.UTF_8);

            assertFalse(SignatureVerifier.verify(tampered, Territory.GIPUZKOA).isEmpty());
        }
    }

    @Test
    @DisplayName("Dos facturas seguidas: la segunda encadena la firma de la primera y el código sale de su propia firma")
    void chainOfTwoInvoices() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            SignedXml first = signer.sign(InvoiceXmlBuilder.build(TestInvoices.simple("1"), null,
                    TestInvoices.TEST_SOFTWARE, null), Territory.ARABA);
            ChainLink link = SignedInvoiceReader.read(first.getBytes()).toChainLink();
            SignedXml second = signer.sign(InvoiceXmlBuilder.build(TestInvoices.simple("2"), link,
                    TestInvoices.TEST_SOFTWARE, null), Territory.ARABA);

            SignedInvoice read = SignedInvoiceReader.read(second.getBytes());
            assertEquals(first.getSignatureValue().substring(0, 100), read.getPreviousLink().getSignatureValuePrefix());
            assertEquals("1", read.getPreviousLink().getNumber());
            assertTrue(read.toTbaiIdentifier().getValue().startsWith(
                    "TBAI-B12345674-181026-" + second.getSignatureValue().substring(0, 13) + "-"));
            assertTrue(XsdValidator.validate(second.getBytes(), XsdValidator.Schema.INVOICE).isEmpty());
        }
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"araba/ejemplo1-factura.xml", "araba/ejemplo2-rectificativa.xml",
            "araba/ejemplo3-anulacion.xml"})
    @DisplayName("La firma real de los ejemplos oficiales de Álava es válida; solo cambia el hash de su política (2022)")
    void officialArabaSignaturesAreValid(String example) {
        List<String> problems = SignatureVerifier.verify(Examples.read(example), Territory.ARABA);

        // Los ejemplos se firmaron con la política anterior a la especificación de 30/05/2022: su hash no es el publicado
        // hoy. La firma y todas sus referencias sí son válidas. (Los ejemplos de Gipuzkoa no sirven para esta prueba: se
        // reformatearon después de firmarlos y su firma ya no valida.)
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("El hash de la política de firma"), problems.get(0));
    }
}
