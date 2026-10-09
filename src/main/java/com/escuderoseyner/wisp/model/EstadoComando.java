package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de comandos_terminal.estado en MySQL
public enum EstadoComando {
    PENDIENTE,   // esperando la próxima consulta del MikroTik
    ENVIADO,     // el MikroTik ya lo recibió (nunca se vuelve a enviar)
    EJECUTADO,
    ERROR,       // RouterOS devolvió un error, o el MikroTik nunca devolvió la salida
    EXPIRADO,    // el MikroTik no lo recogió a tiempo: ya no se ejecutará
    CANCELADO    // un admin lo canceló antes de que se enviara
}
