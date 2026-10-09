package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de acciones_cola.motivo en MySQL
public enum MotivoAccion {
    CAMBIO,          // cambió el cliente, su plan o la red
    CORTE,           // corte manual desde la web
    RECONEXION,      // reconexión manual desde la web
    SINCRONIZACION   // "aplicar diferencias": el router no coincide con la web
}
