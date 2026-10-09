package com.escuderoseyner.wisp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoRouterOsTest {

    @Test
    @DisplayName("max-limit en formato RouterOS 7: subida/bajada")
    void maxLimit() {
        assertThat(FormatoRouterOs.maxLimit(5, 15)).isEqualTo("5M/15M");
    }

    @Test
    @DisplayName("La misma velocidad escrita con sufijos o en bps es igual")
    void velocidades() {
        assertThat(FormatoRouterOs.mismaVelocidad("5M/15M", "5000000/15000000")).isTrue();
        assertThat(FormatoRouterOs.mismaVelocidad("5M/15M", "15M/5M")).isFalse();
        assertThat(FormatoRouterOs.mismaVelocidad("5M/15M", "basura")).isFalse();
        assertThat(FormatoRouterOs.parSubidaBajada("512k/1.5M")).containsExactly(512_000L, 1_500_000L);
    }

    @Test
    @DisplayName("El target se compara con o sin /32")
    void target() {
        assertThat(FormatoRouterOs.mismoTarget("192.168.1.11/32", "192.168.1.11")).isTrue();
        assertThat(FormatoRouterOs.mismoTarget("192.168.1.11/32", "192.168.1.11/32")).isTrue();
        assertThat(FormatoRouterOs.mismoTarget("192.168.1.11/32", "192.168.1.12/32")).isFalse();
    }

    @Test
    @DisplayName("El comentario no lleva tildes ni caracteres que rompan el formato")
    void comentario() {
        assertThat(FormatoRouterOs.comentario("María Ñañez")).isEqualTo("Maria Nanez");
        assertThat(FormatoRouterOs.comentario("Ana|\"$x\"\nB")).isEqualTo("Ana x B");
        assertThat(FormatoRouterOs.comentario("   ")).isEqualTo("-");
    }

    @Test
    @DisplayName("Los textos del router se limpian de | y saltos de línea")
    void textoRecibido() {
        assertThat(FormatoRouterOs.textoRecibido("a|b\nc", 100)).isEqualTo("a b c");
        assertThat(FormatoRouterOs.textoRecibido("abcdef", 3)).isEqualTo("abc");
    }
}
