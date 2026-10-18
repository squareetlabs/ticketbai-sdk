package com.squareetlabs.ticketbai.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Línea de detalle ({@code IDDetalleFactura}). Obligatoria en Gipuzkoa; Bizkaia la ignora.
 *
 * @param unitPrice importe unitario <b>sin IVA</b>
 * @param discount  descuento en euros, en positivo; {@code null} si no hay
 * @param total     importe total de la línea <b>con IVA</b> (y recargo de equivalencia, si lo hay)
 */
public final class InvoiceLine {

    private final String description;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal discount;
    private final BigDecimal total;

    public InvoiceLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount, BigDecimal total) {
        this.description = Objects.requireNonNull(description, "description");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
        this.discount = discount;
        this.total = Objects.requireNonNull(total, "total");
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }
}
