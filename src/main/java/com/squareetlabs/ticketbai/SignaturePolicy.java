package com.squareetlabs.ticketbai;

import java.util.Objects;

/**
 * Política de firma XAdES-EPES de un territorio: identificador ({@code SigPolicyId}) y hash SHA-256 en Base64 del
 * documento de la política ({@code SigPolicyHash}).
 */
public final class SignaturePolicy {

    private final String identifier;
    private final String sha256Base64;

    public SignaturePolicy(String identifier, String sha256Base64) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.sha256Base64 = Objects.requireNonNull(sha256Base64, "sha256Base64");
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getSha256Base64() {
        return sha256Base64;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
