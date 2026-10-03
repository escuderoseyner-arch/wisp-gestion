package com.escuderoseyner.wisp.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

// Contraseñas temporales para las cuentas de clientes. El cliente la recibe por WhatsApp
// y debe cambiarla en su primer ingreso.
@Component
public class GeneradorPasswordTemporal {

    private static final int LONGITUD = 8;

    // Sin caracteres que se confunden al leerlos en un celular: O/o/0, I/l/1
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String NUMEROS = "23456789";
    private static final String TODOS = LETRAS + NUMEROS;

    // SecureRandom: generador criptográfico. Random normal sería predecible.
    private final SecureRandom random = new SecureRandom();

    public String generar() {
        while (true) {
            StringBuilder password = new StringBuilder(LONGITUD);
            for (int i = 0; i < LONGITUD; i++) {
                password.append(TODOS.charAt(random.nextInt(TODOS.length())));
            }
            // Que tenga al menos una letra y un número, para que se lea como contraseña
            String texto = password.toString();
            if (texto.chars().anyMatch(c -> NUMEROS.indexOf(c) >= 0)
                    && texto.chars().anyMatch(c -> LETRAS.indexOf(c) >= 0)) {
                return texto;
            }
        }
    }
}
