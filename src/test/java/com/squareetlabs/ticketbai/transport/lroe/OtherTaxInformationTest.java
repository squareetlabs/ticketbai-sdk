package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.TicketBai;
import com.squareetlabs.ticketbai.model.Codes.IssuedBy;
import com.squareetlabs.ticketbai.model.Codes.RectificationCode;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceReference;
import com.squareetlabs.ticketbai.model.Rectification;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.signature.TicketBaiSigner;
import com.squareetlabs.ticketbai.support.TestInvoices;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bloque {@code OtraInformacionTrascendenciaTributaria} del modelo 240 y sus reglas de Batuz. */
class OtherTaxInformationTest {

    private static final LroeTaxpayer COMPANY = LroeTaxpayer.legalEntity("B12345674", "CONSTRUCCIONES DE PRUEBA SL");
    private static SignedXml plain;
    private static SignedXml r1;
    private static SignedXml r2;
    private static SignedXml byThirdParty;

    @BeforeAll
    static void signFiles() {
        try (TicketBaiSigner signer = TestInvoices.signer()) {
            TicketBai tbai = TicketBai.builder().territory(Territory.BIZKAIA).environment(Environment.TEST)
                    .software(TestInvoices.TEST_SOFTWARE).signer(signer).build();
            plain = tbai.issue(TestInvoices.simple("1"), null).getSignedXml();
            r1 = tbai.issue(rectifying("2", RectificationCode.R1), null).getSignedXml();
            r2 = tbai.issue(rectifying("3", RectificationCode.R2), null).getSignedXml();
            byThirdParty = tbai.issue(TestInvoices.simpleBuilder("4").issuedBy(IssuedBy.THIRD_PARTY).build(), null).getSignedXml();
        }
    }

    @Test
    @DisplayName("Con todos los campos, el alta del 240 cumple el XSD y los escribe en el orden del esquema")
    void allFieldsAreValid() {
        OtherTaxInformation info = OtherTaxInformation.create()
                .representativeNif("11111111H")
                .property(OtherTaxInformation.PropertySituation.BASQUE_COUNTRY_OR_NAVARRE, "U1234567890")
                .property(OtherTaxInformation.PropertySituation.ABROAD, null)
                .propertyTransferSubjectToVat(new BigDecimal("1000"))
                .billingAgreementNumber("ACU-2026-01")
                .externalReference("PEDIDO-4711")
                .coupon(true)
                .succeededEntity("A99800005", "ENTIDAD ABSORBIDA SA")
                .additionalProvisionsTwoAndFive(true)
                .hydrocarbonsFromDeposit(false);

        LroeBatch batch = LroeBatch.builder(LroeModel.MODEL_240, COMPANY, Year.of(2026), LroeOperation.INVOICES)
                .add(r1, info).add(plain).build();
        byte[] xml = LroeRequestBuilder.toBytes(batch);

        assertEquals(List.of(), XsdValidator.validate(xml, XsdValidator.Schema.LROE_240_INVOICES));
        Element block = (Element) Xml.parse(xml).getElementsByTagNameNS("*", "OtraInformacionTrascendenciaTributaria").item(0);
        assertEquals(List.of("NIFRepresentanteDeclarado", "Inmuebles", "ImporteTransmisionInmueblesSujetoAIVA",
                "NumRegistroAcuerdoFacturacion", "ReferenciaExterna", "Cupon", "EntidadSucedida",
                "FacturacionDispAdicionalSegundaYQuinta", "HidrocarburosExtraidosDeposito"), childNames(block));
        assertEquals("1000.00", Xml.text(block, "ImporteTransmisionInmueblesSujetoAIVA"));
        assertEquals(2, block.getElementsByTagNameNS("*", "DetalleInmueble").getLength());
        assertEquals("N", Xml.text(block, "HidrocarburosExtraidosDeposito"));
        assertEquals(1, Xml.parse(xml).getElementsByTagNameNS("*", "OtraInformacionTrascendenciaTributaria").getLength());
    }

    @Test
    @DisplayName("También en la modificación (M00); un bloque vacío no se envía")
    void modificationAndEmptyBlock() {
        LroeBatch batch = LroeBatch.builder(LroeModel.MODEL_240, COMPANY, Year.of(2026), LroeOperation.MODIFICATIONS)
                .add(plain, OtherTaxInformation.create().externalReference("X")).build();
        LroeBatch empty = LroeBatch.builder(LroeModel.MODEL_240, COMPANY, Year.of(2026), LroeOperation.INVOICES)
                .add(plain, OtherTaxInformation.create()).build();

        assertEquals(List.of(), XsdValidator.validate(LroeRequestBuilder.toBytes(batch), XsdValidator.Schema.LROE_240_INVOICES));
        assertNull(empty.getEntries().get(0).getOtherTaxInformation());
        assertEquals(0, Xml.parse(LroeRequestBuilder.toBytes(empty))
                .getElementsByTagNameNS("*", "OtraInformacionTrascendenciaTributaria").getLength());
    }

    @Test
    @DisplayName("B4_2000300: referencia catastral obligatoria en las situaciones 1 y 2")
    void cadastralReference() {
        OtherTaxInformation info = OtherTaxInformation.create();

        assertThrows(IllegalArgumentException.class,
                () -> info.property(OtherTaxInformation.PropertySituation.SPAIN_WITH_CADASTRAL_REFERENCE, null));
        assertThrows(IllegalArgumentException.class,
                () -> info.property(OtherTaxInformation.PropertySituation.BASQUE_COUNTRY_OR_NAVARRE, " "));
        info.property(OtherTaxInformation.PropertySituation.WITHOUT_CADASTRAL_REFERENCE, null);
        assertThrows(IllegalArgumentException.class, () -> info.property(OtherTaxInformation.PropertySituation.ABROAD,
                "12345678901234567890123456"));
        assertThrows(IllegalArgumentException.class, () -> info.billingAgreementNumber("1234567890123456"));
    }

    @Test
    @DisplayName("B4_2000301 y B4_2000302: cupón solo en R1/R5; disposiciones 2.ª y 5.ª no si emite un tercero")
    void rulesAgainstTheFile() {
        LroeBatch.Builder builder = LroeBatch.builder(LroeModel.MODEL_240, COMPANY, Year.of(2026), LroeOperation.INVOICES);

        IllegalArgumentException coupon = assertThrows(IllegalArgumentException.class,
                () -> builder.add(r2, OtherTaxInformation.create().coupon(true)));
        assertTrue(coupon.getMessage().contains("B4_2000301"));
        assertThrows(IllegalArgumentException.class, () -> builder.add(plain, OtherTaxInformation.create().coupon(true)));
        builder.add(r2, OtherTaxInformation.create().coupon(false));
        IllegalArgumentException provisions = assertThrows(IllegalArgumentException.class,
                () -> builder.add(byThirdParty, OtherTaxInformation.create().additionalProvisionsTwoAndFive(true)));
        assertTrue(provisions.getMessage().contains("B4_2000302"));
        builder.add(byThirdParty, OtherTaxInformation.create().additionalProvisionsTwoAndFive(false));
        assertEquals(2, builder.build().getEntries().size());
    }

    @Test
    @DisplayName("Solo en el alta y la modificación del 240")
    void onlyModel240() {
        LroeTaxpayer ane = LroeTaxpayer.individual("79732487C", "ANE", "ARRIOLA", "LEJARDI");
        OtherTaxInformation info = OtherTaxInformation.create().externalReference("X");

        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_140, ane, Year.of(2026),
                LroeOperation.INVOICES).add(plain, info));
        assertThrows(IllegalArgumentException.class, () -> LroeBatch.builder(LroeModel.MODEL_240, COMPANY, Year.of(2026),
                LroeOperation.ANNULMENTS).add(plain, info));
    }

    private static Invoice rectifying(String number, RectificationCode code) {
        return TestInvoices.simpleBuilder(number)
                .rectification(Rectification.byDifferences(code, List.of(new InvoiceReference("F2026", "1",
                        LocalDate.of(2026, 10, 18)))))
                .build();
    }

    private static List<String> childNames(Element parent) {
        List<String> names = new java.util.ArrayList<>();
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                names.add(n.getLocalName());
            }
        }
        return names;
    }
}
