package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ConfiguracionPublicaResponse;
import com.escuderoseyner.wisp.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionService {

    // La tabla configuracion tiene una sola fila, siempre con id = 1
    private static final Integer ID_CONFIGURACION = 1;

    private final ConfiguracionRepository configuracionRepository;

    public ConfiguracionService(ConfiguracionRepository configuracionRepository) {
        this.configuracionRepository = configuracionRepository;
    }

    // Si todavía no hay configuración, se usa un nombre genérico para que el login siga funcionando
    @Transactional(readOnly = true)
    public ConfiguracionPublicaResponse obtenerPublica() {
        return configuracionRepository.findById(ID_CONFIGURACION)
                .map(c -> new ConfiguracionPublicaResponse(c.getNombreEmpresa(), c.getLogoUrl()))
                .orElse(new ConfiguracionPublicaResponse("Gestión WISP", null));
    }
}
