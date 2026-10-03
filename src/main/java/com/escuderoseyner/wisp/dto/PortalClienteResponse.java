package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Lo que ve un cliente en su portal. Solo SUS datos: el servidor toma el cliente del token.
// No incluye datos internos (quién registró cada pago, observaciones, IP, etc.).
public record PortalClienteResponse(
        String codigo,
        String nombres,
        EstadoCliente estado,
        PlanDelPortal plan,
        String moneda,
        MesPorPagar proximoVencimiento,   // el próximo mes que aún no vence, o null
        List<MesPorPagar> mesesQueDebe,   // meses VENCIDOS, del más antiguo al más reciente
        BigDecimal deuda,
        List<PagoDelPortal> historial,    // el más reciente primero
        DatosParaPagar datosParaPagar,
        Soporte soporte
) {

    public record PlanDelPortal(String nombre, Integer bajadaMbps, Integer subidaMbps, BigDecimal precio) {
    }

    public record MesPorPagar(String periodo, LocalDate vence, BigDecimal monto) {
    }

    public record PagoDelPortal(String periodo, BigDecimal monto, LocalDate fechaPago, MetodoPago metodo) {
    }

    public record DatosParaPagar(String yapeNumero, String yapeTitular, String cuentaBancaria) {
    }

    public record Soporte(String codigoPais, String whatsapp) {
    }
}
