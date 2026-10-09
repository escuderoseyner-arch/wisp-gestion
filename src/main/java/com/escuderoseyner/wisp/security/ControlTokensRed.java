package com.escuderoseyner.wisp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Frena a quien prueba tokens de MikroTik al azar: más de 60 tokens inválidos desde una IP
// en 15 minutos -> esa IP espera (ni siquiera se consulta la base de datos).
// El límite es alto a propósito: los MikroTik están detrás del CGNAT de Starlink y comparten IP
// con otros usuarios; un router con el token viejo (unas 22 consultas fallidas cada 15 minutos)
// nunca llega a bloquearse, así que al corregir su token vuelve a conectar al instante.
// Un token de 256 bits no se puede adivinar ni con millones de intentos: esto es para no gastar recursos.
@Slf4j
@Component
public class ControlTokensRed {

    public static final int MAX_FALLOS_POR_IP = 60;
    public static final Duration VENTANA = Duration.ofMinutes(15);
    private static final int LIMPIAR_DESDE = 10_000;

    private record Contador(Instant inicio, int fallos) {
    }

    private final Map<String, Contador> porIp = new ConcurrentHashMap<>();
    private final Clock reloj;

    public ControlTokensRed() {
        this(Clock.systemUTC());
    }

    ControlTokensRed(Clock reloj) {
        this.reloj = reloj;
    }

    public boolean ipBloqueada(String ip) {
        Contador c = porIp.get(ip);
        return c != null && !vencido(c, reloj.instant()) && c.fallos() >= MAX_FALLOS_POR_IP;
    }

    public void registrarFallo(String ip) {
        Instant ahora = reloj.instant();
        if (porIp.size() > LIMPIAR_DESDE) {
            porIp.values().removeIf(c -> vencido(c, ahora));
        }
        int fallos = porIp.compute(ip, (k, actual) -> actual == null || vencido(actual, ahora)
                ? new Contador(ahora, 1)
                : new Contador(actual.inicio(), actual.fallos() + 1)).fallos();
        if (fallos == MAX_FALLOS_POR_IP) {
            log.warn("IP {} bloqueada 15 minutos por {} tokens de MikroTik inválidos", ip, MAX_FALLOS_POR_IP);
        }
    }

    private boolean vencido(Contador contador, Instant ahora) {
        return !ahora.isBefore(contador.inicio().plus(VENTANA));
    }
}
