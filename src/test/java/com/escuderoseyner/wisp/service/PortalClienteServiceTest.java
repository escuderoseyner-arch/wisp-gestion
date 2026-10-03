package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.dto.EstadoCuentaResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// El portal solo puede devolver datos del cliente dueño del token
@ExtendWith(MockitoExtension.class)
class PortalClienteServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PagoService pagoService;

    @Mock
    private ConfiguracionService configuracionService;

    @InjectMocks
    private PortalClienteService portalClienteService;

    private Usuario usuarioCliente(String username, int clienteId, EstadoCliente estado) {
        Plan plan = new Plan();
        plan.setNombre("Estándar");
        plan.setBajadaMbps(15);
        plan.setSubidaMbps(5);
        plan.setPrecio(new BigDecimal("79.00"));

        Cliente cliente = new Cliente();
        cliente.setId(clienteId);
        cliente.setCodigo("C-01");
        cliente.setNombres("María Torres");
        cliente.setEstado(estado);
        cliente.setPlan(plan);
        cliente.setFechaInicio(LocalDate.of(2026, 7, 1));
        cliente.setDiaPago(27);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setRol(Rol.CLIENTE);
        usuario.setCliente(cliente);
        return usuario;
    }

    private EstadoCuentaResponse cuentaVacia(int clienteId) {
        return new EstadoCuentaResponse(
                new EstadoCuentaResponse.ClienteDelPago(clienteId, "C-01", "María Torres", "Estándar", EstadoCliente.ACTIVO),
                "S/", List.of(), List.of(), List.of(), null, new BigDecimal("79.00"), 0, BigDecimal.ZERO);
    }

    private ConfiguracionResponse configuracion() {
        return new ConfiguracionResponse("Empresa Demo", null, null, "900000000", "51", "900000000",
                "Empresa Demo", null, 3, "Hola {nombre}", "S/");
    }

    @Test
    @DisplayName("Usa el cliente del usuario del token y nunca otro")
    void usaElClienteDelToken() {
        when(usuarioRepository.findConClienteByUsername("900000001"))
                .thenReturn(Optional.of(usuarioCliente("900000001", 1, EstadoCliente.ACTIVO)));
        when(pagoService.estadoDeCuenta(1)).thenReturn(cuentaVacia(1));
        when(configuracionService.obtener()).thenReturn(configuracion());

        var resumen = portalClienteService.resumen("900000001");

        assertThat(resumen.codigo()).isEqualTo("C-01");
        verify(pagoService).estadoDeCuenta(1);
        verify(pagoService, never()).estadoDeCuenta(2);
    }

    @Test
    @DisplayName("Un SUSPENDIDO puede ver su portal")
    void suspendidoPuedeEntrar() {
        when(usuarioRepository.findConClienteByUsername("900000001"))
                .thenReturn(Optional.of(usuarioCliente("900000001", 1, EstadoCliente.SUSPENDIDO)));
        when(pagoService.estadoDeCuenta(1)).thenReturn(cuentaVacia(1));
        when(configuracionService.obtener()).thenReturn(configuracion());

        assertThat(portalClienteService.resumen("900000001").estado()).isEqualTo(EstadoCliente.SUSPENDIDO);
    }

    @Test
    @DisplayName("Un RETIRADO no puede ver su portal")
    void retiradoNoEntra() {
        when(usuarioRepository.findConClienteByUsername("900000001"))
                .thenReturn(Optional.of(usuarioCliente("900000001", 1, EstadoCliente.RETIRADO)));

        assertThatThrownBy(() -> portalClienteService.resumen("900000001"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verifyNoInteractions(pagoService);
    }

    @Test
    @DisplayName("Un ADMIN (sin cliente) no obtiene datos de ningún cliente")
    void adminSinCliente() {
        Usuario admin = new Usuario();
        admin.setUsername("admin");
        admin.setRol(Rol.ADMIN);
        when(usuarioRepository.findConClienteByUsername("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> portalClienteService.resumen("admin"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(pagoService, never()).estadoDeCuenta(anyInt());
    }

    @Test
    @DisplayName("Si el usuario del token ya no existe, no devuelve nada")
    void usuarioInexistente() {
        when(usuarioRepository.findConClienteByUsername("otro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portalClienteService.resumen("otro"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verifyNoInteractions(pagoService);
    }
}
