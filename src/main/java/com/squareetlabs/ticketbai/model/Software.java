package com.squareetlabs.ticketbai.model;

import java.util.Objects;

/**
 * Software garante ({@code HuellaTBAI/Software}). La licencia, el NIF de la entidad desarrolladora y el nombre tienen
 * que coincidir <b>exactamente</b> con los del Registro de Software TicketBAI; si no, Hacienda devuelve un aviso.
 */
public final class Software {

    private final String license;
    private final Party developer;
    private final String name;
    private final String version;

    public Software(String license, Party developer, String name, String version) {
        this.license = Objects.requireNonNull(license, "license");
        this.developer = Objects.requireNonNull(developer, "developer");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
    }

    public String getLicense() {
        return license;
    }

    public Party getDeveloper() {
        return developer;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }
}
