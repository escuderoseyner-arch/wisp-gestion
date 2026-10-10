package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;

public record CajaResumenResponse(
        Integer id,
        Integer redId,
        String redNombre,
        String moneda,
        BigDecimal saldo
) {
}
