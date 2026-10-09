package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ColaClienteResponse;
import com.escuderoseyner.wisp.dto.ColaResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ColaService(ColaRepository colaRepository, AccionColaRepository accionColaRepository,
                       RedRepository redRepository, ClienteRepository clienteRepository,
                       UsuarioRepository usuarioRepository, SincronizacionService sincronizacionService) {
        this.colaRepository = colaRepository;
        this.accionColaRepository = accionColaRepository;
        this.redRepository = redRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.sincronizacionService = sincronizacionService;
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

    // ---------- Utilidades ----------

    private ColaClienteResponse aColaCliente(Cliente cliente) {
        Red red = cliente.getRed();
        ColaResponse cola = red == null || cliente.getNombreCola() == null ? null
                : colaRepository.findByRedIdAndNombreIgnoreCase(red.getId(), cliente.getNombreCola())
                .filter(c -> c.getCliente().getId().equals(cliente.getId()))
                .map(this::aResponse)
                .orElse(null);
        return new ColaClienteResponse(cliente.getCorteManual(), red == null ? null : red.getModo(), cola);
    }

    private ColaResponse aResponse(Cola c) {
        Cliente cliente = c.getCliente();
        boolean vigente = cliente.getEstado() != EstadoCliente.RETIRADO
                && cliente.getRed() != null && cliente.getRed().getId().equals(c.getRed().getId())
                && c.getNombre().equalsIgnoreCase(cliente.getNombreCola());

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
