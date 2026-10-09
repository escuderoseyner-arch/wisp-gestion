package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.ModoRed;

// Estado de la cola de un cliente en el MikroTik. "cola" es null si no está en ninguna red
// o si la web todavía no calcula su cola.
public record ColaClienteResponse(
        boolean corteManual,
        ModoRed modoRed,              // null si no está en ninguna red
        ColaResponse cola
) {
}
