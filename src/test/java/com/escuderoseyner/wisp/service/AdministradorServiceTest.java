package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import com.escuderoseyner.wisp.security.GeneradorPasswordTemporal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministradorServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GeneradorPasswordTemporal generadorPassword;

    @Mock
    private ControlIntentosLogin controlIntentos;

    @InjectMocks
    private AdministradorService servicio;

    private Usuario admin(int id, String username, boolean activo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setNombreMostrar(username);
        u.setRol(Rol.ADMIN);
        u.setActivo(activo);
        u.setDebeCambiarPassword(false);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("Un admin no puede desactivarse a sí mismo")
    void noSeDesactivaASiMismo() {
        Usuario yo = admin(1, "admin", true);
        assertThatThrownBy(() -> servicio.desactivar(1, "admin")).isInstanceOf(ReglaNegocioException.class);
        assertThat(yo.getActivo()).isTrue();
        verify(usuarioRepository, never()).findActivosPorRolBloqueando(any());
    }

    @Test
    @DisplayName("Siempre queda al menos un admin activo")
    void quedaAlMenosUno() {
        Usuario otro = admin(2, "rosa", true);
        when(usuarioRepository.findActivosPorRolBloqueando(Rol.ADMIN)).thenReturn(List.of(otro));

        assertThatThrownBy(() -> servicio.desactivar(2, "admin")).isInstanceOf(ReglaNegocioException.class);
        assertThat(otro.getActivo()).isTrue();
    }

    @Test
    @DisplayName("Con dos admins activos, uno puede desactivar al otro")
    void desactivaAOtro() {
        Usuario otro = admin(2, "rosa", true);
        Usuario yo = new Usuario();
        yo.setRol(Rol.ADMIN);
        yo.setActivo(true);
        when(usuarioRepository.findActivosPorRolBloqueando(Rol.ADMIN)).thenReturn(List.of(yo, otro));

        assertThat(servicio.desactivar(2, "admin").activo()).isFalse();
        assertThat(otro.getActivo()).isFalse();
    }

    @Test
    @DisplayName("No se puede usar esta pantalla para tocar la cuenta de un cliente")
    void soloAdmins() {
        Usuario cliente = new Usuario();
        cliente.setRol(Rol.CLIENTE);
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> servicio.desactivar(5, "admin")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.restablecerPassword(5, "admin")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Restablecer: contraseña temporal, debe cambiarla, se cierran sus sesiones y se desbloquea")
    void restablecer() {
        Usuario otro = admin(2, "rosa", true);
        when(generadorPassword.generar()).thenReturn("Ab3dEf7h");
        when(passwordEncoder.encode("Ab3dEf7h")).thenReturn("$2a$hash");

        var cuenta = servicio.restablecerPassword(2, "admin");

        assertThat(cuenta.passwordTemporal()).isEqualTo("Ab3dEf7h");
        assertThat(otro.getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(otro.getDebeCambiarPassword()).isTrue();
        assertThat(otro.getPasswordCambiadoEn()).isNotNull(); // invalida sus tokens anteriores
        verify(controlIntentos).desbloquearCuenta("rosa");
    }

    @Test
    @DisplayName("Un admin no restablece su propia contraseña desde aquí")
    void noRestableceLaPropia() {
        admin(1, "admin", true);
        assertThatThrownBy(() -> servicio.restablecerPassword(1, "ADMIN")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Crear: usuario repetido da error; si no, queda con contraseña temporal")
    void crear() {
        when(usuarioRepository.existsByUsername("rosa")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(new CrearAdministradorRequest("Rosa", " rosa ")))
                .isInstanceOf(ReglaNegocioException.class);

        when(usuarioRepository.existsByUsername("lucho")).thenReturn(false);
        when(generadorPassword.generar()).thenReturn("Xy7kLm2p");
        when(passwordEncoder.encode("Xy7kLm2p")).thenReturn("$2a$otro");
        var cuenta = servicio.crear(new CrearAdministradorRequest("Lucho", "lucho"));

        assertThat(cuenta.username()).isEqualTo("lucho");
        assertThat(cuenta.passwordTemporal()).isEqualTo("Xy7kLm2p");
        verify(usuarioRepository).save(any(Usuario.class));
    }
}
