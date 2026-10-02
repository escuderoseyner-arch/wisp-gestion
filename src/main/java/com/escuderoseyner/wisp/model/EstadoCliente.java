package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de la columna clientes.estado en MySQL.
// Si se agrega uno aquí, también hay que agregarlo en la base de datos.
public enum EstadoCliente {
    ACTIVO,
    SUSPENDIDO,
    RETIRADO
}
