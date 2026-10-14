package com.escuderoseyner.wisp.dto;

import java.math.BigDecimal;
import java.util.List;

// Página pública de aviso por Wi-Fi (/aviso/<token>). Solo los datos del cliente de ESA cola:
// nombre, lo que debe y cómo pagar. Nada de IP, celular, historial ni datos de otros clientes.
public record AvisoResponse(
        Etapa etapa,
        String empresa,
        String logoUrl,
        String nombre,               // null si la cola ya no tiene un cliente vigente (página genérica)
        String codigo,               // solo para el mensaje de WhatsApp al soporte
        String moneda,
        List<String> meses,          // meses impagos cuyo día de pago ya llegó ("2026-10"), del más antiguo
        BigDecimal monto,            // total de esos meses
        String texto,                // plantilla de Configuración con {nombre}, {monto} y {mes}
        DatosParaPagar datosParaPagar,
        Soporte soporte,
        boolean puedeEntendido       // false si está suspendido (el botón no le devuelve el internet)
) {

    public enum Etapa { SUAVE, VENCIDO, SUSPENDIDO, AL_DIA }

    public record DatosParaPagar(String yapeNumero, String yapeTitular, String cuentaBancaria) {
    }

    public record Soporte(String codigoPais, String whatsapp) {
    }
}
