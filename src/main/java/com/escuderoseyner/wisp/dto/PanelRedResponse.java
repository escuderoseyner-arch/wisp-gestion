package com.escuderoseyner.wisp.dto;

import java.time.LocalDate;
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
        long accionesConError
) {

    public record ClienteSinConexion(Integer clienteId, String codigo, String nombres, String cola) {
    }

    public record ColaConDiferencias(Integer clienteId, String codigo, String nombres, String cola,
                                     boolean clienteVigente, List<String> diferencias) {
    }
}
