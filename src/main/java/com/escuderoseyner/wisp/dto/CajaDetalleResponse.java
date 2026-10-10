package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// Saldo actual (puede ser negativo), totales del mes en curso y configuración del descuento
public record CajaDetalleResponse(
        Integer id,
        Integer redId,
        String redNombre,
        String moneda,
        BigDecimal saldo,
        String mes,                 // "2026-10"
        BigDecimal ingresosMes,
        BigDecimal descuentosMes,
        BigDecimal retirosMes,
        Descuento descuento
) {
    public record Descuento(
            BigDecimal monto,
            Integer dia,
            boolean activo,
            LocalDate desde,
            LocalDate proximaFecha  // null si está desactivado
    ) {
    }
}
