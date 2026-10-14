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
// a partir de las plantillas src/main/resources/mikrotik/wisp-sync.rsc (instalador) y
// wisp-sync-cuerpo.rsc (el código del script).
//
// El código va dentro de source="..." como texto escapado en UNA sola línea, igual que lo hace /export.
// No se usa source={ ... } en varias líneas: RouterOS lo interpreta distinto al pegarlo en la terminal,
// al ejecutarlo como script o con /import, y en RouterOS 7.24 daba "syntax error".
//
// El script instalado convive con el script "puente" sin tocarlo: solo crea, reemplaza o borra
// su propio script y su propia tarea programada (ambos llamados "wisp-sync").
@Service
public class ScriptRouterOsService {

    private static final String PLANTILLA = "mikrotik/wisp-sync.rsc";
    private static final String PLANTILLA_CUERPO = "mikrotik/wisp-sync-cuerpo.rsc";
    // Archivo temporal del comando corto de instalación (se borra al terminar)
    public static final String ARCHIVO_INSTALACION = "wisp-instalar.rsc";
    // La URL va dentro de un texto de RouterOS: nada de comillas, $, \, espacios ni llaves
    private static final Pattern URL_SEGURA = Pattern.compile("^https?://[A-Za-z0-9.-]+(:\\d{1,5})?(/[A-Za-z0-9._~/-]*)?$");

    private final String plantilla;
    private final String plantillaCuerpo;
    private final String urlPublica;
    private final String marcadorPuente;

    public ScriptRouterOsService(@Value("${app.mikrotik.url-publica:}") String urlPublica,
                                 @Value("${app.mikrotik.marcador-puente}") String marcadorPuente) {
        this.plantilla = leerPlantilla(PLANTILLA);
        this.plantillaCuerpo = leerPlantilla(PLANTILLA_CUERPO);
        this.urlPublica = quitarBarraFinal(urlPublica.trim());
        this.marcadorPuente = marcadorPuente.trim();
    }

    // urlDePeticion: la de la petición actual, solo si no está configurada APP_URL_PUBLICA
    public String generar(Red red, String token, String urlDePeticion) {
        if (!TokenRed.formatoValido(token)) {
            throw new ReglaNegocioException("El token no tiene un formato válido.");
        }
        String url = urlBase(urlDePeticion);
        String cuerpo = plantillaCuerpo
                .replace("{{URL}}", url)
                .replace("{{HOST}}", hostWeb(url))
                .replace("{{TOKEN}}", token);
        return plantilla
                .replace("{{RED}}", FormatoRouterOs.comentario(red.getNombre()))
                .replace("{{INTERVALO}}", String.valueOf(red.getIntervaloSegundos()))
                .replace("{{SOURCE}}", textoRouterOs(cuerpo));
    }

    // Comando corto para pegar en la terminal: descarga el instalador (token en el header),
    // lo importa y borra el archivo, aunque la importación falle.
    public String comandoInstalacion(String token, String urlDePeticion) {
        if (!TokenRed.formatoValido(token)) {
            throw new ReglaNegocioException("El token no tiene un formato válido.");
        }
        String url = urlBase(urlDePeticion);
        return "/tool fetch url=\"" + url + "/api/mikrotik/instalar\" http-header-field=\"X-Red-Token: " + token
                + "\" check-certificate=yes-without-crl output=file dst-path=" + ARCHIVO_INSTALACION + "\n"
                + ":do { /import file-name=" + ARCHIVO_INSTALACION + " } on-error={ :log warning \"wisp-gestion: fallo la instalacion\" };"
                + " /file remove [find where name=\"" + ARCHIVO_INSTALACION + "\"]\n";
    }

    // Texto entre comillas de RouterOS: escapa \, ", $ y ? y convierte los saltos de línea en \n
    // (sin esto, $ se tomaría como variable y ? abriría la ayuda en la terminal)
    static String textoRouterOs(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 256);
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '$' -> sb.append("\\$");
                case '?' -> sb.append("\\?");
                case '\n' -> sb.append("\\n");
                case '\r' -> { }
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
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

    // "https://mi-app.onrender.com" -> "mi-app.onrender.com": el proxy del MikroTik redirige a <host>/aviso/<token>
    // por HTTP y la web lo pasa a HTTPS
    static String hostWeb(String url) {
        return url.replaceFirst("^https?://", "");
    }

    private static String leerPlantilla(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            // Siempre saltos de línea \n, aunque Git haya guardado el archivo con \r\n en Windows
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la plantilla " + ruta, e);
        }
    }

    private static String quitarBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
