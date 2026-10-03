package com.escuderoseyner.wisp.dto;

// Lo ÚNICO de la configuración que puede ver alguien sin iniciar sesión.
// Yape, cuenta bancaria, plantillas, etc. quedan fuera a propósito.
// whatsappSoporte + codigoPais: para el enlace "¿Olvidaste tu contraseña?" del login.
public record ConfiguracionPublicaResponse(String nombreEmpresa, String logoUrl, String whatsappSoporte,
                                           String codigoPais) {
}
