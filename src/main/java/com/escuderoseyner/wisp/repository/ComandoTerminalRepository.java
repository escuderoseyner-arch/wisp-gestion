package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.ComandoTerminal;
import com.escuderoseyner.wisp.model.EstadoComando;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ComandoTerminalRepository extends JpaRepository<ComandoTerminal, Long> {

    // Historial de una red, del más nuevo al más antiguo, con quién lo envió
    @Query("SELECT c FROM ComandoTerminal c JOIN FETCH c.creadoPor WHERE c.red.id = :redId ORDER BY c.id DESC")
    List<ComandoTerminal> findHistorial(Integer redId, Pageable pagina);

    List<ComandoTerminal> findByRedIdAndEstadoOrderByIdAsc(Integer redId, EstadoComando estado);

    long countByRedIdAndEstado(Integer redId, EstadoComando estado);
}
