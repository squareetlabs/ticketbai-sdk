package com.squareetlabs.ticketbai.identifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Crc8Test {

    @Test
    @DisplayName("Reproduce el CRC del ejemplo oficial de código TicketBAI (Anexo V, Gipuzkoa)")
    void officialIdentifierExample() {
        assertEquals("237", Crc8.compute("TBAI-00000006Y-251019-btFpwP8dcLGAF-"));
    }

    @Test
    @DisplayName("Reproduce el CRC del ejemplo oficial de URL del QR (Anexo V, Gipuzkoa)")
    void officialQrExample() {
        assertEquals("182", Crc8.compute("https://tbai.egoitza.gipuzkoa.eus/qr/?id=TBAI-44619360G-261020-EzyQEMtxw37Gm-161"
                + "&s=TB-2020-F&nf=419&i=1542.75"));
    }

    @Test
    @DisplayName("Rellena con ceros a la izquierda hasta tres dígitos")
    void padsWithZeros() {
        assertEquals("000", Crc8.compute(""));
        assertEquals(3, Crc8.compute("a").length());
    }
}
