package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// Datos para crear o editar un plan. "activo" no va aquí: se cambia con su propio endpoint.
public record PlanRequest(
        @NotBlank(message = "El nombre del plan es obligatorio")
        @Size(max = 50, message = "El nombre del plan no puede tener más de 50 caracteres")
        String nombre,

        @NotNull(message = "La velocidad de bajada es obligatoria")
        @Positive(message = "La velocidad de bajada debe ser mayor que 0")
        @Max(value = 10000, message = "La velocidad de bajada no puede superar 10000 Mbps")
        Integer bajadaMbps,

        @NotNull(message = "La velocidad de subida es obligatoria")
        @Positive(message = "La velocidad de subida debe ser mayor que 0")
        @Max(value = 10000, message = "La velocidad de subida no puede superar 10000 Mbps")
        Integer subidaMbps,

        // DECIMAL(8,2) en MySQL: hasta 6 cifras enteras y 2 decimales
        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser mayor que 0")
        @Digits(integer = 6, fraction = 2, message = "El precio puede tener hasta 6 cifras enteras y 2 decimales")
        BigDecimal precio
) {
}
