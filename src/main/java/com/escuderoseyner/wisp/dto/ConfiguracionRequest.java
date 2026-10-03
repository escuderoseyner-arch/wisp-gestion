package com.escuderoseyner.wisp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Datos de la empresa que edita el admin. Los formatos (RUC, números, URL, variables
// de la plantilla) se revisan en ConfiguracionService después de limpiar espacios.
public record ConfiguracionRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio")
        @Size(max = 100, message = "El nombre de la empresa no puede tener más de 100 caracteres")
        String nombreEmpresa,

        @Size(max = 11, message = "El RUC no puede tener más de 11 caracteres")
        String ruc,

        @Size(max = 255, message = "La URL del logo no puede tener más de 255 caracteres")
        String logoUrl,

        @NotBlank(message = "El WhatsApp de soporte es obligatorio")
        @Size(max = 15, message = "El WhatsApp de soporte no puede tener más de 15 dígitos")
        String whatsappSoporte,

        @NotBlank(message = "El código de país es obligatorio")
        @Size(max = 4, message = "El código de país no puede tener más de 4 dígitos")
        String codigoPais,

        @Size(max = 15, message = "El número de Yape no puede tener más de 15 caracteres")
        String yapeNumero,

        @Size(max = 100, message = "El titular de Yape no puede tener más de 100 caracteres")
        String yapeTitular,

        @Size(max = 150, message = "La cuenta bancaria no puede tener más de 150 caracteres")
        String cuentaBancaria,

        @NotNull(message = "Los días de tolerancia son obligatorios")
        @Min(value = 0, message = "Los días de tolerancia deben estar entre 0 y 15")
        @Max(value = 15, message = "Los días de tolerancia deben estar entre 0 y 15")
        Integer diasTolerancia,

        @NotBlank(message = "La plantilla de recordatorio es obligatoria")
        @Size(max = 500, message = "La plantilla de recordatorio no puede tener más de 500 caracteres")
        String plantillaRecordatorio
) {
}
