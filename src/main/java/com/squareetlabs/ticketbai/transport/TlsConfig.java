package com.squareetlabs.ticketbai.transport;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * TLS mutuo con las Diputaciones: certificado de cliente (PKCS#12 del obligado, de su representante, sello o de
 * dispositivo) y confianza en las CA del JDK más las que se añadan.
 * <p>
 * Las tres Diputaciones cambian la cadena de sus servidores a Sectigo (producción entre noviembre de 2026 y enero de
 * 2027). El SDK incluye las raíces <i>Sectigo Public Server Authentication Root R46/E46</i> y las añade por defecto a las
 * del JDK ({@link Builder#withoutBundledRoots()} para no usarlas). Nunca se desactiva la comprobación del nombre del
 * servidor.
 */
public final class TlsConfig {

    private final SSLContext sslContext;

    private TlsConfig(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    private static final String[] BUNDLED_ROOTS = {
            "/trust/sectigo-public-server-authentication-root-r46.pem",
            "/trust/sectigo-public-server-authentication-root-e46.pem"
    };

    /** Raíces incluidas en el SDK (Sectigo R46 y E46). */
    public static List<X509Certificate> bundledRoots() {
        List<X509Certificate> roots = new ArrayList<>();
        for (String resource : BUNDLED_ROOTS) {
            try (InputStream in = TlsConfig.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Falta la raíz incluida " + resource);
                }
                roots.addAll(readCertificates(in));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return roots;
    }

    /** Lee certificados X.509 (PEM o DER) de un flujo. */
    public static List<X509Certificate> readCertificates(InputStream in) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certificates = new ArrayList<>();
            factory.generateCertificates(in).forEach(c -> certificates.add((X509Certificate) c));
            return certificates;
        } catch (CertificateException e) {
            throw new IllegalArgumentException("Certificado no válido: " + e.getMessage(), e);
        }
    }

    /** Constructor de {@link TlsConfig}. */
    public static final class Builder {
        private byte[] clientPkcs12;
        private char[] clientPassword;
        private final List<X509Certificate> extraTrusted = new ArrayList<>();
        private boolean bundledRoots = true;

        private Builder() {
        }

        /** No añade las raíces Sectigo incluidas en el SDK (solo las del JDK y las de {@link #trust}). */
        public Builder withoutBundledRoots() {
            this.bundledRoots = false;
            return this;
        }

        /** Certificado de cliente para el TLS mutuo (PKCS#12). Puede ser distinto del de firma. */
        public Builder clientCertificate(byte[] pkcs12, char[] password) {
            this.clientPkcs12 = Objects.requireNonNull(pkcs12, "pkcs12").clone();
            this.clientPassword = Objects.requireNonNull(password, "password").clone();
            return this;
        }

        /** Confía además en {@code certificate} (una raíz o CA que el JDK no trae). */
        public Builder trust(X509Certificate certificate) {
            extraTrusted.add(Objects.requireNonNull(certificate, "certificate"));
            return this;
        }

        public TlsConfig build() {
            if (clientPkcs12 == null) {
                throw new IllegalStateException("Las Diputaciones exigen certificado de cliente (TLS mutuo)");
            }
            try {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(new ByteArrayInputStream(clientPkcs12), clientPassword);
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, clientPassword);

                SSLContext context = SSLContext.getInstance("TLS");
                context.init(kmf.getKeyManagers(), new TrustManager[]{trustManager()}, null);
                return new TlsConfig(context);
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalArgumentException("No se pudo cargar el certificado de cliente: " + e.getMessage(), e);
            } finally {
                Arrays.fill(clientPassword, '\0');
            }
        }

        private X509TrustManager trustManager() throws GeneralSecurityException, IOException {
            X509TrustManager jdk = defaultTrustManager(null);
            List<X509Certificate> trusted = new ArrayList<>(extraTrusted);
            if (bundledRoots) {
                trusted.addAll(bundledRoots());
            }
            if (trusted.isEmpty()) {
                return jdk;
            }
            KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
            extra.load(null, null);
            for (int i = 0; i < trusted.size(); i++) {
                extra.setCertificateEntry("extra-" + i, trusted.get(i));
            }
            X509TrustManager added = defaultTrustManager(extra);
            return new CompositeTrustManager(jdk, added);
        }

        private static X509TrustManager defaultTrustManager(KeyStore store) throws GeneralSecurityException {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
            throw new IllegalStateException("No hay gestor de confianza X.509");
        }
    }

    /** Acepta el servidor si lo acepta el almacén del JDK o el de las CA añadidas. */
    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager first;
        private final X509TrustManager second;

        CompositeTrustManager(X509TrustManager first, X509TrustManager second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            first.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                first.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                second.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = first.getAcceptedIssuers();
            X509Certificate[] b = second.getAcceptedIssuers();
            X509Certificate[] all = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }
    }
}
