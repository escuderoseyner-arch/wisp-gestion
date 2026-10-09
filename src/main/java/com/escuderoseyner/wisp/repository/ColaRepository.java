package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Cola;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ColaRepository extends JpaRepository<Cola, Integer> {

    @Query("SELECT c FROM Cola c JOIN FETCH c.cliente WHERE c.red.id = :redId ORDER BY c.nombre")
    List<Cola> findByRedId(Integer redId);

    Optional<Cola> findByRedIdAndNombreIgnoreCase(Integer redId, String nombre);

    List<Cola> findByClienteId(Integer clienteId);
}
