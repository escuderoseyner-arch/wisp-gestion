package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Red;

import java.util.Set;

// Qué redes puede ver el usuario actual. El ADMIN ve todas (también los clientes sin red);
// el OPERADOR solo las que tiene asignadas, y nunca los clientes sin red.
public record Alcance(boolean todas, Set<Integer> redIds) {

    public static Alcance admin() {
        return new Alcance(true, Set.of());
    }

    public static Alcance operador(Set<Integer> redIds) {
        return new Alcance(false, Set.copyOf(redIds));
    }

    public boolean puedeVer(Integer redId) {
        return todas || (redId != null && redIds.contains(redId));
    }

    public boolean puedeVer(Cliente cliente) {
        return puedeVer(redDe(cliente));
    }

    // 403 si no puede verla
    public void verificar(Integer redId) {
        if (!puedeVer(redId)) {
            throw new AccesoDenegadoException();
        }
    }

    public void verificar(Cliente cliente) {
        verificar(redDe(cliente));
    }

    // Filtro de una pantalla. redPedida null = "Todas las redes" (las que puede ver).
    // Pedir una red que no tiene asignada da 403.
    public FiltroRed filtro(Integer redPedida) {
        if (redPedida == null) {
            return todas ? FiltroRed.TODAS : new FiltroRed(false, redIds);
        }
        verificar(redPedida);
        return new FiltroRed(false, Set.of(redPedida));
    }

    // getId() de un proxy LAZY no consulta la base de datos
    private static Integer redDe(Cliente cliente) {
        Red red = cliente.getRed();
        return red == null ? null : red.getId();
    }

    // Qué clientes entran en una consulta
    public record FiltroRed(boolean todas, Set<Integer> redIds) {

        public static final FiltroRed TODAS = new FiltroRed(true, Set.of());

        public boolean incluye(Cliente cliente) {
            return todas || (cliente.getRed() != null && redIds.contains(cliente.getRed().getId()));
        }
    }
}
