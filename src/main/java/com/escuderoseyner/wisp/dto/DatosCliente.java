package com.escuderoseyner.wisp.dto;

import java.time.LocalDate;

// Los datos de una persona que comparten "crear/editar cliente" y "reasignar código".
// Así ClienteService valida y guarda ambos casos con el mismo código.
public interface DatosCliente {

    String nombres();

    String celular();

    String referencia();

    String zona();

    Integer planId();

    Integer diaPago();

    LocalDate fechaInicio();

    String ip();

    // MikroTik al que pertenece (null = ninguno) y nombre de su cola (vacío = el código)
    Integer redId();

    String nombreCola();
}
