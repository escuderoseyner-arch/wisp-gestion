package com.escuderoseyner.wisp.dto;

import java.time.LocalDateTime;

// "Entendido" en la página de aviso: hasta cuándo no lo vuelve a ver
public record EntendidoResponse(LocalDateTime hasta) {
}
