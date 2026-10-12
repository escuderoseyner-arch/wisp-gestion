package com.escuderoseyner.wisp.service;

// El usuario pidió algo de una red que no tiene asignada (ej: un operador de Shiaunto
// pidiendo un cliente de Cuypirmarca). Se responde con 403.
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException() {
        super("No tienes acceso a la información de esa red.");
    }
}
