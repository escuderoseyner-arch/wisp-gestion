package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.CambiarTokenRedRequest;
import com.escuderoseyner.wisp.dto.CrearRedRequest;
import com.escuderoseyner.wisp.dto.RedRequest;
import com.escuderoseyner.wisp.dto.RedResponse;
import com.escuderoseyner.wisp.dto.TokenRedResponse;
import com.escuderoseyner.wisp.service.RedService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// No hay DELETE: las redes no se borran
@RestController
@RequestMapping("/api/admin/redes") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class RedController {

    private final RedService redService;

    public RedController(RedService redService) {
        this.redService = redService;
    }

    @GetMapping
    public List<RedResponse> listar() {
        return redService.listar();
    }

    @GetMapping("/{id}")
    public RedResponse detalle(@PathVariable Integer id) {
        return redService.detalle(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TokenRedResponse crear(@Valid @RequestBody CrearRedRequest request) {
        return redService.crear(request);
    }

    @PutMapping("/{id}")
    public RedResponse editar(@PathVariable Integer id, @Valid @RequestBody RedRequest request) {
        return redService.editar(id, request);
    }

    // Cuerpo vacío ({}) = la web genera un token nuevo
    @PostMapping("/{id}/token")
    public TokenRedResponse cambiarToken(@PathVariable Integer id, @Valid @RequestBody CambiarTokenRedRequest request) {
        return redService.cambiarToken(id, request.token());
    }
}
