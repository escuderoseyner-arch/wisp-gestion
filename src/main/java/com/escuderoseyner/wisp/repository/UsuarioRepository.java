package com.escuderoseyner.wisp.repository;

import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // Para el login. Optional: puede que el usuario no exista.
    Optional<Usuario> findByUsername(String username);

    // Igual, pero trayendo también su cliente (para saber si está retirado) en la misma consulta
    @Query("SELECT u FROM Usuario u LEFT JOIN FETCH u.cliente WHERE u.username = :username")
    Optional<Usuario> findConClienteByUsername(String username);

    // Para "¿Olvidaste tu contraseña?"
    Optional<Usuario> findByEmail(String email);

    // Para saber si hay que crear el admin inicial al arrancar
    boolean existsByRol(Rol rol);

    // Lista del personal (administradores y operadores)
    List<Usuario> findByRolInOrderByNombreMostrarAsc(Collection<Rol> roles);

    // Admins activos, BLOQUEANDO esas filas hasta que termine la transacción (SELECT ... FOR UPDATE).
    // Así, si dos admins se desactivan entre sí al mismo tiempo, el segundo espera al primero
    // y ve el conteo real: nunca se queda el sistema sin ningún admin activo.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.rol = :rol AND u.activo = true")
    List<Usuario> findActivosPorRolBloqueando(Rol rol);

    // La cuenta del portal de un cliente (si tiene)
    Optional<Usuario> findByClienteId(Integer clienteId);

    // ¿Otro usuario ya usa este username?
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);
}
