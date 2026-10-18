package com.squareetlabs.ticketbai.support;

import com.squareetlabs.ticketbai.model.Breakdown;
import com.squareetlabs.ticketbai.model.Codes.NonExemptType;
import com.squareetlabs.ticketbai.model.Codes.RegimeKey;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceLine;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.model.TaxBreakdown;
import com.squareetlabs.ticketbai.model.VatDetail;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** Datos de prueba comunes: una factura sencilla, el software de pruebas y el certificado de pruebas. */
public final class TestInvoices {

    public static final Software TEST_SOFTWARE = new Software("TBAIGIPRE00000000001", Party.withNif("A99800005", null),
            "SOFTWARE GARANTE TICKETBAI PRUEBA", "1.0");

    private TestInvoices() {
    }

    /** Factura de 1.000 € + 21 % de IVA a un cliente con NIF y dirección. */
    public static Invoice simple(String number) {
        return Invoice.builder()
                .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL")
                .recipient(Party.withNif("11111111H", "CLIENTE DE PRUEBA").withAddress("20001", "Calle Mayor 1"))
                .series("F2026").number(number)
                .issuedAt(LocalDate.of(2026, 10, 18), LocalTime.of(10, 30))
                .description("Certificación de obra")
                .line(new InvoiceLine("Partida 1", new BigDecimal("1"), new BigDecimal("1000"), null, new BigDecimal("1210")))
                .totalAmount(new BigDecimal("1210"))
                .regimeKey(RegimeKey.GENERAL)
                .breakdown(Breakdown.ofInvoice(TaxBreakdown.builder()
                        .nonExempt(NonExemptType.S1, VatDetail.of(new BigDecimal("1000"), new BigDecimal("21"),
                                new BigDecimal("210")))
                        .build()))
                .build();
    }

    public static TicketBaiSigner signer() {
        return TicketBaiSigner.pkcs12(TestInvoices.class.getResourceAsStream("/certs/test-signer.p12"),
                "ticketbai-test".toCharArray());
    }
}
