package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.EstadoCuentaResponse;
import com.escuderoseyner.wisp.dto.RegistrarPagoRequest;
import com.escuderoseyner.wisp.dto.ResumenMesResponse;
import com.escuderoseyner.wisp.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pagos") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    // GET /api/admin/pagos/mes?periodo=2026-10  (sin periodo: el mes actual)
    @GetMapping("/mes")
    public ResumenMesResponse resumenDelMes(@RequestParam(required = false) String periodo) {
        return pagoService.resumenDelMes(periodo);
    }

    @GetMapping("/cliente/{clienteId}")
    public EstadoCuentaResponse estadoDeCuenta(@PathVariable Integer clienteId) {
        return pagoService.estadoDeCuenta(clienteId);
    }

    // registrado_por = el admin del token, nunca un dato enviado por el navegador
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstadoCuentaResponse registrar(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody RegistrarPagoRequest request) {
        return pagoService.registrar(request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Integer id) {
        pagoService.eliminar(id);
    }
}
