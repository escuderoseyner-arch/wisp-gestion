package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.AccionCola;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoAccion;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.ModoRed;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.AccionColaRepository;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Calcula el ESTADO DESEADO de cada cola y genera las acciones para el MikroTik.
//
// Reglas:
//  - Una cola por cliente vigente de la red: target = IP/32, max-limit = subida/bajada del plan,
//    parent = cola padre, tipo fq-codel, comentario = nombre, deshabilitada si no está ACTIVO o tiene corte manual.
//  - "cortada" (su IP va a la address-list wisp-cortados, que el firewall del router bloquea):
//    cliente SUSPENDIDO o con corte manual, y las colas sin dueño vigente (RETIRADOS).
//  - Una cola que se queda sin dueño vigente (retiro, cambio de nombre o de red) se DESHABILITA y se CORTA,
//    nunca se borra. Excepción: si su IP ya la usa un cliente vigente de la misma red, no se corta
//    (si no, se bloquearía a la persona nueva).
//  - "aviso" (su IP va a la address-list wisp-aviso): red con el aviso activo, cliente ACTIVO sin corte manual,
//    sin la casilla "No mostrar aviso", con un mes impago cuyo día de pago ya llegó y sin "Entendido" vigente.
//  - "portal" (su regla en el proxy que desvía su HTTP a la página de aviso): red con el aviso activo y
//    cola en aviso o cortada (los suspendidos ven "Servicio suspendido").
//  - Las IP terminadas en .1 a .10 (MikroTik e infraestructura) nunca tienen aviso ni portal.
//  - La cola padre y las protegidas nunca reciben acciones.
//  - En SOLO_LECTURA se guarda el estado deseado pero no se crean acciones.
@Service
public class SincronizacionService {

    public static final List<EstadoAccion> POR_ENVIAR = List.of(EstadoAccion.PENDIENTE, EstadoAccion.ENVIADA);

    private final ColaRepository colaRepository;
    private final AccionColaRepository accionColaRepository;
    private final ClienteRepository clienteRepository;
    private final RedRepository redRepository;
    private final PagoRepository pagoRepository;

    public SincronizacionService(ColaRepository colaRepository, AccionColaRepository accionColaRepository,
                                 ClienteRepository clienteRepository, RedRepository redRepository,
                                 PagoRepository pagoRepository) {
        this.colaRepository = colaRepository;
        this.accionColaRepository = accionColaRepository;
        this.clienteRepository = clienteRepository;
        this.redRepository = redRepository;
        this.pagoRepository = pagoRepository;
    }

    // ---------- Recalcular ----------

    // Después de crear, editar, suspender, reactivar, retirar o reasignar un cliente.
    // Recalcula su red actual y las redes donde tenía colas (por si cambió de red o de nombre de cola).
    @Transactional
    public void alCambiarCliente(Cliente cliente, MotivoAccion motivo, Usuario usuario) {
        Set<Red> redes = new LinkedHashSet<>();
        if (cliente.getRed() != null) {
            redes.add(cliente.getRed());
        }
        colaRepository.findByClienteId(cliente.getId()).forEach(cola -> redes.add(cola.getRed()));
        redes.forEach(red -> recalcularRed(red, motivo, usuario));
    }

    // Cambió la velocidad de un plan o la cola padre: se recalculan todas las redes
    @Transactional
    public void recalcularTodas() {
        redRepository.findAll().forEach(red -> recalcularRed(red, MotivoAccion.CAMBIO, null));
    }

    // Devuelve cuántas colas cambiaron
    @Transactional
    public int recalcularRed(Red red, MotivoAccion motivo, Usuario usuario) {
        Map<String, Cola> porNombre = new HashMap<>();
        for (Cola cola : colaRepository.findByRedId(red.getId())) {
            porNombre.put(clave(cola.getNombre()), cola);
        }

        // El aviso depende de la fecha: como el MikroTik consulta cada minuto, entra y sale solo
        LocalDateTime ahora = LocalDateTime.now();
        boolean avisoRed = Boolean.TRUE.equals(red.getAvisoActivo());
        Map<Integer, Set<YearMonth>> pagados = avisoRed
                ? AvisoWifi.agruparPagados(pagoRepository.findClienteYPeriodoDeRed(red.getId()))
                : Map.of();

        int cambios = 0;
        Set<Integer> conDueno = new HashSet<>();
        Set<String> targetsVigentes = new HashSet<>();
        for (Cliente cliente : clienteRepository.findVigentesDeRed(red.getId())) {
            String nombre = cliente.getNombreCola();
            // Defensa extra: la validación del cliente ya lo impide
            if (cliente.getIp() == null || nombre == null || RedService.esColaProtegida(red, nombre)) {
                continue;
            }
            Cola cola = porNombre.get(clave(nombre));
            boolean nueva = cola == null;
            if (nueva) {
                cola = new Cola();
                cola.setRed(red);
                cola.setNombre(nombre);
                porNombre.put(clave(nombre), cola);
            }
            cola.setCliente(cliente);
            boolean deshabilitada = cliente.getEstado() != EstadoCliente.ACTIVO || cliente.getCorteManual();
            String target = FormatoRouterOs.target(cliente.getIp());
            targetsVigentes.add(target);
            boolean ipPermitida = AvisoWifi.ipPermitida(cliente.getIp());
            boolean aviso = avisoRed && ipPermitida && !deshabilitada && !Boolean.TRUE.equals(cliente.getSinAviso())
                    && !AvisoWifi.pospuesto(cliente, ahora)
                    && !AvisoWifi.mesesEnAviso(cliente, pagados.getOrDefault(cliente.getId(), Set.of()),
                    ahora.toLocalDate()).isEmpty();
            boolean cambio = aplicarDeseado(cola,
                    target,
                    FormatoRouterOs.maxLimit(cliente.getPlan().getSubidaMbps(), cliente.getPlan().getBajadaMbps()),
                    red.getColaPadre(),
                    FormatoRouterOs.comentario(cliente.getNombres()),
                    deshabilitada,
                    deshabilitada, // suspendido o corte manual: también se bloquea
                    aviso,
                    avisoRed && ipPermitida && (aviso || deshabilitada));
            if (nueva || cambio) {
                colaRepository.save(cola);
                accionSiCorresponde(cola, motivo, usuario);
                cambios++;
            }
            conDueno.add(cola.getId());
        }

        // Colas sin dueño vigente (retirados): se deshabilitan y se cortan, salvo que su IP ya sea de un vigente
        for (Cola cola : porNombre.values()) {
            if (cola.getId() == null || conDueno.contains(cola.getId())) {
                continue;
            }
            boolean cortada = !targetsVigentes.contains(cola.getTarget());
            boolean portal = avisoRed && cortada && AvisoWifi.ipPermitida(cola.getTarget());
            if (aplicarDeseado(cola, cola.getTarget(), cola.getMaxLimit(), red.getColaPadre(), cola.getComentario(),
                    true, cortada, false, portal)) {
                accionSiCorresponde(cola, motivo, usuario);
                cambios++;
            }
        }
        return cambios;
    }

    // ---------- Corte manual ----------

    @Transactional
    public Cola cortarOReconectar(Cliente cliente, boolean cortar, Usuario usuario) {
        if (cliente.getEstado() == EstadoCliente.RETIRADO) {
            throw new ReglaNegocioException("El cliente está retirado: su cola ya está deshabilitada.");
        }
        Red red = cliente.getRed();
        if (red == null) {
            throw new ReglaNegocioException("El cliente no está asignado a ninguna red.");
        }
        if (red.getModo() != ModoRed.CONTROL) {
            throw new ReglaNegocioException("La red \"" + red.getNombre()
                    + "\" está en modo Solo lectura: la web no envía cambios al MikroTik.");
        }
        if (cliente.getCorteManual() == cortar) {
            throw new ReglaNegocioException(cortar ? "El cliente ya tiene un corte manual." : "El cliente no tiene un corte manual.");
        }
        cliente.setCorteManual(cortar);
        recalcularRed(red, cortar ? MotivoAccion.CORTE : MotivoAccion.RECONEXION, usuario);
        return colaRepository.findByRedIdAndNombreIgnoreCase(red.getId(), cliente.getNombreCola()).orElse(null);
    }

    // ---------- Aplicar diferencias ----------

    // Crea una acción para cada cola cuyo último reporte no coincide con la web.
    // Devuelve cuántas acciones se crearon.
    @Transactional
    public int aplicarDiferencias(Red red, Usuario usuario) {
        if (red.getModo() != ModoRed.CONTROL) {
            throw new ReglaNegocioException("La red \"" + red.getNombre()
                    + "\" está en modo Solo lectura: cámbiala a Control para aplicar diferencias.");
        }
        recalcularRed(red, MotivoAccion.CAMBIO, usuario);
        int creadas = 0;
        for (Cola cola : colaRepository.findByRedId(red.getId())) {
            if (!diferencias(cola).isEmpty()
                    && accionColaRepository.findByColaIdAndEstadoIn(cola.getId(), POR_ENVIAR).isEmpty()
                    && !RedService.esColaProtegida(red, cola.getNombre())) {
                crearAccion(cola, MotivoAccion.SINCRONIZACION, usuario);
                creadas++;
            }
        }
        return creadas;
    }

    // Lo que no coincide entre la web y el último reporte del MikroTik. Vacío si coincide o si aún no reporta.
    public static List<String> diferencias(Cola cola) {
        List<String> lista = new ArrayList<>();
        if (cola.getRepExiste() == null) {
            return lista;
        }
        if (!cola.getRepExiste()) {
            lista.add("No existe en el MikroTik");
            if (cola.getCortada() && Boolean.FALSE.equals(cola.getRepCortada())) {
                lista.add("Su IP debería estar en wisp-cortados (bloqueada)");
            }
            if (cola.getAviso() && Boolean.FALSE.equals(cola.getRepAviso())) {
                lista.add("Su IP debería estar en wisp-aviso (aviso de pago)");
            }
            return lista;
        }
        if (!FormatoRouterOs.mismoTarget(cola.getTarget(), cola.getRepTarget())) {
            lista.add("Target: web " + cola.getTarget() + ", MikroTik " + mostrar(cola.getRepTarget()));
        }
        if (!FormatoRouterOs.mismaVelocidad(cola.getMaxLimit(), cola.getRepMaxLimit())) {
            lista.add("Velocidad: web " + cola.getMaxLimit() + ", MikroTik " + mostrar(cola.getRepMaxLimit()));
        }
        if (cola.getRepParent() == null || !cola.getParent().equalsIgnoreCase(cola.getRepParent())) {
            lista.add("Cola padre: web " + cola.getParent() + ", MikroTik " + mostrar(cola.getRepParent()));
        }
        if (!cola.getTipoCola().equals(cola.getRepTipoCola())) {
            lista.add("Tipo de cola: web " + cola.getTipoCola() + ", MikroTik " + mostrar(cola.getRepTipoCola()));
        }
        if (!cola.getComentario().equals(cola.getRepComentario())) {
            lista.add("Comentario: web \"" + cola.getComentario() + "\", MikroTik \"" + mostrar(cola.getRepComentario()) + "\"");
        }
        if (!Objects.equals(cola.getDeshabilitada(), cola.getRepDeshabilitada())) {
            lista.add(cola.getDeshabilitada() ? "Debería estar deshabilitada" : "Debería estar habilitada");
        }
        // Solo si el script del router ya informa la lista (versión con wisp-cortados)
        if (cola.getRepCortada() != null && !Objects.equals(cola.getCortada(), cola.getRepCortada())) {
            lista.add(cola.getCortada() ? "Su IP debería estar en wisp-cortados (bloqueada)"
                    : "Su IP no debería estar en wisp-cortados");
        }
        if (cola.getRepAviso() != null && !Objects.equals(cola.getAviso(), cola.getRepAviso())) {
            lista.add(cola.getAviso() ? "Su IP debería estar en wisp-aviso (aviso de pago)"
                    : "Su IP no debería estar en wisp-aviso");
        }
        return lista;
    }

    // ---------- Internos ----------

    // true si cambió algo del estado deseado
    private boolean aplicarDeseado(Cola cola, String target, String maxLimit, String parent, String comentario,
                                   boolean deshabilitada, boolean cortada, boolean aviso, boolean portal) {
        boolean cambio = !Objects.equals(cola.getTarget(), target)
                || !Objects.equals(cola.getMaxLimit(), maxLimit)
                || !Objects.equals(cola.getParent(), parent)
                || !Objects.equals(cola.getTipoCola(), FormatoRouterOs.TIPO_COLA)
                || !Objects.equals(cola.getComentario(), comentario)
                || !Objects.equals(cola.getDeshabilitada(), deshabilitada)
                || !Objects.equals(cola.getCortada(), cortada)
                || !Objects.equals(cola.getAviso(), aviso)
                || !Objects.equals(cola.getPortal(), portal);
        if (cambio) {
            cola.setTarget(target);
            cola.setMaxLimit(maxLimit);
            cola.setParent(parent);
            cola.setTipoCola(FormatoRouterOs.TIPO_COLA);
            cola.setComentario(comentario);
            cola.setDeshabilitada(deshabilitada);
            cola.setCortada(cortada);
            cola.setAviso(aviso);
            cola.setPortal(portal);
            if (!portal) {
                cola.setPortalTokenHash(null); // su página deja de abrir al instante
            }
            cola.setDeseadoEn(LocalDateTime.now());
        }
        return cambio;
    }

    private void accionSiCorresponde(Cola cola, MotivoAccion motivo, Usuario usuario) {
        if (cola.getRed().getModo() == ModoRed.CONTROL && !RedService.esColaProtegida(cola.getRed(), cola.getNombre())) {
            crearAccion(cola, motivo, usuario);
        }
    }

    // La acción nueva reemplaza a las que aún no se confirmaban para esa cola
    private void crearAccion(Cola cola, MotivoAccion motivo, Usuario usuario) {
        LocalDateTime ahora = LocalDateTime.now();
        for (AccionCola anterior : accionColaRepository.findByColaIdAndEstadoIn(cola.getId(), POR_ENVIAR)) {
            anterior.setEstado(EstadoAccion.REEMPLAZADA);
            anterior.setResueltaEn(ahora);
        }
        AccionCola accion = new AccionCola();
        accion.setCola(cola);
        accion.setMotivo(motivo);
        accion.setCreadoPor(usuario);
        accionColaRepository.save(accion);
    }

    private static String clave(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    private static String mostrar(String valor) {
        return valor == null || valor.isEmpty() ? "(vacío)" : valor;
    }
}
