package com.squareetlabs.ticketbai.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Lectura segura de XML (sin DTD ni entidades externas) y búsqueda de elementos por nombre local. */
public final class Xml {

    public static final String DSIG_NS = "http://www.w3.org/2000/09/xmldsig#";

    private Xml() {
    }

    /** Analiza {@code xml} con espacios de nombres y sin DTD ni entidades externas (XXE). */
    public static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("XML no válido: " + e.getMessage(), e);
        }
    }

    /** Primer descendiente de {@code parent} con ese nombre local, en cualquier espacio de nombres, o {@code null}. */
    public static Element firstChild(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    /** Texto (sin espacios a los lados) del primer descendiente con ese nombre local, o {@code null}. */
    public static String text(Element parent, String localName) {
        Element element = firstChild(parent, localName);
        return element == null ? null : element.getTextContent().trim();
    }

    /** Como {@link #text}, pero falla si el elemento no existe o está vacío. */
    public static String requiredText(Element parent, String localName) {
        String value = text(parent, localName);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Falta el elemento " + localName);
        }
        return value;
    }
}
