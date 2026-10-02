package com.escuderoseyner.wisp.dto;

import java.util.List;

// Formato único para todos los errores de la API
public record ErrorResponse(String mensaje, List<String> detalles) {

    public static ErrorResponse de(String mensaje) {
        return new ErrorResponse(mensaje, List.of());
    }
}
