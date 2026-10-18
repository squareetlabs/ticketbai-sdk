package com.squareetlabs.ticketbai.validation;

import com.squareetlabs.ticketbai.Territory;
import com.squareetlabs.ticketbai.model.Breakdown;
import com.squareetlabs.ticketbai.model.Codes.ExemptionCause;
import com.squareetlabs.ticketbai.model.Codes.OtherIdType;
import com.squareetlabs.ticketbai.model.Codes.RectificationCode;
import com.squareetlabs.ticketbai.model.Codes.RegimeKey;
import com.squareetlabs.ticketbai.model.Invoice;
import com.squareetlabs.ticketbai.model.InvoiceLine;
import com.squareetlabs.ticketbai.model.Party;
import com.squareetlabs.ticketbai.model.TaxBreakdown;
import com.squareetlabs.ticketbai.model.VatDetail;
import com.squareetlabs.ticketbai.validation.ValidationIssue.Severity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validación de negocio de una factura antes de firmarla: cuadres que comprueba Hacienda, combinaciones de claves y
 * datos que exige cada territorio. Complementa al XSD, que solo comprueba la forma del fichero.
 * <p>
 * Solo incluye reglas publicadas por las Diputaciones; la referencia de cada problema indica su fuente (el código de
 * validación de Gipuzkoa o el documento de Álava). La tolerancia de los cuadres no está publicada: por defecto es
 * exacta y se puede ampliar con {@link #withTolerance(BigDecimal)}.
 */
public final class InvoiceValidator {

    private static final String ARABA_COMMUNICATION = "Álava, comunicación a desarrolladores";
    private static final Set<Character> PUBLIC_ADMINISTRATION_NIF = Set.of('P', 'Q', 'S', 'V');

    private final BigDecimal tolerance;

    private InvoiceValidator(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }

    /** Validador con cuadres exactos. */
    public static InvoiceValidator strict() {
        return new InvoiceValidator(BigDecimal.ZERO);
    }

    /** Validador que admite una diferencia de hasta {@code tolerance} euros en los cuadres. */
    public InvoiceValidator withTolerance(BigDecimal tolerance) {
        if (Objects.requireNonNull(tolerance, "tolerance").signum() < 0) {
            throw new IllegalArgumentException("La tolerancia no puede ser negativa");
        }
        return new InvoiceValidator(tolerance);
    }

    /** Problemas de {@code invoice} para {@code territory}; lista vacía si no hay ninguno. */
    public List<ValidationIssue> validate(Invoice invoice, Territory territory) {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(territory, "territory");
        List<ValidationIssue> issues = new ArrayList<>();
        totals(invoice, issues);
        regimeKeys(invoice, issues);
        recipients(invoice, issues);
        breakdownType(invoice, issues);
        if (territory == Territory.GIPUZKOA) {
            gipuzkoa(invoice, issues);
        }
        if (invoice.getOperationDate() != null && invoice.getOperationDate().equals(invoice.getIssueDate())) {
            issues.add(warning("DatosFactura/FechaOperacion",
                    "Solo se informa si es distinta de la fecha de expedición", "Anexo I"));
        }
        return issues;
    }

    private void totals(Invoice invoice, List<ValidationIssue> issues) {
        BigDecimal total = invoice.getTotalAmount();
        if (!invoice.getLines().isEmpty()) {
            BigDecimal lines = invoice.getLines().stream().map(InvoiceLine::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!matches(lines, total)) {
                issues.add(error("DatosFactura/ImporteTotalFactura", "La suma de las líneas (" + lines.toPlainString()
                        + ") no coincide con el importe total (" + total.toPlainString() + ")", ARABA_COMMUNICATION));
            }
        }
        BigDecimal breakdown = breakdownTotal(invoice.getBreakdown());
        if (!matches(breakdown, total)) {
            issues.add(error("TipoDesglose", "La suma del desglose (" + breakdown.toPlainString()
                    + ") no coincide con el importe total (" + total.toPlainString() + ")", ARABA_COMMUNICATION));
        }
    }

    private static BigDecimal breakdownTotal(Breakdown breakdown) {
        if (!breakdown.isByOperationType()) {
            return taxTotal(breakdown.getInvoice());
        }
        return taxTotal(breakdown.getServices()).add(taxTotal(breakdown.getGoods()));
    }

    /** Bases exentas + (base + cuota + recargo) de lo no exento + importes no sujetos. */
    private static BigDecimal taxTotal(TaxBreakdown breakdown) {
        if (breakdown == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = breakdown.getExempt().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (List<VatDetail> details : breakdown.getNonExempt().values()) {
            for (VatDetail d : details) {
                sum = sum.add(d.getTaxableBase()).add(orZero(d.getQuota())).add(orZero(d.getSurchargeQuota()));
            }
        }
        return sum.add(breakdown.getNotSubject().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static void regimeKeys(Invoice invoice, List<ValidationIssue> issues) {
        List<RegimeKey> keys = invoice.getRegimeKeys();
        if (new HashSet<>(keys).size() != keys.size()) {
            issues.add(error("DatosFactura/Claves", "Una clave de régimen está repetida", "Anexo I"));
        }
        if (keys.contains(RegimeKey.GENERAL) && keys.contains(RegimeKey.EXPORT)) {
            issues.add(error("DatosFactura/Claves", "Las claves 01 (general) y 02 (exportación) no pueden ir juntas",
                    ARABA_COMMUNICATION));
        }
        if (keys.contains(RegimeKey.PUBLIC_WORKS_CERTIFICATION)) {
            boolean publicAdministration = invoice.getRecipients().stream().anyMatch(r -> r.hasNif()
                    && PUBLIC_ADMINISTRATION_NIF.contains(Character.toUpperCase(r.getNif().charAt(0))));
            if (!publicAdministration) {
                issues.add(error("Sujetos/Destinatarios", "Con la clave 14 (certificación de obra a una Administración"
                        + " Pública) el destinatario tiene que tener un NIF que empiece por P, Q, S o V", "Gipuzkoa 1310"));
            }
        }
    }

    private static void recipients(Invoice invoice, List<ValidationIssue> issues) {
        for (Party recipient : invoice.getRecipients()) {
            if (recipient.hasNif()) {
                continue;
            }
            if (recipient.getOtherIdType() != OtherIdType.VAT_NUMBER && recipient.getCountryCode() == null) {
                issues.add(error("IDDestinatario/IDOtro/CodigoPais",
                        "El código de país es obligatorio salvo con NIF-IVA (tipo 02)", "Gipuzkoa 1124"));
            }
            if ("ES".equalsIgnoreCase(recipient.getCountryCode()) && recipient.getOtherIdType() != OtherIdType.PASSPORT) {
                issues.add(error("IDDestinatario/IDOtro/CodigoPais",
                        "Con país ES solo se admite el pasaporte (tipo 03); un NIF español va en NIF", "Gipuzkoa 1168"));
            }
        }
        boolean e5 = exemptions(invoice.getBreakdown()).contains(ExemptionCause.E5);
        boolean vatNumber = invoice.getRecipients().stream()
                .anyMatch(r -> !r.hasNif() && r.getOtherIdType() == OtherIdType.VAT_NUMBER);
        if (e5 && !vatNumber) {
            issues.add(error("TipoDesglose/.../CausaExencion", "La exención E5 (entrega intracomunitaria) exige un"
                    + " destinatario con NIF-IVA (IDOtro tipo 02)", "Gipuzkoa 1303"));
        }
    }

    private static void breakdownType(Invoice invoice, List<ValidationIssue> issues) {
        boolean foreign = invoice.getRecipients().stream()
                .anyMatch(r -> !r.hasNif() || Character.toUpperCase(r.getNif().charAt(0)) == 'N');
        if (foreign && !invoice.getBreakdown().isByOperationType()) {
            issues.add(error("TipoDesglose", "Con un destinatario extranjero (IDOtro o NIF que empieza por N) el"
                    + " desglose va por tipo de operación (servicios o entregas)", "Gipuzkoa 5033"));
        }
    }

    private static void gipuzkoa(Invoice invoice, List<ValidationIssue> issues) {
        if (invoice.getLines().isEmpty()) {
            issues.add(error("DatosFactura/DetallesFactura", "En Gipuzkoa las líneas de detalle son obligatorias",
                    "Gipuzkoa 003"));
        }
        boolean r5 = invoice.getRectification() != null && invoice.getRectification().getCode() == RectificationCode.R5;
        boolean recipientRequired = !invoice.isSimplified() || invoice.isSubstitutesSimplified()
                || (invoice.getRectification() != null && !r5);
        if (recipientRequired && invoice.getRecipients().isEmpty()) {
            issues.add(error("Sujetos/Destinatarios", "En Gipuzkoa la factura completa lleva destinatario",
                    "Gipuzkoa 1158"));
        }
        if (recipientRequired) {
            for (Party recipient : invoice.getRecipients()) {
                if (isBlank(recipient.getPostalCode())) {
                    issues.add(warning("IDDestinatario/CodigoPostal", "En Gipuzkoa la factura completa lleva el código"
                            + " postal del destinatario (" + recipient.getName() + ")", "Gipuzkoa 5031"));
                }
                if (isBlank(recipient.getAddress())) {
                    issues.add(warning("IDDestinatario/Direccion", "En Gipuzkoa la factura completa lleva la dirección"
                            + " del destinatario (" + recipient.getName() + ")", "Gipuzkoa 5032"));
                }
            }
        }
    }

    private static Set<ExemptionCause> exemptions(Breakdown breakdown) {
        Set<ExemptionCause> causes = new HashSet<>();
        for (TaxBreakdown tb : new TaxBreakdown[]{breakdown.getInvoice(), breakdown.getServices(), breakdown.getGoods()}) {
            if (tb != null) {
                causes.addAll(tb.getExempt().keySet());
            }
        }
        return causes;
    }

    private boolean matches(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationIssue error(String field, String message, String reference) {
        return new ValidationIssue(Severity.ERROR, field, message, reference);
    }

    private static ValidationIssue warning(String field, String message, String reference) {
        return new ValidationIssue(Severity.WARNING, field, message, reference);
    }
}
