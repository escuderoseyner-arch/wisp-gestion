package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // Para el login. Optional: puede que el usuario no exista.
    Optional<Usuario> findByUsername(String username);

    // Para "¿Olvidaste tu contraseña?"
    Optional<Usuario> findByEmail(String email);

    // Para saber si hay que crear el admin inicial al arrancar
    boolean existsByRol(Rol rol);

    // La cuenta del portal de un cliente (si tiene)
    Optional<Usuario> findByClienteId(Integer clienteId);

    // ¿Otro usuario ya usa este username?
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);
}
