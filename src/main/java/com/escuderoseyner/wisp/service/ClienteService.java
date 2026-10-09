package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ClienteDetalleResponse;
import com.escuderoseyner.wisp.dto.ClienteRequest;
import com.escuderoseyner.wisp.dto.ClienteResumenResponse;
import com.escuderoseyner.wisp.dto.DatosCliente;
import com.escuderoseyner.wisp.dto.ReasignarCodigoRequest;
import com.escuderoseyner.wisp.dto.SiguienteCodigoResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.model.Plan;
import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.RedRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Un cliente nunca se borra. Al retirarse queda RETIRADO con todo su historial,
// y su código puede pasar a otra persona (reasignar), que es un registro nuevo.
@Service
public class ClienteService {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("^C-(\\d{2,8})$");
    private static final Pattern FORMATO_CELULAR = Pattern.compile("^9\\d{8}$");
    // Cuatro números de 0 a 255 separados por puntos, sin ceros a la izquierda
    private static final Pattern FORMATO_IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    // Nombre de cola en el MikroTik: sin espacios ni caracteres que rompan los scripts de RouterOS
    private static final Pattern FORMATO_NOMBRE_COLA = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$");

    public static final List<EstadoCliente> VIGENTES = List.of(EstadoCliente.ACTIVO, EstadoCliente.SUSPENDIDO);
    private static final int MAX_LARGO_BUSQUEDA = 100;

    // Orden numérico del código: C-2, C-10, C-100 (como texto, C-100 iría antes que C-2).
    // Público porque PagoService ordena igual la lista del mes.
    public static final Comparator<Cliente> POR_CODIGO =
            Comparator.comparingLong((Cliente c) -> numeroDeCodigo(c.getCodigo()))
                    .thenComparing(Cliente::getCodigo)
                    .thenComparing(Cliente::getId);

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PlanService planService;
    private final RedRepository redRepository;
    private final SincronizacionService sincronizacionService;

    public ClienteService(ClienteRepository clienteRepository, UsuarioRepository usuarioRepository,
                          PlanService planService, RedRepository redRepository,
                          SincronizacionService sincronizacionService) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.planService = planService;
        this.redRepository = redRepository;
        this.sincronizacionService = sincronizacionService;
    }

    // ---------- Consultas ----------

    // Sin estado: solo vigentes (ACTIVO y SUSPENDIDO)
    @Transactional(readOnly = true)
    public List<ClienteResumenResponse> listar(EstadoCliente estado, String zona, String busqueda) {
        List<EstadoCliente> estados = estado != null ? List.of(estado) : VIGENTES;

        String patron = null;
        String texto = limpiarTexto(busqueda);
        if (texto != null) {
            if (texto.length() > MAX_LARGO_BUSQUEDA) {
                texto = texto.substring(0, MAX_LARGO_BUSQUEDA);
            }
            patron = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        }

        return clienteRepository.buscar(estados, limpiarTexto(zona), patron).stream()
                .sorted(POR_CODIGO)
                .map(this::aResumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> zonas() {
        return clienteRepository.findZonas();
    }

    // El número más alto usado alguna vez + 1. No reutiliza códigos de retirados:
    // para darle un código antiguo a otra persona está "reasignar código".
    @Transactional(readOnly = true)
    public SiguienteCodigoResponse siguienteCodigo() {
        long mayor = clienteRepository.findAllCodigos().stream()
                .map(FORMATO_CODIGO::matcher)
                .filter(Matcher::matches)
                .mapToLong(m -> Long.parseLong(m.group(1)))
                .max()
                .orElse(0);
        return new SiguienteCodigoResponse(String.format("C-%02d", mayor + 1));
    }

    @Transactional(readOnly = true)
    public ClienteDetalleResponse detalle(Integer id) {
        return aDetalle(buscar(id));
    }

    // ---------- Crear y editar ----------

    @Transactional
    public ClienteDetalleResponse crear(ClienteRequest request) {
        Cliente cliente = new Cliente();
        validarYAplicar(cliente, request.codigo(), request, null, null, new ArrayList<>());
        clienteRepository.save(cliente);
        actualizarColas(cliente);
        return aDetalle(cliente, Optional.empty());
    }

    @Transactional
    public ClienteDetalleResponse editar(Integer id, ClienteRequest request) {
        Cliente cliente = buscarVigente(id);
        Optional<Usuario> cuenta = usuarioRepository.findByClienteId(id);

        validarYAplicar(cliente, request.codigo(), request, id, cliente.getPlan(), new ArrayList<>());
        actualizarColas(cliente);

        // El usuario de acceso NO cambia con el celular: se cambia aparte ("Cambiar usuario")
        cuenta.ifPresent(usuario -> usuario.setNombreMostrar(cliente.getNombres()));
        return aDetalle(cliente, cuenta);
    }

    // ---------- Cambios de estado ----------

    @Transactional
    public ClienteDetalleResponse suspender(Integer id) {
        Cliente cliente = buscarVigente(id);
        if (cliente.getEstado() != EstadoCliente.ACTIVO) {
            throw new ReglaNegocioException("Solo se puede suspender a un cliente activo.");
        }
        cliente.setEstado(EstadoCliente.SUSPENDIDO);
        actualizarColas(cliente);
        return aDetalle(cliente);
    }

    @Transactional
    public ClienteDetalleResponse reactivar(Integer id) {
        Cliente cliente = buscarVigente(id);
        if (cliente.getEstado() != EstadoCliente.SUSPENDIDO) {
            throw new ReglaNegocioException("Solo se puede reactivar a un cliente suspendido.");
        }
        cliente.setEstado(EstadoCliente.ACTIVO);
        actualizarColas(cliente);
        return aDetalle(cliente);
    }

    @Transactional
    public ClienteDetalleResponse retirar(Integer id) {
        Cliente cliente = buscarVigente(id);
        marcarRetirado(cliente);
        actualizarColas(cliente);
        return aDetalle(cliente);
    }

    // Todo en UNA transacción: si algo falla, no queda ni el anterior retirado ni el nuevo creado.
    @Transactional
    public ClienteDetalleResponse reasignarCodigo(Integer id, ReasignarCodigoRequest request) {
        Cliente anterior = buscarVigente(id);

        // Primero se valida la persona nueva. La IP del anterior cuenta como libre porque se va a retirar.
        Cliente nuevo = new Cliente();
        validarYAplicar(nuevo, anterior.getCodigo(), request, anterior.getId(), null, new ArrayList<>());

        marcarRetirado(anterior);
        // flush: guarda YA el retiro, para que MySQL libere codigo_vigente antes de insertar
        // al nuevo. Sin esto, el UNIQUE de codigo_vigente rechazaría el INSERT.
        clienteRepository.saveAndFlush(anterior);
        clienteRepository.save(nuevo);
        // La cola (mismo nombre) pasa a la persona nueva; si cambió de red, la anterior se deshabilita
        actualizarColas(nuevo);
        actualizarColas(anterior);
        return aDetalle(nuevo, Optional.empty());
    }

    // ---------- Validación ----------

    // Revisa todo y junta TODOS los problemas antes de avisar, para corregirlos de una vez.
    // idExcluir: el propio cliente al editar (o el que se retira al reasignar).
    // planActual: al editar, conservar el mismo plan está permitido aunque esté desactivado.
    private void validarYAplicar(Cliente cliente, String codigoRecibido, DatosCliente datos,
                                 Integer idExcluir, Plan planActual, List<String> problemas) {
        String codigo = codigoRecibido == null ? "" : codigoRecibido.trim().toUpperCase(Locale.ROOT);
        if (!FORMATO_CODIGO.matcher(codigo).matches()) {
            problemas.add("El código debe tener el formato C-NN (ej: C-07).");
        } else if (codigoEnUso(codigo, idExcluir)) {
            problemas.add("El código " + codigo + " ya lo tiene otro cliente vigente.");
        }

        String celular = limpiarCelular(datos.celular());
        if (celular != null && !FORMATO_CELULAR.matcher(celular).matches()) {
            problemas.add("El celular debe tener 9 dígitos y empezar con 9 (ej: 987654321).");
        }

        String ip = limpiarTexto(datos.ip());
        if (ip != null) {
            if (!FORMATO_IPV4.matcher(ip).matches()) {
                problemas.add("La IP no es una IPv4 válida (ej: 192.168.1.20).");
            } else if (ipEnUso(ip, idExcluir)) {
                problemas.add("La IP " + ip + " ya la usa otro cliente vigente.");
            }
        }

        Plan plan = null;
        try {
            plan = planActual != null && planActual.getId().equals(datos.planId())
                    ? planActual
                    : planService.obtenerParaClienteNuevo(datos.planId());
        } catch (ReglaNegocioException | RecursoNoEncontradoException e) {
            problemas.add(e.getMessage());
        }

        // Red (MikroTik) y nombre de su cola. Sin nombre, la cola se llama como el código.
        Red red = null;
        String nombreCola = null;
        if (datos.redId() != null) {
            red = redRepository.findById(datos.redId()).orElse(null);
            if (red == null) {
                problemas.add("No existe la red elegida.");
            } else {
                if (ip == null) {
                    problemas.add("Para asignarlo a una red, el cliente necesita una IP.");
                }
                nombreCola = limpiarTexto(datos.nombreCola());
                if (nombreCola == null) {
                    nombreCola = codigo;
                }
                if (!FORMATO_NOMBRE_COLA.matcher(nombreCola).matches()) {
                    problemas.add("El nombre de la cola solo puede tener letras, números, punto, guion y guion bajo (sin espacios).");
                } else if (RedService.esColaProtegida(red, nombreCola)) {
                    problemas.add("La cola \"" + nombreCola + "\" es la cola padre o una cola protegida de la red: elige otro nombre.");
                } else if (clienteRepository.colaEnUso(red.getId(), nombreCola, idExcluir)) {
                    problemas.add("La cola \"" + nombreCola + "\" ya la usa otro cliente vigente de esa red.");
                }
            }
        }

        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException("Revisa los datos del cliente.", problemas);
        }

        cliente.setCodigo(codigo);
        cliente.setNombres(limpiarTexto(datos.nombres()));
        cliente.setCelular(celular);
        cliente.setReferencia(limpiarTexto(datos.referencia()));
        cliente.setZona(normalizarZona(datos.zona()));
        cliente.setPlan(plan);
        cliente.setDiaPago(datos.diaPago());
        cliente.setFechaInicio(datos.fechaInicio());
        cliente.setIp(ip);
        cliente.setRed(red);
        cliente.setNombreCola(nombreCola);
    }

    private boolean codigoEnUso(String codigo, Integer idExcluir) {
        return idExcluir == null
                ? clienteRepository.existsByCodigoVigente(codigo)
                : clienteRepository.existsByCodigoVigenteAndIdNot(codigo, idExcluir);
    }

    private boolean ipEnUso(String ip, Integer idExcluir) {
        return idExcluir == null
                ? clienteRepository.existsByIpAndEstadoNot(ip, EstadoCliente.RETIRADO)
                : clienteRepository.existsByIpAndEstadoNotAndIdNot(ip, EstadoCliente.RETIRADO, idExcluir);
    }

    // ---------- Utilidades ----------

    // Estado deseado de sus colas en el MikroTik (y acciones, si la red está en modo Control)
    private void actualizarColas(Cliente cliente) {
        sincronizacionService.alCambiarCliente(cliente, MotivoAccion.CAMBIO, null);
    }

    // Estado RETIRADO + fecha de retiro (MySQL exige las dos juntas) + cuenta desactivada
    private void marcarRetirado(Cliente cliente) {
        cliente.setEstado(EstadoCliente.RETIRADO);
        cliente.setFechaRetiro(LocalDate.now());
        usuarioRepository.findByClienteId(cliente.getId()).ifPresent(usuario -> usuario.setActivo(false));
    }

    private Cliente buscar(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con el id " + id + "."));
    }

    // Un cliente retirado queda como historial: ya no se edita ni cambia de estado
    private Cliente buscarVigente(Integer id) {
        Cliente cliente = buscar(id);
        if (cliente.getEstado() == EstadoCliente.RETIRADO) {
            throw new ReglaNegocioException("El cliente " + cliente.getCodigo()
                    + " está retirado: se conserva como historial y ya no se puede modificar.");
        }
        return cliente;
    }

    // Si ya existe "Zona Norte", escribir "zona norte" usa la misma forma (evita duplicados en el filtro)
    private String normalizarZona(String zona) {
        String limpia = limpiarTexto(zona);
        if (limpia == null) {
            return null;
        }
        return clienteRepository.findZonas().stream()
                .filter(limpia::equalsIgnoreCase)
                .findFirst()
                .orElse(limpia);
    }

    // null o solo espacios -> null; si no, sin espacios de sobra
    private static String limpiarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim().replaceAll("\\s+", " ");
    }

    // "987 654-321" -> "987654321"
    private static String limpiarCelular(String celular) {
        if (celular == null) {
            return null;
        }
        String soloDigitos = celular.replaceAll("[\\s-]", "");
        return soloDigitos.isEmpty() ? null : soloDigitos;
    }

    // En LIKE, % y _ son comodines: se escapan para buscarlos como texto normal
    private static String escaparLike(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static long numeroDeCodigo(String codigo) {
        Matcher m = FORMATO_CODIGO.matcher(codigo);
        return m.matches() ? Long.parseLong(m.group(1)) : Long.MAX_VALUE;
    }

    private ClienteResumenResponse aResumen(Cliente c) {
        return new ClienteResumenResponse(c.getId(), c.getCodigo(), c.getNombres(), c.getZona(),
                c.getPlan().getNombre(), c.getDiaPago(), c.getEstado());
    }

    private ClienteDetalleResponse aDetalle(Cliente cliente) {
        return aDetalle(cliente, usuarioRepository.findByClienteId(cliente.getId()));
    }

    private ClienteDetalleResponse aDetalle(Cliente c, Optional<Usuario> cuenta) {
        Plan plan = c.getPlan();
        return new ClienteDetalleResponse(c.getId(), c.getCodigo(), c.getNombres(), c.getCelular(),
                c.getReferencia(), c.getZona(),
                new ClienteDetalleResponse.PlanDelCliente(plan.getId(), plan.getNombre(), plan.getPrecio(),
                        plan.getActivo()),
                c.getDiaPago(), c.getFechaInicio(), c.getFechaRetiro(), c.getIp(), c.getEstado(),
                cuenta.map(u -> new ClienteDetalleResponse.CuentaDelCliente(u.getUsername(), u.getActivo(),
                        u.getDebeCambiarPassword())).orElse(null),
                c.getRed() == null ? null
                        : new ClienteDetalleResponse.RedDelCliente(c.getRed().getId(), c.getRed().getNombre()),
                c.getNombreCola(), c.getCorteManual());
    }
}
