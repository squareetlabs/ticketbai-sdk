package com.squareetlabs.ticketbai.transport;

/**
 * No se obtuvo una respuesta válida de la Diputación (red, tiempo de espera, error HTTP o respuesta con formato
 * inesperado). El fichero no se ha registrado con seguridad: hay que reintentar el envío con el <b>mismo</b> fichero.
 */
public final class TransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public TransportException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** Código HTTP, o 0 si no hubo respuesta. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
