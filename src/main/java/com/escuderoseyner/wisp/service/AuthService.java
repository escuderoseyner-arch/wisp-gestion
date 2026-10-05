package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CambiarPasswordRequest;
import com.escuderoseyner.wisp.dto.LoginRequest;
import com.escuderoseyner.wisp.dto.LoginResponse;
import com.escuderoseyner.wisp.dto.UsuarioActualResponse;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import com.escuderoseyner.wisp.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final int MAX_BYTES_BCRYPT = 72;

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ControlIntentosLogin controlIntentos;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, ControlIntentosLogin controlIntentos) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.controlIntentos = controlIntentos;
    }

    // ip: desde dónde llega el intento. Los límites están explicados en ControlIntentosLogin.
    @Transactional
    public LoginResponse login(LoginRequest request, String ip) {
        // 1. Demasiados fallos desde esta conexión: ni siquiera se revisa la contraseña
        if (controlIntentos.ipBloqueada(ip)) {
            throw new DemasiadosIntentosException();
        }
        // 2. Esta cuenta está bloqueada SOLO para esta IP: mismo mensaje genérico de siempre
        if (controlIntentos.cuentaBloqueada(request.username(), ip)) {
            throw new CredencialesInvalidasException();
        }
        if (excedeLimiteBcrypt(request.password())) {
            controlIntentos.registrarFallo(request.username(), ip);
            throw new CredencialesInvalidasException();
        }

        try {
            // Busca el usuario, revisa que esté activo y compara la contraseña con BCrypt
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException e) {
            // Usuario inexistente, contraseña incorrecta o cuenta desactivada: cuenta como fallo
            controlIntentos.registrarFallo(request.username(), ip);
            throw new CredencialesInvalidasException();
        }

        controlIntentos.registrarExito(request.username(), ip);
        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(CredencialesInvalidasException::new);
        usuario.setUltimoAcceso(LocalDateTime.now());
        // No hace falta save(): al terminar la transacción, JPA guarda los cambios solo
        return crearRespuesta(usuario);
    }

    // Devuelve un token nuevo, ya sin la restricción de "debe cambiar contraseña"
    @Transactional
    public LoginResponse cambiarPassword(String username, CambiarPasswordRequest request, String ip) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .filter(Usuario::getActivo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (controlIntentos.ipBloqueada(ip) || controlIntentos.cuentaBloqueada(username, ip)) {
            throw new DemasiadosIntentosException();
        }
        // Los intentos fallidos aquí también cuentan: evita adivinar la contraseña con un token robado
        if (excedeLimiteBcrypt(request.passwordActual())
                || !passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            controlIntentos.registrarFallo(username, ip);
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        if (excedeLimiteBcrypt(request.passwordNueva())) {
            throw new ReglaNegocioException("La contraseña nueva es demasiado larga.");
        }
        if (passwordEncoder.matches(request.passwordNueva(), usuario.getPasswordHash())) {
            throw new ReglaNegocioException("La contraseña nueva debe ser distinta de la actual.");
        }

        usuario.cambiarPasswordHash(passwordEncoder.encode(request.passwordNueva())); // cierra sus otras sesiones
        usuario.setDebeCambiarPassword(false);
        controlIntentos.registrarExito(username, ip);
        return crearRespuesta(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioActualResponse usuarioActual(String username) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(CredencialesInvalidasException::new);
        // getId() de una relación LAZY no hace otra consulta: el id ya está en la fila
        Integer clienteId = usuario.getCliente() != null ? usuario.getCliente().getId() : null;
        return new UsuarioActualResponse(usuario.getId(), usuario.getUsername(), usuario.getNombreMostrar(),
                usuario.getEmail(), usuario.getRol(), clienteId, usuario.getDebeCambiarPassword());
    }

    // BCrypt no acepta más de 72 bytes (una "ñ" o un emoji ocupan más de 1 byte)
    private boolean excedeLimiteBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT;
    }

    private LoginResponse crearRespuesta(Usuario usuario) {
        return new LoginResponse(jwtService.generarToken(usuario), "Bearer", jwtService.getDuracionSegundos(),
                usuario.getRol(), usuario.getDebeCambiarPassword());
    }
}
