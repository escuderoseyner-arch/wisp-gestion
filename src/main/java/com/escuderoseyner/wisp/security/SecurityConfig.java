package com.escuderoseyner.wisp.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    // Permiso especial que reemplaza al rol mientras debe_cambiar_password = true
    public static final String AUTORIDAD_CAMBIO_PASSWORD_PENDIENTE = "CAMBIO_PASSWORD_PENDIENTE";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RespuestasSeguridad respuestas) throws Exception {
        http
                // CSRF protege sesiones con cookies; aquí usamos tokens en un header, así que no aplica
                .csrf(csrf -> csrf.disable())
                // Stateless: el servidor no guarda sesiones, cada petición trae su token
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // CSP: el navegador solo ejecuta scripts y estilos de nuestros propios archivos.
                // Si alguien lograra inyectar un <script> en la página, no correría (protege el token).
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https: data:; "
                                + "connect-src 'self'; object-src 'none'; base-uri 'self'; "
                                + "form-action 'self'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Archivos del frontend: son públicos porque no contienen datos.
                        // Los datos están en /api/** y ahí sí se exige token.
                        .requestMatchers(HttpMethod.GET, "/", "/*.html", "/admin/*.html", "/cliente/*.html",
                                "/css/**", "/js/**", "/img/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // El MikroTik no usa el login: se autentica con el token de su red (ver MikrotikService)
                        .requestMatchers("/api/mikrotik/**").permitAll()
                        // Cualquier usuario con token válido, incluso si debe cambiar su contraseña
                        .requestMatchers(HttpMethod.POST, "/api/auth/cambiar-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/cliente/**").hasRole("CLIENTE")
                        // Todo lo que no esté en la lista queda prohibido
                        .anyRequest().denyAll())
                // Lee el header "Authorization: Bearer <token>" y valida el JWT
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    // Convierte el token en permisos: el claim "rol" pasa a ser ROLE_ADMIN o ROLE_CLIENTE.
    // Si el usuario debe cambiar su contraseña, NO recibe su rol, solo el permiso especial.
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtService.CLAIM_DEBE_CAMBIAR_PASSWORD))) {
                return List.<GrantedAuthority>of(new SimpleGrantedAuthority(AUTORIDAD_CAMBIO_PASSWORD_PENDIENTE));
            }
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString(JwtService.CLAIM_ROL)));
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Lo usa AuthService en el login: busca el usuario (UsuarioDetailsService) y compara con BCrypt
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // Clave para firmar y verificar los tokens, a partir de JWT_SECRET (en Base64).
    // Si falta o es muy corta, la app no arranca: mejor fallar que tener tokens débiles.
    @Bean
    public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(jwtProperties.secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET debe estar codificado en Base64");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (256 bits)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).build();
    }

    // Verifica la firma, la fecha de expiración, que el emisor sea esta app
    // y que el usuario siga vigente (UsuarioVigenteValidator consulta la base de datos)
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, UsuarioVigenteValidator usuarioVigenteValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JwtService.EMISOR),
                usuarioVigenteValidator));
        return decoder;
    }
}
