package com.escuderoseyner.wisp.dto;

// ÚNICA vez que la contraseña temporal sale del servidor: en la respuesta de
// "crear cuenta" o "restablecer contraseña". En la base de datos solo queda su hash BCrypt.
public record CuentaTemporalResponse(String username, String passwordTemporal) {
}
