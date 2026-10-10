package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.Rol;

import java.time.LocalDateTime;

// Una cuenta del personal (ADMIN u OPERADOR) en la lista. Nunca incluye la contraseña ni su hash.
public record AdministradorResponse(
        Integer id,
        String username,
        String nombreMostrar,
        Rol rol,
        boolean activo,
        boolean debeCambiarPassword,
        LocalDateTime ultimoAcceso,
        boolean esUstedMismo          // el admin que está viendo la lista
) {
}
