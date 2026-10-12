package com.escuderoseyner.wisp.dto;

// Una red en el selector "Todas las redes / ...". Sin datos del MikroTik (lo ve también el operador).
public record RedVisibleResponse(Integer id, String nombre, String prefijoCodigo) {
}
