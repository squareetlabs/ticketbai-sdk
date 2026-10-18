package com.squareetlabs.ticketbai.transport.lroe;

/** Operación del LROE para el subcapítulo 1.1 del modelo 240. */
public enum LroeOperation {
    /** Alta de facturas emitidas con software garante ({@code A00}). */
    INVOICES("A00"),
    /** Anulación de facturas emitidas con software garante ({@code AN0}). */
    ANNULMENTS("AN0");

    private final String code;

    LroeOperation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
