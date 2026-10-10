package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.CajaDetalleResponse;
import com.escuderoseyner.wisp.dto.CajaResumenResponse;
import com.escuderoseyner.wisp.dto.DescuentoCajaRequest;
import com.escuderoseyner.wisp.dto.HistorialCajaResponse;
import com.escuderoseyner.wisp.dto.RetiroCajaRequest;
import com.escuderoseyner.wisp.service.CajaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/cajas") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping
    public List<CajaResumenResponse> listar() {
        return cajaService.listar();
    }

    @GetMapping("/{id}")
    public CajaDetalleResponse detalle(@PathVariable Integer id) {
        return cajaService.detalle(id);
    }

    // GET /api/admin/cajas/1/movimientos?mes=2026-10  (sin mes: todo el historial)
    @GetMapping("/{id}/movimientos")
    public HistorialCajaResponse historial(@PathVariable Integer id, @RequestParam(required = false) String mes) {
        return cajaService.historial(id, mes);
    }

    // El admin que retira sale del token, nunca de un dato enviado por el navegador
    @PostMapping("/{id}/retiros")
    @ResponseStatus(HttpStatus.CREATED)
    public CajaDetalleResponse registrarRetiro(@PathVariable Integer id, @AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody RetiroCajaRequest request) {
        return cajaService.registrarRetiro(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}/retiros/{movimientoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarRetiro(@PathVariable Integer id, @PathVariable Integer movimientoId) {
        cajaService.eliminarRetiro(id, movimientoId);
    }

    @PutMapping("/{id}/descuento")
    public CajaDetalleResponse editarDescuento(@PathVariable Integer id,
                                               @Valid @RequestBody DescuentoCajaRequest request) {
        return cajaService.editarDescuento(id, request);
    }
}
