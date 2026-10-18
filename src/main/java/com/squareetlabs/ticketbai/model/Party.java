package com.squareetlabs.ticketbai.model;

import com.squareetlabs.ticketbai.model.Codes.OtherIdType;

import java.util.Objects;

/**
 * Identificación de una persona o entidad: NIF español, o {@code IDOtro} (país, tipo de documento e identificador).
 * Se usa para el destinatario y para la entidad desarrolladora del software.
 */
public final class Party {

    private final String nif;
    private final String countryCode;
    private final OtherIdType otherIdType;
    private final String otherId;
    private final String name;
    private final String postalCode;
    private final String address;

    private Party(String nif, String countryCode, OtherIdType otherIdType, String otherId, String name, String postalCode,
                  String address) {
        this.nif = nif;
        this.countryCode = countryCode;
        this.otherIdType = otherIdType;
        this.otherId = otherId;
        this.name = name;
        this.postalCode = postalCode;
        this.address = address;
    }

    /** Con NIF español (9 caracteres). */
    public static Party withNif(String nif, String name) {
        return new Party(Objects.requireNonNull(nif, "nif"), null, null, null, name, null, null);
    }

    /** Con {@code IDOtro}. {@code countryCode} (ISO 3166-1 alfa-2) es obligatorio salvo con NIF-IVA. */
    public static Party withOtherId(String countryCode, OtherIdType type, String id, String name) {
        return new Party(null, countryCode, Objects.requireNonNull(type, "type"), Objects.requireNonNull(id, "id"), name,
                null, null);
    }

    /** Copia con código postal y dirección (destinatario; Gipuzkoa exige la dirección). */
    public Party withAddress(String postalCode, String address) {
        return new Party(nif, countryCode, otherIdType, otherId, name, postalCode, address);
    }

    public boolean hasNif() {
        return nif != null;
    }

    public String getNif() {
        return nif;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public OtherIdType getOtherIdType() {
        return otherIdType;
    }

    public String getOtherId() {
        return otherId;
    }

    public String getName() {
        return name;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getAddress() {
        return address;
    }
}
