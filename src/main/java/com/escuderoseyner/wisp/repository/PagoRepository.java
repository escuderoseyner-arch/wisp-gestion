package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    // Historial de un cliente, del mes más reciente al más antiguo
    List<Pago> findByClienteIdOrderByPeriodoDesc(Integer clienteId);

    // Todos los pagos de un mes (para "cobrado vs pendiente")
    List<Pago> findByPeriodo(LocalDate periodo);

    // Pagos hechos entre dos fechas (para "pagos de la semana")
    List<Pago> findByFechaPagoBetween(LocalDate desde, LocalDate hasta);
}
