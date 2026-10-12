package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.Rol;

import java.time.LocalDateTime;
import java.util.List;

// Una cuenta del personal (ADMIN u OPERADOR) en la lista. Nunca incluye la contraseña ni su hash.
public record AdministradorResponse(
        Integer id,
        String username,
        String nombreMostrar,
        Rol rol,
        boolean activo,
        boolean debeCambiarPassword,
        LocalDateTime ultimoAcceso,
        boolean esUstedMismo,         // el admin que está viendo la lista
        List<RedVisibleResponse> redes // OPERADOR: sus redes asignadas (ADMIN: vacío, ve todas)
) {
}
