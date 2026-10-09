package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.MotivoAccion;

import java.time.LocalDateTime;
import java.util.List;

// Una cola: lo que quiere la web, lo último que reportó el MikroTik y sus diferencias
public record ColaResponse(
        Integer id,
        String nombre,
        ClienteDeCola cliente,
        Deseado deseado,
        Reportado reportado,          // null si el MikroTik aún no la reporta
        List<String> diferencias,
        UltimaAccion ultimaAccion     // null si nunca se le envió una acción
) {

    // vigente = false: el dueño se retiró o cambió de cola/red; la cola queda deshabilitada
    public record ClienteDeCola(Integer id, String codigo, String nombres, boolean vigente) {
    }

    public record Deseado(String target, String maxLimit, String parent, String tipoCola, String comentario,
                          boolean deshabilitada, LocalDateTime actualizadoEn) {
    }

    public record Reportado(boolean existe, String target, String maxLimit, String parent, String tipoCola,
                            String comentario, Boolean deshabilitada, Long rateSubidaBps, Long rateBajadaBps,
                            Boolean pingOk, LocalDateTime reportadoEn) {
    }

    public record UltimaAccion(Long id, MotivoAccion motivo, EstadoAccion estado, String error,
                               LocalDateTime creadoEn, LocalDateTime resueltaEn) {
    }
}
