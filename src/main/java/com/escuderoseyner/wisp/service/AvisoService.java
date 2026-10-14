package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.AvisoResponse;
import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.model.Cliente;
import com.escuderoseyner.wisp.model.Cola;
import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.MotivoAccion;
import com.escuderoseyner.wisp.repository.ColaRepository;
import com.escuderoseyner.wisp.repository.PagoRepository;
import com.escuderoseyner.wisp.security.TokenRed;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Página pública de aviso por Wi-Fi. REGLA DE SEGURIDAD: el cliente sale SOLO del token de la URL
// (el MikroTik lo pone en la regla del proxy de SU IP), nunca de un código o id que mande el navegador.
// En la base solo está el hash del token.
@Service
public class AvisoService {

    private final ColaRepository colaRepository;
    private final PagoRepository pagoRepository;
    private final ConfiguracionService configuracionService;
    private final SincronizacionService sincronizacionService;

    public AvisoService(ColaRepository colaRepository, PagoRepository pagoRepository,
                        ConfiguracionService configuracionService, SincronizacionService sincronizacionService) {
        this.colaRepository = colaRepository;
        this.pagoRepository = pagoRepository;
        this.configuracionService = configuracionService;
        this.sincronizacionService = sincronizacionService;
    }

    @Transactional(readOnly = true)
    public AvisoResponse ver(String token) {
        Cola cola = buscar(token);
        ConfiguracionResponse config = configuracionService.obtener();
        AvisoResponse.DatosParaPagar pagar = new AvisoResponse.DatosParaPagar(config.yapeNumero(),
                config.yapeTitular(), config.cuentaBancaria());
        AvisoResponse.Soporte soporte = new AvisoResponse.Soporte(config.codigoPais(), config.whatsappSoporte());

        // La IP ya no es de un cliente vigente (ej: retirado): página genérica, sin datos de nadie
        if (!esDuenoVigente(cola)) {
            return new AvisoResponse(AvisoResponse.Etapa.SUSPENDIDO, config.nombreEmpresa(), config.logoUrl(),
                    null, null, config.moneda(), List.of(), BigDecimal.ZERO, null, pagar, soporte, false);
        }

        Cliente cliente = cola.getCliente();
        LocalDateTime ahora = LocalDateTime.now();
        Set<YearMonth> pagados = pagoRepository.findByClienteIdOrderByPeriodoDesc(cliente.getId()).stream()
                .map(p -> YearMonth.from(p.getPeriodo()))
                .collect(Collectors.toSet());
        List<YearMonth> meses = AvisoWifi.mesesEnAviso(cliente, pagados, ahora.toLocalDate());
        BigDecimal monto = cliente.getPlan().getPrecio().multiply(BigDecimal.valueOf(meses.size()));

        AvisoResponse.Etapa etapa;
        String texto;
        if (suspendido(cliente)) {
            etapa = AvisoResponse.Etapa.SUSPENDIDO;
            texto = config.avisoTextoSuspendido();
        } else {
            etapa = switch (AvisoWifi.etapa(cliente, pagados, config.diasTolerancia(), ahora.toLocalDate())) {
                case SUAVE -> AvisoResponse.Etapa.SUAVE;
                case VENCIDO -> AvisoResponse.Etapa.VENCIDO;
                case NINGUNA -> AvisoResponse.Etapa.AL_DIA;
            };
            texto = switch (etapa) {
                case SUAVE -> config.avisoTextoSuave();
                case VENCIDO -> config.avisoTextoVencido();
                default -> null;
            };
        }

        return new AvisoResponse(etapa, config.nombreEmpresa(), config.logoUrl(), cliente.getNombres(),
                cliente.getCodigo(), config.moneda(), meses.stream().map(YearMonth::toString).toList(), monto,
                texto, pagar, soporte, etapa != AvisoResponse.Etapa.SUSPENDIDO);
    }

    // "Entendido": no vuelve a ver el aviso hasta mañana a las 8:00.
    // Sale de wisp-aviso en la próxima consulta del MikroTik (máximo un intervalo).
    @Transactional
    public LocalDateTime entendido(String token) {
        Cola cola = buscar(token);
        if (!esDuenoVigente(cola) || suspendido(cola.getCliente())) {
            throw new ReglaNegocioException("Tu servicio está suspendido: paga y envíanos tu comprobante para volver a activarlo.");
        }
        Cliente cliente = cola.getCliente();
        LocalDateTime hasta = AvisoWifi.pospuestoHasta(LocalDateTime.now());
        cliente.setAvisoPospuestoHasta(hasta);
        sincronizacionService.recalcularRed(cola.getRed(), MotivoAccion.CAMBIO, null);
        return hasta;
    }

    private Cola buscar(String token) {
        String limpio = token == null ? "" : token.trim();
        Optional<Cola> cola = TokenRed.formatoValido(limpio)
                ? colaRepository.findByPortalTokenHash(TokenRed.hash(limpio))
                : Optional.empty();
        return cola.orElseThrow(() -> new RecursoNoEncontradoException("Este aviso ya no está disponible."));
    }

    private static boolean suspendido(Cliente cliente) {
        return cliente.getEstado() != EstadoCliente.ACTIVO || Boolean.TRUE.equals(cliente.getCorteManual());
    }

    // Igual que en ColaService: la cola es de un cliente vigente de esa red con ese nombre de cola
    private static boolean esDuenoVigente(Cola c) {
        Cliente cliente = c.getCliente();
        return cliente.getEstado() != EstadoCliente.RETIRADO
                && cliente.getRed() != null && cliente.getRed().getId().equals(c.getRed().getId())
                && c.getNombre().equalsIgnoreCase(cliente.getNombreCola());
    }
}
