package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ColaClienteResponse;
import com.escuderoseyner.wisp.dto.ColaResponse;
import com.escuderoseyner.wisp.dto.PanelRedResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.ConsumoMensualRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Lo que el ADMIN ve y hace con las colas: listado con diferencias, corte/reconexión y "aplicar diferencias"
@Service
public class ColaService {

    private final ColaRepository colaRepository;
    private final AccionColaRepository accionColaRepository;
    private final RedRepository redRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final SincronizacionService sincronizacionService;
    private final ConsumoMensualRepository consumoMensualRepository;
    private final RedService redService;

    public ColaService(ColaRepository colaRepository, AccionColaRepository accionColaRepository,
                       RedRepository redRepository, ClienteRepository clienteRepository,
                       UsuarioRepository usuarioRepository, SincronizacionService sincronizacionService,
                       ConsumoMensualRepository consumoMensualRepository, RedService redService) {
        this.colaRepository = colaRepository;
        this.accionColaRepository = accionColaRepository;
        this.redRepository = redRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.sincronizacionService = sincronizacionService;
        this.consumoMensualRepository = consumoMensualRepository;
        this.redService = redService;
    }

    @Transactional
    public List<ColaResponse> listarDeRed(Integer redId) {
        Red red = buscarRed(redId);
        // Así el listado muestra el estado deseado al día aunque el MikroTik aún no consulte
        sincronizacionService.recalcularRed(red, MotivoAccion.CAMBIO, null);
        return colaRepository.findByRedId(redId).stream().map(this::aResponse).toList();
    }

    @Transactional
    public int aplicarDiferencias(Integer redId, String usernameAdmin) {
        return sincronizacionService.aplicarDiferencias(buscarRed(redId), admin(usernameAdmin));
    }

    @Transactional(readOnly = true)
    public ColaClienteResponse deCliente(Integer clienteId) {
        return aColaCliente(buscarCliente(clienteId));
    }

    @Transactional
    public ColaClienteResponse cortarOReconectar(Integer clienteId, boolean cortar, String usernameAdmin) {
        Cliente cliente = buscarCliente(clienteId);
        sincronizacionService.cortarOReconectar(cliente, cortar, admin(usernameAdmin));
        return aColaCliente(cliente);
    }

    // ---------- Panel de la red ----------

    @Transactional
    public PanelRedResponse panel(Integer redId) {
        Red red = buscarRed(redId);
        sincronizacionService.recalcularRed(red, MotivoAccion.CAMBIO, null);
        LocalDateTime ahora = LocalDateTime.now();

        int colas = 0;
        int conectados = 0;
        int sinDatos = 0;
        List<PanelRedResponse.ClienteSinConexion> sinConexion = new ArrayList<>();
        List<PanelRedResponse.ColaConDiferencias> diferencias = new ArrayList<>();
        for (Cola c : colaRepository.findByRedId(redId)) {
            Cliente cliente = c.getCliente();
            boolean vigente = esDuenoVigente(c);
            List<String> difs = SincronizacionService.diferencias(c);
            if (!difs.isEmpty()) {
                diferencias.add(new PanelRedResponse.ColaConDiferencias(cliente.getId(), cliente.getCodigo(),
                        cliente.getNombres(), c.getNombre(), vigente, difs));
            }
            if (!vigente) {
                continue;
            }
            colas++;
            switch (conexion(c, red, ahora)) {
                case CONECTADO -> conectados++;
                case SIN_DATOS -> sinDatos++;
                case SIN_CONEXION -> sinConexion.add(new PanelRedResponse.ClienteSinConexion(cliente.getId(),
                        cliente.getCodigo(), cliente.getNombres(), c.getNombre()));
            }
        }

        LocalDate periodo = ahora.toLocalDate().withDayOfMonth(1);
        Object[] suma = consumoMensualRepository.sumarPorRed(redId, periodo).getFirst();
        boolean enLinea = red.getUltimaConexion() != null && !red.getUltimaConexion().isBefore(ahora.minus(margen(red)));

        return new PanelRedResponse(redService.detalle(redId), enLinea, colas, conectados, sinDatos, sinConexion,
                new ColaClienteResponse.ConsumoMes(periodo, ((Number) suma[0]).longValue(), ((Number) suma[1]).longValue()),
                diferencias,
                accionColaRepository.contarPorRedYEstados(redId, SincronizacionService.POR_ENVIAR),
                accionColaRepository.contarPorRedYEstados(redId, List.of(EstadoAccion.ERROR)));
    }

    // ---------- Utilidades ----------

    // Si el último reporte es más viejo que esto, no se sabe si el cliente está conectado
    private static Duration margen(Red red) {
        Duration tresIntervalos = Duration.ofSeconds(3L * red.getIntervaloSegundos());
        Duration minimo = Duration.ofMinutes(5);
        return tresIntervalos.compareTo(minimo) > 0 ? tresIntervalos : minimo;
    }

    static ColaClienteResponse.Conexion conexion(Cola c, Red red, LocalDateTime ahora) {
        if (c.getReportadoEn() == null || c.getReportadoEn().isBefore(ahora.minus(margen(red)))) {
            return ColaClienteResponse.Conexion.SIN_DATOS;
        }
        return Boolean.TRUE.equals(c.getPingOk())
                ? ColaClienteResponse.Conexion.CONECTADO
                : ColaClienteResponse.Conexion.SIN_CONEXION;
    }

    private ColaClienteResponse aColaCliente(Cliente cliente) {
        Red red = cliente.getRed();
        Cola cola = red == null || cliente.getNombreCola() == null ? null
                : colaRepository.findByRedIdAndNombreIgnoreCase(red.getId(), cliente.getNombreCola())
                .filter(c -> c.getCliente().getId().equals(cliente.getId()))
                .orElse(null);

        LocalDate periodo = LocalDate.now().withDayOfMonth(1);
        ColaClienteResponse.ConsumoMes consumo = consumoMensualRepository
                .findByClienteIdAndPeriodo(cliente.getId(), periodo)
                .map(c -> new ColaClienteResponse.ConsumoMes(periodo, c.getBytesSubida(), c.getBytesBajada()))
                .orElse(new ColaClienteResponse.ConsumoMes(periodo, 0, 0));

        ColaResponse.UltimaAccion ultimoCorte = cola == null ? null
                : accionColaRepository.findFirstByColaIdAndMotivoInOrderByIdDesc(cola.getId(),
                        List.of(MotivoAccion.CORTE, MotivoAccion.RECONEXION))
                .map(a -> new ColaResponse.UltimaAccion(a.getId(), a.getMotivo(), a.getEstado(), a.getError(),
                        a.getCreadoEn(), a.getResueltaEn()))
                .orElse(null);

        return new ColaClienteResponse(cliente.getCorteManual(), red == null ? null : red.getModo(),
                red == null ? null : red.getNombre(),
                cola == null ? null : conexion(cola, red, LocalDateTime.now()),
                cola == null ? null : aResponse(cola), ultimoCorte, consumo);
    }

    private static boolean esDuenoVigente(Cola c) {
        Cliente cliente = c.getCliente();
        return cliente.getEstado() != EstadoCliente.RETIRADO
                && cliente.getRed() != null && cliente.getRed().getId().equals(c.getRed().getId())
                && c.getNombre().equalsIgnoreCase(cliente.getNombreCola());
    }

    private ColaResponse aResponse(Cola c) {
        Cliente cliente = c.getCliente();
        boolean vigente = esDuenoVigente(c);

        ColaResponse.Reportado reportado = c.getRepExiste() == null ? null
                : new ColaResponse.Reportado(c.getRepExiste(), c.getRepTarget(), c.getRepMaxLimit(), c.getRepParent(),
                c.getRepTipoCola(), c.getRepComentario(), c.getRepDeshabilitada(), c.getRateSubidaBps(),
                c.getRateBajadaBps(), c.getPingOk(), c.getReportadoEn());

        ColaResponse.UltimaAccion ultima = accionColaRepository.findFirstByColaIdOrderByIdDesc(c.getId())
                .map(a -> new ColaResponse.UltimaAccion(a.getId(), a.getMotivo(), a.getEstado(), a.getError(),
                        a.getCreadoEn(), a.getResueltaEn()))
                .orElse(null);

        return new ColaResponse(c.getId(), c.getNombre(),
                new ColaResponse.ClienteDeCola(cliente.getId(), cliente.getCodigo(), cliente.getNombres(), vigente),
                new ColaResponse.Deseado(c.getTarget(), c.getMaxLimit(), c.getParent(), c.getTipoCola(),
                        c.getComentario(), c.getDeshabilitada(), c.getDeseadoEn()),
                reportado, SincronizacionService.diferencias(c), ultima);
    }

    private Red buscarRed(Integer id) {
        return redRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una red con el id " + id + "."));
    }

    private Cliente buscarCliente(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con el id " + id + "."));
    }

    private Usuario admin(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró tu usuario."));
    }
}
