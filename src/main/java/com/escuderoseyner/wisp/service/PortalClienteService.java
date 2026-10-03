package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.dto.EstadoCuentaResponse;
import com.escuderoseyner.wisp.dto.EstadoMes;
import com.escuderoseyner.wisp.dto.MesResponse;
import com.escuderoseyner.wisp.dto.PortalClienteResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Rol;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

// Portal del cliente. REGLA DE SEGURIDAD: el cliente siempre se obtiene del username del token,
// nunca de un id que mande el navegador. Así un cliente no puede pedir los datos de otro.
@Service
public class PortalClienteService {

    private final UsuarioRepository usuarioRepository;
    private final PagoService pagoService;
    private final ConfiguracionService configuracionService;

    public PortalClienteService(UsuarioRepository usuarioRepository, PagoService pagoService,
                                ConfiguracionService configuracionService) {
        this.usuarioRepository = usuarioRepository;
        this.pagoService = pagoService;
        this.configuracionService = configuracionService;
    }

    @Transactional(readOnly = true)
    public PortalClienteResponse resumen(String usernameDelToken) {
        Cliente cliente = clienteDelUsuario(usernameDelToken);
        EstadoCuentaResponse cuenta = pagoService.estadoDeCuenta(cliente.getId());
        ConfiguracionResponse config = configuracionService.obtener();
        Plan plan = cliente.getPlan();
        BigDecimal precio = plan.getPrecio();

        List<PortalClienteResponse.MesPorPagar> vencidos = cuenta.meses().stream()
                .filter(m -> m.estado() == EstadoMes.VENCIDO)
                .sorted(Comparator.comparing(MesResponse::periodo))
                .map(m -> new PortalClienteResponse.MesPorPagar(m.periodo(), m.vence(), precio))
                .toList();

        // El mes impago más antiguo que todavía no vence
        PortalClienteResponse.MesPorPagar proximo = cuenta.mesesPorPagar().stream()
                .filter(m -> m.estado() == EstadoMes.PENDIENTE)
                .findFirst()
                .map(m -> new PortalClienteResponse.MesPorPagar(m.periodo(), m.vence(), precio))
                .orElse(null);

        return new PortalClienteResponse(
                cliente.getCodigo(),
                cliente.getNombres(),
                cliente.getEstado(),
                new PortalClienteResponse.PlanDelPortal(plan.getNombre(), plan.getBajadaMbps(), plan.getSubidaMbps(), precio),
                cuenta.moneda(),
                proximo,
                vencidos,
                cuenta.deuda(),
                cuenta.historial().stream()
                        .map(p -> new PortalClienteResponse.PagoDelPortal(p.periodo(), p.monto(), p.fechaPago(), p.metodo()))
                        .toList(),
                new PortalClienteResponse.DatosParaPagar(config.yapeNumero(), config.yapeTitular(), config.cuentaBancaria()),
                new PortalClienteResponse.Soporte(config.codigoPais(), config.whatsappSoporte()));
    }

    // Un usuario CLIENTE con su cliente; un RETIRADO ya no tiene acceso
    private Cliente clienteDelUsuario(String username) {
        Usuario usuario = usuarioRepository.findConClienteByUsername(username)
                .orElseThrow(CredencialesInvalidasException::new);
        Cliente cliente = usuario.getCliente();
        if (usuario.getRol() != Rol.CLIENTE || cliente == null || cliente.getEstado() == EstadoCliente.RETIRADO) {
            throw new CredencialesInvalidasException();
        }
        return cliente;
    }
}
