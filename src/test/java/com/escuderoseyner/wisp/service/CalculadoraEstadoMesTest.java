package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.EstadoMes;
import com.escuderoseyner.wisp.model.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

// Cliente de prueba: empezó el 10/08/2026, paga el día 5 y la tolerancia es 3 días,
// así que cada mes vence el día 8. "Hoy" es el 03/10/2026.
class CalculadoraEstadoMesTest {

    private static final int TOLERANCIA = 3;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 3);

    private Cliente cliente;

    @BeforeEach
    void crearCliente() {
        cliente = new Cliente();
        cliente.setFechaInicio(LocalDate.of(2026, 8, 10));
        cliente.setDiaPago(5);
    }

    private EstadoMes estado(YearMonth mes, boolean pagado, LocalDate hoy) {
        return CalculadoraEstadoMes.estado(cliente, mes, pagado, TOLERANCIA, hoy);
    }

    @Test
    @DisplayName("Un mes con pago registrado es PAGADO")
    void pagado() {
        assertThat(estado(YearMonth.of(2026, 9), true, HOY)).isEqualTo(EstadoMes.PAGADO);
    }

    @Test
    @DisplayName("Sin pago y ya pasó el día de pago + tolerancia: VENCIDO")
    void vencido() {
        assertThat(estado(YearMonth.of(2026, 8), false, HOY)).isEqualTo(EstadoMes.VENCIDO);
        assertThat(estado(YearMonth.of(2026, 9), false, HOY)).isEqualTo(EstadoMes.VENCIDO);
    }

    @Test
    @DisplayName("Sin pago y todavía no vence: PENDIENTE (incluye meses adelantados)")
    void pendiente() {
        assertThat(estado(YearMonth.of(2026, 10), false, HOY)).isEqualTo(EstadoMes.PENDIENTE);
        assertThat(estado(YearMonth.of(2026, 12), false, HOY)).isEqualTo(EstadoMes.PENDIENTE);
    }

    @Test
    @DisplayName("La tolerancia cuenta: el último día de tolerancia sigue PENDIENTE y al día siguiente es VENCIDO")
    void tolerancia() {
        YearMonth octubre = YearMonth.of(2026, 10);
        assertThat(CalculadoraEstadoMes.vencimiento(cliente, octubre, TOLERANCIA)).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(estado(octubre, false, LocalDate.of(2026, 10, 5))).isEqualTo(EstadoMes.PENDIENTE); // día de pago
        assertThat(estado(octubre, false, LocalDate.of(2026, 10, 8))).isEqualTo(EstadoMes.PENDIENTE); // último día
        assertThat(estado(octubre, false, LocalDate.of(2026, 10, 9))).isEqualTo(EstadoMes.VENCIDO);
    }

    @Test
    @DisplayName("Con tolerancia 0 vence justo después del día de pago")
    void sinTolerancia() {
        YearMonth octubre = YearMonth.of(2026, 10);
        assertThat(CalculadoraEstadoMes.estado(cliente, octubre, false, 0, LocalDate.of(2026, 10, 5)))
                .isEqualTo(EstadoMes.PENDIENTE);
        assertThat(CalculadoraEstadoMes.estado(cliente, octubre, false, 0, LocalDate.of(2026, 10, 6)))
                .isEqualTo(EstadoMes.VENCIDO);
    }

    @Test
    @DisplayName("El plazo puede pasar al mes siguiente: día 28 + 3 días vence el 31 o el 1/2/3")
    void vencimientoCruzaElMes() {
        cliente.setDiaPago(28);
        assertThat(CalculadoraEstadoMes.vencimiento(cliente, YearMonth.of(2026, 2), TOLERANCIA))
                .isEqualTo(LocalDate.of(2026, 3, 3));
    }

    @Test
    @DisplayName("Antes del mes de inicio: NO_APLICA; el mes de inicio sí aplica")
    void antesDelInicio() {
        assertThat(estado(YearMonth.of(2026, 7), false, HOY)).isEqualTo(EstadoMes.NO_APLICA);
        assertThat(CalculadoraEstadoMes.aplica(cliente, YearMonth.of(2026, 8))).isTrue();
    }

    @Test
    @DisplayName("Después del mes de retiro: NO_APLICA; el mes del retiro sí aplica")
    void despuesDelRetiro() {
        cliente.setFechaRetiro(LocalDate.of(2026, 9, 20));
        assertThat(estado(YearMonth.of(2026, 10), false, HOY)).isEqualTo(EstadoMes.NO_APLICA);
        assertThat(estado(YearMonth.of(2026, 9), false, HOY)).isEqualTo(EstadoMes.VENCIDO);
    }

    @Test
    @DisplayName("Un pago registrado se muestra PAGADO aunque el mes ya no aplique")
    void pagadoFueraDePeriodo() {
        assertThat(estado(YearMonth.of(2026, 7), true, HOY)).isEqualTo(EstadoMes.PAGADO);
    }
}
