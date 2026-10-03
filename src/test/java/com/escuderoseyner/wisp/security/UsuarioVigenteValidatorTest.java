package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioVigenteValidatorTest {

    private static final LocalDateTime CAMBIO_PASSWORD = LocalDateTime.of(2026, 10, 3, 10, 0, 0);

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioVigenteValidator validator;

    private Jwt token(Instant emitido) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("900000001").issuedAt(emitido).build();
    }

    private Instant instante(LocalDateTime fecha) {
        return fecha.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Usuario usuario(boolean activo, EstadoCliente estadoCliente, LocalDateTime cambioPassword) {
        Cliente cliente = new Cliente();
        cliente.setEstado(estadoCliente);
        Usuario usuario = new Usuario();
        usuario.setUsername("900000001");
        usuario.setRol(Rol.CLIENTE);
        usuario.setActivo(activo);
        usuario.setCliente(cliente);
        usuario.setPasswordCambiadoEn(cambioPassword);
        when(usuarioRepository.findConClienteByUsername("900000001")).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    @DisplayName("Usuario activo, token emitido después del cambio de contraseña: válido")
    void valido() {
        usuario(true, EstadoCliente.SUSPENDIDO, CAMBIO_PASSWORD);
        assertThat(validator.validate(token(instante(CAMBIO_PASSWORD.plusMinutes(5)))).hasErrors()).isFalse();
        // emitido en el mismo segundo del cambio (el token nuevo que se entrega al cambiarla): válido
        assertThat(validator.validate(token(instante(CAMBIO_PASSWORD))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Token emitido antes del último cambio de contraseña: rechazado")
    void tokenAnteriorAlCambio() {
        usuario(true, EstadoCliente.ACTIVO, CAMBIO_PASSWORD);
        assertThat(validator.validate(token(instante(CAMBIO_PASSWORD.minusSeconds(1)))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Cuenta desactivada: rechazado")
    void desactivado() {
        usuario(false, EstadoCliente.ACTIVO, null);
        assertThat(validator.validate(token(Instant.now())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Cliente retirado: rechazado")
    void clienteRetirado() {
        usuario(true, EstadoCliente.RETIRADO, null);
        assertThat(validator.validate(token(Instant.now())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("El usuario del token ya no existe (o cambió de nombre): rechazado")
    void usuarioInexistente() {
        when(usuarioRepository.findConClienteByUsername("900000001")).thenReturn(Optional.empty());
        assertThat(validator.validate(token(Instant.now())).hasErrors()).isTrue();
    }
}
