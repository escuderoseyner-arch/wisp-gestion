package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ConfiguracionRequest;
import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.service.ConfiguracionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/configuracion") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public ConfiguracionResponse obtener() {
        return configuracionService.obtener();
    }

    @PutMapping
    public ConfiguracionResponse actualizar(@Valid @RequestBody ConfiguracionRequest request) {
        return configuracionService.actualizar(request);
    }
}
