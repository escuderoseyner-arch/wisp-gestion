package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CuentaResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Cuenta del portal de un cliente: usuario = su celular, contraseña inicial escrita por el admin.
// El cliente debe cambiarla en su primer ingreso. En la base de datos solo queda su hash BCrypt.
@Service
public class CuentaClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ControlIntentosLogin controlIntentos;

    public CuentaClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder, ControlIntentosLogin controlIntentos) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.controlIntentos = controlIntentos;
    }

    @Transactional
    public CuentaResponse crearCuenta(Integer clienteId, String password) {
        Cliente cliente = buscarVigente(clienteId);
        if (cliente.getCelular() == null) {
            throw new ReglaNegocioException("Agrega un celular al cliente: es el usuario con el que ingresa al portal.");
        }
        if (usuarioRepository.findByClienteId(clienteId).isPresent()) {
            throw new ReglaNegocioException("Este cliente ya tiene cuenta. Usa \"Restablecer contraseña\" si la olvidó.");
        }
        if (usuarioRepository.existsByUsername(cliente.getCelular())) {
            throw new ReglaNegocioException("El celular " + cliente.getCelular()
                    + " ya lo usa otro usuario del sistema. No se creó la cuenta.");
        }

        ReglasPassword.validar(password);
        Usuario usuario = new Usuario();
        usuario.setUsername(cliente.getCelular());
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRol(Rol.CLIENTE);
        usuario.setCliente(cliente);
        usuario.setNombreMostrar(cliente.getNombres());
        usuario.setDebeCambiarPassword(true); // la cambia en su primer ingreso
        usuarioRepository.save(usuario);
        return new CuentaResponse(usuario.getUsername());
    }

    // También desbloquea la cuenta si estaba bloqueada por intentos fallidos
    @Transactional
    public CuentaResponse restablecerPassword(Integer clienteId, String password) {
        buscarVigente(clienteId);
        Usuario usuario = usuarioRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ReglaNegocioException("Este cliente todavía no tiene cuenta. Usa \"Crear cuenta\"."));

        ReglasPassword.validar(password);
        usuario.cambiarPasswordHash(passwordEncoder.encode(password)); // cierra las sesiones abiertas
        usuario.setDebeCambiarPassword(true);
        usuario.setActivo(true);
        // El bloqueo real vive en memoria (ControlIntentosLogin); las columnas se dejan limpias
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        controlIntentos.desbloquearCuenta(usuario.getUsername());
        return new CuentaResponse(usuario.getUsername());
    }

    // El usuario es independiente del celular. La comparación en MySQL no distingue
    // mayúsculas, así que "Juan" choca con "juan".
    @Transactional
    public String cambiarUsuario(Integer clienteId, String usernameNuevo) {
        buscarVigente(clienteId);
        Usuario usuario = usuarioRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ReglaNegocioException("Este cliente todavía no tiene cuenta. Usa \"Crear cuenta\"."));
        String username = usernameNuevo.trim();
        if (usuarioRepository.existsByUsernameAndIdNot(username, usuario.getId())) {
            throw new ReglaNegocioException("El usuario " + username + " ya lo usa otra persona.");
        }
        usuario.setUsername(username);
        return username;
    }

    private Cliente buscarVigente(Integer clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con el id " + clienteId + "."));
        if (cliente.getEstado() == EstadoCliente.RETIRADO) {
            throw new ReglaNegocioException("El cliente " + cliente.getCodigo()
                    + " está retirado: su cuenta queda desactivada.");
        }
        return cliente;
    }
}
