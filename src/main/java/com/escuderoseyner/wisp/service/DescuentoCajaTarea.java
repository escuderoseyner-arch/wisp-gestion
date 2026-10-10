package com.escuderoseyner.wisp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Aplica el descuento mensual (pago de Starlink) de cada caja. Corre cada hora y también al arrancar,
// porque en Render la app se duerme y puede no estar despierta justo el día del descuento.
// Si estuvo dormida varios días, crea los descuentos con la fecha que les correspondía.
@Slf4j
@Component
public class DescuentoCajaTarea {

    private final CajaService cajaService;

    public DescuentoCajaTarea(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        aplicar();
    }

    @Scheduled(cron = "0 5 * * * *", zone = "America/Lima")
    public void cadaHora() {
        aplicar();
    }

    // Cada caja en su propia transacción: si una falla, las demás siguen
    private void aplicar() {
        for (Integer cajaId : cajaService.idsConDescuentoActivo()) {
            try {
                int creados = cajaService.aplicarDescuentosPendientes(cajaId);
                if (creados > 0) {
                    log.info("Caja {}: {} descuento(s) mensual(es) aplicado(s)", cajaId, creados);
                }
            } catch (RuntimeException e) {
                log.warn("Caja {}: no se pudo aplicar el descuento mensual: {}", cajaId, e.getMessage());
            }
        }
    }
}
