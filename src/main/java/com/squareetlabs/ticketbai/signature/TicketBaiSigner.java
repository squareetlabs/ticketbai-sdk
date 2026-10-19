package com.squareetlabs.ticketbai.signature;

import com.squareetlabs.ticketbai.SignaturePolicy;
import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.util.XmlWriter;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.Policy;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.token.SignatureTokenConnection;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.reference.DSSReference;
import eu.europa.esig.dss.xades.reference.EnvelopedSignatureTransform;
import eu.europa.esig.dss.xades.signature.XAdESService;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Firma XAdES-EPES <i>enveloped</i> de los ficheros TicketBAI con EU DSS: XAdES Baseline-B con el identificador de
 * política del territorio ({@code SignaturePolicyIdentifier}, con {@code SPURI}), {@code SigningTime} y
 * {@code SigningCertificate}. Firma RSA con SHA-256, que vale en los tres territorios (Álava solo admite RSA).
 * <p>
 * El firmante se crea con el certificado del obligado, de su representante, sello o de dispositivo (PKCS#12). Cierra
 * el almacén con {@link #close()}.
 */
public final class TicketBaiSigner implements AutoCloseable {

    private static final String INCLUSIVE_C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

    private final SignatureTokenConnection token;
    private final DSSPrivateKeyEntry key;
    private final XAdESService service = new XAdESService(new CommonCertificateVerifier());

    private TicketBaiSigner(SignatureTokenConnection token, DSSPrivateKeyEntry key) {
        this.token = token;
        this.key = key;
    }

    /** Firmante con el primer certificado con clave privada de un PKCS#12. */
    public static TicketBaiSigner pkcs12(byte[] pkcs12, char[] password) {
        Objects.requireNonNull(pkcs12, "pkcs12");
        Objects.requireNonNull(password, "password");
        Pkcs12SignatureToken token = new Pkcs12SignatureToken(pkcs12, new KeyStore.PasswordProtection(password));
        return fromToken(token);
    }

    /** Firmante con el primer certificado con clave privada de un PKCS#12 leído de {@code in} (no se cierra). */
    public static TicketBaiSigner pkcs12(InputStream in, char[] password) {
        try {
            return pkcs12(in.readAllBytes(), password);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Firmante con un token de DSS ya abierto (PKCS#11, almacén del sistema...). Usa su primera clave. */
    public static TicketBaiSigner fromToken(SignatureTokenConnection token) {
        List<DSSPrivateKeyEntry> keys = token.getKeys();
        if (keys.isEmpty()) {
            token.close();
            throw new IllegalArgumentException("El almacén no tiene ninguna clave privada");
        }
        return new TicketBaiSigner(token, keys.get(0));
    }

    /** Firma el fichero ({@code TicketBai} o {@code AnulaTicketBai}) con la política de {@code territory}. */
    public SignedXml sign(Document unsigned, Territory territory) {
        return sign(XmlWriter.toBytes(unsigned), territory);
    }

    /** Firma el fichero serializado ({@code TicketBai} o {@code AnulaTicketBai}) con la política de {@code territory}. */
    public SignedXml sign(byte[] unsignedXml, Territory territory) {
        Objects.requireNonNull(unsignedXml, "unsignedXml");
        DSSDocument document = new InMemoryDocument(unsignedXml);
        XAdESSignatureParameters parameters = parameters(Objects.requireNonNull(territory, "territory").getSignaturePolicy(),
                document);
        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue value = token.sign(dataToSign, parameters.getDigestAlgorithm(), key);
        DSSDocument signed = service.signDocument(document, parameters, value);
        byte[] bytes = readAll(signed);
        return new SignedXml(bytes, signatureValueOf(bytes));
    }

    private XAdESSignatureParameters parameters(SignaturePolicy signaturePolicy, DSSDocument document) {
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        // Como los ficheros oficiales: referencia al documento entero con la transformación enveloped-signature, y C14N
        // inclusiva (no el filtro XPath ni la C14N exclusiva que DSS usa por defecto).
        DSSReference reference = new DSSReference();
        reference.setId("r-" + UUID.randomUUID());
        reference.setUri("");
        reference.setContents(document);
        reference.setDigestMethodAlgorithm(DigestAlgorithm.SHA256);
        reference.setTransforms(List.of(new EnvelopedSignatureTransform()));
        parameters.setReferences(List.of(reference));
        parameters.setSignedInfoCanonicalizationMethod(INCLUSIVE_C14N);
        parameters.setSignedPropertiesCanonicalizationMethod(INCLUSIVE_C14N);
        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificateDigestMethod(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(key.getCertificate());
        parameters.setCertificateChain(key.getCertificateChain());
        parameters.setEn319132(false);

        Policy policy = new Policy();
        policy.setId(signaturePolicy.getIdentifier());
        policy.setDigestAlgorithm(DigestAlgorithm.SHA256);
        policy.setDigestValue(Base64.getDecoder().decode(signaturePolicy.getSha256Base64()));
        policy.setSpuri(signaturePolicy.getIdentifier());
        parameters.bLevel().setSignaturePolicy(policy);
        return parameters;
    }

    private static byte[] readAll(DSSDocument document) {
        try (InputStream in = document.openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String signatureValueOf(byte[] signedXml) {
        NodeList values = Xml.parse(signedXml).getElementsByTagNameNS(Xml.DSIG_NS, "SignatureValue");
        if (values.getLength() != 1) {
            throw new IllegalStateException("El fichero firmado no tiene un único SignatureValue");
        }
        return values.item(0).getTextContent().replaceAll("\\s", "");
    }

    @Override
    public void close() {
        token.close();
    }
}
