package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CambiarPasswordRequest;
import com.escuderoseyner.wisp.dto.LoginRequest;
import com.escuderoseyner.wisp.dto.LoginResponse;
import com.escuderoseyner.wisp.dto.UsuarioActualResponse;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_BYTES_BCRYPT = 72;

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // noRollbackFor: aunque el login falle, el contador de intentos SÍ debe guardarse
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public LoginResponse login(LoginRequest request) {
        if (excedeLimiteBcrypt(request.password())) {
            throw new CredencialesInvalidasException();
        }

        try {
            // Busca el usuario, revisa bloqueo/activo y compara la contraseña con BCrypt
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            // Usuario inexistente o contraseña incorrecta: solo se cuenta si el usuario existe
            usuarioRepository.findByUsername(request.username()).ifPresent(this::registrarIntentoFallido);
            throw new CredencialesInvalidasException();
        } catch (AuthenticationException e) {
            // Cuenta bloqueada o desactivada: mismo mensaje genérico
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(CredencialesInvalidasException::new);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuario.setUltimoAcceso(LocalDateTime.now());
        // No hace falta save(): al terminar la transacción, JPA guarda los cambios solo
        return crearRespuesta(usuario);
    }

    // Devuelve un token nuevo, ya sin la restricción de "debe cambiar contraseña"
    @Transactional(noRollbackFor = ReglaNegocioException.class)
    public LoginResponse cambiarPassword(String username, CambiarPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .filter(Usuario::getActivo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (estaBloqueado(usuario)) {
            throw new ReglaNegocioException("Tu cuenta está bloqueada temporalmente. Espera 15 minutos e inténtalo de nuevo.");
        }
        // Los intentos fallidos aquí también cuentan: evita adivinar la contraseña con un token robado
        if (excedeLimiteBcrypt(request.passwordActual())
                || !passwordEncoder.matches(request.passwordActual(), usuario.getPasswordHash())) {
            registrarIntentoFallido(usuario);
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
        usuario.setIntentosFallidos(0);
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

    // 5 fallos seguidos: bloquea 15 minutos y reinicia el contador
    private void registrarIntentoFallido(Usuario usuario) {
        int intentos = usuario.getIntentosFallidos() + 1;
        if (intentos >= MAX_INTENTOS_FALLIDOS) {
            usuario.setBloqueadoHasta(LocalDateTime.now().plus(DURACION_BLOQUEO));
            usuario.setIntentosFallidos(0);
        } else {
            usuario.setIntentosFallidos(intentos);
        }
    }

    private boolean estaBloqueado(Usuario usuario) {
        return usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(LocalDateTime.now());
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
