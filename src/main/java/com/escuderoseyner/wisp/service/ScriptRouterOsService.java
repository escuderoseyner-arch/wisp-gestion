package com.escuderoseyner.wisp.service;

import com.escuderoseyner.wisp.model.Red;
import com.escuderoseyner.wisp.security.TokenRed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// Arma el script de RouterOS que instala la sincronización ("wisp-sync") en el MikroTik,
// a partir de la plantilla src/main/resources/mikrotik/wisp-sync.rsc.
//
// El script instalado convive con el script "puente" sin tocarlo: solo crea, reemplaza o borra
// su propio script y su propia tarea programada (ambos llamados "wisp-sync").
@Service
public class ScriptRouterOsService {

    private static final String PLANTILLA = "mikrotik/wisp-sync.rsc";
    // La URL va dentro de un texto de RouterOS: nada de comillas, $, \, espacios ni llaves
    private static final Pattern URL_SEGURA = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?(/[A-Za-z0-9._~/-]*)?$");

    private final String plantilla;
    private final String urlPublica;
    private final String marcadorPuente;

    public ScriptRouterOsService(@Value("${app.mikrotik.url-publica:}") String urlPublica,
                                 @Value("${app.mikrotik.marcador-puente}") String marcadorPuente) {
        this.plantilla = leerPlantilla();
        this.urlPublica = quitarBarraFinal(urlPublica.trim());
        this.marcadorPuente = marcadorPuente.trim();
    }

    // urlDePeticion: la de la petición actual, solo si no está configurada APP_URL_PUBLICA
    public String generar(Red red, String token, String urlDePeticion) {
        if (!TokenRed.formatoValido(token)) {
            throw new ReglaNegocioException("El token no tiene un formato válido.");
        }
        String url = urlBase(urlDePeticion);
        return plantilla
                .replace("{{RED}}", FormatoRouterOs.comentario(red.getNombre()))
                .replace("{{URL}}", url)
                .replace("{{TOKEN}}", token)
                .replace("{{INTERVALO}}", String.valueOf(red.getIntervaloSegundos()));
    }

    // Lo que descarga el script "puente": la primera línea es su marcador, sin ella el puente no ejecuta nada
    public String generarParaPuente(Red red, String token, String urlDePeticion) {
        return marcadorPuente + "\n" + generar(red, token, urlDePeticion);
    }

    public String urlBase(String urlDePeticion) {
        String url = !urlPublica.isEmpty() ? urlPublica : quitarBarraFinal(urlDePeticion == null ? "" : urlDePeticion);
        if (!URL_SEGURA.matcher(url).matches()) {
            throw new ReglaNegocioException("La URL pública de la web no es válida para el script: " + url
                    + ". Configura la variable APP_URL_PUBLICA (ej: https://mi-app.onrender.com).");
        }
        return url;
    }

    private static String leerPlantilla() {
        try (InputStream entrada = new ClassPathResource(PLANTILLA).getInputStream()) {
            // Siempre saltos de línea \n, aunque Git haya guardado el archivo con \r\n en Windows
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la plantilla " + PLANTILLA, e);
        }
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
