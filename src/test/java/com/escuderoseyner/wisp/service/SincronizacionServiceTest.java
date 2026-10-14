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
import com.escuderoseyner.wisp.repository.PagoRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Mock
    private PagoRepository pagoRepository;

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

    // ---------- wisp-cortados ----------

    @Test
    @DisplayName("Activo: no va a wisp-cortados; suspendido o con corte manual: sí")
    void cortadaSegunEstado() {
        cliente(1, "C-01", EstadoCliente.ACTIVO);
        cliente(2, "C-02", EstadoCliente.SUSPENDIDO);
        Cliente manual = cliente(3, "C-03", EstadoCliente.ACTIVO);
        manual.setCorteManual(true);

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas).extracting(Cola::getNombre, Cola::getCortada)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("C-01", false),
                        org.assertj.core.groups.Tuple.tuple("C-02", true), org.assertj.core.groups.Tuple.tuple("C-03", true));
    }

    @Test
    @DisplayName("Retirado: su cola se deshabilita y su IP va a wisp-cortados (con una acción en modo Control)")
    void retiradoQuedaCortado() {
        Cliente c = cliente(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        c.setEstado(EstadoCliente.RETIRADO);
        clientes.clear();
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colas.getFirst().getDeshabilitada()).isTrue();
        assertThat(colas.getFirst().getCortada()).isTrue();
        verify(accionColaRepository, org.mockito.Mockito.times(2)).save(any(AccionCola.class));
    }

    @Test
    @DisplayName("Retirado cuya IP ya usa un cliente vigente de la red: no se corta (no bloquea a la persona nueva)")
    void retiradoConIpReutilizadaNoSeCorta() {
        Cliente anterior = cliente(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        anterior.setEstado(EstadoCliente.RETIRADO);
        clientes.clear();
        Cliente nuevo = cliente(2, "C-05", EstadoCliente.ACTIVO);
        nuevo.setIp(anterior.getIp());
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        Cola vieja = colas.stream().filter(c -> c.getNombre().equals("C-01")).findFirst().orElseThrow();
        Cola nueva = colas.stream().filter(c -> c.getNombre().equals("C-05")).findFirst().orElseThrow();
        assertThat(vieja.getDeshabilitada()).isTrue();
        assertThat(vieja.getCortada()).isFalse();
        assertThat(nueva.getCortada()).isFalse();
    }

    @Test
    @DisplayName("Diferencias de wisp-cortados: solo si el router ya informa la lista")
    void diferenciasCortada() {
        Cola cola = new Cola();
        cola.setTarget("192.168.1.11/32");
        cola.setMaxLimit("5M/15M");
        cola.setParent("Total-Clientes");
        cola.setTipoCola("fq-codel-up/fq-codel-down");
        cola.setComentario("Maria Demo");
        cola.setDeshabilitada(true);
        cola.setCortada(true);
        cola.setRepExiste(true);
        cola.setRepTarget("192.168.1.11/32");
        cola.setRepMaxLimit("5M/15M");
        cola.setRepParent("Total-Clientes");
        cola.setRepTipoCola("fq-codel-up/fq-codel-down");
        cola.setRepComentario("Maria Demo");
        cola.setRepDeshabilitada(true);

        assertThat(SincronizacionService.diferencias(cola)).isEmpty(); // script anterior: rep_cortada null
        cola.setRepCortada(false);
        assertThat(SincronizacionService.diferencias(cola)).containsExactly("Su IP debería estar en wisp-cortados (bloqueada)");
        cola.setRepCortada(true);
        assertThat(SincronizacionService.diferencias(cola)).isEmpty();
        cola.setAviso(true);
        cola.setRepAviso(false);
        assertThat(SincronizacionService.diferencias(cola)).containsExactly("Su IP debería estar en wisp-aviso (aviso de pago)");
    }

    // ---------- Aviso de pago por Wi-Fi ----------

    // Activo desde este mes, paga el día 1: hoy ya llegó su día de pago
    private Cliente clienteParaAviso(int id, String cola, EstadoCliente estado) {
        Cliente c = cliente(id, cola, estado);
        c.setDiaPago(1);
        c.setFechaInicio(LocalDate.now().withDayOfMonth(1));
        return c;
    }

    private Cola colaDe(String nombre) {
        return colas.stream().filter(c -> c.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Aviso: con la red activa, el que no pagó y ya pasó su día de pago va a wisp-aviso y al proxy")
    void avisoAlQueNoPago() {
        red.setAvisoActivo(true);
        clienteParaAviso(1, "C-01", EstadoCliente.ACTIVO);
        Cliente pagado = clienteParaAviso(2, "C-02", EstadoCliente.ACTIVO);
        when(pagoRepository.findClienteYPeriodoDeRed(1))
                .thenReturn(List.<Object[]>of(new Object[]{pagado.getId(), LocalDate.now().withDayOfMonth(1)}));

        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colaDe("C-01").getAviso()).isTrue();
        assertThat(colaDe("C-01").getPortal()).isTrue();
        assertThat(colaDe("C-02").getAviso()).isFalse();
        assertThat(colaDe("C-02").getPortal()).isFalse();
    }

    @Test
    @DisplayName("Aviso: nunca con la red desactivada, \"No mostrar aviso\", \"Entendido\" vigente ni IP .1 a .10")
    void sinAviso() {
        clienteParaAviso(1, "C-01", EstadoCliente.ACTIVO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);
        assertThat(colaDe("C-01").getAviso()).isFalse(); // red desactivada

        red.setAvisoActivo(true);
        clienteParaAviso(2, "C-02", EstadoCliente.ACTIVO).setSinAviso(true);
        clienteParaAviso(3, "C-03", EstadoCliente.ACTIVO).setAvisoPospuestoHasta(LocalDateTime.now().plusHours(2));
        clienteParaAviso(4, "C-04", EstadoCliente.ACTIVO).setIp("192.168.10.9");
        clienteParaAviso(5, "C-05", EstadoCliente.ACTIVO).setAvisoPospuestoHasta(LocalDateTime.now().minusMinutes(1));
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        assertThat(colaDe("C-01").getAviso()).isTrue();
        assertThat(colaDe("C-02").getAviso()).isFalse();
        assertThat(colaDe("C-03").getAviso()).isFalse();
        assertThat(colaDe("C-04").getAviso()).isFalse();
        assertThat(colaDe("C-04").getPortal()).isFalse();
        assertThat(colaDe("C-05").getAviso()).isTrue(); // el "Entendido" ya venció
    }

    @Test
    @DisplayName("Suspendido: sin aviso de pago, pero con regla en el proxy (ve \"Servicio suspendido\"); al apagar el aviso se quita")
    void suspendidoConPortal() {
        red.setAvisoActivo(true);
        clienteParaAviso(1, "C-01", EstadoCliente.SUSPENDIDO);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);

        Cola cola = colaDe("C-01");
        assertThat(cola.getCortada()).isTrue();
        assertThat(cola.getAviso()).isFalse();
        assertThat(cola.getPortal()).isTrue();

        cola.setPortalTokenHash("a".repeat(64));
        red.setAvisoActivo(false);
        servicio.recalcularRed(red, MotivoAccion.CAMBIO, null);
        assertThat(cola.getCortada()).isTrue(); // el corte sigue igual
        assertThat(cola.getPortal()).isFalse();
        assertThat(cola.getPortalTokenHash()).isNull(); // su página deja de abrir al instante
    }
}
