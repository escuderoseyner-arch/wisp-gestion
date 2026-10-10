package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record DescuentoCajaRequest(
        @NotNull(message = "El monto es obligatorio")
        @PositiveOrZero(message = "El monto no puede ser negativo")
        @Digits(integer = 6, fraction = 2, message = "El monto puede tener hasta 6 cifras enteras y 2 decimales")
        BigDecimal monto,

        @NotNull(message = "El día es obligatorio")
        @Min(value = 1, message = "El día debe estar entre 1 y 28")
        @Max(value = 28, message = "El día debe estar entre 1 y 28")
        Integer dia,

        @NotNull(message = "Indica si el descuento está activo")
        Boolean activo
) {
}
