package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// Le dice a Spring Security cómo buscar un usuario en la tabla "usuarios".
// Spring usa esto en el login para comparar la contraseña con el hash BCrypt.
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findConClienteByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        boolean bloqueado = usuario.getBloqueadoHasta() != null
                && usuario.getBloqueadoHasta().isAfter(LocalDateTime.now());
        // Un cliente RETIRADO no entra al portal (un SUSPENDIDO sí, para ver su deuda)
        boolean clienteRetirado = usuario.getCliente() != null
                && usuario.getCliente().getEstado() == EstadoCliente.RETIRADO;

        // Si la cuenta está bloqueada o desactivada, Spring rechaza el login
        // ANTES de revisar la contraseña.
        return User.withUsername(usuario.getUsername())
                .password(usuario.getPasswordHash())
                .roles(usuario.getRol().name())
                .disabled(!usuario.getActivo() || clienteRetirado)
                .accountLocked(bloqueado)
                .build();
    }
}
