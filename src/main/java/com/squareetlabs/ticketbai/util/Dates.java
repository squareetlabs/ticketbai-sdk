package com.squareetlabs.ticketbai.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Fechas de TicketBAI: siempre {@code dd-mm-aaaa}. */
public final class Dates {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private Dates() {
    }

    public static String format(LocalDate date) {
        return DATE.format(date);
    }

    public static LocalDate parse(String value) {
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha no válida (dd-mm-aaaa): " + value, e);
        }
    }
}
