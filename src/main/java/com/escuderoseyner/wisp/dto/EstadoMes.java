package com.escuderoseyner.wisp.dto;

// Estado de un mes para un cliente. No se guarda en la base de datos: se calcula.
public enum EstadoMes {
    PAGADO,     // hay un pago registrado para ese mes
    PENDIENTE,  // sin pago, pero aún no pasa el día de pago + los días de tolerancia
    VENCIDO,    // sin pago y ya pasó ese plazo
    NO_APLICA   // antes de su fecha de inicio o después de su retiro
}
