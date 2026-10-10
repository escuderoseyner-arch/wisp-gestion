package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CrearAdministradorRequest;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.ControlIntentosLogin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
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
    private ControlIntentosLogin controlIntentos;

    @InjectMocks
    private AdministradorService servicio;

    private Usuario cuenta(int id, String username, Rol rol, boolean activo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setNombreMostrar(username);
        u.setRol(rol);
        u.setActivo(activo);
        u.setDebeCambiarPassword(false);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private Usuario admin(int id, String username, boolean activo) {
        return cuenta(id, username, Rol.ADMIN, activo);
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
    @DisplayName("Desactivar a un operador no depende de cuántos admins quedan")
    void desactivaOperador() {
        Usuario operador = cuenta(3, "lucho", Rol.OPERADOR, true);

        assertThat(servicio.desactivar(3, "admin").activo()).isFalse();
        assertThat(operador.getActivo()).isFalse();
        verify(usuarioRepository, never()).findActivosPorRolBloqueando(any());
    }

    @Test
    @DisplayName("No se puede usar esta pantalla para tocar la cuenta de un cliente")
    void soloPersonal() {
        Usuario cliente = new Usuario();
        cliente.setRol(Rol.CLIENTE);
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> servicio.desactivar(5, "admin")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.restablecerPassword(5, "Nueva-clave-1", "admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("Restablecer: clave escrita por el admin, debe cambiarla, se cierran sus sesiones y se desbloquea")
    void restablecer() {
        Usuario otro = cuenta(2, "rosa", Rol.OPERADOR, true);
        otro.setIntentosFallidos(4);
        otro.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));
        when(passwordEncoder.encode("Nueva-clave-1")).thenReturn("$2a$hash");

        var respuesta = servicio.restablecerPassword(2, "Nueva-clave-1", "admin");

        assertThat(respuesta.username()).isEqualTo("rosa");
        assertThat(otro.getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(otro.getDebeCambiarPassword()).isTrue();
        assertThat(otro.getPasswordCambiadoEn()).isNotNull(); // invalida sus tokens anteriores
        assertThat(otro.getIntentosFallidos()).isZero();
        assertThat(otro.getBloqueadoHasta()).isNull();
        verify(controlIntentos).desbloquearCuenta("rosa");
    }

    @Test
    @DisplayName("Un admin no restablece su propia contraseña desde aquí")
    void noRestableceLaPropia() {
        admin(1, "admin", true);
        assertThatThrownBy(() -> servicio.restablecerPassword(1, "Nueva-clave-1", "ADMIN"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    @DisplayName("Crear: usuario repetido da error; si no, queda con el rol elegido y debe cambiar la clave")
    void crear() {
        when(usuarioRepository.existsByUsername("rosa")).thenReturn(true);
        assertThatThrownBy(() -> servicio.crear(new CrearAdministradorRequest("Rosa", " rosa ", Rol.ADMIN, "Clave-segura-1")))
                .isInstanceOf(ReglaNegocioException.class);

        when(usuarioRepository.existsByUsername("lucho")).thenReturn(false);
        when(passwordEncoder.encode("Clave-segura-1")).thenReturn("$2a$otro");
        var respuesta = servicio.crear(new CrearAdministradorRequest("Lucho", "lucho", Rol.OPERADOR, "Clave-segura-1"));

        assertThat(respuesta.username()).isEqualTo("lucho");
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getRol()).isEqualTo(Rol.OPERADOR);
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("$2a$otro");
        assertThat(guardado.getValue().getDebeCambiarPassword()).isTrue();
        assertThat(guardado.getValue().getCliente()).isNull();
    }

    @Test
    @DisplayName("Desde la pestaña Admins no se crean cuentas de cliente")
    void noCreaClientes() {
        assertThatThrownBy(() -> servicio.crear(new CrearAdministradorRequest("X", "xx1", Rol.CLIENTE, "Clave-segura-1")))
                .isInstanceOf(ReglaNegocioException.class);
        verify(usuarioRepository, never()).save(any());
    }
}
