package com.escuderoseyner.wisp.security;

import com.escuderoseyner.wisp.model.EstadoCliente;
import com.escuderoseyner.wisp.model.Usuario;
import com.escuderoseyner.wisp.repository.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

// Se ejecuta en CADA petición con token, después de comprobar la firma y la expiración.
// Rechaza (401) el token si:
//  - el usuario ya no existe o cambió de nombre de usuario,
//  - su cuenta está desactivada, o es de un cliente RETIRADO,
//  - se emitió ANTES del último cambio o restablecimiento de su contraseña.
// Así un token robado o viejo deja de servir sin esperar a que expire.
@Component
public class UsuarioVigenteValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESION_INVALIDA =
            new OAuth2Error("invalid_token", "La sesión ya no es válida", null);

    private final UsuarioRepository usuarioRepository;

    public UsuarioVigenteValidator(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Optional<Usuario> encontrado = usuarioRepository.findConClienteByUsername(jwt.getSubject());
        if (encontrado.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(SESION_INVALIDA);
        }
        Usuario usuario = encontrado.get();

        boolean clienteRetirado = usuario.getCliente() != null
                && usuario.getCliente().getEstado() == EstadoCliente.RETIRADO;
        if (!usuario.getActivo() || clienteRetirado) {
            return OAuth2TokenValidatorResult.failure(SESION_INVALIDA);
        }

        if (usuario.getPasswordCambiadoEn() != null) {
            Instant cambio = usuario.getPasswordCambiadoEn().atZone(ZoneId.systemDefault()).toInstant();
            Instant emitido = jwt.getIssuedAt();
            if (emitido == null || emitido.isBefore(cambio)) {
                return OAuth2TokenValidatorResult.failure(SESION_INVALIDA);
            }
        }
        return OAuth2TokenValidatorResult.success();
    }
}
