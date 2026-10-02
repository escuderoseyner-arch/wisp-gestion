package com.escuderoseyner.wisp.service;

// Login fallido. El mensaje es SIEMPRE el mismo para no revelar si el usuario existe,
// si la contraseña estaba mal o si la cuenta está bloqueada.
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos. Si fallaste varias veces, espera 15 minutos e inténtalo de nuevo.");
    }
}
