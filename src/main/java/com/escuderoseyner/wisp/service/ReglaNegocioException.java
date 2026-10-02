package com.escuderoseyner.wisp.service;

// Error de una regla del sistema (ej: "la contraseña actual no es correcta").
// El mensaje se muestra tal cual al usuario, así que debe ser claro y en español.
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
