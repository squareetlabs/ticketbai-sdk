package com.squareetlabs.ticketbai.transport.kontsulta;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.Territory;
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
 * Cliente Kontsulta de Gipuzkoa y Álava: {@code POST} del XML de consulta, sin firmar, con TLS mutuo.
 * <p>
 * El certificado decide qué facturas se ven: el del emisor ve todas las suyas; un representante o un dispositivo, solo
 * las que envió con ese mismo certificado. Los envíos rechazados no aparecen. En Álava la información se actualiza una
 * vez al día (la del día anterior), así que no sirve para recuperar la cadena justo después de emitir.
 */
public final class KontsultaClient {

    private static final String CONTENT_TYPE = "application/xml;charset=UTF-8";

    private final URI invoicesEndpoint;
    private final URI latestEndpoint;
    private final HttpClient http;
    private final Duration requestTimeout;

    /**
     * Cliente para los servicios oficiales del territorio.
     *
     * @throws IllegalArgumentException en Bizkaia (se consulta en el LROE) y en la producción de Gipuzkoa, cuya URL no está
     *                                  publicada: en ese caso hay que indicarla con el otro constructor
     */
    public KontsultaClient(Territory territory, Environment environment, TlsConfig tls) {
        this(endpoint(territory, environment, false), endpoint(territory, environment, true), tls, Duration.ofSeconds(10),
                Duration.ofSeconds(60));
    }

    /** Cliente con los servicios y los tiempos de espera explícitos. */
    public KontsultaClient(URI invoicesEndpoint, URI latestEndpoint, TlsConfig tls, Duration connectTimeout,
                           Duration requestTimeout) {
        this.invoicesEndpoint = Objects.requireNonNull(invoicesEndpoint, "invoicesEndpoint");
        this.latestEndpoint = Objects.requireNonNull(latestEndpoint, "latestEndpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.http = HttpClient.newBuilder()
                .sslContext(Objects.requireNonNull(tls, "tls").getSslContext())
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** URL oficial del servicio de facturas ({@code latest = false}) o de última factura. */
    public static URI endpoint(Territory territory, Environment environment, boolean latest) {
        boolean production = Objects.requireNonNull(environment, "environment") == Environment.PRODUCTION;
        switch (Objects.requireNonNull(territory, "territory")) {
            case ARABA:
                String a = production ? "https://ticketbai.araba.eus/TicketBAIConsultas/v1/"
                        : "https://pruebas-ticketbai.araba.eus/TicketBAIConsultas/v1/";
                return URI.create(a + (latest ? "ultimafactura" : "facturaspaginadas/"));
            case GIPUZKOA:
                if (production) {
                    throw new IllegalArgumentException("Gipuzkoa no ha publicado la URL de producción de Kontsulta: indíquela "
                            + "con el constructor de URIs explícitas");
                }
                return URI.create("https://tbai-k.prep.gipuzkoa.eus/kontsulta/" + (latest ? "azkena" : "fakturak"));
            case BIZKAIA:
            default:
                throw new IllegalArgumentException("Bizkaia no tiene Kontsulta: la consulta se hace en el LROE (LroeClient.query)");
        }
    }

    /** Una página de las facturas enviadas que cumplen los filtros. */
    public KontsultaResult query(KontsultaQuery query) {
        return post(invoicesEndpoint, KontsultaXmlBuilder.toBytes(KontsultaXmlBuilder.query(query)));
    }

    /** La última factura admitida del emisor (como mucho una). */
    public KontsultaResult latest(String issuerNif) {
        return post(latestEndpoint, KontsultaXmlBuilder.toBytes(KontsultaXmlBuilder.latest(Objects.requireNonNull(issuerNif))));
    }

    private KontsultaResult post(URI uri, byte[] body) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransportException("No se pudo consultar " + uri + ": " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Consulta interrumpida", 0, e);
        }
        if (response.statusCode() != 200) {
            throw new TransportException("Kontsulta respondió HTTP " + response.statusCode(), response.statusCode(), null);
        }
        return KontsultaResponseParser.parse(response.body(), response.statusCode());
    }
}
