package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ConfiguracionPublicaResponse;
import com.escuderoseyner.wisp.service.ConfiguracionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/public/**: no necesita token (ver SecurityConfig)
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ConfiguracionService configuracionService;

    public PublicController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping("/configuracion")
    public ConfiguracionPublicaResponse configuracion() {
        return configuracionService.obtenerPublica();
    }
}
