package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.ModoRed;

import java.time.LocalDate;

// Estado de la cola de un cliente en el MikroTik. "cola" es null si no está en ninguna red
// o si la web todavía no calcula su cola.
public record ColaClienteResponse(
        boolean corteManual,
        ModoRed modoRed,                      // null si no está en ninguna red
        String nombreRed,
        Conexion conexion,
        ColaResponse cola,
        ColaResponse.UltimaAccion ultimoCorte, // último Cortar/Reconectar con su estado (null si nunca)
        ConsumoMes consumoMes
) {

    // CONECTADO: responde al ping. SIN_CONEXION: no responde. SIN_DATOS: el MikroTik no reporta hace rato.
    public enum Conexion {
        CONECTADO, SIN_CONEXION, SIN_DATOS
    }

    public record ConsumoMes(LocalDate periodo, long bytesSubida, long bytesBajada) {
    }
}
