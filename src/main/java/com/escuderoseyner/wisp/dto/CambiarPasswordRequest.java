package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 72, message = "La contraseña actual no puede tener más de 72 caracteres")
        String passwordActual,

        // BCrypt solo usa los primeros 72 bytes, por eso el máximo
        @NotBlank(message = "La contraseña nueva es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña nueva debe tener entre 8 y 72 caracteres")
        String passwordNueva
) {
}
