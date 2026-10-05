package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.AdministradorResponse;
import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.dto.CuentaTemporalResponse;
import com.escuderoseyner.wisp.service.AdministradorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// No hay DELETE: los admins se desactivan. El admin que hace la acción sale del token.
@RestController
@RequestMapping("/api/admin/administradores") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class AdministradorController {

    private final AdministradorService administradorService;

    public AdministradorController(AdministradorService administradorService) {
        this.administradorService = administradorService;
    }

    @GetMapping
    public List<AdministradorResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return administradorService.listar(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuentaTemporalResponse crear(@Valid @RequestBody CrearAdministradorRequest request) {
        return administradorService.crear(request);
    }

    @PostMapping("/{id}/desactivar")
    public AdministradorResponse desactivar(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return administradorService.desactivar(id, jwt.getSubject());
    }

    @PostMapping("/{id}/reactivar")
    public AdministradorResponse reactivar(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return administradorService.reactivar(id, jwt.getSubject());
    }

    @PostMapping("/{id}/restablecer-password")
    public CuentaTemporalResponse restablecerPassword(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt) {
        return administradorService.restablecerPassword(id, jwt.getSubject());
    }
}
