package com.squareetlabs.ticketbai.model;

import java.util.Objects;

/**
 * {@code TipoDesglose}: desglose de la factura ({@code DesgloseFactura}), cuando la contraparte es nacional o no hay
 * contraparte; o desglose por tipo de operación ({@code DesgloseTipoOperacion}: servicios y entregas), cuando la
 * contraparte no es nacional.
 */
public final class Breakdown {

    private final TaxBreakdown invoice;
    private final TaxBreakdown services;
    private final TaxBreakdown goods;

    private Breakdown(TaxBreakdown invoice, TaxBreakdown services, TaxBreakdown goods) {
        this.invoice = invoice;
        this.services = services;
        this.goods = goods;
    }

    /** {@code DesgloseFactura}. */
    public static Breakdown ofInvoice(TaxBreakdown breakdown) {
        return new Breakdown(Objects.requireNonNull(breakdown, "breakdown"), null, null);
    }

    /** {@code DesgloseTipoOperacion}: prestación de servicios y/o entrega de bienes (al menos uno). */
    public static Breakdown byOperationType(TaxBreakdown services, TaxBreakdown goods) {
        if (services == null && goods == null) {
            throw new IllegalArgumentException("El desglose por tipo de operación necesita servicios o entregas");
        }
        return new Breakdown(null, services, goods);
    }

    public boolean isByOperationType() {
        return invoice == null;
    }

    public TaxBreakdown getInvoice() {
        return invoice;
    }

    public TaxBreakdown getServices() {
        return services;
    }

    public TaxBreakdown getGoods() {
        return goods;
    }
}
