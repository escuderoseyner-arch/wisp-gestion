package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Red;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RedRepository extends JpaRepository<Red, Integer> {

    List<Red> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    // Dos redes con el mismo prefijo darían el mismo "siguiente código"
    boolean existsByPrefijoCodigo(String prefijo);

    boolean existsByPrefijoCodigoAndIdNot(String prefijo, Integer id);

    List<Red> findByIdInOrderByNombreAsc(java.util.Collection<Integer> ids);

    // El MikroTik se identifica con su token: se busca por el hash, nunca por el token
    Optional<Red> findByTokenHash(String tokenHash);

    boolean existsByTokenHash(String tokenHash);
}
