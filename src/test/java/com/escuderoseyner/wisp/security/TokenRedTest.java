package com.escuderoseyner.wisp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenRedTest {

    @Test
    @DisplayName("El hash es SHA-256 en hexadecimal (valor conocido)")
    void hashConocido() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenRed.hash("abc"));
    }

    @Test
    @DisplayName("Los tokens generados son distintos y tienen un formato válido")
    void generados() {
        TokenRed tokenRed = new TokenRed();
        String a = tokenRed.generar();
        String b = tokenRed.generar();
        assertNotEquals(a, b);
        assertEquals(43, a.length());
        assertTrue(TokenRed.formatoValido(a));
    }

    @Test
    @DisplayName("Rechaza tokens cortos o con caracteres que rompen RouterOS")
    void formatoInvalido() {
        assertFalse(TokenRed.formatoValido("corto"));
        assertFalse(TokenRed.formatoValido("token-con-comillas\"aaaaaaaaaa"));
        assertFalse(TokenRed.formatoValido("token$variable-aaaaaaaaaaaaaaa"));
        assertFalse(TokenRed.formatoValido("token,con,comas-aaaaaaaaaaaaaa"));
        assertFalse(TokenRed.formatoValido("token con espacios aaaaaaaaaaaa"));
        assertTrue(TokenRed.formatoValido("Clave.Propia_2026~ok+/="));
    }
}
