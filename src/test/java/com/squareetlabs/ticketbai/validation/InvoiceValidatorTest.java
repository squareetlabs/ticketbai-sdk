package com.squareetlabs.ticketbai.validation;

import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.model.Breakdown;
import com.squareetlabs.ticketbai.model.Codes.ExemptionCause;
import com.squareetlabs.ticketbai.model.Codes.NonExemptType;
import com.squareetlabs.ticketbai.model.Codes.OtherIdType;
import com.squareetlabs.ticketbai.model.Codes.RegimeKey;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceLine;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.TaxBreakdown;
import com.squareetlabs.ticketbai.model.VatDetail;
import com.squareetlabs.ticketbai.support.TestInvoices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceValidatorTest {

    private static final InvoiceValidator VALIDATOR = InvoiceValidator.strict();

    @ParameterizedTest(name = "{0}")
    @EnumSource(Territory.class)
    @DisplayName("Una factura completa y cuadrada no tiene problemas en ningún territorio")
    void validInvoice(Territory territory) {
        assertEquals(List.of(), VALIDATOR.validate(TestInvoices.simple("1"), territory));
    }

    @Test
    @DisplayName("La suma de las líneas y la del desglose tienen que cuadrar con el total")
    void totalsMustMatch() {
        Invoice invoice = base().totalAmount(bd("1210.01")).build();

        assertEquals(List.of("ARABA:DatosFactura/ImporteTotalFactura", "ARABA:TipoDesglose"), errors(invoice, Territory.ARABA));
    }

    @Test
    @DisplayName("Con tolerancia, un céntimo de diferencia se admite")
    void tolerance() {
        Invoice invoice = base().totalAmount(bd("1210.01")).build();

        assertEquals(List.of(), InvoiceValidator.strict().withTolerance(bd("0.01")).validate(invoice, Territory.ARABA));
    }

    @Test
    @DisplayName("Las claves 01 y 02 no van juntas, y no se repiten")
    void regimeKeys() {
        assertEquals(List.of("ARABA:DatosFactura/Claves"),
                errors(base().regimeKey(RegimeKey.EXPORT).build(), Territory.ARABA));
        assertEquals(List.of("ARABA:DatosFactura/Claves"),
                errors(base().regimeKey(RegimeKey.GENERAL).build(), Territory.ARABA));
    }

    @Test
    @DisplayName("Con la clave 14 el destinatario es una Administración Pública (NIF P, Q, S o V)")
    void publicWorksCertification() {
        Invoice toCompany = base().regimeKey(RegimeKey.PUBLIC_WORKS_CERTIFICATION).build();
        Invoice toCouncil = Invoice.builder()
                .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL")
                .recipient(Party.withNif("P2006900F", "AYUNTAMIENTO").withAddress("20001", "Plaza 1"))
                .number("1").issuedAt(LocalDate.of(2026, 10, 18), LocalTime.NOON).description("Certificación")
                .line(line()).totalAmount(bd("1210"))
                .regimeKey(RegimeKey.PUBLIC_WORKS_CERTIFICATION).breakdown(breakdown()).build();

        assertEquals(List.of("ARABA:Sujetos/Destinatarios"), errors(toCompany, Territory.ARABA));
        assertEquals(List.of(), errors(toCouncil, Territory.ARABA));
    }

    @Test
    @DisplayName("Un destinatario extranjero exige el desglose por tipo de operación y, salvo NIF-IVA, el país")
    void foreignRecipient() {
        Invoice noCountry = withRecipient(Party.withOtherId(null, OtherIdType.PASSPORT, "X123", "CLIENTE"), breakdown());
        Invoice spainNotPassport = withRecipient(Party.withOtherId("ES", OtherIdType.OFFICIAL_ID, "X1", "CLIENTE"),
                Breakdown.byOperationType(breakdown().getInvoice(), null));

        assertEquals(List.of("ARABA:IDDestinatario/IDOtro/CodigoPais", "ARABA:TipoDesglose"),
                errors(noCountry, Territory.ARABA));
        assertEquals(List.of("ARABA:IDDestinatario/IDOtro/CodigoPais"), errors(spainNotPassport, Territory.ARABA));
    }

    @Test
    @DisplayName("La exención E5 exige un destinatario con NIF-IVA")
    void intraCommunityExemption() {
        TaxBreakdown e5 = TaxBreakdown.builder().exempt(ExemptionCause.E5, bd("1210")).build();
        Invoice withVat = withRecipient(Party.withOtherId("FR", OtherIdType.VAT_NUMBER, "FR40303265045", "CLIENT"),
                Breakdown.byOperationType(null, e5));
        Invoice withPassport = withRecipient(Party.withOtherId("FR", OtherIdType.PASSPORT, "X1", "CLIENT"),
                Breakdown.byOperationType(null, e5));

        assertEquals(List.of(), errors(withVat, Territory.ARABA));
        assertEquals(List.of("ARABA:TipoDesglose/.../CausaExencion"), errors(withPassport, Territory.ARABA));
    }

    @Test
    @DisplayName("Gipuzkoa exige líneas y destinatario en la factura completa, y avisa si falta su dirección")
    void gipuzkoaRules() {
        Invoice noLinesNoRecipient = Invoice.builder()
                .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL").number("1")
                .issuedAt(LocalDate.of(2026, 10, 18), LocalTime.NOON).description("d").totalAmount(bd("1210"))
                .regimeKey(RegimeKey.GENERAL).breakdown(breakdown()).build();
        Invoice noAddress = withRecipient(Party.withNif("11111111H", "CLIENTE"), breakdown());

        assertEquals(List.of("GIPUZKOA:DatosFactura/DetallesFactura", "GIPUZKOA:Sujetos/Destinatarios"),
                errors(noLinesNoRecipient, Territory.GIPUZKOA));
        assertEquals(List.of(), errors(noLinesNoRecipient, Territory.BIZKAIA), "Bizkaia no exige líneas");
        List<ValidationIssue> warnings = VALIDATOR.validate(noAddress, Territory.GIPUZKOA);
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().noneMatch(ValidationIssue::isError));
    }

    private static Invoice.Builder base() {
        return Invoice.builder()
                .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL")
                .recipient(Party.withNif("11111111H", "CLIENTE").withAddress("20001", "Calle Mayor 1"))
                .number("1").issuedAt(LocalDate.of(2026, 10, 18), LocalTime.NOON).description("Certificación")
                .line(line()).totalAmount(bd("1210")).regimeKey(RegimeKey.GENERAL).breakdown(breakdown());
    }

    private static Invoice withRecipient(Party recipient, Breakdown breakdown) {
        return Invoice.builder()
                .issuer("B12345674", "CONSTRUCCIONES DE PRUEBA SL").recipient(recipient)
                .number("1").issuedAt(LocalDate.of(2026, 10, 18), LocalTime.NOON).description("Certificación")
                .line(line()).totalAmount(bd("1210")).regimeKey(RegimeKey.GENERAL).breakdown(breakdown).build();
    }

    private static InvoiceLine line() {
        return new InvoiceLine("Partida", bd("1"), bd("1000"), null, bd("1210"));
    }

    private static Breakdown breakdown() {
        return Breakdown.ofInvoice(TaxBreakdown.builder()
                .nonExempt(NonExemptType.S1, VatDetail.of(bd("1000"), bd("21"), bd("210"))).build());
    }

    private static List<String> errors(Invoice invoice, Territory territory) {
        return VALIDATOR.validate(invoice, territory).stream().filter(ValidationIssue::isError)
                .map(i -> territory + ":" + i.getField()).collect(Collectors.toList());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
