package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoComando;

import java.time.LocalDateTime;

public record ComandoTerminalResponse(
        Long id,
        String comando,
        EstadoComando estado,
        String salida,
        boolean salidaTruncada,
        String enviadoPor,           // nombre del admin
        LocalDateTime creadoEn,
        LocalDateTime expiraEn,
        LocalDateTime enviadoEn,
        LocalDateTime finalizadoEn
) {
}
