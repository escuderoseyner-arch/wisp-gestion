package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.AccionCola;
import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.MotivoAccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccionColaRepository extends JpaRepository<AccionCola, Long> {

    // Acciones por enviar a una red, de la más antigua a la más nueva
    @Query("""
            SELECT a FROM AccionCola a JOIN FETCH a.cola c
            WHERE c.red.id = :redId AND a.estado IN :estados
            ORDER BY a.id
            """)
    List<AccionCola> findPorRedYEstados(Integer redId, Collection<EstadoAccion> estados);

    List<AccionCola> findByColaIdAndEstadoIn(Integer colaId, Collection<EstadoAccion> estados);

    // Última acción de una cola (para mostrar si un corte está pendiente, aplicado o con error)
    Optional<AccionCola> findFirstByColaIdOrderByIdDesc(Integer colaId);

    // Último Cortar/Reconectar de una cola
    Optional<AccionCola> findFirstByColaIdAndMotivoInOrderByIdDesc(Integer colaId, Collection<MotivoAccion> motivos);

    @Query("SELECT COUNT(a) FROM AccionCola a WHERE a.cola.red.id = :redId AND a.estado IN :estados")
    long contarPorRedYEstados(Integer redId, Collection<EstadoAccion> estados);
}
