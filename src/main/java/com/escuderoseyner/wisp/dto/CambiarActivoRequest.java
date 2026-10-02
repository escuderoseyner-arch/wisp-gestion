package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotNull;

// {"activo": true} para activar, {"activo": false} para desactivar
public record CambiarActivoRequest(
        @NotNull(message = "Indica si el plan debe quedar activo o no")
        Boolean activo
) {
}
