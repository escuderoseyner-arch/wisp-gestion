package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

// La configuración es una sola fila: se lee con findById(1)
public interface ConfiguracionRepository extends JpaRepository<Configuracion, Integer> {
}
