package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    // Spring arma la consulta a partir del nombre del método:
    // SELECT * FROM clientes WHERE estado = ?
    List<Cliente> findByEstado(EstadoCliente estado);

    // Listado con filtros. Un filtro en null significa "no filtrar por eso".
    // JOIN FETCH trae el plan en la misma consulta (evita una consulta extra por cada cliente).
    // ESCAPE '!': permite buscar textos que contienen % o _ sin que actúen como comodines.
    @Query("""
            SELECT c FROM Cliente c JOIN FETCH c.plan
            WHERE c.estado IN :estados
              AND (:zona IS NULL OR c.zona = :zona)
              AND (:busqueda IS NULL
                   OR LOWER(c.codigo) LIKE :busqueda ESCAPE '!'
                   OR LOWER(c.nombres) LIKE :busqueda ESCAPE '!')
            """)
    List<Cliente> buscar(Collection<EstadoCliente> estados, String zona, String busqueda);

    // Clientes que cuentan en el resumen de un mes: los vigentes hoy, los retirados en ese mes o después
    // (ese mes todavía eran clientes) y cualquiera que tenga un pago de ese mes.
    // Si de verdad le tocaba pagar ese mes lo decide CalculadoraEstadoMes (inicio y retiro).
    @Query("""
            SELECT c FROM Cliente c JOIN FETCH c.plan
            WHERE c.estado <> com.escuderoseyner.wisp.model.EstadoCliente.RETIRADO
               OR c.fechaRetiro >= :inicioMes
               OR c.id IN (SELECT p.cliente.id FROM Pago p WHERE p.periodo = :inicioMes)
            """)
    List<Cliente> findParaResumenDelMes(LocalDate inicioMes);

    // Zonas ya usadas, para el filtro y para sugerirlas al escribir
    @Query("SELECT DISTINCT c.zona FROM Cliente c WHERE c.zona IS NOT NULL ORDER BY c.zona")
    List<String> findZonas();

    // Todos los códigos (incluidos retirados), para sugerir el siguiente
    @Query("SELECT c.codigo FROM Cliente c")
    List<String> findAllCodigos();

    // codigo_vigente vale el código solo si el cliente NO está retirado
    boolean existsByCodigoVigente(String codigo);

    boolean existsByCodigoVigenteAndIdNot(String codigo, Integer id);

    // ¿Otro cliente vigente usa esta IP?
    boolean existsByIpAndEstadoNot(String ip, EstadoCliente estado);

    boolean existsByIpAndEstadoNotAndIdNot(String ip, EstadoCliente estado, Integer id);

    // ¿Otro cliente vigente de la misma red usa este nombre de cola?
    @Query("""
            SELECT COUNT(c) > 0 FROM Cliente c
            WHERE c.red.id = :redId AND LOWER(c.nombreCola) = LOWER(:nombreCola)
              AND c.estado <> com.escuderoseyner.wisp.model.EstadoCliente.RETIRADO
              AND (:idExcluir IS NULL OR c.id <> :idExcluir)
            """)
    boolean colaEnUso(Integer redId, String nombreCola, Integer idExcluir);

    // Nombres de cola de los clientes vigentes de una red
    @Query("""
            SELECT c.nombreCola FROM Cliente c
            WHERE c.red.id = :redId AND c.estado <> com.escuderoseyner.wisp.model.EstadoCliente.RETIRADO
            """)
    List<String> findNombresColaVigentes(Integer redId);

    long countByRedIdAndEstadoNot(Integer redId, EstadoCliente estado);

    // Clientes vigentes de una red, con su plan (para calcular sus colas)
    @Query("""
            SELECT c FROM Cliente c JOIN FETCH c.plan
            WHERE c.red.id = :redId AND c.estado <> com.escuderoseyner.wisp.model.EstadoCliente.RETIRADO
            """)
    List<Cliente> findVigentesDeRed(Integer redId);
}
