package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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

        // Un cliente RETIRADO no entra al portal (un SUSPENDIDO sí, para ver su deuda)
        boolean clienteRetirado = usuario.getCliente() != null
                && usuario.getCliente().getEstado() == EstadoCliente.RETIRADO;

        // Si la cuenta está desactivada, Spring rechaza el login ANTES de revisar la contraseña.
        // Los bloqueos por intentos fallidos los maneja ControlIntentosLogin (por cuenta + IP),
        // así que las columnas intentos_fallidos y bloqueado_hasta ya no se usan.
        return User.withUsername(usuario.getUsername())
                .password(usuario.getPasswordHash())
                .roles(usuario.getRol().name())
                .disabled(!usuario.getActivo() || clienteRetirado)
                .build();
    }
}
