package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotNull;

// Activar o desactivar el aviso de pago por Wi-Fi de una red
public record CambiarAvisoRequest(
        @NotNull(message = "Indica si el aviso queda activado o desactivado")
        Boolean activo
) {
}
