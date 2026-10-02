package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.Rol;

// Datos básicos del usuario logueado. Nunca incluye el hash de la contraseña.
public record UsuarioActualResponse(
        Integer id,
        String username,
        String nombreMostrar,
        String email,
        Rol rol,
        Integer clienteId,           // null si es ADMIN
        boolean debeCambiarPassword
) {
}
