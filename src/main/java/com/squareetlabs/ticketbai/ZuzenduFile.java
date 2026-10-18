package com.squareetlabs.ticketbai;

/**
 * Fichero Zuzendu (sin firma) listo para enviar al servicio de corrección de Gipuzkoa o Álava. Se conserva junto al
 * fichero original, que no cambia.
 */
public final class ZuzenduFile {

    /** Qué corrige. */
    public enum Kind {
        /** Subsanación o modificación de un alta ({@code SubsanacionModificacionTicketBAI}). */
        INVOICE,
        /** Subsanación de una anulación ({@code SubsanacionAnulacionTicketBAI}). */
        ANNULMENT
    }

    private final byte[] bytes;
    private final Kind kind;

    ZuzenduFile(byte[] bytes, Kind kind) {
        this.bytes = bytes;
        this.kind = kind;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public Kind getKind() {
        return kind;
    }
}
