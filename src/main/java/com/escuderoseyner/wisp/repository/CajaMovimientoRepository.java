package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.CajaMovimiento;
import com.escuderoseyner.wisp.model.TipoMovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CajaMovimientoRepository extends JpaRepository<CajaMovimiento, Integer> {

    // [tipo, suma de montos] de toda la caja
    @Query("SELECT m.tipo, SUM(m.monto) FROM CajaMovimiento m WHERE m.caja.id = :cajaId GROUP BY m.tipo")
    List<Object[]> sumarPorTipo(Integer cajaId);

    // [tipo, suma de montos] entre dos fechas (incluidas)
    @Query("""
            SELECT m.tipo, SUM(m.monto) FROM CajaMovimiento m
            WHERE m.caja.id = :cajaId AND m.fecha BETWEEN :desde AND :hasta
            GROUP BY m.tipo
            """)
    List<Object[]> sumarPorTipoEntre(Integer cajaId, LocalDate desde, LocalDate hasta);

    // [tipo, suma de montos] antes de una fecha (para el saldo con que empieza un mes)
    @Query("""
            SELECT m.tipo, SUM(m.monto) FROM CajaMovimiento m
            WHERE m.caja.id = :cajaId AND m.fecha < :fecha
            GROUP BY m.tipo
            """)
    List<Object[]> sumarPorTipoAntesDe(Integer cajaId, LocalDate fecha);

    // En el orden en que se acumula el saldo
    @Query("""
            SELECT m FROM CajaMovimiento m LEFT JOIN FETCH m.usuario
            WHERE m.caja.id = :cajaId
            ORDER BY m.fecha, m.id
            """)
    List<CajaMovimiento> findDeCaja(Integer cajaId);

    @Query("""
            SELECT m FROM CajaMovimiento m LEFT JOIN FETCH m.usuario
            WHERE m.caja.id = :cajaId AND m.fecha BETWEEN :desde AND :hasta
            ORDER BY m.fecha, m.id
            """)
    List<CajaMovimiento> findDeCajaEntre(Integer cajaId, LocalDate desde, LocalDate hasta);

    boolean existsByCajaIdAndTipoAndPeriodo(Integer cajaId, TipoMovimientoCaja tipo, LocalDate periodo);

    Optional<CajaMovimiento> findByIdAndCajaId(Integer id, Integer cajaId);

    @Modifying
    @Query("DELETE FROM CajaMovimiento m WHERE m.pagoId = :pagoId")
    void eliminarDePago(Integer pagoId);
}
