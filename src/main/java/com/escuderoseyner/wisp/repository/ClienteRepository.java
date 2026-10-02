package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    // Spring arma la consulta a partir del nombre del método:
    // SELECT * FROM clientes WHERE estado = ?
    List<Cliente> findByEstado(EstadoCliente estado);
}
