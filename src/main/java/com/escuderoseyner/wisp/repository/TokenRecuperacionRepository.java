package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.TokenRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Integer> {

    // Se busca por el hash: el token que llega por correo se hashea antes de buscar
    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);
}
