package com.squareetlabs.ticketbai.validation;

import java.util.List;
import java.util.stream.Collectors;

/** La factura tiene errores de validación: no se firma ni se envía. */
public final class InvalidInvoiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ValidationIssue> issues;

    public InvalidInvoiceException(List<ValidationIssue> issues) {
        super("La factura no es válida: " + issues.stream().filter(ValidationIssue::isError)
                .map(ValidationIssue::toString).collect(Collectors.joining("; ")));
        this.issues = List.copyOf(issues);
    }

    /** Todos los problemas (errores y avisos). */
    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
