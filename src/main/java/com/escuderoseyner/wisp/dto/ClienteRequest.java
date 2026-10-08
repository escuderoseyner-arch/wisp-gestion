package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Crear o editar un cliente. El estado no va aquí: tiene sus propias acciones.
// El formato del código, celular e IP se revisa en ClienteService después de limpiar
// espacios, para poder aceptar "987 654 321" o "c-07".
public record ClienteRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 10, message = "El código no puede tener más de 10 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
        String nombres,

        @Size(max = 15, message = "El celular no puede tener más de 15 caracteres")
        String celular,

        @Size(max = 200, message = "La referencia no puede tener más de 200 caracteres")
        String referencia,

        @Size(max = 50, message = "La zona no puede tener más de 50 caracteres")
        String zona,

        @NotNull(message = "El plan es obligatorio")
        Integer planId,

        @NotNull(message = "El día de pago es obligatorio")
        @Min(value = 1, message = "El día de pago debe estar entre 1 y 28")
        @Max(value = 28, message = "El día de pago debe estar entre 1 y 28")
        Integer diaPago,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate fechaInicio,

        @Size(max = 15, message = "La IP no puede tener más de 15 caracteres")
        String ip,

        Integer redId,

        @Size(max = 40, message = "El nombre de la cola no puede tener más de 40 caracteres")
        String nombreCola
) implements DatosCliente {
}
