package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ColaClienteResponse;
import com.escuderoseyner.wisp.dto.ColaResponse;
import com.escuderoseyner.wisp.dto.PanelRedResponse;
import com.escuderoseyner.wisp.service.ColaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Colas de los clientes en el MikroTik (solo ADMIN, ver SecurityConfig)
@RestController
@RequestMapping("/api/admin")
public class ColaController {

    private final ColaService colaService;

    public ColaController(ColaService colaService) {
        this.colaService = colaService;
    }

    // Resumen: última conexión del MikroTik, clientes sin conexión, consumo del mes y diferencias
    @GetMapping("/redes/{id}/panel")
    public PanelRedResponse panel(@PathVariable Integer id) {
        return colaService.panel(id);
    }

    // Colas de la red con lo que quiere la web, lo que reportó el MikroTik y sus diferencias
    @GetMapping("/redes/{id}/colas")
    public List<ColaResponse> colasDeRed(@PathVariable Integer id) {
        return colaService.listarDeRed(id);
    }

    // Solo en modo Control: crea acciones para las colas que no coinciden con la web
    @PostMapping("/redes/{id}/aplicar-diferencias")
    public Map<String, Integer> aplicarDiferencias(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("accionesCreadas", colaService.aplicarDiferencias(id, jwt.getSubject()));
    }

    @GetMapping("/clientes/{id}/cola")
    public ColaClienteResponse colaDeCliente(@PathVariable Integer id) {
        return colaService.deCliente(id);
    }

    @PostMapping("/clientes/{id}/cortar")
    public ColaClienteResponse cortar(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return colaService.cortarOReconectar(id, true, jwt.getSubject());
    }

    @PostMapping("/clientes/{id}/reconectar")
    public ColaClienteResponse reconectar(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return colaService.cortarOReconectar(id, false, jwt.getSubject());
    }
}
