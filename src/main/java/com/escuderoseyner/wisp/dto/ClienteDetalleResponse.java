package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoCliente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClienteDetalleResponse(
        Integer id,
        String codigo,
        String nombres,
        String celular,
        String referencia,
        String zona,
        PlanDelCliente plan,
        Integer diaPago,
        LocalDate fechaInicio,
        LocalDate fechaRetiro,
        String ip,
        EstadoCliente estado,
        CuentaDelCliente cuenta,     // null si no tiene cuenta en el portal
        RedDelCliente red,           // null si no está en ninguna red (MikroTik)
        String nombreCola,
        boolean corteManual
) {

    public record RedDelCliente(Integer id, String nombre) {
    }

    public record PlanDelCliente(Integer id, String nombre, BigDecimal precio, boolean activo) {
    }

    // Nunca incluye la contraseña ni su hash
    public record CuentaDelCliente(String username, boolean activa, boolean debeCambiarPassword) {
    }
}
