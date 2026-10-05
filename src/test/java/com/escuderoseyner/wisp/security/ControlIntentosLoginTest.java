package com.escuderoseyner.wisp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ControlIntentosLoginTest {

    // Reloj que se puede adelantar a mano, para no esperar 15 minutos de verdad
    static class RelojDePrueba extends Clock {
        private Instant ahora = Instant.parse("2026-10-04T10:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private static final String IP_ATACANTE = "203.0.113.7";
    private static final String IP_DUENO = "198.51.100.20";

    private RelojDePrueba reloj;
    private ControlIntentosLogin control;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        control = new ControlIntentosLogin(reloj);
    }

    private void fallar(String usuario, String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            control.registrarFallo(usuario, ip);
        }
    }

    @Test
    @DisplayName("5 fallos bloquean la cuenta solo para esa IP: el dueño sigue entrando desde la suya")
    void bloqueoPorCuentaEIp() {
        fallar("admin", IP_ATACANTE, 4);
        assertThat(control.cuentaBloqueada("admin", IP_ATACANTE)).isFalse();

        control.registrarFallo("admin", IP_ATACANTE);
        assertThat(control.cuentaBloqueada("admin", IP_ATACANTE)).isTrue();
        assertThat(control.cuentaBloqueada("admin", IP_DUENO)).isFalse(); // no se puede bloquear a otro
    }

    @Test
    @DisplayName("El usuario no distingue mayúsculas: 'Admin' y 'admin' son la misma cuenta")
    void mayusculas() {
        fallar("Admin", IP_ATACANTE, 3);
        fallar("ADMIN", IP_ATACANTE, 2);
        assertThat(control.cuentaBloqueada("admin", IP_ATACANTE)).isTrue();
    }

    @Test
    @DisplayName("Entrar bien borra los fallos de esa cuenta desde esa IP")
    void exitoReinicia() {
        fallar("admin", IP_DUENO, 4);
        control.registrarExito("admin", IP_DUENO);
        fallar("admin", IP_DUENO, 4);
        assertThat(control.cuentaBloqueada("admin", IP_DUENO)).isFalse();
    }

    @Test
    @DisplayName("20 fallos desde una IP (con distintos usuarios) bloquean esa IP, no las demás")
    void limitePorIp() {
        for (int i = 0; i < 19; i++) {
            control.registrarFallo("usuario" + i, IP_ATACANTE);
        }
        assertThat(control.ipBloqueada(IP_ATACANTE)).isFalse();

        control.registrarFallo("otro", IP_ATACANTE);
        assertThat(control.ipBloqueada(IP_ATACANTE)).isTrue();
        assertThat(control.ipBloqueada(IP_DUENO)).isFalse();
    }

    @Test
    @DisplayName("Pasados 15 minutos desde el primer fallo, todo se desbloquea solo")
    void ventanaDe15Minutos() {
        fallar("admin", IP_ATACANTE, 20);
        reloj.avanzar(Duration.ofMinutes(14));
        assertThat(control.ipBloqueada(IP_ATACANTE)).isTrue();
        assertThat(control.cuentaBloqueada("admin", IP_ATACANTE)).isTrue();

        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(control.ipBloqueada(IP_ATACANTE)).isFalse();
        assertThat(control.cuentaBloqueada("admin", IP_ATACANTE)).isFalse();
    }

    @Test
    @DisplayName("Restablecer la contraseña desbloquea la cuenta en todas las IP")
    void desbloquearCuenta() {
        fallar("900000001", IP_ATACANTE, 5);
        fallar("900000001", IP_DUENO, 5);
        control.desbloquearCuenta("900000001");
        assertThat(control.cuentaBloqueada("900000001", IP_ATACANTE)).isFalse();
        assertThat(control.cuentaBloqueada("900000001", IP_DUENO)).isFalse();
    }
}
