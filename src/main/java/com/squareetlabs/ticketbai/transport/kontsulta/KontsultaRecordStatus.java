package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.Territory;

/**
 * Estado de registro de una factura en Kontsulta ({@code EstadoRegistro} del filtro, {@code Estado} de la respuesta).
 * Los dos territorios no publican los mismos estados: Gipuzkoa (1.0) tiene «Modificada» y Álava (1.2.3), «Sustituida».
 */
public enum KontsultaRecordStatus {
    CORRECT("C", true, true),
    ANNULLED("A", true, true),
    REJECTED("R", true, true),
    RECTIFIED("Z", true, true),
    /** Solo Gipuzkoa. */
    MODIFIED("M", true, false),
    /** Solo Álava. */
    REPLACED("S", false, true);

    private final String code;
    private final boolean gipuzkoa;
    private final boolean araba;

    KontsultaRecordStatus(String code, boolean gipuzkoa, boolean araba) {
        this.code = code;
        this.gipuzkoa = gipuzkoa;
        this.araba = araba;
    }

    public String code() {
        return code;
    }

    public boolean isSupportedIn(Territory territory) {
        return territory == Territory.GIPUZKOA ? gipuzkoa : territory == Territory.ARABA && araba;
    }

    /** Estado por su código, o {@code null} si no se conoce. */
    public static KontsultaRecordStatus fromCode(String code) {
        for (KontsultaRecordStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
