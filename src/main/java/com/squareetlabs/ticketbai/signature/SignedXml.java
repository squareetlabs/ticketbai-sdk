package com.squareetlabs.ticketbai.signature;

/** Fichero firmado: los bytes exactos (que hay que conservar sin cambios) y su {@code SignatureValue}. */
public final class SignedXml {

    private final byte[] bytes;
    private final String signatureValue;

    SignedXml(byte[] bytes, String signatureValue) {
        this.bytes = bytes;
        this.signatureValue = signatureValue;
    }

    /** Fichero ya firmado (por ejemplo, el conservado para reenviarlo o para el LROE). */
    public static SignedXml of(byte[] signedXml) {
        byte[] copy = java.util.Objects.requireNonNull(signedXml, "signedXml").clone();
        return new SignedXml(copy, TicketBaiSigner.signatureValueOf(copy));
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
