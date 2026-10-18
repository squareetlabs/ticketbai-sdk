package com.squareetlabs.ticketbai;

/**
 * Territorio histórico con TicketBAI. Cada uno publica su política de firma y su aplicación web de comprobación del
 * QR; el formato del fichero (XSD 1.2.2) es común.
 */
public enum Territory {

    /** Álava / Araba (DFA). Política de firma: especificaciones de 30/05/2022; solo algoritmos RSA y SHA-256 o superior. */
    ARABA(new SignaturePolicy("https://ticketbai.araba.eus/tbai/sinadura/",
            "4Vk3uExj7tGn9DyUCPDsV9HRmK6KZfYdRiW3StOjcQA="),
            "https://pruebas-ticketbai.araba.eus/tbai/qrtbai/",
            "https://ticketbai.araba.eus/tbai/qrtbai/"),

    /**
     * Bizkaia (DFB, Batuz). Política de firma v1.2 (OF 601/2025). No hay una URL de QR de pruebas publicada: se usa la
     * de producción también en pruebas, y el QR se comprueba con la herramienta de pruebas de Batuz.
     */
    BIZKAIA(new SignaturePolicy(
            "https://www.batuz.eus/fitxategiak/batuz/ticketbai/sinadura_elektronikoaren_zehaztapenak_especificaciones_de_la_firma_electronica_v1_2.pdf",
            "ODqaRy1UVthzP7SpiNP4f950jTxgYC63kTnPQHJGLZw="),
            "https://batuz.eus/QRTBAI/",
            "https://batuz.eus/QRTBAI/"),

    /** Gipuzkoa (DFG). Política de firma del Anexo III (OF 521/2020, modificado por la OF 64/2026). */
    GIPUZKOA(new SignaturePolicy("https://www.gipuzkoa.eus/ticketbai/sinadura",
            "4LDJbY5hqHHHX858s9QV1P8yVGzo6H23P/iNRRv+PnQ="),
            "https://tbai.prep.gipuzkoa.eus/qr/",
            "https://tbai.egoitza.gipuzkoa.eus/qr/");

    private final SignaturePolicy signaturePolicy;
    private final String testQrBaseUrl;
    private final String productionQrBaseUrl;

    Territory(SignaturePolicy signaturePolicy, String testQrBaseUrl, String productionQrBaseUrl) {
        this.signaturePolicy = signaturePolicy;
        this.testQrBaseUrl = testQrBaseUrl;
        this.productionQrBaseUrl = productionQrBaseUrl;
    }

    public SignaturePolicy getSignaturePolicy() {
        return signaturePolicy;
    }

    /** URL base de la aplicación web de comprobación del QR, con la "/" final que entra en el cálculo del CRC. */
    public String getQrBaseUrl(Environment environment) {
        return environment == Environment.PRODUCTION ? productionQrBaseUrl : testQrBaseUrl;
    }
}
