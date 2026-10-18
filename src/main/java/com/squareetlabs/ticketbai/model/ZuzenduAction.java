package com.squareetlabs.ticketbai.model;

/** Acción de Zuzendu sobre un fichero de alta (Gipuzkoa y Álava). */
public enum ZuzenduAction {
    /** Corrige un fichero <b>rechazado</b> en un envío anterior. */
    SUBSANAR,
    /**
     * Corrige un fichero <b>recibido</b> (con errores o con datos que cambiar), siempre que la causa no exija una factura
     * rectificativa. El importe total no puede cambiar.
     */
    MODIFICAR
}
