package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.repository.PlanRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Controlador de PRUEBA para confirmar que todo conecta.
// Más adelante devolveremos DTOs en vez de la entidad directamente.
@RestController
@RequestMapping("/api/planes")
public class PlanController {

    private final PlanRepository planRepository;

    // Inyección por constructor (la forma recomendada)
    public PlanController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public List<Plan> listarActivos() {
        return planRepository.findByActivoTrue();
    }
}