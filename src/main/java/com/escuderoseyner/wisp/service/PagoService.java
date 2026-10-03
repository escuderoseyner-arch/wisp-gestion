package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.EstadoCuentaResponse;
import com.escuderoseyner.wisp.dto.EstadoMes;
import com.escuderoseyner.wisp.dto.MesResponse;
import com.escuderoseyner.wisp.dto.PagoResponse;
import com.escuderoseyner.wisp.dto.RegistrarPagoRequest;
import com.escuderoseyner.wisp.dto.ResumenMesResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Pago;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Un pago = un cliente + un mes. En la base de datos el mes se guarda como su primer día
// (2026-10-01 = octubre); aquí se trabaja con YearMonth y en JSON viaja como "2026-10".
@Service
public class PagoService {

    // Meses futuros que el formulario ofrece para pagar por adelantado
    private static final int MESES_ADELANTADOS_EN_FORMULARIO = 3;
    // Límite de adelanto que acepta el servidor
    private static final int MAX_MESES_ADELANTO = 12;

    private static final DateTimeFormatter NOMBRE_MES =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es"));

    private final PagoRepository pagoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;

    public PagoService(PagoRepository pagoRepository, ClienteRepository clienteRepository,
                       UsuarioRepository usuarioRepository, ConfiguracionService configuracionService) {
        this.pagoRepository = pagoRepository;
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionService = configuracionService;
    }

    // ---------- Pantalla de pagos de un mes ----------

    @Transactional(readOnly = true)
    public ResumenMesResponse resumenDelMes(String periodoTexto) {
        YearMonth mes = periodoTexto == null || periodoTexto.isBlank() ? YearMonth.now() : parsearMes(periodoTexto);
        int tolerancia = configuracionService.diasTolerancia();
        LocalDate hoy = LocalDate.now();

        // getCliente().getId() no hace otra consulta: el id ya viene en la fila del pago
        Map<Integer, Pago> pagoPorCliente = pagoRepository.findDelPeriodo(mes.atDay(1)).stream()
                .collect(Collectors.toMap(p -> p.getCliente().getId(), Function.identity()));
        BigDecimal cobrado = pagoPorCliente.values().stream()
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ResumenMesResponse.FilaMes> filas = new ArrayList<>();
        BigDecimal pendiente = BigDecimal.ZERO;
        int pagados = 0;
        int pendientes = 0;
        int vencidos = 0;

        List<Cliente> vigentes = clienteRepository.buscar(ClienteService.VIGENTES, null, null).stream()
                .sorted(ClienteService.POR_CODIGO)
                .toList();
        for (Cliente cliente : vigentes) {
            Pago pago = pagoPorCliente.get(cliente.getId());
            EstadoMes estado = estadoDe(cliente, mes, pago != null, tolerancia, hoy);
            switch (estado) {
                case NO_APLICA -> {
                    continue; // aún no era cliente ese mes
                }
                case PAGADO -> pagados++;
                case PENDIENTE -> {
                    pendientes++;
                    pendiente = pendiente.add(cliente.getPlan().getPrecio());
                }
                case VENCIDO -> {
                    vencidos++;
                    pendiente = pendiente.add(cliente.getPlan().getPrecio());
                }
            }
            filas.add(new ResumenMesResponse.FilaMes(cliente.getId(), cliente.getCodigo(), cliente.getNombres(),
                    cliente.getZona(), cliente.getPlan().getNombre(), cliente.getPlan().getPrecio(), estado,
                    vencimiento(cliente, mes, tolerancia), pago == null ? null : aResponse(pago)));
        }

        return new ResumenMesResponse(mes.toString(), configuracionService.moneda(), cobrado, pendiente,
                pagados, pendientes, vencidos, filas);
    }

    // ---------- Estado de cuenta de un cliente ----------

    @Transactional(readOnly = true)
    public EstadoCuentaResponse estadoDeCuenta(Integer clienteId) {
        Cliente cliente = buscarCliente(clienteId);
        int tolerancia = configuracionService.diasTolerancia();
        LocalDate hoy = LocalDate.now();
        YearMonth actual = YearMonth.from(hoy);

        List<Pago> pagos = pagoRepository.findHistorialDeCliente(clienteId);
        Map<YearMonth, Pago> pagoPorMes = pagos.stream()
                .collect(Collectors.toMap(p -> YearMonth.from(p.getPeriodo()), Function.identity()));

        YearMonth inicio = YearMonth.from(cliente.getFechaInicio());
        YearMonth retiro = cliente.getFechaRetiro() == null ? null : YearMonth.from(cliente.getFechaRetiro());

        // Historial de meses: del más reciente (mes actual o de retiro) hacia su inicio
        YearMonth finHistorial = retiro != null && retiro.isBefore(actual) ? retiro : actual;
        List<MesResponse> meses = new ArrayList<>();
        int mesesVencidos = 0;
        for (YearMonth mes = finHistorial; !mes.isBefore(inicio); mes = mes.minusMonths(1)) {
            MesResponse fila = aMes(cliente, mes, pagoPorMes.get(mes), tolerancia, hoy);
            if (fila.estado() == EstadoMes.VENCIDO) {
                mesesVencidos++;
            }
            meses.add(fila);
        }

        // Meses que se pueden pagar: impagos desde su inicio, más algunos adelantados (si no está retirado)
        YearMonth limite = retiro != null ? retiro : actual.plusMonths(MESES_ADELANTADOS_EN_FORMULARIO);
        List<MesResponse> porPagar = new ArrayList<>();
        for (YearMonth mes = inicio; !mes.isAfter(limite); mes = mes.plusMonths(1)) {
            if (!pagoPorMes.containsKey(mes)) {
                porPagar.add(aMes(cliente, mes, null, tolerancia, hoy));
            }
        }

        BigDecimal precio = cliente.getPlan().getPrecio();
        return new EstadoCuentaResponse(
                new EstadoCuentaResponse.ClienteDelPago(cliente.getId(), cliente.getCodigo(), cliente.getNombres(),
                        cliente.getPlan().getNombre(), cliente.getEstado()),
                configuracionService.moneda(),
                meses,
                porPagar,
                pagos.stream().map(this::aResponse).toList(),
                porPagar.isEmpty() ? null : porPagar.get(0).periodo(),
                precio,
                mesesVencidos,
                precio.multiply(BigDecimal.valueOf(mesesVencidos)));
    }

    // ---------- Registrar y eliminar ----------

    // Una fila por mes, todas con el mismo monto. Si un mes falla, no se guarda ninguno.
    @Transactional
    public EstadoCuentaResponse registrar(RegistrarPagoRequest request, String usernameAdmin) {
        Cliente cliente = buscarCliente(request.clienteId());
        Usuario admin = usuarioRepository.findByUsername(usernameAdmin)
                .orElseThrow(CredencialesInvalidasException::new);

        YearMonth inicio = YearMonth.from(cliente.getFechaInicio());
        YearMonth limite = cliente.getFechaRetiro() != null
                ? YearMonth.from(cliente.getFechaRetiro())
                : YearMonth.now().plusMonths(MAX_MESES_ADELANTO);
        Set<YearMonth> yaPagados = pagoRepository.findHistorialDeCliente(cliente.getId()).stream()
                .map(p -> YearMonth.from(p.getPeriodo()))
                .collect(Collectors.toSet());

        List<String> problemas = new ArrayList<>();
        Set<YearMonth> meses = new LinkedHashSet<>(); // sin repetidos y en el orden recibido
        for (String texto : request.periodos()) {
            YearMonth mes;
            try {
                mes = parsearMes(texto);
            } catch (ReglaNegocioException e) {
                problemas.add(e.getMessage());
                continue;
            }
            if (mes.isBefore(inicio)) {
                problemas.add(nombreMes(mes) + " es anterior a su fecha de inicio.");
            } else if (mes.isAfter(limite)) {
                problemas.add(cliente.getFechaRetiro() != null
                        ? nombreMes(mes) + " es posterior a su retiro."
                        : "No se puede adelantar más de " + MAX_MESES_ADELANTO + " meses (" + nombreMes(mes) + ").");
            } else if (yaPagados.contains(mes)) {
                problemas.add(nombreMes(mes) + " ya está pagado.");
            } else {
                meses.add(mes);
            }
        }
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException("No se registró ningún pago.", problemas);
        }

        String observacion = request.observacion() == null || request.observacion().isBlank()
                ? null
                : request.observacion().trim();
        for (YearMonth mes : meses) {
            Pago pago = new Pago();
            pago.setCliente(cliente);
            pago.setPeriodo(mes.atDay(1));
            pago.setMonto(request.monto());
            pago.setFechaPago(request.fechaPago());
            pago.setMetodo(request.metodo());
            pago.setObservacion(observacion);
            pago.setRegistradoPor(admin);
            pagoRepository.save(pago);
        }
        return estadoDeCuenta(cliente.getId());
    }

    // Para corregir un pago registrado por error
    @Transactional
    public void eliminar(Integer pagoId) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un pago con el id " + pagoId + "."));
        pagoRepository.delete(pago);
    }

    // ---------- Reglas del estado de un mes (en CalculadoraEstadoMes) ----------

    private LocalDate vencimiento(Cliente cliente, YearMonth mes, int tolerancia) {
        return CalculadoraEstadoMes.vencimiento(cliente, mes, tolerancia);
    }

    private EstadoMes estadoDe(Cliente cliente, YearMonth mes, boolean pagado, int tolerancia, LocalDate hoy) {
        return CalculadoraEstadoMes.estado(cliente, mes, pagado, tolerancia, hoy);
    }

    private MesResponse aMes(Cliente cliente, YearMonth mes, Pago pago, int tolerancia, LocalDate hoy) {
        return new MesResponse(mes.toString(), estadoDe(cliente, mes, pago != null, tolerancia, hoy),
                vencimiento(cliente, mes, tolerancia), pago == null ? null : aResponse(pago));
    }

    // ---------- Utilidades ----------

    private Cliente buscarCliente(Integer id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con el id " + id + "."));
    }

    private YearMonth parsearMes(String texto) {
        try {
            return YearMonth.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException("El mes \"" + texto + "\" no es válido. Usa el formato AAAA-MM (ej: 2026-10).");
        }
    }

    private String nombreMes(YearMonth mes) {
        String texto = mes.format(NOMBRE_MES);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private PagoResponse aResponse(Pago pago) {
        return new PagoResponse(pago.getId(), YearMonth.from(pago.getPeriodo()).toString(), pago.getMonto(),
                pago.getFechaPago(), pago.getMetodo(), pago.getObservacion(),
                pago.getRegistradoPor().getNombreMostrar());
    }
}
