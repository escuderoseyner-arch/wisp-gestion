package com.escuderoseyner.wisp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Lee las propiedades app.jwt.* de application.properties
// (que a su vez vienen de las variables de entorno JWT_SECRET y JWT_EXPIRACION_MINUTOS)
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expiracionMinutos) {
}
