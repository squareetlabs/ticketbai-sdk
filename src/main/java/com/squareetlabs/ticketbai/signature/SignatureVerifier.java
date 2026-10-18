package com.squareetlabs.ticketbai.signature;

import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.util.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprobación local de la firma de un fichero TicketBAI con la API de firma XML del JDK: la firma y las referencias son
 * válidas con el certificado de {@code KeyInfo}, y la política es la del territorio. No valida la cadena de confianza del
 * certificado ni su revocación: eso lo hace Hacienda al recibir el fichero.
 */
public final class SignatureVerifier {

    private static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

    private SignatureVerifier() {
    }

    /** Problemas encontrados; lista vacía si la firma es válida y la política es la de {@code territory}. */
    public static List<String> verify(byte[] signedXml, Territory territory) {
        List<String> problems = new ArrayList<>();
        Document doc = Xml.parse(signedXml);
        NodeList signatures = doc.getElementsByTagNameNS(Xml.DSIG_NS, "Signature");
        if (signatures.getLength() != 1) {
            problems.add("El fichero tiene que llevar exactamente una firma");
            return problems;
        }
        markIdAttributes(doc.getDocumentElement());
        try {
            DOMValidateContext context = new DOMValidateContext(new KeyInfoCertificateSelector(), signatures.item(0));
            XMLSignature signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            if (!signature.validate(context)) {
                if (!signature.getSignatureValue().validate(context)) {
                    problems.add("El SignatureValue no corresponde al certificado de la firma");
                }
                for (Object reference : signature.getSignedInfo().getReferences()) {
                    javax.xml.crypto.dsig.Reference ref = (javax.xml.crypto.dsig.Reference) reference;
                    if (!ref.validate(context)) {
                        problems.add("La referencia «" + ref.getURI() + "» no coincide: el contenido firmado ha cambiado");
                    }
                }
                if (problems.isEmpty()) {
                    problems.add("La firma no es válida");
                }
            }
        } catch (Exception e) {
            problems.add("No se pudo comprobar la firma: " + e.getMessage());
        }
        checkPolicy(doc, territory, problems);
        return problems;
    }

    private static void checkPolicy(Document doc, Territory territory, List<String> problems) {
        NodeList ids = doc.getElementsByTagNameNS(XADES_NS, "SigPolicyId");
        NodeList hashes = doc.getElementsByTagNameNS(XADES_NS, "SigPolicyHash");
        if (ids.getLength() == 0 || hashes.getLength() == 0) {
            problems.add("Falta la política de firma (XAdES-EPES)");
            return;
        }
        String identifier = Xml.text((Element) ids.item(0), "Identifier");
        String hash = Xml.text((Element) hashes.item(0), "DigestValue");
        if (!territory.getSignaturePolicy().getIdentifier().equals(identifier)) {
            problems.add("La política de firma no es la de " + territory + ": " + identifier);
        }
        if (!territory.getSignaturePolicy().getSha256Base64().equals(hash)) {
            problems.add("El hash de la política de firma no es el publicado por " + territory + ": " + hash);
        }
    }

    /** Las referencias de XAdES apuntan a atributos {@code Id}: se registran como ID para que se puedan resolver. */
    private static void markIdAttributes(Element element) {
        if (element.hasAttributeNS(null, "Id")) {
            element.setIdAttributeNS(null, "Id", true);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                markIdAttributes((Element) children.item(i));
            }
        }
    }

    /** Toma la clave pública del certificado X.509 de {@code KeyInfo}. */
    private static final class KeyInfoCertificateSelector extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context)
                throws KeySelectorException {
            if (keyInfo != null) {
                for (Object info : keyInfo.getContent()) {
                    if (info instanceof X509Data) {
                        for (Object data : ((X509Data) info).getContent()) {
                            if (data instanceof X509Certificate) {
                                X509Certificate certificate = (X509Certificate) data;
                                return certificate::getPublicKey;
                            }
                        }
                    }
                }
            }
            throw new KeySelectorException("La firma no incluye el certificado X.509");
        }
    }
}
