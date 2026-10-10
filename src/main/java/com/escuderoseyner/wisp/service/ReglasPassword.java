package com.escuderoseyner.wisp.service;

import java.nio.charset.StandardCharsets;

// Reglas para las contraseñas que escribe el admin (el largo de 8 a 72 lo valida @Size en el DTO)
final class ReglasPassword {

    private static final int MAX_BYTES_BCRYPT = 72;

    private ReglasPassword() {
    }

    // BCrypt no acepta más de 72 bytes (una "ñ" o un emoji ocupan más de 1 byte)
    static void validar(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
            throw new ReglaNegocioException("La contraseña es demasiado larga: usa menos tildes, ñ o emojis.");
        }
    }
}
