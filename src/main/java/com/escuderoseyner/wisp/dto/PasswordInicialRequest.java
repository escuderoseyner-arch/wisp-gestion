package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Contraseña que escribe el admin al crear la cuenta de un cliente o al restablecer cualquier cuenta.
// La persona debe cambiarla en su próximo ingreso.
public record PasswordInicialRequest(
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password
) {
}
