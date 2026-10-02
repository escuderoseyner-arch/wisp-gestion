package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.Rol;

// Lo que recibe el frontend al iniciar sesión o cambiar la contraseña
public record LoginResponse(
        String token,
        String tipo,                 // siempre "Bearer": se envía como "Authorization: Bearer <token>"
        long expiraEnSegundos,
        Rol rol,
        boolean debeCambiarPassword
) {
}
