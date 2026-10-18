package com.squareetlabs.ticketbai.support;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compara dos árboles XML por elementos y textos: mismos nombres locales en el mismo orden y mismos textos (sin espacios a
 * los lados; los números se comparan por valor, porque el XSD admite {@code 880.0} y {@code 880.00}). Ignora los
 * elementos de {@code ignored} (por ejemplo, la firma).
 */
public final class XmlTreeComparator {

    private XmlTreeComparator() {
    }

    public static List<String> differences(Element expected, Element actual, Set<String> ignored) {
        List<String> diffs = new ArrayList<>();
        compare(expected, actual, "/" + expected.getLocalName(), ignored, diffs);
        return diffs;
    }

    private static void compare(Element expected, Element actual, String path, Set<String> ignored, List<String> diffs) {
        if (!expected.getLocalName().equals(actual.getLocalName())) {
            diffs.add(path + ": se esperaba " + expected.getLocalName() + " y hay " + actual.getLocalName());
            return;
        }
        List<Element> e = children(expected, ignored);
        List<Element> a = children(actual, ignored);
        if (e.isEmpty() && a.isEmpty()) {
            String et = expected.getTextContent().trim();
            String at = actual.getTextContent().trim();
            if (!sameValue(et, at)) {
                diffs.add(path + ": se esperaba «" + et + "» y hay «" + at + "»");
            }
            return;
        }
        for (int i = 0; i < Math.max(e.size(), a.size()); i++) {
            if (i >= e.size()) {
                diffs.add(path + ": sobra " + a.get(i).getLocalName());
            } else if (i >= a.size()) {
                diffs.add(path + ": falta " + e.get(i).getLocalName());
            } else {
                compare(e.get(i), a.get(i), path + "/" + e.get(i).getLocalName(), ignored, diffs);
            }
        }
    }

    private static boolean sameValue(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        try {
            return new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<Element> children(Element parent, Set<String> ignored) {
        List<Element> list = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && !ignored.contains(n.getLocalName())) {
                list.add((Element) n);
            }
        }
        return list;
    }
}
