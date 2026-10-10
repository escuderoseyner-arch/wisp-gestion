package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.CajaDetalleResponse;
import com.escuderoseyner.wisp.dto.CajaResumenResponse;
import com.escuderoseyner.wisp.dto.DescuentoCajaRequest;
import com.escuderoseyner.wisp.dto.HistorialCajaResponse;
import com.escuderoseyner.wisp.dto.MovimientoCajaResponse;
import com.escuderoseyner.wisp.dto.RetiroCajaRequest;
import com.escuderoseyner.wisp.model.Caja;
import com.escuderoseyner.wisp.model.CajaMovimiento;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Pago;
import com.escuderoseyner.wisp.model.TipoMovimientoCaja;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.CajaMovimientoRepository;
import com.escuderoseyner.wisp.repository.CajaRepository;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Caja de una red: sube con cada pago de sus clientes, baja con la cuota mensual y con retiros.
// El saldo nunca se guarda: siempre es la suma de los movimientos.
@Service
public class CajaService {

    // Las fechas de la caja (hoy, día del descuento) son siempre las de Perú, aunque el servidor esté en UTC
    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    private static final String DESCRIPCION_DESCUENTO = "Cuota mensual préstamo";
    private static final DateTimeFormatter MES_CORTO = DateTimeFormatter.ofPattern("MM/yyyy");

    private final CajaRepository cajaRepository;
    private final CajaMovimientoRepository movimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;

    public CajaService(CajaRepository cajaRepository, CajaMovimientoRepository movimientoRepository,
                       UsuarioRepository usuarioRepository, ConfiguracionService configuracionService) {
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionService = configuracionService;
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    // ---------- Consultas ----------

    @Transactional(readOnly = true)
    public List<CajaResumenResponse> listar() {
        String moneda = configuracionService.moneda();
        return cajaRepository.findTodasConRed().stream()
                .map(c -> new CajaResumenResponse(c.getId(), c.getRed().getId(), c.getRed().getNombre(), moneda,
                        saldo(movimientoRepository.sumarPorTipo(c.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public CajaDetalleResponse detalle(Integer cajaId) {
        Caja caja = buscarCaja(cajaId);
        LocalDate hoy = hoy();
        YearMonth mes = YearMonth.from(hoy);
        Map<TipoMovimientoCaja, BigDecimal> delMes =
                totales(movimientoRepository.sumarPorTipoEntre(cajaId, mes.atDay(1), mes.atEndOfMonth()));

        return new CajaDetalleResponse(caja.getId(), caja.getRed().getId(), caja.getRed().getNombre(),
                configuracionService.moneda(),
                saldo(movimientoRepository.sumarPorTipo(cajaId)),
                mes.toString(),
                delMes.get(TipoMovimientoCaja.INGRESO_PAGO),
                delMes.get(TipoMovimientoCaja.DESCUENTO_MENSUAL),
                delMes.get(TipoMovimientoCaja.RETIRO),
                new CajaDetalleResponse.Descuento(caja.getDescuentoMonto(), caja.getDescuentoDia(),
                        caja.getDescuentoActivo(), caja.getDescuentoDesde(), proximoDescuento(caja, hoy)));
    }

    // mesTexto "2026-10" o vacío (todo). El saldo de cada fila se acumula en orden de fecha e id;
    // la lista se devuelve del más reciente al más antiguo.
    @Transactional(readOnly = true)
    public HistorialCajaResponse historial(Integer cajaId, String mesTexto) {
        buscarCaja(cajaId);
        YearMonth mes = mesTexto == null || mesTexto.isBlank() ? null : parsearMes(mesTexto);

        BigDecimal saldo;
        List<CajaMovimiento> movimientos;
        if (mes == null) {
            saldo = BigDecimal.ZERO;
            movimientos = movimientoRepository.findDeCaja(cajaId);
        } else {
            saldo = saldo(movimientoRepository.sumarPorTipoAntesDe(cajaId, mes.atDay(1)));
            movimientos = movimientoRepository.findDeCajaEntre(cajaId, mes.atDay(1), mes.atEndOfMonth());
        }
        BigDecimal saldoInicial = saldo;

        List<MovimientoCajaResponse> filas = new ArrayList<>();
        for (CajaMovimiento m : movimientos) {
            saldo = m.getTipo().suma() ? saldo.add(m.getMonto()) : saldo.subtract(m.getMonto());
            filas.add(new MovimientoCajaResponse(m.getId(), m.getFecha(), m.getTipo(), m.getDescripcion(),
                    m.getMonto(), saldo, m.getUsuario() == null ? null : m.getUsuario().getNombreMostrar(),
                    m.getTipo() == TipoMovimientoCaja.RETIRO));
        }
        Collections.reverse(filas);
        return new HistorialCajaResponse(mes == null ? null : mes.toString(), configuracionService.moneda(),
                saldoInicial, filas);
    }

    // ---------- Retiros ----------

    @Transactional
    public CajaDetalleResponse registrarRetiro(Integer cajaId, RetiroCajaRequest request, String usernameAdmin) {
        Caja caja = buscarCaja(cajaId);
        Usuario admin = usuarioRepository.findByUsername(usernameAdmin)
                .orElseThrow(CredencialesInvalidasException::new);
        LocalDate fecha = request.fecha() == null ? hoy() : request.fecha();
        if (fecha.isAfter(hoy())) {
            throw new ReglaNegocioException("La fecha del retiro no puede ser futura.");
        }

        CajaMovimiento retiro = new CajaMovimiento();
        retiro.setCaja(caja);
        retiro.setTipo(TipoMovimientoCaja.RETIRO);
        retiro.setMonto(request.monto());
        retiro.setFecha(fecha);
        retiro.setDescripcion(request.descripcion().trim());
        retiro.setUsuario(admin);
        movimientoRepository.save(retiro);
        return detalle(cajaId);
    }

    // Solo los retiros se pueden eliminar (por si hubo un error al registrarlo)
    @Transactional
    public void eliminarRetiro(Integer cajaId, Integer movimientoId) {
        CajaMovimiento movimiento = movimientoRepository.findByIdAndCajaId(movimientoId, cajaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe ese movimiento en esta caja."));
        if (movimiento.getTipo() != TipoMovimientoCaja.RETIRO) {
            throw new ReglaNegocioException("Solo se pueden eliminar retiros. Los ingresos y descuentos los maneja el sistema.");
        }
        movimientoRepository.delete(movimiento);
    }

    // ---------- Configuración del descuento ----------

    @Transactional
    public CajaDetalleResponse editarDescuento(Integer cajaId, DescuentoCajaRequest request) {
        Caja caja = buscarCaja(cajaId);
        if (request.activo() && request.monto().signum() == 0) {
            throw new ReglaNegocioException("Para activar el descuento, el monto debe ser mayor que 0.");
        }
        // Al reactivarlo no se cobran los meses en pausa: empieza en la próxima fecha de descuento
        if (request.activo() && !caja.getDescuentoActivo()) {
            LocalDate hoy = hoy();
            YearMonth desde = hoy.getDayOfMonth() > request.dia()
                    ? YearMonth.from(hoy).plusMonths(1)
                    : YearMonth.from(hoy);
            if (desde.atDay(1).isAfter(caja.getDescuentoDesde())) {
                caja.setDescuentoDesde(desde.atDay(1));
            }
        }
        caja.setDescuentoMonto(request.monto());
        caja.setDescuentoDia(request.dia());
        caja.setDescuentoActivo(request.activo());
        cajaRepository.save(caja);
        aplicarDescuentosPendientes(cajaId);
        return detalle(cajaId);
    }

    // ---------- Movimientos automáticos ----------

    // Se llama dentro de la transacción del pago: si falla, tampoco se guarda el pago
    @Transactional
    public void registrarIngresoDePago(Pago pago) {
        Cliente cliente = pago.getCliente();
        if (cliente.getRed() == null) {
            return;
        }
        cajaRepository.findByRedId(cliente.getRed().getId()).ifPresent(caja -> {
            CajaMovimiento ingreso = new CajaMovimiento();
            ingreso.setCaja(caja);
            ingreso.setTipo(TipoMovimientoCaja.INGRESO_PAGO);
            ingreso.setMonto(pago.getMonto());
            ingreso.setFecha(pago.getFechaPago());
            ingreso.setDescripcion(descripcionPago(cliente, pago));
            ingreso.setPagoId(pago.getId());
            ingreso.setUsuario(pago.getRegistradoPor());
            movimientoRepository.save(ingreso);
        });
    }

    @Transactional
    public void eliminarIngresoDePago(Integer pagoId) {
        movimientoRepository.eliminarDePago(pagoId);
    }

    // Crea los descuentos que falten desde descuento_desde hasta hoy. Devuelve cuántos creó.
    // Si dos procesos lo hacen a la vez, la restricción UNIQUE de MySQL impide el duplicado.
    @Transactional
    public int aplicarDescuentosPendientes(Integer cajaId) {
        Caja caja = buscarCaja(cajaId);
        if (!caja.getDescuentoActivo() || caja.getDescuentoMonto().signum() <= 0) {
            return 0;
        }
        LocalDate hoy = hoy();
        int creados = 0;
        for (YearMonth mes = YearMonth.from(caja.getDescuentoDesde());
             !mes.atDay(caja.getDescuentoDia()).isAfter(hoy);
             mes = mes.plusMonths(1)) {
            if (existeDescuento(cajaId, mes)) {
                continue;
            }
            CajaMovimiento descuento = new CajaMovimiento();
            descuento.setCaja(caja);
            descuento.setTipo(TipoMovimientoCaja.DESCUENTO_MENSUAL);
            descuento.setMonto(caja.getDescuentoMonto());
            descuento.setFecha(mes.atDay(caja.getDescuentoDia()));
            descuento.setPeriodo(mes.atDay(1));
            descuento.setDescripcion(DESCRIPCION_DESCUENTO);
            movimientoRepository.save(descuento);
            creados++;
        }
        return creados;
    }

    @Transactional(readOnly = true)
    public List<Integer> idsConDescuentoActivo() {
        return cajaRepository.findIdsConDescuentoActivo();
    }

    // ---------- Utilidades ----------

    // Próximo día de descuento que todavía no se aplicó
    private LocalDate proximoDescuento(Caja caja, LocalDate hoy) {
        if (!caja.getDescuentoActivo()) {
            return null;
        }
        YearMonth mes = YearMonth.from(caja.getDescuentoDesde());
        if (mes.isBefore(YearMonth.from(hoy))) {
            mes = YearMonth.from(hoy);
        }
        if (mes.atDay(caja.getDescuentoDia()).isBefore(hoy)
                || existeDescuento(caja.getId(), mes)) {
            mes = mes.plusMonths(1);
        }
        return mes.atDay(caja.getDescuentoDia());
    }

    private boolean existeDescuento(Integer cajaId, YearMonth mes) {
        return movimientoRepository.existsByCajaIdAndTipoAndPeriodo(cajaId, TipoMovimientoCaja.DESCUENTO_MENSUAL,
                mes.atDay(1));
    }

    private Caja buscarCaja(Integer id) {
        return cajaRepository.findConRed(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una caja con el id " + id + "."));
    }

    private static Map<TipoMovimientoCaja, BigDecimal> totales(List<Object[]> filas) {
        Map<TipoMovimientoCaja, BigDecimal> totales = new EnumMap<>(TipoMovimientoCaja.class);
        for (TipoMovimientoCaja tipo : TipoMovimientoCaja.values()) {
            totales.put(tipo, BigDecimal.ZERO);
        }
        for (Object[] fila : filas) {
            totales.put((TipoMovimientoCaja) fila[0], (BigDecimal) fila[1]);
        }
        return totales;
    }

    private static BigDecimal saldo(List<Object[]> filas) {
        BigDecimal saldo = BigDecimal.ZERO;
        for (Map.Entry<TipoMovimientoCaja, BigDecimal> total : totales(filas).entrySet()) {
            saldo = total.getKey().suma() ? saldo.add(total.getValue()) : saldo.subtract(total.getValue());
        }
        return saldo;
    }

    private static String descripcionPago(Cliente cliente, Pago pago) {
        String texto = "Pago " + cliente.getCodigo() + " - " + cliente.getNombres()
                + " (" + pago.getPeriodo().format(MES_CORTO) + ")";
        return texto.length() > 255 ? texto.substring(0, 255) : texto;
    }

    private static YearMonth parsearMes(String texto) {
        try {
            return YearMonth.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException("El mes \"" + texto + "\" no es válido. Usa el formato AAAA-MM (ej: 2026-10).");
        }
    }
}
