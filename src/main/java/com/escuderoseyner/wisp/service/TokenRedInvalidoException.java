package com.escuderoseyner.wisp.service;

// El MikroTik no mandó un token válido (o su IP está frenada por demasiados intentos).
// No dice cuál de las dos cosas pasó, a propósito.
public class TokenRedInvalidoException extends RuntimeException {

    public TokenRedInvalidoException() {
        super("Token de red inválido.");
    }
}
