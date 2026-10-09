package com.escuderoseyner.wisp.dto;

// Script de RouterOS listo para pegar en la terminal del MikroTik
public record ScriptRedResponse(
        String script,
        String url,           // URL que consultará el router
        boolean https         // false = el router no podrá verificar el certificado (solo sirve para pruebas)
) {
}
