package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoCliente;

import java.math.BigDecimal;
import java.util.List;

// Situación de pagos de un cliente: lo que usa el detalle del cliente y el formulario de pago
public record EstadoCuentaResponse(
        ClienteDelPago cliente,
        String moneda,
        List<MesResponse> meses,          // desde su inicio hasta el mes actual (o su retiro), el más reciente primero
        List<MesResponse> mesesPorPagar,  // impagos que se pueden pagar (incluye algunos adelantados), el más antiguo primero
        List<PagoResponse> historial,     // todos sus pagos, el más reciente primero
        String periodoSugerido,           // el mes impago más antiguo, o null
        BigDecimal montoSugerido,         // precio de su plan
        int mesesVencidos,
        BigDecimal deuda                  // meses vencidos x precio actual de su plan
) {

    public record ClienteDelPago(Integer id, String codigo, String nombres, String planNombre, EstadoCliente estado) {
    }
}
