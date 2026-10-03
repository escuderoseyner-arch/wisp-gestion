package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.PortalClienteResponse;
import com.escuderoseyner.wisp.service.PortalClienteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/cliente/**: solo CLIENTE (ver SecurityConfig).
// Ningún endpoint recibe un id de cliente: siempre se usa el del token.
@RestController
@RequestMapping("/api/cliente")
public class PortalClienteController {

    private final PortalClienteService portalClienteService;

    public PortalClienteController(PortalClienteService portalClienteService) {
        this.portalClienteService = portalClienteService;
    }

    @GetMapping("/resumen")
    public PortalClienteResponse resumen(@AuthenticationPrincipal Jwt jwt) {
        return portalClienteService.resumen(jwt.getSubject());
    }
}
