package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Crea los tokens JWT. La validación (firma y expiración) la hace Spring Security
// con el JwtDecoder definido en SecurityConfig.
@Service
public class JwtService {

    public static final String EMISOR = "wisp-gestion";
    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_DEBE_CAMBIAR_PASSWORD = "debeCambiarPassword";

    private final JwtEncoder jwtEncoder;
    private final Duration duracion;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.duracion = Duration.ofMinutes(jwtProperties.expiracionMinutos());
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuario.getUsername())          // "sub": a quién pertenece el token
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .claim(CLAIM_ROL, usuario.getRol().name())
                .claim(CLAIM_DEBE_CAMBIAR_PASSWORD, usuario.getDebeCambiarPassword())
                .build();
        // HS256: firma con la clave secreta (HMAC-SHA256)
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getDuracionSegundos() {
        return duracion.toSeconds();
    }
}
