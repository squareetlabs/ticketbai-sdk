package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Element;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Envío al LROE de Bizkaia (modelo 240, subcapítulo 1.1): ficheros TicketBAI firmados de <b>un solo obligado</b>, un
 * solo ejercicio y una sola operación, hasta 1.000 por envío. Los ficheros se envían tal cual, en Base64.
 */
public final class LroeBatch {

    public static final int MAX_FILES = 1000;

    private final String nif;
    private final String name;
    private final Year fiscalYear;
    private final LroeOperation operation;
    private final List<byte[]> files;

    private LroeBatch(String nif, String name, Year fiscalYear, LroeOperation operation, List<byte[]> files) {
        this.nif = nif;
        this.name = name;
        this.fiscalYear = fiscalYear;
        this.operation = operation;
        this.files = files;
    }

    /**
     * @param nif        NIF del obligado tributario (el emisor de todas las facturas)
     * @param name       nombre o razón social del obligado
     * @param fiscalYear ejercicio del modelo
     * @param operation  alta o anulación
     * @param files      ficheros firmados ({@code TicketBai} para el alta, {@code AnulaTicketBai} para la anulación)
     */
    public static LroeBatch of(String nif, String name, Year fiscalYear, LroeOperation operation, List<SignedXml> files) {
        Objects.requireNonNull(nif, "nif");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(fiscalYear, "fiscalYear");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(files, "files");
        if (files.isEmpty() || files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Un envío LROE lleva de 1 a 1000 ficheros: " + files.size());
        }
        String expectedRoot = operation == LroeOperation.INVOICES ? "TicketBai" : "AnulaTicketBai";
        List<byte[]> bytes = new ArrayList<>(files.size());
        for (SignedXml file : files) {
            byte[] content = file.getBytes();
            Element root = Xml.parse(content).getDocumentElement();
            if (!expectedRoot.equals(root.getLocalName())) {
                throw new IllegalArgumentException("Para " + operation + " cada fichero tiene que ser " + expectedRoot
                        + ", no " + root.getLocalName());
            }
            String issuer = Xml.requiredText(Xml.firstChild(root, "Emisor"), "NIF");
            if (!nif.equals(issuer)) {
                throw new IllegalArgumentException("Un envío LROE es de un solo obligado: " + issuer + " no es " + nif);
            }
            bytes.add(content);
        }
        return new LroeBatch(nif, name, fiscalYear, operation, Collections.unmodifiableList(bytes));
    }

    public String getNif() {
        return nif;
    }

    public String getName() {
        return name;
    }

    public Year getFiscalYear() {
        return fiscalYear;
    }

    public LroeOperation getOperation() {
        return operation;
    }

    /** Bytes de los ficheros firmados, en el orden del envío. */
    public List<byte[]> getFiles() {
        return files;
    }
}
