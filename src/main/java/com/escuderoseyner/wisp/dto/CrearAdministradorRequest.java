package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cuenta del personal (ADMIN u OPERADOR). La contraseña inicial la escribe el admin;
// la persona debe cambiarla en su primer ingreso.
public record CrearAdministradorRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
        String nombreMostrar,

        @NotBlank(message = "El usuario es obligatorio")
        @Pattern(regexp = "^\s*[A-Za-z0-9._-]{3,50}\s*$",
                message = "El usuario debe tener de 3 a 50 caracteres: letras sin tildes, números, punto, guion o guion bajo")
        String username,

        @NotNull(message = "Elige el rol: administrador u operador")
        Rol rol,

        @NotBlank(message = "La contraseña inicial es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña inicial debe tener entre 8 y 72 caracteres")
        String password
) {
}
