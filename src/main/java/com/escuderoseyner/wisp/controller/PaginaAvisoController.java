package com.escuderoseyner.wisp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// El proxy del MikroTik redirige a /aviso/<token> (o a /aviso, la regla final sin token).
// Siempre se entrega la misma página: aviso.js lee el token de la URL y pide los datos a /api/public/aviso.
@Controller
public class PaginaAvisoController {

    @GetMapping({"/aviso", "/aviso/", "/aviso/{token}"})
    public String pagina() {
        return "forward:/aviso.html";
    }
}
