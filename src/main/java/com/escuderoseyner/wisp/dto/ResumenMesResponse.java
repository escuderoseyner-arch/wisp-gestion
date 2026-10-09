package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Pantalla de pagos de un mes: totales y estado de cada cliente que lo era ese mes (incluye retirados después)
public record ResumenMesResponse(
        String periodo,           // "2026-10"
        String moneda,
        BigDecimal cobrado,       // suma de los pagos de ese mes (de todos los clientes)
        BigDecimal pendiente,     // precio del plan de los clientes que aún no pagan ese mes
        int pagados,
        int pendientes,
        int vencidos,
        List<FilaMes> clientes
) {

    public record FilaMes(
            Integer clienteId,
            String codigo,
            String nombres,
            String zona,
            String planNombre,
            BigDecimal precioPlan,
            EstadoMes estado,
            LocalDate vence,
            PagoResponse pago,    // null si no pagó ese mes
            boolean retirado      // hoy está retirado (ese mes todavía era cliente, o pagó ese mes)
    ) {
    }
}
