package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.ConsumoMensual;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ConsumoMensualRepository extends JpaRepository<ConsumoMensual, Integer> {

    Optional<ConsumoMensual> findByClienteIdAndPeriodo(Integer clienteId, LocalDate periodo);
}
