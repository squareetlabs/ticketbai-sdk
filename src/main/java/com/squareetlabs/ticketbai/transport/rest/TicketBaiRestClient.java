package com.squareetlabs.ticketbai.transport.rest;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.ZuzenduFile;
import com.squareetlabs.ticketbai.signature.SignedXml;
import com.squareetlabs.ticketbai.transport.SubmissionResult;
import com.squareetlabs.ticketbai.transport.TlsConfig;
import com.squareetlabs.ticketbai.transport.TransportException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Envío REST a Gipuzkoa y Álava: un fichero firmado por petición, {@code POST} con
 * {@code Content-Type: application/xml;charset=UTF-8}, sin comprimir y con TLS mutuo. La respuesta es síncrona.
 * <p>
 * Se envía inmediatamente después de firmar, sin esperar a la respuesta para entregar la factura. Si el envío falla
 * ({@link TransportException}), se reintenta con el <b>mismo</b> fichero: nunca se regenera ni se vuelve a firmar.
 */
public final class TicketBaiRestClient {

    private static final String CONTENT_TYPE = "application/xml;charset=UTF-8";

    private final RestEndpoints endpoints;
    private final HttpClient http;
    private final Duration requestTimeout;

    /** Cliente para los servicios oficiales de {@code territory} (Gipuzkoa o Álava). */
    public TicketBaiRestClient(Territory territory, Environment environment, TlsConfig tls) {
        this(RestEndpoints.of(territory, environment), tls, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    /** Cliente con servicios y tiempos de espera explícitos (por ejemplo, para pruebas). */
    public TicketBaiRestClient(RestEndpoints endpoints, TlsConfig tls, Duration connectTimeout, Duration requestTimeout) {
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.http = HttpClient.newBuilder()
                .sslContext(Objects.requireNonNull(tls, "tls").getSslContext())
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Envía un fichero de alta firmado. */
    public SubmissionResult submitInvoice(SignedXml signed) {
        return post(endpoints.getInvoices(), signed.getBytes());
    }

    /** Envía un fichero de anulación firmado. */
    public SubmissionResult submitAnnulment(SignedXml signed) {
        return post(endpoints.getAnnulments(), signed.getBytes());
    }

    /** Envía un fichero Zuzendu (subsanación o modificación de un alta, o subsanación de una anulación). */
    public SubmissionResult submitCorrection(ZuzenduFile file) {
        URI uri = file.getKind() == ZuzenduFile.Kind.INVOICE ? endpoints.getInvoiceCorrections()
                : endpoints.getAnnulmentCorrections();
        if (uri == null) {
            throw new IllegalStateException("No hay servicio Zuzendu configurado para " + file.getKind());
        }
        return post(uri, file.getBytes());
    }

    private SubmissionResult post(URI uri, byte[] body) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransportException("No se pudo enviar el fichero a " + uri + ": " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Envío interrumpido", 0, e);
        }
        if (response.statusCode() != 200) {
            throw new TransportException("La Diputación respondió HTTP " + response.statusCode(), response.statusCode(), null);
        }
        return ResponseParser.parse(response.body());
    }
}
