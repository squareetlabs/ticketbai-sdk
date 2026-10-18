package com.squareetlabs.ticketbai.identifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TbaiIdentifierTest {

    @Test
    @DisplayName("Construye el código del ejemplo oficial: NIF, fecha ddMMyy, 13 caracteres de la firma y CRC")
    void officialExample() {
        TbaiIdentifier id = TbaiIdentifier.of("00000006Y", LocalDate.of(2019, 10, 25),
                "btFpwP8dcLGAFresto-de-la-firma-que-no-entra");

        assertEquals("TBAI-00000006Y-251019-btFpwP8dcLGAF-237", id.getValue());
        assertEquals(39, id.getValue().length());
        assertEquals("237", id.getCrc());
    }

    @Test
    @DisplayName("Lee un código existente y comprueba su CRC")
    void parse() {
        TbaiIdentifier id = TbaiIdentifier.parse("TBAI-00000006Y-251019-btFpwP8dcLGAF-237");

        assertEquals("00000006Y", id.getIssuerNif());
        assertEquals(LocalDate.of(2019, 10, 25), id.getIssueDate());
        assertEquals("btFpwP8dcLGAF", id.getSignaturePrefix());
        assertThrows(IllegalArgumentException.class, () -> TbaiIdentifier.parse("TBAI-00000006Y-251019-btFpwP8dcLGAF-238"));
    }

    @Test
    @DisplayName("Rechaza un NIF que no tiene 9 caracteres o una firma de menos de 13")
    void rejectsInvalidInput() {
        LocalDate date = LocalDate.of(2026, 10, 18);
        assertThrows(IllegalArgumentException.class, () -> TbaiIdentifier.of("1234", date, "btFpwP8dcLGAFxx"));
        assertThrows(IllegalArgumentException.class, () -> TbaiIdentifier.of("00000006Y", date, "corta"));
        assertThrows(NullPointerException.class, () -> TbaiIdentifier.of("00000006Y", null, "btFpwP8dcLGAFxx"));
    }
}
