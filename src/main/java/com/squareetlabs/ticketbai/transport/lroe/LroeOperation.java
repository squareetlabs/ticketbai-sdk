package com.squareetlabs.ticketbai.transport.lroe;

/** Operación del LROE sobre el subcapítulo 1.1. */
public enum LroeOperation {
    /** Alta ({@code A00}). */
    INVOICES("A00"),
    /**
     * Modificación ({@code M00}) de anotaciones ya registradas: mismo esquema que el alta. El fichero TicketBAI firmado
     * no cambia (se envía el mismo); cambian los datos que lo acompañan (en el 140, los de renta).
     */
    MODIFICATIONS("M00"),
    /** Anulación ({@code AN0}) con los ficheros {@code AnulaTicketBai}. */
    ANNULMENTS("AN0");

    private final String code;

    LroeOperation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
