package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.dto.EstadoMes;
import com.escuderoseyner.wisp.dto.PanelResponse;
import com.escuderoseyner.wisp.dto.ResumenMesResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Pago;
import com.escuderoseyner.wisp.repository.ClienteRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PanelService {

    // "Próximos a vencer": los que vencen desde hoy hasta dentro de estos días
    private static final int DIAS_PROXIMOS = 3;

    private final ClienteRepository clienteRepository;
    private final PagoRepository pagoRepository;
    private final PagoService pagoService;
    private final ConfiguracionService configuracionService;

    public PanelService(ClienteRepository clienteRepository, PagoRepository pagoRepository,
                        PagoService pagoService, ConfiguracionService configuracionService) {
        this.clienteRepository = clienteRepository;
        this.pagoRepository = pagoRepository;
        this.pagoService = pagoService;
        this.configuracionService = configuracionService;
    }

    @Transactional(readOnly = true)
    public PanelResponse panel() {
        LocalDate hoy = LocalDate.now();
        YearMonth actual = YearMonth.from(hoy);
        ConfiguracionResponse config = configuracionService.obtener();
        int tolerancia = config.diasTolerancia();

        // Resumen del mes actual (mismo cálculo que la pantalla de pagos)
        ResumenMesResponse mes = pagoService.resumenDelMes(actual.toString());
        BigDecimal esperado = mes.clientes().stream()
                .map(ResumenMesResponse.FilaMes::precioPlan)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Cliente> vigentes = clienteRepository.buscar(ClienteService.VIGENTES, null, null).stream()
                .sorted(ClienteService.POR_CODIGO)
                .toList();
        int activos = (int) vigentes.stream().filter(c -> c.getEstado() == EstadoCliente.ACTIVO).count();

        // Meses pagados de cada cliente, en una sola consulta liviana
        Map<Integer, Set<YearMonth>> pagadosPorCliente = new HashMap<>();
        for (Object[] fila : pagoRepository.findClienteYPeriodo()) {
            pagadosPorCliente.computeIfAbsent((Integer) fila[0], id -> new HashSet<>())
                    .add(YearMonth.from((LocalDate) fila[1]));
        }

        List<PanelResponse.Atrasado> atrasados = new ArrayList<>();
        List<PanelResponse.ProximoAVencer> proximos = new ArrayList<>();
        for (Cliente cliente : vigentes) {
            Set<YearMonth> pagados = pagadosPorCliente.getOrDefault(cliente.getId(), Set.of());
            BigDecimal precio = cliente.getPlan().getPrecio();

            // Atrasados: meses VENCIDOS desde su inicio hasta hoy
            List<String> vencidos = new ArrayList<>();
            LocalDate primerVencimiento = null;
            for (YearMonth m = YearMonth.from(cliente.getFechaInicio()); !m.isAfter(actual); m = m.plusMonths(1)) {
                if (CalculadoraEstadoMes.estado(cliente, m, pagados.contains(m), tolerancia, hoy) == EstadoMes.VENCIDO) {
                    if (primerVencimiento == null) {
                        primerVencimiento = CalculadoraEstadoMes.vencimiento(cliente, m, tolerancia);
                    }
                    vencidos.add(m.toString());
                }
            }
            if (!vencidos.isEmpty()) {
                atrasados.add(new PanelResponse.Atrasado(cliente.getId(), cliente.getCodigo(), cliente.getNombres(),
                        cliente.getZona(), cliente.getCelular(), vencidos,
                        precio.multiply(BigDecimal.valueOf(vencidos.size())),
                        ChronoUnit.DAYS.between(primerVencimiento, hoy)));
            }

            // Próximos a vencer: este mes o el siguiente (con tolerancia, el plazo puede caer el mes que viene)
            for (YearMonth m : List.of(actual, actual.plusMonths(1))) {
                if (pagados.contains(m) || !CalculadoraEstadoMes.aplica(cliente, m)) {
                    continue;
                }
                LocalDate vence = CalculadoraEstadoMes.vencimiento(cliente, m, tolerancia);
                long dias = ChronoUnit.DAYS.between(hoy, vence);
                if (dias >= 0 && dias <= DIAS_PROXIMOS) {
                    proximos.add(new PanelResponse.ProximoAVencer(cliente.getId(), cliente.getCodigo(),
                            cliente.getNombres(), cliente.getZona(), cliente.getCelular(), m.toString(), vence, dias, precio));
                }
            }
        }
        atrasados.sort(Comparator.comparingLong(PanelResponse.Atrasado::diasAtraso).reversed()
                .thenComparing(PanelResponse.Atrasado::deuda, Comparator.reverseOrder()));
        proximos.sort(Comparator.comparing(PanelResponse.ProximoAVencer::vence));

        // Pagos recibidos esta semana (de lunes a domingo)
        LocalDate lunes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate domingo = lunes.plusDays(6);
        List<PanelResponse.PagoDeLaSemana> semana = pagoRepository.findRecibidosEntre(lunes, domingo).stream()
                .map(this::aPagoDeLaSemana)
                .toList();
        BigDecimal totalSemana = semana.stream()
                .map(PanelResponse.PagoDeLaSemana::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PanelResponse(mes.moneda(), mes.periodo(), esperado, mes.cobrado(), mes.pendiente(),
                activos, vigentes.size() - activos, atrasados, proximos, DIAS_PROXIMOS,
                lunes, domingo, semana, totalSemana, config.plantillaRecordatorio(), config.codigoPais());
    }

    private PanelResponse.PagoDeLaSemana aPagoDeLaSemana(Pago p) {
        Cliente c = p.getCliente();
        return new PanelResponse.PagoDeLaSemana(p.getId(), c.getId(), c.getCodigo(), c.getNombres(),
                YearMonth.from(p.getPeriodo()).toString(), p.getMonto(), p.getFechaPago(), p.getMetodo());
    }
}
