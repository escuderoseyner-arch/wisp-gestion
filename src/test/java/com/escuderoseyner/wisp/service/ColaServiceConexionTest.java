package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ColaClienteResponse.Conexion;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.Red;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ColaServiceConexionTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 10, 8, 12, 0);

    private Red red(int intervalo) {
        Red red = new Red();
        red.setIntervaloSegundos(intervalo);
        return red;
    }

    private Cola cola(Boolean ping, LocalDateTime reportado) {
        Cola cola = new Cola();
        cola.setPingOk(ping);
        cola.setReportadoEn(reportado);
        return cola;
    }

    @Test
    @DisplayName("Reporte reciente: conectado si responde al ping, sin conexión si no")
    void reporteReciente() {
        assertThat(ColaService.conexion(cola(true, ahora.minusMinutes(1)), red(60), ahora)).isEqualTo(Conexion.CONECTADO);
        assertThat(ColaService.conexion(cola(false, ahora.minusMinutes(1)), red(60), ahora)).isEqualTo(Conexion.SIN_CONEXION);
    }

    @Test
    @DisplayName("Sin reporte o con reporte viejo (más de 5 min o 3 intervalos): sin datos")
    void reporteViejo() {
        assertThat(ColaService.conexion(cola(true, null), red(60), ahora)).isEqualTo(Conexion.SIN_DATOS);
        assertThat(ColaService.conexion(cola(true, ahora.minusMinutes(6)), red(60), ahora)).isEqualTo(Conexion.SIN_DATOS);
        // Con intervalo de 10 min, el margen es de 30 min
        assertThat(ColaService.conexion(cola(true, ahora.minusMinutes(20)), red(600), ahora)).isEqualTo(Conexion.CONECTADO);
    }
}
