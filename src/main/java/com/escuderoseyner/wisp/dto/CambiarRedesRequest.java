package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// Redes asignadas a un OPERADOR. Lista vacía = ninguna (no ve ningún cliente).
public record CambiarRedesRequest(
        @NotNull(message = "Indica las redes (puede ser una lista vacía)")
        List<Integer> redIds
) {
}
