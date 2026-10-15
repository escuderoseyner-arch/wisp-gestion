package com.escuderoseyner.wisp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Borra el historial de la terminal con más de 30 días. Corre cada día y también al arrancar,
// porque en Render la app se duerme y puede no estar despierta a la hora programada.
@Slf4j
@Component
public class TerminalLimpiezaTarea {

    private final TerminalService terminalService;

    public TerminalLimpiezaTarea(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        limpiar();
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Lima")
    public void cadaDia() {
        limpiar();
    }

    private void limpiar() {
        try {
            int borrados = terminalService.borrarAntiguos();
            if (borrados > 0) {
                log.info("Terminal: {} comando(s) de más de 30 días borrado(s)", borrados);
            }
        } catch (RuntimeException e) {
            log.warn("Terminal: no se pudo borrar el historial antiguo: {}", e.getMessage());
        }
    }
}
