package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.RegistrarPagoRequest;
import com.escuderoseyner.wisp.dto.ResumenMesResponse;
import com.escuderoseyner.wisp.dto.RetiroCajaRequest;
import com.escuderoseyner.wisp.model.Caja;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MetodoPago;
import com.escuderoseyner.wisp.model.Pago;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.CajaMovimientoRepository;
import com.escuderoseyner.wisp.repository.CajaRepository;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.ConsumoMensualRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import com.escuderoseyner.wisp.security.AlcanceRedes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Un OPERADOR de Shiaunto (red 1) no puede ver ni registrar nada de Cuypirmarca (red 2).
// La regla se valida en los servicios (backend), no solo en la pantalla.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeparacionPorRedTest {

    private static final int SHIAUNTO = 1;
    private static final int CUYPIRMARCA = 2;

    @Mock private AlcanceRedes alcanceRedes;
    @Mock private ClienteRepository clienteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PlanService planService;
    @Mock private RedRepository redRepository;
    @Mock private SincronizacionService sincronizacionService;
    @Mock private PagoRepository pagoRepository;
    @Mock private ConfiguracionService configuracionService;
    @Mock private CajaService cajaServiceMock;
    @Mock private CajaRepository cajaRepository;
    @Mock private CajaMovimientoRepository movimientoRepository;
    @Mock private ColaRepository colaRepository;
    @Mock private AccionColaRepository accionColaRepository;
    @Mock private ConsumoMensualRepository consumoMensualRepository;
    @Mock private RedService redService;

    private ClienteService clienteService;
    private PagoService pagoService;
    private CajaService cajaService;
    private ColaService colaService;
    private PanelService panelService;

    private Cliente deShiaunto;
    private Cliente deCuypirmarca;
    private Cliente sinRed;

    @BeforeEach
    void preparar() {
        clienteService = new ClienteService(clienteRepository, usuarioRepository, planService, redRepository,
                sincronizacionService, alcanceRedes);
        pagoService = new PagoService(pagoRepository, clienteRepository, usuarioRepository, configuracionService,
                cajaServiceMock, alcanceRedes);
        cajaService = new CajaService(cajaRepository, movimientoRepository, usuarioRepository, pagoRepository,
                configuracionService, alcanceRedes);
        colaService = new ColaService(colaRepository, accionColaRepository, redRepository, clienteRepository,
                usuarioRepository, sincronizacionService, consumoMensualRepository, redService, alcanceRedes,
                pagoRepository, configuracionService);
        panelService = new PanelService(clienteRepository, pagoRepository, pagoService, configuracionService,
                alcanceRedes);

        deShiaunto = cliente(10, "C-10", red(SHIAUNTO, "Shiaunto"), "50.00");
        deCuypirmarca = cliente(20, "CP-01", red(CUYPIRMARCA, "Cuypirmarca"), "40.00");
        sinRed = cliente(30, "C-30", null, "30.00");

        when(alcanceRedes.actual()).thenReturn(Alcance.operador(Set.of(SHIAUNTO)));
        when(clienteRepository.findById(10)).thenReturn(Optional.of(deShiaunto));
        when(clienteRepository.findById(20)).thenReturn(Optional.of(deCuypirmarca));
        when(clienteRepository.findById(30)).thenReturn(Optional.of(sinRed));
        when(clienteRepository.buscar(any(), any(), any())).thenReturn(List.of(deShiaunto, deCuypirmarca, sinRed));
        when(clienteRepository.findParaResumenDelMes(any())).thenReturn(List.of(deShiaunto, deCuypirmarca, sinRed));
        when(configuracionService.moneda()).thenReturn("S/");
        when(configuracionService.diasTolerancia()).thenReturn(5);
    }

    // ---------- Clientes ----------

    @Test
    @DisplayName("La lista de clientes del operador solo trae los de su red (ni otra red ni sin red)")
    void listaSoloSuRed() {
        var lista = clienteService.listar(null, null, null, null);
        assertThat(lista).extracting("codigo").containsExactly("C-10");
    }

    @Test
    @DisplayName("Pedir por filtro o por URL un cliente de otra red da 403")
    void clienteDeOtraRed() {
        assertThatThrownBy(() -> clienteService.listar(null, null, null, CUYPIRMARCA))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> clienteService.detalle(20)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> clienteService.detalle(30)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("Un operador sin redes asignadas no ve ningún cliente")
    void operadorSinRedes() {
        when(alcanceRedes.actual()).thenReturn(Alcance.operador(Set.of()));
        assertThat(clienteService.listar(null, null, null, null)).isEmpty();
        assertThatThrownBy(() -> clienteService.detalle(10)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("El ADMIN ve todas las redes (y los clientes sin red) y puede filtrar por una")
    void adminVeTodo() {
        when(alcanceRedes.actual()).thenReturn(Alcance.admin());
        assertThat(clienteService.listar(null, null, null, null)).hasSize(3);
        assertThat(clienteService.listar(null, null, null, CUYPIRMARCA)).extracting("codigo").containsExactly("CP-01");
    }

    // ---------- Pagos ----------

    @Test
    @DisplayName("No puede registrar un pago de un cliente de otra red: 403 y no se guarda nada")
    void noRegistraPagoDeOtraRed() {
        var pedido = new RegistrarPagoRequest(20, List.of(YearMonth.now().toString()), new BigDecimal("40.00"),
                LocalDate.now(), MetodoPago.values()[0], null);
        assertThatThrownBy(() -> pagoService.registrar(pedido, "lucho")).isInstanceOf(AccesoDenegadoException.class);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No ve el estado de cuenta ni el resumen del mes de otra red")
    void noVePagosDeOtraRed() {
        assertThatThrownBy(() -> pagoService.estadoDeCuentaParaPersonal(20)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> pagoService.resumenDelMes((String) null, CUYPIRMARCA)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("El resumen del mes del operador solo suma cobrado y pendiente de su red")
    void totalesSoloDeSuRed() {
        YearMonth mes = YearMonth.now();
        when(pagoRepository.findDelPeriodo(mes.atDay(1))).thenReturn(List.of(pago(1, deShiaunto, "50.00"),
                pago(2, deCuypirmarca, "40.00")));

        ResumenMesResponse resumen = pagoService.resumenDelMes((String) null, null);

        assertThat(resumen.cobrado()).isEqualByComparingTo("50.00");
        assertThat(resumen.pendiente()).isEqualByComparingTo("0");
        assertThat(resumen.clientes()).extracting("codigo").containsExactly("C-10");
    }

    @Test
    @DisplayName("El panel de otra red da 403")
    void panelDeOtraRed() {
        assertThatThrownBy(() -> panelService.panel(CUYPIRMARCA)).isInstanceOf(AccesoDenegadoException.class);
    }

    // ---------- Caja y conexión ----------

    @Test
    @DisplayName("Solo ve la caja de su red; la de otra red da 403 (también al registrar un retiro)")
    void cajaDeOtraRed() {
        Caja cajaShiaunto = caja(1, deShiaunto.getRed());
        Caja cajaCuypirmarca = caja(2, deCuypirmarca.getRed());
        when(cajaRepository.findTodasConRed()).thenReturn(List.of(cajaShiaunto, cajaCuypirmarca));
        when(cajaRepository.findConRed(2)).thenReturn(Optional.of(cajaCuypirmarca));

        assertThat(cajaService.listar()).extracting("redNombre").containsExactly("Shiaunto");
        assertThatThrownBy(() -> cajaService.detalle(2)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> cajaService.historial(2, null)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> cajaService.registrarRetiro(2, new RetiroCajaRequest(new BigDecimal("10.00"),
                LocalDate.now(), "Prueba"), "lucho")).isInstanceOf(AccesoDenegadoException.class);
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No ve el estado de conexión de un cliente de otra red")
    void conexionDeOtraRed() {
        assertThatThrownBy(() -> colaService.deCliente(20)).isInstanceOf(AccesoDenegadoException.class);
    }

    // ---------- Utilidades ----------

    private static Red red(int id, String nombre) {
        Red red = new Red();
        red.setId(id);
        red.setNombre(nombre);
        return red;
    }

    private static Cliente cliente(int id, String codigo, Red red, String precio) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setNombre("Plan " + codigo);
        plan.setPrecio(new BigDecimal(precio));
        Cliente c = new Cliente();
        c.setId(id);
        c.setCodigo(codigo);
        c.setNombres("Cliente " + codigo);
        c.setPlan(plan);
        c.setRed(red);
        c.setDiaPago(28);
        c.setFechaInicio(LocalDate.now().withDayOfMonth(1));
        c.setEstado(EstadoCliente.ACTIVO);
        return c;
    }

    private static Pago pago(int id, Cliente cliente, String monto) {
        Pago p = new Pago();
        p.setId(id);
        p.setCliente(cliente);
        p.setPeriodo(YearMonth.now().atDay(1));
        p.setMonto(new BigDecimal(monto));
        p.setFechaPago(LocalDate.now());
        com.escuderoseyner.wisp.model.Usuario admin = new com.escuderoseyner.wisp.model.Usuario();
        admin.setNombreMostrar("Admin");
        p.setRegistradoPor(admin);
        return p;
    }

    private static Caja caja(int id, Red red) {
        Caja caja = new Caja();
        caja.setId(id);
        caja.setRed(red);
        return caja;
    }
}
