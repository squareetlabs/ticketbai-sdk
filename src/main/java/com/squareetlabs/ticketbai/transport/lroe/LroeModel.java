package com.squareetlabs.ticketbai.transport.lroe;

/** Modelo del LROE de Bizkaia que lleva los ficheros TicketBAI (subcapítulo 1.1). */
public enum LroeModel {
    /** Personas jurídicas y no residentes con establecimiento permanente: «Facturas emitidas con software garante». */
    MODEL_240("240"),
    /** Personas físicas con actividad económica: «Ingresos con facturas con software garante», con los datos de renta. */
    MODEL_140("140");

    private final String code;

    LroeModel(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
