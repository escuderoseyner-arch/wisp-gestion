package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDate;

// monto siempre positivo (el tipo dice si suma o resta); saldo = cómo quedó la caja después de este movimiento
public record MovimientoCajaResponse(
        Integer id,
        LocalDate fecha,
        TipoMovimientoCaja tipo,
        String descripcion,
        BigDecimal monto,
        BigDecimal saldo,
        String hechoPor,        // null = automático
        boolean eliminable      // solo los retiros
) {
}
