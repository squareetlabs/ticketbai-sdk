package com.squareetlabs.ticketbai.xml;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.chain.SignedInvoice;
import com.squareetlabs.ticketbai.chain.SignedInvoiceReader;
import com.squareetlabs.ticketbai.model.Breakdown;
import com.squareetlabs.ticketbai.model.Codes.NonExemptType;
import com.squareetlabs.ticketbai.model.Codes.RectificationCode;
import com.squareetlabs.ticketbai.model.Codes.RegimeKey;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceLine;
import com.squareetlabs.ticketbai.model.InvoiceReference;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.Rectification;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.model.TaxBreakdown;
import com.squareetlabs.ticketbai.model.VatDetail;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.util.XmlWriter;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Se rehacen con el modelo las facturas oficiales y se exige el mismo XML (salvo la firma), y que con una firma cumpla el
 * XSD.
 */
class InvoiceXmlBuilderTest {

    private static final Set<String> SIGNATURE = Set.of("Signature");

    @Test
    @DisplayName("Gipuzkoa: la primera factura oficial (simplificada, dos líneas con descuento) sale igual")
    void gipuzkoaFirstInvoice() {
        Invoice invoice = Invoice.builder()
                .issuer("99999974E", "GEZURREZKO JAULKITZAILEA - EMISOR FICTICIO")
                .series("TB-2024-S").number("1")
                .issuedAt(LocalDate.of(2024, 2, 29), LocalTime.of(11, 5, 7))
                .simplified(true)
                .operationDate(LocalDate.of(2024, 2, 29))
                .description("Lehen faktura - Primera factura")
                .line(new InvoiceLine("Lehen produktua - Primer producto", bd("1"), bd("200"), bd("20"), bd("217.80")))
                .line(new InvoiceLine("Bigarren produktua - Segundo producto", bd("2"), bd("350"), null, bd("847")))
                .totalAmount(bd("1064.80"))
                .regimeKey(RegimeKey.GENERAL)
                .breakdown(Breakdown.ofInvoice(TaxBreakdown.builder()
                        .nonExempt(NonExemptType.S1, VatDetail.of(bd("880"), bd("21"), bd("184.80"))
                                .withSurchargeOrSimplifiedRegime(false))
                        .build()))
                .build();
        Software software = new Software("TBAIGIPRE00000000501", Party.withNif("P2000000F", null), "FAKTURABAI", "1.0");

        assertSameAsOfficial("gipuzkoa/alta-01-primera.xml", InvoiceXmlBuilder.build(invoice, null, software, null));
    }

    @Test
    @DisplayName("Álava: la rectificativa oficial (destinatario con dirección, sustitución y encadenamiento) sale igual")
    void arabaCorrectiveInvoice() {
        Invoice invoice = Invoice.builder()
                .issuer("A01111111", "Empresa Ejemplo TBAI")
                .recipient(Party.withNif("11111111H", "Destinatario1").withAddress("01001", "Eduardo Dato 1"))
                .series("R2021").number("37")
                .issuedAt(LocalDate.of(2021, 11, 5), LocalTime.of(13, 41, 23))
                .rectification(Rectification.bySubstitution(RectificationCode.R1, bd("73.86"), bd("15.50"), null,
                        List.of(new InvoiceReference("R2021", "123", LocalDate.of(2020, 11, 4)))))
                .description("rectificación factura A2021/123")
                .line(new InvoiceLine("Artículo 1 Ejemplo", bd("1"), bd("23.356"), bd("5"), bd("22.21")))
                .line(new InvoiceLine("Artículo 2 xxx", bd("1.5"), bd("18.2"), null, bd("33.03")))
                .totalAmount(bd("55.24"))
                .regimeKey(RegimeKey.GENERAL)
                .breakdown(Breakdown.ofInvoice(TaxBreakdown.builder()
                        .nonExempt(NonExemptType.S1, VatDetail.of(bd("45.66"), bd("21"), bd("9.59"))
                                .withSurchargeOrSimplifiedRegime(false))
                        .build()))
                .build();
        ChainLink previous = ChainLink.ofPrefix("R2021", "36", LocalDate.of(2021, 1, 1),
                "S9jsVmybBZlBkodZpA5VggFQ+oM6lk6eu2QLiP+TQ77mQwnKkfR1G3X4I9lX/aC70qJJ/sfTvBd56QamGc7qAb/GBc3Ex+wSImvU");
        Software software = new Software("TBAGIXuwierwwerxx9", Party.withNif("12121212T", null), "nombre 9", "1.0");

        assertSameAsOfficial("araba/ejemplo2-rectificativa.xml",
                InvoiceXmlBuilder.build(invoice, previous, software, "12332413"));
    }

    @Test
    @DisplayName("Una factura sin datos obligatorios no se construye")
    void requiredData() {
        assertThrows(NullPointerException.class, () -> Invoice.builder().issuer("A01111111", "X").build());
        assertThrows(IllegalArgumentException.class, () -> Invoice.builder()
                .issuer("A01111111", "X").number("1").issuedAt(LocalDate.now(), LocalTime.NOON).description("d")
                .totalAmount(BigDecimal.ONE).breakdown(Breakdown.ofInvoice(TaxBreakdown.builder().build())).build());
    }

    private static void assertSameAsOfficial(String example, Document built) {
        Document official = Xml.parse(Examples.read(example));

        List<String> diffs = XmlTreeComparator.differences(official.getDocumentElement(), built.getDocumentElement(), SIGNATURE);
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));

        // Con la firma oficial al final, el fichero construido cumple el XSD y se lee igual que el oficial.
        Element signature = (Element) official.getElementsByTagNameNS(Xml.DSIG_NS, "Signature").item(0);
        built.getDocumentElement().appendChild(built.importNode(signature, true));
        byte[] bytes = XmlWriter.toBytes(built);
        List<String> errors = XsdValidator.validate(bytes, XsdValidator.Schema.INVOICE);
        assertTrue(errors.isEmpty(), errors.toString());

        SignedInvoice expected = SignedInvoiceReader.read(Examples.read(example));
        SignedInvoice actual = SignedInvoiceReader.read(bytes);
        assertEquals(expected.getIssuerNif(), actual.getIssuerNif());
        assertEquals(expected.getSeries(), actual.getSeries());
        assertEquals(expected.getNumber(), actual.getNumber());
        assertEquals(expected.getIssueDate(), actual.getIssueDate());
        assertEquals(expected.getPreviousLink(), actual.getPreviousLink());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
