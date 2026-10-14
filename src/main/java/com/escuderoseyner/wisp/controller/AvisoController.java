package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.AvisoResponse;
import com.escuderoseyner.wisp.dto.EntendidoResponse;
import com.escuderoseyner.wisp.service.AvisoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Página de aviso por Wi-Fi: pública, sin login (ver SecurityConfig). El token identifica la cola.
@RestController
@RequestMapping("/api/public/aviso")
public class AvisoController {

    private final AvisoService avisoService;

    public AvisoController(AvisoService avisoService) {
        this.avisoService = avisoService;
    }

    @GetMapping("/{token}")
    public AvisoResponse ver(@PathVariable String token) {
        return avisoService.ver(token);
    }

    @PostMapping("/{token}/entendido")
    public EntendidoResponse entendido(@PathVariable String token) {
        return new EntendidoResponse(avisoService.entendido(token));
    }
}
