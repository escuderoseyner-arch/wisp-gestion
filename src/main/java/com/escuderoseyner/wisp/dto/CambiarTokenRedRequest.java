package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Size;

// Vacío = la web genera un token nuevo; con valor = se usa ese.
public record CambiarTokenRedRequest(
        @Size(max = 128, message = "El token no puede tener más de 128 caracteres")
        String token
) {
}
