package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Spring crea la implementación sola: findAll(), save(), findById(), etc.
public interface PlanRepository extends JpaRepository<Plan, Integer> {

    // Todos los planes: primero los activos, luego por nombre
    List<Plan> findAllByOrderByActivoDescNombreAsc();

    // Solo los que se pueden elegir para un cliente nuevo
    List<Plan> findByActivoTrueOrderByNombreAsc();

    // Planes con ese nombre ("Básico" y "básico" cuentan como iguales), para revisar repetidos por red
    List<Plan> findByNombreIgnoreCase(String nombre);
}
