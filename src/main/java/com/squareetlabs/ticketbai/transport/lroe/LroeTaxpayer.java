package com.squareetlabs.ticketbai.transport.lroe;

import java.util.Objects;

/**
 * Obligado tributario del envío. En el modelo 240 (persona jurídica) basta la razón social. En el 140 (persona física)
 * la cabecera {@code eus-bizkaia-n3-data} lleva el nombre y los apellidos por separado; en el XML van como
 * «APELLIDO1 APELLIDO2 NOMBRE».
 */
public final class LroeTaxpayer {

    private final String nif;
    private final String name;
    private final String firstSurname;
    private final String secondSurname;

    private LroeTaxpayer(String nif, String name, String firstSurname, String secondSurname) {
        this.nif = Objects.requireNonNull(nif, "nif");
        this.name = Objects.requireNonNull(name, "name");
        this.firstSurname = firstSurname;
        this.secondSurname = secondSurname;
    }

    /** Persona jurídica (modelo 240). */
    public static LroeTaxpayer legalEntity(String nif, String businessName) {
        return new LroeTaxpayer(nif, businessName, null, null);
    }

    /** Persona física (modelo 140). {@code secondSurname} puede ser {@code null}. */
    public static LroeTaxpayer individual(String nif, String givenName, String firstSurname, String secondSurname) {
        return new LroeTaxpayer(nif, givenName, Objects.requireNonNull(firstSurname, "firstSurname"), secondSurname);
    }

    public boolean isIndividual() {
        return firstSurname != null;
    }

    public String getNif() {
        return nif;
    }

    /** Razón social, o el nombre de pila de una persona física. */
    public String getName() {
        return name;
    }

    public String getFirstSurname() {
        return firstSurname;
    }

    public String getSecondSurname() {
        return secondSurname;
    }

    /** {@code ApellidosNombreRazonSocial} del XML. */
    public String getFullName() {
        if (!isIndividual()) {
            return name;
        }
        return firstSurname + (secondSurname == null || secondSurname.isBlank() ? "" : " " + secondSurname) + " " + name;
    }
}
