package com.escuderoseyner.wisp.dto;

// Script "puente" listo para pegar en la terminal del MikroTik (contiene el token)
public record PuenteRedResponse(
        String comando,
        String url,           // URL que consultará el router
        boolean https         // false = el router no podrá verificar el certificado (solo sirve para pruebas)
) {
}
