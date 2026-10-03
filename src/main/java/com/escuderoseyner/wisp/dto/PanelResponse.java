package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Panel del admin: resumen del mes actual, atrasados, próximos a vencer y pagos de la semana
public record PanelResponse(
        String moneda,
        String periodo,              // mes actual, "2026-10"
        BigDecimal esperado,         // precio del plan de los clientes vigentes que deben pagar este mes
        BigDecimal cobrado,          // pagos registrados para este mes
        BigDecimal pendiente,        // lo que falta cobrar de este mes
        int clientesActivos,
        int clientesSuspendidos,
        List<Atrasado> atrasados,          // del más atrasado al menos
        List<ProximoAVencer> proximos,     // los que vencen en los próximos días, el más cercano primero
        int diasProximos,
        LocalDate semanaDesde,       // lunes
        LocalDate semanaHasta,       // domingo
        List<PagoDeLaSemana> pagosSemana,
        BigDecimal totalSemana,
        String plantillaRecordatorio,  // para los enlaces "Recordar" por WhatsApp
        String codigoPais
) {

    public record Atrasado(
            Integer clienteId,
            String codigo,
            String nombres,
            String zona,
            String celular,
            List<String> periodos,   // meses VENCIDOS, del más antiguo al más reciente
            BigDecimal deuda,
            long diasAtraso          // días desde el vencimiento del mes más antiguo
    ) {
    }

    public record ProximoAVencer(
            Integer clienteId,
            String codigo,
            String nombres,
            String zona,
            String celular,
            String periodo,
            LocalDate vence,
            long diasRestantes,      // 0 = vence hoy
            BigDecimal monto
    ) {
    }

    public record PagoDeLaSemana(
            Integer pagoId,
            Integer clienteId,
            String codigo,
            String nombres,
            String periodo,
            BigDecimal monto,
            LocalDate fechaPago,
            MetodoPago metodo
    ) {
    }
}
