package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.EstadoMes;
import com.escuderoseyner.wisp.model.Cliente;

import java.time.LocalDate;
import java.time.YearMonth;

// Regla del estado de un mes para un cliente. Sin base de datos ni Spring:
// recibe todo lo que necesita, así se puede probar sola (ver CalculadoraEstadoMesTest).
public final class CalculadoraEstadoMes {

    private CalculadoraEstadoMes() {
    }

    // Último día para pagar sin que el mes quede VENCIDO: día de pago + días de tolerancia.
    // El día de pago va de 1 a 28, así que existe en todos los meses.
    public static LocalDate vencimiento(Cliente cliente, YearMonth mes, int diasTolerancia) {
        return mes.atDay(cliente.getDiaPago()).plusDays(diasTolerancia);
    }

    // ¿El cliente debía pagar ese mes? Desde el mes de su inicio hasta el mes de su retiro (incluidos).
    public static boolean aplica(Cliente cliente, YearMonth mes) {
        boolean antesDeInicio = mes.isBefore(YearMonth.from(cliente.getFechaInicio()));
        boolean despuesDeRetiro = cliente.getFechaRetiro() != null
                && mes.isAfter(YearMonth.from(cliente.getFechaRetiro()));
        return !antesDeInicio && !despuesDeRetiro;
    }

    // PAGADO si hay pago (aunque luego se haya cambiado su fecha de inicio);
    // NO_APLICA fuera de su periodo; si no, PENDIENTE hasta el vencimiento (incluido) y VENCIDO después.
    public static EstadoMes estado(Cliente cliente, YearMonth mes, boolean pagado, int diasTolerancia, LocalDate hoy) {
        if (pagado) {
            return EstadoMes.PAGADO;
        }
        if (!aplica(cliente, mes)) {
            return EstadoMes.NO_APLICA;
        }
        return hoy.isAfter(vencimiento(cliente, mes, diasTolerancia)) ? EstadoMes.VENCIDO : EstadoMes.PENDIENTE;
    }
}
