package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Spring crea la implementación sola: findAll(), save(), findById(), etc.
public interface PlanRepository extends JpaRepository<Plan, Integer> {

    // Spring arma la consulta a partir del nombre del método:
    // SELECT * FROM planes WHERE activo = true
    List<Plan> findByActivoTrue();
}