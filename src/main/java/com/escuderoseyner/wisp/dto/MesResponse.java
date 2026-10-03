package com.escuderoseyner.wisp.dto;

import java.time.LocalDate;

// Un mes de un cliente: su estado, hasta cuándo puede pagar sin vencerse y el pago si lo hay
public record MesResponse(
        String periodo,          // "2026-10"
        EstadoMes estado,
        LocalDate vence,         // día de pago + tolerancia
        PagoResponse pago        // null si no está pagado
) {
}
