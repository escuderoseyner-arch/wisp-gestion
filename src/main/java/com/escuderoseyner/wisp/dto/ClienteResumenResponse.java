package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoCliente;

// Lo que muestra cada tarjeta del listado
public record ClienteResumenResponse(
        Integer id,
        String codigo,
        String nombres,
        String zona,
        String planNombre,
        Integer diaPago,
        EstadoCliente estado
) {
}
