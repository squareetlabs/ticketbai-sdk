package com.squareetlabs.ticketbai.validation;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Validación contra los XSD oficiales TicketBAI 1.2.2 (comunes a Álava, Bizkaia y Gipuzkoa), cargados del classpath
 * ({@code /xsd}). Los esquemas se compilan una vez y se reutilizan.
 */
public final class XsdValidator {

    /** Esquema contra el que se valida. */
    public enum Schema {
        INVOICE("ticketBaiV1-2-2.xsd"),
        ANNULMENT("Anula_ticketBaiV1-2-2.xsd"),
        RESPONSE("ticketBaiResponse.xsd"),
        /** Zuzendu (Gipuzkoa y Álava): subsanación o modificación de un fichero de alta. Sin firma. */
        ZUZENDU_INVOICE("ZuzenduAlta_ticketBaiV1-2-2.xsd"),
        /** Zuzendu (Gipuzkoa y Álava): subsanación de un fichero de anulación. Sin firma. */
        ZUZENDU_ANNULMENT("ZuzenduAnula_ticketBaiV1-2-2.xsd"),
        /** LROE de Bizkaia, modelo 240, subcapítulo 1.1: alta (y modificación) de facturas emitidas con software garante. */
        LROE_240_INVOICES("LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AltaPeticion_V1_0_2.xsd"),
        LROE_240_INVOICES_RESPONSE("LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AltaRespuesta_V1_0_1.xsd"),
        LROE_240_ANNULMENTS("LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AnulacionPeticion_V1_0_0.xsd"),
        LROE_240_ANNULMENTS_RESPONSE("LROE_PJ_240_1_1_FacturasEmitidas_ConSG_AnulacionRespuesta_V1_0_0.xsd"),
        /** LROE de Bizkaia, modelo 140, subcapítulo 1.1: alta (y modificación) de ingresos con factura con software garante. */
        LROE_140_INVOICES("LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AltaPeticion_V1_0_2.xsd"),
        LROE_140_INVOICES_RESPONSE("LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AltaRespuesta_V1_0_2.xsd"),
        LROE_140_ANNULMENTS("LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AnulacionPeticion_V1_0_0.xsd"),
        LROE_140_ANNULMENTS_RESPONSE("LROE_PF_140_1_1_Ingresos_ConfacturaConSG_AnulacionRespuesta_V1_0_0.xsd");

        private final String file;

        Schema(String file) {
            this.file = file;
        }
    }

    private static final String XSD_DIR = "/xsd/";
    private static final Map<Schema, javax.xml.validation.Schema> SCHEMAS = new EnumMap<>(Schema.class);

    private XsdValidator() {
    }

    /** Errores de validación ({@code "línea N: mensaje"}); lista vacía si el fichero cumple el esquema. */
    public static List<String> validate(byte[] xml, Schema schema) {
        List<String> errors = new ArrayList<>();
        Validator validator = compiled(schema).newValidator();
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new Collector(errors));
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            if (errors.isEmpty()) {
                errors.add(format(e));
            }
        } catch (SAXException | IOException e) {
            errors.add(e.getMessage());
        }
        return Collections.unmodifiableList(errors);
    }

    private static synchronized javax.xml.validation.Schema compiled(Schema schema) {
        return SCHEMAS.computeIfAbsent(schema, s -> {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setResourceResolver(new ClasspathResolver());
            try {
                return factory.newSchema(new StreamSource(open(s.file), s.file));
            } catch (SAXException e) {
                throw new IllegalStateException("No se pudo cargar el XSD " + s.file, e);
            }
        });
    }

    private static InputStream open(String file) {
        InputStream in = XsdValidator.class.getResourceAsStream(XSD_DIR + file);
        if (in == null) {
            throw new IllegalStateException("No existe el XSD " + file);
        }
        return in;
    }

    private static String format(SAXParseException e) {
        return "línea " + e.getLineNumber() + ": " + e.getMessage();
    }

    private static final class Collector implements ErrorHandler {
        private final List<String> errors;

        Collector(List<String> errors) {
            this.errors = errors;
        }

        @Override
        public void warning(SAXParseException e) {
            // Los avisos del analizador no invalidan el fichero.
        }

        @Override
        public void error(SAXParseException e) {
            errors.add(format(e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            errors.add(format(e));
            throw e;
        }
    }

    /** Resuelve los {@code import} de los XSD (firma XML) desde el classpath, sin acceder a la red. */
    private static final class ClasspathResolver implements LSResourceResolver {
        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            String file = systemId == null ? null : systemId.substring(systemId.lastIndexOf('/') + 1);
            if (file == null || XsdValidator.class.getResource(XSD_DIR + file) == null) {
                return null;
            }
            return new Input(publicId, systemId, open(file));
        }
    }

    private static final class Input implements LSInput {
        private final String publicId;
        private final String systemId;
        private final InputStream stream;

        Input(String publicId, String systemId, InputStream stream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.stream = stream;
        }

        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader characterStream) { }
        @Override public InputStream getByteStream() { return stream; }
        @Override public void setByteStream(InputStream byteStream) { }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String stringData) { }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String systemId) { }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String publicId) { }
        @Override public String getBaseURI() { return null; }
        @Override public void setBaseURI(String baseURI) { }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String encoding) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean certifiedText) { }
    }
}
