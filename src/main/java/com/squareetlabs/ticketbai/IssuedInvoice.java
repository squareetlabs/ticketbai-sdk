package com.squareetlabs.ticketbai;

import com.squareetlabs.ticketbai.chain.ChainLink;
import com.squareetlabs.ticketbai.identifier.TbaiIdentifier;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.validation.ValidationIssue;

import java.util.List;

/**
 * Factura emitida: el fichero de alta firmado (que hay que enviar y conservar sin cambios), el código TicketBAI y la URL
 * del QR (que se imprimen en la factura) y el eslabón que tiene que encadenar la siguiente factura del emisor.
 */
public final class IssuedInvoice {

    private final SignedXml signedXml;
    private final TbaiIdentifier identifier;
    private final String qrUrl;
    private final ChainLink chainLink;
    private final List<ValidationIssue> warnings;

    IssuedInvoice(SignedXml signedXml, TbaiIdentifier identifier, String qrUrl, ChainLink chainLink,
                  List<ValidationIssue> warnings) {
        this.signedXml = signedXml;
        this.identifier = identifier;
        this.qrUrl = qrUrl;
        this.chainLink = chainLink;
        this.warnings = List.copyOf(warnings);
    }

    /** Avisos de la validación previa (Hacienda aceptaría el fichero, pero con aviso). */
    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    public SignedXml getSignedXml() {
        return signedXml;
    }

    public TbaiIdentifier getIdentifier() {
        return identifier;
    }

    /** Contenido del QR (URL de comprobación con el CRC-8). */
    public String getQrUrl() {
        return qrUrl;
    }

    /** Eslabón para {@code EncadenamientoFacturaAnterior} de la siguiente factura del emisor. */
    public ChainLink getChainLink() {
        return chainLink;
    }
}
