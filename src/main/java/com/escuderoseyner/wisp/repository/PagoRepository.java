package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    // Historial de un cliente, del mes más reciente al más antiguo
    List<Pago> findByClienteIdOrderByPeriodoDesc(Integer clienteId);

    // Todos los pagos de un mes (para "cobrado vs pendiente")
    List<Pago> findByPeriodo(LocalDate periodo);

    // Pagos hechos entre dos fechas (para "pagos de la semana")
    List<Pago> findByFechaPagoBetween(LocalDate desde, LocalDate hasta);

    // Igual que los anteriores, pero trayendo en la misma consulta al admin que registró cada pago
    @Query("SELECT p FROM Pago p JOIN FETCH p.registradoPor WHERE p.cliente.id = :clienteId ORDER BY p.periodo DESC")
    List<Pago> findHistorialDeCliente(Integer clienteId);

    @Query("SELECT p FROM Pago p JOIN FETCH p.registradoPor WHERE p.periodo = :periodo")
    List<Pago> findDelPeriodo(LocalDate periodo);

    // Pagos recibidos entre dos fechas, con su cliente (para "pagos de esta semana")
    @Query("""
            SELECT p FROM Pago p JOIN FETCH p.cliente
            WHERE p.fechaPago BETWEEN :desde AND :hasta
            ORDER BY p.fechaPago DESC, p.id DESC
            """)
    List<Pago> findRecibidosEntre(LocalDate desde, LocalDate hasta);

    // [id del pago, nombre del admin que lo registró] (para el historial de la caja)
    @Query("SELECT p.id, u.nombreMostrar FROM Pago p JOIN p.registradoPor u WHERE p.id IN :ids")
    List<Object[]> findNombreRegistradorDe(Collection<Integer> ids);

    // Solo [id del cliente, periodo] de todos los pagos: liviano, para calcular los atrasados
    @Query("SELECT p.cliente.id, p.periodo FROM Pago p")
    List<Object[]> findClienteYPeriodo();
}
