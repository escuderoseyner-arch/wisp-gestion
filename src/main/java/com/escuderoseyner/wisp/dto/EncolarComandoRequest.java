package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// "confirmo" debe venir en true: el admin aceptó que el comando se ejecuta en el router REAL.
// Así un clic accidental (o una llamada sin pasar por la confirmación) no encola nada.
public record EncolarComandoRequest(
        @NotBlank(message = "Escribe un comando")
        @Size(max = 2000, message = "El comando no puede tener más de 2000 caracteres")
        String comando,

        @AssertTrue(message = "Debes confirmar que el comando se ejecutará en el MikroTik real")
        boolean confirmo
) {
}
