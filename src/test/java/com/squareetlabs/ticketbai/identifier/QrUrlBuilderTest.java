package com.squareetlabs.ticketbai.identifier;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrUrlBuilderTest {

    private static final TbaiIdentifier OFFICIAL_ID = TbaiIdentifier.parse("TBAI-44619360G-261020-EzyQEMtxw37Gm-161");

    @Test
    @DisplayName("Reproduce la URL del QR del ejemplo oficial de Gipuzkoa, con cr=182")
    void officialGipuzkoaExample() {
        String url = QrUrlBuilder.build(Territory.GIPUZKOA, Environment.PRODUCTION, OFFICIAL_ID, "TB-2020-F", "419",
                new BigDecimal("1542.75"));

        assertEquals("https://tbai.egoitza.gipuzkoa.eus/qr/?id=TBAI-44619360G-261020-EzyQEMtxw37Gm-161"
                + "&s=TB-2020-F&nf=419&i=1542.75&cr=182", url);
    }

    @Test
    @DisplayName("El importe sale siempre con dos decimales y punto, como ImporteTotalFactura")
    void amountFormat() {
        String url = QrUrlBuilder.build(Territory.ARABA, Environment.PRODUCTION, OFFICIAL_ID, "A", "1", new BigDecimal("1210"));

        assertTrue(url.startsWith("https://ticketbai.araba.eus/tbai/qrtbai/?id="), url);
        assertTrue(url.contains("&i=1210.00&cr="), url);
    }

    @Test
    @DisplayName("Codifica como URL la serie y el número, y calcula el CRC sobre la URL ya codificada")
    void encodesParameters() {
        String url = QrUrlBuilder.build(Territory.BIZKAIA, Environment.TEST, OFFICIAL_ID, "SERIE A/Ñ", "7&8",
                new BigDecimal("10.5"));
        String withoutCrc = url.substring(0, url.lastIndexOf("&cr="));

        assertTrue(withoutCrc.contains("&s=SERIE%20A%2F%C3%91&nf=7%268&i=10.50"), url);
        assertEquals(Crc8.compute(withoutCrc), url.substring(url.lastIndexOf('=') + 1));
    }

    @Test
    @DisplayName("Sin serie, el parámetro s va vacío")
    void withoutSeries() {
        String url = QrUrlBuilder.build(Territory.GIPUZKOA, Environment.TEST, OFFICIAL_ID, null, "419", new BigDecimal("1"));

        assertTrue(url.startsWith("https://tbai.prep.gipuzkoa.eus/qr/?id="), url);
        assertTrue(url.contains("&s=&nf=419&i=1.00&cr="), url);
    }
}
