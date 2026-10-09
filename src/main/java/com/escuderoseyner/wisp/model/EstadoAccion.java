package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de acciones_cola.estado en MySQL
public enum EstadoAccion {
    PENDIENTE,    // esperando que el MikroTik consulte
    ENVIADA,      // el MikroTik ya la recibió y falta su confirmación (se reenvía: aplicarla dos veces da lo mismo)
    APLICADA,
    ERROR,
    REEMPLAZADA   // llegó otra acción más nueva para la misma cola antes de aplicarse
}
