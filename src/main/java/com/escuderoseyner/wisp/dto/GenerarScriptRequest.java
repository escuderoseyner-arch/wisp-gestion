package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// La web no guarda el token (solo su hash): para armar el script, el admin lo vuelve a escribir.
// Se comprueba que corresponda a la red antes de generar nada.
public record GenerarScriptRequest(
        @NotBlank(message = "Escribe el token de la red")
        @Size(max = 128, message = "El token no puede tener más de 128 caracteres")
        String token
) {
}
