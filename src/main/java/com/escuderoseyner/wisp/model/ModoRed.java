package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de la columna redes.modo en MySQL.
// SOLO_LECTURA: el MikroTik solo reporta; la web nunca le manda cambios.
// CONTROL: la web también le manda las acciones sobre las colas de los clientes.
public enum ModoRed {
    SOLO_LECTURA,
    CONTROL
}
