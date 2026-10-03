package com.escuderoseyner.wisp.service;

import java.util.List;

// Error de una regla del sistema (ej: "la contraseña actual no es correcta").
// El mensaje se muestra tal cual al usuario, así que debe ser claro y en español.
// "detalles" permite avisar varios problemas a la vez (ej: código repetido Y celular inválido).
public class ReglaNegocioException extends RuntimeException {

    private final List<String> detalles;

    public ReglaNegocioException(String mensaje) {
        this(mensaje, List.of());
    }

    public ReglaNegocioException(String mensaje, List<String> detalles) {
        super(mensaje);
        this.detalles = List.copyOf(detalles);
    }

    public List<String> getDetalles() {
        return detalles;
    }
}
