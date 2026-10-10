package com.escuderoseyner.wisp.model;

// Los mismos valores que el ENUM de la columna usuarios.rol en MySQL.
//  - ADMIN: ve y gestiona todo.
//  - OPERADOR: ayuda con los cobros (panel, clientes, pagos, caja). No edita ni borra nada.
//  - CLIENTE: solo ve su propia información en el portal.
public enum Rol {
    ADMIN,
    OPERADOR,
    CLIENTE;

    // Cuentas del personal (pestaña Admins): no están ligadas a un cliente
    public boolean esPersonal() {
        return this == ADMIN || this == OPERADOR;
    }
}
