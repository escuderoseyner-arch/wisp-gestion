package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;
import java.util.List;

// movimientos: del más reciente al más antiguo
public record HistorialCajaResponse(
        String mes,                 // null = todo el historial
        String moneda,
        BigDecimal saldoInicial,    // saldo antes del primer movimiento listado
        List<MovimientoCajaResponse> movimientos
) {
}
