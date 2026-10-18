package com.squareetlabs.ticketbai.xml;

import com.squareetlabs.ticketbai.model.Annulment;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.Software;
import com.squareetlabs.ticketbai.support.Examples;
import com.squareetlabs.ticketbai.support.XmlTreeComparator;
import com.squareetlabs.ticketbai.util.Xml;
import com.squareetlabs.ticketbai.util.XmlWriter;
import com.squareetlabs.ticketbai.validation.XsdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnulmentXmlBuilderTest {

    @Test
    @DisplayName("Álava: la anulación oficial sale igual y, con su firma, cumple el XSD de anulación")
    void arabaAnnulment() {
        Annulment annulment = new Annulment("A01111111", "AAA, BBBBB, CCCCCCCCC", "A2021", "121", LocalDate.of(2021, 11, 4));
        Software software = new Software("TBAGIXuwierwwer009", Party.withNif("12121212T", null), "nombre 9", "1.0.4");
        Document built = AnnulmentXmlBuilder.build(annulment, software, "8888889999911111");
        Document official = Xml.parse(Examples.read("araba/ejemplo3-anulacion.xml"));

        List<String> diffs = XmlTreeComparator.differences(official.getDocumentElement(), built.getDocumentElement(),
                Set.of("Signature"));
        assertTrue(diffs.isEmpty(), String.join("\n", diffs));

        Element signature = (Element) official.getElementsByTagNameNS(Xml.DSIG_NS, "Signature").item(0);
        built.getDocumentElement().appendChild(built.importNode(signature, true));
        List<String> errors = XsdValidator.validate(XmlWriter.toBytes(built), XsdValidator.Schema.ANNULMENT);
        assertTrue(errors.isEmpty(), errors.toString());
    }
}
