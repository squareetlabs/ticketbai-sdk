package com.squareetlabs.ticketbai.transport.rest;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;

import java.net.URI;
import java.util.Objects;

/**
 * Servicios REST de alta y anulación de Gipuzkoa y Álava. Bizkaia no tiene: allí el fichero va dentro del LROE.
 */
public final class RestEndpoints {

    private final URI invoices;
    private final URI annulments;

    public RestEndpoints(URI invoices, URI annulments) {
        this.invoices = Objects.requireNonNull(invoices, "invoices");
        this.annulments = Objects.requireNonNull(annulments, "annulments");
    }

    /** Servicios oficiales del territorio y el entorno. */
    public static RestEndpoints of(Territory territory, Environment environment) {
        boolean production = Objects.requireNonNull(environment, "environment") == Environment.PRODUCTION;
        switch (Objects.requireNonNull(territory, "territory")) {
            case GIPUZKOA:
                String g = production ? "https://tbai-z.egoitza.gipuzkoa.eus/sarrerak/" : "https://tbai-z.prep.gipuzkoa.eus/sarrerak/";
                return new RestEndpoints(URI.create(g + "alta"), URI.create(g + "baja"));
            case ARABA:
                String a = production ? "https://ticketbai.araba.eus/TicketBAI/v1/" : "https://pruebas-ticketbai.araba.eus/TicketBAI/v1/";
                return new RestEndpoints(URI.create(a + "facturas/"), URI.create(a + "anulaciones/"));
            case BIZKAIA:
            default:
                throw new IllegalArgumentException("Bizkaia no recibe ficheros por REST: se envían dentro del LROE (modelo 240 o 140)");
        }
    }

    public URI getInvoices() {
        return invoices;
    }

    public URI getAnnulments() {
        return annulments;
    }
}
