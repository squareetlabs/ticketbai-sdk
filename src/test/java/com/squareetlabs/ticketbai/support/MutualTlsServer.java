package com.squareetlabs.ticketbai.support;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.squareetlabs.ticketbai.transport.TlsConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * Servidor HTTPS local que, como las Diputaciones, exige certificado de cliente. Acepta solo el certificado de
 * {@code test-signer.p12}, guarda la última petición y responde lo que se le indique.
 */
public final class MutualTlsServer implements AutoCloseable {

    private static final char[] PASSWORD = "ticketbai-test".toCharArray();

    private final HttpsServer server;
    private volatile int status = 200;
    private volatile byte[] response = new byte[0];
    private volatile byte[] lastBody;
    private volatile String lastContentType;
    private volatile String lastMethod;
    private volatile com.sun.net.httpserver.Headers lastHeaders;
    private volatile java.util.Map<String, String> responseHeaders = java.util.Map.of();

    public MutualTlsServer() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = MutualTlsServer.class.getResourceAsStream("/certs/test-server.p12")) {
            keyStore.load(in, PASSWORD);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, PASSWORD);
        KeyStore trusted = KeyStore.getInstance(KeyStore.getDefaultType());
        trusted.load(null, null);
        trusted.setCertificateEntry("client", certificate("/certs/test-signer.cer"));
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trusted);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                params.setNeedClientAuth(true);
            }
        });
        server.createContext("/", exchange -> {
            lastMethod = exchange.getRequestMethod();
            lastHeaders = exchange.getRequestHeaders();
            responseHeaders.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            lastContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            try (InputStream in = exchange.getRequestBody()) {
                lastBody = in.readAllBytes();
            }
            byte[] body = response;
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    public static X509Certificate certificate(String resource) throws Exception {
        try (InputStream in = MutualTlsServer.class.getResourceAsStream(resource)) {
            return TlsConfig.readCertificates(in).get(0);
        }
    }

    public URI uri(String path) {
        return URI.create("https://localhost:" + server.getAddress().getPort() + path);
    }

    public void respond(int status, byte[] body) {
        this.status = status;
        this.response = body;
    }

    public void respond(int status, byte[] body, java.util.Map<String, String> headers) {
        this.responseHeaders = headers;
        respond(status, body);
    }

    public String getLastHeader(String name) {
        return lastHeaders == null ? null : lastHeaders.getFirst(name);
    }

    public byte[] getLastBody() {
        return lastBody;
    }

    public String getLastContentType() {
        return lastContentType;
    }

    public String getLastMethod() {
        return lastMethod;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
