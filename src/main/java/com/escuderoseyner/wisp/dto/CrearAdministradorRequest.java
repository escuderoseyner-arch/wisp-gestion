package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// La contraseña no va aquí: el sistema genera una temporal
public record CrearAdministradorRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
        String nombreMostrar,

        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = "^\\s*[A-Za-z0-9._-]{3,50}\\s*$",
                message = "El usuario debe tener de 3 a 50 caracteres: letras sin tildes, números, punto, guion o guion bajo")
        String username
) {
}
