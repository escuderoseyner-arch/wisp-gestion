package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.service.MikrotikService;
import com.escuderoseyner.wisp.service.RecursoNoEncontradoException;
import com.escuderoseyner.wisp.service.TokenRedInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Endpoints que consulta el MikroTik con /tool fetch (modelo "pull": la web nunca se conecta al router).
// No usan el login de la web: se autentican con el token de la red en el header X-Red-Token.
// Responden texto plano (text/plain), que es fácil de leer con scripts de RouterOS.
// El MikroTik debe enviar los POST con "Content-Type: text/plain".
@RestController
@RequestMapping(value = "/api/mikrotik", produces = MediaType.TEXT_PLAIN_VALUE)
public class MikrotikController {

    public static final String HEADER_TOKEN = "X-Red-Token";
    private static final int MAX_BYTES_CUERPO = 512 * 1024;

    private final MikrotikService mikrotikService;

    public MikrotikController(MikrotikService mikrotikService) {
        this.mikrotikService = mikrotikService;
    }

    @GetMapping("/acciones")
    public String acciones(@RequestHeader(value = HEADER_TOKEN, required = false) String token,
                           HttpServletRequest http) {
        return mikrotikService.consultar(token, http.getRemoteAddr());
    }

    @PostMapping("/acciones/{id}/aplicada")
    public String aplicada(@PathVariable Long id,
                           @RequestHeader(value = HEADER_TOKEN, required = false) String token,
                           HttpServletRequest http) {
        mikrotikService.confirmarAccion(token, http.getRemoteAddr(), id, true, null);
        return "ok\n";
    }

    // Cuerpo: el mensaje de error de RouterOS
    @PostMapping("/acciones/{id}/error")
    public String error(@PathVariable Long id,
                        @RequestHeader(value = HEADER_TOKEN, required = false) String token,
                        HttpServletRequest http) throws IOException {
        mikrotikService.confirmarAccion(token, http.getRemoteAddr(), id, false, leerCuerpo(http, 4096));
        return "ok\n";
    }

    @PostMapping("/reporte")
    public String reporte(@RequestHeader(value = HEADER_TOKEN, required = false) String token,
                          HttpServletRequest http) throws IOException {
        int colas = mikrotikService.recibirReporte(token, http.getRemoteAddr(), leerCuerpo(http, MAX_BYTES_CUERPO));
        return "ok " + colas + "\n";
    }

    // Token inválido: 401 sin ningún detalle
    @ExceptionHandler(TokenRedInvalidoException.class)
    public ResponseEntity<String> tokenInvalido() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.TEXT_PLAIN).body("");
    }

    // Acción que no existe o es de otra red (en texto, no en JSON)
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<String> noEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body(e.getMessage() + "\n");
    }

    // Se lee el cuerpo crudo con un tope: si el MikroTik (o alguien) manda más, se corta ahí.
    private static String leerCuerpo(HttpServletRequest http, int maximo) throws IOException {
        try (InputStream entrada = http.getInputStream()) {
            byte[] bytes = entrada.readNBytes(maximo);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
