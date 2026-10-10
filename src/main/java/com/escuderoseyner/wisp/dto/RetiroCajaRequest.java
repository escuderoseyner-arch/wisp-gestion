package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RetiroCajaRequest(
        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que 0")
        @Digits(integer = 6, fraction = 2, message = "El monto puede tener hasta 6 cifras enteras y 2 decimales")
        BigDecimal monto,

        LocalDate fecha,            // vacía = hoy (hora de Lima); no puede ser futura

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 255, message = "La descripción no puede tener más de 255 caracteres")
        String descripcion
) {
}
