package com.squareetlabs.ticketbai.transport.lroe;

import com.squareetlabs.ticketbai.Environment;
import com.squareetlabs.ticketbai.transport.TlsConfig;
import com.squareetlabs.ticketbai.transport.TransportException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Envío al LROE de Bizkaia, modelos 240 y 140 (servicio de entradas {@code N3B4000M/aurkezpena}): el XML del envío comprimido con GZIP,
 * {@code Content-Type: application/octet-stream} y las cabeceras {@code eus-bizkaia-n3-*}, con TLS mutuo. La respuesta
 * llega comprimida con GZIP y el estado global va en las cabeceras.
 * <p>
 * En Bizkaia los ficheros TicketBAI no se envían uno a uno en el momento de expedir: se registran en el LROE dentro de
 * su plazo (trimestral, o 4 días para quien esté en un régimen tipo SII). Agrupa los ficheros de un obligado en lotes de
 * hasta 1.000.
 */
public final class LroeClient {

    public static final URI PRODUCTION = URI.create("https://sarrerak.bizkaia.eus/N3B4000M/aurkezpena");
    public static final URI TEST = URI.create("https://pruesarrerak.bizkaia.eus/N3B4000M/aurkezpena");
    public static final URI PRODUCTION_QUERY = URI.create("https://sarrerak.bizkaia.eus/N3B4001M/kontsulta");
    public static final URI TEST_QUERY = URI.create("https://pruesarrerak.bizkaia.eus/N3B4001M/kontsulta");

    private final URI endpoint;
    private final URI queryEndpoint;
    private final HttpClient http;
    private final Duration requestTimeout;

    public LroeClient(Environment environment, TlsConfig tls) {
        this(Objects.requireNonNull(environment, "environment") == Environment.PRODUCTION ? PRODUCTION : TEST,
                environment == Environment.PRODUCTION ? PRODUCTION_QUERY : TEST_QUERY, tls,
                Duration.ofSeconds(10), Duration.ofSeconds(120));
    }

    /** Cliente con el servicio de entradas y los tiempos de espera explícitos (sin servicio de consulta). */
    public LroeClient(URI endpoint, TlsConfig tls, Duration connectTimeout, Duration requestTimeout) {
        this(endpoint, null, tls, connectTimeout, requestTimeout);
    }

    /** Cliente con los servicios de entradas y de consulta explícitos (por ejemplo, para pruebas). */
    public LroeClient(URI endpoint, URI queryEndpoint, TlsConfig tls, Duration connectTimeout, Duration requestTimeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.queryEndpoint = queryEndpoint;
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.http = HttpClient.newBuilder()
                .sslContext(Objects.requireNonNull(tls, "tls").getSslContext())
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Envía el lote y devuelve el resultado de cada fichero. */
    public LroeResult submit(LroeBatch batch) {
        HttpResponse<byte[]> response = post(endpoint, LroeRequestBuilder.toBytes(batch),
                n3Data(batch.getModel(), batch.getTaxpayer(), batch.getFiscalYear().getValue()));
        return LroeResponseParser.parse(header(response, "eus-bizkaia-n3-tipo-respuesta"),
                header(response, "eus-bizkaia-n3-identificativo"), header(response, "eus-bizkaia-n3-codigo-respuesta"),
                header(response, "eus-bizkaia-n3-mensaje-respuesta"), gunzipIfNeeded(response.body()));
    }

    /**
     * Consulta ({@code C00}) las facturas registradas: sirve para conciliar el estado y para recuperar el último eslabón
     * de la cadena.
     *
     * @throws TransportException si no hay respuesta válida, o si el LROE responde «Incorrecto» (con su código)
     */
    public LroeQueryResult query(LroeQuery query) {
        if (queryEndpoint == null) {
            throw new IllegalStateException("Este cliente no tiene configurado el servicio de consulta");
        }
        HttpResponse<byte[]> response = post(queryEndpoint, LroeQueryBuilder.toBytes(query),
                n3Data(query.getModel(), query.getTaxpayer(), query.getFiscalYear().getValue()));
        if ("Incorrecto".equals(header(response, "eus-bizkaia-n3-tipo-respuesta"))) {
            throw new TransportException("La consulta del LROE es incorrecta: " + header(response, "eus-bizkaia-n3-codigo-respuesta")
                    + " " + header(response, "eus-bizkaia-n3-mensaje-respuesta"), 200, null);
        }
        return LroeQueryResponseParser.parse(gunzipIfNeeded(response.body()));
    }

    private HttpResponse<byte[]> post(URI uri, byte[] xml, String n3Data) {
        byte[] body = gzip(xml);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept-Encoding", "gzip")
                .header("Content-Encoding", "gzip")
                .header("Content-Type", "application/octet-stream")
                .header("eus-bizkaia-n3-version", "1.0")
                .header("eus-bizkaia-n3-content-type", "application/xml")
                .header("eus-bizkaia-n3-data", n3Data)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransportException("No se pudo enviar el lote al LROE: " + e.getMessage(), 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("Envío interrumpido", 0, e);
        }
        if (response.statusCode() != 200) {
            throw new TransportException("El LROE respondió HTTP " + response.statusCode(), response.statusCode(), null);
        }
        return response;
    }

    /**
     * Cabecera {@code eus-bizkaia-n3-data}: concepto LROE, apartado 1.1, obligado (en el 140, nombre y apellidos por
     * separado) y modelo con su ejercicio.
     */
    static String n3Data(LroeBatch batch) {
        return n3Data(batch.getModel(), batch.getTaxpayer(), batch.getFiscalYear().getValue());
    }

    static String n3Data(LroeModel model, LroeTaxpayer t, int fiscalYear) {
        StringBuilder inte = new StringBuilder("{\"nif\":\"").append(json(t.getNif())).append("\",\"nrs\":\"")
                .append(json(t.getName())).append('"');
        if (t.isIndividual()) {
            inte.append(",\"ap1\":\"").append(json(t.getFirstSurname())).append('"');
            if (t.getSecondSurname() != null && !t.getSecondSurname().isBlank()) {
                inte.append(",\"ap2\":\"").append(json(t.getSecondSurname())).append('"');
            }
        }
        inte.append('}');
        return "{\"con\":\"LROE\",\"apa\":\"1.1\",\"inte\":" + inte + ",\"drs\":{\"mode\":\""
                + model.code() + "\",\"ejer\":\"" + fiscalYear + "\"}}";
    }

    /** Texto JSON con los caracteres no ASCII escapados, para que la cabecera HTTP sea ASCII. */
    static String json(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c < 0x20 || c > 0x7E) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static byte[] gunzipIfNeeded(byte[] data) {
        if (data.length < 2 || (data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B) {
            return data;
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new TransportException("Respuesta del LROE con GZIP no válido", 200, e);
        }
    }
}
