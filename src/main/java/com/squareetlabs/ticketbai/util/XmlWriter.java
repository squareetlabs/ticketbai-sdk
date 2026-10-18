package com.squareetlabs.ticketbai.util;

import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;

/** Serializa un DOM a bytes UTF-8, sin sangrado (la firma depende de los bytes exactos). */
public final class XmlWriter {

    private XmlWriter() {
    }

    public static byte[] toBytes(Node node) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(node), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("No se pudo serializar el XML", e);
        }
    }
}
