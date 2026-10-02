package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;

public record PlanResponse(
        Integer id,
        String nombre,
        Integer bajadaMbps,
        Integer subidaMbps,
        BigDecimal precio,
        boolean activo
) {
}
