package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.ComandoTerminalResponse;
import com.escuderoseyner.wisp.dto.EncolarComandoRequest;
import com.escuderoseyner.wisp.service.TerminalService;
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

// Terminal remota del MikroTik (solo ADMIN, ver SecurityConfig)
@RestController
@RequestMapping("/api/admin")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @GetMapping("/redes/{id}/comandos")
    public List<ComandoTerminalResponse> historial(@PathVariable Integer id) {
        return terminalService.historial(id);
    }

    @PostMapping("/redes/{id}/comandos")
    @ResponseStatus(HttpStatus.CREATED)
    public ComandoTerminalResponse encolar(@PathVariable Integer id, @Valid @RequestBody EncolarComandoRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return terminalService.encolar(id, request.comando(), jwt.getSubject());
    }

    @PostMapping("/comandos/{id}/cancelar")
    public ComandoTerminalResponse cancelar(@PathVariable Long id) {
        return terminalService.cancelar(id);
    }
}
