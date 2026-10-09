package com.escuderoseyner.wisp.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Conversión de valores entre la web y RouterOS 7 (velocidades, targets, comentarios).
// Todo lo que viaja al router pasa por aquí para que nunca lleve caracteres que rompan
// el formato de texto (una acción por línea, campos separados por "|").
public final class FormatoRouterOs {

    // Tipo de cola que usan todas las colas de clientes (subida/bajada)
    public static final String TIPO_COLA = "fq-codel-up/fq-codel-down";

    // "15M", "512k", "1G" o un número en bits por segundo ("15000000")
    private static final Pattern VELOCIDAD = Pattern.compile("^(\\d+(?:\\.\\d+)?)([kKmMgG]?)(?:bps)?$");
    private static final int MAX_COMENTARIO = 100;

    private FormatoRouterOs() {
    }

    // RouterOS 7: "subida/bajada". Ej: 5 de subida y 15 de bajada -> "5M/15M"
    public static String maxLimit(int subidaMbps, int bajadaMbps) {
        return subidaMbps + "M/" + bajadaMbps + "M";
    }

    public static String target(String ip) {
        return ip + "/32";
    }

    // "5M/15M" o "5000000/15000000" -> {5000000, 15000000}. null si no se entiende.
    public static long[] parSubidaBajada(String valor) {
        if (valor == null) {
            return null;
        }
        String[] partes = valor.trim().split("/");
        if (partes.length != 2) {
            return null;
        }
        Long subida = bps(partes[0]);
        Long bajada = bps(partes[1]);
        return subida == null || bajada == null ? null : new long[]{subida, bajada};
    }

    public static Long bps(String valor) {
        Matcher m = VELOCIDAD.matcher(valor.trim());
        if (!m.matches()) {
            return null;
        }
        double numero = Double.parseDouble(m.group(1));
        long multiplicador = switch (m.group(2).toLowerCase(Locale.ROOT)) {
            case "k" -> 1_000L;
            case "m" -> 1_000_000L;
            case "g" -> 1_000_000_000L;
            default -> 1L;
        };
        return Math.round(numero * multiplicador);
    }

    public static boolean mismaVelocidad(String deseada, String reportada) {
        long[] a = parSubidaBajada(deseada);
        long[] b = parSubidaBajada(reportada);
        return a != null && b != null && a[0] == b[0] && a[1] == b[1];
    }

    // RouterOS puede devolver "192.168.1.11/32" o "192.168.1.11"
    public static boolean mismoTarget(String deseado, String reportado) {
        if (reportado == null) {
            return false;
        }
        String r = reportado.trim();
        if (!r.contains("/")) {
            r = r + "/32";
        }
        return deseado.equals(r);
    }

    // "María Ñañez (Casa)" -> "Maria Nanez (Casa)". Solo letras sin tilde, números y . , - _ ( ) espacio.
    public static String comentario(String nombre) {
        String sinTildes = Normalizer.normalize(nombre == null ? "" : nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String limpio = sinTildes.replaceAll("[^A-Za-z0-9 .,_()-]", " ").replaceAll("\\s+", " ").trim();
        if (limpio.length() > MAX_COMENTARIO) {
            limpio = limpio.substring(0, MAX_COMENTARIO).trim();
        }
        return limpio.isEmpty() ? "-" : limpio;
    }

    // Valores que llegan del router: sin "|", sin saltos ni caracteres de control, y con tope de largo
    public static String textoRecibido(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.replaceAll("[\\p{Cntrl}|]", " ").trim();
        return limpio.length() > maximo ? limpio.substring(0, maximo) : limpio;
    }

    // RouterOS escribe "true"/"false" o "yes"/"no"
    public static Boolean booleano(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> null;
        };
    }
}
