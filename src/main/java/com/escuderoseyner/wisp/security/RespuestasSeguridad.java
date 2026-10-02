package com.escuderoseyner.wisp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Respuestas JSON en español para los errores que ocurren en los filtros de seguridad,
// antes de llegar a los controladores (por eso no los atrapa @RestControllerAdvice).
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 401: no hay token, es inválido o expiró
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, HttpServletResponse.SC_UNAUTHORIZED,
                "Debes iniciar sesión. El token falta, no es válido o ya expiró.");
    }

    // 403: hay token válido, pero el usuario no tiene permiso para esa ruta
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean debeCambiarPassword = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> SecurityConfig.AUTORIDAD_CAMBIO_PASSWORD_PENDIENTE.equals(a.getAuthority()));

        String mensaje = debeCambiarPassword
                ? "Debes cambiar tu contraseña antes de continuar."
                : "No tienes permiso para acceder a este recurso.";
        escribir(response, HttpServletResponse.SC_FORBIDDEN, mensaje);
    }

    // Los mensajes son textos fijos sin comillas, por eso se puede armar el JSON a mano
    private void escribir(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"detalles\":[]}");
    }
}
