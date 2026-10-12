package com.escuderoseyner.wisp.dto;

// Script de RouterOS listo para pegar en la terminal del MikroTik
public record ScriptRedResponse(
        String script,
        String comandoCorto,  // 2 líneas: descarga el script con /tool fetch y lo importa con /import
        String url,           // URL que consultará el router
        boolean https         // false = el router no podrá verificar el certificado (solo sirve para pruebas)
) {
}
