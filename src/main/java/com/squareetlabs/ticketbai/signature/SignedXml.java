package com.squareetlabs.ticketbai.signature;

/** Fichero firmado: los bytes exactos (que hay que conservar sin cambios) y su {@code SignatureValue}. */
public final class SignedXml {

    private final byte[] bytes;
    private final String signatureValue;

    SignedXml(byte[] bytes, String signatureValue) {
        this.bytes = bytes;
        this.signatureValue = signatureValue;
    }

    /** Bytes del fichero firmado. Se envían y se conservan tal cual: cualquier cambio invalida la firma. */
    public byte[] getBytes() {
        return bytes.clone();
    }

    /** {@code SignatureValue} sin espacios ni saltos de línea (encadenamiento y código TicketBAI). */
    public String getSignatureValue() {
        return signatureValue;
    }
}
