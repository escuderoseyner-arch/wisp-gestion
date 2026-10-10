package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Integer> {

    @Query("SELECT c FROM Caja c JOIN FETCH c.red ORDER BY c.red.nombre")
    List<Caja> findTodasConRed();

    @Query("SELECT c FROM Caja c JOIN FETCH c.red WHERE c.id = :id")
    Optional<Caja> findConRed(Integer id);

    Optional<Caja> findByRedId(Integer redId);

    @Query("SELECT c.id FROM Caja c WHERE c.descuentoActivo = true")
    List<Integer> findIdsConDescuentoActivo();
}
