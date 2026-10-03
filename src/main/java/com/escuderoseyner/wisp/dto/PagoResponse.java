package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoResponse(
        Integer id,
        String periodo,          // "2026-10"
        BigDecimal monto,
        LocalDate fechaPago,
        MetodoPago metodo,
        String observacion,
        String registradoPor     // nombre del admin
) {
}
