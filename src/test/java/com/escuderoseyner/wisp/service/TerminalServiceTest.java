package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.ComandoTerminal;
import com.escuderoseyner.wisp.model.EstadoComando;
import com.escuderoseyner.wisp.model.ModoRed;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ComandoTerminalRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminalServiceTest {

    @Mock
    private ComandoTerminalRepository comandoRepository;

    @Mock
    private RedRepository redRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private TerminalService servicio;

    private Red red;

    @BeforeEach
    void preparar() {
        red = new Red();
        red.setId(1);
        red.setNombre("Red Demo");
        red.setModo(ModoRed.CONTROL);
        when(redRepository.findById(1)).thenReturn(Optional.of(red));
        Usuario admin = new Usuario();
        admin.setUsername("admin");
        admin.setNombreMostrar("Admin Demo");
        when(usuarioRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(comandoRepository.findByRedIdAndEstadoOrderByIdAsc(any(), any())).thenReturn(List.of());
    }

    private ComandoTerminal comando(long id, EstadoComando estado, Red deRed) {
        ComandoTerminal c = new ComandoTerminal();
        c.setId(id);
        c.setRed(deRed);
        c.setEstado(estado);
        c.setComando("/system resource print");
        c.setExpiraEn(LocalDateTime.now().plusMinutes(10));
        c.setEnviadoEn(LocalDateTime.now());
        when(comandoRepository.findById(id)).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    @DisplayName("En Solo lectura no se encola ningún comando")
    void soloLectura() {
        red.setModo(ModoRed.SOLO_LECTURA);
        assertThatThrownBy(() -> servicio.encolar(1, "/system resource print", "admin"))
                .isInstanceOf(ReglaNegocioException.class);
        verify(comandoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No se aceptan comandos que mencionen fasttrack")
    void sinFasttrack() {
        assertThatThrownBy(() -> servicio.encolar(1, "/ip firewall filter add action=FastTrack-connection", "admin"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("fasttrack");
        verify(comandoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un comando válido queda PENDIENTE y con fecha de expiración")
    void encolaPendiente() {
        when(comandoRepository.save(any(ComandoTerminal.class))).thenAnswer(inv -> inv.getArgument(0));
        var respuesta = servicio.encolar(1, "  /system resource print  ", "admin");
        assertThat(respuesta.estado()).isEqualTo(EstadoComando.PENDIENTE);
        assertThat(respuesta.comando()).isEqualTo("/system resource print");
        assertThat(respuesta.expiraEn()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Al enviarse al MikroTik pasa a ENVIADO y ya no vuelve a salir como pendiente")
    void seEnviaUnaSolaVez() {
        ComandoTerminal c = comando(5, EstadoComando.PENDIENTE, red);
        when(comandoRepository.findByRedIdAndEstadoOrderByIdAsc(1, EstadoComando.PENDIENTE)).thenReturn(List.of(c));

        assertThat(servicio.tomarParaEnviar(red)).containsExactly(c);
        assertThat(c.getEstado()).isEqualTo(EstadoComando.ENVIADO);
    }

    @Test
    @DisplayName("Un pendiente vencido expira y no se envía")
    void expira() {
        ComandoTerminal c = comando(6, EstadoComando.PENDIENTE, red);
        c.setExpiraEn(LocalDateTime.now().minusSeconds(1));
        when(comandoRepository.findByRedIdAndEstadoOrderByIdAsc(1, EstadoComando.PENDIENTE))
                .thenReturn(List.of(c)).thenReturn(List.of());

        assertThat(servicio.tomarParaEnviar(red)).isEmpty();
        assertThat(c.getEstado()).isEqualTo(EstadoComando.EXPIRADO);
    }

    @Test
    @DisplayName("Un MikroTik no puede leer ni responder comandos de otra red")
    void otraRed() {
        Red otra = new Red();
        otra.setId(2);
        comando(7, EstadoComando.ENVIADO, otra);

        assertThatThrownBy(() -> servicio.textoParaEjecutar(red, 7L)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.recibirResultado(red, 7L, true, "x")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("La salida se guarda con tope de 16 KB y se marca truncada")
    void salidaTruncada() {
        ComandoTerminal c = comando(8, EstadoComando.ENVIADO, red);
        servicio.recibirResultado(red, 8L, true, "a".repeat(TerminalService.MAX_SALIDA + 10));

        assertThat(c.getEstado()).isEqualTo(EstadoComando.EJECUTADO);
        assertThat(c.getSalida()).hasSize(TerminalService.MAX_SALIDA);
        assertThat(c.getSalidaTruncada()).isTrue();
    }
}
