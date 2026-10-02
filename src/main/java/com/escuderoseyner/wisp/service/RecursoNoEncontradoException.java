package com.escuderoseyner.wisp.service;

// Se pidió algo que no existe (ej: un plan con un id inexistente). Se responde con 404.
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
