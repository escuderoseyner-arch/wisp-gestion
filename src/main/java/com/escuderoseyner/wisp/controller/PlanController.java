package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.CambiarActivoRequest;
import com.escuderoseyner.wisp.dto.PlanRequest;
import com.escuderoseyner.wisp.dto.PlanResponse;
import com.escuderoseyner.wisp.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// No hay DELETE a propósito: los planes se desactivan, nunca se borran
@RestController
@RequestMapping("/api/admin/planes") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    // GET /api/admin/planes            -> todos
    // GET /api/admin/planes?activos=true -> solo los que se pueden asignar a clientes nuevos
    @GetMapping
    public List<PlanResponse> listar(@RequestParam(defaultValue = "false") boolean activos) {
        return planService.listar(activos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse crear(@Valid @RequestBody PlanRequest request) {
        return planService.crear(request);
    }

    @PutMapping("/{id}")
    public PlanResponse actualizar(@PathVariable Integer id, @Valid @RequestBody PlanRequest request) {
        return planService.actualizar(id, request);
    }

    @PatchMapping("/{id}/activo")
    public PlanResponse cambiarActivo(@PathVariable Integer id, @Valid @RequestBody CambiarActivoRequest request) {
        return planService.cambiarActivo(id, request.activo());
    }
}
