package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.dto.ConfiguracionPublicaResponse;
import com.escuderoseyner.wisp.dto.ConfiguracionRequest;
import com.escuderoseyner.wisp.dto.ConfiguracionResponse;
import com.escuderoseyner.wisp.model.Configuracion;
import com.escuderoseyner.wisp.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConfiguracionService {

    // La tabla configuracion tiene una sola fila, siempre con id = 1
    private static final Integer ID_CONFIGURACION = 1;

    // Variables que se pueden usar en la plantilla de recordatorio
    public static final Set<String> VARIABLES_PLANTILLA = Set.of("nombre", "monto", "mes");
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]*)}");

    private static final Pattern RUC = Pattern.compile("^\\d{11}$");
    private static final Pattern TELEFONO = Pattern.compile("^\\d{6,15}$");
    private static final Pattern CODIGO_PAIS = Pattern.compile("^[1-9]\\d{0,3}$");
    private static final Pattern URL_LOGO = Pattern.compile("^(https://|/)\\S+$");

    private final ConfiguracionRepository configuracionRepository;

    public ConfiguracionService(ConfiguracionRepository configuracionRepository) {
        this.configuracionRepository = configuracionRepository;
    }

    // Si todavía no hay configuración, se usa un nombre genérico para que el login siga funcionando
    @Transactional(readOnly = true)
    public ConfiguracionPublicaResponse obtenerPublica() {
        return configuracionRepository.findById(ID_CONFIGURACION)
                .map(c -> new ConfiguracionPublicaResponse(c.getNombreEmpresa(), c.getLogoUrl(),
                        c.getWhatsappSoporte(), c.getCodigoPais()))
                .orElse(new ConfiguracionPublicaResponse("Gestión WISP", null, null, null));
    }

    // Días de gracia después del día de pago antes de considerar un mes VENCIDO
    @Transactional(readOnly = true)
    public int diasTolerancia() {
        return configuracionRepository.findById(ID_CONFIGURACION)
                .map(Configuracion::getDiasTolerancia)
                .orElse(3);
    }

    @Transactional(readOnly = true)
    public String moneda() {
        return configuracionRepository.findById(ID_CONFIGURACION)
                .map(Configuracion::getMoneda)
                .orElse("S/");
    }

    // ---------- Edición por el ADMIN ----------

    @Transactional(readOnly = true)
    public ConfiguracionResponse obtener() {
        return aResponse(buscarOCrear());
    }

    @Transactional
    public ConfiguracionResponse actualizar(ConfiguracionRequest request) {
        String ruc = limpiar(request.ruc());
        String logoUrl = limpiar(request.logoUrl());
        String whatsapp = soloDigitos(request.whatsappSoporte());
        String codigoPais = soloDigitos(request.codigoPais());
        String yapeNumero = soloDigitos(request.yapeNumero());
        String plantilla = request.plantillaRecordatorio().trim();

        List<String> problemas = new ArrayList<>();
        if (ruc != null && !RUC.matcher(ruc).matches()) {
            problemas.add("El RUC debe tener 11 dígitos.");
        }
        if (logoUrl != null && !URL_LOGO.matcher(logoUrl).matches()) {
            problemas.add("La URL del logo debe empezar con https:// (o con / si es un archivo del sistema).");
        }
        if (whatsapp == null || !TELEFONO.matcher(whatsapp).matches()) {
            problemas.add("El WhatsApp de soporte debe tener entre 6 y 15 dígitos, sin el código de país.");
        }
        if (codigoPais == null || !CODIGO_PAIS.matcher(codigoPais).matches()) {
            problemas.add("El código de país debe tener de 1 a 4 dígitos, sin el + (ej: 51 para Perú).");
        }
        if (yapeNumero != null && !TELEFONO.matcher(yapeNumero).matches()) {
            problemas.add("El número de Yape debe tener entre 6 y 15 dígitos.");
        }
        Matcher variables = VARIABLE.matcher(plantilla);
        while (variables.find()) {
            if (!VARIABLES_PLANTILLA.contains(variables.group(1))) {
                problemas.add("La plantilla usa la variable {" + variables.group(1)
                        + "}, que no existe. Usa solo {nombre}, {monto} y {mes}.");
            }
        }
        if (!problemas.isEmpty()) {
            throw new ReglaNegocioException("Revisa la configuración.", problemas);
        }

        Configuracion c = buscarOCrear();
        c.setNombreEmpresa(request.nombreEmpresa().trim());
        c.setRuc(ruc);
        c.setLogoUrl(logoUrl);
        c.setWhatsappSoporte(whatsapp);
        c.setCodigoPais(codigoPais);
        c.setYapeNumero(yapeNumero);
        c.setYapeTitular(limpiar(request.yapeTitular()));
        c.setCuentaBancaria(limpiar(request.cuentaBancaria()));
        c.setDiasTolerancia(request.diasTolerancia());
        c.setPlantillaRecordatorio(plantilla);
        return aResponse(configuracionRepository.save(c));
    }

    // La fila la crea wisp_db.sql; por si alguien la borró, se crea de nuevo con id = 1
    private Configuracion buscarOCrear() {
        return configuracionRepository.findById(ID_CONFIGURACION).orElseGet(Configuracion::new);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // "+51 987 654-321" -> "51987654321"; vacío -> null
    private static String soloDigitos(String texto) {
        if (texto == null) {
            return null;
        }
        String digitos = texto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    private ConfiguracionResponse aResponse(Configuracion c) {
        return new ConfiguracionResponse(c.getNombreEmpresa(), c.getRuc(), c.getLogoUrl(), c.getWhatsappSoporte(),
                c.getCodigoPais(), c.getYapeNumero(), c.getYapeTitular(), c.getCuentaBancaria(),
                c.getDiasTolerancia(), c.getPlantillaRecordatorio(), c.getMoneda());
    }
}
