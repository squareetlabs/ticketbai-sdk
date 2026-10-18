package com.squareetlabs.ticketbai.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Lee los ejemplos oficiales de {@code src/test/resources/examples}. */
public final class Examples {

    private Examples() {
    }

    public static byte[] read(String path) {
        try (InputStream in = Examples.class.getResourceAsStream("/examples/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("No existe el ejemplo " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
