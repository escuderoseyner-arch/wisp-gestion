package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.service.AvisoWifi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Resumen de una red para su panel
public record PanelRedResponse(
        RedResponse red,
        boolean mikrotikEnLinea,         // consultó hace poco (menos de 3 intervalos)
        int colas,                       // colas de clientes vigentes
        int conectados,
        int sinDatos,                    // el MikroTik aún no las reporta o el reporte es viejo
        List<ClienteSinConexion> sinConexion,
        ColaClienteResponse.ConsumoMes consumoMes,
        List<ColaConDiferencias> diferencias,
        long accionesPendientes,
        long accionesConError,
        List<ClienteEnAviso> enAviso     // hoy, si la red tiene el aviso activo
) {

    // entendidoHasta != null: tocó "Entendido" y no lo ve hasta esa hora.
    // enMikrotik: lo que informó el router (null = su script aún no informa wisp-aviso)
    public record ClienteEnAviso(Integer clienteId, String codigo, String nombres, AvisoWifi.Etapa etapa,
                                 BigDecimal deuda, LocalDateTime entendidoHasta, Boolean enMikrotik) {
    }

    public record ClienteSinConexion(Integer clienteId, String codigo, String nombres, String cola) {
    }

    public record ColaConDiferencias(Integer clienteId, String codigo, String nombres, String cola,
                                     boolean clienteVigente, List<String> diferencias) {
    }
}
