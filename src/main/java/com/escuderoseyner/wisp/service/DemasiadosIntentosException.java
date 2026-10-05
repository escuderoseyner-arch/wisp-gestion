package com.escuderoseyner.wisp.service;

// Demasiados intentos fallidos desde una misma conexión. Se responde con 429.
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException() {
        super("Hubo demasiados intentos fallidos desde tu conexión. Espera 15 minutos e inténtalo de nuevo.");
    }
}
