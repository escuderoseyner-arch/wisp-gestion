package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.AccionCola;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.ModoRed;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SincronizacionServiceTest {

    @Mock
    private ColaRepository colaRepository;

    @Mock
    private AccionColaRepository accionColaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private RedRepository redRepository;

    @InjectMocks
    private SincronizacionService servicio;

    private Red red;
    private final List<Cola> colas = new ArrayList<>();
    private final List<Cliente> clientes = new ArrayList<>();
    private int siguienteIdCola = 100;

    @BeforeEach
    void preparar() {
        red = new Red();
        red.setId(1);
        red.setNombre("Red Demo");
        red.setColaPadre("Total-Clientes");
        red.setColasProtegidas("Oficina");
        red.setModo(ModoRed.CONTROL);
        when(colaRepository.findByRedId(1)).thenReturn(colas);
        when(clienteRepository.findVigentesDeRed(1)).thenReturn(clientes);
        when(colaRepository.save(any(Cola.class))).thenAnswer(inv -> {
            Cola c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(siguienteIdCola++);
                colas.add(c);
            }
            return c;
        });
        when(accionColaRepository.findByColaIdAndEstadoIn(any(), any())).thenReturn(List.of());
    }

    private Cliente cliente(int id, String cola, EstadoCliente estado) {
        Plan plan = new Plan();
        plan.setSubidaMbps(5);
        plan.setBajadaMbps(15);
        Cliente c = new Cliente();
        c.setId(id);
        c.setCodigo(cola);
        c.setNombres("María Demo");
        c.setIp("192.168.1." + (10 + id));
        c.setPlan(plan);
        c.setEstado(estado);
        c.setRed(red);
        c.setNombreCola(cola);
        if (estado != EstadoCliente.RETIRADO) {
            clientes.add(c);
        }
        return c;
    }

    @Test
    @DisplayName("Cliente activo: cola habilitada con su plan, la cola padre y fq-codel, y una acción en modo Control")
    void colaDeClienteActivo() {
        cliente(1, "C-01", EstadoCliente.ACTIVO);

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        Cola cola = colas.getFirst();
        assertThat(cola.getTarget()).isEqualTo("192.168.1.11/32");
        assertThat(cola.getMaxLimit()).isEqualTo("5M/15M");
        assertThat(cola.getParent()).isEqualTo("Total-Clientes");
        assertThat(cola.getTipoCola()).isEqualTo("fq-codel-up/fq-codel-down");
        assertThat(cola.getComentario()).isEqualTo("Maria Demo");
        assertThat(cola.getDeshabilitada()).isFalse();
        verify(accionColaRepository).save(any(AccionCola.class));
    }

    @Test
    @DisplayName("Suspendido o con corte manual: cola deshabilitada")
    void suspendidoOCortado() {
        cliente(1, "C-01", EstadoCliente.SUSPENDIDO);
        Cliente cortado = cliente(2, "C-02", EstadoCliente.ACTIVO);
        cortado.setCorteManual(true);

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas).allMatch(Cola::getDeshabilitada);
    }

    @Test
    @DisplayName("En Solo lectura se guarda el estado deseado pero NO se crea ninguna acción")
    void soloLecturaSinAcciones() {
        red.setModo(ModoRed.SOLO_LECTURA);
        cliente(1, "C-01", EstadoCliente.ACTIVO);

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas).hasSize(1);
        verify(accionColaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin cambios no se crean acciones nuevas")
    void sinCambiosNoHayAccion() {
        cliente(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        int cambios = servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(cambios).isZero();
        verify(accionColaRepository).save(any(AccionCola.class)); // solo la primera vez
    }

    @Test
    @DisplayName("Una cola que se queda sin dueño vigente se deshabilita, no se borra")
    void colaSinDuenoSeDeshabilita() {
        Cliente c = cliente(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        c.setEstado(EstadoCliente.RETIRADO);
        clientes.clear();
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas).hasSize(1);
        assertThat(colas.getFirst().getDeshabilitada()).isTrue();
        verify(colaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Nunca se crea ni se toca una cola con el nombre de la cola padre o una protegida")
    void colasProtegidas() {
        cliente(1, "Total-Clientes", EstadoCliente.ACTIVO);
        cliente(2, "oficina", EstadoCliente.ACTIVO);

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas).isEmpty();
        verify(accionColaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una acción nueva reemplaza a la que aún no se confirmaba")
    void accionReemplazaPendiente() {
        Cliente c = cliente(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);
        AccionCola anterior = new AccionCola();
        anterior.setEstado(EstadoAccion.ENVIADA);
        when(accionColaRepository.findByColaIdAndEstadoIn(any(), any())).thenReturn(List.of(anterior));

        c.setCorteManual(true);
        servicio.recalcularRed(red, MotivoAccion.CORTE, null);

        assertThat(anterior.getEstado()).isEqualTo(EstadoAccion.REEMPLAZADA);
        ArgumentCaptor<AccionCola> captor = ArgumentCaptor.forClass(AccionCola.class);
        verify(accionColaRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getMotivo()).isEqualTo(MotivoAccion.CORTE);
    }

    @Test
    @DisplayName("Corte manual: no se permite en modo Solo lectura")
    void corteEnSoloLectura() {
        red.setModo(ModoRed.SOLO_LECTURA);
        Cliente c = cliente(1, "C-01", EstadoCliente.ACTIVO);

        assertThatThrownBy(() -> servicio.cortarOReconectar(c, true, null))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Solo lectura");
        assertThat(c.getCorteManual()).isFalse();
    }

    @Test
    @DisplayName("Diferencias: velocidad igual en otro formato no cuenta; deshabilitada distinta sí")
    void diferencias() {
        Cola cola = new Cola();
        cola.setTarget("192.168.1.11/32");
        cola.setMaxLimit("5M/15M");
        cola.setParent("Total-Clientes");
        cola.setTipoCola("fq-codel-up/fq-codel-down");
        cola.setComentario("Maria Demo");
        cola.setDeshabilitada(true);
        cola.setRepExiste(true);
        cola.setRepTarget("192.168.1.11/32");
        cola.setRepMaxLimit("5000000/15000000");
        cola.setRepParent("Total-Clientes");
        cola.setRepTipoCola("fq-codel-up/fq-codel-down");
        cola.setRepComentario("Maria Demo");
        cola.setRepDeshabilitada(false);

        assertThat(SincronizacionService.diferencias(cola)).containsExactly("Debería estar deshabilitada");
    }
}
