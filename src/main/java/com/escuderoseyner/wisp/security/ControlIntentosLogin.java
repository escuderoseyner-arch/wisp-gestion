package com.escuderoseyner.wisp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Frena los intentos de adivinar contraseñas, contando los FALLOS en ventanas de 15 minutos:
//  1. Por IP: más de 20 fallos desde una misma conexión (con cualquier usuario) -> esa IP espera.
//  2. Por cuenta + IP: 5 fallos con un usuario desde una IP -> ese usuario se bloquea SOLO para esa IP.
//     Así nadie puede bloquear a propósito la cuenta de otra persona: el dueño, desde su propia
//     conexión, sigue entrando con normalidad.
// Los contadores viven en memoria (se reinician si se reinicia la app). Es suficiente para un
// solo servidor; si algún día hay varios, habría que moverlos a la base de datos o a Redis.
@Slf4j
@Component
public class ControlIntentosLogin {

    public static final int MAX_FALLOS_POR_IP = 20;
    public static final int MAX_FALLOS_POR_CUENTA_E_IP = 5;
    public static final Duration VENTANA = Duration.ofMinutes(15);
    private static final int LIMPIAR_DESDE = 10_000; // entradas guardadas antes de borrar las vencidas

    // Primer fallo de la ventana y cuántos van
    private record Contador(Instant inicio, int fallos) {
    }

    private final Map<String, Contador> porIp = new ConcurrentHashMap<>();
    private final Map<String, Contador> porCuentaEIp = new ConcurrentHashMap<>();
    private final Clock reloj;

    public ControlIntentosLogin() {
        this(Clock.systemUTC());
    }

    // Para las pruebas: permite simular el paso del tiempo
    ControlIntentosLogin(Clock reloj) {
        this.reloj = reloj;
    }

    public boolean ipBloqueada(String ip) {
        return superaLimite(porIp.get(ip), MAX_FALLOS_POR_IP);
    }

    public boolean cuentaBloqueada(String username, String ip) {
        return superaLimite(porCuentaEIp.get(clave(username, ip)), MAX_FALLOS_POR_CUENTA_E_IP);
    }

    public void registrarFallo(String username, String ip) {
        if (porIp.size() + porCuentaEIp.size() > LIMPIAR_DESDE) {
            limpiarVencidos();
        }
        if (sumar(porIp, ip) == MAX_FALLOS_POR_IP) {
            // Sirve también para comprobar en producción que se ve la IP real del cliente (y no la del proxy)
            log.warn("IP {} bloqueada 15 minutos por {} intentos de login fallidos", ip, MAX_FALLOS_POR_IP);
        }
        sumar(porCuentaEIp, clave(username, ip));
    }

    // Entró bien: se olvidan los fallos de esa cuenta desde esa IP (el límite de la IP se mantiene)
    public void registrarExito(String username, String ip) {
        porCuentaEIp.remove(clave(username, ip));
    }

    // El admin restableció la contraseña: se desbloquea esa cuenta en todas las IP
    public void desbloquearCuenta(String username) {
        String prefijo = normalizar(username) + "|";
        porCuentaEIp.keySet().removeIf(k -> k.startsWith(prefijo));
    }

    // Devuelve cuántos fallos van en la ventana actual
    private int sumar(Map<String, Contador> mapa, String clave) {
        Instant ahora = reloj.instant();
        return mapa.compute(clave, (k, actual) -> actual == null || vencido(actual, ahora)
                ? new Contador(ahora, 1)
                : new Contador(actual.inicio(), actual.fallos() + 1)).fallos();
    }

    private boolean superaLimite(Contador contador, int maximo) {
        return contador != null && !vencido(contador, reloj.instant()) && contador.fallos() >= maximo;
    }

    private boolean vencido(Contador contador, Instant ahora) {
        return !ahora.isBefore(contador.inicio().plus(VENTANA));
    }

    private void limpiarVencidos() {
        Instant ahora = reloj.instant();
        porIp.values().removeIf(c -> vencido(c, ahora));
        porCuentaEIp.values().removeIf(c -> vencido(c, ahora));
    }

    // En MySQL los usuarios no distinguen mayúsculas: "Admin" y "admin" son la misma cuenta
    private static String normalizar(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String clave(String username, String ip) {
        return normalizar(username) + "|" + ip;
    }
}
