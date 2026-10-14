package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Cliente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AvisoWifiTest {

    private static final int TOLERANCIA = 3;

    // Cliente desde agosto de 2026, paga el día 10
    private Cliente cliente() {
        Cliente c = new Cliente();
        c.setId(1);
        c.setDiaPago(10);
        c.setFechaInicio(LocalDate.of(2026, 8, 1));
        return c;
    }

    @Test
    @DisplayName("Sin aviso antes del día de pago; suave desde ese día; vencido después de la tolerancia")
    void etapas() {
        Set<YearMonth> pagados = Set.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        Cliente c = cliente();

        assertThat(AvisoWifi.etapa(c, pagados, TOLERANCIA, LocalDate.of(2026, 10, 9))).isEqualTo(AvisoWifi.Etapa.NINGUNA);
        assertThat(AvisoWifi.etapa(c, pagados, TOLERANCIA, LocalDate.of(2026, 10, 10))).isEqualTo(AvisoWifi.Etapa.SUAVE);
        assertThat(AvisoWifi.etapa(c, pagados, TOLERANCIA, LocalDate.of(2026, 10, 13))).isEqualTo(AvisoWifi.Etapa.SUAVE);
        assertThat(AvisoWifi.etapa(c, pagados, TOLERANCIA, LocalDate.of(2026, 10, 14))).isEqualTo(AvisoWifi.Etapa.VENCIDO);
    }

    @Test
    @DisplayName("Meses en aviso: todos los impagos cuyo día de pago ya llegó; al pagar, sale")
    void meses() {
        Cliente c = cliente();
        LocalDate hoy = LocalDate.of(2026, 10, 12);

        assertThat(AvisoWifi.mesesEnAviso(c, Set.of(), hoy))
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(AvisoWifi.etapa(c, Set.of(), TOLERANCIA, hoy)).isEqualTo(AvisoWifi.Etapa.VENCIDO);
        Set<YearMonth> todos = Set.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10));
        assertThat(AvisoWifi.mesesEnAviso(c, todos, hoy)).isEmpty();
    }

    @Test
    @DisplayName("Las IP .1 a .10 (MikroTik e infraestructura) nunca reciben aviso")
    void ipInfraestructura() {
        assertThat(AvisoWifi.ipPermitida("192.168.10.1")).isFalse();
        assertThat(AvisoWifi.ipPermitida("192.168.10.10")).isFalse();
        assertThat(AvisoWifi.ipPermitida("192.168.10.10/32")).isFalse();
        assertThat(AvisoWifi.ipPermitida("192.168.10.11")).isTrue();
        assertThat(AvisoWifi.ipPermitida("192.168.10.11/32")).isTrue();
        assertThat(AvisoWifi.ipPermitida(null)).isFalse();
        assertThat(AvisoWifi.ipPermitida("no-es-ip")).isFalse();
    }

    @Test
    @DisplayName("\"Entendido\" dura hasta mañana a las 8:00, aunque se toque cerca de medianoche")
    void entendido() {
        assertThat(AvisoWifi.pospuestoHasta(LocalDateTime.of(2026, 10, 13, 23, 50)))
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 8, 0));
        assertThat(AvisoWifi.pospuestoHasta(LocalDateTime.of(2026, 10, 13, 7, 0)))
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 8, 0));

        Cliente c = cliente();
        c.setAvisoPospuestoHasta(LocalDateTime.of(2026, 10, 14, 8, 0));
        assertThat(AvisoWifi.pospuesto(c, LocalDateTime.of(2026, 10, 14, 7, 59))).isTrue();
        assertThat(AvisoWifi.pospuesto(c, LocalDateTime.of(2026, 10, 14, 8, 0))).isFalse();
    }
}
