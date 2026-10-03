package com.escuderoseyner.wisp.dto;

// Configuración completa de la empresa. Solo la ve el ADMIN.
public record ConfiguracionResponse(
        String nombreEmpresa,
        String ruc,
        String logoUrl,
        String whatsappSoporte,
        String codigoPais,
        String yapeNumero,
        String yapeTitular,
        String cuentaBancaria,
        Integer diasTolerancia,
        String plantillaRecordatorio,
        String moneda
) {
}
