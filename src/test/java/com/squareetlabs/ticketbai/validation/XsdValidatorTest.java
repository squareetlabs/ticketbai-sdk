package com.squareetlabs.ticketbai.validation;

import com.squareetlabs.ticketbai.support.Examples;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdValidatorTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gipuzkoa/alta-01-primera.xml", "gipuzkoa/alta-02-sucesiva.xml", "araba/ejemplo1-factura.xml",
            "araba/ejemplo2-rectificativa.xml"})
    @DisplayName("Los ficheros de alta oficiales cumplen el XSD TicketBAI 1.2.2")
    void officialInvoicesAreValid(String path) {
        List<String> errors = XsdValidator.validate(Examples.read(path), XsdValidator.Schema.INVOICE);

        assertTrue(errors.isEmpty(), errors.toString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gipuzkoa/anulacion-02.xml", "araba/ejemplo3-anulacion.xml"})
    @DisplayName("Los ficheros de anulación oficiales cumplen el XSD de anulación 1.2.2")
    void officialAnnulmentsAreValid(String path) {
        assertTrue(XsdValidator.validate(Examples.read(path), XsdValidator.Schema.ANNULMENT).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"araba/ejemplo1-respuesta.xml", "araba/ejemplo2-respuesta.xml"})
    @DisplayName("Las respuestas oficiales cumplen el XSD de respuesta")
    void officialResponsesAreValid(String path) {
        assertTrue(XsdValidator.validate(Examples.read(path), XsdValidator.Schema.RESPONSE).isEmpty());
    }

    @Test
    @DisplayName("Un fichero que no cumple el esquema devuelve los errores, con su línea")
    void invalidFileReportsErrors() {
        String broken = new String(Examples.read("araba/ejemplo1-factura.xml"), StandardCharsets.UTF_8)
                .replace("<NumFactura>123</NumFactura>", "<NumFacturaMal>123</NumFacturaMal>");

        List<String> errors = XsdValidator.validate(broken.getBytes(StandardCharsets.UTF_8), XsdValidator.Schema.INVOICE);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).startsWith("línea "), errors.get(0));
    }

    @Test
    @DisplayName("Un XML mal formado devuelve un error, no una excepción")
    void malformedXml() {
        assertEquals(1, XsdValidator.validate("<TicketBai".getBytes(StandardCharsets.UTF_8), XsdValidator.Schema.INVOICE).size());
    }
}
