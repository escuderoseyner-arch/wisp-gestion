package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.ComandoTerminal;
import com.escuderoseyner.wisp.model.EstadoComando;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ComandoTerminalRepository extends JpaRepository<ComandoTerminal, Long> {

    // Historial de una red, del más nuevo al más antiguo, con quién lo envió
    @Query("SELECT c FROM ComandoTerminal c JOIN FETCH c.creadoPor WHERE c.red.id = :redId ORDER BY c.id DESC")
    List<ComandoTerminal> findHistorial(Integer redId, Pageable pagina);

    List<ComandoTerminal> findByRedIdAndEstadoOrderByIdAsc(Integer redId, EstadoComando estado);

    long countByRedIdAndEstado(Integer redId, EstadoComando estado);

    // Borra los comandos ya terminados de una red (los que esperan al MikroTik se conservan)
    @Modifying
    @Query("DELETE FROM ComandoTerminal c WHERE c.red.id = :redId AND c.estado IN :terminados")
    int borrarTerminadosDeRed(Integer redId, Collection<EstadoComando> terminados);

    // Borra los comandos terminados creados antes de la fecha indicada (todas las redes)
    @Modifying
    @Query("DELETE FROM ComandoTerminal c WHERE c.creadoEn < :antesDe AND c.estado IN :terminados")
    int borrarTerminadosAntesDe(LocalDateTime antesDe, Collection<EstadoComando> terminados);
}
