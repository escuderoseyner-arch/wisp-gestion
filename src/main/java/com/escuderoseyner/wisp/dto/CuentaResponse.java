package com.escuderoseyner.wisp.dto;

// Respuesta de "crear cuenta" o "restablecer contraseña". La contraseña nunca sale del servidor:
// la escribió el admin y en la base de datos solo queda su hash BCrypt.
public record CuentaResponse(String username) {
}
