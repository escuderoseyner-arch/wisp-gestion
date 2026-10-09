package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.ConsumoMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConsumoMensualRepository extends JpaRepository<ConsumoMensual, Integer> {

    Optional<ConsumoMensual> findByClienteIdAndPeriodo(Integer clienteId, LocalDate periodo);

    // Consumo del mes de los clientes que hoy son dueños de una cola de la red: {subida, bajada}
    @Query("""
            SELECT COALESCE(SUM(c.bytesSubida), 0), COALESCE(SUM(c.bytesBajada), 0) FROM ConsumoMensual c
            WHERE c.periodo = :periodo
              AND c.cliente.id IN (SELECT co.cliente.id FROM Cola co WHERE co.red.id = :redId)
            """)
    List<Object[]> sumarPorRed(Integer redId, LocalDate periodo);
}
