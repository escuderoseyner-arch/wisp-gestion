package com.escuderoseyner.wisp.dto;

import java.time.LocalDateTime;

// Un administrador en la lista. Nunca incluye la contraseña ni su hash.
public record AdministradorResponse(
        Integer id,
        String username,
        String nombreMostrar,
        boolean activo,
        boolean debeCambiarPassword,
        LocalDateTime ultimoAcceso,
        boolean esUstedMismo          // el admin que está viendo la lista
) {
}
