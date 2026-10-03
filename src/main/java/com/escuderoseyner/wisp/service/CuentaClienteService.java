package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CuentaTemporalResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.GeneradorPasswordTemporal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Cuenta del portal de un cliente: usuario = su celular, contraseña temporal aleatoria.
// La contraseña en texto plano solo existe en la respuesta de estos métodos;
// en la base de datos se guarda únicamente su hash BCrypt.
@Service
public class CuentaClienteService {

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final GeneradorPasswordTemporal generadorPassword;

    public CuentaClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder, GeneradorPasswordTemporal generadorPassword) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.generadorPassword = generadorPassword;
    }

    @Transactional
    public CuentaTemporalResponse crearCuenta(Integer clienteId) {
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

        String password = generadorPassword.generar();
        Usuario usuario = new Usuario();
        usuario.setUsername(cliente.getCelular());
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRol(Rol.CLIENTE);
        usuario.setCliente(cliente);
        usuario.setNombreMostrar(cliente.getNombres());
        usuario.setDebeCambiarPassword(true); // la cambia en su primer ingreso
        usuarioRepository.save(usuario);
        return new CuentaTemporalResponse(usuario.getUsername(), password);
    }

    // También desbloquea la cuenta si estaba bloqueada por intentos fallidos
    @Transactional
    public CuentaTemporalResponse restablecerPassword(Integer clienteId) {
        buscarVigente(clienteId);
        Usuario usuario = usuarioRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new ReglaNegocioException("Este cliente todavía no tiene cuenta. Usa \"Crear cuenta\"."));

        String password = generadorPassword.generar();
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setDebeCambiarPassword(true);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuario.setActivo(true);
        return new CuentaTemporalResponse(usuario.getUsername(), password);
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
