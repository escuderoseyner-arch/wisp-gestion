package com.escuderoseyner.wisp.dto;

import com.escuderoseyner.wisp.model.ModoRed;

import java.time.LocalDateTime;
import java.util.List;

// Nunca incluye el token ni su hash
public record RedResponse(
        Integer id,
        String nombre,
        String prefijoCodigo,
        String colaPadre,
        List<String> colasProtegidas,
        Integer intervaloSegundos,
        ModoRed modo,
        boolean instalacionPendiente,
        boolean avisoActivo,     // aviso de pago por Wi-Fi y página "Servicio suspendido"
        LocalDateTime ultimaConexion,
        LocalDateTime ultimoReporte,
        String ultimaIp,
        long clientes          // clientes vigentes (ACTIVO o SUSPENDIDO) en esta red
) {
}
