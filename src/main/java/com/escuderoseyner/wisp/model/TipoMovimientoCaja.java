package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de caja_movimientos.tipo. El monto siempre es positivo:
// el tipo dice si suma (INGRESO_PAGO) o resta (DESCUENTO_MENSUAL, RETIRO).
public enum TipoMovimientoCaja {
    INGRESO_PAGO,
    DESCUENTO_MENSUAL,
    RETIRO;

    public boolean suma() {
        return this == INGRESO_PAGO;
    }
}
