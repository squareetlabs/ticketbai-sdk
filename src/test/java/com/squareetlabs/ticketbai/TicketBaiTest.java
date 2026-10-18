package com.squareetlabs.ticketbai;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.chain.SignedInvoiceReader;
import com.squareetlabs.ticketbai.identifier.Crc8;
import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.signature.SignatureVerifier;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketBaiTest {

    @Test
    @DisplayName("Emite tres facturas encadenadas y anula la última, todo firmado, válido y con su código y QR")
    void issueChainAndAnnul() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            TicketBai tbai = TicketBai.builder().territory(Territory.GIPUZKOA).environment(Environment.TEST)
                    .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();

            ChainLink previous = null;
            IssuedInvoice last = null;
            for (int n = 1; n <= 3; n++) {
                IssuedInvoice issued = tbai.issue(TestInvoices.simple(String.valueOf(n)), previous);
                ChainLink link = SignedInvoiceReader.read(issued.getSignedXml().getBytes()).getPreviousLink();
                if (n == 1) {
                    assertNull(link, "La primera no encadena");
                } else {
                    assertEquals(previous, link, "La factura " + n + " encadena la anterior");
                }
                assertEquals(List.of(), SignatureVerifier.verify(issued.getSignedXml().getBytes(), Territory.GIPUZKOA));
                previous = issued.getChainLink();
                last = issued;
            }

            String qr = last.getQrUrl();
            assertTrue(qr.startsWith("https://tbai.prep.gipuzkoa.eus/qr/?id=" + last.getIdentifier().getValue()), qr);
            assertTrue(qr.contains("&s=F2026&nf=3&i=1210.00&cr="), qr);
            assertEquals(Crc8.compute(qr.substring(0, qr.lastIndexOf("&cr="))), qr.substring(qr.lastIndexOf('=') + 1));

            SignedXml annulment = tbai.annul(Annulment.of(TestInvoices.simple("3")));
            assertTrue(XsdValidator.validate(annulment.getBytes(), XsdValidator.Schema.ANNULMENT).isEmpty());
            assertEquals(List.of(), SignatureVerifier.verify(annulment.getBytes(), Territory.GIPUZKOA));
            String xml = new String(annulment.getBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<NumFactura>3</NumFactura>") && xml.contains("<SerieFactura>F2026</SerieFactura>"), xml);
        }
    }

    @Test
    @DisplayName("Un dato que no cumple el XSD (texto demasiado largo) falla al emitir, antes de enviar nada")
    void invalidDataFailsBeforeSending() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            TicketBai tbai = TicketBai.builder().territory(Territory.ARABA).environment(Environment.TEST)
                    .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();
            Invoice tooLongSeries = Invoice.builder()
                    .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL")
                    .series("SERIE-DEMASIADO-LARGA-PARA-TICKETBAI").number("1")
                    .issuedAt(java.time.LocalDate.of(2026, 10, 18), java.time.LocalTime.NOON)
                    .description("d").totalAmount(java.math.BigDecimal.TEN)
                    .regimeKey(com.squareetlabs.ticketbai.model.Codes.RegimeKey.GENERAL)
                    .breakdown(TestInvoices.simple("1").getBreakdown())
                    .build();

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> tbai.issue(tooLongSeries, null));
            assertTrue(e.getMessage().contains("SerieFactura") || e.getMessage().contains("20"), e.getMessage());
        }
    }
}
