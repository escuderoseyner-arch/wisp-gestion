package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.MetodoPago;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Un registro puede cubrir varios meses: se guarda una fila de pago por cada mes, con el mismo monto
public record RegistrarPagoRequest(
        @NotNull(message = "Falta el cliente")
        Integer clienteId,

        @NotEmpty(message = "Elige al menos un mes")
        @Size(max = 12, message = "Se pueden registrar hasta 12 meses a la vez")
        List<@NotBlank(message = "Hay un mes vacío") String> periodos,   // ["2026-09", "2026-10"]

        @NotNull(message = "El monto es obligatorio")
        @Positive(message = "El monto debe ser mayor que 0")
        @Digits(integer = 6, fraction = 2, message = "El monto puede tener hasta 6 cifras enteras y 2 decimales")
        BigDecimal monto,

        @NotNull(message = "La fecha de pago es obligatoria")
        @PastOrPresent(message = "La fecha de pago no puede ser futura")
        LocalDate fechaPago,

        @NotNull(message = "Elige el método de pago")
        MetodoPago metodo,

        @Size(max = 255, message = "La observación no puede tener más de 255 caracteres")
        String observacion
) {
}
