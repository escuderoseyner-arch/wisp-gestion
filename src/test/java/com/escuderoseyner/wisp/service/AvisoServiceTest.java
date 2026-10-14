package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.AvisoResponse;
import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import com.escuderoseyner.wisp.security.TokenRed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvisoServiceTest {

    private static final String TOKEN = "TokenDeAvisoDePrueba_1234567890abc";

    @Mock
    private ColaRepository colaRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ConfiguracionService configuracionService;

    @Mock
    private SincronizacionService sincronizacionService;

    @InjectMocks
    private AvisoService servicio;

    private Red red;
    private Cliente cliente;
    private Cola cola;

    @BeforeEach
    void preparar() {
        red = new Red();
        red.setId(1);
        Plan plan = new Plan();
        plan.setPrecio(new BigDecimal("79.00"));
        cliente = new Cliente();
        cliente.setId(7);
        cliente.setCodigo("CP-07");
        cliente.setNombres("María Demo");
        cliente.setPlan(plan);
        cliente.setDiaPago(Math.min(LocalDate.now().getDayOfMonth(), 28)); // hoy es su día de pago: aviso suave
        cliente.setFechaInicio(LocalDate.now().withDayOfMonth(1));
        cliente.setEstado(EstadoCliente.ACTIVO);
        cliente.setRed(red);
        cliente.setNombreCola("CP-07");
        cola = new Cola();
        cola.setRed(red);
        cola.setNombre("CP-07");
        cola.setCliente(cliente);

        when(colaRepository.findByPortalTokenHash(anyString())).thenReturn(Optional.empty());
        when(colaRepository.findByPortalTokenHash(TokenRed.hash(TOKEN))).thenReturn(Optional.of(cola));
        when(pagoRepository.findByClienteIdOrderByPeriodoDesc(7)).thenReturn(List.of());
        when(configuracionService.obtener()).thenReturn(new ConfiguracionResponse("Empresa Demo", null, null,
                "900000000", "51", "900000000", "Empresa Demo", null, 3, "Hola", "S/",
                "Suave {nombre}", "Vencido {nombre}", "Suspendido {nombre}"));
    }

    @Test
    @DisplayName("Con el token de su cola: muestra SUS datos, lo que debe y el texto del aviso suave")
    void muestraSoloSusDatos() {
        AvisoResponse aviso = servicio.ver(TOKEN);

        assertThat(aviso.etapa()).isEqualTo(AvisoResponse.Etapa.SUAVE);
        assertThat(aviso.nombre()).isEqualTo("María Demo");
        assertThat(aviso.meses()).hasSize(1);
        assertThat(aviso.monto()).isEqualByComparingTo("79.00");
        assertThat(aviso.texto()).isEqualTo("Suave {nombre}");
        assertThat(aviso.puedeEntendido()).isTrue();
    }

    @Test
    @DisplayName("Token inventado o con otro formato: 404, sin datos de nadie")
    void tokenInvalido() {
        assertThatThrownBy(() -> servicio.ver("OtroTokenQueNoExiste_1234567890"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.ver("CP-07")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.entendido("CP-07")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("La IP ya no es de un cliente vigente (retirado): página genérica sin nombre ni deuda")
    void retiradoSinDatos() {
        cliente.setEstado(EstadoCliente.RETIRADO);

        AvisoResponse aviso = servicio.ver(TOKEN);

        assertThat(aviso.etapa()).isEqualTo(AvisoResponse.Etapa.SUSPENDIDO);
        assertThat(aviso.nombre()).isNull();
        assertThat(aviso.codigo()).isNull();
        assertThat(aviso.meses()).isEmpty();
    }

    @Test
    @DisplayName("Suspendido: ve \"Servicio suspendido\" y no puede tocar \"Entendido\"")
    void suspendido() {
        cliente.setEstado(EstadoCliente.SUSPENDIDO);

        AvisoResponse aviso = servicio.ver(TOKEN);
        assertThat(aviso.etapa()).isEqualTo(AvisoResponse.Etapa.SUSPENDIDO);
        assertThat(aviso.puedeEntendido()).isFalse();
        assertThatThrownBy(() -> servicio.entendido(TOKEN)).isInstanceOf(ReglaNegocioException.class);
        verify(sincronizacionService, never()).recalcularRed(any(), any(), any());
    }

    @Test
    @DisplayName("\"Entendido\": lo pospone hasta mañana a las 8:00 y recalcula su red")
    void entendido() {
        LocalDateTime hasta = servicio.entendido(TOKEN);

        assertThat(hasta).isEqualTo(LocalDate.now().plusDays(1).atTime(8, 0));
        assertThat(cliente.getAvisoPospuestoHasta()).isEqualTo(hasta);
        verify(sincronizacionService).recalcularRed(any(), any(), any());
    }
}
