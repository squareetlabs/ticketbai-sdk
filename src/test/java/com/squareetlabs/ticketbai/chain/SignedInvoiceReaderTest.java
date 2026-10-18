package com.squareetlabs.ticketbai.chain;

import com.squareetlabs.ticketbai.identifier.TbaiIdentifier;
import com.squareetlabs.ticketbai.support.Examples;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contra ficheros firmados oficiales: lo que Hacienda aceptó es la referencia. */
class SignedInvoiceReaderTest {

    @Test
    @DisplayName("Gipuzkoa: la factura 2 encadena la serie, el número y la fecha de la factura 1, con 100 caracteres de firma")
    void gipuzkoaSuccessiveInvoiceChainsTheFirstOne() {
        SignedInvoice first = SignedInvoiceReader.read(Examples.read("gipuzkoa/alta-01-primera.xml"));
        SignedInvoice second = SignedInvoiceReader.read(Examples.read("gipuzkoa/alta-02-sucesiva.xml"));
        ChainLink expected = first.toChainLink();
        ChainLink link = second.getPreviousLink();

        assertNull(first.getPreviousLink(), "La primera factura no lleva encadenamiento");
        assertEquals(expected.getSeries(), link.getSeries());
        assertEquals(expected.getNumber(), link.getNumber());
        assertEquals(expected.getIssueDate(), link.getIssueDate());
        assertEquals(LocalDate.of(2024, 2, 29), link.getIssueDate());
        assertEquals(ChainLink.SIGNATURE_PREFIX_LENGTH, link.getSignatureValuePrefix().length());
        assertEquals(ChainLink.SIGNATURE_PREFIX_LENGTH, expected.getSignatureValuePrefix().length());
        // La firma encadenada no se compara: los dos ejemplos publicados se firmaron por separado, y el prefijo que lleva
        // la factura 2 no es el de la factura 1 publicada.
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"araba/ejemplo1-respuesta.xml", "araba/ejemplo2-respuesta.xml"})
    @DisplayName("Álava: los códigos TicketBAI que devolvió Hacienda pasan nuestra comprobación de formato y CRC")
    void arabaResponseIdentifiersAreValid(String path) {
        String response = new String(Examples.read(path), StandardCharsets.UTF_8);
        String value = response.substring(response.indexOf("TBAI-"), response.indexOf("TBAI-") + TbaiIdentifier.LENGTH);

        TbaiIdentifier id = TbaiIdentifier.parse(value);

        assertEquals("A01111111", id.getIssuerNif());
        assertEquals(value, id.getValue());
        // El código no se recalcula desde el fichero: Álava volvió a firmar los ejemplos con la política nueva (2022) y
        // las respuestas son de 2021.
    }

    @Test
    @DisplayName("El código TicketBAI de un fichero firmado sale de su NIF, su fecha y su firma")
    void identifierFromSignedFile() {
        SignedInvoice invoice = SignedInvoiceReader.read(Examples.read("araba/ejemplo1-factura.xml"));

        assertEquals("TBAI-A01111111-041121-" + invoice.getSignatureValue().substring(0, 13) + "-",
                invoice.toTbaiIdentifier().getValue().substring(0, 36));
    }

    @Test
    @DisplayName("Lee los datos de la factura y de la cadena anterior del ejemplo de Álava")
    void readsInvoiceData() {
        SignedInvoice invoice = SignedInvoiceReader.read(Examples.read("araba/ejemplo1-factura.xml"));

        assertEquals("A01111111", invoice.getIssuerNif());
        assertEquals("A2021", invoice.getSeries());
        assertEquals("123", invoice.getNumber());
        assertEquals(LocalDate.of(2021, 11, 4), invoice.getIssueDate());
        assertEquals("R2021", invoice.getPreviousLink().getSeries());
        assertEquals("35", invoice.getPreviousLink().getNumber());
        assertTrue(invoice.getSignatureValue().length() > 100);
    }
}
